# Internal 模块自动化测试报告

> **测试时间**: 2026-06-08 15:42:42 ~ 15:42:43  
> **测试环境**: Docker PostgreSQL (ai-shop-postgres) + Spring Boot Backend (localhost:8080)  
> **测试工具**: Python 脚本 + requests 库  
> **测试脚本位置**: `docs/backend/test/assets/test-scripts/internal_python/`  
> **测试结果位置**: `docs/backend/test/assets/test-results/internal/`

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

本次测试针对 **Internal 模块** 的 2 个核心接口进行自动化测试：

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 获取商品 AI 摘要 | GET | `/internal/v1/products/{productId}/ai-summary` | 查询单个商品 AI 摘要（需 Internal-Token） |
| 批量获取商品 AI 摘要 | POST | `/internal/v1/products/ai-summaries` | 批量查询商品 AI 摘要（需 Internal-Token） |

测试覆盖 **20 个用例**，涵盖正常流程、异常流程、边界条件、安全验证和响应结构校验。

---

## 2. 测试脚本逻辑说明

### 2.1 测试脚本架构

```
docs/backend/test/assets/
├── test-scripts/
│   └── internal_python/                 # Internal 模块测试脚本（Python）
│       ├── 01_internal_ai_summary_test.py  # AI 摘要接口测试（20 个用例）
│       ├── run_all_tests.py             # 一键运行所有测试
│       ├── api_client.py                # API 客户端封装
│       ├── env_manager.py               # 环境管理工具
│       └── requirements.txt             # Python 依赖
└── test-results/
    └── internal/                        # Internal 模块测试结果
        ├── all_tests_summary.json       # 汇总结果（JSON）
        └── 01_internal_ai_summary_result.json  # AI 摘要测试详细结果
```

### 2.2 AI 摘要测试 (`01_internal_ai_summary_test.py`)

**测试策略**：先注册 MERCHANT 用户并创建 2 个测试商品（耳机、手机），再使用正确的 Internal-Token 调用内部接口，验证摘要内容的完整性和格式。

| 用例编号 | 名称 | 测试逻辑 | 预期结果 |
|---------|------|---------|---------|
| TC-IS001 | 获取单个商品 AI 摘要（正常场景） | 使用正确 Internal-Token 获取已创建商品的摘要 | `success=true`，返回摘要 |
| TC-IS002 | AI 摘要返回正确的 productId | 验证返回的 productId 与请求的一致 | productId 匹配 |
| TC-IS003 | AI 摘要包含 summaryText 字段 | 验证响应中包含 summaryText 字段 | 字段非空 |
| TC-IS004 | AI 摘要包含商品名称 | 验证摘要文本中包含"商品名称" | 摘要中含"商品名称" |
| TC-IS005 | AI 摘要包含价格信息 | 验证摘要文本中包含"价格"和"元" | 摘要中含"价格"和"元" |
| TC-IS006 | AI 摘要包含商品描述 | 验证摘要文本中包含"描述" | 摘要中含"描述" |
| TC-IS007 | AI 摘要包含标签/特点 | 验证摘要文本中包含"特点" | 摘要中含"特点" |
| TC-IS008 | AI 摘要包含分类信息 | 验证摘要文本中包含"分类" | 摘要中含"分类" |
| TC-IS009 | AI 摘要包含评分和销量 | 验证摘要文本中包含"评分"和"销量" | 摘要中含"评分"和"销量" |
| TC-IS010 | AI 摘要包含详情页链接 | 验证摘要文本中包含"详情页" | 摘要中含"详情页" |
| TC-IS011 | AI 摘要包含商品状态 | 验证摘要文本中包含"状态" | 摘要中含"状态" |
| TC-IS012 | 获取不存在的商品 AI 摘要 | 请求不存在的 productId | statusCode=404 |
| TC-IS013 | 批量获取商品 AI 摘要（正常场景） | 传入 2 个存在的商品 ID | `success=true` |
| TC-IS014 | 批量摘要返回 items 列表 | 验证 items 数量与请求的 productIds 数量一致 | 数量匹配 |
| TC-IS015 | 批量摘要（部分商品不存在） | 混合存在的商品和不存在的商品 | 失败商品有错误提示 |
| TC-IS016 | 批量摘要传入空列表 | 传入空列表 | statusCode=400 |
| TC-IS017 | 未携带 Internal-Token | 不传 X-Internal-Token 头 | statusCode=403 |
| TC-IS018 | 携带错误的 Internal-Token | 传错误的 X-Internal-Token | statusCode=403 |
| TC-IS019 | 验证 AI 摘要响应结构 | 验证响应包含 success/code/message/data/traceId | 结构完整 |
| TC-IS020 | 验证批量 AI 摘要响应结构 | 验证响应包含 success/code/data/items | 结构完整 |

**关键实现细节**：
- 使用 `X-Internal-Token: dev-internal-token` 请求头（与 application.yml 配置一致）
- 测试数据使用时间戳生成唯一用户名和商品名，避免数据冲突
- 摘要内容验证使用字符串包含判断（如 `"商品名称" in summary_text`）
- 批量测试验证错误隔离机制（部分商品失败不影响其他商品）

---

## 3. 测试结果汇总

| 测试套件 | 总用例数 | 通过 | 失败 | 通过率 |
|---------|:-------:|:---:|:---:|:-----:|
| AI 摘要接口测试 | 20 | 20 | 0 | **100%** |
| **总计** | **20** | **20** | **0** | **100%** |

> ✅ **全部 20 个测试用例通过，通过率 100%**

**测试耗时**：0.88 秒

---

## 4. 详细测试结果

### 4.1 AI 摘要接口测试结果

| 用例 | 状态 | 详情 |
|------|:----:|------|
| 准备: 商家注册成功 | ✅ PASS | `username=internal_merchant_20260608154242` |
| 准备: 商家登录成功 | ✅ PASS | — |
| 准备: 商品1创建成功 | ✅ PASS | `productId=p10042`（耳机） |
| 准备: 商品2创建成功 | ✅ PASS | `productId=p10043`（手机） |
| TC-IS001: 获取单个商品 AI 摘要成功 | ✅ PASS | `status_code=200, productId=p10042` |
| TC-IS002: AI 摘要返回正确的 productId | ✅ PASS | `expected=p10042, actual=p10042` |
| TC-IS003: AI 摘要包含 summaryText 字段 | ✅ PASS | `summaryText_length=169` |
| TC-IS004: AI 摘要包含商品名称 | ✅ PASS | `摘要中含"商品名称"` |
| TC-IS005: AI 摘要包含价格信息 | ✅ PASS | `摘要中含"价格"和"元"` |
| TC-IS006: AI 摘要包含商品描述 | ✅ PASS | `摘要中含"描述"` |
| TC-IS007: AI 摘要包含标签/特点 | ✅ PASS | `摘要中含"特点"` |
| TC-IS008: AI 摘要包含分类信息 | ✅ PASS | `摘要中含"分类"` |
| TC-IS009: AI 摘要包含评分和销量 | ✅ PASS | `摘要中含"评分"和"销量"` |
| TC-IS010: AI 摘要包含详情页链接 | ✅ PASS | `摘要中含"详情页"` |
| TC-IS011: AI 摘要包含商品状态 | ✅ PASS | `摘要中含"状态"` |
| TC-IS012: 获取不存在的商品返回 404 | ✅ PASS | `status_code=404` |
| TC-IS013: 批量获取商品 AI 摘要成功 | ✅ PASS | `status_code=200` |
| TC-IS014: 批量摘要返回 items 列表 | ✅ PASS | `expected_count=2, actual_count=2` |
| TC-IS015: 批量摘要（部分商品不存在） | ✅ PASS | `total_items=4, failed_count=2` |
| TC-IS016: 批量摘要传入空列表返回 400 | ✅ PASS | `status_code=400` |
| TC-IS017: 未携带 Internal-Token 返回 403 | ✅ PASS | `status_code=403` |
| TC-IS018: 携带错误的 Internal-Token 返回 403 | ✅ PASS | `status_code=403` |
| TC-IS019: AI 摘要响应结构完整 | ✅ PASS | `success=True, code=True, message=True, data=True, traceId=True` |
| TC-IS020: 批量 AI 摘要响应结构完整 | ✅ PASS | `success=True, code=True, data=True, items=True` |

### 4.2 测试商品数据

| 商品 | productId | 名称 | 价格 | 库存 | 分类 |
|:----:|:---------:|------|:----:|:----:|:----:|
| 商品1 | p10042 | AI摘要测试耳机_20260608154242 | 299.00 | 100 | 耳机 |
| 商品2 | p10043 | AI摘要测试手机_20260608154242 | 5999.00 | 50 | 手机 |

### 4.3 摘要内容示例

测试中生成的 AI 摘要文本（长度 169 字符）包含以下 11 个字段：

```
商品名称：AI摘要测试耳机_20260608154242
价格：299.00 元
库存：100 件
分类：耳机
描述：AI摘要测试耳机_20260608154242 的描述信息
特点：蓝牙、降噪、无线
评分：0.0 分
销量：0 件
状态：在售
详情页：/api/v1/products/p10042
```

---

## 5. 数据库验证

### 5.1 Docker 容器状态

```bash
$ docker ps
# 输出显示 ai-shop-postgres 容器正常运行
```

可通过如下方式获取数据库中内容：
```ps1
# 查看测试商品
docker exec ai-shop-postgres psql -U ai_shop -d ai_shop -c "SELECT product_id, name, price, stock, status FROM products WHERE name LIKE 'AI摘要测试%' ORDER BY created_at DESC;"

# 查看所有商品
docker exec ai-shop-postgres psql -U ai_shop -d ai_shop -c "SELECT product_id, name, price, stock, status FROM products ORDER BY created_at DESC LIMIT 10;"
```

### 5.2 数据库表结构

**products 表**（主要字段）：

| 字段 | 类型 | 约束 |
|------|------|------|
| id | bigint | PK, auto-increment |
| product_id | varchar(64) | UNIQUE, NOT NULL |
| merchant_id | varchar(64) | FK → users(user_id), NOT NULL |
| name | varchar(200) | NOT NULL |
| description | text | 可空 |
| category_id | varchar(64) | FK → categories(category_id) |
| price | decimal(10,2) | NOT NULL |
| stock | int | NOT NULL, DEFAULT 0 |
| sales | int | NOT NULL, DEFAULT 0 |
| rating | decimal(2,1) | NOT NULL, DEFAULT 0.0 |
| status | varchar(32) | NOT NULL (ON_SALE / OFF_SALE) |
| created_at | timestamptz | NOT NULL |
| updated_at | timestamptz | NOT NULL |

### 5.3 数据库数据验证

**products 表数据**（测试商品）：

| product_id | name | price | stock | status |
|-----------|------|:-----:|:-----:|:------:|
| p10042 | AI摘要测试耳机_20260608154242 | 299.00 | 100 | ON_SALE |
| p10043 | AI摘要测试手机_20260608154242 | 5999.00 | 50 | ON_SALE |

**数据一致性验证**：
- ✅ 测试商品正确创建在数据库中
- ✅ 商品状态默认为 ON_SALE
- ✅ 商品价格、库存、分类信息正确存储

---

## 6. 结论与建议

### 6.1 测试结论

| 检查项 | 结果 |
|--------|:----:|
| 单个商品 AI 摘要（正常场景） | ✅ 全部通过 |
| AI 摘要内容完整性（11 个字段验证） | ✅ 全部通过 |
| 不存在的商品返回 404 | ✅ 正确 |
| 批量 AI 摘要（正常场景） | ✅ 正确 |
| 批量摘要错误隔离（部分失败） | ✅ 正确 |
| 批量摘要空列表返回 400 | ✅ 正确 |
| 未携带 Internal-Token 返回 403 | ✅ 正确 |
| 携带错误 Internal-Token 返回 403 | ✅ 正确 |
| 响应结构完整性 | ✅ 完整 |

### 6.2 代码设计确认

通过测试验证了以下代码设计：

1. **结构化摘要格式**：摘要包含 11 个关键字段（名称、价格、库存、分类、描述、特点、评分、销量、状态、详情页）
2. **错误隔离**：批量查询中单个商品失败不影响其他商品（TC-IS015 验证 4 个商品中 2 个失败仍返回完整结果）
3. **内部 Token 验证**：未携带或携带错误的 Token 返回 403（TC-IS017、TC-IS018）
4. **统一响应格式**：所有接口返回 success/code/message/data/traceId 结构（TC-IS019、TC-IS020）
5. **参数校验**：空列表返回 400（TC-IS016）
6. **异常处理**：MissingRequestHeaderException 正确返回 403 而非 500

### 6.3 改进建议

1. **测试覆盖**：后续可增加以下测试场景：
   - 并发请求测试（多个 AI Service 同时查询）
   - 大数据量批量测试（100+ 商品 ID）
   - AI 索引通知的集成测试（验证通知是否成功发送）
2. **性能测试**：建议对批量摘要接口进行性能测试，验证大量商品 ID 下的响应时间
3. **缓存机制**：后续可考虑对 AI 摘要添加缓存（如 Redis），减少数据库查询次数
4. **监控告警**：建议对 AI 索引通知失败添加监控告警，及时发现 AI Service 异常

---

*报告生成时间: 2026-06-08 15:42:43*  
*测试脚本维护位置: `docs/backend/test/assets/test-scripts/internal_python/`*  
*测试结果存储位置: `docs/backend/test/assets/test-results/internal/`*
