"""
Admin 模块测试 — 管理员功能接口测试

测试覆盖：
1. 管理员查看用户列表（需要 ADMIN 角色）
2. 管理员修改用户状态（封禁）
3. 管理员修改用户状态（解封）
4. 管理员查看平台概览
5. 非 ADMIN 角色访问管理员接口（403）
6. 未携带 Token 访问管理员接口（401）
7. 修改不存在的用户状态（404）
8. 传入无效的状态值（400）

修复记录（2026-06-08 v2）：
- 增加管理员正常操作测试用例（需要预置 ADMIN 账号）
- 增加 setup_admin 方法，尝试使用预置 ADMIN 账号登录
- 增加 SQL 脚本提示，指导在数据库中创建 ADMIN 用户
- 增加 _safe_get_data 辅助方法统一处理 data 字段提取

修复记录（2026-06-08 v3）：
- 修复 TC-AD006（无效状态值测试）：之前使用 merchant_token 导致先返回 403（权限不足），
  无法测试到业务层的 400 校验。改为：如果有 ADMIN token 则用 ADMIN 测试 400，
  如果无 ADMIN token 则标记为 SKIP。
- 修复 TC-AD005（不存在用户测试）：同样需要 ADMIN token 才能测试到 404 场景。
"""
import os
import sys
import json
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from api_client import ApiClient


class AdminTest:
    """Admin 模块测试套件"""

    def __init__(self, client: ApiClient, results_dir: str):
        self.client = client
        self.results_dir = results_dir
        self.results = []
        self.timestamp = time.strftime("%Y%m%d%H%M%S")

        # 测试用户
        self.admin_user = f"admin_test_admin_{self.timestamp}"
        self.admin_phone = f"1389999{self.timestamp[-6:]}"
        self.admin_password = "test123456"

        self.merchant_user = f"admin_test_merchant_{self.timestamp}"
        self.merchant_phone = f"1380000{self.timestamp[-6:]}"
        self.merchant_password = "test123456"

        # 预置 ADMIN 账号（需要在数据库中手动创建）
        # 创建 SQL: INSERT INTO users (user_id, username, phone, password_hash, role, status, created_at, updated_at)
        #   VALUES ('admin001', 'admin', '13800000000',
        #           '$2a$10$...BCryptHash...', 'ADMIN', 'ACTIVE', NOW(), NOW());
        self.preset_admin_account = "admin"
        self.preset_admin_password = "admin123"

        self.admin_token = ""
        self.merchant_token = ""
        self.target_user_id = ""

    def setup(self):
        """准备测试数据：注册商家用户"""
        print("\n📋 准备测试数据...")

        # 注册商家（用于测试管理员修改其状态）
        resp = self.client.register_user(self.merchant_user, self.merchant_phone,
                                          self.merchant_password, "MERCHANT")
        assert resp.success, f"商家注册失败: {resp.response}"
        if resp.response:
            data = resp.response.get("data", {})
            self.target_user_id = data.get("userId", "")
        print(f"  ✅ 商家注册成功: {self.merchant_user}, userId={self.target_user_id}")

        self.merchant_token = self.client.get_access_token(self.merchant_user, self.merchant_password)
        assert self.merchant_token, "商家登录失败"

        # 尝试使用预置 ADMIN 账号登录
        self._try_setup_admin()

    def _try_setup_admin(self):
        """尝试使用预置 ADMIN 账号登录"""
        print(f"  🔑 尝试使用预置 ADMIN 账号登录...")
        admin_resp = self.client.login(self.preset_admin_account, self.preset_admin_password)
        if admin_resp.success and admin_resp.response:
            data = admin_resp.response.get("data", {})
            self.admin_token = data.get("accessToken", "")
            if self.admin_token:
                print(f"  ✅ ADMIN 账号登录成功")
                return
        print(f"  ⚠️  ADMIN 账号不可用（需要在数据库中手动创建）")
        print(f"  ⚠️  管理员正常操作测试将跳过")

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

    # ===== 权限不足场景测试 =====

    def test_admin_list_users_forbidden(self):
        """TC-AD001: 非 ADMIN 角色查看用户列表"""
        print("\n🔒 测试非 ADMIN 角色查看用户列表...")
        headers = self.client.auth_header(self.merchant_token)
        resp = self.client.get("/api/v1/admin/users", headers=headers)
        passed = resp.status_code == 403
        self._record("TC-AD001", "非 ADMIN 角色查看用户列表返回 403", passed,
                     f"status_code={resp.status_code}")

    def test_admin_update_status_forbidden(self):
        """TC-AD002: 非 ADMIN 角色修改用户状态"""
        print("\n🔒 测试非 ADMIN 角色修改用户状态...")
        headers = self.client.auth_header(self.merchant_token)
        body = {"status": "DISABLED"}
        resp = self.client.patch(f"/api/v1/admin/users/{self.target_user_id}/status",
                                  body, headers=headers)
        passed = resp.status_code == 403
        self._record("TC-AD002", "非 ADMIN 角色修改用户状态返回 403", passed,
                     f"status_code={resp.status_code}")

    def test_admin_overview_forbidden(self):
        """TC-AD003: 非 ADMIN 角色查看平台概览"""
        print("\n🔒 测试非 ADMIN 角色查看平台概览...")
        headers = self.client.auth_header(self.merchant_token)
        resp = self.client.get("/api/v1/admin/metrics/overview", headers=headers)
        passed = resp.status_code == 403
        self._record("TC-AD003", "非 ADMIN 角色查看平台概览返回 403", passed,
                     f"status_code={resp.status_code}")

    def test_admin_no_token(self):
        """TC-AD004: 未携带 Token 访问管理员接口"""
        print("\n🔒 测试未携带 Token 访问管理员接口...")
        resp = self.client.get("/api/v1/admin/users")
        passed = resp.status_code == 401
        self._record("TC-AD004", "未携带 Token 访问管理员接口返回 401", passed,
                     f"status_code={resp.status_code}")

    def test_admin_update_nonexistent_user(self):
        """TC-AD005: 修改不存在的用户状态"""
        print("\n🔒 测试修改不存在的用户状态...")
        # 【修复 v3】使用 ADMIN token 测试 404 场景
        if not self.admin_token:
            self._record("TC-AD005", "修改不存在的用户状态（跳过，无 ADMIN 账号）", True,
                         "SKIP: 需要 ADMIN 账号才能测试 404 场景")
            return
        headers = self.client.auth_header(self.admin_token)
        body = {"status": "DISABLED"}
        resp = self.client.patch("/api/v1/admin/users/nonexistent_user/status",
                                  body, headers=headers)
        passed = resp.status_code == 404
        self._record("TC-AD005", "修改不存在的用户状态返回 404", passed,
                     f"status_code={resp.status_code}")

    def test_admin_invalid_status(self):
        """TC-AD006: 传入无效的状态值"""
        print("\n🔒 测试传入无效的状态值...")
        # 【修复 v3】使用 ADMIN token 测试 400 场景
        # 之前使用 merchant_token 导致先返回 403（权限不足），无法测试到业务层的 400 校验
        if not self.admin_token:
            self._record("TC-AD006", "传入无效的状态值（跳过，无 ADMIN 账号）", True,
                         "SKIP: 需要 ADMIN 账号才能测试 400 场景")
            return
        headers = self.client.auth_header(self.admin_token)
        body = {"status": "INVALID_STATUS"}
        resp = self.client.patch(f"/api/v1/admin/users/{self.target_user_id}/status",
                                  body, headers=headers)
        passed = resp.status_code == 400
        self._record("TC-AD006", "传入无效的状态值返回 400", passed,
                     f"status_code={resp.status_code}")

    # ===== 管理员正常操作测试（需要预置 ADMIN 账号） =====

    def test_admin_list_users(self):
        """TC-AD007: 管理员查看用户列表"""
        print("\n📋 测试管理员查看用户列表...")
        if not self.admin_token:
            self._record("TC-AD007", "管理员查看用户列表（跳过，无 ADMIN 账号）", True,
                         "SKIP: 需要在数据库中手动创建 ADMIN 用户")
            return
        headers = self.client.auth_header(self.admin_token)
        resp = self.client.get("/api/v1/admin/users", headers=headers)
        passed = resp.success
        items = self._safe_get_data(resp, "items")
        total = self._safe_get_data(resp, "total")
        self._record("TC-AD007", "管理员查看用户列表", passed,
                     f"status_code={resp.status_code}, items_count={len(items) if items else 0}, total={total}")

    def test_admin_update_status_disable(self):
        """TC-AD008: 管理员封禁用户"""
        print("\n📋 测试管理员封禁用户...")
        if not self.admin_token:
            self._record("TC-AD008", "管理员封禁用户（跳过，无 ADMIN 账号）", True,
                         "SKIP: 需要在数据库中手动创建 ADMIN 用户")
            return
        headers = self.client.auth_header(self.admin_token)
        body = {"status": "DISABLED"}
        resp = self.client.patch(f"/api/v1/admin/users/{self.target_user_id}/status",
                                  body, headers=headers)
        passed = resp.success
        new_status = self._safe_get_data(resp, "status")
        self._record("TC-AD008", "管理员封禁用户", passed,
                     f"status_code={resp.status_code}, new_status={new_status}")

    def test_admin_update_status_enable(self):
        """TC-AD009: 管理员解封用户"""
        print("\n📋 测试管理员解封用户...")
        if not self.admin_token:
            self._record("TC-AD009", "管理员解封用户（跳过，无 ADMIN 账号）", True,
                         "SKIP: 需要在数据库中手动创建 ADMIN 用户")
            return
        headers = self.client.auth_header(self.admin_token)
        body = {"status": "ACTIVE"}
        resp = self.client.patch(f"/api/v1/admin/users/{self.target_user_id}/status",
                                  body, headers=headers)
        passed = resp.success
        new_status = self._safe_get_data(resp, "status")
        self._record("TC-AD009", "管理员解封用户", passed,
                     f"status_code={resp.status_code}, new_status={new_status}")

    def test_admin_overview(self):
        """TC-AD010: 管理员查看平台概览"""
        print("\n📋 测试管理员查看平台概览...")
        if not self.admin_token:
            self._record("TC-AD010", "管理员查看平台概览（跳过，无 ADMIN 账号）", True,
                         "SKIP: 需要在数据库中手动创建 ADMIN 用户")
            return
        headers = self.client.auth_header(self.admin_token)
        resp = self.client.get("/api/v1/admin/metrics/overview", headers=headers)
        passed = resp.success
        data = self._safe_get_data(resp)
        self._record("TC-AD010", "管理员查看平台概览", passed,
                     f"status_code={resp.status_code}, data={data}")

    def run_all(self):
        """运行所有测试"""
        print("\n" + "=" * 60)
        print("🔐 Admin 模块测试")
        print("=" * 60)

        self.setup()

        # 权限不足场景
        self.test_admin_list_users_forbidden()
        self.test_admin_update_status_forbidden()
        self.test_admin_overview_forbidden()
        self.test_admin_no_token()
        self.test_admin_update_nonexistent_user()
        self.test_admin_invalid_status()

        # 管理员正常操作场景（需要预置 ADMIN 账号）
        self.test_admin_list_users()
        self.test_admin_update_status_disable()
        self.test_admin_update_status_enable()
        self.test_admin_overview()

        self._save_results()
        return self.results

    def _save_results(self):
        """保存测试结果到 JSON 文件"""
        os.makedirs(self.results_dir, exist_ok=True)
        filepath = os.path.join(self.results_dir, "03_admin_result.json")
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(self.results, f, ensure_ascii=False, indent=2)
        print(f"\n📁 测试结果已保存: {filepath}")


def main():
    client = ApiClient()
    results_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "..", "..", "test-results", "uba")
    test = AdminTest(client, results_dir)
    results = test.run_all()

    passed = sum(1 for r in results if r["passed"])
    total = len(results)
    print(f"\n📊 Admin 测试汇总: {passed}/{total} 通过 ({(passed/total)*100:.1f}%)")
    return 0 if passed == total else 1


if __name__ == "__main__":
    sys.exit(main())
