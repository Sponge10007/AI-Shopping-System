import html
import json
import os
import re
import sqlite3
import threading
from collections import defaultdict
from datetime import datetime, timezone
from typing import Any, Callable, Dict, List, Optional, Sequence, TypedDict

from langchain_core.messages import (
    AIMessage,
    BaseMessage,
    HumanMessage,
    SystemMessage,
    ToolMessage,
    messages_from_dict,
    messages_to_dict,
)
from langchain_core.tools import StructuredTool
from langchain_openai import ChatOpenAI
from langgraph.graph import END, StateGraph

from core.labelDB import LabelDB


class ChatState(TypedDict):
    """LangGraph 节点之间传递的状态，只维护完整消息列表。"""

    messages: List[BaseMessage]


class llmChat:
    """面向网页后端的商品搜索聊天助手。

    本类负责三件事：
    1. 调用大模型完成多轮对话；
    2. 通过工具函数接入商品搜索；
    3. 将 HumanMessage / AIMessage / ToolMessage 原样持久化到 SQLite。
    """

    def __init__(
        self,
        api_key: str,
        prompt_file_path: str = "prompt.txt",
        db_path: str = "sqlite:///chat_history.db",
        model: str = "deepseek-v4-flash",
        base_url: str = "https://api.deepseek.com",
    ):
        self.api_key = api_key
        self.prompt_file_path = prompt_file_path
        self.db_file = self._parse_sqlite_path(db_path)
        self.system_prompt = self._load_system_prompt(prompt_file_path)
        self.model = model
        self.base_url = base_url

        # SQLite 连接允许跨线程使用，但真实写入仍通过锁保护。
        self.conn = sqlite3.connect(self.db_file, check_same_thread=False)
        self.conn.row_factory = sqlite3.Row
        self.db_lock = threading.RLock()
        self.session_locks = defaultdict(threading.RLock)
        self._init_db()

        # 外部商品详情查询函数：输入商品 id，返回包含该商品所有信息的字符串。
        self.search_function: Optional[Callable[[str], str]] = None
        # LabelDB 会加载 Hugging Face embedding 模型，网络不可用或模型未缓存时
        # 可能初始化失败。聊天本身不应因此不可用，所以只在商品搜索工具真正被
        # 调用时再懒加载。
        self.label_db: Optional[LabelDB] = None

        self.tools = self._build_tools()
        self.tool_map = {tool.name: tool for tool in self.tools}
        self.graph = self._build_graph()

    def set_search_function(
        self,
        search_function: Callable[[str], str],
    ) -> None:
        """注册业务侧商品详情查询函数。

        search_function 的参数约定为：
        id: LabelDB 返回的商品 ID。

        返回值为字符串，包含该商品的标题、价格、图片、链接、描述等完整信息。
        """

        self.search_function = search_function

    def chat(self, content: str, user_id: str, session_id: str) -> Dict[str, Any]:
        """同步对话接口。

        不同 session_id 使用不同锁，保证同一会话的消息顺序稳定；不同会话可以并发执行。
        """

        if not content or not str(content).strip():
            raise ValueError("content 不能为空")
        if not user_id:
            raise ValueError("user_id 不能为空")
        if not session_id:
            raise ValueError("session_id 不能为空")

        lock = self.session_locks[session_id]
        with lock:
            history = self._load_history(user_id=user_id, session_id=session_id)
            user_message = HumanMessage(content=content)
            messages = [SystemMessage(content=self.system_prompt), *history, user_message]

            result_state = self.graph.invoke({"messages": messages})
            result_messages = result_state["messages"]

            # 只保存本轮新增消息，避免重复写入历史消息。ToolMessage 会完整保存。
            new_messages = result_messages[len(messages) - 1 :]
            self._save_messages(
                user_id=user_id,
                session_id=session_id,
                messages=new_messages,
            )

            final_message = self._last_ai_message(result_messages)
            parsed = self._parse_model_answer(final_message)
            return parsed

    def delete_history(self, user_id: str, session_id: str) -> None:
        """删除指定用户在指定会话中的历史记忆。

        user_id 和 session_id 共同定位一段对话，避免不同用户或不同会话的记忆互相影响。
        """

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

    def search(
        self,
        id: str,
    ) -> str:
        """调用外部商品详情查询函数，返回单个商品的完整信息字符串。"""

        if self.search_function is None:
            return ""
        return self.search_function(str(id))

    def _search_products_for_ai(
        self,
        query: str,
        distance_threshold: float = 0.9,
        max_results: int = 3,
        recall_limit: int = 50,
    ) -> List[str]:
        """AI 工具专用搜索流程。

        先用 LabelDB.prod_search 获取候选商品 ID。这里 user_id 使用默认 "-1"，
        表示 AI 调用，不向用户标签库写入偏好。然后选前 1~3 个 ID，逐个调用
        外部 search(id) 函数获取商品完整信息。
        """

        limit = max(1, min(int(max_results), 3))
        # 模型有时会传入过严的 distance_threshold，导致手动搜索能命中、
        # 工具调用却返回空。这里做一层渐进放宽，课程原型阶段优先保证召回。
        product_ids = []
        for threshold in self._relaxed_thresholds(distance_threshold):
            product_ids = self._get_label_db().prod_search(
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

    def _collect_media_from_product_infos(
        self,
        product_infos: Sequence[str],
    ) -> Dict[str, List[str]]:
        """从商品详情字符串中提取图片和链接，方便模型和前端使用。"""

        text = "\n".join(product_infos)
        return {
            "image_list": self._dedupe(self._extract_images(text)),
            "link_list": self._dedupe(self._extract_links(text)),
        }

    def _build_tool_payload(self, product_infos: Sequence[str]) -> str:
        """把商品详情整理为 ToolMessage 中保存的 JSON 字符串。"""

        media = self._collect_media_from_product_infos(product_infos)
        return json.dumps(
            {
                "products": list(product_infos),
                "image_list": media["image_list"],
                "link_list": media["link_list"],
            },
            ensure_ascii=False,
        )

    def _safe_int(self, value: Any, default: int) -> int:
        """把工具参数安全转成整数，避免模型传入异常字符串。"""

        try:
            return int(value)
        except (TypeError, ValueError):
            return default

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

    def _build_tools(self) -> List[StructuredTool]:
        """构造 LangChain 工具，工具返回值会进入 ToolMessage 并被写入记忆库。"""

        def product_search(
            query: str,
            distance_threshold: float = 0.9,
            max_results: int = 3,
            recall_limit: int = 50,
        ) -> str:
            """搜索商品，返回 JSON 字符串，products 中包含商品完整信息文本。"""

            products = self.search_products(
                query=query,
                distance_threshold=distance_threshold,
                max_results=max_results,
                recall_limit=recall_limit,
            )
            return self._build_tool_payload(products)

        return [
            StructuredTool.from_function(
                func=product_search,
                name="product_search",
                description=(
                    "搜索淘宝风格商品。输入用户搜索词，返回前 1 到 3 个相关商品的完整信息。"
                ),
            )
        ]

    def _build_graph(self):
        """构建 LangGraph：model 节点负责思考，tools 节点负责执行工具。"""

        graph = StateGraph(ChatState)
        graph.add_node("model", self._call_model)
        graph.add_node("tools", self._run_tools)
        graph.set_entry_point("model")
        graph.add_conditional_edges(
            "model",
            self._should_continue,
            {"tools": "tools", END: END},
        )
        graph.add_edge("tools", "model")
        return graph.compile()

    def _call_model(self, state: ChatState) -> ChatState:
        """模型节点：让大模型基于历史消息决定直接回答或调用工具。"""

        # ChatOpenAI 底层使用 httpx/openai client。部分环境中长时间复用后会出现
        # "Cannot send a request, as the client has been closed."，因此这里为每次
        # 模型调用创建新的轻量适配器，避免复用已关闭的 HTTP client。
        llm_with_tools = self._new_llm_with_tools()
        response = llm_with_tools.invoke(state["messages"])
        return {"messages": [*state["messages"], response]}

    def _new_llm_with_tools(self):
        """创建一次性模型调用对象，避免复用被关闭的底层 HTTP client。"""

        llm = ChatOpenAI(
            api_key=self.api_key,
            base_url=self.base_url,
            model=self.model,
            temperature=0.2,
        )
        return llm.bind_tools(self.tools)

    def _get_label_db(self) -> LabelDB:
        """懒加载商品向量库，避免普通聊天被 embedding 模型下载问题阻断。"""

        if self.label_db is None:
            self.label_db = LabelDB()
        return self.label_db

    def _run_tools(self, state: ChatState) -> ChatState:
        """工具节点：执行 AIMessage 中的全部 tool_calls，并生成完整 ToolMessage。"""

        last_message = state["messages"][-1]
        tool_messages: List[ToolMessage] = []
        for tool_call in getattr(last_message, "tool_calls", []) or []:
            name = tool_call.get("name")
            args = tool_call.get("args") or {}
            tool_call_id = tool_call.get("id")
            try:
                tool_result = self.tool_map[name].invoke(args)
            except Exception as exc:  # 工具错误也写入 ToolMessage，方便后续追踪
                tool_result = json.dumps(
                    {"error": str(exc), "tool": name, "args": args},
                    ensure_ascii=False,
                )

            tool_messages.append(
                ToolMessage(
                    content=str(tool_result),
                    tool_call_id=tool_call_id,
                    name=name,
                )
            )
        return {"messages": [*state["messages"], *tool_messages]}

    def _should_continue(self, state: ChatState) -> str:
        """路由函数：模型请求工具则进入 tools，否则结束图执行。"""

        last_message = state["messages"][-1]
        if getattr(last_message, "tool_calls", None):
            return "tools"
        return END

    def _init_db(self) -> None:
        """创建普通 SQLite 聊天记忆表。message_json 保存 LangChain 原始消息。"""

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
        messages: Sequence[BaseMessage],
    ) -> None:
        """批量保存消息，完整保留 additional_kwargs、tool_calls 等字段。"""

        if not messages:
            return

        now = datetime.now(timezone.utc).isoformat()
        rows = []
        for message in messages:
            message_json = json.dumps(messages_to_dict([message])[0], ensure_ascii=False)
            rows.append(
                (
                    user_id,
                    session_id,
                    message.type,
                    message_json,
                    now,
                )
            )

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

    def _load_history(self, user_id: str, session_id: str) -> List[BaseMessage]:
        """按用户和会话读取历史，确保不同用户、不同对话互相隔离。"""

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

        raw_messages = [json.loads(row["message_json"]) for row in records]
        return messages_from_dict(raw_messages) if raw_messages else []

    def _parse_model_answer(self, message: Optional[AIMessage]) -> Dict[str, Any]:
        """解析模型回答，补齐图片和链接 HTML，返回前端可直接渲染的字典。"""

        raw_answer = self._message_content_to_text(message.content if message else "")
        answer = raw_answer
        image_list: List[str] = []
        link_list: List[str] = []

        # 推荐模型返回 JSON：{"answer": "...", "image_list": [...], "link_list": [...]}。
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

    def _last_ai_message(self, messages: Sequence[BaseMessage]) -> Optional[AIMessage]:
        """从消息列表尾部寻找最终 AI 回复。"""

        for message in reversed(messages):
            if isinstance(message, AIMessage):
                return message
        return None

    def _load_system_prompt(self, prompt_file_path: str) -> str:
        """读取系统提示词；文件不存在时使用内置商品助手提示词。"""

        if prompt_file_path and os.path.exists(prompt_file_path):
            with open(prompt_file_path, "r", encoding="utf-8") as file:
                return file.read().strip()

        return (
            "你是一个类似淘宝的中文商品搜索助手。"
            "你需要理解用户需求，必要时调用 product_search 工具搜索商品。"
            "回答要简洁、可信，并尽量给出商品图片 image_list 和链接 link_list。"
            "如果工具结果中包含图片或链接，请把它们整理到 JSON 字段中："
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


def init(
    api_key: str,
    prompt_file_path: str = "prompt.txt",
    db_path: str = "sqlite:///chat_history.db",
    model: str = "deepseek-v4-flash",
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
    """模块级商品详情查询函数，方便普通后端接口直接调用。

    调用方传入 search_function 时转发查询；未传入时返回空字符串。
    """

    if search_function is None:
        return ""
    return search_function(str(id))
