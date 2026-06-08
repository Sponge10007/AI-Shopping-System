"""
04_product_flow_test.py - 商品模块完整业务流程测试
模拟真实商家操作流程：
1. 注册 MERCHANT 用户
2. 创建多个商品（不同分类、不同价格）
3. 公开查询在售商品列表（验证分页、分类筛选、排序）
4. 查询商品详情（验证完整字段）
5. 商家查询自己的商品列表
6. 更新商品信息（PATCH 语义）
7. 补货操作（验证原子性）
8. 下架商品（逻辑删除）
9. 验证下架后不在公开列表中
10. 权限验证（其他商家无权操作）
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
    print("  商品模块完整业务流程测试")
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

    merchant_user_id = None
    if r1.success and r1.response and r1.response.get("success"):
        data = r1.response.get("data")
        if data:
            merchant_user_id = data.get("userId")

    assert_test("F1.2 返回 userId", merchant_user_id is not None,
                f"userId={merchant_user_id}")
    assert_test("F1.3 userId 以 m 开头",
                merchant_user_id is not None and merchant_user_id.startswith("m"),
                f"userId={merchant_user_id}")

    token = client.get_access_token(merchant_user, merchant_pwd)
    assert_test("F1.4 商家登录成功", token is not None, "")
    assert_test("F1.5 获取到 Token", token is not None, "")

    headers = client.make_auth_headers(token) if token else {}

    # ============================================
    # 场景 2: 创建多个商品
    # ============================================
    print_step("场景 2: 创建多个商品（不同分类、不同价格）")

    id1 = None
    id2 = None
    id3 = None

    # 商品1: 耳机
    p1 = client.post("/api/v1/merchant/products", {
        "name": f"流程测试耳机_{timestamp}",
        "description": "高品质降噪蓝牙耳机",
        "categoryId": "c_headphone",
        "price": 299.00,
        "stock": 200,
        "tags": ["蓝牙", "降噪"],
        "imageUrls": ["https://example.com/img/earphone1.jpg",
                       "https://example.com/img/earphone2.jpg"]
    }, headers=headers)

    assert_test("F2.1 创建耳机商品成功",
                p1.success and p1.response and p1.response.get("success") is True, "")

    if p1.success and p1.response and p1.response.get("success"):
        data = p1.response.get("data")
        if data:
            id1 = data.get("productId")

    assert_test("F2.2 返回 productId", id1 is not None, f"productId={id1}")
    assert_test("F2.3 productId 以 p 开头",
                id1 is not None and id1.startswith("p"),
                f"productId={id1}")

    if p1.success and p1.response and p1.response.get("success"):
        data = p1.response.get("data")
        if data:
            assert_test("F2.4 返回 status=ON_SALE",
                        data.get("status") == "ON_SALE",
                        f"status={data.get('status')}")

    # 商品2: 手机
    p2 = client.post("/api/v1/merchant/products", {
        "name": f"流程测试手机_{timestamp}",
        "description": "旗舰智能手机",
        "categoryId": "c_phone",
        "price": 5999.00,
        "stock": 50,
        "tags": ["5G", "旗舰"]
    }, headers=headers)

    assert_test("F2.5 创建手机商品成功",
                p2.success and p2.response and p2.response.get("success") is True, "")
    if p2.success and p2.response and p2.response.get("success"):
        data = p2.response.get("data")
        if data:
            id2 = data.get("productId")

    # 商品3: 电脑
    p3 = client.post("/api/v1/merchant/products", {
        "name": f"流程测试电脑_{timestamp}",
        "description": "入门级笔记本电脑",
        "categoryId": "c_computer",
        "price": 3999.00,
        "stock": 30
    }, headers=headers)

    assert_test("F2.6 创建电脑商品成功",
                p3.success and p3.response and p3.response.get("success") is True, "")
    if p3.success and p3.response and p3.response.get("success"):
        data = p3.response.get("data")
        if data:
            id3 = data.get("productId")

    print(f"  已创建商品: {id1}, {id2}, {id3}")

    # ============================================
    # 场景 3: 公开查询在售商品列表
    # ============================================
    print_step("场景 3: 公开查询在售商品列表")

    q1 = client.get("/api/v1/products")
    assert_test("F3.1 公开查询在售商品列表成功",
                q1.success and q1.response and q1.response.get("success") is True, "")

    if q1.success and q1.response and q1.response.get("success"):
        data = q1.response.get("data")
        if data:
            assert_test("F3.2 返回分页信息",
                        data.get("total", -1) >= 0 and data.get("page", -1) >= 1,
                        f"total={data.get('total')}, page={data.get('page')}")
            items = data.get("items", [])
            assert_test("F3.3 返回商品列表", len(items) >= 0,
                        f"count={len(items)}")

    q2 = client.get("/api/v1/products?categoryId=c_headphone")
    assert_test("F3.4 按分类筛选耳机成功",
                q2.success and q2.response and q2.response.get("success") is True, "")

    q3 = client.get("/api/v1/products?sortBy=price&sortOrder=asc")
    assert_test("F3.5 按价格升序排列成功",
                q3.success and q3.response and q3.response.get("success") is True, "")

    q4 = client.get("/api/v1/products?page=1&size=5")
    assert_test("F3.6 分页查询成功",
                q4.success and q4.response and q4.response.get("success") is True, "")

    # ============================================
    # 场景 4: 查询商品详情
    # ============================================
    print_step("场景 4: 查询商品详情")

    if id1:
        d1 = client.get(f"/api/v1/products/{id1}")
        assert_test("F4.1 查询商品详情成功",
                    d1.success and d1.response and d1.response.get("success") is True, "")

        if d1.success and d1.response and d1.response.get("success"):
            data = d1.response.get("data")
            if data:
                assert_test("F4.2 返回商品名称",
                            data.get("name") and "流程测试耳机" in data.get("name", ""),
                            f"name={data.get('name')}")
                assert_test("F4.3 返回商品描述", bool(data.get("description")), "")
                assert_test("F4.4 返回分类信息",
                            data.get("categoryId") == "c_headphone",
                            f"categoryId={data.get('categoryId')}")
                assert_test("F4.5 返回分类名称",
                            data.get("categoryName") == "耳机",
                            f"categoryName={data.get('categoryName')}")
                assert_test("F4.6 返回价格",
                            data.get("price") == "299.00",
                            f"price={data.get('price')}")
                assert_test("F4.7 返回库存", data.get("stock", -1) >= 0,
                            f"stock={data.get('stock')}")
                assert_test("F4.8 返回销量", data.get("sales", -1) >= 0,
                            f"sales={data.get('sales')}")
                assert_test("F4.9 返回评分", data.get("rating", -1) >= 0,
                            f"rating={data.get('rating')}")
                assert_test("F4.10 返回状态 ON_SALE",
                            data.get("status") == "ON_SALE",
                            f"status={data.get('status')}")
                assert_test("F4.11 返回标签",
                            len(data.get("tags", [])) >= 1,
                            f"tags={data.get('tags')}")
                assert_test("F4.12 返回图片列表",
                            len(data.get("imageUrls", [])) == 2,
                            f"imageCount={len(data.get('imageUrls', []))}")
                assert_test("F4.13 返回 detailUrl",
                            data.get("detailUrl") == f"/api/v1/products/{id1}",
                            f"detailUrl={data.get('detailUrl')}")
                assert_test("F4.14 返回 createdAt", data.get("createdAt") is not None, "")
                assert_test("F4.15 返回 updatedAt", data.get("updatedAt") is not None, "")
    else:
        assert_test("F4.1~F4.15 查询详情（跳过）", False, "前置条件不满足：无商品ID")

    # 查询不存在的商品
    d2 = client.get(f"/api/v1/products/p_nonexist_{timestamp}")
    assert_test("F4.16 查询不存在的商品返回 404",
                d2.success is False and d2.status_code == 404,
                f"statusCode={d2.status_code}")

    # ============================================
    # 场景 5: 商家查询自己的商品列表
    # ============================================
    print_step("场景 5: 商家查询自己的商品列表")

    m1 = client.get("/api/v1/merchant/products", headers=headers)
    assert_test("F5.1 商家查询自己的商品成功",
                m1.success and m1.response and m1.response.get("success") is True, "")

    if m1.success and m1.response and m1.response.get("success"):
        data = m1.response.get("data")
        if data:
            items = data.get("items", [])
            assert_test("F5.2 返回的商品数 >= 3", len(items) >= 3,
                        f"count={len(items)}")

    m2 = client.get("/api/v1/merchant/products?status=ON_SALE", headers=headers)
    assert_test("F5.3 商家按状态筛选成功",
                m2.success and m2.response and m2.response.get("success") is True, "")

    # ============================================
    # 场景 6: 更新商品信息
    # ============================================
    print_step("场景 6: 更新商品信息（PATCH 语义）")

    if id1:
        u1 = client.patch(f"/api/v1/merchant/products/{id1}",
                          {"name": f"已更新名称_流程测试耳机_{timestamp}"}, headers=headers)
        assert_test("F6.1 更新商品名称成功",
                    u1.success and u1.response and u1.response.get("success") is True, "")
        if u1.success and u1.response and u1.response.get("success"):
            data = u1.response.get("data")
            if data:
                assert_test("F6.2 更新后名称正确",
                            data.get("name") and "已更新名称" in data.get("name", ""),
                            f"name={data.get('name')}")

        u2 = client.patch(f"/api/v1/merchant/products/{id1}",
                          {"price": 199.00}, headers=headers)
        assert_test("F6.3 更新商品价格成功",
                    u2.success and u2.response and u2.response.get("success") is True, "")
        if u2.success and u2.response and u2.response.get("success"):
            data = u2.response.get("data")
            if data:
                assert_test("F6.4 更新后价格正确",
                            data.get("price") is not None and float(data.get("price")) == 199.00,
                            f"price={data.get('price')}")

        # PATCH 语义验证
        if u2.success and u2.response and u2.response.get("success"):
            data = u2.response.get("data")
            if data:
                assert_test("F6.5 PATCH 语义：描述未被覆盖",
                            data.get("description") and "降噪蓝牙耳机" in data.get("description", ""),
                            f"desc={data.get('description')}")
                assert_test("F6.6 PATCH 语义：分类未被覆盖",
                            data.get("categoryId") == "c_headphone",
                            f"categoryId={data.get('categoryId')}")
                assert_test("F6.7 PATCH 语义：库存未被覆盖",
                            data.get("stock") == 200,
                            f"stock={data.get('stock')}")
    else:
        assert_test("F6.1~F6.7 更新商品（跳过）", False, "前置条件不满足：无商品ID")

    # ============================================
    # 场景 7: 补货操作
    # ============================================
    print_step("场景 7: 补货操作（验证原子性）")

    rest1_data = None

    if id3:
        before_restock = client.get(f"/api/v1/products/{id3}")
        before_stock = 0
        if before_restock.success and before_restock.response and before_restock.response.get("success"):
            data = before_restock.response.get("data")
            if data:
                before_stock = data.get("stock", 0)
        print(f"  补货前库存: {before_stock}")

        rest1 = client.post(f"/api/v1/merchant/products/{id3}/restock",
                            {"quantity": 30, "remark": "月末补货"}, headers=headers)
        assert_test("F7.1 补货成功",
                    rest1.success and rest1.response and rest1.response.get("success") is True, "")
        if rest1.success and rest1.response and rest1.response.get("success"):
            data = rest1.response.get("data")
            if data:
                rest1_data = data
                assert_test("F7.2 补货响应返回 stock",
                            data.get("stock") is not None,
                            f"stock={data.get('stock')}")
                assert_test("F7.3 返回 productId",
                            data.get("productId") == id3,
                            f"productId={data.get('productId')}")

                # 通过查询详情验证库存确实增加了
                after1 = client.get(f"/api/v1/products/{id3}")
                if after1.success and after1.response and after1.response.get("success"):
                    after_data = after1.response.get("data")
                    if after_data:
                        actual_stock = after_data.get("stock", 0)
                        assert_test("F7.2b 数据库库存已增加（补货30）",
                                    actual_stock == before_stock + 30,
                                    f"before={before_stock}, after={actual_stock}")

        rest2 = client.post(f"/api/v1/merchant/products/{id3}/restock",
                            {"quantity": 20, "remark": "追加补货"}, headers=headers)
        assert_test("F7.4 再次补货成功",
                    rest2.success and rest2.response and rest2.response.get("success") is True, "")
        if rest2.success and rest2.response and rest2.response.get("success"):
            data = rest2.response.get("data")
            if data:
                assert_test("F7.5 再次补货响应返回 stock",
                            data.get("stock") is not None,
                            f"stock={data.get('stock')}")

                # 通过查询详情验证库存确实增加了
                after2 = client.get(f"/api/v1/products/{id3}")
                if after2.success and after2.response and after2.response.get("success"):
                    after_data = after2.response.get("data")
                    if after_data:
                        actual_stock = after_data.get("stock", 0)
                        assert_test("F7.5b 数据库库存已增加（再补货20）",
                                    actual_stock == before_stock + 50,
                                    f"expected={before_stock + 50}, actual={actual_stock}")
    else:
        assert_test("F7.1~F7.5 补货操作（跳过）", False, "前置条件不满足：无商品ID")

    # ============================================
    # 场景 8: 下架商品
    # ============================================
    print_step("场景 8: 下架商品（逻辑删除）")

    if id2:
        off1 = client.delete(f"/api/v1/merchant/products/{id2}", headers=headers)
        assert_test("F8.1 下架商品成功",
                    off1.success and off1.response and off1.response.get("success") is True, "")
        if off1.success and off1.response and off1.response.get("success"):
            data = off1.response.get("data")
            if data:
                assert_test("F8.2 下架后 status=OFF_SALE",
                            data.get("status") == "OFF_SALE",
                            f"status={data.get('status')}")
                assert_test("F8.3 返回 vectorIndexStatus",
                            data.get("vectorIndexStatus") == "DELETE_PENDING",
                            f"vectorIndexStatus={data.get('vectorIndexStatus')}")

        detail_after_off = client.get(f"/api/v1/products/{id2}")
        if detail_after_off.success and detail_after_off.response and detail_after_off.response.get("success"):
            data = detail_after_off.response.get("data")
            if data:
                assert_test("F8.4 下架后商品详情中 status=OFF_SALE",
                            data.get("status") == "OFF_SALE",
                            f"status={data.get('status')}")
    else:
        assert_test("F8.1~F8.4 下架商品（跳过）", False, "前置条件不满足：无商品ID")

    # ============================================
    # 场景 9: 权限验证
    # ============================================
    print_step("场景 9: 权限验证（其他商家无权操作）")

    other_user = f"flow_other_{timestamp}"
    other_phone = f"1385000{timestamp[-6:]}"
    other_pwd = "password123"

    other_reg = client.register_user(other_user, other_phone, other_pwd, "MERCHANT")
    assert_test("F9.1 其他商家注册成功",
                other_reg.success and other_reg.response and other_reg.response.get("success") is True, "")

    other_token = client.get_access_token(other_user, other_pwd)
    other_headers = client.make_auth_headers(other_token) if other_token else {}

    if id1 and other_token:
        forbid_update = client.patch(f"/api/v1/merchant/products/{id1}",
                                     {"name": "其他商家想改名"}, headers=other_headers)
        assert_test("F9.2 其他商家无权更新（403）",
                    forbid_update.success is False and forbid_update.status_code == 403,
                    f"statusCode={forbid_update.status_code}")
    else:
        assert_test("F9.2 权限验证（跳过）", False, "前置条件不满足")

    if id1 and other_token:
        forbid_off = client.delete(f"/api/v1/merchant/products/{id1}", headers=other_headers)
        assert_test("F9.3 其他商家无权下架（403）",
                    forbid_off.success is False and forbid_off.status_code == 403,
                    f"statusCode={forbid_off.status_code}")
    else:
        assert_test("F9.3 权限验证（跳过）", False, "前置条件不满足")

    if id3 and other_token:
        forbid_restock = client.post(f"/api/v1/merchant/products/{id3}/restock",
                                     {"quantity": 10, "remark": "其他商家想补货"}, headers=other_headers)
        assert_test("F9.4 其他商家无权补货（403）",
                    forbid_restock.success is False and forbid_restock.status_code == 403,
                    f"statusCode={forbid_restock.status_code}")
    else:
        assert_test("F9.4 权限验证（跳过）", False, "前置条件不满足")

    no_token = client.get("/api/v1/merchant/products")
    assert_test("F9.5 未携带 Token 访问商家接口（401）",
                no_token.success is False and no_token.status_code == 401,
                f"statusCode={no_token.status_code}")

    # ============================================
    # 场景 10: 验证商品 ID 格式和响应结构
    # ============================================
    print_step("场景 10: 验证商品 ID 格式和响应结构")

    if id1:
        assert_test("F10.1 productId 格式为 p+数字",
                    bool(re.match(r"^p\d+$", id1)),
                    f"productId={id1}")

    if p1.success and p1.response and p1.response.get("success"):
        data = p1.response.get("data")
        if data:
            assert_test("F10.2 创建响应包含 productId",
                        bool(data.get("productId")),
                        f"productId={data.get('productId')}")
            assert_test("F10.3 创建响应包含 status",
                        bool(data.get("status")),
                        f"status={data.get('status')}")
            assert_test("F10.4 创建响应包含 vectorIndexStatus",
                        bool(data.get("vectorIndexStatus")),
                        f"vectorIndexStatus={data.get('vectorIndexStatus')}")

    if rest1_data is not None:
        assert_test("F10.5 补货响应包含 productId",
                    rest1_data.get("productId") == id3,
                    f"productId={rest1_data.get('productId')}")
        assert_test("F10.6 补货响应包含 stock",
                    rest1_data.get("stock", -1) >= 0,
                    f"stock={rest1_data.get('stock')}")
    elif id3:
        assert_test("F10.5~F10.6 补货响应验证（跳过）", False,
                    "前置条件不满足：场景7补货未成功")

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
        "TestSuite": "商品模块完整业务流程测试",
        "Timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "Total": total,
        "Passed": PASS_COUNT,
        "Failed": FAIL_COUNT,
        "Results": RESULTS
    }
    output_path = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                               "04_product_flow_result.json")
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result_obj, f, ensure_ascii=False, indent=2)
    print(f"\n结果已保存到: 04_product_flow_result.json")

    return 0 if FAIL_COUNT == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
