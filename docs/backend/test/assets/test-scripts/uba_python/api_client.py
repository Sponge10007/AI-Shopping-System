"""
通用 API 客户端
封装 HTTP 请求，统一处理响应解析和错误处理

修复记录（2026-06-08）：
1. 移除 Session 级别的 Content-Type 默认头，改为在 _request 中按需设置
   - 原因：Session 级别的 Content-Type: application/json 会覆盖 post_file 中
     requests 库自动设置的 multipart/form-data 边界头，导致后端无法解析 MultipartFile
   - 修复：_request 中设置 Content-Type: application/json，post_file 中不设置 Content-Type
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
        # ⚠️ 注意：不在 Session 级别设置 Content-Type
        # 原因：post_file 方法需要 requests 自动设置 multipart/form-data 边界头
        # Session 级别的 Content-Type 会覆盖自动设置的值

    def _request(self, method, path, body=None, headers=None) -> ApiResponse:
        """发送 HTTP 请求并解析响应"""
        url = f"{self.base_url}{path}"

        # 合并请求头：默认 JSON 格式，可被传入的 headers 覆盖
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

    def post_file(self, path, file_param_name, file_path, headers=None) -> ApiResponse:
        """上传文件

        注意：不设置 Content-Type 头，让 requests 库自动设置 multipart/form-data 边界头。
        Session 级别的 Content-Type 会覆盖自动设置的值，导致后端无法解析 MultipartFile。
        """
        url = f"{self.base_url}{path}"
        try:
            with open(file_path, 'rb') as f:
                files = {file_param_name: f}
                # 不传 Content-Type，让 requests 自动设置 multipart/form-data
                resp = self.session.post(url, files=files, headers=headers or {}, timeout=60)

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
        except Exception as e:
            return ApiResponse(success=False, status_code=0,
                               response={"error": str(e)})

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
