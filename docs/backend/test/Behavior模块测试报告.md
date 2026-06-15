# Behavior 模块自动化测试报告

> **测试时间**: 2026-06-08 15:22:47 ~ 15:22:49  
> **测试环境**: Docker PostgreSQL (ai-shop-postgres) + Spring Boot Backend (localhost:8080)  
> **测试工具**: Python 脚本 + requests 库  
> **测试脚本位置**: `docs/backend/test/assets/test-scripts/uba_python/`  
> **测试结果位置**: `docs/backend/test/assets/test-results/uba/`

---

## 目录

1. [测试概述](#1-测试概述)
2. [测试脚本逻辑说明](#2-测试脚本逻辑说明)
3. [测试结果汇总](#3-测试结果汇总)
4. [详细测试结果](#4-详细测试结果)
5. [数据库验证](#5-数据库验证)
6. [问题修复历程](#6-问题修复历程)
7. [结论与建议](#7-结论与建议)

---

## 1. 测试概述

本次测试针对 **Behavior 模块** 的 1 个核心接口进行自动化测试：

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 记录行为事件 | POST | `/api/v1/behavior-events` | 记录用户行为（VIEW/SEARCH/ADD_TO_CART/PURCHASE） |

测试覆盖 **7 个用例**，涵盖 4 种事件类型、权限验证和参数校验。

---

## 2. 测试脚本逻辑说明

### 2.1 测试脚本架构

```
docs/backend/test/assets/
├── test-scripts/
│   └── uba_python/                    # Upload/Behavior/Admin 模块测试脚本（Python）
│       ├── 01_upload_test.py          # 文件上传接口测试
│       ├── 02_behavior_test.py        # 行为记录接口测试（7 个用例）
│       ├── 03_admin_test.py           # 管理员功能接口测试
│       ├── 04_uba_flow_test.py        # 完整业务流程测试（10 个场景）
│       ├── run_all_tests.py           # 一键运行所有测试
│       ├── api_client.py              # API 客户端封装
│       └── env_manager.py             # 环境管理工具
└── test-results/
    └── uba/                           # Upload/Behavior/Admin 模块测试结果
        ├── all_tests_summary.json     # 汇总结果（JSON）
        ├── 01_upload_result.json      # 上传测试结果
        ├── 02_behavior_result.json    # 行为测试结果
        ├── 03_admin_result.json       # 管理员测试结果
        └── 04_uba_flow_result.json    # 业务流程测试结果
```

### 2.2 行为记录测试 (`02_behavior_test.py`)

**测试策略**：先注册 CUSTOMER 用户并登录，然后执行各种行为记录场景。

| 用例编号 | 名称 | 测试逻辑 | 预期结果 |
|---------|------|---------|---------|
| TC-BE001 | 正常记录 VIEW 行为 | `eventType=VIEW, targetType=PRODUCT, targetId=p10001, metadata={"source":"homepage"}` | `success=true, accepted=true` |
| TC-BE002 | 正常记录 SEARCH 行为 | `eventType=SEARCH, metadata={"keyword":"耳机","results":10}` | `success=true, accepted=true` |
| TC-BE003 | 正常记录 ADD_TO_CART 行为 | `eventType=ADD_TO_CART, targetType=PRODUCT, targetId=p10001, metadata={"quantity":1}` | `success=true, accepted=true` |
| TC-BE004 | 正常记录 PURCHASE 行为 | `eventType=PURCHASE, targetType=ORDER, targetId=o10001, metadata={"amount":299.00}` | `success=true, accepted=true` |
| TC-BE005 | 未携带 Token 记录行为 | 不传 Authorization 头 | `success=false, statusCode=401` |
| TC-BE006 | 传入不支持的事件类型 | `eventType=CLICK`（不在白名单中） | `success=false, statusCode=400` |
| TC-BE007 | 事件类型为空 | `eventType=""` | `success=false, statusCode=400` |

**关键实现细节**：
- 每种事件类型使用不同的 metadata 结构，验证 JSONB 字段的灵活性
- SEARCH 事件不传 targetType 和 targetId，验证 SEARCH 的特殊校验逻辑
- metadata 直接传 Python dict，`json.dumps` 自动序列化为 JSON 对象
- 响应验证 `data.accepted == True`

### 2.3 完整业务流程测试 (`04_uba_flow_test.py`)

Behavior 相关场景：

| 场景 | 名称 | 断言数 | 测试内容 |
|:----:|------|:------:|---------|
| 场景 3 | 注册 CUSTOMER 用户 | 3 | 注册成功、userId 以 u 开头、登录成功 |
| 场景 4 | 记录 VIEW 行为 | 2 | 记录成功、返回 accepted=true |
| 场景 5 | 记录 SEARCH 行为 | 2 | 记录成功、返回 accepted=true |
| 场景 6 | 记录 ADD_TO_CART 行为 | 2 | 记录成功、返回 accepted=true |
| 场景 9 | 记录 PURCHASE 行为 | 2 | 记录成功、响应包含 accepted 字段 |

---

## 3. 测试结果汇总

| 测试套件 | 总用例数 | 通过 | 失败 | 通过率 |
|---------|:-------:|:---:|:---:|:-----:|
| 行为记录接口测试 | 7 | 7 | 0 | **100%** |
| 完整业务流程测试（Behavior 相关） | 8 | 8 | 0 | **100%** |
| **总计** | **15** | **15** | **0** | **100%** |

> ✅ **全部 15 个 Behavior 相关测试用例通过，通过率 100%**

---

## 4. 详细测试结果

### 4.1 行为记录接口测试结果

| 用例 | 状态 | 详情 |
|------|:----:|------|
| 准备: 用户注册成功 | ✅ PASS | `username=behavior_test_20260608152247` |
| 准备: 用户登录成功 | ✅ PASS | — |
| TC-BE001: 正常记录 VIEW 行为 | ✅ PASS | `status_code=200, accepted=True` |
| TC-BE001: 返回 accepted=true | ✅ PASS | `body.data.accepted=True` |
| TC-BE002: 正常记录 SEARCH 行为 | ✅ PASS | `status_code=200, accepted=True` |
| TC-BE002: 返回 accepted=true | ✅ PASS | — |
| TC-BE003: 正常记录 ADD_TO_CART 行为 | ✅ PASS | `status_code=200, accepted=True` |
| TC-BE003: 返回 accepted=true | ✅ PASS | — |
| TC-BE004: 正常记录 PURCHASE 行为 | ✅ PASS | `status_code=200, accepted=True` |
| TC-BE004: 返回 accepted=true | ✅ PASS | — |
| TC-BE005: 未携带 Token 返回 401 | ✅ PASS | `status_code=401` |
| TC-BE006: 不支持的事件类型返回 400 | ✅ PASS | `status_code=400` |
| TC-BE007: 事件类型为空返回 400 | ✅ PASS | `status_code=400` |

### 4.2 完整业务流程测试（Behavior 相关）

| 场景 | 用例 | 状态 | 详情 |
|:----:|------|:----:|------|
| 场景 3 | F3.1 普通用户注册成功 | ✅ PASS | `username=flow_customer_20260608152248` |
| 场景 3 | F3.2 普通用户 userId 以 u 开头 | ✅ PASS | `userId=u10159` |
| 场景 3 | F3.3 普通用户登录成功获取 Token | ✅ PASS | — |
| 场景 4 | F4.1 记录 VIEW 行为成功 | ✅ PASS | — |
| 场景 4 | F4.2 返回 accepted=true | ✅ PASS | — |
| 场景 5 | F5.1 记录 SEARCH 行为成功 | ✅ PASS | — |
| 场景 5 | F5.2 返回 accepted=true | ✅ PASS | — |
| 场景 6 | F6.1 记录 ADD_TO_CART 行为成功 | ✅ PASS | — |
| 场景 6 | F6.2 返回 accepted=true | ✅ PASS | — |
| 场景 9 | F9.1 记录 PURCHASE 行为成功 | ✅ PASS | — |
| 场景 9 | F9.2 行为记录响应包含 accepted 字段 | ✅ PASS | — |

---

## 5. 数据库验证

### 5.1 Docker 容器状态

```bash
$ docker ps
# 输出显示 ai-shop-postgres 容器正常运行
```

可通过如下方式获取数据库中内容：
```ps1
# 查看所有行为日志
docker exec ai-shop-postgres psql -U ai_shop -d ai_shop -c "SELECT id, user_id, event_type, product_id, query, metadata, created_at FROM behavior_logs ORDER BY created_at DESC LIMIT 10;"

# 按事件类型统计
docker exec ai-shop-postgres psql -U ai_shop -d ai_shop -c "SELECT event_type, COUNT(*) as count FROM behavior_logs GROUP BY event_type ORDER BY count DESC;"

# 统计行为日志总数
docker exec ai-shop-postgres psql -U ai_shop -d ai_shop -c "SELECT COUNT(*) as total_logs FROM behavior_logs;"
```

### 5.2 数据库表结构

**behavior_logs 表**（6 个字段）：

| 字段 | 类型 | 约束 |
|------|------|------|
| id | bigint | PK, auto-increment |
| user_id | varchar(64) | NOT NULL |
| event_type | varchar(32) | NOT NULL |
| product_id | varchar(64) | 可空 |
| query | text | 可空（仅 SEARCH 事件使用） |
| metadata | jsonb | 可空 |
| created_at | timestamptz | NOT NULL |

### 5.3 数据库数据验证

**behavior_logs 表数据**（截取最新 8 条）：

| id | user_id | event_type | product_id | query | metadata | created_at |
|:--:|---------|:----------:|:----------:|:-----:|----------|------------|
| 1 | u10157 | VIEW | p10001 | null | `{"source": "homepage"}` | 2026-06-08 07:22:48 |
| 2 | u10157 | SEARCH | null | 耳机 | `{"keyword": "耳机", "results": 10}` | 2026-06-08 07:22:48 |
| 3 | u10157 | ADD_TO_CART | p10001 | null | `{"quantity": 1}` | 2026-06-08 07:22:48 |
| 4 | u10157 | PURCHASE | o10001 | null | `{"amount": 299.0}` | 2026-06-08 07:22:48 |
| 5 | u10159 | VIEW | p10001 | null | `{"source": "homepage"}` | 2026-06-08 07:22:49 |
| 6 | u10159 | SEARCH | null | 耳机 | `{"keyword": "耳机", "results": 10}` | 2026-06-08 07:22:49 |
| 7 | u10159 | ADD_TO_CART | p10001 | null | `{"quantity": 1}` | 2026-06-08 07:22:49 |
| 8 | u10159 | PURCHASE | o10001 | null | `{"amount": 299.0}` | 2026-06-08 07:22:49 |

**数据一致性验证**：
- ✅ 4 种事件类型（VIEW/SEARCH/ADD_TO_CART/PURCHASE）全部成功写入
- ✅ SEARCH 事件的 `product_id` 为 null（符合业务语义）
- ✅ SEARCH 事件的 `query` 字段正确提取了 metadata 中的 keyword
- ✅ metadata 以 JSONB 格式正确存储（可存储任意结构的 JSON 对象）
- ✅ 每条记录都有正确的 `user_id` 和 `created_at`
- ✅ 事件类型白名单正常工作（CLICK 等非法类型被拒绝）

### 5.4 测试数据与数据库的对应关系

| 测试脚本 | 生成的用户 | 行为记录数 | 数据库验证结果 |
|---------|-----------|:---------:|:-------------:|
| 02_behavior_test.py | behavior_test_20260608152247 (u10157) | 4 | ✅ 存在 |
| 04_uba_flow_test.py | flow_customer_20260608152248 (u10159) | 4 | ✅ 存在 |

---

## 6. 问题修复历程

### 6.1 首次测试结果（v1 版本）

运行 51 个测试用例，39 通过，12 失败（通过率 76.5%）。

**失败分布：**

| 测试套件 | 失败数 | 失败用例 |
|---------|:------:|---------|
| `02_behavior_test.py` | 4 | TC-BE001, TC-BE002, TC-BE003, TC-BE004 |
| `04_uba_flow_test.py` | 8 | F4.1, F4.2, F5.1, F5.2, F6.1, F6.2, F9.1, F9.2 |

### 6.2 Bug 1：metadata 字段 JSONB 类型不匹配

**错误信息：**
```
ERROR: column "metadata" is of type jsonb but expression is of type character varying
```

**根因：** `BehaviorLogEntity` 中 metadata 字段定义为 `String` 类型，配合 `@JdbcTypeCode(SqlTypes.JSON)` 注解。但 Hibernate 底层仍然以 VARCHAR 方式设置 PreparedStatement 参数，PostgreSQL 不允许 VARCHAR → JSONB 的隐式转换。

**修复：** 将 metadata 类型从 `String` 改为 `Map<String, Object>`，Hibernate 使用 Jackson 将 Map 序列化为 JSON 对象，以 JSONB 格式正确写入数据库。

### 6.3 Bug 2：SEARCH 事件 metadata 双重序列化

**错误信息：**
```
status_code=400
```

**根因：** 测试脚本中 metadata 传的是 JSON 字符串 `'{"keyword": "耳机", "results": 10}'`，`api_client.py` 使用 `json=body` 参数调用 `requests` 库，`json.dumps` 对字符串值做转义，导致 Spring 无法正确反序列化。

**修复：** 测试脚本中 metadata 改为直接传 Python dict `{"keyword": "耳机", "results": 10}`，同时 `BehaviorEventRequest.metadata` 类型从 `String` 改为 `Map<String, Object>`。

### 6.4 Bug 3：SEARCH 事件 targetType 强制校验

**错误信息：**
```
status_code=400（SEARCH 事件未传 targetType）
```

**根因：** `BehaviorService` 中对所有事件类型都强制校验 `targetType` 非空，但 SEARCH 事件在业务语义上没有特定的目标类型。

**修复：** 将校验逻辑改为仅对非 SEARCH 事件生效。

### 6.5 修复影响范围

| Bug | 根因 | 修复文件 | 影响用例数 |
|-----|------|---------|:---------:|
| metadata JSONB 类型不匹配 | `String` + `@JdbcTypeCode` 不生效 | `BehaviorLogEntity.java` | 6 |
| metadata 双重序列化 | 测试脚本传 JSON 字符串 | `BehaviorEventRequest.java`, `02_behavior_test.py`, `04_uba_flow_test.py` | 4 |
| SEARCH targetType 校验过严 | 对所有事件类型强制校验 | `BehaviorService.java` | 2 |

**修复后结果：** 12 个失败用例全部转为通过，整体通过率从 76.5% 提升至 100%。

---

## 7. 结论与建议

### 7.1 测试结论

| 检查项 | 结果 |
|--------|:----:|
| VIEW 行为记录 | ✅ 全部通过 |
| SEARCH 行为记录（无 targetType） | ✅ 全部通过 |
| ADD_TO_CART 行为记录 | ✅ 全部通过 |
| PURCHASE 行为记录 | ✅ 全部通过 |
| 未携带 Token 记录行为（401） | ✅ 正确 |
| 不支持的事件类型（400） | ✅ 正确 |
| 事件类型为空（400） | ✅ 正确 |
| 数据库 JSONB 存储 | ✅ 正确 |
| SEARCH 事件 query 字段提取 | ✅ 正确 |

### 7.2 代码设计确认

通过测试验证了以下代码设计：

1. **事件类型白名单**：仅支持 VIEW/SEARCH/ADD_TO_CART/PURCHASE 四种类型
2. **SEARCH 特殊处理**：不校验 targetType，从 metadata 提取 keyword 作为 query
3. **metadata JSONB 存储**：使用 `Map<String, Object>` 类型，Hibernate 自动序列化为 JSONB
4. **权限控制**：所有行为记录接口需要登录（401 未认证）
5. **响应结构**：返回 `{accepted: true}`，发后即忘模式

### 7.3 改进建议

1. **测试覆盖**：后续可增加以下测试场景：
   - 大数据量 metadata 测试（嵌套 JSON 对象）
   - 并发行为记录测试
   - 行为日志查询/分析接口测试
   - 用户行为轨迹回放测试
2. **异步写入**：高并发场景下建议改为异步消息队列（如 RabbitMQ/Kafka），当前为同步写入
3. **数据归档**：行为日志增长较快，建议增加定时归档策略

---

*报告生成时间: 2026-06-08 15:22:49*  
*测试脚本维护位置: `docs/backend/test/assets/test-scripts/uba_python/`*  
*测试结果存储位置: `docs/backend/test/assets/test-results/uba/`*
