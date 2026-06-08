"""
Internal 模块测试 — 内部商品 AI 摘要接口测试

测试覆盖：
1. 获取单个商品 AI 摘要（正常场景）
2. 获取不存在的商品 AI 摘要（404）
3. 批量获取商品 AI 摘要（正常场景）
4. 批量获取商品 AI 摘要（部分商品不存在）
5. 未携带 Internal-Token 访问内部接口（403）
6. 携带错误的 Internal-Token 访问内部接口（403）
7. 验证 AI 摘要内容格式（包含关键字段）
8. 验证批量摘要返回结构

测试策略：
- 先注册 MERCHANT 用户并创建测试商品
- 使用正确的 Internal-Token 调用内部接口
- 验证摘要内容的完整性和格式
"""
import os
import sys
import json
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from api_client import ApiClient


class InternalAiSummaryTest:
    """Internal 模块 AI 摘要测试套件"""

    # 内部 Token（与 application.yml 中的 app.internal-token 保持一致）
    INTERNAL_TOKEN = "dev-internal-token"

    def __init__(self, client: ApiClient, results_dir: str):
        self.client = client
        self.results_dir = results_dir
        self.results = []
        self.timestamp = time.strftime("%Y%m%d%H%M%S")

        # 测试用户
        self.merchant_user = f"internal_merchant_{self.timestamp}"
        self.merchant_phone = f"1380000{self.timestamp[-6:]}"
        self.merchant_password = "test123456"

        self.merchant_token = ""
        self.test_product_ids = []

    def setup(self):
        """准备测试数据：注册商家并创建测试商品"""
        print("\n📋 准备测试数据...")

        # 注册商家
        resp = self.client.register_user(self.merchant_user, self.merchant_phone,
                                          self.merchant_password, "MERCHANT")
        assert resp.success, f"商家注册失败: {resp.response}"
        print(f"  ✅ 商家注册成功: {self.merchant_user}")

        self.merchant_token = self.client.get_access_token(self.merchant_user, self.merchant_password)
        assert self.merchant_token, "商家登录失败"
        print(f"  ✅ 商家登录成功")

        # 创建测试商品
        headers = self.client.auth_header(self.merchant_token)

        # 商品 1：耳机
        product1 = {
            "name": f"AI摘要测试耳机_{self.timestamp}",
            "description": "高品质降噪蓝牙耳机，支持主动降噪、蓝牙5.3、续航30小时。适合通勤、运动、办公等多种场景使用。",
            "categoryId": "c_headphone",
            "price": 299.00,
            "stock": 100,
            "tags": ["蓝牙", "降噪", "无线"]
        }
        resp1 = self.client.post("/api/v1/merchant/products", product1, headers=headers)
        assert resp1.success, f"商品1创建失败: {resp1.response}"
        pid1 = resp1.response.get("data", {}).get("productId", "")
        self.test_product_ids.append(pid1)
        print(f"  ✅ 商品1创建成功: {pid1}")

        # 商品 2：手机
        product2 = {
            "name": f"AI摘要测试手机_{self.timestamp}",
            "description": "旗舰智能手机，搭载最新处理器，支持5G网络，后置三摄系统，支持无线充电。",
            "categoryId": "c_phone",
            "price": 5999.00,
            "stock": 50,
            "tags": ["5G", "旗舰", "拍照"]
        }
        resp2 = self.client.post("/api/v1/merchant/products", product2, headers=headers)
        assert resp2.success, f"商品2创建失败: {resp2.response}"
        pid2 = resp2.response.get("data", {}).get("productId", "")
        self.test_product_ids.append(pid2)
        print(f"  ✅ 商品2创建成功: {pid2}")

        print(f"  📦 共创建 {len(self.test_product_ids)} 个测试商品")

    def _internal_header(self) -> dict:
        """构建内部接口请求头"""
        return {"X-Internal-Token": self.INTERNAL_TOKEN}

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

    # ===== 单个商品 AI 摘要测试 =====

    def test_get_single_ai_summary(self):
        """TC-IS001: 获取单个商品 AI 摘要（正常场景）"""
        print("\n📖 测试获取单个商品 AI 摘要...")
        pid = self.test_product_ids[0]
        headers = self._internal_header()
        resp = self.client.get(f"/internal/v1/products/{pid}/ai-summary", headers=headers)

        passed = resp.success
        self._record("TC-IS001", "获取单个商品 AI 摘要成功", passed,
                     f"status_code={resp.status_code}, productId={pid}")

    def test_ai_summary_contains_product_id(self):
        """TC-IS002: AI 摘要返回正确的 productId"""
        print("\n📖 验证 AI 摘要返回正确的 productId...")
        pid = self.test_product_ids[0]
        headers = self._internal_header()
        resp = self.client.get(f"/internal/v1/products/{pid}/ai-summary", headers=headers)

        data = self._safe_get_data(resp)
        returned_pid = data.get("productId", "")
        passed = returned_pid == pid
        self._record("TC-IS002", "AI 摘要返回正确的 productId", passed,
                     f"expected={pid}, actual={returned_pid}")

    def test_ai_summary_contains_summary_text(self):
        """TC-IS003: AI 摘要包含 summaryText 字段"""
        print("\n📖 验证 AI 摘要包含 summaryText 字段...")
        pid = self.test_product_ids[0]
        headers = self._internal_header()
        resp = self.client.get(f"/internal/v1/products/{pid}/ai-summary", headers=headers)

        data = self._safe_get_data(resp)
        summary_text = data.get("summaryText", "")
        passed = bool(summary_text)
        self._record("TC-IS003", "AI 摘要包含 summaryText 字段", passed,
                     f"summaryText_length={len(summary_text)}")

    def test_ai_summary_contains_product_name(self):
        """TC-IS004: AI 摘要包含商品名称"""
        print("\n📖 验证 AI 摘要包含商品名称...")
        pid = self.test_product_ids[0]
        headers = self._internal_header()
        resp = self.client.get(f"/internal/v1/products/{pid}/ai-summary", headers=headers)

        data = self._safe_get_data(resp)
        summary_text = data.get("summaryText", "")
        passed = "商品名称" in summary_text
        self._record("TC-IS004", "AI 摘要包含商品名称", passed)

    def test_ai_summary_contains_price(self):
        """TC-IS005: AI 摘要包含价格信息"""
        print("\n📖 验证 AI 摘要包含价格信息...")
        pid = self.test_product_ids[0]
        headers = self._internal_header()
        resp = self.client.get(f"/internal/v1/products/{pid}/ai-summary", headers=headers)

        data = self._safe_get_data(resp)
        summary_text = data.get("summaryText", "")
        passed = "价格" in summary_text and "元" in summary_text
        self._record("TC-IS005", "AI 摘要包含价格信息", passed)

    def test_ai_summary_contains_description(self):
        """TC-IS006: AI 摘要包含商品描述"""
        print("\n📖 验证 AI 摘要包含商品描述...")
        pid = self.test_product_ids[0]
        headers = self._internal_header()
        resp = self.client.get(f"/internal/v1/products/{pid}/ai-summary", headers=headers)

        data = self._safe_get_data(resp)
        summary_text = data.get("summaryText", "")
        passed = "描述" in summary_text
        self._record("TC-IS006", "AI 摘要包含商品描述", passed)

    def test_ai_summary_contains_tags(self):
        """TC-IS007: AI 摘要包含标签/特点"""
        print("\n📖 验证 AI 摘要包含标签/特点...")
        pid = self.test_product_ids[0]
        headers = self._internal_header()
        resp = self.client.get(f"/internal/v1/products/{pid}/ai-summary", headers=headers)

        data = self._safe_get_data(resp)
        summary_text = data.get("summaryText", "")
        passed = "特点" in summary_text
        self._record("TC-IS007", "AI 摘要包含标签/特点", passed)

    def test_ai_summary_contains_category(self):
        """TC-IS008: AI 摘要包含分类信息"""
        print("\n📖 验证 AI 摘要包含分类信息...")
        pid = self.test_product_ids[0]
        headers = self._internal_header()
        resp = self.client.get(f"/internal/v1/products/{pid}/ai-summary", headers=headers)

        data = self._safe_get_data(resp)
        summary_text = data.get("summaryText", "")
        passed = "分类" in summary_text
        self._record("TC-IS008", "AI 摘要包含分类信息", passed)

    def test_ai_summary_contains_rating_and_sales(self):
        """TC-IS009: AI 摘要包含评分和销量"""
        print("\n📖 验证 AI 摘要包含评分和销量...")
        pid = self.test_product_ids[0]
        headers = self._internal_header()
        resp = self.client.get(f"/internal/v1/products/{pid}/ai-summary", headers=headers)

        data = self._safe_get_data(resp)
        summary_text = data.get("summaryText", "")
        passed = "评分" in summary_text and "销量" in summary_text
        self._record("TC-IS009", "AI 摘要包含评分和销量", passed)

    def test_ai_summary_contains_detail_url(self):
        """TC-IS010: AI 摘要包含详情页链接"""
        print("\n📖 验证 AI 摘要包含详情页链接...")
        pid = self.test_product_ids[0]
        headers = self._internal_header()
        resp = self.client.get(f"/internal/v1/products/{pid}/ai-summary", headers=headers)

        data = self._safe_get_data(resp)
        summary_text = data.get("summaryText", "")
        passed = "详情页" in summary_text
        self._record("TC-IS010", "AI 摘要包含详情页链接", passed)

    def test_ai_summary_contains_status(self):
        """TC-IS011: AI 摘要包含商品状态"""
        print("\n📖 验证 AI 摘要包含商品状态...")
        pid = self.test_product_ids[0]
        headers = self._internal_header()
        resp = self.client.get(f"/internal/v1/products/{pid}/ai-summary", headers=headers)

        data = self._safe_get_data(resp)
        summary_text = data.get("summaryText", "")
        passed = "状态" in summary_text
        self._record("TC-IS011", "AI 摘要包含商品状态", passed)

    # ===== 异常场景测试 =====

    def test_get_nonexistent_product(self):
        """TC-IS012: 获取不存在的商品 AI 摘要"""
        print("\n🔒 测试获取不存在的商品 AI 摘要...")
        headers = self._internal_header()
        resp = self.client.get("/internal/v1/products/p_nonexistent/ai-summary", headers=headers)

        # 内部接口应该返回 404（Resource Not Found）
        passed = resp.status_code == 404
        self._record("TC-IS012", "获取不存在的商品 AI 摘要返回 404", passed,
                     f"status_code={resp.status_code}")

    # ===== 批量 AI 摘要测试 =====

    def test_batch_ai_summaries(self):
        """TC-IS013: 批量获取商品 AI 摘要（正常场景）"""
        print("\n📚 测试批量获取商品 AI 摘要...")
        headers = self._internal_header()
        body = {"productIds": self.test_product_ids}
        resp = self.client.post("/internal/v1/products/ai-summaries", body, headers=headers)

        passed = resp.success
        self._record("TC-IS013", "批量获取商品 AI 摘要成功", passed,
                     f"status_code={resp.status_code}")

    def test_batch_ai_summaries_contains_items(self):
        """TC-IS014: 批量摘要返回 items 列表"""
        print("\n📚 验证批量摘要返回 items 列表...")
        headers = self._internal_header()
        body = {"productIds": self.test_product_ids}
        resp = self.client.post("/internal/v1/products/ai-summaries", body, headers=headers)

        data = self._safe_get_data(resp)
        items = data.get("items", [])
        passed = len(items) == len(self.test_product_ids)
        self._record("TC-IS014", "批量摘要返回 items 列表", passed,
                     f"expected_count={len(self.test_product_ids)}, actual_count={len(items)}")

    def test_batch_ai_summaries_partial_failure(self):
        """TC-IS015: 批量摘要（部分商品不存在）"""
        print("\n📚 测试批量摘要（部分商品不存在）...")
        headers = self._internal_header()
        # 混合存在的商品和不存在的商品
        mixed_ids = self.test_product_ids + ["p_nonexistent_1", "p_nonexistent_2"]
        body = {"productIds": mixed_ids}
        resp = self.client.post("/internal/v1/products/ai-summaries", body, headers=headers)

        # 批量接口应该返回 200，但 items 中不存在的商品标记为失败
        passed = resp.success
        data = self._safe_get_data(resp)
        items = data.get("items", [])
        if items:
            # 验证不存在的商品有错误提示
            failed_items = [i for i in items if i.get("summaryText", "").startswith("【摘要生成失败】")]
            passed = passed and len(failed_items) == 2
            self._record("TC-IS015", "批量摘要（部分商品不存在）", passed,
                         f"total_items={len(items)}, failed_count={len(failed_items)}")
        else:
            self._record("TC-IS015", "批量摘要（部分商品不存在）", False,
                         "items 为空")

    def test_batch_ai_summaries_empty_list(self):
        """TC-IS016: 批量摘要传入空列表"""
        print("\n📚 测试批量摘要传入空列表...")
        headers = self._internal_header()
        body = {"productIds": []}
        resp = self.client.post("/internal/v1/products/ai-summaries", body, headers=headers)

        # 空列表应该返回 400（@NotEmpty 校验）
        passed = resp.status_code == 400
        self._record("TC-IS016", "批量摘要传入空列表返回 400", passed,
                     f"status_code={resp.status_code}")

    # ===== 内部 Token 验证测试 =====

    def test_without_internal_token(self):
        """TC-IS017: 未携带 Internal-Token 访问内部接口"""
        print("\n🔒 测试未携带 Internal-Token 访问内部接口...")
        pid = self.test_product_ids[0]
        # 不传 X-Internal-Token 头
        resp = self.client.get(f"/internal/v1/products/{pid}/ai-summary")

        # 应该返回 403（Forbidden）
        passed = resp.status_code == 403
        self._record("TC-IS017", "未携带 Internal-Token 返回 403", passed,
                     f"status_code={resp.status_code}")

    def test_with_wrong_internal_token(self):
        """TC-IS018: 携带错误的 Internal-Token 访问内部接口"""
        print("\n🔒 测试携带错误的 Internal-Token 访问内部接口...")
        pid = self.test_product_ids[0]
        headers = {"X-Internal-Token": "wrong-token"}
        resp = self.client.get(f"/internal/v1/products/{pid}/ai-summary", headers=headers)

        # 应该返回 403（Forbidden）
        passed = resp.status_code == 403
        self._record("TC-IS018", "携带错误的 Internal-Token 返回 403", passed,
                     f"status_code={resp.status_code}")

    # ===== 响应结构验证 =====

    def test_response_structure(self):
        """TC-IS019: 验证 AI 摘要响应结构"""
        print("\n📖 验证 AI 摘要响应结构...")
        pid = self.test_product_ids[0]
        headers = self._internal_header()
        resp = self.client.get(f"/internal/v1/products/{pid}/ai-summary", headers=headers)

        response_data = resp.response
        if response_data:
            has_success = "success" in response_data
            has_code = "code" in response_data
            has_message = "message" in response_data
            has_data = "data" in response_data
            has_trace_id = "traceId" in response_data
            passed = has_success and has_code and has_message and has_data and has_trace_id
            self._record("TC-IS019", "AI 摘要响应结构完整", passed,
                         f"success={has_success}, code={has_code}, message={has_message}, data={has_data}, traceId={has_trace_id}")
        else:
            self._record("TC-IS019", "AI 摘要响应结构完整", False,
                         "响应为空")

    def test_batch_response_structure(self):
        """TC-IS020: 验证批量 AI 摘要响应结构"""
        print("\n📚 验证批量 AI 摘要响应结构...")
        headers = self._internal_header()
        body = {"productIds": self.test_product_ids}
        resp = self.client.post("/internal/v1/products/ai-summaries", body, headers=headers)

        response_data = resp.response
        if response_data:
            has_success = "success" in response_data
            has_code = "code" in response_data
            has_data = "data" in response_data
            data = response_data.get("data", {})
            has_items = "items" in data
            passed = has_success and has_code and has_data and has_items
            self._record("TC-IS020", "批量 AI 摘要响应结构完整", passed,
                         f"success={has_success}, code={has_code}, data={has_data}, items={has_items}")
        else:
            self._record("TC-IS020", "批量 AI 摘要响应结构完整", False,
                         "响应为空")

    def run_all(self):
        """运行所有测试"""
        print("\n" + "=" * 60)
        print("🔧 Internal 模块 — AI 摘要接口测试")
        print("=" * 60)

        self.setup()

        # 单个商品 AI 摘要测试
        self.test_get_single_ai_summary()
        self.test_ai_summary_contains_product_id()
        self.test_ai_summary_contains_summary_text()
        self.test_ai_summary_contains_product_name()
        self.test_ai_summary_contains_price()
        self.test_ai_summary_contains_description()
        self.test_ai_summary_contains_tags()
        self.test_ai_summary_contains_category()
        self.test_ai_summary_contains_rating_and_sales()
        self.test_ai_summary_contains_detail_url()
        self.test_ai_summary_contains_status()

        # 异常场景
        self.test_get_nonexistent_product()

        # 批量 AI 摘要测试
        self.test_batch_ai_summaries()
        self.test_batch_ai_summaries_contains_items()
        self.test_batch_ai_summaries_partial_failure()
        self.test_batch_ai_summaries_empty_list()

        # 内部 Token 验证
        self.test_without_internal_token()
        self.test_with_wrong_internal_token()

        # 响应结构验证
        self.test_response_structure()
        self.test_batch_response_structure()

        self._save_results()
        return self.results

    def _save_results(self):
        """保存测试结果到 JSON 文件"""
        os.makedirs(self.results_dir, exist_ok=True)
        filepath = os.path.join(self.results_dir, "01_internal_ai_summary_result.json")
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(self.results, f, ensure_ascii=False, indent=2)
        print(f"\n📁 测试结果已保存: {filepath}")


def main():
    client = ApiClient()
    results_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "..", "..", "test-results", "internal")
    test = InternalAiSummaryTest(client, results_dir)
    results = test.run_all()

    passed = sum(1 for r in results if r["passed"])
    total = len(results)
    print(f"\n📊 Internal 测试汇总: {passed}/{total} 通过 ({(passed/total)*100:.1f}%)")
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
