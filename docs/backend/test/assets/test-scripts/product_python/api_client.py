"""
API 客户端 — 封装 HTTP 请求，统一处理响应解析和错误处理
完全替代 PowerShell 的 Invoke-Api 函数，避免 PowerShell 5 的异常处理缺陷
"""
import json
import time
import requests
from typing import Any, Optional


class ApiResponse:
    """统一的 API 响应封装"""
    def __init__(self, success: bool, status_code: int, response: Any):
        self.success = success          # HTTP 请求是否成功（非异常）
        self.status_code = status_code  # HTTP 状态码
        self.response = response        # 解析后的 JSON 响应体（dict 或 None）

    def __repr__(self):
        return f"ApiResponse(success={self.success}, status_code={self.status_code}, response={self.response})"


class ApiClient:
    """
    HTTP API 客户端
    使用 requests 库，异常处理稳定可靠，兼容所有 Python 版本
    """

    def __init__(self, base_url: str = "http://localhost:8080"):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()
        self.session.headers.update({
            "Content-Type": "application/json"
        })

    def _request(self, method: str, path: str, body: Any = None,
                 headers: Optional[dict] = None) -> ApiResponse:
        """
        发送 HTTP 请求并解析响应

        Args:
            method: HTTP 方法 (GET, POST, PATCH, DELETE)
            path: 请求路径（如 /api/v1/auth/login）
            body: 请求体（dict 或 None）
            headers: 额外的请求头

        Returns:
            ApiResponse 对象
        """
        url = f"{self.base_url}{path}"
        req_headers = {}
        if headers:
            req_headers.update(headers)

        try:
            if method.upper() in ("GET", "DELETE"):
                resp = self.session.request(
                    method=method.upper(),
                    url=url,
                    headers=req_headers,
                    timeout=30
                )
            else:
                resp = self.session.request(
                    method=method.upper(),
                    url=url,
                    headers=req_headers,
                    json=body,
                    timeout=30
                )

            status_code = resp.status_code

            # 尝试解析 JSON 响应体
            response_data = None
            if resp.text:
                try:
                    response_data = resp.json()
                except json.JSONDecodeError:
                    response_data = {"raw": resp.text}

            # HTTP 状态码 2xx 视为成功
            is_success = 200 <= status_code < 300

            return ApiResponse(
                success=is_success,
                status_code=status_code,
                response=response_data
            )

        except requests.exceptions.ConnectionError as e:
            return ApiResponse(
                success=False,
                status_code=0,
                response={"error": f"连接失败: {str(e)}"}
            )
        except requests.exceptions.Timeout as e:
            return ApiResponse(
                success=False,
                status_code=0,
                response={"error": f"请求超时: {str(e)}"}
            )
        except requests.exceptions.RequestException as e:
            return ApiResponse(
                success=False,
                status_code=0,
                response={"error": f"请求异常: {str(e)}"}
            )

    def get(self, path: str, headers: Optional[dict] = None) -> ApiResponse:
        """发送 GET 请求"""
        return self._request("GET", path, headers=headers)

    def post(self, path: str, body: Any = None, headers: Optional[dict] = None) -> ApiResponse:
        """发送 POST 请求"""
        return self._request("POST", path, body=body, headers=headers)

    def patch(self, path: str, body: Any = None, headers: Optional[dict] = None) -> ApiResponse:
        """发送 PATCH 请求"""
        return self._request("PATCH", path, body=body, headers=headers)

    def delete(self, path: str, headers: Optional[dict] = None) -> ApiResponse:
        """发送 DELETE 请求"""
        return self._request("DELETE", path, headers=headers)

    def register_user(self, username: str, phone: str, password: str,
                      role: str) -> ApiResponse:
        """注册用户"""
        return self.post("/api/v1/auth/register", {
            "username": username,
            "phone": phone,
            "password": password,
            "role": role
        })

    def login(self, account: str, password: str) -> ApiResponse:
        """登录并返回 ApiResponse"""
        return self.post("/api/v1/auth/login", {
            "account": account,
            "password": password
        })

    def get_access_token(self, account: str, password: str) -> Optional[str]:
        """
        登录并获取 access_token
        返回 token 字符串，失败返回 None
        """
        resp = self.login(account, password)
        if resp.success and resp.response and resp.response.get("success"):
            data = resp.response.get("data")
            if data:
                # 后端返回 camelCase: accessToken
                return data.get("accessToken")
        return None

    def make_auth_headers(self, token: str) -> dict:
        """生成带 Authorization 的请求头"""
        return {"Authorization": f"Bearer {token}"}

    # ============ 健康检查与等待就绪 ============

    def health_check(self) -> bool:
        """检查后端健康状态"""
        try:
            resp = self.get("/actuator/health")
            return resp.success
        except Exception:
            return False

    def wait_for_ready(self, timeout: int = 60, interval: int = 2) -> bool:
        """
        等待后端服务就绪

        Args:
            timeout: 最大等待时间（秒）
            interval: 轮询间隔（秒）

        Returns:
            bool: 后端是否就绪
        """
        print(f"等待后端服务就绪（超时 {timeout} 秒）...")
        start_time = time.time()

        while time.time() - start_time < timeout:
            if self.health_check():
                elapsed = int(time.time() - start_time)
                print(f"后端已就绪！耗时 {elapsed} 秒")
                return True

            elapsed = int(time.time() - start_time)
            if elapsed % 10 == 0 and elapsed > 0:
                print(f"  已等待 {elapsed} 秒...")

            time.sleep(interval)

        print(f"后端启动超时（{timeout} 秒）")
        return False
