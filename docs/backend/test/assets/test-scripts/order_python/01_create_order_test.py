"""
01_create_order_test.py - 订单创建接口测试
测试 Order 模块的创建接口：
- POST /api/v1/orders（创建订单）

测试用例：
1. TC-OC001: 正常创建订单（单个商品）
2. TC-OC002: 正常创建订单（多个商品）
3. TC-OC003: 创建订单时商品列表为空（应返回 400）
4. TC-OC004: 创建订单时商品不存在（应返回 404）
5. TC-OC005: 创建订单时库存不足（应返回 409）
6. TC-OC006: 未携带 Token 创建订单（应返回 401）
7. TC-OC007: 创建订单时收货信息为空（应返回 400）
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


def print_response_error(prefix, resp):
    if resp.response:
        print(f"  {prefix} status_code={resp.status_code}, body={json.dumps(resp.response, ensure_ascii=False)}")
    else:
        print(f"  {prefix} status_code={resp.status_code}, body=<empty>")


def main():
    global PASS_COUNT, FAIL_COUNT, RESULTS
    PASS_COUNT = 0
    FAIL_COUNT = 0
    RESULTS = []

    timestamp = datetime.now().strftime("%Y%m%d%H%M%S")

    print("=" * 40)
    print("  订单创建接口测试")
    print("=" * 40)

    # ============================================
    # 准备：注册 MERCHANT（创建商品用）+ CUSTOMER（下单用）
    # ============================================
    print("\n--- 准备测试数据 ---")

    merchant_user = f"oc_merchant_{timestamp}"
    merchant_phone = f"1381000{timestamp[-6:]}"
    merchant_pwd = "password123"

    reg_m = client.register_user(merchant_user, merchant_phone, merchant_pwd, "MERCHANT")
    assert_test("准备: 商家注册成功",
                reg_m.success and reg_m.response and reg_m.response.get("success") is True,
                f"username={merchant_user}")
    if not (reg_m.success and reg_m.response and reg_m.response.get("success")):
        print_response_error("  商家注册失败:", reg_m)

    token_m = client.get_access_token(merchant_user, merchant_pwd)
    assert_test("准备: 商家登录成功", token_m is not None, "")
    headers_m = client.make_auth_headers(token_m) if token_m else {}

    buyer_user = f"oc_buyer_{timestamp}"
    buyer_phone = f"1382000{timestamp[-6:]}"
    buyer_pwd = "password123"

    reg_b = client.register_user(buyer_user, buyer_phone, buyer_pwd, "CUSTOMER")
    assert_test("准备: 买家注册成功",
                reg_b.success and reg_b.response and reg_b.response.get("success") is True,
                f"username={buyer_user}")
    if not (reg_b.success and reg_b.response and reg_b.response.get("success")):
        print_response_error("  买家注册失败:", reg_b)

    token_b = client.get_access_token(buyer_user, buyer_pwd)
    assert_test("准备: 买家登录成功", token_b is not None, "")
    headers_b = client.make_auth_headers(token_b) if token_b else {}

    # 商家创建商品
    product_id_1 = None
    product_id_2 = None
    product_id_low = None

    p1 = client.post("/api/v1/merchant/products", {
        "name": f"订单测试商品A_{timestamp}",
        "description": "用于创建订单测试的商品A",
        "categoryId": "c_accessory",
        "price": 99.99,
        "stock": 100
    }, headers=headers_m)

    if p1.success and p1.response and p1.response.get("success"):
        product_id_1 = p1.response.get("data", {}).get("productId")
        print(f"  ✅ 创建商品A: {product_id_1}")
    else:
        print_response_error("  ❌ 创建商品A失败:", p1)

    p2 = client.post("/api/v1/merchant/products", {
        "name": f"订单测试商品B_{timestamp}",
        "description": "用于创建订单测试的商品B",
        "categoryId": "c_accessory",
        "price": 199.00,
        "stock": 50
    }, headers=headers_m)

    if p2.success and p2.response and p2.response.get("success"):
        product_id_2 = p2.response.get("data", {}).get("productId")
        print(f"  ✅ 创建商品B: {product_id_2}")
    else:
        print_response_error("  ❌ 创建商品B失败:", p2)

    p3 = client.post("/api/v1/merchant/products", {
        "name": f"订单测试低库存商品_{timestamp}",
        "description": "库存只有1件",
        "categoryId": "c_accessory",
        "price": 9.99,
        "stock": 1
    }, headers=headers_m)

    if p3.success and p3.response and p3.response.get("success"):
        product_id_low = p3.response.get("data", {}).get("productId")
        print(f"  ✅ 创建低库存商品: {product_id_low}")
    else:
        print_response_error("  ❌ 创建低库存商品失败:", p3)

    print("\n" + "=" * 40)
    print("  测试用例执行")
    print("=" * 40)

    # ============================================
    # TC-OC001: 正常创建订单（单个商品）
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OC001: 正常创建订单（单个商品）")
    print("─" * 40)

    if product_id_1 and token_b:
        o1 = client.post("/api/v1/orders", {
            "items": [
                {"productId": product_id_1, "quantity": 2}
            ],
            "receiver": {
                "name": "张三",
                "phone": "13800138000",
                "address": "北京市朝阳区测试地址1"
            }
        }, headers=headers_b)

        assert_test("TC-OC001: 创建订单成功",
                    o1.success and o1.response and o1.response.get("success") is True,
                    f"status_code={o1.status_code}")
        if not (o1.success and o1.response and o1.response.get("success")):
            print_response_error("  ", o1)

        if o1.success and o1.response and o1.response.get("success"):
            data = o1.response.get("data")
            if data:
                order_id_1 = data.get("orderId")
                assert_test("TC-OC001: 返回 orderId", order_id_1 is not None,
                            f"orderId={order_id_1}")
                assert_test("TC-OC001: orderId 以 o 开头",
                            order_id_1 is not None and order_id_1.startswith("o"),
                            f"orderId={order_id_1}")
                assert_test("TC-OC001: 返回 status=CREATED",
                            data.get("status") == "CREATED",
                            f"status={data.get('status')}")
                assert_test("TC-OC001: 返回 totalAmount",
                            data.get("totalAmount") is not None,
                            f"totalAmount={data.get('totalAmount')}")
                assert_test("TC-OC001: 返回 items",
                            data.get("items") is not None and len(data.get("items")) == 1,
                            f"items_count={len(data.get('items', []))}")
                assert_test("TC-OC001: 返回 receiver",
                            data.get("receiver") is not None,
                            f"receiver={data.get('receiver')}")
                assert_test("TC-OC001: 返回 createdAt",
                            data.get("createdAt") is not None,
                            f"createdAt={data.get('createdAt')}")

                # 金额：99.99 * 2 = 199.98
                assert_test("TC-OC001: 金额正确",
                            data.get("totalAmount") == "199.98",
                            f"totalAmount={data.get('totalAmount')}")

                # 验证订单项
                items = data.get("items", [])
                if len(items) > 0:
                    item = items[0]
                    assert_test("TC-OC001: 订单项 productId",
                                item.get("productId") == product_id_1,
                                f"productId={item.get('productId')}")
                    assert_test("TC-OC001: 订单项 name",
                                item.get("name") and "订单测试商品A" in item.get("name", ""),
                                f"name={item.get('name')}")
                    assert_test("TC-OC001: 订单项 unitPrice",
                                item.get("unitPrice") == "99.99",
                                f"unitPrice={item.get('unitPrice')}")
                    assert_test("TC-OC001: 订单项 quantity=2",
                                item.get("quantity") == 2,
                                f"quantity={item.get('quantity')}")
    else:
        assert_test("TC-OC001: 创建订单（跳过）", False,
                    "前置条件不满足：商品或买家Token不存在")

    # ============================================
    # TC-OC002: 正常创建订单（多个商品）
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OC002: 正常创建订单（多个商品）")
    print("─" * 40)

    if product_id_1 and product_id_2 and token_b:
        o2 = client.post("/api/v1/orders", {
            "items": [
                {"productId": product_id_1, "quantity": 1},
                {"productId": product_id_2, "quantity": 3}
            ],
            "receiver": {
                "name": "李四",
                "phone": "13900139000",
                "address": "上海市浦东新区测试地址2"
            }
        }, headers=headers_b)

        assert_test("TC-OC002: 创建多商品订单成功",
                    o2.success and o2.response and o2.response.get("success") is True,
                    f"status_code={o2.status_code}")
        if not (o2.success and o2.response and o2.response.get("success")):
            print_response_error("  ", o2)

        if o2.success and o2.response and o2.response.get("success"):
            data = o2.response.get("data")
            if data:
                items = data.get("items", [])
                assert_test("TC-OC002: 返回2个订单项",
                            len(items) == 2,
                            f"items_count={len(items)}")
                # 金额：99.99*1 + 199.00*3 = 99.99 + 597.00 = 696.99
                assert_test("TC-OC002: 金额正确",
                            data.get("totalAmount") == "696.99",
                            f"totalAmount={data.get('totalAmount')}")
    else:
        assert_test("TC-OC002: 创建多商品订单（跳过）", False,
                    "前置条件不满足：商品或买家Token不存在")

    # ============================================
    # TC-OC003: 商品列表为空
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OC003: 商品列表为空（应返回 400）")
    print("─" * 40)

    if token_b:
        o3 = client.post("/api/v1/orders", {
            "items": [],
            "receiver": {
                "name": "王五",
                "phone": "13700137000",
                "address": "广州市天河区测试地址3"
            }
        }, headers=headers_b)

        assert_test("TC-OC003: 返回 400",
                    o3.success is False and o3.status_code == 400,
                    f"status_code={o3.status_code}")
        if o3.status_code != 400:
            print_response_error("  ", o3)
    else:
        assert_test("TC-OC003: 空商品列表（跳过）", False,
                    "前置条件不满足：买家Token不存在")

    # ============================================
    # TC-OC004: 商品不存在
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OC004: 商品不存在（应返回 404）")
    print("─" * 40)

    if token_b:
        o4 = client.post("/api/v1/orders", {
            "items": [
                {"productId": f"p_nonexist_{timestamp}", "quantity": 1}
            ],
            "receiver": {
                "name": "赵六",
                "phone": "13600136000",
                "address": "深圳市南山区测试地址4"
            }
        }, headers=headers_b)

        assert_test("TC-OC004: 返回 404",
                    o4.success is False and o4.status_code == 404,
                    f"status_code={o4.status_code}")
        if o4.status_code != 404:
            print_response_error("  ", o4)
    else:
        assert_test("TC-OC004: 商品不存在（跳过）", False,
                    "前置条件不满足：买家Token不存在")

    # ============================================
    # TC-OC005: 库存不足
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OC005: 库存不足（应返回 409）")
    print("─" * 40)

    if product_id_low and token_b:
        # 先买走唯一的库存
        o5a = client.post("/api/v1/orders", {
            "items": [
                {"productId": product_id_low, "quantity": 1}
            ],
            "receiver": {
                "name": "孙七",
                "phone": "13500135000",
                "address": "杭州市西湖区测试地址5"
            }
        }, headers=headers_b)

        if o5a.success and o5a.response and o5a.response.get("success"):
            print("  ✅ 第一次购买成功（消耗了唯一库存）")
        else:
            print_response_error("  ⚠️ 第一次购买结果:", o5a)

        # 再次购买同一商品（库存已为0）
        o5b = client.post("/api/v1/orders", {
            "items": [
                {"productId": product_id_low, "quantity": 1}
            ],
            "receiver": {
                "name": "孙七",
                "phone": "13500135000",
                "address": "杭州市西湖区测试地址5"
            }
        }, headers=headers_b)

        assert_test("TC-OC005: 库存不足返回 409",
                    o5b.success is False and o5b.status_code == 409,
                    f"status_code={o5b.status_code}")
        if o5b.status_code != 409:
            print_response_error("  ", o5b)
    else:
        assert_test("TC-OC005: 库存不足（跳过）", False,
                    "前置条件不满足：低库存商品或买家Token不存在")

    # ============================================
    # TC-OC006: 未携带 Token
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OC006: 未携带 Token（应返回 401）")
    print("─" * 40)

    o6 = client.post("/api/v1/orders", {
        "items": [
            {"productId": "p_test", "quantity": 1}
        ],
        "receiver": {
            "name": "无Token用户",
            "phone": "13400134000",
            "address": "无Token测试地址"
        }
    })

    assert_test("TC-OC006: 返回 401",
                o6.success is False and o6.status_code == 401,
                f"status_code={o6.status_code}")
    if o6.status_code != 401:
        print_response_error("  ", o6)

    # ============================================
    # TC-OC007: 收货信息为空
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OC007: 收货信息为空（应返回 400）")
    print("─" * 40)

    if token_b:
        o7 = client.post("/api/v1/orders", {
            "items": [
                {"productId": product_id_1 if product_id_1 else "p_test", "quantity": 1}
            ],
            "receiver": None
        }, headers=headers_b)

        assert_test("TC-OC007: 返回 400",
                    o7.success is False and o7.status_code == 400,
                    f"status_code={o7.status_code}")
        if o7.status_code != 400:
            print_response_error("  ", o7)
    else:
        assert_test("TC-OC007: 收货信息为空（跳过）", False,
                    "前置条件不满足：买家Token不存在")

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

    result_obj = {
        "TestSuite": "订单创建接口测试",
        "Timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "Total": total,
        "Passed": PASS_COUNT,
        "Failed": FAIL_COUNT,
        "Results": RESULTS
    }
    output_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "01_create_order_result.json")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result_obj, f, ensure_ascii=False, indent=2)
    print(f"\n结果已保存到: 01_create_order_result.json")

    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
