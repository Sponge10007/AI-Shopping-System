# Auth 模块自动化测试报告

> **测试时间**: 2026-06-07 11:34:36 ~ 11:34:38  
> **测试环境**: Docker PostgreSQL (ai-shop-postgres) + Spring Boot Backend (localhost:8080)  
> **测试工具**: PowerShell 脚本 (Windows) + curl  
> **测试脚本位置**: `docs/backend/test/assets/test-scripts/auth_ps1/`  
> **测试结果位置**: `docs/backend/test/assets/test-results/auth/`

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

本次测试针对 **Auth 模块** 的 3 个核心接口进行自动化测试：

| 接口 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 注册 | POST | `/api/v1/auth/register` | 用户注册（CUSTOMER / MERCHANT） |
| 登录 | POST | `/api/v1/auth/login` | 用户名/手机号登录，返回 JWT Token |
| 登出 | POST | `/api/v1/auth/logout` | 用户登出（当前为简化实现） |

测试覆盖 **53 个用例**，涵盖正常流程、异常流程、边界条件和 JWT Token 验证。

---

## 2. 测试脚本逻辑说明

### 2.1 测试脚本架构

```
docs/backend/test/assets/
├── test-scripts/
│   └── auth_ps1/                       # Auth 模块测试脚本（PowerShell）
│       ├── 01_register_test.ps1        # 注册接口测试（5 个用例）
│       ├── 02_login_test.ps1           # 登录接口测试（6 个用例）
│       ├── 03_logout_test.ps1          # 登出接口测试（4 个用例）
│       ├── 04_auth_flow_test.ps1       # 完整业务流程测试（38 个用例）
│       └── run_all_tests.ps1           # 一键运行所有测试
└── test-results/
    └── auth/                           # Auth 模块测试结果
        ├── all_tests_summary.json      # 汇总结果（JSON）
        ├── 01_register_result.json     # 注册测试结果
        ├── 02_login_result.json        # 登录测试结果
        ├── 03_logout_result.json       # 登出测试结果
        └── 04_auth_flow_result.json    # 业务流程测试结果
```

### 2.2 注册测试 (`01_register_test.ps1`)

**测试策略**：使用时间戳生成唯一用户名/手机号，避免测试数据冲突。

| 用例编号 | 名称 | 测试逻辑 | 预期结果 |
|---------|------|---------|---------|
| TC-R001 | 正常注册 CUSTOMER 用户 | 提交完整注册信息，role=CUSTOMER | `success=true`，返回 userId、role |
| TC-R002 | 正常注册 MERCHANT 用户 | 提交完整注册信息，role=MERCHANT | `success=true`，返回 userId、role |
| TC-R003 | 重复用户名注册 | 使用 TC-R001 已注册的用户名再次注册 | `success=false`，`code=DUPLICATE_RESOURCE` |
| TC-R004 | 重复手机号注册 | 使用 TC-R001 已注册的手机号再次注册 | `success=false`，`code=DUPLICATE_RESOURCE` |
| TC-R005 | 缺少必填字段 | 不传 username 字段 | `success=false`，`code=INVALID_ARGUMENT` |

**关键实现细节**：
- 使用 `Invoke-RestMethod` 发送 POST 请求，`ConvertTo-Json` 序列化请求体
- 通过 `try/catch` 捕获 HTTP 错误响应（4xx），解析响应体中的 `success` 和 `code` 字段
- 测试结果保存为 JSON 文件供汇总脚本读取

### 2.3 登录测试 (`02_login_test.ps1`)

**测试策略**：先注册一个测试用户，再基于该用户执行各种登录场景。

| 用例编号 | 名称 | 测试逻辑 | 预期结果 |
|---------|------|---------|---------|
| TC-L001 | 使用用户名正常登录 | `account=用户名, password=正确密码` | `success=true`，返回 Token |
| TC-L002 | 使用手机号正常登录 | `account=手机号, password=正确密码` | `success=true`，返回 Token |
| TC-L003 | 密码错误登录 | `account=用户名, password=错误密码` | `success=false`，`code=UNAUTHORIZED` |
| TC-L004 | 用户不存在登录 | `account=不存在的用户名` | `success=false`，`code=RESOURCE_NOT_FOUND` |
| TC-L005 | 缺少必填字段 | 不传 account 字段 | `success=false`，`code=INVALID_ARGUMENT` |
| TC-L006 | 验证 Token 格式和响应结构 | 检查 access_token、refresh_token、expires_in、user 信息 | 所有字段完整，JWT 三段式格式 |

**关键实现细节**：
- 登录接口支持 **用户名** 和 **手机号** 两种登录方式（通过 `account` 字段自动识别）
- Token 格式验证：检查 JWT 是否为三段式（`header.payload.signature`）
- 响应结构验证：检查 `data.access_token`、`data.refresh_token`、`data.expires_in`、`data.user` 等字段

### 2.4 登出测试 (`03_logout_test.ps1`)

**测试策略**：先注册并登录获取 Token，然后测试不同场景下的登出行为。

| 用例编号 | 名称 | 测试逻辑 | 预期结果 |
|---------|------|---------|---------|
| TC-O001 | 正常登出（携带有效 Token） | `Authorization: Bearer <有效Token>` | `success=true` |
| TC-O002 | 未携带 Token 登出 | 不传 Authorization 头 | `success=true`（简化实现） |
| TC-O003 | 携带无效 Token 登出 | `Authorization: Bearer invalid.jwt.token.here` | `success=true`（简化实现） |
| TC-O004 | 验证登出响应结构 | 检查 success、code、data.logged_out 字段 | 响应结构完整 |

**注意**：当前登出接口为 **简化实现**，不验证 Token 有效性，无论是否携带 Token 都返回成功。后续可引入 Redis 黑名单机制实现真正的 Token 失效。

### 2.5 完整业务流程测试 (`04_auth_flow_test.ps1`)

**测试策略**：模拟真实用户从注册到登出的完整操作流程，覆盖 10 个场景共 38 个断言。

| 场景 | 名称 | 断言数 | 测试内容 |
|------|------|:------:|---------|
| 场景 1 | 注册 CUSTOMER 用户 | 4 | 注册成功、返回 userId、role=CUSTOMER、userId 以 `u` 开头 |
| 场景 2 | 使用用户名登录 | 6 | 登录成功、返回 access_token/refresh_token、expires_in=7200、用户信息匹配 |
| 场景 3 | 使用手机号登录 | 2 | 手机号登录成功、返回相同 userId |
| 场景 4 | 使用 Token 访问登出接口 | 3 | 有效 Token 登出、无 Token 登出、无效 Token 登出 |
| 场景 5 | 错误密码登录 | 2 | 登录失败（401）、错误码 UNAUTHORIZED |
| 场景 6 | 不存在的用户登录 | 2 | 登录失败（404）、错误码 RESOURCE_NOT_FOUND |
| 场景 7 | 重复注册 | 4 | 重复用户名（409）、重复手机号（409）、错误码 DUPLICATE_RESOURCE |
| 场景 8 | MERCHANT 角色注册和登录 | 5 | 注册成功、userId 以 `m` 开头、role=MERCHANT、登录成功、登录返回 role=MERCHANT |
| 场景 9 | 缺少必填字段 | 3 | 注册缺 username（400）、注册缺 password（400）、登录缺 account（400） |
| 场景 10 | 验证 JWT Token 内容 | 7 | sub/role/iat/exp 字段存在、sub 与 userId 一致、role 正确、exp = iat + 7200s |

**JWT Token 解码验证逻辑**（场景 10）：
```powershell
# 解码 JWT payload（Base64）
$parts = $accessToken.Split('.')
$payload = $parts[1]
$padding = 4 - ($payload.Length % 4)
if ($padding -ne 4) { $payload = $payload.PadRight($payload.Length + $padding, '=') }
$payloadBytes = [Convert]::FromBase64String($payload)
$payloadJson = [System.Text.Encoding]::UTF8.GetString($payloadBytes)
$payloadObj = $payloadJson | ConvertFrom-Json
# 验证字段
Assert-Test "sub 与 userId 一致" ($payloadObj.sub -eq $customerUserId)
Assert-Test "exp = iat + 7200s" ($payloadObj.exp -eq ($payloadObj.iat + 7200))
```

### 2.6 汇总脚本 (`run_all_tests.ps1`)

**执行流程**：
1. 依次执行 4 个测试脚本（01 → 02 → 03 → 04）
2. 每个脚本执行完毕后读取对应的 JSON 结果文件
3. 汇总所有测试结果，计算通过率
4. 输出最终汇总报告并保存为 `all_tests_summary.json`

---

## 3. 测试结果汇总

| 测试套件 | 总用例数 | 通过 | 失败 | 通过率 |
|---------|:-------:|:---:|:---:|:-----:|
| 注册接口测试 | 5 | 5 | 0 | **100%** |
| 登录接口测试 | 6 | 6 | 0 | **100%** |
| 登出接口测试 | 4 | 4 | 0 | **100%** |
| 完整业务流程测试 | 38 | 38 | 0 | **100%** |
| **总计** | **53** | **53** | **0** | **100%** |

> ✅ **全部 53 个测试用例通过，通过率 100%**

---

## 4. 详细测试结果

### 4.1 注册接口测试结果

| 用例 | 状态 | 详情 |
|------|:----:|------|
| TC-R001: 正常注册 CUSTOMER 用户 | ✅ PASS | `success=True` |
| TC-R002: 正常注册 MERCHANT 用户 | ✅ PASS | `success=True` |
| TC-R003: 重复用户名注册 | ✅ PASS | `success=False, code=DUPLICATE_RESOURCE` |
| TC-R004: 重复手机号注册 | ✅ PASS | `success=False, code=DUPLICATE_RESOURCE` |
| TC-R005: 缺少必填字段（无 username） | ✅ PASS | `success=False, code=INVALID_ARGUMENT` |

### 4.2 登录接口测试结果

| 用例 | 状态 | 详情 |
|------|:----:|------|
| TC-L001: 使用用户名正常登录 | ✅ PASS | `success=True` |
| TC-L002: 使用手机号正常登录 | ✅ PASS | `success=True` |
| TC-L003: 密码错误登录 | ✅ PASS | `success=False, code=UNAUTHORIZED` |
| TC-L004: 用户不存在登录 | ✅ PASS | `success=False, code=RESOURCE_NOT_FOUND` |
| TC-L005: 缺少必填字段（无 account） | ✅ PASS | `success=False, code=INVALID_ARGUMENT` |
| TC-L006: 验证 Token 格式和响应结构 | ✅ PASS | 所有字段完整，JWT 格式正确 |

### 4.3 登出接口测试结果

| 用例 | 状态 | 详情 |
|------|:----:|------|
| TC-O001: 正常登出（携带有效 Token） | ✅ PASS | `success=True` |
| TC-O002: 未携带 Token 登出（简化实现） | ✅ PASS | `success=True` |
| TC-O003: 携带无效 Token 登出（简化实现） | ✅ PASS | `success=True` |
| TC-O004: 验证登出响应结构 | ✅ PASS | 响应结构完整 |

### 4.4 完整业务流程测试结果

| 场景 | 用例 | 状态 | 详情 |
|:----:|------|:----:|------|
| 场景 1 | F1.1 CUSTOMER 注册成功 | ✅ PASS | `username=flow_customer_20260607113437` |
| 场景 1 | F1.2 返回 userId | ✅ PASS | `userId=u10029` |
| 场景 1 | F1.3 返回 role=CUSTOMER | ✅ PASS | `role=CUSTOMER` |
| 场景 1 | F1.4 userId 以 u 开头 | ✅ PASS | `userId=u10029` |
| 场景 2 | F2.1 用户名登录成功 | ✅ PASS | `account=flow_customer_20260607113437` |
| 场景 2 | F2.2 返回 access_token | ✅ PASS | `长度=156` |
| 场景 2 | F2.3 返回 refresh_token | ✅ PASS | `长度=156` |
| 场景 2 | F2.4 expires_in = 7200 | ✅ PASS | `expires_in=7200` |
| 场景 2 | F2.5 返回用户信息 | ✅ PASS | `user={...}` |
| 场景 2 | F2.6 用户信息中 userId 匹配 | ✅ PASS | `userId=u10029` |
| 场景 3 | F3.1 手机号登录成功 | ✅ PASS | `account=1383333113437` |
| 场景 3 | F3.2 手机号登录返回相同 userId | ✅ PASS | `userId=u10029` |
| 场景 4 | F4.1 携带有效 Token 登出成功 | ✅ PASS | — |
| 场景 4 | F4.2 不携带 Token 登出（简化实现） | ✅ PASS | 当前为简化实现 |
| 场景 4 | F4.3 携带无效 Token 登出（简化实现） | ✅ PASS | 当前为简化实现 |
| 场景 5 | F5.1 错误密码登录失败 | ✅ PASS | `statusCode=401` |
| 场景 5 | F5.2 返回错误码 UNAUTHORIZED | ✅ PASS | `code=UNAUTHORIZED` |
| 场景 6 | F6.1 不存在的用户登录失败 | ✅ PASS | `statusCode=404` |
| 场景 6 | F6.2 返回错误码 RESOURCE_NOT_FOUND | ✅ PASS | `code=RESOURCE_NOT_FOUND` |
| 场景 7 | F7.1 重复用户名注册失败 | ✅ PASS | `statusCode=409` |
| 场景 7 | F7.2 返回错误码 DUPLICATE_RESOURCE | ✅ PASS | `code=DUPLICATE_RESOURCE` |
| 场景 7 | F7.3 重复手机号注册失败 | ✅ PASS | `statusCode=409` |
| 场景 7 | F7.4 返回错误码 DUPLICATE_RESOURCE | ✅ PASS | `code=DUPLICATE_RESOURCE` |
| 场景 8 | F8.1 MERCHANT 注册成功 | ✅ PASS | `username=flow_merchant_20260607113437` |
| 场景 8 | F8.2 MERCHANT 的 userId 以 m 开头 | ✅ PASS | `userId=m10030` |
| 场景 8 | F8.3 MERCHANT 的 role=MERCHANT | ✅ PASS | `role=MERCHANT` |
| 场景 8 | F8.4 MERCHANT 登录成功 | ✅ PASS | — |
| 场景 8 | F8.5 MERCHANT 登录返回 role=MERCHANT | ✅ PASS | `role=MERCHANT` |
| 场景 9 | F9.1 注册缺少 username 失败 | ✅ PASS | `statusCode=400` |
| 场景 9 | F9.2 注册缺少 password 失败 | ✅ PASS | `statusCode=400` |
| 场景 9 | F9.3 登录缺少 account 失败 | ✅ PASS | `statusCode=400` |
| 场景 10 | F10.1 Token 包含 sub (userId) | ✅ PASS | `sub=u10029` |
| 场景 10 | F10.2 Token 包含 role | ✅ PASS | `role=CUSTOMER` |
| 场景 10 | F10.3 Token 包含 iat (签发时间) | ✅ PASS | `iat=1780803277` |
| 场景 10 | F10.4 Token 包含 exp (过期时间) | ✅ PASS | `exp=1780810477` |
| 场景 10 | F10.5 Token 中 sub 与 userId 一致 | ✅ PASS | `sub=u10029, userId=u10029` |
| 场景 10 | F10.6 Token 中 role 为 CUSTOMER | ✅ PASS | `role=CUSTOMER` |
| 场景 10 | F10.7 Token 过期时间 = iat + 7200s | ✅ PASS | `exp=1780810477, iat+7200=1780810477` |

---

## 5. 数据库验证

### 5.1 Docker 容器状态

```bash
$ docker ps
# 输出显示 ai-shop-postgres 容器正常运行
```

可通过如下方式获取数据库中内容：
```ps1
# 查看所有用户
docker exec ai-shop-postgres psql -U ai_shop -d ai_shop -c "SELECT user_id, username, role, status FROM users;"

# 查看用户资料
docker exec ai-shop-postgres psql -U ai_shop -d ai_shop -c "SELECT id, user_id, nickname, phone FROM user_profiles;"

# 查看最新注册的用户
docker exec ai-shop-postgres psql -U ai_shop -d ai_shop -c "SELECT user_id, username, role, created_at FROM users ORDER BY created_at DESC LIMIT 5;"

# 统计总数
docker exec ai-shop-postgres psql -U ai_shop -d ai_shop -c "SELECT COUNT(*) as total_users FROM users;"
```

### 5.2 数据库表结构

**users 表**（9 个字段）：

| 字段 | 类型 | 约束 |
|------|------|------|
| id | bigint | PK, auto-increment |
| user_id | varchar(64) | UNIQUE, NOT NULL |
| username | varchar(100) | UNIQUE, NOT NULL |
| phone | varchar(32) | 可空 |
| password_hash | varchar(255) | NOT NULL |
| role | varchar(32) | NOT NULL |
| status | varchar(32) | NOT NULL, DEFAULT 'ACTIVE' |
| created_at | timestamptz | NOT NULL |
| updated_at | timestamptz | NOT NULL |

**user_profiles 表**（6 个字段）：

| 字段 | 类型 | 约束 |
|------|------|------|
| id | bigint | PK, auto-increment |
| user_id | varchar(64) | UNIQUE, FK → users(user_id) |
| nickname | varchar(100) | 可空 |
| avatar_url | text | 可空 |
| created_at | timestamptz | NOT NULL |
| updated_at | timestamptz | NOT NULL |

### 5.3 数据库数据验证

**users 表数据**（共 30 条记录，截取最新 6 条）：

| user_id | username | phone | role | status | created_at |
|---------|----------|-------|------|--------|------------|
| u10025 | test_customer_20260607113436 | 1380000113436 | CUSTOMER | ACTIVE | 2026-06-07 03:34:36 |
| m10026 | test_merchant_20260607113436 | 1390000113436 | MERCHANT | ACTIVE | 2026-06-07 03:34:36 |
| u10027 | login_test_20260607113437 | 1381111113437 | CUSTOMER | ACTIVE | 2026-06-07 03:34:37 |
| u10028 | logout_test_20260607113437 | 1382222113437 | CUSTOMER | ACTIVE | 2026-06-07 03:34:37 |
| **u10029** | **flow_customer_20260607113437** | **1383333113437** | **CUSTOMER** | **ACTIVE** | **2026-06-07 03:34:37** |
| **m10030** | **flow_merchant_20260607113437** | **1385555113437** | **MERCHANT** | **ACTIVE** | **2026-06-07 03:34:38** |

> 加粗的两条记录为最后一次完整业务流程测试（04_auth_flow_test.ps1）生成的数据。

**user_profiles 表数据**：共 30 条记录，与 users 表一一对应（通过 `user_id` 外键关联）。

**数据一致性验证**：
- ✅ `users` 表记录数 = `user_profiles` 表记录数 = **30**
- ✅ 每次注册操作都在 `users` 和 `user_profiles` 中同时插入了记录
- ✅ CUSTOMER 用户的 `user_id` 以 `u` 开头（如 `u10029`）
- ✅ MERCHANT 用户的 `user_id` 以 `m` 开头（如 `m10030`）
- ✅ 所有用户状态均为 `ACTIVE`
- ✅ 用户名和手机号唯一约束正常工作（重复注册返回 `DUPLICATE_RESOURCE`）

### 5.4 测试数据与数据库的对应关系

| 测试脚本 | 生成的用户 | 数据库中的 user_id | 数据库验证结果 |
|---------|-----------|:-----------------:|:-------------:|
| 01_register_test.ps1 | test_customer_20260607113436 | u10025 | ✅ 存在 |
| 01_register_test.ps1 | test_merchant_20260607113436 | m10026 | ✅ 存在 |
| 02_login_test.ps1 | login_test_20260607113437 | u10027 | ✅ 存在 |
| 03_logout_test.ps1 | logout_test_20260607113437 | u10028 | ✅ 存在 |
| 04_auth_flow_test.ps1 | flow_customer_20260607113437 | **u10029** | ✅ 存在 |
| 04_auth_flow_test.ps1 | flow_merchant_20260607113437 | **m10030** | ✅ 存在 |

---

## 6. 结论与建议

### 6.1 测试结论

| 检查项 | 结果 |
|--------|:----:|
| 注册接口（正常/异常/边界） | ✅ 全部通过 |
| 登录接口（用户名/手机号/错误密码/不存在用户） | ✅ 全部通过 |
| 登出接口（携带Token/无Token/无效Token） | ✅ 全部通过 |
| 完整业务流程（注册→登录→登出→重复注册） | ✅ 全部通过 |
| JWT Token 格式和内容验证 | ✅ 全部通过 |
| 角色区分（CUSTOMER vs MERCHANT） | ✅ 正确 |
| 数据库数据一致性 | ✅ 完全一致 |
| 错误码体系（UNAUTHORIZED / RESOURCE_NOT_FOUND / DUPLICATE_RESOURCE / INVALID_ARGUMENT） | ✅ 正确 |

### 6.2 代码设计确认

通过测试验证了以下代码设计：

1. **用户 ID 生成规则**：CUSTOMER 以 `u` 开头，MERCHANT 以 `m` 开头（自增数字后缀）
2. **JWT Token 配置**：过期时间 = 7200 秒（2 小时），payload 包含 `sub`（userId）、`role`、`iat`、`exp`
3. **登录方式**：支持用户名和手机号两种方式，通过 `account` 字段自动识别
4. **错误码体系**：统一使用 `ApiResponse` 返回，包含 `success`、`code`、`message`、`data` 字段
5. **登出接口**：当前为简化实现，不验证 Token 有效性

### 6.3 改进建议

1. **登出接口**：建议引入 Redis 黑名单机制，使登出后 Token 真正失效
2. **测试覆盖**：后续可增加以下测试场景：
   - Token 过期后的访问测试
   - 并发注册/登录测试
   - 密码强度校验测试
   - 手机号格式校验测试
3. **自动化**：可将测试脚本集成到 CI/CD 流水线中，每次部署后自动执行

---

*报告生成时间: 2026-06-07 11:34:38*  
*测试脚本维护位置: `docs/backend/test/assets/test-scripts/auth_ps1/`*  
*测试结果存储位置: `docs/backend/test/assets/test-results/auth/`*
