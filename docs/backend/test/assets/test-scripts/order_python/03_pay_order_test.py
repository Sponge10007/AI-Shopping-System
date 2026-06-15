"""
03_pay_order_test.py - 订单支付接口测试
测试 Order 模块的支付接口：
- POST /api/v1/orders/{orderId}/pay（支付订单）

测试用例：
1. TC-OP001: 正常支付订单
2. TC-OP002: 支付已支付的订单（应返回 409）
3. TC-OP003: 支付不存在的订单（应返回 404）
4. TC-OP004: 支付其他用户的订单（应返回 403）
5. TC-OP005: 未携带 Token 支付订单（应返回 401）
6. TC-OP006: 支付时指定支付方式
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
    print("  订单支付接口测试")
    print("=" * 40)

    # ============================================
    # 准备：注册商家 + 买家A + 买家B
    # ============================================
    print("\n--- 准备测试数据 ---")

    merchant_user = f"op_merchant_{timestamp}"
    merchant_phone = f"1381000{timestamp[-6:]}"
    merchant_pwd = "password123"

    reg_m = client.register_user(merchant_user, merchant_phone, merchant_pwd, "MERCHANT")
    assert_test("准备: 商家注册成功",
                reg_m.success and reg_m.response and reg_m.response.get("success") is True, "")
    if not (reg_m.success and reg_m.response and reg_m.response.get("success")):
        print_response_error("  商家注册失败:", reg_m)

    token_m = client.get_access_token(merchant_user, merchant_pwd)
    headers_m = client.make_auth_headers(token_m) if token_m else {}

    buyer_a_user = f"op_buyer_a_{timestamp}"
    buyer_a_phone = f"1382000{timestamp[-6:]}"
    buyer_a_pwd = "password123"

    reg_ba = client.register_user(buyer_a_user, buyer_a_phone, buyer_a_pwd, "CUSTOMER")
    assert_test("准备: 买家A注册成功",
                reg_ba.success and reg_ba.response and reg_ba.response.get("success") is True, "")
    if not (reg_ba.success and reg_ba.response and reg_ba.response.get("success")):
        print_response_error("  买家A注册失败:", reg_ba)

    token_ba = client.get_access_token(buyer_a_user, buyer_a_pwd)
    headers_ba = client.make_auth_headers(token_ba) if token_ba else {}

    buyer_b_user = f"op_buyer_b_{timestamp}"
    buyer_b_phone = f"1383000{timestamp[-6:]}"
    buyer_b_pwd = "password123"

    reg_bb = client.register_user(buyer_b_user, buyer_b_phone, buyer_b_pwd, "CUSTOMER")
    assert_test("准备: 买家B注册成功",
                reg_bb.success and reg_bb.response and reg_bb.response.get("success") is True, "")
    if not (reg_bb.success and reg_bb.response and reg_bb.response.get("success")):
        print_response_error("  买家B注册失败:", reg_bb)

    token_bb = client.get_access_token(buyer_b_user, buyer_b_pwd)
    headers_bb = client.make_auth_headers(token_bb) if token_bb else {}

    # 商家创建商品
    product_id = None
    p1 = client.post("/api/v1/merchant/products", {
        "name": f"支付测试商品_{timestamp}",
        "description": "用于订单支付测试",
        "categoryId": "c_accessory",
        "price": 100.00,
        "stock": 500
    }, headers=headers_m)

    if p1.success and p1.response and p1.response.get("success"):
        product_id = p1.response.get("data", {}).get("productId")
        print(f"  ✅ 创建商品: {product_id}")
    else:
        print_response_error("  ❌ 创建商品失败:", p1)

    # 买家A创建3个订单
    order_id_pay = None
    order_id_paid = None
    order_id_pay_method = None

    if product_id and token_ba:
        o1 = client.post("/api/v1/orders", {
            "items": [{"productId": product_id, "quantity": 1}],
            "receiver": {
                "name": "支付测试_张三",
                "phone": "13800138000",
                "address": "支付测试地址1"
            }
        }, headers=headers_ba)

        if o1.success and o1.response and o1.response.get("success"):
            order_id_pay = o1.response.get("data", {}).get("orderId")
            print(f"  ✅ 创建待支付订单: {order_id_pay}")
        else:
            print_response_error("  ❌ 创建待支付订单失败:", o1)

        o2 = client.post("/api/v1/orders", {
            "items": [{"productId": product_id, "quantity": 1}],
            "receiver": {
                "name": "支付测试_李四",
                "phone": "13900139000",
                "address": "支付测试地址2"
            }
        }, headers=headers_ba)

        if o2.success and o2.response and o2.response.get("success"):
            order_id_paid = o2.response.get("data", {}).get("orderId")
            print(f"  ✅ 创建待支付订单2: {order_id_paid}")
            pay2 = client.post(f"/api/v1/orders/{order_id_paid}/pay",
                              {"method": "BALANCE"}, headers=headers_ba)
            if pay2.success and pay2.response and pay2.response.get("success"):
                print(f"  ✅ 订单2已支付成功")
            else:
                print_response_error("  ⚠️ 订单2支付结果:", pay2)
        else:
            print_response_error("  ❌ 创建订单2失败:", o2)

        o3 = client.post("/api/v1/orders", {
            "items": [{"productId": product_id, "quantity": 1}],
            "receiver": {
                "name": "支付测试_王五",
                "phone": "13700137000",
                "address": "支付测试地址3"
            }
        }, headers=headers_ba)

        if o3.success and o3.response and o3.response.get("success"):
            order_id_pay_method = o3.response.get("data", {}).get("orderId")
            print(f"  ✅ 创建待支付订单3: {order_id_pay_method}")
        else:
            print_response_error("  ❌ 创建订单3失败:", o3)

    print("\n" + "=" * 40)
    print("  测试用例执行")
    print("=" * 40)

    # ============================================
    # TC-OP001: 正常支付订单
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OP001: 正常支付订单")
    print("─" * 40)

    if order_id_pay and token_ba:
        p1 = client.post(f"/api/v1/orders/{order_id_pay}/pay",
                        {"method": "BALANCE"}, headers=headers_ba)
        assert_test("TC-OP001: 支付成功",
                    p1.success and p1.response and p1.response.get("success") is True,
                    f"status_code={p1.status_code}")
        if not (p1.success and p1.response and p1.response.get("success")):
            print_response_error("  ", p1)

        if p1.success and p1.response and p1.response.get("success"):
            data = p1.response.get("data")
            if data:
                assert_test("TC-OP001: 返回 orderId",
                            data.get("orderId") == order_id_pay,
                            f"orderId={data.get('orderId')}")
                assert_test("TC-OP001: 返回 paymentId",
                            data.get("paymentId") is not None,
                            f"paymentId={data.get('paymentId')}")
                assert_test("TC-OP001: paymentId 以 pay 开头",
                            data.get("paymentId", "").startswith("pay"),
                            f"paymentId={data.get('paymentId')}")
                assert_test("TC-OP001: 返回 status=PAID",
                            data.get("status") == "PAID",
                            f"status={data.get('status')}")
                assert_test("TC-OP001: 返回 amount",
                            data.get("amount") is not None,
                            f"amount={data.get('amount')}")

        check = client.get(f"/api/v1/orders/{order_id_pay}", headers=headers_ba)
        if check.success and check.response and check.response.get("success"):
            check_data = check.response.get("data")
            if check_data:
                assert_test("TC-OP001: 支付后订单状态为 PAID",
                            check_data.get("status") == "PAID",
                            f"status={check_data.get('status')}")
    else:
        assert_test("TC-OP001: 支付订单（跳过）", False,
                    "前置条件不满足：订单ID或买家AToken不存在")

    # ============================================
    # TC-OP002: 支付已支付的订单
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OP002: 支付已支付的订单（应返回 409）")
    print("─" * 40)

    if order_id_paid and token_ba:
        p2 = client.post(f"/api/v1/orders/{order_id_paid}/pay",
                        {"method": "BALANCE"}, headers=headers_ba)
        assert_test("TC-OP002: 重复支付返回 409",
                    p2.success is False and p2.status_code == 409,
                    f"status_code={p2.status_code}")
        if p2.status_code != 409:
            print_response_error("  ", p2)
    else:
        assert_test("TC-OP002: 重复支付（跳过）", False,
                    "前置条件不满足：已支付订单ID或买家AToken不存在")

    # ============================================
    # TC-OP003: 支付不存在的订单
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OP003: 支付不存在的订单（应返回 404）")
    print("─" * 40)

    if token_ba:
        p3 = client.post(f"/api/v1/orders/o_nonexist_{timestamp}/pay",
                        {"method": "BALANCE"}, headers=headers_ba)
        assert_test("TC-OP003: 返回 404",
                    p3.success is False and p3.status_code == 404,
                    f"status_code={p3.status_code}")
        if p3.status_code != 404:
            print_response_error("  ", p3)
    else:
        assert_test("TC-OP003: 支付不存在订单（跳过）", False,
                    "前置条件不满足：买家AToken不存在")

    # ============================================
    # TC-OP004: 支付其他用户的订单
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OP004: 支付其他用户的订单（应返回 403）")
    print("─" * 40)

    if order_id_pay and token_bb:
        p4 = client.post(f"/api/v1/orders/{order_id_pay}/pay",
                        {"method": "BALANCE"}, headers=headers_bb)
        assert_test("TC-OP004: 返回 403",
                    p4.success is False and p4.status_code == 403,
                    f"status_code={p4.status_code}")
        if p4.status_code != 403:
            print_response_error("  ", p4)
    else:
        assert_test("TC-OP004: 权限验证（跳过）", False,
                    "前置条件不满足：订单ID或买家BToken不存在")

    # ============================================
    # TC-OP005: 未携带 Token
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OP005: 未携带 Token 支付订单（应返回 401）")
    print("─" * 40)

    p5 = client.post(f"/api/v1/orders/o_test_order/pay",
                    {"method": "BALANCE"})
    assert_test("TC-OP005: 返回 401",
                p5.success is False and p5.status_code == 401,
                f"status_code={p5.status_code}")
    if p5.status_code != 401:
        print_response_error("  ", p5)

    # ============================================
    # TC-OP006: 指定支付方式
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OP006: 指定支付方式（WECHAT）")
    print("─" * 40)

    if order_id_pay_method and token_ba:
        p6 = client.post(f"/api/v1/orders/{order_id_pay_method}/pay",
                        {"method": "WECHAT"}, headers=headers_ba)
        assert_test("TC-OP006: 指定支付方式成功",
                    p6.success and p6.response and p6.response.get("success") is True,
                    f"status_code={p6.status_code}")
        if not (p6.success and p6.response and p6.response.get("success")):
            print_response_error("  ", p6)

        if p6.success and p6.response and p6.response.get("success"):
            data = p6.response.get("data")
            if data:
                assert_test("TC-OP006: 返回 status=PAID",
                            data.get("status") == "PAID",
                            f"status={data.get('status')}")
                assert_test("TC-OP006: 返回 paymentId",
                            data.get("paymentId") is not None,
                            f"paymentId={data.get('paymentId')}")
    else:
        assert_test("TC-OP006: 指定支付方式（跳过）", False,
                    "前置条件不满足：订单ID或买家AToken不存在")

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
        "TestSuite": "订单支付接口测试",
        "Timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "Total": total,
        "Passed": PASS_COUNT,
        "Failed": FAIL_COUNT,
        "Results": RESULTS
    }
    output_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "03_pay_order_result.json")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result_obj, f, ensure_ascii=False, indent=2)
    print(f"\n结果已保存到: 03_pay_order_result.json")

    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
