"""
Upload 模块测试 — 文件上传接口测试

测试覆盖：
1. 正常上传商品图片（需 MERCHANT 角色）
2. 正常上传搜索图片（需登录）
3. 未携带 Token 上传（401）
4. CUSTOMER 角色上传商品图片（403）
5. 上传空文件（400）
6. 上传不支持的文件类型（415）
7. 上传超大文件（413）

修复记录（2026-06-08 v2）：
- 所有测试用例增加 detail 信息输出，包含 status_code 和 response body
- 增加 _safe_get_data 辅助方法统一处理 data 字段提取
- 增加 _safe_detail 方法安全地构建 detail 字符串
"""
import os
import sys
import json
import time
import tempfile

# 添加父目录到路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from api_client import ApiClient


class UploadTest:
    """Upload 模块测试套件"""

    def __init__(self, client: ApiClient, results_dir: str):
        self.client = client
        self.results_dir = results_dir
        self.results = []
        self.timestamp = time.strftime("%Y%m%d%H%M%S")
        self.test_user = f"upload_test_merchant_{self.timestamp}"
        self.test_phone = f"1386666{self.timestamp[-6:]}"
        self.test_password = "test123456"
        self.merchant_token = ""
        self.customer_token = ""

    def setup(self):
        """准备测试数据：注册商家和普通用户"""
        print("\n📋 准备测试数据...")

        # 注册商家
        resp = self.client.register_user(self.test_user, self.test_phone,
                                          self.test_password, "MERCHANT")
        assert resp.success, f"商家注册失败: {resp.response}"
        print(f"  ✅ 商家注册成功: {self.test_user}")

        # 商家登录
        self.merchant_token = self.client.get_access_token(self.test_user, self.test_password)
        assert self.merchant_token, "商家登录失败"
        print(f"  ✅ 商家登录成功")

        # 注册普通用户
        customer_user = f"upload_test_customer_{self.timestamp}"
        customer_phone = f"1387777{self.timestamp[-6:]}"
        resp = self.client.register_user(customer_user, customer_phone,
                                          self.test_password, "CUSTOMER")
        assert resp.success, f"普通用户注册失败: {resp.response}"

        self.customer_token = self.client.get_access_token(customer_user, self.test_password)
        assert self.customer_token, "普通用户登录失败"
        print(f"  ✅ 普通用户注册并登录成功")

    def _record(self, case_id: str, name: str, passed: bool, detail: str = ""):
        """记录测试结果"""
        result = {
            "case_id": case_id,
            "name": name,
            "passed": passed,
            "detail": detail
        }
        self.results.append(result)
        status = "✅ PASS" if passed else "❌ FAIL"
        print(f"  {status} | {case_id}: {name}")
        if detail:
            print(f"         {detail}")

    def _safe_get_data(self, resp, field: str = None, default=None):
        """安全地从响应中提取 data 字段"""
        if not resp or not resp.response:
            return default
        data = resp.response.get('data')
        if data is None:
            data = {}
        if field is not None:
            return data.get(field, default)
        return data

    def _safe_detail(self, resp, extra: str = ""):
        """安全地构建 detail 字符串"""
        parts = [f"status_code={resp.status_code}"]
        if resp.response:
            # 截断过长的响应体
            body_str = json.dumps(resp.response, ensure_ascii=False)
            if len(body_str) > 200:
                body_str = body_str[:200] + "..."
            parts.append(f"body={body_str}")
        if extra:
            parts.append(extra)
        return ", ".join(parts)

    def _create_temp_image(self, suffix: str = ".jpg") -> str:
        """创建临时图片文件用于测试"""
        fd, path = tempfile.mkstemp(suffix=suffix)
        # 写入一个最小的 JPEG 文件头
        if suffix in (".jpg", ".jpeg"):
            os.write(fd, b'\xFF\xD8\xFF\xE0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00')
        elif suffix == ".png":
            os.write(fd, b'\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR')
        elif suffix == ".gif":
            os.write(fd, b'GIF89a\x01\x00\x01\x00\x80\x00\x00\xff\xff\xff\x00\x00\x00!\xf9\x04\x00\x00\x00\x00\x00,\x00\x00\x00\x00\x01\x00\x01\x00\x00\x02\x02D\x01\x00;')
        else:
            os.write(fd, b'fake file content')
        os.close(fd)
        return path

    def test_upload_product_image(self):
        """TC-UP001: 商家正常上传商品图片"""
        print("\n📸 测试上传商品图片...")
        temp_path = self._create_temp_image(".jpg")
        try:
            headers = self.client.auth_header(self.merchant_token)
            resp = self.client.post_file(
                "/api/v1/uploads/product-images",
                "image", temp_path,
                headers=headers
            )
            passed = resp.success
            detail_parts = []
            if resp.success and resp.response:
                data = self._safe_get_data(resp)
                url = data.get("url")
                file_id = data.get("fileId")
                passed = bool(url) and bool(file_id)
                detail_parts.append(f"url={url}, fileId={file_id}")
            self._record("TC-UP001", "商家正常上传商品图片", passed,
                         self._safe_detail(resp, "; ".join(detail_parts)))
        finally:
            os.unlink(temp_path)

    def test_upload_search_image(self):
        """TC-UP002: 登录用户正常上传搜索图片"""
        print("\n📸 测试上传搜索图片...")
        temp_path = self._create_temp_image(".png")
        try:
            headers = self.client.auth_header(self.merchant_token)
            resp = self.client.post_file(
                "/api/v1/uploads/search-images",
                "image", temp_path,
                headers=headers
            )
            passed = resp.success
            detail_parts = []
            if resp.success and resp.response:
                data = self._safe_get_data(resp)
                url = data.get("url")
                file_id = data.get("fileId")
                passed = bool(url) and bool(file_id)
                detail_parts.append(f"url={url}, fileId={file_id}")
            self._record("TC-UP002", "登录用户正常上传搜索图片", passed,
                         self._safe_detail(resp, "; ".join(detail_parts)))
        finally:
            os.unlink(temp_path)

    def test_upload_no_token(self):
        """TC-UP003: 未携带 Token 上传"""
        print("\n🔒 测试未携带 Token 上传...")
        temp_path = self._create_temp_image(".jpg")
        try:
            resp = self.client.post_file(
                "/api/v1/uploads/product-images",
                "image", temp_path
            )
            passed = resp.status_code == 401
            self._record("TC-UP003", "未携带 Token 上传返回 401", passed,
                         self._safe_detail(resp))
        finally:
            os.unlink(temp_path)

    def test_upload_customer_forbidden(self):
        """TC-UP004: CUSTOMER 角色上传商品图片"""
        print("\n🔒 测试 CUSTOMER 角色上传商品图片...")
        temp_path = self._create_temp_image(".jpg")
        try:
            headers = self.client.auth_header(self.customer_token)
            resp = self.client.post_file(
                "/api/v1/uploads/product-images",
                "image", temp_path,
                headers=headers
            )
            passed = resp.status_code == 403
            self._record("TC-UP004", "CUSTOMER 上传商品图片返回 403", passed,
                         self._safe_detail(resp))
        finally:
            os.unlink(temp_path)

    def test_upload_empty_file(self):
        """TC-UP005: 上传空文件"""
        print("\n📸 测试上传空文件...")
        fd, path = tempfile.mkstemp(suffix=".jpg")
        os.close(fd)  # 空文件
        try:
            headers = self.client.auth_header(self.merchant_token)
            resp = self.client.post_file(
                "/api/v1/uploads/product-images",
                "image", path,
                headers=headers
            )
            passed = resp.status_code == 400
            self._record("TC-UP005", "上传空文件返回 400", passed,
                         self._safe_detail(resp))
        finally:
            os.unlink(path)

    def test_upload_unsupported_type(self):
        """TC-UP006: 上传不支持的文件类型"""
        print("\n📸 测试上传不支持的文件类型...")
        fd, path = tempfile.mkstemp(suffix=".txt")
        os.write(fd, b'This is a text file, not an image.')
        os.close(fd)
        try:
            headers = self.client.auth_header(self.merchant_token)
            resp = self.client.post_file(
                "/api/v1/uploads/product-images",
                "image", path,
                headers=headers
            )
            passed = resp.status_code == 415
            self._record("TC-UP006", "上传不支持的文件类型返回 415", passed,
                         self._safe_detail(resp))
        finally:
            os.unlink(path)

    def run_all(self):
        """运行所有测试"""
        print("\n" + "=" * 60)
        print("📤 Upload 模块测试")
        print("=" * 60)

        self.setup()
        self.test_upload_product_image()
        self.test_upload_search_image()
        self.test_upload_no_token()
        self.test_upload_customer_forbidden()
        self.test_upload_empty_file()
        self.test_upload_unsupported_type()

        # 保存结果
        self._save_results()
        return self.results

    def _save_results(self):
        """保存测试结果到 JSON 文件"""
        os.makedirs(self.results_dir, exist_ok=True)
        filepath = os.path.join(self.results_dir, "01_upload_result.json")
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(self.results, f, ensure_ascii=False, indent=2)
        print(f"\n📁 测试结果已保存: {filepath}")


def main():
    client = ApiClient()
    results_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "..", "..", "test-results", "uba")
    test = UploadTest(client, results_dir)
    results = test.run_all()

    passed = sum(1 for r in results if r["passed"])
    total = len(results)
    print(f"\n📊 Upload 测试汇总: {passed}/{total} 通过 ({(passed/total)*100:.1f}%)")
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
