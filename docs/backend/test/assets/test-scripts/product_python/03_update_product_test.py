"""
03_update_product_test.py - 商品更新/下架/补货接口测试
测试 Product 模块的商家管理接口：
- PATCH /api/v1/merchant/products/{product_id}（更新商品）
- DELETE /api/v1/merchant/products/{product_id}（下架商品）
- POST /api/v1/merchant/products/{product_id}/restock（补货）

测试用例：
1. TC-PU001: 更新商品名称
2. TC-PU002: 更新商品价格
3. TC-PU003: 更新商品标签和图片
4. TC-PU004: 更新不存在的商品（应返回 404）
5. TC-PU005: 其他商家无权更新（应返回 403）
6. TC-PO001: 下架商品
7. TC-PO002: 下架已下架的商品（应返回成功，幂等）
8. TC-PO003: 其他商家无权下架（应返回 403）
9. TC-PR001: 补货
10. TC-PR002: 补货数量为0（应返回错误）
11. TC-PR003: 其他商家无权补货（应返回 403）
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
    print("  商品更新/下架/补货接口测试")
    print("=" * 40)

    # ============================================
    # 准备：注册商家A和商家B，创建测试商品
    # ============================================
    print("\n--- 准备测试数据 ---")

    # 商家A（操作商品）
    merchant_a_user = f"upd_a_{timestamp}"
    merchant_a_phone = f"1383000{timestamp[-6:]}"
    merchant_a_pwd = "password123"

    # 商家B（测试权限）
    merchant_b_user = f"upd_b_{timestamp}"
    merchant_b_phone = f"1383001{timestamp[-6:]}"
    merchant_b_pwd = "password123"

    # 注册商家A
    reg_a = client.register_user(merchant_a_user, merchant_a_phone, merchant_a_pwd, "MERCHANT")
    assert_test("准备: 商家A注册成功",
                reg_a.success and reg_a.response and reg_a.response.get("success") is True, "")

    # 注册商家B
    reg_b = client.register_user(merchant_b_user, merchant_b_phone, merchant_b_pwd, "MERCHANT")
    assert_test("准备: 商家B注册成功",
                reg_b.success and reg_b.response and reg_b.response.get("success") is True, "")

    # 登录商家A
    token_a = client.get_access_token(merchant_a_user, merchant_a_pwd)
    assert_test("准备: 商家A登录成功", token_a is not None, "")

    # 登录商家B
    token_b = client.get_access_token(merchant_b_user, merchant_b_pwd)
    assert_test("准备: 商家B登录成功", token_b is not None, "")

    headers_a = client.make_auth_headers(token_a) if token_a else {}
    headers_b = client.make_auth_headers(token_b) if token_b else {}

    # 商家A创建3个商品
    update_product_id = None
    off_sale_product_id = None
    restock_product_id = None

    product_defs = [
        {"name": f"更新测试商品_{timestamp}", "description": "用于更新测试",
         "categoryId": "c_accessory", "price": 88.88, "stock": 200},
        {"name": f"下架测试商品_{timestamp}", "description": "用于下架测试",
         "categoryId": "c_accessory", "price": 66.66, "stock": 150},
        {"name": f"补货测试商品_{timestamp}", "description": "用于补货测试",
         "categoryId": "c_accessory", "price": 55.55, "stock": 100}
    ]

    p1 = client.post("/api/v1/merchant/products", product_defs[0], headers=headers_a)
    p2 = client.post("/api/v1/merchant/products", product_defs[1], headers=headers_a)
    p3 = client.post("/api/v1/merchant/products", product_defs[2], headers=headers_a)

    if p1.success and p1.response and p1.response.get("success"):
        update_product_id = p1.response.get("data", {}).get("productId")
        print(f"  ✅ 创建更新测试商品: {update_product_id}")

    if p2.success and p2.response and p2.response.get("success"):
        off_sale_product_id = p2.response.get("data", {}).get("productId")
        print(f"  ✅ 创建下架测试商品: {off_sale_product_id}")

    if p3.success and p3.response and p3.response.get("success"):
        restock_product_id = p3.response.get("data", {}).get("productId")
        print(f"  ✅ 创建补货测试商品: {restock_product_id}")

    print("\n" + "=" * 40)
    print("  测试用例执行")
    print("=" * 40)

    # ============================================
    # 场景 1: 更新商品
    # ============================================
    print("\n" + "─" * 40)
    print("  场景 1: 更新商品")
    print("─" * 40)

    # TC-PU001: 更新商品名称
    if update_product_id:
        u1 = client.patch(f"/api/v1/merchant/products/{update_product_id}",
                          {"name": f"已更新名称_{timestamp}"}, headers=headers_a)
        assert_test("TC-PU001: 更新名称成功",
                    u1.success and u1.response and u1.response.get("success") is True, "")
        if u1.success and u1.response and u1.response.get("success"):
            data = u1.response.get("data")
            if data:
                assert_test("TC-PU001: 更新后名称正确",
                            data.get("name") and "已更新名称" in data.get("name", ""),
                            f"name={data.get('name')}")
    else:
        assert_test("TC-PU001: 更新名称（跳过）", False, "前置条件不满足：无商品ID")

    # TC-PU002: 更新商品价格
    if update_product_id:
        u2 = client.patch(f"/api/v1/merchant/products/{update_product_id}",
                          {"price": 199.99}, headers=headers_a)
        assert_test("TC-PU002: 更新价格成功",
                    u2.success and u2.response and u2.response.get("success") is True, "")
    else:
        assert_test("TC-PU002: 更新价格（跳过）", False, "前置条件不满足：无商品ID")

    # TC-PU003: 更新商品标签和图片
    if update_product_id:
        u3 = client.patch(f"/api/v1/merchant/products/{update_product_id}", {
            "tags": ["新标签1", "新标签2"],
            "imageUrls": ["https://example.com/images/new1.jpg"]
        }, headers=headers_a)
        assert_test("TC-PU003: 更新标签和图片成功",
                    u3.success and u3.response and u3.response.get("success") is True, "")
    else:
        assert_test("TC-PU003: 更新标签和图片（跳过）", False, "前置条件不满足：无商品ID")

    # TC-PU004: 更新不存在的商品
    u4 = client.patch(f"/api/v1/merchant/products/p_nonexist_{timestamp}",
                      {"name": "不存在"}, headers=headers_a)
    assert_test("TC-PU004: 更新不存在的商品返回 404",
                u4.success is False and u4.status_code == 404,
                f"statusCode={u4.status_code}")

    # TC-PU005: 其他商家无权更新
    if update_product_id:
        u5 = client.patch(f"/api/v1/merchant/products/{update_product_id}",
                          {"name": "商家B想改名"}, headers=headers_b)
        assert_test("TC-PU005: 其他商家无权更新返回 403",
                    u5.success is False and u5.status_code == 403,
                    f"statusCode={u5.status_code}")
    else:
        assert_test("TC-PU005: 权限验证（跳过）", False, "前置条件不满足：无商品ID")

    # ============================================
    # 场景 2: 下架商品
    # ============================================
    print("\n" + "─" * 40)
    print("  场景 2: 下架商品")
    print("─" * 40)

    # TC-PO001: 下架商品
    if off_sale_product_id:
        o1 = client.delete(f"/api/v1/merchant/products/{off_sale_product_id}", headers=headers_a)
        assert_test("TC-PO001: 下架商品成功",
                    o1.success and o1.response and o1.response.get("success") is True, "")
        if o1.success and o1.response and o1.response.get("success"):
            data = o1.response.get("data")
            if data:
                assert_test("TC-PO001: 下架后 status=OFF_SALE",
                            data.get("status") == "OFF_SALE",
                            f"status={data.get('status')}")
                assert_test("TC-PO001: 返回 vectorIndexStatus",
                            data.get("vectorIndexStatus") == "DELETE_PENDING",
                            f"vectorIndexStatus={data.get('vectorIndexStatus')}")
    else:
        assert_test("TC-PO001: 下架商品（跳过）", False, "前置条件不满足：无商品ID")

    # TC-PO002: 下架已下架的商品（幂等）
    if off_sale_product_id:
        o2 = client.delete(f"/api/v1/merchant/products/{off_sale_product_id}", headers=headers_a)
        assert_test("TC-PO002: 下架已下架的商品（幂等）成功",
                    o2.success and o2.response and o2.response.get("success") is True, "")
    else:
        assert_test("TC-PO002: 幂等下架（跳过）", False, "前置条件不满足：无商品ID")

    # TC-PO003: 其他商家无权下架
    if off_sale_product_id:
        o3 = client.delete(f"/api/v1/merchant/products/{off_sale_product_id}", headers=headers_b)
        assert_test("TC-PO003: 其他商家无权下架返回 403",
                    o3.success is False and o3.status_code == 403,
                    f"statusCode={o3.status_code}")
    else:
        assert_test("TC-PO003: 权限验证（跳过）", False, "前置条件不满足：无商品ID")

    # ============================================
    # 场景 3: 补货
    # ============================================
    print("\n" + "─" * 40)
    print("  场景 3: 补货")
    print("─" * 40)

    # TC-PR001: 正常补货
    if restock_product_id:
        # 先查询补货前的库存
        before = client.get(f"/api/v1/products/{restock_product_id}")
        before_stock = 0
        if before.success and before.response and before.response.get("success"):
            data = before.response.get("data")
            if data:
                before_stock = data.get("stock", 0)
        print(f"  补货前库存: {before_stock}")

        r1 = client.post(f"/api/v1/merchant/products/{restock_product_id}/restock",
                         {"quantity": 50, "remark": "常规补货"}, headers=headers_a)
        assert_test("TC-PR001: 补货成功",
                    r1.success and r1.response and r1.response.get("success") is True, "")
        if r1.success and r1.response and r1.response.get("success"):
            data = r1.response.get("data")
            if data:
                # 后端返回 camelCase: productId, stock
                returned_stock = data.get("stock")
                assert_test("TC-PR001: 补货响应返回 stock",
                            returned_stock is not None,
                            f"stock={returned_stock}")
                assert_test("TC-PR001: 返回 productId",
                            data.get("productId") == restock_product_id,
                            f"productId={data.get('productId')}")

                # 通过查询详情验证库存确实增加了
                after = client.get(f"/api/v1/products/{restock_product_id}")
                if after.success and after.response and after.response.get("success"):
                    after_data = after.response.get("data")
                    if after_data:
                        actual_stock = after_data.get("stock", 0)
                        assert_test("TC-PR001: 数据库库存已增加",
                                    actual_stock == before_stock + 50,
                                    f"before={before_stock}, after={actual_stock}")
    else:
        assert_test("TC-PR001: 补货（跳过）", False, "前置条件不满足：无商品ID")

    # TC-PR002: 补货数量为0
    if restock_product_id:
        r2 = client.post(f"/api/v1/merchant/products/{restock_product_id}/restock",
                         {"quantity": 0, "remark": "无效补货"}, headers=headers_a)
        assert_test("TC-PR002: 补货数量为0返回 400",
                    r2.success is False and r2.status_code == 400,
                    f"statusCode={r2.status_code}")
    else:
        assert_test("TC-PR002: 补货数量校验（跳过）", False, "前置条件不满足：无商品ID")

    # TC-PR003: 其他商家无权补货
    if restock_product_id:
        r3 = client.post(f"/api/v1/merchant/products/{restock_product_id}/restock",
                         {"quantity": 10, "remark": "商家B想补货"}, headers=headers_b)
        assert_test("TC-PR003: 其他商家无权补货返回 403",
                    r3.success is False and r3.status_code == 403,
                    f"statusCode={r3.status_code}")
    else:
        assert_test("TC-PR003: 权限验证（跳过）", False, "前置条件不满足：无商品ID")

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
        "TestSuite": "商品更新/下架/补货接口测试",
        "Timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "Total": total,
        "Passed": PASS_COUNT,
        "Failed": FAIL_COUNT,
        "Results": RESULTS
    }
    output_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "03_update_product_result.json")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result_obj, f, ensure_ascii=False, indent=2)
    print(f"\n结果已保存到: 03_update_product_result.json")

    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
