import html
import json
import os
import re
import socket
import sqlite3
import threading
from collections import defaultdict
from datetime import datetime, timezone
from typing import Any, Callable, Dict, List, Optional, Sequence, TYPE_CHECKING
from urllib import request as url_request
from urllib.error import HTTPError, URLError

if TYPE_CHECKING:
    from core.labelDB import LabelDB


class llmChat:
    """面向网页后端的商品搜索聊天助手。

    这里保留原来的 init().chat() 对外接口，但内部直接调用 OpenAI-compatible
    /chat/completions HTTP 接口。这样本地开发不再依赖 LangChain/LangGraph 的
    重型运行时，也能对认证、模型名和网络错误给出更快、更明确的反馈。
    """

    def __init__(
        self,
        api_key: str,
        prompt_file_path: str = "prompt.txt",
        db_path: str = "sqlite:///chat_history.db",
        model: str = "deepseek-chat",
        base_url: str = "https://api.deepseek.com",
    ):
        self.api_key = api_key
        self.prompt_file_path = prompt_file_path
        self.db_file = self._parse_sqlite_path(db_path)
        self.system_prompt = self._load_system_prompt(prompt_file_path)
        self.model = model
        self.base_url = base_url.rstrip("/")
        self.request_timeout = self._safe_float(os.getenv("AI_CHAT_TIMEOUT_SECONDS"), 15.0)

        self.conn = sqlite3.connect(self.db_file, check_same_thread=False)
        self.conn.row_factory = sqlite3.Row
        self.db_lock = threading.RLock()
        self.session_locks = defaultdict(threading.RLock)
        self._init_db()

        # 外部商品详情查询函数：输入商品 id，返回包含该商品所有信息的字符串。
        self.search_function: Optional[Callable[[str], str]] = None
        # LabelDB 会加载 Hugging Face embedding 模型，只有显式搜索时才懒加载。
        self.label_db: Optional["LabelDB"] = None

    def set_search_function(self, search_function: Callable[[str], str]) -> None:
        """注册业务侧商品详情查询函数。"""

        self.search_function = search_function

    def chat(self, content: str, user_id: str, session_id: str) -> Dict[str, Any]:
        """同步对话接口。"""

        if not content or not str(content).strip():
            raise ValueError("content 不能为空")
        if not user_id:
            raise ValueError("user_id 不能为空")
        if not session_id:
            raise ValueError("session_id 不能为空")

        lock = self.session_locks[session_id]
        with lock:
            history = self._load_history(user_id=user_id, session_id=session_id)
            user_message = {"role": "user", "content": str(content).strip()}
            messages = [
                {"role": "system", "content": self.system_prompt},
                *history,
                user_message,
            ]

            try:
                raw_answer = self._call_chat_completions(messages)
            except RuntimeError as exc:
                raw_answer = (
                    "AI 服务调用失败："
                    f"{exc}。请检查 ai-service/.env 中的 DEEPSEEK_API_KEY、"
                    "DEEPSEEK_BASE_URL 和 DEEPSEEK_MODEL 是否属于同一个服务商。"
                )

            assistant_message = {"role": "assistant", "content": raw_answer}
            self._save_messages(
                user_id=user_id,
                session_id=session_id,
                messages=[user_message, assistant_message],
            )
            return self._parse_model_answer(raw_answer)

    def delete_history(self, user_id: str, session_id: str) -> None:
        """删除指定用户在指定会话中的历史记忆。"""

        if not user_id:
            raise ValueError("user_id 不能为空")
        if not session_id:
            raise ValueError("session_id 不能为空")

        with self.db_lock:
            self.conn.execute(
                "DELETE FROM chat_messages WHERE user_id = ? AND session_id = ?",
                (user_id, session_id),
            )
            self.conn.commit()

    def search(self, id: str) -> str:
        """调用外部商品详情查询函数，返回单个商品的完整信息字符串。"""

        if self.search_function is None:
            return ""
        return self.search_function(str(id))

    def search_products(
        self,
        query: str,
        distance_threshold: float = 0.9,
        max_results: int = 3,
        recall_limit: int = 50,
    ) -> List[str]:
        """公开的 AI 商品搜索流程，便于后端或测试直接调用。"""

        if not query:
            return []
        max_results = self._safe_int(max_results, 3)
        recall_limit = self._safe_int(recall_limit, 50)
        return self._search_products_for_ai(
            query=query,
            distance_threshold=distance_threshold,
            max_results=max_results,
            recall_limit=recall_limit,
        )

    def _search_products_for_ai(
        self,
        query: str,
        distance_threshold: float = 0.9,
        max_results: int = 3,
        recall_limit: int = 50,
    ) -> List[str]:
        """用 LabelDB 找商品 ID，再通过 Java 内部接口取商品摘要。"""

        limit = max(1, min(int(max_results), 3))
        product_ids = []
        for threshold in self._relaxed_thresholds(distance_threshold):
            product_ids = self._get_label_db().prod_search(
                user_id="-1",
                query=query,
                distance_threshold=threshold,
                limit=recall_limit,
            )
            if product_ids:
                break

        product_infos = []
        for product_id in product_ids[:limit]:
            detail = self.search(str(product_id))
            if detail:
                product_infos.append(detail)
        return product_infos

    def _call_chat_completions(self, messages: Sequence[Dict[str, str]]) -> str:
        """调用 OpenAI-compatible Chat Completions 接口。"""

        if not self.api_key:
            raise RuntimeError("缺少 API key")
        if not self.model:
            raise RuntimeError("缺少模型名")
        if not self.base_url:
            raise RuntimeError("缺少 base_url")

        payload = json.dumps(
            {
                "model": self.model,
                "messages": list(messages),
                "temperature": 0.2,
            },
            ensure_ascii=False,
        ).encode("utf-8")
        request = url_request.Request(
            self._chat_completions_url(),
            data=payload,
            headers={
                "Authorization": f"Bearer {self.api_key}",
                "Content-Type": "application/json; charset=utf-8",
                "Accept": "application/json",
            },
            method="POST",
        )

        try:
            with url_request.urlopen(request, timeout=self.request_timeout) as response:
                raw_body = response.read().decode("utf-8")
        except HTTPError as exc:
            error_body = exc.read().decode("utf-8", errors="replace")
            message = self._extract_api_error(error_body) or exc.reason or "HTTP error"
            raise RuntimeError(f"HTTP {exc.code}: {message}") from exc
        except (URLError, TimeoutError, socket.timeout) as exc:
            raise RuntimeError(f"网络请求失败或超时: {exc}") from exc

        try:
            data = json.loads(raw_body)
        except json.JSONDecodeError as exc:
            raise RuntimeError(f"模型服务返回非 JSON 内容: {raw_body[:200]}") from exc

        try:
            content = data["choices"][0]["message"]["content"]
        except (KeyError, IndexError, TypeError) as exc:
            raise RuntimeError(f"模型服务返回格式异常: {raw_body[:300]}") from exc

        return self._message_content_to_text(content)

    def _chat_completions_url(self) -> str:
        """兼容 base_url=https://api.deepseek.com 或 .../v1 两类写法。"""

        if self.base_url.endswith("/chat/completions"):
            return self.base_url
        return f"{self.base_url}/chat/completions"

    def _extract_api_error(self, raw_body: str) -> str:
        """从 OpenAI-compatible 错误响应中提取可读错误。"""

        try:
            data = json.loads(raw_body)
        except json.JSONDecodeError:
            return raw_body[:300]

        error = data.get("error") if isinstance(data, dict) else None
        if isinstance(error, dict):
            return str(error.get("message") or error.get("code") or error)
        if isinstance(error, str):
            return error
        return str(data)[:300]

    def _get_label_db(self) -> "LabelDB":
        """懒加载商品向量库，避免普通聊天被 embedding 模型下载问题阻断。"""

        if self.label_db is None:
            from core.labelDB import LabelDB

            self.label_db = LabelDB()
        return self.label_db

    def _init_db(self) -> None:
        """创建普通 SQLite 聊天记忆表。"""

        with self.db_lock:
            self.conn.execute(
                """
                CREATE TABLE IF NOT EXISTS chat_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id TEXT NOT NULL,
                    session_id TEXT NOT NULL,
                    role TEXT NOT NULL,
                    message_json TEXT NOT NULL,
                    created_at TEXT NOT NULL
                )
                """
            )
            self.conn.execute(
                """
                CREATE INDEX IF NOT EXISTS idx_chat_messages_session
                ON chat_messages(user_id, session_id, id)
                """
            )
            self.conn.commit()

    def _save_messages(
        self,
        user_id: str,
        session_id: str,
        messages: Sequence[Dict[str, str]],
    ) -> None:
        """批量保存本轮新增消息。"""

        if not messages:
            return

        now = datetime.now(timezone.utc).isoformat()
        rows = []
        for message in messages:
            role = str(message.get("role") or "assistant")
            content = self._message_content_to_text(message.get("content", ""))
            message_json = json.dumps(
                {"role": role, "content": content},
                ensure_ascii=False,
            )
            rows.append((user_id, session_id, role, message_json, now))

        with self.db_lock:
            self.conn.executemany(
                """
                INSERT INTO chat_messages
                (user_id, session_id, role, message_json, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                rows,
            )
            self.conn.commit()

    def _load_history(self, user_id: str, session_id: str) -> List[Dict[str, str]]:
        """按用户和会话读取历史，转换成 Chat Completions messages。"""

        with self.db_lock:
            records = self.conn.execute(
                """
                SELECT message_json
                FROM chat_messages
                WHERE user_id = ? AND session_id = ?
                ORDER BY id ASC
                """,
                (user_id, session_id),
            ).fetchall()

        messages = []
        for row in records[-20:]:
            normalized = self._normalize_stored_message(row["message_json"])
            if normalized:
                messages.append(normalized)
        return messages

    def _normalize_stored_message(self, raw_json: str) -> Optional[Dict[str, str]]:
        """兼容旧 LangChain message_json 和新的轻量 message_json。"""

        try:
            data = json.loads(raw_json)
        except json.JSONDecodeError:
            return None

        role = data.get("role") if isinstance(data, dict) else None
        content = data.get("content") if isinstance(data, dict) else None

        if isinstance(data, dict) and "data" in data:
            legacy = data.get("data") or {}
            role = data.get("type") or role
            content = legacy.get("content") if isinstance(legacy, dict) else content

        role_map = {
            "human": "user",
            "user": "user",
            "ai": "assistant",
            "assistant": "assistant",
            "system": "system",
            "tool": "assistant",
        }
        normalized_role = role_map.get(str(role), "assistant")
        normalized_content = self._message_content_to_text(content or "")
        if not normalized_content:
            return None
        if normalized_role == "system":
            return None
        return {"role": normalized_role, "content": normalized_content}

    def _parse_model_answer(self, raw_answer: Any) -> Dict[str, Any]:
        """解析模型回答，补齐图片和链接 HTML，返回前端可直接渲染的字典。"""

        raw_answer = self._message_content_to_text(raw_answer)
        answer = raw_answer
        image_list: List[str] = []
        link_list: List[str] = []

        parsed_json = self._try_parse_json(raw_answer)
        if isinstance(parsed_json, dict):
            answer = str(parsed_json.get("answer", ""))
            image_list = self._normalize_str_list(parsed_json.get("image_list", []))
            link_list = self._normalize_str_list(parsed_json.get("link_list", []))

        image_list.extend(self._extract_images(answer))
        link_list.extend(self._extract_links(answer))
        image_list = self._dedupe(image_list)
        link_list = self._dedupe(link_list)

        final_answer = self._append_html(answer, image_list, link_list)
        return {
            "answer": final_answer,
            "image_list": image_list,
            "link_list": link_list,
            "raw_answer": raw_answer,
        }

    def _append_html(
        self,
        answer: str,
        image_list: Sequence[str],
        link_list: Sequence[str],
    ) -> str:
        """把图片路径和链接拼接成前端 v-html 可渲染的标签。"""

        html_parts = [answer]
        if "<img" not in answer:
            for image_url in image_list:
                safe_src = html.escape(image_url, quote=True)
                html_parts.append(
                    f'<img src="{safe_src}" alt="图片" '
                    'style="max-width: 100%; height: auto;" />'
                )

        if "<a " not in answer:
            for link_url in link_list:
                safe_href = html.escape(link_url, quote=True)
                html_parts.append(
                    f'<a href="{safe_href}" target="_blank" '
                    'style="color: blue; text-decoration: underline;">相关链接</a>'
                )
        return " ".join(part for part in html_parts if part)

    def _load_system_prompt(self, prompt_file_path: str) -> str:
        """读取系统提示词；文件不存在时使用内置商品助手提示词。"""

        if prompt_file_path and os.path.exists(prompt_file_path):
            with open(prompt_file_path, "r", encoding="utf-8") as file:
                return file.read().strip()

        return (
            "你是一个类似淘宝的中文商品搜索助手。"
            "你需要理解用户需求，回答要简洁、可信。"
            "如果你返回 JSON，请使用："
            '{"answer": "中文回答", "image_list": [], "link_list": []}。'
        )

    def _parse_sqlite_path(self, db_path: str) -> str:
        """解析 sqlite:///chat_history.db 形式的数据库路径。"""

        if not db_path.startswith("sqlite:///"):
            raise ValueError("db_path 必须使用 sqlite:/// 开头")

        path = db_path.replace("sqlite:///", "", 1)
        if path == ":memory:":
            return path

        path = os.path.abspath(path)
        directory = os.path.dirname(path)
        if directory:
            os.makedirs(directory, exist_ok=True)
        return path

    def _message_content_to_text(self, content: Any) -> str:
        """兼容字符串和多模态 block 列表形式的模型输出。"""

        if isinstance(content, str):
            return content
        if isinstance(content, list):
            parts = []
            for item in content:
                if isinstance(item, dict):
                    parts.append(str(item.get("text") or item.get("content") or ""))
                else:
                    parts.append(str(item))
            return "\n".join(part for part in parts if part)
        return str(content)

    def _try_parse_json(self, text: str) -> Any:
        """宽松解析 JSON；支持模型把 JSON 包在 ```json 代码块中。"""

        stripped = text.strip()
        if stripped.startswith("```"):
            stripped = re.sub(r"^```(?:json)?\s*", "", stripped, flags=re.IGNORECASE)
            stripped = re.sub(r"\s*```$", "", stripped)
        try:
            return json.loads(stripped)
        except json.JSONDecodeError:
            return None

    def _extract_images(self, text: str) -> List[str]:
        """从普通回答中提取常见图片 URL 或本地路径。"""

        pattern = r"(?:https?://[^\s\"'<>()]+|/[^\s\"'<>()]+)\.(?:png|jpg|jpeg|gif|webp)"
        return re.findall(pattern, text, flags=re.IGNORECASE)

    def _extract_links(self, text: str) -> List[str]:
        """从普通回答中提取 http/https 链接。"""

        pattern = r"https?://[^\s\"'<>()]+"
        return re.findall(pattern, text)

    def _normalize_str_list(self, value: Any) -> List[str]:
        """把模型返回的列表字段规范化为字符串列表。"""

        if value is None:
            return []
        if isinstance(value, str):
            return [value]
        if isinstance(value, list):
            return [str(item) for item in value if item]
        return []

    def _dedupe(self, values: Sequence[str]) -> List[str]:
        """保序去重，避免 HTML 标签重复追加。"""

        seen = set()
        result = []
        for value in values:
            if value not in seen:
                seen.add(value)
                result.append(value)
        return result

    def _relaxed_thresholds(self, distance_threshold: float) -> List[float]:
        """生成保序去重的放宽阈值列表。"""

        thresholds = [self._safe_float(distance_threshold, 0.9), 0.9, 1.5, 2.0]
        result = []
        for threshold in thresholds:
            if threshold not in result:
                result.append(threshold)
        return result

    def _safe_float(self, value: Any, default: float) -> float:
        """把工具参数安全转成浮点数。"""

        try:
            return float(value)
        except (TypeError, ValueError):
            return default

    def _safe_int(self, value: Any, default: int) -> int:
        """把工具参数安全转成整数，避免模型传入异常字符串。"""

        try:
            return int(value)
        except (TypeError, ValueError):
            return default


def init(
    api_key: str,
    prompt_file_path: str = "prompt.txt",
    db_path: str = "sqlite:///chat_history.db",
    model: str = "deepseek-chat",
    base_url: str = "https://api.deepseek.com",
) -> llmChat:
    """模块级初始化函数，符合 goals.txt 要求，返回 llmChat 实例。"""

    return llmChat(
        api_key=api_key,
        prompt_file_path=prompt_file_path,
        db_path=db_path,
        model=model,
        base_url=base_url,
    )


def search(
    id: str,
    search_function: Optional[Callable[[str], str]] = None,
) -> str:
    """模块级商品详情查询函数，方便普通后端接口直接调用。"""

    if search_function is None:
        return ""
    return search_function(str(id))
