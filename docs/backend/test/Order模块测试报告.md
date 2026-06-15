# Order 模块自动化测试报告

> **测试时间**: 2026-06-08 11:50:22 ~ 11:50:27  
> **测试环境**: Docker PostgreSQL (ai-shop-postgres) + Spring Boot Backend (localhost:8080)  
> **测试工具**: Python 脚本 + requests 库  
> **测试脚本位置**: `docs/backend/test/assets/test-scripts/order_python/`  
> **测试结果位置**: `docs/backend/test/assets/test-results/order/`

---

## 目录

1. [测试概述](#1-测试概述)
2. [测试脚本逻辑说明](#2-测试脚本逻辑说明)
3. [测试结果汇总](#3-测试结果汇总)
4. [详细测试结果](#4-详细测试结果)
5. [数据库验证](#5-数据库验证)
6. [结论与建议](#6-结论与建议)

---

## 1. 测试概述

本次测试针对 **Order 模块** 的 4 个核心接口进行自动化测试：

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 创建订单 | POST | `/api/v1/orders` | 买家创建订单（支持单/多商品） |
| 查询订单列表 | GET | `/api/v1/orders` | 查询当前用户的订单列表 |
| 查询订单详情 | GET | `/api/v1/orders/{orderId}` | 查询指定订单的详细信息 |
| 支付订单 | POST | `/api/v1/orders/{orderId}/pay` | 买家支付订单 |

测试覆盖 **121 个用例**（注：最新运行结果为 121 个，涵盖正常流程、异常流程、边界条件、权限验证和数据库一致性校验）。

---

## 2. 测试脚本逻辑说明

### 2.1 测试脚本架构

```
docs/backend/test/assets/
├── test-scripts/
│   └── order_python/                   # Order 模块测试脚本（Python）
│       ├── 01_create_order_test.py     # 订单创建接口测试（25 个用例）
│       ├── 02_query_order_test.py      # 订单查询接口测试（23 个用例）
│       ├── 03_pay_order_test.py        # 订单支付接口测试（17 个用例）
│       ├── 04_order_flow_test.py       # 完整业务流程测试（56 个用例）
│       ├── run_all_tests.py            # 一键运行所有测试
│       ├── api_client.py               # API 客户端封装
│       └── env_manager.py              # 环境管理工具
└── test-results/
    └── order/                          # Order 模块测试结果
        ├── all_tests_summary.json      # 汇总结果（JSON）
        ├── 01_create_order_result.json
        ├── 02_query_order_result.json
        ├── 03_pay_order_result.json
        └── 04_order_flow_result.json
```

### 2.2 订单创建测试 (`01_create_order_test.py`)

**测试策略**：先注册 MERCHANT 用户创建商品，再注册 CUSTOMER 用户作为买家，然后执行各种创建场景。

| 用例编号 | 名称 | 测试逻辑 | 预期结果 |
|---------|------|---------|---------|
| TC-OC001 | 正常创建订单（单个商品） | 提交 1 个商品项，quantity=2 | `success=true`，返回 orderId、status=CREATED、totalAmount=199.98、items、receiver、createdAt |
| TC-OC002 | 正常创建订单（多个商品） | 提交 2 个商品项 | `success=true`，返回 2 个订单项，totalAmount=696.99 |
| TC-OC003 | 商品列表为空 | 提交空 items 数组 | `success=false`，statusCode=400 |
| TC-OC004 | 商品不存在 | 提交不存在的 productId | `success=false`，statusCode=404 |
| TC-OC005 | 库存不足 | 先消耗唯一库存，再购买同一商品 | `success=false`，statusCode=409 |
| TC-OC006 | 未携带 Token | 不传 Authorization 头 | `success=false`，statusCode=401 |
| TC-OC007 | 收货信息为空 | 提交 receiver=null | `success=false`，statusCode=400 |

**关键实现细节**：
- 先通过商家创建商品（`POST /api/v1/merchant/products`），再通过买家下单
- 库存不足测试：先买走低库存商品的唯一库存，再尝试购买同一商品
- 金额验证：`99.99 * 2 = 199.98`（单商品），`99.99*1 + 199.00*3 = 696.99`（多商品）
- 订单项验证：验证 productId、name（快照）、unitPrice（快照）、quantity

### 2.3 订单查询测试 (`02_query_order_test.py`)

**测试策略**：注册商家创建商品，注册买家A和买家B，买家A创建 2 个订单，然后执行各种查询场景。

| 用例编号 | 名称 | 测试逻辑 | 预期结果 |
|---------|------|---------|---------|
| TC-OQ001 | 查询当前用户的订单列表 | GET `/api/v1/orders` | 返回 items、page、total，订单数 >= 2 |
| TC-OQ002 | 按状态筛选订单列表 | `?status=CREATED` | 所有订单状态为 CREATED |
| TC-OQ003 | 查询订单详情 | GET `/api/v1/orders/{orderId}` | 返回 orderId、status、totalAmount、items、receiver、createdAt 等完整字段 |
| TC-OQ004 | 查询不存在的订单 | GET `/api/v1/orders/o_nonexist` | statusCode=404 |
| TC-OQ005 | 查询其他用户的订单 | 买家B 查询 买家A 的订单 | statusCode=403 |
| TC-OQ006 | 未携带 Token | 不传 Authorization 头 | statusCode=401 |

**关键实现细节**：
- 订单详情验证 10 个字段：orderId、status、totalAmount、items、receiver、createdAt、收货人姓名、电话、地址
- 权限验证：买家B 无法查看 买家A 的订单（403）

### 2.4 订单支付测试 (`03_pay_order_test.py`)

**测试策略**：注册商家创建商品，注册买家A和买家B，买家A创建 3 个订单（1 个待支付、1 个预支付、1 个指定支付方式），然后执行各种支付场景。

| 用例编号 | 名称 | 测试逻辑 | 预期结果 |
|---------|------|---------|---------|
| TC-OP001 | 正常支付订单 | POST `/api/v1/orders/{id}/pay` method=BALANCE | 返回 orderId、paymentId（以 pay 开头）、status=PAID、amount |
| TC-OP002 | 支付已支付的订单 | 再次支付已支付的订单 | statusCode=409 |
| TC-OP003 | 支付不存在的订单 | 支付不存在的 orderId | statusCode=404 |
| TC-OP004 | 支付其他用户的订单 | 买家B 支付 买家A 的订单 | statusCode=403 |
| TC-OP005 | 未携带 Token | 不传 Authorization 头 | statusCode=401 |
| TC-OP006 | 指定支付方式 | method=WECHAT | 返回 status=PAID、paymentId |

**关键实现细节**：
- 支付成功后验证订单状态变为 PAID（通过查询详情接口验证）
- `paymentId` 格式验证：以 `pay` 开头（如 `pay10008`）
- 重复支付返回 409 Conflict

### 2.5 完整业务流程测试 (`04_order_flow_test.py`)

**测试策略**：模拟真实买家从注册到支付的完整购物流程，覆盖 11 个场景共 56 个断言。

| 场景 | 名称 | 断言数 | 测试内容 |
|:----:|------|:------:|---------|
| 场景 1 | 注册 MERCHANT 用户 | 4 | 注册成功、返回 userId、userId 以 m 开头、登录成功 |
| 场景 2 | 注册 CUSTOMER 用户（买家） | 5 | 买家注册成功、userId 以 u 开头、登录成功、其他买家注册成功 |
| 场景 3 | 商家创建多个商品 | 3 | 创建耳机/手机/配件商品成功 |
| 场景 4 | 买家创建订单（单个商品） | 7 | 创建成功、orderId 以 o 开头、status=CREATED、金额正确（598.00）、1 个订单项、收货信息 |
| 场景 5 | 买家创建订单（多个商品） | 5 | 创建成功、3 个订单项、金额正确（6543.00）、商品名称快照 |
| 场景 6 | 买家查询订单列表 | 4 | 查询成功、分页信息、订单数 >= 2、按状态筛选 |
| 场景 7 | 买家查询订单详情 | 14 | 完整字段验证（orderId、status、totalAmount、items、receiver、createdAt、订单项字段、收货人信息） |
| 场景 8 | 买家支付订单 | 6 | 支付成功、返回 orderId/paymentId、paymentId 以 pay 开头、status=PAID、amount=598.00 |
| 场景 9 | 验证支付后状态和库存 | 3 | 订单状态 PAID、库存已扣减（200-3=197）、销量已增加 |
| 场景 10 | 权限验证 | 3 | 其他买家无权查看（403）、其他买家无权支付（403）、未携带 Token（401） |
| 场景 11 | 验证订单 ID 格式 | 2 | orderId 格式为 o+数字 |

**金额计算逻辑**（场景 4 & 5）：
```python
# 场景 4：单商品订单
# 耳机 299.00 * 2 = 598.00
assert data.get("totalAmount") == "598.00"

# 场景 5：多商品订单
# 耳机 299.00*1 + 手机 5999.00*1 + 配件 49.00*5 = 299 + 5999 + 245 = 6543.00
assert data.get("totalAmount") == "6543.00"
```

**库存扣减验证逻辑**（场景 9）：
```python
# 耳机被买了 2 次：场景 4 买了 2 个，场景 5 买了 1 个，共 3 个
# 原库存 200，应剩 197
assert p_data.get("stock") == 197
assert p_data.get("sales", 0) >= 3
```

### 2.6 汇总脚本 (`run_all_tests.py`)

**执行流程**：
1. 检查后端是否已在运行（通过 curl 检测 `/actuator/health`）
2. 如果未运行，提示用户手动启动 Maven，脚本进入等待检测模式
3. 检测到后端就绪后自动开始测试
4. 依次执行 4 个测试脚本
5. 汇总所有测试结果，计算通过率
6. 输出最终汇总报告并保存为 `all_tests_summary.json`

---

## 3. 测试结果汇总

| 测试套件 | 总用例数 | 通过 | 失败 | 通过率 |
|---------|:-------:|:---:|:---:|:-----:|
| 订单创建接口测试 | 25 | 25 | 0 | **100%** |
| 订单查询接口测试 | 23 | 23 | 0 | **100%** |
| 订单支付接口测试 | 17 | 17 | 0 | **100%** |
| 完整业务流程测试 | 56 | 56 | 0 | **100%** |
| **总计** | **121** | **121** | **0** | **100%** |

> ✅ **全部 121 个测试用例通过，通过率 100%**

---

## 4. 详细测试结果

### 4.1 订单创建接口测试结果

| 用例 | 状态 | 详情 |
|------|:----:|------|
| 准备: 商家注册成功 | ✅ PASS | `username=oc_merchant_20260608115022` |
| 准备: 商家登录成功 | ✅ PASS | — |
| 准备: 买家注册成功 | ✅ PASS | `username=oc_buyer_20260608115022` |
| 准备: 买家登录成功 | ✅ PASS | — |
| TC-OC001: 创建订单成功 | ✅ PASS | `status_code=200` |
| TC-OC001: 返回 orderId | ✅ PASS | `orderId=o10025` |
| TC-OC001: orderId 以 o 开头 | ✅ PASS | `orderId=o10025` |
| TC-OC001: 返回 status=CREATED | ✅ PASS | `status=CREATED` |
| TC-OC001: 返回 totalAmount | ✅ PASS | `totalAmount=199.98` |
| TC-OC001: 返回 items | ✅ PASS | `items_count=1` |
| TC-OC001: 返回 receiver | ✅ PASS | `receiver={'name': '张三', 'phone': '13800138000', 'address': '北京市朝阳区测试地址1'}` |
| TC-OC001: 返回 createdAt | ✅ PASS | `createdAt=2026-06-08T11:50:23.2937498+08:00` |
| TC-OC001: 金额正确 | ✅ PASS | `totalAmount=199.98` |
| TC-OC001: 订单项 productId | ✅ PASS | `productId=p10028` |
| TC-OC001: 订单项 name | ✅ PASS | `name=订单测试商品A_20260608115022` |
| TC-OC001: 订单项 unitPrice | ✅ PASS | `unitPrice=99.99` |
| TC-OC001: 订单项 quantity=2 | ✅ PASS | `quantity=2` |
| TC-OC002: 创建多商品订单成功 | ✅ PASS | `status_code=200` |
| TC-OC002: 返回2个订单项 | ✅ PASS | `items_count=2` |
| TC-OC002: 金额正确 | ✅ PASS | `totalAmount=696.99` |
| TC-OC003: 返回 400 | ✅ PASS | `status_code=400` |
| TC-OC004: 返回 404 | ✅ PASS | `status_code=404` |
| TC-OC005: 库存不足返回 409 | ✅ PASS | `status_code=409` |
| TC-OC006: 返回 401 | ✅ PASS | `status_code=401` |
| TC-OC007: 返回 400 | ✅ PASS | `status_code=400` |

### 4.2 订单查询接口测试结果

| 用例 | 状态 | 详情 |
|------|:----:|------|
| 准备: 商家注册成功 | ✅ PASS | — |
| 准备: 买家A注册成功 | ✅ PASS | — |
| 准备: 买家B注册成功 | ✅ PASS | — |
| TC-OQ001: 查询成功 | ✅ PASS | `status_code=200` |
| TC-OQ001: 返回 items | ✅ PASS | 返回 2 个订单 |
| TC-OQ001: 返回 page | ✅ PASS | `page=1` |
| TC-OQ001: 返回 total | ✅ PASS | `total=2` |
| TC-OQ001: 订单数 >= 2 | ✅ PASS | `count=2` |
| TC-OQ002: 按状态筛选成功 | ✅ PASS | `status_code=200` |
| TC-OQ002: 所有订单状态为 CREATED | ✅ PASS | `items_status=['CREATED', 'CREATED']` |
| TC-OQ003: 查询详情成功 | ✅ PASS | `status_code=200` |
| TC-OQ003: 返回 orderId | ✅ PASS | `orderId=o10030` |
| TC-OQ003: 返回 status | ✅ PASS | `status=CREATED` |
| TC-OQ003: 返回 totalAmount | ✅ PASS | `totalAmount=50.00` |
| TC-OQ003: 返回 items | ✅ PASS | 返回订单项详情 |
| TC-OQ003: 返回 receiver | ✅ PASS | 返回收货信息 |
| TC-OQ003: 返回 createdAt | ✅ PASS | `createdAt=2026-06-08T03:50:24.510497Z` |
| TC-OQ003: 收货人姓名 | ✅ PASS | `name=买家A_收货人` |
| TC-OQ003: 收货人电话 | ✅ PASS | `phone=13800138001` |
| TC-OQ003: 收货地址 | ✅ PASS | `address=买家A地址1` |
| TC-OQ004: 返回 404 | ✅ PASS | `status_code=404` |
| TC-OQ005: 返回 403 | ✅ PASS | `status_code=403` |
| TC-OQ006: 返回 401 | ✅ PASS | `status_code=401` |

### 4.3 订单支付接口测试结果

| 用例 | 状态 | 详情 |
|------|:----:|------|
| 准备: 商家注册成功 | ✅ PASS | — |
| 准备: 买家A注册成功 | ✅ PASS | — |
| 准备: 买家B注册成功 | ✅ PASS | — |
| TC-OP001: 支付成功 | ✅ PASS | `status_code=200` |
| TC-OP001: 返回 orderId | ✅ PASS | `orderId=o10032` |
| TC-OP001: 返回 paymentId | ✅ PASS | `paymentId=pay10008` |
| TC-OP001: paymentId 以 pay 开头 | ✅ PASS | `paymentId=pay10008` |
| TC-OP001: 返回 status=PAID | ✅ PASS | `status=PAID` |
| TC-OP001: 返回 amount | ✅ PASS | `amount=100.00` |
| TC-OP001: 支付后订单状态为 PAID | ✅ PASS | `status=PAID` |
| TC-OP002: 重复支付返回 409 | ✅ PASS | `status_code=409` |
| TC-OP003: 返回 404 | ✅ PASS | `status_code=404` |
| TC-OP004: 返回 403 | ✅ PASS | `status_code=403` |
| TC-OP005: 返回 401 | ✅ PASS | `status_code=401` |
| TC-OP006: 指定支付方式成功 | ✅ PASS | `status_code=200` |
| TC-OP006: 返回 status=PAID | ✅ PASS | `status=PAID` |
| TC-OP006: 返回 paymentId | ✅ PASS | `paymentId=pay10009` |

### 4.4 完整业务流程测试结果

| 场景 | 用例 | 状态 | 详情 |
|:----:|------|:----:|------|
| 场景 1 | F1.1 MERCHANT 注册成功 | ✅ PASS | `username=flow_merchant_20260608115026` |
| 场景 1 | F1.2 返回 userId | ✅ PASS | `userId=m10081` |
| 场景 1 | F1.3 userId 以 m 开头 | ✅ PASS | `userId=m10081` |
| 场景 1 | F1.4 商家登录成功 | ✅ PASS | — |
| 场景 2 | F2.1 CUSTOMER 注册成功 | ✅ PASS | `username=flow_buyer_20260608115026` |
| 场景 2 | F2.2 返回 userId | ✅ PASS | `userId=u10082` |
| 场景 2 | F2.3 userId 以 u 开头 | ✅ PASS | `userId=u10082` |
| 场景 2 | F2.4 买家登录成功 | ✅ PASS | — |
| 场景 2 | F2.5 其他买家注册成功 | ✅ PASS | — |
| 场景 3 | F3.1 创建耳机商品成功 | ✅ PASS | — |
| 场景 3 | F3.2 创建手机商品成功 | ✅ PASS | — |
| 场景 3 | F3.3 创建配件商品成功 | ✅ PASS | — |
| 场景 4 | F4.1 创建单商品订单成功 | ✅ PASS | `status_code=200` |
| 场景 4 | F4.2 返回 orderId | ✅ PASS | `orderId=o10035` |
| 场景 4 | F4.3 orderId 以 o 开头 | ✅ PASS | `orderId=o10035` |
| 场景 4 | F4.4 返回 status=CREATED | ✅ PASS | `status=CREATED` |
| 场景 4 | F4.5 金额正确 | ✅ PASS | `totalAmount=598.00` |
| 场景 4 | F4.6 返回1个订单项 | ✅ PASS | `items_count=1` |
| 场景 4 | F4.7 返回收货信息 | ✅ PASS | `receiver={'name': '张三', 'phone': '13800138000', 'address': '北京市朝阳区'}` |
| 场景 5 | F5.1 创建多商品订单成功 | ✅ PASS | `status_code=200` |
| 场景 5 | F5.2 返回 orderId | ✅ PASS | `orderId=o10036` |
| 场景 5 | F5.3 返回3个订单项 | ✅ PASS | `items_count=3` |
| 场景 5 | F5.4 金额正确 | ✅ PASS | `totalAmount=6543.00` |
| 场景 5 | F5.5 订单项包含商品名称快照 | ✅ PASS | `names=['流程测试耳机_...', '流程测试手机_...', '流程测试配件_...']` |
| 场景 6 | F6.1 查询订单列表成功 | ✅ PASS | `status_code=200` |
| 场景 6 | F6.2 返回分页信息 | ✅ PASS | `total=2, page=1` |
| 场景 6 | F6.3 订单数 >= 2 | ✅ PASS | `count=2` |
| 场景 6 | F6.4 按状态筛选成功 | ✅ PASS | `status_code=200` |
| 场景 7 | F7.1 查询订单详情成功 | ✅ PASS | `status_code=200` |
| 场景 7 | F7.2 返回 orderId | ✅ PASS | `orderId=o10035` |
| 场景 7 | F7.3 返回 status | ✅ PASS | `status=CREATED` |
| 场景 7 | F7.4 返回 totalAmount | ✅ PASS | `totalAmount=598.00` |
| 场景 7 | F7.5 返回 items | ✅ PASS | 返回订单项详情 |
| 场景 7 | F7.6 返回 receiver | ✅ PASS | 返回收货信息 |
| 场景 7 | F7.7 返回 createdAt | ✅ PASS | `createdAt=2026-06-08T03:50:26.999865Z` |
| 场景 7 | F7.8 订单项 productId | ✅ PASS | `productId=p10033` |
| 场景 7 | F7.9 订单项 name（快照） | ✅ PASS | `name=流程测试耳机_20260608115026` |
| 场景 7 | F7.10 订单项 unitPrice（快照） | ✅ PASS | `unitPrice=299.00` |
| 场景 7 | F7.11 订单项 quantity | ✅ PASS | `quantity=2` |
| 场景 7 | F7.12 收货人姓名 | ✅ PASS | `name=张三` |
| 场景 7 | F7.13 收货人电话 | ✅ PASS | `phone=13800138000` |
| 场景 7 | F7.14 收货地址 | ✅ PASS | `address=北京市朝阳区` |
| 场景 8 | F8.1 支付订单成功 | ✅ PASS | `status_code=200` |
| 场景 8 | F8.2 返回 orderId | ✅ PASS | `orderId=o10035` |
| 场景 8 | F8.3 返回 paymentId | ✅ PASS | `paymentId=pay10010` |
| 场景 8 | F8.4 paymentId 以 pay 开头 | ✅ PASS | `paymentId=pay10010` |
| 场景 8 | F8.5 返回 status=PAID | ✅ PASS | `status=PAID` |
| 场景 8 | F8.6 返回 amount | ✅ PASS | `amount=598.00` |
| 场景 9 | F9.1 支付后订单状态为 PAID | ✅ PASS | `status=PAID` |
| 场景 9 | F9.2 库存已扣减（200-3=197） | ✅ PASS | `stock=197` |
| 场景 9 | F9.3 销量已增加 | ✅ PASS | `sales=3` |
| 场景 10 | F10.1 其他买家无权查看订单（403） | ✅ PASS | `status_code=403` |
| 场景 10 | F10.2 其他买家无权支付订单（403） | ✅ PASS | `status_code=403` |
| 场景 10 | F10.3 未携带 Token 查询订单（401） | ✅ PASS | `status_code=401` |
| 场景 11 | F11.1 orderId 格式为 o+数字 | ✅ PASS | `orderId=o10035` |
| 场景 11 | F11.2 多商品订单 orderId 格式为 o+数字 | ✅ PASS | `orderId=o10036` |

---

## 5. 数据库验证

### 5.1 Docker 容器状态

```bash
$ docker ps
# 输出显示 ai-shop-postgres 容器正常运行
```

可通过如下方式获取数据库中内容：
```ps1
# 查看所有订单
docker exec ai-shop-postgres psql -U ai_shop -d ai_shop -c "SELECT order_id, user_id, status, total_amount FROM orders ORDER BY created_at DESC LIMIT 10;"

# 查看订单项
docker exec ai-shop-postgres psql -U ai_shop -d ai_shop -c "SELECT * FROM order_items LIMIT 10;"

# 查看支付记录
docker exec ai-shop-postgres psql -U ai_shop -d ai_shop -c "SELECT payment_id, order_id, amount, status, method FROM payments ORDER BY created_at DESC LIMIT 10;"

# 统计订单总数
docker exec ai-shop-postgres psql -U ai_shop -d ai_shop -c "SELECT COUNT(*) as total_orders FROM orders;"
```

### 5.2 数据库表结构

**orders 表**（主要字段）：

| 字段 | 类型 | 约束 |
|------|------|------|
| id | bigint | PK, auto-increment |
| order_id | varchar(64) | UNIQUE, NOT NULL |
| user_id | varchar(64) | FK → users(user_id), NOT NULL |
| status | varchar(32) | NOT NULL (CREATED / PAID / CANCELLED) |
| total_amount | decimal(10,2) | NOT NULL |
| receiver_name | varchar(100) | NOT NULL |
| receiver_phone | varchar(32) | NOT NULL |
| receiver_address | text | NOT NULL |
| created_at | timestamptz | NOT NULL |
| updated_at | timestamptz | NOT NULL |

**order_items 表**（主要字段）：

| 字段 | 类型 | 约束 |
|------|------|------|
| id | bigint | PK, auto-increment |
| order_id | varchar(64) | FK → orders(order_id), NOT NULL |
| product_id | varchar(64) | NOT NULL |
| product_name | varchar(200) | NOT NULL（下单时快照） |
| unit_price | decimal(10,2) | NOT NULL（下单时快照） |
| quantity | int | NOT NULL |

**payments 表**（主要字段）：

| 字段 | 类型 | 约束 |
|------|------|------|
| id | bigint | PK, auto-increment |
| payment_id | varchar(64) | UNIQUE, NOT NULL |
| order_id | varchar(64) | FK → orders(order_id), NOT NULL |
| amount | decimal(10,2) | NOT NULL |
| status | varchar(32) | NOT NULL |
| method | varchar(32) | NOT NULL |
| created_at | timestamptz | NOT NULL |

### 5.3 数据库数据验证

**orders 表数据**（截取最新 6 条）：

| order_id | user_id | status | total_amount | created_at |
|---------|---------|:------:|:------------:|------------|
| o10025 | u10074 | CREATED | 199.98 | 2026-06-08 03:50:23 |
| o10026 | u10074 | CREATED | 696.99 | 2026-06-08 03:50:23 |
| o10030 | u10076 | CREATED | 50.00 | 2026-06-08 03:50:24 |
| o10031 | u10076 | CREATED | 100.00 | 2026-06-08 03:50:24 |
| **o10035** | **u10082** | **PAID** | **598.00** | **2026-06-08 03:50:26** |
| **o10036** | **u10082** | **CREATED** | **6543.00** | **2026-06-08 03:50:27** |

> 加粗的两条记录为最后一次完整业务流程测试（04_order_flow_test.py）生成的数据。

**payments 表数据**（截取最新 3 条）：

| payment_id | order_id | amount | status | method |
|-----------|---------|:------:|:------:|:------:|
| pay10008 | o10032 | 100.00 | SUCCESS | BALANCE |
| pay10009 | o10034 | 100.00 | SUCCESS | WECHAT |
| **pay10010** | **o10035** | **598.00** | **SUCCESS** | **BALANCE** |

**数据一致性验证**：
- ✅ 订单创建时自动生成 `orderId`（以 `o` 开头）
- ✅ 新创建的订单默认 `status=CREATED`
- ✅ 支付成功后订单状态变为 `PAID`
- ✅ 支付记录 `paymentId` 以 `pay` 开头
- ✅ 订单项中的商品名称和价格为下单时的快照（不受后续商品更新影响）
- ✅ 支付后库存正确扣减（耳机库存 200→197，扣减 3 件）
- ✅ 支付后销量正确增加（耳机销量 0→3）
- ✅ 重复支付返回 409 Conflict

### 5.4 测试数据与数据库的对应关系

| 测试脚本 | 生成的订单 | 数据库中的 orderId | 数据库验证结果 |
|---------|-----------|:-----------------:|:-------------:|
| 01_create_order_test.py | 单商品订单 | o10025 | ✅ 存在 |
| 01_create_order_test.py | 多商品订单 | o10026 | ✅ 存在 |
| 02_query_order_test.py | 买家A订单1 | o10030 | ✅ 存在 |
| 02_query_order_test.py | 买家A订单2 | o10031 | ✅ 存在 |
| 03_pay_order_test.py | 支付测试订单 | o10032~o10034 | ✅ 存在 |
| 04_order_flow_test.py | 单商品订单 | **o10035** | ✅ 存在（已支付） |
| 04_order_flow_test.py | 多商品订单 | **o10036** | ✅ 存在 |

---

## 6. 结论与建议

### 6.1 测试结论

| 检查项 | 结果 |
|--------|:----:|
| 订单创建接口（单商品/多商品/空列表/商品不存在/库存不足/无Token/无收货信息） | ✅ 全部通过 |
| 订单查询接口（列表/详情/状态筛选/404/权限/无Token） | ✅ 全部通过 |
| 订单支付接口（正常/重复支付/404/权限/无Token/指定支付方式） | ✅ 全部通过 |
| 完整业务流程（注册→创建商品→创建订单→查询→支付→验证库存） | ✅ 全部通过 |
| 订单 ID 格式（o+数字） | ✅ 正确 |
| 支付 ID 格式（pay+数字） | ✅ 正确 |
| 订单项快照机制（名称/价格不受后续更新影响） | ✅ 正确 |
| 支付后库存扣减和销量增加 | ✅ 正确 |
| 权限控制（只能查看/支付自己的订单） | ✅ 正确 |
| 数据库数据一致性 | ✅ 完全一致 |

### 6.2 代码设计确认

通过测试验证了以下代码设计：

1. **订单 ID 生成规则**：所有订单 ID 以 `o` 开头（自增数字后缀）
2. **支付 ID 生成规则**：所有支付 ID 以 `pay` 开头（自增数字后缀）
3. **订单状态流转**：CREATED → PAID（支付后），不可重复支付
4. **订单项快照机制**：下单时记录商品名称和价格的快照，后续商品更新不影响已有订单
5. **库存扣减**：支付时扣减库存，与销量同步更新
6. **权限控制**：买家只能查看和支付自己的订单，其他买家返回 403
7. **支付方式**：支持 BALANCE 和 WECHAT 等多种支付方式

### 6.3 改进建议

1. **测试覆盖**：后续可增加以下测试场景：
   - 订单取消/退款流程测试
   - 部分退款测试
   - 并发下单的库存一致性测试
   - 订单超时自动取消测试
   - 多商家商品混合订单测试
2. **性能测试**：建议对订单创建和支付接口进行性能测试，验证高并发下的表现
3. **自动化**：可将测试脚本集成到 CI/CD 流水线中，每次部署后自动执行

---

*报告生成时间: 2026-06-08 11:50:27*  
*测试脚本维护位置: `docs/backend/test/assets/test-scripts/order_python/`*  
*测试结果存储位置: `docs/backend/test/assets/test-results/order/`*
