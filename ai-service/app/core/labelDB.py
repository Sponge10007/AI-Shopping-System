import random
import os
import threading
import time
from concurrent.futures import ThreadPoolExecutor

import chromadb
import numpy as np
from sentence_transformers import SentenceTransformer


class LabelDB:
    _instance = None
    _instance_lock = threading.Lock()
    DEFAULT_SEARCH_LIMIT = 50

    def __new__(cls, *args, **kwargs):
        """线程安全单例，避免并发请求重复加载 Embedding 模型。"""
        if cls._instance is None:
            with cls._instance_lock:
                if cls._instance is None:
                    cls._instance = super(LabelDB, cls).__new__(cls)
        return cls._instance

    def __init__(self, path=None, max_workers=4):
        """初始化 ChromaDB、Embedding 模型和后台线程池。"""
        if hasattr(self, "_initialized") and self._initialized:
            return

        # __new__ 只能保证对象只有一个；__init__ 也要加锁，避免两个线程同时初始化同一对象。
        with self._instance_lock:
            if hasattr(self, "_initialized") and self._initialized:
                return

            if path is None:
                path = os.getenv("LABELDB_CHROMA_PATH", "./chroma_db")
            model_name_or_path = (
                os.getenv("LABELDB_MODEL_PATH")
                or os.getenv("SENTENCE_TRANSFORMERS_MODEL")
                or "sentence-transformers/all-MiniLM-L6-v2"
            )
            cache_folder = os.getenv("SENTENCE_TRANSFORMERS_HOME") or None

            self.client = chromadb.PersistentClient(path=path)
            self.prod_collection = self.client.get_or_create_collection(name="products")
            self.user_collection = self.client.get_or_create_collection(name="user_labels")
            self.model = SentenceTransformer(model_name_or_path, cache_folder=cache_folder)
            self.executor = ThreadPoolExecutor(max_workers=max_workers)
            self.user_label_lock = threading.Lock()
            self._initialized = True

    def _normalize_embedding(self, embedding):
        """Normalize a vector for cosine-distance style comparison."""
        norm = np.linalg.norm(embedding)
        return embedding if norm == 0 else embedding / norm

    def _do_prod_add_product(self, product_id: str, description: str):
        """Encode and store a product in ChromaDB."""
        try:
            embedding = self.model.encode([description])[0]
            embedding = self._normalize_embedding(embedding)

            self.prod_collection.upsert(
                embeddings=[embedding.tolist()],
                documents=[description],
                ids=[str(product_id)],
            )
        except Exception as e:
            print(f"[Error] Failed to add product in background: {e}")

    def prod_add_product(self, product_id: str, description: str):
        """Add or update a product asynchronously."""
        self.executor.submit(self._do_prod_add_product, product_id, description)

    def prod_delete_product(self, product_id: str):
        """Delete a product synchronously."""
        self.prod_collection.delete(ids=[str(product_id)])

    def prod_search(
        self,
        user_id: str = "-1",
        query: str = "",
        distance_threshold: float = 0.9,
        limit: int = DEFAULT_SEARCH_LIMIT,
    ):
        """按 ChromaDB 距离搜索商品，并可选记录用户标签。

        user_id 默认值为 "-1"，表示本次搜索由 AI 工具调用触发，不属于真实用户行为，
        因此不会写入用户标签库；传入其他字符串用户 ID 时才会记录用户偏好。

        ChromaDB 的默认距离是欧几里得距离，数值越小越相似。因此这里使用
        distance_threshold 表示“最大允许距离”，只保留 dist < distance_threshold
        的商品。

        limit 控制向量库召回 Top K 数量，避免商品量很大时每次全库返回造成内存和
        响应时间压力。
        """


        user_id = str(user_id)
        if user_id != "-1":
            self.user_add_label(user_id, query)

        product_count = self.prod_collection.count()
        if product_count == 0:
            return []

        q_emb = self._normalize_embedding(self.model.encode([query])[0])
        n_results = min(max(1, int(limit)), product_count)
        results = self.prod_collection.query(
            query_embeddings=[q_emb.tolist()],
            n_results=n_results,
        )

        res_ids = []
        if results["ids"] and results["ids"][0]:
            for id_str, dist in zip(results["ids"][0], results["distances"][0]):
                if dist < distance_threshold:
                    res_ids.append(str(id_str))
        return res_ids

    def _do_user_add_label(self, user_id: str, query: str):
        """Encode and store one user label, keeping at most 10 labels per user."""
        try:
            user_id = str(user_id)
            embedding = self.model.encode([query])[0]
            embedding = self._normalize_embedding(embedding)
            now = time.time()

            with self.user_label_lock:
                user_labels = self.user_collection.get(
                    where={"user_id": user_id},
                    include=["metadatas"],
                )

                if user_labels and user_labels["ids"] and len(user_labels["ids"]) >= 10:
                    records = [
                        (label_id, metadata["date"])
                        for label_id, metadata in zip(
                            user_labels["ids"],
                            user_labels["metadatas"],
                        )
                    ]
                    records.sort(key=lambda item: item[1])
                    ids_to_delete = [
                        label_id for label_id, _ in records[: len(records) - 9]
                    ]
                    self.user_collection.delete(ids=ids_to_delete)

                self.user_collection.add(
                    ids=[f"{user_id}_{now}"],
                    embeddings=[embedding.tolist()],
                    documents=[query],
                    metadatas=[{"user_id": user_id, "date": now}],
                )
        except Exception as e:
            print(f"[Error] Failed to update user labels in background: {e}")

    def user_add_label(self, user_id: str, query: str):
        """Add a user label asynchronously."""
        self.executor.submit(self._do_user_add_label, user_id, query)

    def user_search(self, user_id: str, maxnum: int = 5):
        """Recommend products from the user's historical labels."""
        user_id = str(user_id)
        if self.prod_collection.count() == 0:
            return []

        history = self.user_collection.get(
            where={"user_id": user_id},
            include=["embeddings"],
        )

        embeddings = history.get("embeddings") if history else None
        if embeddings is None or len(embeddings) == 0:
            return []

        candidate_pool = set()
        k = min(maxnum * 3, self.prod_collection.count())

        for emb in embeddings:
            results = self.prod_collection.query(
                query_embeddings=[emb],
                n_results=k,
            )

            if results["ids"] and results["ids"][0]:
                current_results = [str(pid) for pid in results["ids"][0]]
                if len(current_results) <= maxnum:
                    candidate_pool.update(current_results)
                else:
                    candidate_pool.update(random.sample(current_results, maxnum))

        return list(candidate_pool)
