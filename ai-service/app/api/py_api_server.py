import json
import os
import traceback
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Dict, Optional
from urllib.parse import parse_qs, unquote, urlparse
from urllib import request as url_request
from urllib.error import HTTPError, URLError

from core.image_ai import ImageAI
from core.labelDB import LabelDB
from core.llmchat import init as init_llm_chat


"""
Python 侧 HTTP 接口服务。

为什么使用 HTTP：
1. Java 和 Python 是两个不同运行时，直接互相 import 不现实；
2. HTTP/JSON 是最简单、最稳定的跨语言通信方式；
3. Java 后端可以用 Spring RestTemplate、WebClient、OkHttp 等任意 HTTP 客户端调用；
4. Python 侧仍然保留 core/labelDB.py 和 core/llmchat.py 业务代码，本文件只负责把函数包装成接口。

启动示例：
    set DEEPSEEK_API_KEY=你的key
    set DEEPSEEK_MODEL=deepseek-v4-flash
    set JAVA_PRODUCT_SEARCH_URL=http://127.0.0.1:8080/product/searchById
    python -m api.py_api_server

其中 JAVA_PRODUCT_SEARCH_URL 是 Java 侧提供的商品详情查询接口。
core/llmchat.py 里的工具会先用 LabelDB 搜索商品 ID，再调用这里配置的 Java 接口：
    POST JAVA_PRODUCT_SEARCH_URL
    {"id": "商品ID"}

Java 返回值可以是纯文本，也可以是 JSON：
    "商品名xx,价格xx,网页链接:xxx"
或：
    {"data": "商品名xx,价格xx,网页链接:xxx"}

对 Java 暴露的接口路径：
    POST   /internal/v1/ai/products/{product_id}/index
    DELETE /internal/v1/ai/products/{product_id}/index
    POST   /internal/v1/ai/search/products
    POST   /internal/v1/ai/search/image
    GET    /internal/v1/ai/users/{user_id}/recommendations?maxnum=5
    POST   /internal/v1/ai/chat/messages
    DELETE /internal/v1/ai/chat/history
"""


def load_env_file():
    """加载 ai-service/.env，避免每个新终端都要手动 source。

    已存在的系统环境变量优先级更高，不会被 .env 覆盖。这样线上部署时仍可以
    通过真正的环境变量覆盖本地开发配置。
    """

    candidate_paths = [
        Path.cwd() / ".env",
        Path.cwd().parent / ".env",
        Path(__file__).resolve().parents[2] / ".env",
    ]
    env_path = next((path for path in candidate_paths if path.exists()), None)
    if env_path is None:
        return

    for raw_line in env_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        if key and key not in os.environ:
            os.environ[key] = value


load_env_file()

HOST = os.getenv("PY_API_HOST", "127.0.0.1")
PORT = int(os.getenv("PY_API_PORT", "9000"))
DEEPSEEK_API_KEY = os.getenv("DEEPSEEK_API_KEY", "")
DEEPSEEK_MODEL = os.getenv("DEEPSEEK_MODEL", "deepseek-v4-flash")
DEEPSEEK_BASE_URL = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com")
IMAGE_AI_API_KEY = os.getenv("IMAGE_AI_API_KEY", DEEPSEEK_API_KEY)
IMAGE_AI_MODEL = os.getenv("IMAGE_AI_MODEL", DEEPSEEK_MODEL)
IMAGE_AI_BASE_URL = os.getenv("IMAGE_AI_BASE_URL", DEEPSEEK_BASE_URL)
PROMPT_FILE_PATH = os.getenv("PROMPT_FILE_PATH", "prompt.txt")
CHAT_DB_PATH = os.getenv("CHAT_DB_PATH", "sqlite:///chat_history.db")
JAVA_PRODUCT_SEARCH_URL = os.getenv("JAVA_PRODUCT_SEARCH_URL", "")
BACKEND_INTERNAL_BASE_URL = os.getenv("BACKEND_INTERNAL_BASE_URL", "http://127.0.0.1:8080/internal/v1")


# LabelDB 会加载 Embedding 模型，成本较高；这里使用懒加载，避免导入 API 模块时就
# 触发模型下载/加载。第一次真正访问向量接口时才初始化，后续复用同一个单例。
label_db = None

# LLM 聊天对象比较重，并且需要 DEEPSEEK_API_KEY；使用懒加载，只有 /chat 被调用时才初始化。
chat_app = None

# 视觉搜索对象也需要模型配置，懒加载，只有 /internal/v1/ai/search/image 被调用时初始化。
image_ai_app = None


def get_label_db():
    """懒加载 LabelDB，避免 API 模块 import 阶段加载向量模型。"""

    global label_db
    if label_db is None:
        label_db = LabelDB()
    return label_db


def get_chat_app():
    """懒加载 llmChat 实例，并把 Java 商品详情查询函数注入给 LLM 工具使用。"""

    global chat_app
    if chat_app is None:
        if not DEEPSEEK_API_KEY:
            raise RuntimeError("缺少 DEEPSEEK_API_KEY 环境变量，无法初始化 llmChat。")

        chat_app = init_llm_chat(
            api_key=DEEPSEEK_API_KEY,
            prompt_file_path=PROMPT_FILE_PATH,
            db_path=CHAT_DB_PATH,
            model=DEEPSEEK_MODEL,
            base_url=DEEPSEEK_BASE_URL,
        )

        # core/llmchat.py 的商品工具需要 search(str id) -> str。
        # 这里把它映射成一次 Java HTTP 调用，让 Java 继续负责普通商品数据库查询。
        chat_app.set_search_function(search_product_detail_from_java)

    return chat_app


def get_image_ai_app():
    """懒加载 ImageAI 实例，用于图片理解和视觉搜索。"""

    global image_ai_app
    if image_ai_app is None:
        if not IMAGE_AI_API_KEY:
            raise RuntimeError("缺少 IMAGE_AI_API_KEY 或 DEEPSEEK_API_KEY，无法初始化 ImageAI。")

        image_ai_app = ImageAI(
            api_key=IMAGE_AI_API_KEY,
            model=IMAGE_AI_MODEL,
            base_url=IMAGE_AI_BASE_URL,
            label_db=get_label_db(),
        )
    return image_ai_app


def search_product_detail_from_java(product_id: str) -> str:
    """调用 Java 后端，按商品 ID 查询完整商品信息。

    优先兼容旧 POST 接口，接收：
        {"id": "商品ID"}

    也兼容当前 Java 后端已实现的内部接口：
        GET /internal/v1/products/{product_id}/ai-summary

    返回：
        纯文本字符串；或 JSON 中带 data/result/content/detail 任一字段。
    """

    if JAVA_PRODUCT_SEARCH_URL:
        return fetch_product_detail_by_legacy_post(product_id)

    if BACKEND_INTERNAL_BASE_URL:
        return fetch_product_detail_by_internal_summary(product_id)

    return ""


def fetch_product_detail_by_legacy_post(product_id: str) -> str:
    """调用旧的 POST 商品详情接口。"""

    payload = json.dumps({"id": str(product_id)}, ensure_ascii=False).encode("utf-8")
    request = url_request.Request(
        JAVA_PRODUCT_SEARCH_URL,
        data=payload,
        headers={"Content-Type": "application/json; charset=utf-8"},
        method="POST",
    )

    try:
        with url_request.urlopen(request, timeout=5) as response:
            raw_body = response.read().decode("utf-8")
    except (HTTPError, URLError, TimeoutError) as exc:
        # 商品详情查询失败时，不让整个聊天失败；把错误信息作为空结果处理。
        print(f"[Warn] Java 商品详情接口调用失败: {exc}")
        return ""

    return extract_product_detail(raw_body)


def fetch_product_detail_by_internal_summary(product_id: str) -> str:
    """调用当前 Java 后端内部商品摘要接口。"""

    base_url = BACKEND_INTERNAL_BASE_URL.rstrip("/")
    url = f"{base_url}/products/{product_id}/ai-summary"
    request = url_request.Request(url, method="GET")

    try:
        with url_request.urlopen(request, timeout=5) as response:
            raw_body = response.read().decode("utf-8")
    except (HTTPError, URLError, TimeoutError) as exc:
        print(f"[Warn] Java 商品摘要接口调用失败: {exc}")
        return ""

    return extract_product_detail(raw_body)


def extract_product_detail(raw_body: str) -> str:
    """从纯文本、旧 JSON 或 Java ApiResponse 中提取商品详情字符串。"""

    try:
        data = json.loads(raw_body)
    except json.JSONDecodeError:
        return raw_body

    if isinstance(data, str):
        return data
    if isinstance(data, dict):
        nested_data = data.get("data")
        if isinstance(nested_data, dict):
            for key in ("summary_text", "summaryText", "summary", "detail", "content"):
                value = nested_data.get(key)
                if isinstance(value, str):
                    return value
            return json.dumps(nested_data, ensure_ascii=False)
        for key in ("data", "result", "content", "detail"):
            value = data.get(key)
            if isinstance(value, str):
                return value
        return json.dumps(data, ensure_ascii=False)

    return str(data)


def json_response(handler: BaseHTTPRequestHandler, status: int, body: Dict[str, Any]):
    """统一返回 JSON，Java 侧只需要按 UTF-8 JSON 解析即可。"""

    encoded = json.dumps(body, ensure_ascii=False).encode("utf-8")
    handler.send_response(status)
    handler.send_header("Content-Type", "application/json; charset=utf-8")
    handler.send_header("Content-Length", str(len(encoded)))
    handler.end_headers()
    handler.wfile.write(encoded)


def read_json_body(handler: BaseHTTPRequestHandler) -> Dict[str, Any]:
    """读取请求体并解析 JSON；没有请求体时返回空字典。"""

    content_length = int(handler.headers.get("Content-Length", "0"))
    if content_length <= 0:
        return {}

    raw_body = handler.rfile.read(content_length).decode("utf-8")
    if not raw_body.strip():
        return {}
    return json.loads(raw_body)


def get_query_params(path: str) -> Dict[str, str]:
    """解析 URL query 参数，只取每个参数的第一个值。"""

    query = parse_qs(urlparse(path).query)
    return {key: values[0] for key, values in query.items() if values}


def clean_path(path: str) -> str:
    """去掉 query string，只保留纯路径用于路由匹配。"""

    return urlparse(path).path


def require_str(data: Dict[str, Any], key: str) -> str:
    """读取必填字符串参数，缺失时抛出清晰错误，方便 Java 调试。"""

    value = data.get(key)
    if value is None or str(value).strip() == "":
        raise ValueError(f"缺少必填参数: {key}")
    return str(value)


class PythonApiHandler(BaseHTTPRequestHandler):
    """把 readme.txt 中的 Python 函数包装为 Java 可调用的 HTTP 接口。"""

    def do_GET(self):
        """处理健康检查和用户画像推荐接口。"""

        try:
            path = clean_path(self.path)
            if path == "/health":
                json_response(self, 200, {"ok": True, "service": "python-api"})
                return

            result = self.route_get(path, get_query_params(self.path))
            json_response(self, 200, {"ok": True, "data": result})
        except ValueError as exc:
            json_response(self, 400, {"ok": False, "error": str(exc)})
        except Exception as exc:
            traceback.print_exc()
            json_response(self, 500, {"ok": False, "error": str(exc)})

    def do_POST(self):
        """处理新增/更新商品索引、文本语义搜索和 AI 对话接口。"""

        try:
            data = read_json_body(self)
            result = self.route_post(clean_path(self.path), data)
            json_response(self, 200, {"ok": True, "data": result})
        except ValueError as exc:
            json_response(self, 400, {"ok": False, "error": str(exc)})
        except Exception as exc:
            # 生产环境可以把 traceback 写入日志系统；这里打印方便课程项目调试。
            traceback.print_exc()
            json_response(self, 500, {"ok": False, "error": str(exc)})

    def do_DELETE(self):
        """处理删除商品索引和清除对话历史接口。"""

        try:
            data = read_json_body(self)
            query = get_query_params(self.path)
            result = self.route_delete(clean_path(self.path), data, query)
            json_response(self, 200, {"ok": True, "data": result})
        except ValueError as exc:
            json_response(self, 400, {"ok": False, "error": str(exc)})
        except Exception as exc:
            traceback.print_exc()
            json_response(self, 500, {"ok": False, "error": str(exc)})

    def route_get(self, path: str, query: Dict[str, str]) -> Optional[Any]:
        """根据 GET URL 分发到具体 Python 函数。"""

        user_prefix = "/internal/v1/ai/users/"
        user_suffix = "/recommendations"
        if path.startswith(user_prefix) and path.endswith(user_suffix):
            user_id = unquote(path[len(user_prefix) : -len(user_suffix)])
            maxnum = int(query.get("maxnum", 5))
            return self.search_user_recommendations(user_id=user_id, maxnum=maxnum)
        raise ValueError(f"未知 GET 接口: {path}")

    def route_post(self, path: str, data: Dict[str, Any]) -> Optional[Any]:
        """根据 URL 分发到具体 Python 函数。"""

        product_id = self.parse_product_index_path(path)
        if product_id is not None:
            return self.add_product(product_id=product_id, data=data)
        if path == "/internal/v1/ai/search/products":
            return self.search_product_ids(data)
        if path == "/internal/v1/ai/search/image":
            return self.search_products_by_image(data)
        if path == "/internal/v1/ai/chat/messages":
            return self.chat(data)
        raise ValueError(f"未知 POST 接口: {path}")

    def route_delete(
        self,
        path: str,
        data: Dict[str, Any],
        query: Dict[str, str],
    ) -> Optional[Any]:
        """根据 DELETE URL 分发到具体 Python 函数。"""

        product_id = self.parse_product_index_path(path)
        if product_id is not None:
            return self.delete_product(product_id=product_id)
        if path == "/internal/v1/ai/chat/history":
            return self.delete_chat_history(data=data, query=query)
        raise ValueError(f"未知 DELETE 接口: {path}")

    def parse_product_index_path(self, path: str) -> Optional[str]:
        """解析 /internal/v1/ai/products/{product_id}/index 中的商品 ID。"""

        prefix = "/internal/v1/ai/products/"
        suffix = "/index"
        if not path.startswith(prefix) or not path.endswith(suffix):
            return None
        product_id = unquote(path[len(prefix) : -len(suffix)])
        if not product_id:
            raise ValueError("商品 ID 不能为空")
        return product_id

    def add_product(self, product_id: str, data: Dict[str, Any]) -> Dict[str, str]:
        """对应 labelDB.prod_add_product(str product_id, str description)。"""

        description = require_str(data, "description")
        get_label_db().prod_add_product(product_id, description)
        return {"message": "商品向量写入任务已提交"}

    def delete_product(self, product_id: str) -> Dict[str, str]:
        """对应 labelDB.prod_delete_product(str product_id)。"""

        get_label_db().prod_delete_product(product_id)
        return {"message": "商品向量已删除"}

    def search_product_ids(self, data: Dict[str, Any]):
        """对应 labelDB.prod_search，返回匹配商品 ID 列表。"""

        query = require_str(data, "query")
        user_id = str(data.get("user_id", "-1"))
        distance_threshold = float(data.get("distance_threshold", 0.9))
        limit = int(data.get("limit", LabelDB.DEFAULT_SEARCH_LIMIT))

        return get_label_db().prod_search(
            user_id=user_id,
            query=query,
            distance_threshold=distance_threshold,
            limit=limit,
        )

    def search_products_by_image(self, data: Dict[str, Any]):
        """对应 ImageAI.image_search，先识图提关键词，再用 LabelDB 搜索商品。"""

        user_id = str(data.get("user_id", "-1"))
        image_path_or_url = (
            data.get("image_path_or_url")
            or data.get("image_url")
            or data.get("image_path")
        )
        if image_path_or_url is None or str(image_path_or_url).strip() == "":
            raise ValueError("缺少必填参数: image_path_or_url")

        limit = int(data.get("limit", 20))
        distance_threshold = float(data.get("distance_threshold", 0.9))
        return get_image_ai_app().image_search(
            user_id=user_id,
            image_path_or_url=str(image_path_or_url),
            limit=limit,
            distance_threshold=distance_threshold,
        )

    def search_user_recommendations(self, user_id: str, maxnum: int = 5):
        """对应 labelDB.user_search(str user_id, int maxnum=5)。"""

        if not user_id:
            raise ValueError("user_id 不能为空")
        return get_label_db().user_search(user_id=user_id, maxnum=maxnum)

    def chat(self, data: Dict[str, Any]):
        """对应 llmChat.chat(str content, str user_id, str session_id)。"""

        content = require_str(data, "content")
        user_id = require_str(data, "user_id")
        session_id = require_str(data, "session_id")
        return get_chat_app().chat(content=content, user_id=user_id, session_id=session_id)

    def delete_chat_history(
        self,
        data: Dict[str, Any],
        query: Dict[str, str],
    ) -> Dict[str, str]:
        """对应 llmChat.delete_history(str user_id, str session_id)。"""

        # DELETE 请求有些 Java 客户端不方便带 body，因此同时支持 query 参数：
        # /internal/v1/ai/chat/history?user_id=u1&session_id=s1
        merged = {**query, **data}
        user_id = require_str(merged, "user_id")
        session_id = require_str(merged, "session_id")
        get_chat_app().delete_history(user_id=user_id, session_id=session_id)
        return {"message": "聊天历史已删除"}

    def log_message(self, format: str, *args):
        """减少默认 HTTP 日志噪音；需要调试时可以改成 super().log_message。"""

        return


def main():
    """启动多线程 HTTP 服务，支持 Java 后端并发调用。"""

    server = ThreadingHTTPServer((HOST, PORT), PythonApiHandler)
    print(f"Python API 服务已启动: http://{HOST}:{PORT}")
    print("健康检查: GET /health")
    server.serve_forever()


if __name__ == "__main__":
    main()
