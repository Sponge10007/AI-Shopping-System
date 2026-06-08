"""
通用 API 客户端
封装 HTTP 请求，统一处理响应解析和错误处理
"""
import json
import requests


class ApiResponse:
    """统一 API 响应封装"""

    def __init__(self, success: bool, status_code: int, response):
        self.success = success
        self.status_code = status_code
        self.response = response

    def __repr__(self):
        return f"ApiResponse(success={self.success}, status_code={self.status_code})"


class ApiClient:
    """
    HTTP API 客户端
    使用 requests 库，4xx/5xx 不抛异常，通过 status_code 和 json() 直接获取
    """

    def __init__(self, base_url: str = "http://localhost:8080"):
        self.base_url = base_url.rstrip("/")
        self.session = requests.Session()

    def _request(self, method, path, body=None, headers=None) -> ApiResponse:
        """发送 HTTP 请求并解析响应"""
        url = f"{self.base_url}{path}"

        merged_headers = {"Content-Type": "application/json"}
        if headers:
            merged_headers.update(headers)

        try:
            resp = self.session.request(
                method=method.upper(),
                url=url,
                headers=merged_headers,
                json=body,
                timeout=30
            )

            response_data = None
            if resp.text:
                try:
                    response_data = resp.json()
                except json.JSONDecodeError:
                    response_data = {"raw": resp.text}

            return ApiResponse(
                success=200 <= resp.status_code < 300,
                status_code=resp.status_code,
                response=response_data
            )

        except requests.exceptions.ConnectionError as e:
            return ApiResponse(success=False, status_code=0,
                               response={"error": f"连接失败: {str(e)}"})
        except requests.exceptions.Timeout as e:
            return ApiResponse(success=False, status_code=0,
                               response={"error": f"请求超时: {str(e)}"})

    def get(self, path, headers=None) -> ApiResponse:
        return self._request("GET", path, headers=headers)

    def post(self, path, body=None, headers=None) -> ApiResponse:
        return self._request("POST", path, body, headers)

    def patch(self, path, body=None, headers=None) -> ApiResponse:
        return self._request("PATCH", path, body, headers)

    def delete(self, path, headers=None) -> ApiResponse:
        return self._request("DELETE", path, headers=headers)

    def register_user(self, username: str, phone: str, password: str,
                      role: str = "CUSTOMER") -> ApiResponse:
        """注册用户"""
        body = {
            "username": username,
            "phone": phone,
            "password": password,
            "role": role
        }
        return self.post("/api/v1/auth/register", body)

    def login(self, account: str, password: str) -> ApiResponse:
        """登录获取 Token"""
        body = {
            "account": account,
            "password": password
        }
        return self.post("/api/v1/auth/login", body)

    def get_access_token(self, account: str, password: str) -> str:
        """登录并获取 Access Token"""
        resp = self.login(account, password)
        if resp.success and resp.response:
            data = resp.response.get("data", {})
            return data.get("accessToken", "")
        return ""

    def auth_header(self, token: str) -> dict:
        """构建 Authorization 头"""
        return {"Authorization": f"Bearer {token}"}
