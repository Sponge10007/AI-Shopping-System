import base64
import json
import mimetypes
import os
import re
from typing import Any, Dict, List, Optional, TYPE_CHECKING

from langchain_core.messages import HumanMessage
from langchain_openai import ChatOpenAI

if TYPE_CHECKING:
    from core.labelDB import LabelDB


class ImageAI:
    """视觉搜索助手：图片理解 -> 商品关键词 -> 向量检索。

    工作流程：
    1. 使用支持图片输入的模型识别图片；
    2. 将图片浓缩为 10 个以内商品搜索关键词；
    3. 把关键词拼成文本 query；
    4. 调用 LabelDB.prod_search 做语义搜索，返回商品 ID。

    这里使用 OpenAI-compatible 的 ChatOpenAI 适配器，是为了能灵活接入不同视觉模型。
    """

    def __init__(
        self,
        api_key: str,
        model: str,
        base_url: str,
        label_db: Optional["LabelDB"] = None,
    ):
        if not api_key:
            raise ValueError("视觉模型 api_key 不能为空")
        if not model:
            raise ValueError("视觉模型 model 不能为空")
        if not base_url:
            raise ValueError("视觉模型 base_url 不能为空")

        if label_db is None:
            from core.labelDB import LabelDB

            label_db = LabelDB()
        self.label_db = label_db
        self.vision_llm = ChatOpenAI(
            api_key=api_key,
            base_url=base_url,
            model=model,
            temperature=0.1,
            timeout=15,
            max_retries=0,
        )

    def image_search(
        self,
        user_id: str,
        image_path_or_url: str,
        limit: int = 20,
        distance_threshold: float = 0.9,
    ) -> Dict[str, Any]:
        """根据图片搜索商品。

        user_id:
            真实用户 ID。这里会传给 LabelDB.prod_search，用于记录用户标签；
            如果业务希望不记录标签，可以传 "-1"。
        image_path_or_url:
            图片 URL、本地绝对路径或本地相对路径。
        limit:
            返回的最大商品 ID 数量，同时也作为 LabelDB 的召回上限。
        distance_threshold:
            ChromaDB 距离阈值，越小越严格。
        """

        user_id = str(user_id)
        image_path_or_url = str(image_path_or_url).strip()
        if not image_path_or_url:
            raise ValueError("image_path_or_url 不能为空")

        limit = max(1, int(limit))
        keywords = self.extract_keywords(image_path_or_url)
        query = " ".join(keywords)

        product_ids = self.label_db.prod_search(
            user_id=user_id,
            query=query,
            distance_threshold=distance_threshold,
            limit=limit,
        )

        return {
            "keywords": keywords,
            "query": query,
            "product_ids": product_ids[:limit],
        }

    def extract_keywords(self, image_path_or_url: str) -> List[str]:
        """调用视觉模型，把图片转成 10 个以内关键词。

        关键词主要覆盖四类信息：
        - 名字：可能的商品名称或品类；
        - 外观：颜色、形状、材质、风格；
        - 用途：适用场景和功能；
        - 特征：品牌元素、规格、细节。
        """

        image_url = self._to_model_image_url(image_path_or_url)
        prompt = (
            "你是电商商品视觉搜索助手。请观察图片，提取不超过10个中文关键词，"
            "主要包含：商品名字或品类、外观、用途、显著特征。"
            "只返回 JSON，格式为：{\"keywords\":[\"关键词1\",\"关键词2\"]}。"
            "不要返回解释，不要使用 Markdown。"
        )

        response = self.vision_llm.invoke(
            [
                HumanMessage(
                    content=[
                        {"type": "text", "text": prompt},
                        {"type": "image_url", "image_url": {"url": image_url}},
                    ]
                )
            ]
        )

        text = self._message_content_to_text(response.content)
        keywords = self._parse_keywords(text)
        if not keywords:
            raise ValueError(f"视觉模型未返回有效关键词: {text}")
        return keywords[:10]

    def _to_model_image_url(self, image_path_or_url: str) -> str:
        """把 URL 或本地图片路径转换成模型可读取的 image_url。

        - http/https/data URL 可以直接传给模型；
        - 本地文件会读取为 base64，并组装为 data URL。
        """

        if re.match(r"^(https?://|data:image/)", image_path_or_url, re.IGNORECASE):
            return image_path_or_url

        path = os.path.abspath(image_path_or_url)
        if not os.path.exists(path):
            raise FileNotFoundError(f"图片文件不存在: {image_path_or_url}")

        mime_type = mimetypes.guess_type(path)[0] or "image/jpeg"
        with open(path, "rb") as file:
            encoded = base64.b64encode(file.read()).decode("ascii")
        return f"data:{mime_type};base64,{encoded}"

    def _parse_keywords(self, text: str) -> List[str]:
        """解析视觉模型输出，优先解析 JSON"""

        cleaned = text.strip()
        if cleaned.startswith("```"):
            cleaned = re.sub(r"^```(?:json)?\s*", "", cleaned, flags=re.IGNORECASE)
            cleaned = re.sub(r"\s*```$", "", cleaned)

        try:
            data = json.loads(cleaned)
            if isinstance(data, dict):
                return self._normalize_keywords(data.get("keywords", []))
            if isinstance(data, list):
                return self._normalize_keywords(data)
        except json.JSONDecodeError:
            pass

        parts = re.split(r"[，,、\n;；]+", cleaned)
        return self._normalize_keywords(parts)

    def _normalize_keywords(self, value: Any) -> List[str]:
        """清洗关键词：转字符串、去空、保序去重、限制长度。"""

        if isinstance(value, str):
            value = [value]
        if not isinstance(value, list):
            return []

        seen = set()
        keywords = []
        for item in value:
            keyword = str(item).strip()
            if not keyword or keyword in seen:
                continue
            seen.add(keyword)
            keywords.append(keyword)
            if len(keywords) >= 10:
                break
        return keywords

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


def image_search(
    user_id: str,
    image_path_or_url: str,
    limit: int = 20,
    distance_threshold: float = 0.9,
    api_key: Optional[str] = None,
    model: Optional[str] = None,
    base_url: Optional[str] = None,
) -> Dict[str, Any]:
    """直接调用视觉搜索。"""

    image_ai = ImageAI(
        api_key=api_key or os.getenv("IMAGE_AI_API_KEY") or os.getenv("DEEPSEEK_API_KEY", ""),
        model=model or os.getenv("IMAGE_AI_MODEL", "deepseek-chat"),
        base_url=base_url or os.getenv("IMAGE_AI_BASE_URL") or os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com"),
    )
    return image_ai.image_search(
        user_id=user_id,
        image_path_or_url=image_path_or_url,
        limit=limit,
        distance_threshold=distance_threshold,
    )
