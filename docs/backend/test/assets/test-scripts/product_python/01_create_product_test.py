"""
01_create_product_test.py - 商品创建接口测试
测试 Product 模块的创建接口：
- POST /api/v1/merchant/products（商家创建商品）

测试用例：
1. TC-PC001: 正常创建商品（完整字段）
2. TC-PC002: 正常创建商品（仅必填字段）
3. TC-PC003: 创建商品时名称为空（应返回 400）
4. TC-PC004: 创建商品时价格为 0（应返回 400）
5. TC-PC005: 创建商品时库存为负数（应返回 400）
6. TC-PC006: 未携带 Token 创建商品（应返回 401）
7. TC-PC007: CUSTOMER 角色创建商品（应返回 403）
"""
import sys
import os
import time
from datetime import datetime

# 添加父目录到路径，以便导入 api_client
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from api_client import ApiClient

BASE_URL = "http://localhost:8080"
client = ApiClient(BASE_URL)

PASS_COUNT = 0
FAIL_COUNT = 0
RESULTS = []


def assert_test(name, condition, detail=""):
    """断言测试结果"""
    global PASS_COUNT, FAIL_COUNT
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
    print("  商品创建接口测试")
    print("=" * 40)

    # ============================================
    # 准备：注册并登录 MERCHANT 用户
    # ============================================
    print("\n--- 准备测试数据 ---")

    merchant_user = f"create_test_{timestamp}"
    merchant_phone = f"1381000{timestamp[-6:]}"
    merchant_pwd = "password123"

    reg_result = client.register_user(merchant_user, merchant_phone, merchant_pwd, "MERCHANT")
    assert_test("准备: 商家注册成功",
                reg_result.success and reg_result.response and reg_result.response.get("success") is True,
                f"username={merchant_user}")

    token = client.get_access_token(merchant_user, merchant_pwd)
    assert_test("准备: 商家登录成功", token is not None, "")
    headers = client.make_auth_headers(token) if token else {}

    # 注册 CUSTOMER 用户（用于 TC-PC007）
    customer_user = f"create_cust_{timestamp}"
    customer_phone = f"1381001{timestamp[-6:]}"
    client.register_user(customer_user, customer_phone, merchant_pwd, "CUSTOMER")
    customer_token = client.get_access_token(customer_user, merchant_pwd)
    customer_headers = client.make_auth_headers(customer_token) if customer_token else {}

    print("\n" + "=" * 40)
    print("  测试用例执行")
    print("=" * 40)

    # ============================================
    # TC-PC001: 正常创建商品（完整字段）
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-PC001: 正常创建商品（完整字段）")
    print("─" * 40)

    p1 = client.post("/api/v1/merchant/products", {
        "name": f"测试商品_完整字段_{timestamp}",
        "description": "这是一个完整的商品描述",
        "categoryId": "c_headphone",
        "price": 299.00,
        "stock": 100,
        "tags": ["蓝牙", "降噪"],
        "imageUrls": ["https://example.com/img/1.jpg", "https://example.com/img/2.jpg"]
    }, headers=headers)

    assert_test("TC-PC001: 创建成功",
                p1.success and p1.response and p1.response.get("success") is True, "")

    product_id1 = None
    if p1.success and p1.response and p1.response.get("success"):
        data = p1.response.get("data")
        if data:
            # 后端返回 camelCase: productId
            product_id1 = data.get("productId")

    assert_test("TC-PC001: 返回 productId", product_id1 is not None,
                f"productId={product_id1}")
    assert_test("TC-PC001: productId 以 p 开头",
                product_id1 is not None and product_id1.startswith("p"),
                f"productId={product_id1}")

    if p1.success and p1.response and p1.response.get("success"):
        data = p1.response.get("data")
        if data:
            # 后端返回 camelCase: status, vectorIndexStatus
            assert_test("TC-PC001: 返回 status=ON_SALE",
                        data.get("status") == "ON_SALE",
                        f"status={data.get('status')}")
            assert_test("TC-PC001: 返回 vectorIndexStatus",
                        data.get("vectorIndexStatus") == "PENDING",
                        f"vectorIndexStatus={data.get('vectorIndexStatus')}")

    # ============================================
    # TC-PC002: 正常创建商品（仅必填字段）
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-PC002: 正常创建商品（仅必填字段）")
    print("─" * 40)

    p2 = client.post("/api/v1/merchant/products", {
        "name": f"测试商品_必填字段_{timestamp}",
        "description": "仅必填字段测试",
        "categoryId": "c_phone",
        "price": 3999.00,
        "stock": 50
    }, headers=headers)

    assert_test("TC-PC002: 创建成功",
                p2.success and p2.response and p2.response.get("success") is True, "")

    product_id2 = None
    if p2.success and p2.response and p2.response.get("success"):
        data = p2.response.get("data")
        if data:
            # 后端返回 camelCase: productId
            product_id2 = data.get("productId")
    assert_test("TC-PC002: 返回 productId", product_id2 is not None,
                f"productId={product_id2}")

    # ============================================
    # TC-PC003: 名称为空
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-PC003: 名称为空（应返回 400）")
    print("─" * 40)

    p3 = client.post("/api/v1/merchant/products", {
        "name": "",
        "description": "名称为空",
        "categoryId": "c_accessory",
        "price": 10.00,
        "stock": 10
    }, headers=headers)

    assert_test("TC-PC003: 返回 400",
                p3.success is False and p3.status_code == 400,
                f"statusCode={p3.status_code}")

    if p3.response:
        assert_test("TC-PC003: success=false",
                    p3.response.get("success") is False,
                    f"success={p3.response.get('success')}")

    # ============================================
    # TC-PC004: 价格为 0
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-PC004: 价格为 0（应返回 400）")
    print("─" * 40)

    p4 = client.post("/api/v1/merchant/products", {
        "name": f"零价格商品_{timestamp}",
        "description": "价格为零",
        "categoryId": "c_accessory",
        "price": 0,
        "stock": 10
    }, headers=headers)

    assert_test("TC-PC004: 返回 400",
                p4.success is False and p4.status_code == 400,
                f"statusCode={p4.status_code}")

    # ============================================
    # TC-PC005: 库存为负数
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-PC005: 库存为负数（应返回 400）")
    print("─" * 40)

    p5 = client.post("/api/v1/merchant/products", {
        "name": f"负库存商品_{timestamp}",
        "description": "库存为负",
        "categoryId": "c_accessory",
        "price": 10.00,
        "stock": -5
    }, headers=headers)

    assert_test("TC-PC005: 返回 400",
                p5.success is False and p5.status_code == 400,
                f"statusCode={p5.status_code}")

    # ============================================
    # TC-PC006: 未携带 Token
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-PC006: 未携带 Token（应返回 401）")
    print("─" * 40)

    p6 = client.post("/api/v1/merchant/products", {
        "name": f"无Token商品_{timestamp}",
        "description": "无Token测试",
        "categoryId": "c_accessory",
        "price": 10.00,
        "stock": 10
    })

    assert_test("TC-PC006: 返回 401",
                p6.success is False and p6.status_code == 401,
                f"statusCode={p6.status_code}")

    # ============================================
    # TC-PC007: CUSTOMER 角色创建商品
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-PC007: CUSTOMER 角色创建商品（应返回 403）")
    print("─" * 40)

    p7 = client.post("/api/v1/merchant/products", {
        "name": f"顾客创建商品_{timestamp}",
        "description": "顾客尝试创建商品",
        "categoryId": "c_accessory",
        "price": 10.00,
        "stock": 10
    }, headers=customer_headers)

    assert_test("TC-PC007: 返回 403",
                p7.success is False and p7.status_code == 403,
                f"statusCode={p7.status_code}")

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

    # 保存结果到文件
    result_obj = {
        "TestSuite": "商品创建接口测试",
        "Timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "Total": total,
        "Passed": PASS_COUNT,
        "Failed": FAIL_COUNT,
        "Results": RESULTS
    }
    import json
    output_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "01_create_product_result.json")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result_obj, f, ensure_ascii=False, indent=2)
    print(f"\n结果已保存到: 01_create_product_result.json")

    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
