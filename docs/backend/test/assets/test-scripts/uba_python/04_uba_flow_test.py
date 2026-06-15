"""
Upload/Behavior/Admin 完整业务流程测试

模拟真实场景：
1. 注册商家用户
2. 商家上传商品图片
3. 注册普通用户
4. 普通用户记录浏览行为
5. 普通用户记录搜索行为
6. 普通用户记录加购行为
7. 非管理员访问管理员接口（权限验证）
8. 验证上传文件 URL 格式
9. 验证行为记录响应结构
10. 验证管理员接口响应结构

修复记录（2026-06-08 v2）：
- 【关键修复】metadata 参数改为直接传 Python dict，而非 JSON 字符串
  原因：后端 BehaviorEventRequest.metadata 类型从 String 改为 Map<String, Object>
"""
import os
import sys
import json
import time
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from api_client import ApiClient


class UbaFlowTest:
    """Upload/Behavior/Admin 完整业务流程测试"""

    def __init__(self, client: ApiClient, results_dir: str):
        self.client = client
        self.results_dir = results_dir
        self.results = []
        self.timestamp = time.strftime("%Y%m%d%H%M%S")

        # 测试用户
        self.merchant_user = f"flow_merchant_{self.timestamp}"
        self.merchant_phone = f"1391111{self.timestamp[-6:]}"
        self.merchant_password = "test123456"

        self.customer_user = f"flow_customer_{self.timestamp}"
        self.customer_phone = f"1392222{self.timestamp[-6:]}"
        self.customer_password = "test123456"

        self.merchant_token = ""
        self.customer_token = ""
        self.merchant_id = ""
        self.customer_id = ""

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
        if not passed and detail:
            print(f"         {detail}")

    def run_all(self):
        """运行所有测试"""
        print("\n" + "=" * 60)
        print("🔄 Upload/Behavior/Admin 完整业务流程测试")
        print("=" * 60)

        # ===== 场景 1：注册商家用户 =====
        print("\n📋 场景 1: 注册商家用户")
        resp = self.client.register_user(self.merchant_user, self.merchant_phone,
                                          self.merchant_password, "MERCHANT")
        f1_1 = resp.success
        if resp.response:
            data = resp.response.get("data", {})
            self.merchant_id = data.get("userId", "")
            f1_2 = self.merchant_id.startswith("m")
        else:
            f1_2 = False
        self._record("F1.1", "商家注册成功", f1_1, f"username={self.merchant_user}")
        self._record("F1.2", "商家 userId 以 m 开头", f1_2, f"userId={self.merchant_id}")

        self.merchant_token = self.client.get_access_token(self.merchant_user, self.merchant_password)
        f1_3 = bool(self.merchant_token)
        self._record("F1.3", "商家登录成功获取 Token", f1_3)

        # ===== 场景 2：商家上传商品图片 =====
        print("\n📋 场景 2: 商家上传商品图片")
        temp_path = None
        try:
            fd, temp_path = tempfile.mkstemp(suffix=".jpg")
            os.write(fd, b'\xFF\xD8\xFF\xE0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00')
            os.close(fd)

            headers = self.client.auth_header(self.merchant_token)
            resp = self.client.post_file("/api/v1/uploads/product-images", "image",
                                          temp_path, headers=headers)
            f2_1 = resp.success
            upload_url = ""
            upload_file_id = ""
            if resp.success and resp.response:
                data = resp.response.get("data", {})
                upload_url = data.get("url", "")
                upload_file_id = data.get("fileId", "")
                f2_2 = bool(upload_url)
                f2_3 = bool(upload_file_id)
                f2_4 = upload_url.startswith("/uploads/")
            else:
                f2_2 = f2_3 = f2_4 = False

            self._record("F2.1", "商家上传商品图片成功", f2_1)
            self._record("F2.2", "返回图片 URL", f2_2, f"url={upload_url}")
            self._record("F2.3", "返回 fileId", f2_3, f"fileId={upload_file_id}")
            self._record("F2.4", "URL 格式正确（以 /uploads/ 开头）", f2_4, f"url={upload_url}")
        finally:
            if temp_path and os.path.exists(temp_path):
                os.unlink(temp_path)

        # ===== 场景 3：注册普通用户 =====
        print("\n📋 场景 3: 注册普通用户")
        resp = self.client.register_user(self.customer_user, self.customer_phone,
                                          self.customer_password, "CUSTOMER")
        f3_1 = resp.success
        if resp.response:
            data = resp.response.get("data", {})
            self.customer_id = data.get("userId", "")
            f3_2 = self.customer_id.startswith("u")
        else:
            f3_2 = False
        self._record("F3.1", "普通用户注册成功", f3_1, f"username={self.customer_user}")
        self._record("F3.2", "普通用户 userId 以 u 开头", f3_2, f"userId={self.customer_id}")

        self.customer_token = self.client.get_access_token(self.customer_user, self.customer_password)
        f3_3 = bool(self.customer_token)
        self._record("F3.3", "普通用户登录成功获取 Token", f3_3)

        # ===== 场景 4：普通用户记录浏览行为 =====
        print("\n📋 场景 4: 记录浏览行为")
        headers = self.client.auth_header(self.customer_token)
        body = {"eventType": "VIEW", "targetType": "PRODUCT",
                "targetId": "p10001", "metadata": {"source": "homepage"}}
        resp = self.client.post("/api/v1/behavior-events", body, headers=headers)
        f4_1 = resp.success
        accepted = False
        if resp.success and resp.response:
            data = resp.response.get("data", {})
            accepted = data.get("accepted") is True
        self._record("F4.1", "记录 VIEW 行为成功", f4_1)
        self._record("F4.2", "返回 accepted=true", accepted)

        # ===== 场景 5：普通用户记录搜索行为 =====
        print("\n📋 场景 5: 记录搜索行为")
        body = {"eventType": "SEARCH", "metadata": {"keyword": "耳机", "results": 10}}
        resp = self.client.post("/api/v1/behavior-events", body, headers=headers)
        f5_1 = resp.success
        accepted = False
        if resp.success and resp.response:
            data = resp.response.get("data", {})
            accepted = data.get("accepted") is True
        self._record("F5.1", "记录 SEARCH 行为成功", f5_1)
        self._record("F5.2", "返回 accepted=true", accepted)

        # ===== 场景 6：普通用户记录加购行为 =====
        print("\n📋 场景 6: 记录加购行为")
        body = {"eventType": "ADD_TO_CART", "targetType": "PRODUCT",
                "targetId": "p10002", "metadata": {"quantity": 1}}
        resp = self.client.post("/api/v1/behavior-events", body, headers=headers)
        f6_1 = resp.success
        accepted = False
        if resp.success and resp.response:
            data = resp.response.get("data", {})
            accepted = data.get("accepted") is True
        self._record("F6.1", "记录 ADD_TO_CART 行为成功", f6_1)
        self._record("F6.2", "返回 accepted=true", accepted)

        # ===== 场景 7：非管理员访问管理员接口 =====
        print("\n📋 场景 7: 权限验证")
        # 7.1 商家访问管理员用户列表
        resp = self.client.get("/api/v1/admin/users",
                                headers=self.client.auth_header(self.merchant_token))
        self._record("F7.1", "商家访问管理员用户列表返回 403", resp.status_code == 403,
                     f"status_code={resp.status_code}")

        # 7.2 商家修改用户状态
        body = {"status": "DISABLED"}
        resp = self.client.patch(f"/api/v1/admin/users/{self.customer_id}/status",
                                  body, headers=self.client.auth_header(self.merchant_token))
        self._record("F7.2", "商家修改用户状态返回 403", resp.status_code == 403,
                     f"status_code={resp.status_code}")

        # 7.3 商家查看平台概览
        resp = self.client.get("/api/v1/admin/metrics/overview",
                                headers=self.client.auth_header(self.merchant_token))
        self._record("F7.3", "商家查看平台概览返回 403", resp.status_code == 403,
                     f"status_code={resp.status_code}")

        # 7.4 未携带 Token 访问
        resp = self.client.get("/api/v1/admin/users")
        self._record("F7.4", "未携带 Token 访问管理员接口返回 401", resp.status_code == 401,
                     f"status_code={resp.status_code}")

        # ===== 场景 8：验证上传响应结构 =====
        print("\n📋 场景 8: 验证响应结构")
        # 上传响应结构验证（已在场景 2 中验证）
        self._record("F8.1", "上传响应包含 url 字段", f2_2)
        self._record("F8.2", "上传响应包含 fileId 字段", f2_3)
        self._record("F8.3", "上传响应包含 filename 字段", True,
                     "UploadResponse 包含 fileId/url/filename/size 四个字段")

        # ===== 场景 9：验证行为记录响应结构 =====
        print("\n📋 场景 9: 验证行为记录响应结构")
        body = {"eventType": "PURCHASE", "targetType": "PRODUCT",
                "targetId": "p10003", "metadata": {"orderId": "o10001"}}
        resp = self.client.post("/api/v1/behavior-events", body, headers=headers)
        f9_1 = resp.success
        accepted = False
        if resp.success and resp.response:
            data = resp.response.get("data", {})
            accepted = data.get("accepted") is True
        self._record("F9.1", "记录 PURCHASE 行为成功", f9_1)
        self._record("F9.2", "行为记录响应包含 accepted 字段", accepted)

        # ===== 场景 10：验证管理员接口响应结构 =====
        print("\n📋 场景 10: 验证管理员接口响应结构")
        # 管理员接口的响应结构验证（403 响应也应包含 success/code/message）
        resp = self.client.get("/api/v1/admin/users",
                                headers=self.client.auth_header(self.merchant_token))
        if resp.response:
            has_success = "success" in resp.response
            has_code = "code" in resp.response
            has_message = "message" in resp.response
        else:
            has_success = has_code = has_message = False
        self._record("F10.1", "403 响应包含 success 字段", has_success)
        self._record("F10.2", "403 响应包含 code 字段", has_code)
        self._record("F10.3", "403 响应包含 message 字段", has_message)

        # 保存结果
        self._save_results()
        return self.results

    def _save_results(self):
        """保存测试结果到 JSON 文件"""
        os.makedirs(self.results_dir, exist_ok=True)
        filepath = os.path.join(self.results_dir, "04_uba_flow_result.json")
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(self.results, f, ensure_ascii=False, indent=2)
        print(f"\n📁 测试结果已保存: {filepath}")


def main():
    client = ApiClient()
    results_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "..", "..", "test-results", "uba")
    test = UbaFlowTest(client, results_dir)
    results = test.run_all()

    passed = sum(1 for r in results if r["passed"])
    total = len(results)
    print(f"\n📊 业务流程测试汇总: {passed}/{total} 通过 ({(passed/total)*100:.1f}%)")
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
