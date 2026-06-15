"""
02_query_order_test.py - 订单查询接口测试
测试 Order 模块的查询接口：
- GET /api/v1/orders（查询当前用户的订单列表）
- GET /api/v1/orders/{orderId}（获取订单详情）

测试用例：
1. TC-OQ001: 查询当前用户的订单列表
2. TC-OQ002: 按状态筛选订单列表
3. TC-OQ003: 查询订单详情
4. TC-OQ004: 查询不存在的订单（应返回 404）
5. TC-OQ005: 查询其他用户的订单（应返回 403）
6. TC-OQ006: 未携带 Token 查询订单（应返回 401）
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
    print("  订单查询接口测试")
    print("=" * 40)

    # ============================================
    # 准备：注册商家 + 买家A + 买家B
    # ============================================
    print("\n--- 准备测试数据 ---")

    merchant_user = f"oq_merchant_{timestamp}"
    merchant_phone = f"1381000{timestamp[-6:]}"
    merchant_pwd = "password123"

    reg_m = client.register_user(merchant_user, merchant_phone, merchant_pwd, "MERCHANT")
    assert_test("准备: 商家注册成功",
                reg_m.success and reg_m.response and reg_m.response.get("success") is True, "")
    if not (reg_m.success and reg_m.response and reg_m.response.get("success")):
        print_response_error("  商家注册失败:", reg_m)

    token_m = client.get_access_token(merchant_user, merchant_pwd)
    headers_m = client.make_auth_headers(token_m) if token_m else {}

    buyer_a_user = f"oq_buyer_a_{timestamp}"
    buyer_a_phone = f"1382000{timestamp[-6:]}"
    buyer_a_pwd = "password123"

    reg_ba = client.register_user(buyer_a_user, buyer_a_phone, buyer_a_pwd, "CUSTOMER")
    assert_test("准备: 买家A注册成功",
                reg_ba.success and reg_ba.response and reg_ba.response.get("success") is True, "")
    if not (reg_ba.success and reg_ba.response and reg_ba.response.get("success")):
        print_response_error("  买家A注册失败:", reg_ba)

    token_ba = client.get_access_token(buyer_a_user, buyer_a_pwd)
    headers_ba = client.make_auth_headers(token_ba) if token_ba else {}

    buyer_b_user = f"oq_buyer_b_{timestamp}"
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
        "name": f"查询测试商品_{timestamp}",
        "description": "用于订单查询测试",
        "categoryId": "c_accessory",
        "price": 50.00,
        "stock": 200
    }, headers=headers_m)

    if p1.success and p1.response and p1.response.get("success"):
        product_id = p1.response.get("data", {}).get("productId")
        print(f"  ✅ 创建商品: {product_id}")
    else:
        print_response_error("  ❌ 创建商品失败:", p1)

    # 买家A创建2个订单
    order_id_a1 = None
    order_id_a2 = None

    if product_id and token_ba:
        oa1 = client.post("/api/v1/orders", {
            "items": [{"productId": product_id, "quantity": 1}],
            "receiver": {
                "name": "买家A_收货人",
                "phone": "13800138001",
                "address": "买家A地址1"
            }
        }, headers=headers_ba)

        if oa1.success and oa1.response and oa1.response.get("success"):
            order_id_a1 = oa1.response.get("data", {}).get("orderId")
            print(f"  ✅ 买家A创建订单1: {order_id_a1}")
        else:
            print_response_error("  ❌ 买家A创建订单1失败:", oa1)

        oa2 = client.post("/api/v1/orders", {
            "items": [{"productId": product_id, "quantity": 2}],
            "receiver": {
                "name": "买家A_收货人",
                "phone": "13800138001",
                "address": "买家A地址2"
            }
        }, headers=headers_ba)

        if oa2.success and oa2.response and oa2.response.get("success"):
            order_id_a2 = oa2.response.get("data", {}).get("orderId")
            print(f"  ✅ 买家A创建订单2: {order_id_a2}")
        else:
            print_response_error("  ❌ 买家A创建订单2失败:", oa2)

    print("\n" + "=" * 40)
    print("  测试用例执行")
    print("=" * 40)

    # ============================================
    # TC-OQ001: 查询当前用户的订单列表
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OQ001: 查询当前用户的订单列表")
    print("─" * 40)

    if token_ba:
        q1 = client.get("/api/v1/orders", headers=headers_ba)
        assert_test("TC-OQ001: 查询成功",
                    q1.success and q1.response and q1.response.get("success") is True,
                    f"status_code={q1.status_code}")
        if not (q1.success and q1.response and q1.response.get("success")):
            print_response_error("  ", q1)

        if q1.success and q1.response and q1.response.get("success"):
            data = q1.response.get("data")
            if data:
                assert_test("TC-OQ001: 返回 items",
                            data.get("items") is not None,
                            f"items={data.get('items')}")
                assert_test("TC-OQ001: 返回 page",
                            data.get("page", -1) >= 1,
                            f"page={data.get('page')}")
                assert_test("TC-OQ001: 返回 total",
                            data.get("total", -1) >= 0,
                            f"total={data.get('total')}")
                items = data.get("items", [])
                assert_test("TC-OQ001: 订单数 >= 2",
                            len(items) >= 2,
                            f"count={len(items)}")
    else:
        assert_test("TC-OQ001: 查询订单列表（跳过）", False,
                    "前置条件不满足：买家AToken不存在")

    # ============================================
    # TC-OQ002: 按状态筛选订单列表
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OQ002: 按状态筛选订单列表（status=CREATED）")
    print("─" * 40)

    if token_ba:
        q2 = client.get("/api/v1/orders?status=CREATED", headers=headers_ba)
        assert_test("TC-OQ002: 按状态筛选成功",
                    q2.success and q2.response and q2.response.get("success") is True,
                    f"status_code={q2.status_code}")
        if not (q2.success and q2.response and q2.response.get("success")):
            print_response_error("  ", q2)

        if q2.success and q2.response and q2.response.get("success"):
            data = q2.response.get("data")
            if data:
                items = data.get("items", [])
                all_created = all(item.get("status") == "CREATED" for item in items)
                assert_test("TC-OQ002: 所有订单状态为 CREATED",
                            all_created,
                            f"items_status={[item.get('status') for item in items]}")
    else:
        assert_test("TC-OQ002: 按状态筛选（跳过）", False,
                    "前置条件不满足：买家AToken不存在")

    # ============================================
    # TC-OQ003: 查询订单详情
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OQ003: 查询订单详情")
    print("─" * 40)

    if order_id_a1 and token_ba:
        q3 = client.get(f"/api/v1/orders/{order_id_a1}", headers=headers_ba)
        assert_test("TC-OQ003: 查询详情成功",
                    q3.success and q3.response and q3.response.get("success") is True,
                    f"status_code={q3.status_code}")
        if not (q3.success and q3.response and q3.response.get("success")):
            print_response_error("  ", q3)

        if q3.success and q3.response and q3.response.get("success"):
            data = q3.response.get("data")
            if data:
                assert_test("TC-OQ003: 返回 orderId",
                            data.get("orderId") == order_id_a1,
                            f"orderId={data.get('orderId')}")
                assert_test("TC-OQ003: 返回 status",
                            data.get("status") == "CREATED",
                            f"status={data.get('status')}")
                assert_test("TC-OQ003: 返回 totalAmount",
                            data.get("totalAmount") is not None,
                            f"totalAmount={data.get('totalAmount')}")
                assert_test("TC-OQ003: 返回 items",
                            data.get("items") is not None and len(data.get("items")) >= 1,
                            f"items={data.get('items')}")
                assert_test("TC-OQ003: 返回 receiver",
                            data.get("receiver") is not None,
                            f"receiver={data.get('receiver')}")
                assert_test("TC-OQ003: 返回 createdAt",
                            data.get("createdAt") is not None,
                            f"createdAt={data.get('createdAt')}")

                receiver = data.get("receiver")
                if receiver:
                    assert_test("TC-OQ003: 收货人姓名",
                                receiver.get("name") == "买家A_收货人",
                                f"name={receiver.get('name')}")
                    assert_test("TC-OQ003: 收货人电话",
                                receiver.get("phone") == "13800138001",
                                f"phone={receiver.get('phone')}")
                    assert_test("TC-OQ003: 收货地址",
                                receiver.get("address") == "买家A地址1",
                                f"address={receiver.get('address')}")
    else:
        assert_test("TC-OQ003: 查询详情（跳过）", False,
                    "前置条件不满足：订单ID或买家AToken不存在")

    # ============================================
    # TC-OQ004: 查询不存在的订单
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OQ004: 查询不存在的订单（应返回 404）")
    print("─" * 40)

    if token_ba:
        q4 = client.get(f"/api/v1/orders/o_nonexist_{timestamp}", headers=headers_ba)
        assert_test("TC-OQ004: 返回 404",
                    q4.success is False and q4.status_code == 404,
                    f"status_code={q4.status_code}")
        if q4.status_code != 404:
            print_response_error("  ", q4)
    else:
        assert_test("TC-OQ004: 查询不存在订单（跳过）", False,
                    "前置条件不满足：买家AToken不存在")

    # ============================================
    # TC-OQ005: 查询其他用户的订单
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OQ005: 查询其他用户的订单（应返回 403）")
    print("─" * 40)

    if order_id_a1 and token_bb:
        q5 = client.get(f"/api/v1/orders/{order_id_a1}", headers=headers_bb)
        assert_test("TC-OQ005: 返回 403",
                    q5.success is False and q5.status_code == 403,
                    f"status_code={q5.status_code}")
        if q5.status_code != 403:
            print_response_error("  ", q5)
    else:
        assert_test("TC-OQ005: 权限验证（跳过）", False,
                    "前置条件不满足：订单ID或买家BToken不存在")

    # ============================================
    # TC-OQ006: 未携带 Token
    # ============================================
    print("\n" + "─" * 40)
    print("  TC-OQ006: 未携带 Token 查询订单（应返回 401）")
    print("─" * 40)

    q6 = client.get("/api/v1/orders")
    assert_test("TC-OQ006: 返回 401",
                q6.success is False and q6.status_code == 401,
                f"status_code={q6.status_code}")
    if q6.status_code != 401:
        print_response_error("  ", q6)

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
        "TestSuite": "订单查询接口测试",
        "Timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "Total": total,
        "Passed": PASS_COUNT,
        "Failed": FAIL_COUNT,
        "Results": RESULTS
    }
    output_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "02_query_order_result.json")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result_obj, f, ensure_ascii=False, indent=2)
    print(f"\n结果已保存到: 02_query_order_result.json")

    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
