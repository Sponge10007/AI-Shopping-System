"""
Behavior 模块测试 — 用户行为记录接口测试

测试覆盖：
1. 正常记录 VIEW 行为
2. 正常记录 SEARCH 行为
3. 正常记录 ADD_TO_CART 行为
4. 正常记录 PURCHASE 行为
5. 未携带 Token 记录行为（应返回 401）
6. 传入不支持的事件类型（400）
7. 事件类型为空（400）

修复记录（2026-06-08 v2）：
- 修复第 87 行崩溃问题：resp.response.get('data', {}) 可能返回 None，
  改为 resp.response.get('data') or {} 避免 AttributeError
- 增加 detail 信息输出，包含 status_code 和 response body，便于排查
- 增加 _safe_get_data 辅助方法统一处理 data 字段提取

修复记录（2026-06-08 v3）：
- 【关键修复】metadata 参数改为直接传 Python dict，而非 JSON 字符串
  原因：后端 BehaviorEventRequest.metadata 类型从 String 改为 Map<String, Object>，
  Jackson 会自动将 JSON 对象反序列化为 Map。测试脚本中直接传 dict，
  api_client 的 json.dumps 会自动序列化为 JSON 对象，避免双重序列化问题。
"""
import os
import sys
import json
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from api_client import ApiClient


class BehaviorTest:
    """Behavior 模块测试套件"""

    def __init__(self, client: ApiClient, results_dir: str):
        self.client = client
        self.results_dir = results_dir
        self.results = []
        self.timestamp = time.strftime("%Y%m%d%H%M%S")
        self.test_user = f"behavior_test_{self.timestamp}"
        self.test_phone = f"1388888{self.timestamp[-6:]}"
        self.test_password = "test123456"
        self.token = ""

    def setup(self):
        """准备测试数据：注册用户并登录"""
        print("\n📋 准备测试数据...")

        resp = self.client.register_user(self.test_user, self.test_phone,
                                          self.test_password, "CUSTOMER")
        assert resp.success, f"用户注册失败: {resp.response}"
        print(f"  ✅ 用户注册成功: {self.test_user}")

        self.token = self.client.get_access_token(self.test_user, self.test_password)
        assert self.token, "用户登录失败"
        print(f"  ✅ 用户登录成功")

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
        """
        安全地从响应中提取 data 字段

        修复说明：
        - resp.response.get('data', {}) 在 data 为 JSON null 时返回 None
        - 对 None 调用 .get(field) 会抛出 AttributeError
        - 改为 resp.response.get('data') or {} 确保返回 dict
        """
        if not resp or not resp.response:
            return default
        data = resp.response.get('data')
        if data is None:
            data = {}
        if field is not None:
            return data.get(field, default)
        return data

    def _record_event(self, event_type: str, target_type: str = None,
                      target_id: str = None, metadata: dict = None,
                      token: str = None) -> dict:
        """记录行为事件

        【修复 v3】metadata 参数类型从 str 改为 dict
        原因：后端 BehaviorEventRequest.metadata 类型改为 Map<String, Object>
        """
        body = {"eventType": event_type}
        if target_type:
            body["targetType"] = target_type
        if target_id:
            body["targetId"] = target_id
        if metadata:
            body["metadata"] = metadata

        headers = self.client.auth_header(token) if token else None
        return self.client.post("/api/v1/behavior-events", body, headers=headers)

    def test_record_view(self):
        """TC-BE001: 正常记录 VIEW 行为"""
        print("\n📊 测试记录 VIEW 行为...")
        resp = self._record_event("VIEW", "PRODUCT", "p10001",
                                  {"source": "homepage"}, self.token)
        passed = resp.success
        accepted = self._safe_get_data(resp, "accepted")
        if accepted is not None:
            passed = accepted is True
        self._record("TC-BE001", "正常记录 VIEW 行为", passed,
                     f"status_code={resp.status_code}, accepted={accepted}, body={resp.response}")

    def test_record_search(self):
        """TC-BE002: 正常记录 SEARCH 行为"""
        print("\n📊 测试记录 SEARCH 行为...")
        resp = self._record_event("SEARCH", None, None,
                                  {"keyword": "耳机", "results": 10}, self.token)
        passed = resp.success
        accepted = self._safe_get_data(resp, "accepted")
        if accepted is not None:
            passed = accepted is True
        self._record("TC-BE002", "正常记录 SEARCH 行为", passed,
                     f"status_code={resp.status_code}, accepted={accepted}")

    def test_record_add_to_cart(self):
        """TC-BE003: 正常记录 ADD_TO_CART 行为"""
        print("\n📊 测试记录 ADD_TO_CART 行为...")
        resp = self._record_event("ADD_TO_CART", "PRODUCT", "p10002",
                                  {"quantity": 2}, self.token)
        passed = resp.success
        accepted = self._safe_get_data(resp, "accepted")
        if accepted is not None:
            passed = accepted is True
        self._record("TC-BE003", "正常记录 ADD_TO_CART 行为", passed,
                     f"status_code={resp.status_code}, accepted={accepted}")

    def test_record_purchase(self):
        """TC-BE004: 正常记录 PURCHASE 行为"""
        print("\n📊 测试记录 PURCHASE 行为...")
        resp = self._record_event("PURCHASE", "PRODUCT", "p10003",
                                  {"orderId": "o10001", "amount": "299.00"}, self.token)
        passed = resp.success
        accepted = self._safe_get_data(resp, "accepted")
        if accepted is not None:
            passed = accepted is True
        self._record("TC-BE004", "正常记录 PURCHASE 行为", passed,
                     f"status_code={resp.status_code}, accepted={accepted}")

    def test_record_no_token(self):
        """TC-BE005: 未携带 Token 记录行为"""
        print("\n🔒 测试未携带 Token 记录行为...")
        resp = self._record_event("VIEW", "PRODUCT", "p10001")
        passed = resp.status_code == 401
        self._record("TC-BE005", "未携带 Token 记录行为返回 401", passed,
                     f"status_code={resp.status_code}")

    def test_record_invalid_event_type(self):
        """TC-BE006: 传入不支持的事件类型"""
        print("\n📊 测试传入不支持的事件类型...")
        resp = self._record_event("CLICK", "PRODUCT", "p10001", token=self.token)
        passed = resp.status_code == 400
        self._record("TC-BE006", "传入不支持的事件类型返回 400", passed,
                     f"status_code={resp.status_code}")

    def test_record_empty_event_type(self):
        """TC-BE007: 事件类型为空"""
        print("\n📊 测试事件类型为空...")
        resp = self._record_event("", token=self.token)
        passed = resp.status_code == 400
        self._record("TC-BE007", "事件类型为空返回 400", passed,
                     f"status_code={resp.status_code}")

    def run_all(self):
        """运行所有测试"""
        print("\n" + "=" * 60)
        print("📊 Behavior 模块测试")
        print("=" * 60)

        self.setup()
        self.test_record_view()
        self.test_record_search()
        self.test_record_add_to_cart()
        self.test_record_purchase()
        self.test_record_no_token()
        self.test_record_invalid_event_type()
        self.test_record_empty_event_type()

        self._save_results()
        return self.results

    def _save_results(self):
        """保存测试结果到 JSON 文件"""
        os.makedirs(self.results_dir, exist_ok=True)
        filepath = os.path.join(self.results_dir, "02_behavior_result.json")
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(self.results, f, ensure_ascii=False, indent=2)
        print(f"\n📁 测试结果已保存: {filepath}")


def main():
    client = ApiClient()
    results_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "..", "..", "test-results", "uba")
    test = BehaviorTest(client, results_dir)
    results = test.run_all()

    passed = sum(1 for r in results if r["passed"])
    total = len(results)
    print(f"\n📊 Behavior 测试汇总: {passed}/{total} 通过 ({(passed/total)*100:.1f}%)")
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
