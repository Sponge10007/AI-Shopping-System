"""
02_query_product_test.py - 商品查询接口测试
测试 Product 模块的查询接口：
- GET /api/v1/products（公开-在售商品列表）
- GET /api/v1/products/{product_id}（公开-商品详情）
- GET /api/v1/merchant/products（商家-自己的商品列表）

测试用例：
1. TC-PQ001: 查询在售商品列表（公开）
2. TC-PQ002: 查询在售商品列表（按分类筛选）
3. TC-PQ003: 查询在售商品列表（按价格排序）
4. TC-PQ004: 查询商品详情（公开）
5. TC-PQ005: 查询不存在的商品详情（应返回 404）
6. TC-PQ006: 商家查询自己的商品列表
7. TC-PQ007: 商家按状态筛选自己的商品
8. TC-PQ008: 分页参数测试
"""
import sys
import os
import json
from datetime import datetime

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from api_client import ApiClient

BASE_URL = "http://localhost:8080"
client = ApiClient(BASE_URL)

PASS_COUNT = 0
FAIL_COUNT = 0
RESULTS = []


def assert_test(name, condition, detail=""):
    global PASS_COUNT, FAIL_COUNT, RESULTS
    if condition:
        print(f"  ✅ 通过: {name}")
        PASS_COUNT += 1
        RESULTS.append({"Name": name, "Status": "PASS", "Detail": detail})
    else:
        print(f"  ❌ 失败: {name}")
        FAIL_COUNT += 1
        RESULTS.append({"Name": name, "Status": "FAIL", "Detail": detail})


def main():
    global PASS_COUNT, FAIL_COUNT, RESULTS
    PASS_COUNT = 0
    FAIL_COUNT = 0
    RESULTS = []

    timestamp = datetime.now().strftime("%Y%m%d%H%M%S")

    print("=" * 40)
    print("  商品查询接口测试")
    print("=" * 40)

    # ============================================
    # 准备：注册商家 + 创建测试商品
    # ============================================
    print("\n--- 准备测试数据 ---")

    merchant_user = f"query_test_{timestamp}"
    merchant_phone = f"1382000{timestamp[-6:]}"
    merchant_pwd = "password123"

    reg_result = client.register_user(merchant_user, merchant_phone, merchant_pwd, "MERCHANT")
    assert_test("准备: 商家注册成功",
                reg_result.success and reg_result.response and reg_result.response.get("success") is True, "")

    token = client.get_access_token(merchant_user, merchant_pwd)
    assert_test("准备: 商家登录成功", token is not None, "")
    headers = client.make_auth_headers(token) if token else {}

    # 创建 3 个不同分类的商品
    created_product_ids = []
    test_products = [
        {"name": f"查询测试耳机_{timestamp}", "description": "测试耳机",
         "categoryId": "c_headphone", "price": 199.99, "stock": 100, "tags": ["耳机"]},
        {"name": f"查询测试手机_{timestamp}", "description": "测试手机",
         "categoryId": "c_phone", "price": 3999.00, "stock": 50, "tags": ["手机"]},
        {"name": f"查询测试电脑_{timestamp}", "description": "测试电脑",
         "categoryId": "c_computer", "price": 6999.00, "stock": 30, "tags": ["电脑"]}
    ]

    for prod in test_products:
        result = client.post("/api/v1/merchant/products", prod, headers=headers)
        if result.success and result.response and result.response.get("success"):
            data = result.response.get("data")
            if data:
                # 后端返回 camelCase: productId
                pid = data.get("productId")
                created_product_ids.append(pid)
                print(f"  ✅ 创建商品: {prod['name']} -> {pid}")
            else:
                created_product_ids.append(None)
                print(f"  ❌ 创建商品失败: {prod['name']} (无 data)")
        else:
            created_product_ids.append(None)
            print(f"  ❌ 创建商品失败: {prod['name']} (status={result.status_code})")

    print("\n" + "=" * 40)
    print("  测试用例执行")
    print("=" * 40)

    # ============================================
    # TC-PQ001: 查询在售商品列表（公开）
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-PQ001: 查询在售商品列表（公开）")
    print("─" * 40)

    q1 = client.get("/api/v1/products")
    assert_test("TC-PQ001: 查询成功",
                q1.success and q1.response and q1.response.get("success") is True, "")

    if q1.success and q1.response and q1.response.get("success"):
        data = q1.response.get("data")
        if data:
            assert_test("TC-PQ001: 返回 total", data.get("total", -1) >= 0,
                        f"total={data.get('total')}")
            assert_test("TC-PQ001: 返回 page", data.get("page", -1) >= 1,
                        f"page={data.get('page')}")
            items = data.get("items", [])
            assert_test("TC-PQ001: 返回 items", len(items) >= 0,
                        f"count={len(items)}")

    # ============================================
    # TC-PQ002: 按分类筛选
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-PQ002: 按分类筛选（c_headphone）")
    print("─" * 40)

    q2 = client.get("/api/v1/products?categoryId=c_headphone")
    assert_test("TC-PQ002: 分类筛选成功",
                q2.success and q2.response and q2.response.get("success") is True, "")

    # ============================================
    # TC-PQ003: 按价格排序
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-PQ003: 按价格升序排列")
    print("─" * 40)

    q3 = client.get("/api/v1/products?sortBy=price&sortOrder=asc")
    assert_test("TC-PQ003: 排序查询成功",
                q3.success and q3.response and q3.response.get("success") is True, "")

    # ============================================
    # TC-PQ004: 查询商品详情（公开）
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-PQ004: 查询商品详情")
    print("─" * 40)

    detail_id = created_product_ids[0] if len(created_product_ids) > 0 and created_product_ids[0] else None
    if detail_id:
        q4 = client.get(f"/api/v1/products/{detail_id}")
        assert_test("TC-PQ004: 查询详情成功",
                    q4.success and q4.response and q4.response.get("success") is True, "")

        if q4.success and q4.response and q4.response.get("success"):
            data = q4.response.get("data")
            if data:
                # 后端返回 camelCase: name, categoryId, price, stock, status
                assert_test("TC-PQ004: 返回 name", bool(data.get("name")),
                            f"name={data.get('name')}")
                assert_test("TC-PQ004: 返回 categoryId", bool(data.get("categoryId")),
                            f"categoryId={data.get('categoryId')}")
                assert_test("TC-PQ004: 返回 price", data.get("price") is not None,
                            f"price={data.get('price')}")
                assert_test("TC-PQ004: 返回 stock", data.get("stock", -1) >= 0,
                            f"stock={data.get('stock')}")
                assert_test("TC-PQ004: 返回 status", bool(data.get("status")),
                            f"status={data.get('status')}")
    else:
        assert_test("TC-PQ004: 查询详情（跳过-无商品ID）", False,
                    "前置条件不满足：无可用商品ID")

    # ============================================
    # TC-PQ005: 查询不存在的商品
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-PQ005: 查询不存在的商品（应返回 404）")
    print("─" * 40)

    q5 = client.get(f"/api/v1/products/p_nonexistent_{timestamp}")
    assert_test("TC-PQ005: 返回 404",
                q5.success is False and q5.status_code == 404,
                f"statusCode={q5.status_code}")

    # ============================================
    # TC-PQ006: 商家查询自己的商品列表
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-PQ006: 商家查询自己的商品列表")
    print("─" * 40)

    q6 = client.get("/api/v1/merchant/products", headers=headers)
    assert_test("TC-PQ006: 商家查询成功",
                q6.success and q6.response and q6.response.get("success") is True, "")

    if q6.success and q6.response and q6.response.get("success"):
        data = q6.response.get("data")
        if data:
            items = data.get("items", [])
            assert_test("TC-PQ006: 返回的商品数 >= 3", len(items) >= 3,
                        f"count={len(items)}")

    # ============================================
    # TC-PQ007: 商家按状态筛选
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-PQ007: 商家按状态筛选（ON_SALE）")
    print("─" * 40)

    q7 = client.get("/api/v1/merchant/products?status=ON_SALE", headers=headers)
    assert_test("TC-PQ007: 状态筛选成功",
                q7.success and q7.response and q7.response.get("success") is True, "")

    # ============================================
    # TC-PQ008: 分页参数测试
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-PQ008: 分页参数测试（page=1, size=2）")
    print("─" * 40)

    q8 = client.get("/api/v1/products?page=1&size=2")
    assert_test("TC-PQ008: 分页查询成功",
                q8.success and q8.response and q8.response.get("success") is True, "")

    if q8.success and q8.response and q8.response.get("success"):
        data = q8.response.get("data")
        if data:
            items = data.get("items", [])
            assert_test("TC-PQ008: 返回条数 <= 2", len(items) <= 2,
                        f"count={len(items)}")

    # ============================================
    # 汇总报告
    # ============================================
    print("\n\n" + "=" * 40)
    print("  测试汇总报告")
    print("=" * 40)
    total = PASS_COUNT + FAIL_COUNT
    print(f"总计: {total}  |  通过: {PASS_COUNT}  |  失败: {FAIL_COUNT}")

    print("")
    for r in RESULTS:
        status_tag = "PASS" if r["Status"] == "PASS" else "FAIL"
        print(f"[{status_tag}] {r['Name']} - {r['Detail']}")

    # 保存结果
    result_obj = {
        "TestSuite": "商品查询接口测试",
        "Timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "Total": total,
        "Passed": PASS_COUNT,
        "Failed": FAIL_COUNT,
        "Results": RESULTS
    }
    output_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "02_query_product_result.json")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result_obj, f, ensure_ascii=False, indent=2)
    print(f"\n结果已保存到: 02_query_product_result.json")

    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
