"""
04_order_flow_test.py - 订单模块完整业务流程测试
模拟真实买家购物流程：
1. 注册 MERCHANT 用户（创建商品用）
2. 注册 CUSTOMER 用户（买家）
3. 商家创建多个商品（不同价格、不同库存）
4. 买家创建订单（单个商品）
5. 买家创建订单（多个商品）
6. 买家查询订单列表
7. 买家查询订单详情
8. 买家支付订单
9. 验证支付后状态和库存
10. 权限验证（其他用户无权查看/支付）
11. 验证订单 ID 格式
"""
import sys
import os
import json
import re
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


def print_step(message):
    print(f"\n{'=' * 48}")
    print(f"  {message}")
    print(f"{'=' * 48}")


def main():
    global PASS_COUNT, FAIL_COUNT, RESULTS
    PASS_COUNT = 0
    FAIL_COUNT = 0
    RESULTS = []

    timestamp = datetime.now().strftime("%Y%m%d%H%M%S")

    print("=" * 48)
    print("  订单模块完整业务流程测试")
    print("=" * 48)

    # ============================================
    # 场景 1: 注册 MERCHANT 用户
    # ============================================
    print_step("场景 1: 注册 MERCHANT 用户")

    merchant_user = f"flow_merchant_{timestamp}"
    merchant_phone = f"1384000{timestamp[-6:]}"
    merchant_pwd = "password123"

    r1 = client.register_user(merchant_user, merchant_phone, merchant_pwd, "MERCHANT")
    assert_test("F1.1 MERCHANT 注册成功",
                r1.success and r1.response and r1.response.get("success") is True,
                f"username={merchant_user}")
    if not (r1.success and r1.response and r1.response.get("success")):
        print_response_error("  ", r1)

    merchant_user_id = None
    if r1.success and r1.response and r1.response.get("success"):
        merchant_user_id = r1.response.get("data", {}).get("userId")

    assert_test("F1.2 返回 userId", merchant_user_id is not None,
                f"userId={merchant_user_id}")
    assert_test("F1.3 userId 以 m 开头",
                merchant_user_id is not None and merchant_user_id.startswith("m"),
                f"userId={merchant_user_id}")

    token_m = client.get_access_token(merchant_user, merchant_pwd)
    assert_test("F1.4 商家登录成功", token_m is not None, "")
    headers_m = client.make_auth_headers(token_m) if token_m else {}

    # ============================================
    # 场景 2: 注册 CUSTOMER 用户（买家）
    # ============================================
    print_step("场景 2: 注册 CUSTOMER 用户（买家）")

    buyer_user = f"flow_buyer_{timestamp}"
    buyer_phone = f"1385000{timestamp[-6:]}"
    buyer_pwd = "password123"

    r2 = client.register_user(buyer_user, buyer_phone, buyer_pwd, "CUSTOMER")
    assert_test("F2.1 CUSTOMER 注册成功",
                r2.success and r2.response and r2.response.get("success") is True,
                f"username={buyer_user}")
    if not (r2.success and r2.response and r2.response.get("success")):
        print_response_error("  ", r2)

    buyer_user_id = None
    if r2.success and r2.response and r2.response.get("success"):
        buyer_user_id = r2.response.get("data", {}).get("userId")

    assert_test("F2.2 返回 userId", buyer_user_id is not None,
                f"userId={buyer_user_id}")
    assert_test("F2.3 userId 以 u 开头",
                buyer_user_id is not None and buyer_user_id.startswith("u"),
                f"userId={buyer_user_id}")

    token_b = client.get_access_token(buyer_user, buyer_pwd)
    assert_test("F2.4 买家登录成功", token_b is not None, "")
    headers_b = client.make_auth_headers(token_b) if token_b else {}

    # 注册另一个买家（权限测试用）
    other_user = f"flow_other_{timestamp}"
    other_phone = f"1386000{timestamp[-6:]}"
    other_pwd = "password123"

    r3 = client.register_user(other_user, other_phone, other_pwd, "CUSTOMER")
    assert_test("F2.5 其他买家注册成功",
                r3.success and r3.response and r3.response.get("success") is True, "")
    token_o = client.get_access_token(other_user, other_pwd)
    headers_o = client.make_auth_headers(token_o) if token_o else {}

    # ============================================
    # 场景 3: 商家创建多个商品
    # ============================================
    print_step("场景 3: 商家创建多个商品")

    pid1 = None  # 耳机，价格 299.00，库存 200
    pid2 = None  # 手机，价格 5999.00，库存 50
    pid3 = None  # 配件，价格 49.00，库存 1000

    p1 = client.post("/api/v1/merchant/products", {
        "name": f"流程测试耳机_{timestamp}",
        "description": "高品质降噪蓝牙耳机",
        "categoryId": "c_headphone",
        "price": 299.00,
        "stock": 200
    }, headers=headers_m)

    assert_test("F3.1 创建耳机商品成功",
                p1.success and p1.response and p1.response.get("success") is True, "")
    if p1.success and p1.response and p1.response.get("success"):
        pid1 = p1.response.get("data", {}).get("productId")
        print(f"  ✅ 创建耳机: {pid1}")
    else:
        print_response_error("  ❌ 创建耳机失败:", p1)

    p2 = client.post("/api/v1/merchant/products", {
        "name": f"流程测试手机_{timestamp}",
        "description": "旗舰智能手机",
        "categoryId": "c_phone",
        "price": 5999.00,
        "stock": 50
    }, headers=headers_m)

    assert_test("F3.2 创建手机商品成功",
                p2.success and p2.response and p2.response.get("success") is True, "")
    if p2.success and p2.response and p2.response.get("success"):
        pid2 = p2.response.get("data", {}).get("productId")
        print(f"  ✅ 创建手机: {pid2}")
    else:
        print_response_error("  ❌ 创建手机失败:", p2)

    p3 = client.post("/api/v1/merchant/products", {
        "name": f"流程测试配件_{timestamp}",
        "description": "手机配件",
        "categoryId": "c_accessory",
        "price": 49.00,
        "stock": 1000
    }, headers=headers_m)

    assert_test("F3.3 创建配件商品成功",
                p3.success and p3.response and p3.response.get("success") is True, "")
    if p3.success and p3.response and p3.response.get("success"):
        pid3 = p3.response.get("data", {}).get("productId")
        print(f"  ✅ 创建配件: {pid3}")
    else:
        print_response_error("  ❌ 创建配件失败:", p3)

    # ============================================
    # 场景 4: 买家创建订单（单个商品）
    # ============================================
    print_step("场景 4: 买家创建订单（单个商品）")

    order_id_single = None

    if pid1 and token_b:
        o1 = client.post("/api/v1/orders", {
            "items": [{"productId": pid1, "quantity": 2}],
            "receiver": {
                "name": "张三",
                "phone": "13800138000",
                "address": "北京市朝阳区"
            }
        }, headers=headers_b)

        assert_test("F4.1 创建单商品订单成功",
                    o1.success and o1.response and o1.response.get("success") is True,
                    f"status_code={o1.status_code}")
        if not (o1.success and o1.response and o1.response.get("success")):
            print_response_error("  ", o1)

        if o1.success and o1.response and o1.response.get("success"):
            data = o1.response.get("data")
            if data:
                order_id_single = data.get("orderId")
                assert_test("F4.2 返回 orderId", order_id_single is not None,
                            f"orderId={order_id_single}")
                assert_test("F4.3 orderId 以 o 开头",
                            order_id_single is not None and order_id_single.startswith("o"),
                            f"orderId={order_id_single}")
                assert_test("F4.4 返回 status=CREATED",
                            data.get("status") == "CREATED",
                            f"status={data.get('status')}")
                # 金额：299.00 * 2 = 598.00
                assert_test("F4.5 金额正确",
                            data.get("totalAmount") == "598.00",
                            f"totalAmount={data.get('totalAmount')}")
                assert_test("F4.6 返回1个订单项",
                            len(data.get("items", [])) == 1,
                            f"items_count={len(data.get('items', []))}")
                assert_test("F4.7 返回收货信息",
                            data.get("receiver") is not None,
                            f"receiver={data.get('receiver')}")
    else:
        assert_test("F4.1~F4.7 创建单商品订单（跳过）", False,
                    "前置条件不满足")

    # ============================================
    # 场景 5: 买家创建订单（多个商品）
    # ============================================
    print_step("场景 5: 买家创建订单（多个商品）")

    order_id_multi = None

    if pid1 and pid2 and pid3 and token_b:
        o2 = client.post("/api/v1/orders", {
            "items": [
                {"productId": pid1, "quantity": 1},
                {"productId": pid2, "quantity": 1},
                {"productId": pid3, "quantity": 5}
            ],
            "receiver": {
                "name": "李四",
                "phone": "13900139000",
                "address": "上海市浦东新区"
            }
        }, headers=headers_b)

        assert_test("F5.1 创建多商品订单成功",
                    o2.success and o2.response and o2.response.get("success") is True,
                    f"status_code={o2.status_code}")
        if not (o2.success and o2.response and o2.response.get("success")):
            print_response_error("  ", o2)

        if o2.success and o2.response and o2.response.get("success"):
            data = o2.response.get("data")
            if data:
                order_id_multi = data.get("orderId")
                assert_test("F5.2 返回 orderId", order_id_multi is not None,
                            f"orderId={order_id_multi}")
                assert_test("F5.3 返回3个订单项",
                            len(data.get("items", [])) == 3,
                            f"items_count={len(data.get('items', []))}")
                # 金额：299.00*1 + 5999.00*1 + 49.00*5 = 299 + 5999 + 245 = 6543.00
                assert_test("F5.4 金额正确",
                            data.get("totalAmount") == "6543.00",
                            f"totalAmount={data.get('totalAmount')}")

                items = data.get("items", [])
                if len(items) >= 3:
                    names = [item.get("name") for item in items]
                    assert_test("F5.5 订单项包含商品名称快照",
                                all(name and "流程测试" in name for name in names),
                                f"names={names}")
    else:
        assert_test("F5.1~F5.5 创建多商品订单（跳过）", False,
                    "前置条件不满足")

    # ============================================
    # 场景 6: 买家查询订单列表
    # ============================================
    print_step("场景 6: 买家查询订单列表")

    if token_b:
        q1 = client.get("/api/v1/orders", headers=headers_b)
        assert_test("F6.1 查询订单列表成功",
                    q1.success and q1.response and q1.response.get("success") is True,
                    f"status_code={q1.status_code}")
        if not (q1.success and q1.response and q1.response.get("success")):
            print_response_error("  ", q1)

        if q1.success and q1.response and q1.response.get("success"):
            data = q1.response.get("data")
            if data:
                assert_test("F6.2 返回分页信息",
                            data.get("total", -1) >= 0 and data.get("page", -1) >= 1,
                            f"total={data.get('total')}, page={data.get('page')}")
                items = data.get("items", [])
                assert_test("F6.3 订单数 >= 2",
                            len(items) >= 2,
                            f"count={len(items)}")

        q2 = client.get("/api/v1/orders?status=CREATED", headers=headers_b)
        assert_test("F6.4 按状态筛选成功",
                    q2.success and q2.response and q2.response.get("success") is True,
                    f"status_code={q2.status_code}")
    else:
        assert_test("F6.1~F6.4 查询订单列表（跳过）", False,
                    "前置条件不满足")

    # ============================================
    # 场景 7: 买家查询订单详情
    # ============================================
    print_step("场景 7: 买家查询订单详情")

    if order_id_single and token_b:
        d1 = client.get(f"/api/v1/orders/{order_id_single}", headers=headers_b)
        assert_test("F7.1 查询订单详情成功",
                    d1.success and d1.response and d1.response.get("success") is True,
                    f"status_code={d1.status_code}")
        if not (d1.success and d1.response and d1.response.get("success")):
            print_response_error("  ", d1)

        if d1.success and d1.response and d1.response.get("success"):
            data = d1.response.get("data")
            if data:
                assert_test("F7.2 返回 orderId",
                            data.get("orderId") == order_id_single,
                            f"orderId={data.get('orderId')}")
                assert_test("F7.3 返回 status",
                            data.get("status") == "CREATED",
                            f"status={data.get('status')}")
                assert_test("F7.4 返回 totalAmount",
                            data.get("totalAmount") == "598.00",
                            f"totalAmount={data.get('totalAmount')}")
                assert_test("F7.5 返回 items",
                            len(data.get("items", [])) >= 1,
                            f"items={data.get('items')}")
                assert_test("F7.6 返回 receiver",
                            data.get("receiver") is not None,
                            f"receiver={data.get('receiver')}")
                assert_test("F7.7 返回 createdAt",
                            data.get("createdAt") is not None,
                            f"createdAt={data.get('createdAt')}")

                items = data.get("items", [])
                if len(items) > 0:
                    item = items[0]
                    assert_test("F7.8 订单项 productId",
                                item.get("productId") == pid1,
                                f"productId={item.get('productId')}")
                    assert_test("F7.9 订单项 name（快照）",
                                item.get("name") and "流程测试耳机" in item.get("name", ""),
                                f"name={item.get('name')}")
                    assert_test("F7.10 订单项 unitPrice（快照）",
                                item.get("unitPrice") == "299.00",
                                f"unitPrice={item.get('unitPrice')}")
                    assert_test("F7.11 订单项 quantity",
                                item.get("quantity") == 2,
                                f"quantity={item.get('quantity')}")

                receiver = data.get("receiver")
                if receiver:
                    assert_test("F7.12 收货人姓名",
                                receiver.get("name") == "张三",
                                f"name={receiver.get('name')}")
                    assert_test("F7.13 收货人电话",
                                receiver.get("phone") == "13800138000",
                                f"phone={receiver.get('phone')}")
                    assert_test("F7.14 收货地址",
                                receiver.get("address") == "北京市朝阳区",
                                f"address={receiver.get('address')}")
    else:
        assert_test("F7.1~F7.14 查询订单详情（跳过）", False,
                    "前置条件不满足")

    # ============================================
    # 场景 8: 买家支付订单
    # ============================================
    print_step("场景 8: 买家支付订单")

    if order_id_single and token_b:
        pay1 = client.post(f"/api/v1/orders/{order_id_single}/pay",
                          {"method": "BALANCE"}, headers=headers_b)
        assert_test("F8.1 支付订单成功",
                    pay1.success and pay1.response and pay1.response.get("success") is True,
                    f"status_code={pay1.status_code}")
        if not (pay1.success and pay1.response and pay1.response.get("success")):
            print_response_error("  ", pay1)

        if pay1.success and pay1.response and pay1.response.get("success"):
            data = pay1.response.get("data")
            if data:
                assert_test("F8.2 返回 orderId",
                            data.get("orderId") == order_id_single,
                            f"orderId={data.get('orderId')}")
                assert_test("F8.3 返回 paymentId",
                            data.get("paymentId") is not None,
                            f"paymentId={data.get('paymentId')}")
                assert_test("F8.4 paymentId 以 pay 开头",
                            data.get("paymentId", "").startswith("pay"),
                            f"paymentId={data.get('paymentId')}")
                assert_test("F8.5 返回 status=PAID",
                            data.get("status") == "PAID",
                            f"status={data.get('status')}")
                assert_test("F8.6 返回 amount",
                            data.get("amount") == "598.00",
                            f"amount={data.get('amount')}")
    else:
        assert_test("F8.1~F8.6 支付订单（跳过）", False,
                    "前置条件不满足")

    # ============================================
    # 场景 9: 验证支付后状态和库存
    # ============================================
    print_step("场景 9: 验证支付后状态和库存")

    if order_id_single and token_b:
        d2 = client.get(f"/api/v1/orders/{order_id_single}", headers=headers_b)
        if d2.success and d2.response and d2.response.get("success"):
            data = d2.response.get("data")
            if data:
                assert_test("F9.1 支付后订单状态为 PAID",
                            data.get("status") == "PAID",
                            f"status={data.get('status')}")

        # 验证库存已扣减
        # 耳机被买了2次：F4.1 买了2个，F5.1 买了1个，共3个
        # 原库存200，应剩197
        if pid1:
            check_p = client.get(f"/api/v1/products/{pid1}")
            if check_p.success and check_p.response and check_p.response.get("success"):
                p_data = check_p.response.get("data")
                if p_data:
                    assert_test("F9.2 库存已扣减（200-3=197）",
                                p_data.get("stock") == 197,
                                f"stock={p_data.get('stock')}")
                    assert_test("F9.3 销量已增加",
                                p_data.get("sales", 0) >= 3,
                                f"sales={p_data.get('sales')}")

    # 支付多商品订单并验证库存
    if order_id_multi and pid1 and pid2 and pid3 and token_b:
        pay2 = client.post(f"/api/v1/orders/{order_id_multi}/pay",
                          {"method": "BALANCE"}, headers=headers_b)
        if pay2.success and pay2.response and pay2.response.get("success"):
            print("  ✅ 多商品订单支付成功")

            for pid, name, bought in [(pid1, "耳机", 1), (pid2, "手机", 1), (pid3, "配件", 5)]:
                check_p = client.get(f"/api/v1/products/{pid}")
                if check_p.success and check_p.response and check_p.response.get("success"):
                    p_data = check_p.response.get("data")
                    if p_data:
                        print(f"  {name} 当前库存: {p_data.get('stock')}")
        else:
            print_response_error("  ⚠️ 多商品订单支付结果:", pay2)

    # ============================================
    # 场景 10: 权限验证
    # ============================================
    print_step("场景 10: 权限验证")

    if order_id_single and token_o:
        forbid_view = client.get(f"/api/v1/orders/{order_id_single}", headers=headers_o)
        assert_test("F10.1 其他买家无权查看订单（403）",
                    forbid_view.success is False and forbid_view.status_code == 403,
                    f"status_code={forbid_view.status_code}")
        if forbid_view.status_code != 403:
            print_response_error("  ", forbid_view)
    else:
        assert_test("F10.1 权限验证（跳过）", False, "前置条件不满足")

    if order_id_multi and token_o:
        forbid_pay = client.post(f"/api/v1/orders/{order_id_multi}/pay",
                                {"method": "BALANCE"}, headers=headers_o)
        assert_test("F10.2 其他买家无权支付订单（403）",
                    forbid_pay.success is False and forbid_pay.status_code == 403,
                    f"status_code={forbid_pay.status_code}")
        if forbid_pay.status_code != 403:
            print_response_error("  ", forbid_pay)
    else:
        assert_test("F10.2 权限验证（跳过）", False, "前置条件不满足")

    no_token = client.get("/api/v1/orders")
    assert_test("F10.3 未携带 Token 查询订单（401）",
                no_token.success is False and no_token.status_code == 401,
                f"status_code={no_token.status_code}")

    # ============================================
    # 场景 11: 验证订单 ID 格式
    # ============================================
    print_step("场景 11: 验证订单 ID 格式")

    if order_id_single:
        assert_test("F11.1 orderId 格式为 o+数字",
                    bool(re.match(r"^o\d+$", order_id_single)),
                    f"orderId={order_id_single}")

    if order_id_multi:
        assert_test("F11.2 多商品订单 orderId 格式为 o+数字",
                    bool(re.match(r"^o\d+$", order_id_multi)),
                    f"orderId={order_id_multi}")

    # ============================================
    # 汇总报告
    # ============================================
    print("\n\n" + "=" * 48)
    print("  完整业务流程测试汇总报告")
    print("=" * 48)
    total = PASS_COUNT + FAIL_COUNT
    print(f"总计: {total}  |  通过: {PASS_COUNT}  |  失败: {FAIL_COUNT}")

    print("")
    for r in RESULTS:
        status_tag = "PASS" if r["Status"] == "PASS" else "FAIL"
        print(f"[{status_tag}] {r['Name']} - {r['Detail']}")

    result_obj = {
        "TestSuite": "订单模块完整业务流程测试",
        "Timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "Total": total,
        "Passed": PASS_COUNT,
        "Failed": FAIL_COUNT,
        "Results": RESULTS
    }
    output_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "04_order_flow_result.json")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result_obj, f, ensure_ascii=False, indent=2)
    print(f"\n结果已保存到: 04_order_flow_result.json")

    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
