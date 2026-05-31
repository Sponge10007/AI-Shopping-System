# AI 智能购物系统初步开发接口文档

版本：v0.1  
适用阶段：软件架构搭建、前后端联调、AI 服务接入设计  
依据文档：`总体设计报告/AI智能购物系统_总体设计报告.md`、`软件需求规格说明书.docx`、`readme.txt`

## 1. 文档目的

本文档给出 AI 智能购物系统的初步开发接口设计，目标是为后续使用 AI 或开发团队建立软件架构提供清晰边界。文档重点覆盖：

- 前端与后端之间的公开 REST API。
- 后端与 AI Python 模块之间的内部服务接口。
- 商品、订单、用户、推荐、AI 助手等核心数据对象。
- 多编程语言集成方案。
- 当前 AI 已完成功能与待补充接口。

本文档是架构初稿，不等同于最终 OpenAPI 规范。后续可以基于本文件生成 Spring Boot Controller、DTO、Service、Python FastAPI Wrapper、前端 API SDK 和数据库迁移脚本。

## 2. 总体架构边界

推荐采用以下架构：

```text
Vue/WebView 前端
    |
    | HTTPS + JSON
    v
主后端服务 Backend API
Spring Boot 或其他后端框架
    |
    | SQL/JPA/MyBatis
    v
PostgreSQL 业务数据库

主后端服务 Backend API
    |
    | 内网 HTTP + JSON
    v
Python AI Service
FastAPI/Flask 封装现有 AI 函数
    |
    | Python SDK
    v
向量数据库 Chroma/Milvus + LLM 服务
```

### 2.1 多编程语言解决方案

本项目可能同时使用 Vue、Java/Spring Boot 和 Python。推荐不要让 Java 直接 import Python 文件，也不要让前端直接调用 Python AI 模块。建议采用“主后端 + AI 内部服务”的方式：

- Vue 前端只调用主后端的 `/api/v1` 接口。
- 主后端负责登录鉴权、角色权限、商品订单事务、普通数据库访问和统一响应。
- Python AI Service 负责封装 `readme.txt` 中已有的 AI 函数。
- 主后端通过内网 REST API 调用 Python AI Service。
- 商品上架、下架、修改描述后，由主后端异步通知 AI Service 更新向量索引。
- Python AI Service 如果需要查询商品详情，通过主后端提供的内部商品摘要接口查询，不直接访问 PostgreSQL。

这样做的好处是语言边界清晰、方便替换模型或向量库，也方便后续部署扩容。

### 2.2 备选方案

演示版或课程原型可以临时采用同一台服务器部署：

- Spring Boot 监听 `8080`。
- Python AI Service 监听 `8001`。
- PostgreSQL、Chroma/Milvus、Redis 通过 Docker Compose 启动。

不建议长期采用 Java 通过命令行子进程调用 Python 函数。该方式调试简单，但并发、异常处理、超时控制和部署隔离都较差。

## 3. 全局接口约定

### 3.1 基础路径

| 接口类别 | Base URL | 调用方 | 被调用方 |
|---|---|---|---|
| 公开业务接口 | `/api/v1` | 前端 | 主后端 |
| 内部 AI 接口 | `/internal/v1/ai` | 主后端 | Python AI Service |
| 内部商品摘要接口 | `/internal/v1` | Python AI Service | 主后端 |

### 3.2 数据格式

- 请求体：`application/json`。
- 文件上传：`multipart/form-data`。
- 时间格式：ISO 8601，例如 `2026-05-26T10:30:00+08:00`。
- 金额：使用字符串保存十进制定点数，例如 `"299.00"`。
- ID：对外统一使用字符串，例如 `"10001"`。即使数据库内部使用整数，自 API 层也转成字符串，便于兼容 AI 函数中的 `str product_id`。

### 3.3 认证与权限

公开接口使用 Bearer Token：

```http
Authorization: Bearer <access_token>
```

角色初步定义：

| 角色 | 说明 |
|---|---|
| `CUSTOMER` | 普通购物用户 |
| `MERCHANT` | 商家 |
| `ADMIN` | 平台管理员 |

内部接口使用内网访问控制，并增加内部服务令牌：

```http
X-Internal-Token: <internal_service_token>
```

后续生产环境可以升级为 mTLS。

### 3.4 统一响应格式

成功响应：

```json
{
  "success": true,
  "code": "OK",
  "message": "成功",
  "data": {},
  "trace_id": "req_20260526103000001"
}
```

失败响应：

```json
{
  "success": false,
  "code": "PRODUCT_NOT_FOUND",
  "message": "商品不存在",
  "data": null,
  "trace_id": "req_20260526103000002"
}
```

### 3.5 常用错误码

| 错误码 | HTTP 状态码 | 说明 |
|---|---:|---|
| `INVALID_ARGUMENT` | 400 | 参数缺失或格式错误 |
| `UNAUTHORIZED` | 401 | 未登录或 Token 无效 |
| `FORBIDDEN` | 403 | 无权限访问 |
| `RESOURCE_NOT_FOUND` | 404 | 资源不存在 |
| `DUPLICATE_RESOURCE` | 409 | 用户名、手机号或资源重复 |
| `INSUFFICIENT_STOCK` | 409 | 库存不足 |
| `INSUFFICIENT_BALANCE` | 409 | 余额不足 |
| `FILE_TOO_LARGE` | 413 | 上传文件过大 |
| `UNSUPPORTED_FILE_TYPE` | 415 | 文件类型不支持 |
| `RATE_LIMITED` | 429 | 请求过于频繁 |
| `AI_SERVICE_TIMEOUT` | 504 | AI 服务超时 |
| `AI_SERVICE_UNAVAILABLE` | 503 | AI 服务不可用 |
| `INTERNAL_ERROR` | 500 | 服务器内部错误 |

### 3.6 分页格式

请求参数：

```text
page=1&size=20&sort=price_asc
```

响应数据：

```json
{
  "items": [],
  "page": 1,
  "size": 20,
  "total": 100,
  "has_next": true
}
```

### 3.7 团队分工与接口归属

当前团队分工为前端 2 人、后端 2 人、测试 1 人、AI 相关实现 1 人。接口按“谁主要实现、谁主要联调”的原则分配如下。详细请求、响应和权限仍以第 5 章至第 13 章的接口定义为准。

#### 3.7.1 分工总览

| 成员角色 | 建议负责范围 | 主要交付物 |
|---|---|---|
| 前端 1 | 用户购物端 | 登录注册、首页推荐、商品浏览、搜索、AI 助手、下单支付、个人订单页面 |
| 前端 2 | 商家端和管理端 | 商家商品管理、商品图片上传、管理员用户管理、平台监控页面 |
| 后端 1 | 账号、用户、商品和上传基础服务 | Auth/User/Product/Merchant/Admin User/Upload/Internal Product Summary 接口 |
| 后端 2 | 交易、行为和 AI 网关服务 | Search/Recommendation/Behavior/AI Chat/Order/Payment/Admin Metrics 接口 |
| AI 实现 | Python AI Service 和模型能力 | 向量索引、语义搜索、个性化推荐、AI 对话、视觉搜索预留实现 |
| 测试 | 全链路质量保障 | 接口测试、权限测试、联调测试、AI 降级测试、性能和安全测试报告 |

#### 3.7.2 前端 1：用户购物端接口

负责普通用户从进入系统到完成购买的页面和接口联调。

| 页面或功能 | 需要联调的接口 |
|---|---|
| 注册 | `POST /api/v1/auth/register` |
| 登录 | `POST /api/v1/auth/login` |
| 登出 | `POST /api/v1/auth/logout` |
| 当前用户信息 | `GET /api/v1/users/me`、`PATCH /api/v1/users/me` |
| 商品列表、筛选和排序 | `GET /api/v1/products` |
| 商品详情 | `GET /api/v1/products/{product_id}` |
| 首页个性化推荐 | `GET /api/v1/recommendations/home` |
| 语义搜索 | `POST /api/v1/search/semantic` |
| 视觉识图搜索 | `POST /api/v1/search/image`、`POST /api/v1/uploads/search-images` |
| 用户行为埋点 | `POST /api/v1/behavior-events` |
| AI 对话会话创建 | `POST /api/v1/ai/chat/sessions` |
| AI 助手发送消息 | `POST /api/v1/ai/chat/sessions/{session_id}/messages` |
| 清除 AI 对话历史 | `DELETE /api/v1/ai/chat/sessions/{session_id}/history` |
| 创建订单 | `POST /api/v1/orders` |
| 订单列表 | `GET /api/v1/orders` |
| 订单详情 | `GET /api/v1/orders/{order_id}` |
| 发起支付 | `POST /api/v1/orders/{order_id}/pay` |

前端 1 需要重点处理：

- 匿名访问和登录访问的状态切换。
- 搜索、推荐、AI 对话的加载态、空结果、失败提示和降级展示。
- AI 助手返回内容如果包含图片和链接，只按后端过滤后的安全内容渲染。
- 下单前校验购买数量，支付后刷新订单状态和库存展示。

#### 3.7.3 前端 2：商家端和管理端接口

负责商家商品管理、图片上传和平台管理页面。

| 页面或功能 | 需要联调的接口 |
|---|---|
| 商家商品列表 | `GET /api/v1/merchant/products` |
| 商品上架 | `POST /api/v1/merchant/products` |
| 商品编辑 | `PATCH /api/v1/merchant/products/{product_id}` |
| 商品补货 | `POST /api/v1/merchant/products/{product_id}/restock` |
| 商品下架或删除 | `DELETE /api/v1/merchant/products/{product_id}` |
| 商品图片上传 | `POST /api/v1/uploads/product-images` |
| 管理员用户列表 | `GET /api/v1/admin/users` |
| 修改用户状态 | `PATCH /api/v1/admin/users/{user_id}/status` |
| 平台监控概览 | `GET /api/v1/admin/metrics/overview` |

前端 2 需要重点处理：

- `MERCHANT` 和 `ADMIN` 角色的菜单和路由权限。
- 商品表单校验，包括名称、描述、价格、库存、标签和图片。
- 商品上架、编辑、下架后展示 `vector_index_status`，提示 AI 索引可能存在短暂延迟。
- 管理员页面避免把普通用户、商家和管理员操作入口混在一起。

#### 3.7.4 后端 1：账号、用户、商品和上传基础服务接口

负责稳定业务基础能力，并为前端、后端 2 和 AI Service 提供商品数据能力。

| 模块 | 主要实现接口 |
|---|---|
| 认证 | `POST /api/v1/auth/register`、`POST /api/v1/auth/login`、`POST /api/v1/auth/logout` |
| 用户 | `GET /api/v1/users/me`、`PATCH /api/v1/users/me` |
| 普通商品查询 | `GET /api/v1/products`、`GET /api/v1/products/{product_id}` |
| 商家商品管理 | `GET /api/v1/merchant/products`、`POST /api/v1/merchant/products`、`PATCH /api/v1/merchant/products/{product_id}`、`POST /api/v1/merchant/products/{product_id}/restock`、`DELETE /api/v1/merchant/products/{product_id}` |
| 上传 | `POST /api/v1/uploads/product-images`、`POST /api/v1/uploads/search-images` |
| 管理员用户管理 | `GET /api/v1/admin/users`、`PATCH /api/v1/admin/users/{user_id}/status` |
| AI 商品摘要内部接口 | `GET /internal/v1/products/{product_id}/ai-summary`、`POST /internal/v1/products/ai-summaries` |

后端 1 需要重点处理：

- JWT 鉴权、角色权限、用户状态校验和统一错误响应。
- 商品归属校验，商家只能修改自己的商品。
- 商品上架、编辑、下架后向后端 2 或任务队列提交 AI 索引更新事件。
- 上传文件的 MIME、扩展名、文件大小和文件头校验。
- 为 AI Service 提供只读、脱敏、稳定的商品摘要，不让 Python 服务直接访问业务数据库。

#### 3.7.5 后端 2：交易、行为和 AI 网关服务接口

负责订单交易链路、用户行为日志，以及主后端到 Python AI Service 的网关封装。

| 模块 | 主要实现接口 |
|---|---|
| 语义搜索 | `POST /api/v1/search/semantic` |
| 视觉搜索网关 | `POST /api/v1/search/image` |
| 首页推荐 | `GET /api/v1/recommendations/home` |
| 行为日志 | `POST /api/v1/behavior-events` |
| AI 对话会话 | `POST /api/v1/ai/chat/sessions` |
| AI 助手消息 | `POST /api/v1/ai/chat/sessions/{session_id}/messages` |
| 清除 AI 历史 | `DELETE /api/v1/ai/chat/sessions/{session_id}/history` |
| 订单 | `POST /api/v1/orders`、`GET /api/v1/orders`、`GET /api/v1/orders/{order_id}` |
| 支付 | `POST /api/v1/orders/{order_id}/pay` |
| 管理员监控 | `GET /api/v1/admin/metrics/overview` |
| AI 内部调用客户端 | 调用 `/internal/v1/ai/products/{product_id}/index`、`/internal/v1/ai/search/products`、`/internal/v1/ai/users/{user_id}/recommendations`、`/internal/v1/ai/chat/messages`、`/internal/v1/ai/search/image` 等 |

后端 2 需要重点处理：

- 下单和扣减库存必须使用事务，避免超卖。
- 搜索、推荐、AI 对话调用 Python AI Service 时必须设置超时、重试和降级策略。
- AI 返回的 HTML 内容对外返回前必须做白名单过滤。
- 用户行为日志应覆盖浏览、搜索、点击、下单和 AI 对话，供推荐和画像使用。
- 管理员监控概览需要聚合用户、商品、订单、搜索、AI 服务状态和向量库状态。

#### 3.7.6 AI 实现：Python AI Service 接口

负责实现和维护主后端调用的内部 AI 能力，不直接暴露给前端。

| AI 能力 | 需要实现的内部接口 | 对应 Python 函数或建议函数 |
|---|---|---|
| 商品向量索引新增或更新 | `POST /internal/v1/ai/products/{product_id}/index` | `prod_add_product(str product_id, str description)` |
| 删除商品向量索引 | `DELETE /internal/v1/ai/products/{product_id}/index` | `prod_delete_product(str product_id)` |
| 文本语义搜索 | `POST /internal/v1/ai/search/products` | `prod_search(str user_id, str query, int distance_threshold, int limit)` |
| 用户画像推荐 | `GET /internal/v1/ai/users/{user_id}/recommendations` | `user_search(user_id: str, maxnum: int)` |
| AI 助手对话 | `POST /internal/v1/ai/chat/messages` | `chat(str content, str user_id, str session_id)` |
| 清除对话历史 | `DELETE /internal/v1/ai/chat/history` | `delete_history(str user_id, str session_id)` |
| 视觉搜索 | `POST /internal/v1/ai/search/image` | 建议补充 `image_search(str user_id, str image_path_or_url, int limit)` |

AI 实现需要重点处理：

- 使用 FastAPI 或 Flask 将现有 Python 函数封装成 HTTP JSON 接口。
- 商品向量索引和删除操作要支持幂等，避免重复上架或重复删除导致异常。
- 语义搜索只返回商品 ID、分数和必要解释，商品详情由主后端查询。
- AI 助手如需商品详情，应调用后端 1 提供的 `/internal/v1/products/{product_id}/ai-summary` 或批量摘要接口。
- 视觉搜索目前是预留能力，需要补充图片特征提取、图片向量库或多模态模型方案。

#### 3.7.7 测试：接口和联调验证范围

测试人员不单独实现业务接口，负责制定用例、执行接口测试、记录缺陷并跟踪修复。

| 测试范围 | 覆盖接口 |
|---|---|
| 账号和权限测试 | 注册、登录、登出、当前用户信息、商家接口、管理员接口 |
| 商品测试 | 商品列表、详情、商家商品列表、上架、编辑、补货、下架、商品图片上传 |
| 搜索推荐测试 | 语义搜索、视觉搜索、首页推荐、行为记录 |
| AI 助手测试 | 创建会话、发送消息、清除历史、AI 超时和异常降级 |
| 订单支付测试 | 创建订单、订单列表、订单详情、支付、库存不足、重复支付 |
| 内部接口测试 | AI Service 内部接口、商品 AI 摘要接口、内部 Token 校验 |
| 性能和安全测试 | 登录注册、商品查询、语义搜索、AI 对话、下单、图片上传、XSS 和越权访问 |

测试优先级建议：

1. 第一轮先测主流程：注册、登录、商品列表、商品详情、商品上架、语义搜索、AI 对话、下单、支付。
2. 第二轮补充角色权限：匿名用户、普通用户、商家、管理员分别访问不属于自己的接口。
3. 第三轮覆盖异常和降级：库存不足、AI 超时、图片格式错误、Token 失效、商品下架后搜索不可见。
4. 第四轮做基础性能测试，重点关注商品查询、语义搜索、AI 对话和下单接口。

## 4. 核心数据对象

### 4.1 用户 User

```json
{
  "user_id": "u10001",
  "username": "alice",
  "phone": "13800000000",
  "role": "CUSTOMER",
  "nickname": "Alice",
  "avatar_url": "https://example.com/avatar.png",
  "created_at": "2026-05-26T10:30:00+08:00"
}
```

### 4.2 商品 Product

```json
{
  "product_id": "10001",
  "merchant_id": "m10001",
  "name": "蓝牙降噪耳机",
  "description": "适合通勤和学习的主动降噪蓝牙耳机",
  "category_id": "c_headphone",
  "category_name": "耳机",
  "price": "299.00",
  "stock": 120,
  "sales": 320,
  "rating": 4.8,
  "status": "ON_SALE",
  "tags": ["蓝牙", "降噪", "通勤"],
  "image_urls": [
    "https://example.com/products/10001/main.jpg"
  ],
  "detail_url": "https://example.com/products/10001",
  "created_at": "2026-05-26T10:30:00+08:00",
  "updated_at": "2026-05-26T10:30:00+08:00"
}
```

商品状态：

| 状态 | 说明 |
|---|---|
| `DRAFT` | 草稿 |
| `ON_SALE` | 上架销售中 |
| `OFF_SALE` | 已下架 |
| `DELETED` | 已删除 |

### 4.3 商品摘要 ProductSummary

用于搜索结果、推荐结果和 AI 上下文。

```json
{
  "product_id": "10001",
  "name": "蓝牙降噪耳机",
  "price": "299.00",
  "stock": 120,
  "image_url": "https://example.com/products/10001/main.jpg",
  "detail_url": "https://example.com/products/10001",
  "tags": ["蓝牙", "降噪"],
  "score": 0.93,
  "reason": "符合你对通勤、降噪和 300 元以内预算的要求"
}
```

### 4.4 订单 Order

```json
{
  "order_id": "o10001",
  "user_id": "u10001",
  "status": "CREATED",
  "total_amount": "598.00",
  "items": [
    {
      "product_id": "10001",
      "name": "蓝牙降噪耳机",
      "unit_price": "299.00",
      "quantity": 2
    }
  ],
  "receiver": {
    "name": "张三",
    "phone": "13800000000",
    "address": "浙江省杭州市..."
  },
  "created_at": "2026-05-26T10:30:00+08:00"
}
```

订单状态：

| 状态 | 说明 |
|---|---|
| `CREATED` | 已创建，待支付 |
| `PAID` | 已支付 |
| `SHIPPED` | 已发货 |
| `COMPLETED` | 已完成 |
| `CANCELLED` | 已取消 |
| `REFUNDED` | 已退款 |

### 4.5 用户行为 BehaviorEvent

用于用户画像、推荐和审计。

```json
{
  "event_type": "SEARCH",
  "user_id": "u10001",
  "product_id": "10001",
  "query": "300元以内蓝牙耳机",
  "metadata": {
    "source": "semantic_search"
  },
  "created_at": "2026-05-26T10:30:00+08:00"
}
```

事件类型：

| 类型 | 说明 |
|---|---|
| `VIEW` | 浏览商品 |
| `CLICK` | 点击商品 |
| `SEARCH` | 搜索 |
| `IMAGE_SEARCH` | 视觉搜索 |
| `CHAT` | AI 助手对话 |
| `ORDER` | 下单 |
| `FAVORITE` | 收藏 |

## 5. 公开业务接口

### 5.1 注册

```http
POST /api/v1/auth/register
```

权限：无需登录。

请求：

```json
{
  "username": "alice",
  "phone": "13800000000",
  "password": "Password123!",
  "role": "CUSTOMER"
}
```

响应：

```json
{
  "user_id": "u10001",
  "username": "alice",
  "role": "CUSTOMER"
}
```

说明：

- `role` 初期可允许注册 `CUSTOMER` 和 `MERCHANT`。
- `ADMIN` 应由后台初始化或管理员创建。

### 5.2 登录

```http
POST /api/v1/auth/login
```

请求：

```json
{
  "account": "alice",
  "password": "Password123!"
}
```

响应：

```json
{
  "access_token": "jwt_access_token",
  "refresh_token": "jwt_refresh_token",
  "expires_in": 7200,
  "user": {
    "user_id": "u10001",
    "username": "alice",
    "role": "CUSTOMER"
  }
}
```

### 5.3 登出

```http
POST /api/v1/auth/logout
```

权限：登录用户。

响应：

```json
{
  "logged_out": true
}
```

### 5.4 获取当前用户信息

```http
GET /api/v1/users/me
```

权限：登录用户。

响应：`User`。

### 5.5 修改当前用户信息

```http
PATCH /api/v1/users/me
```

权限：登录用户。

请求：

```json
{
  "nickname": "Alice",
  "phone": "13800000001",
  "avatar_url": "https://example.com/avatar.png"
}
```

响应：`User`。

## 6. 商品接口

### 6.1 商品列表和普通筛选

```http
GET /api/v1/products
```

权限：可匿名访问。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `keyword` | string | 否 | 商品名称或关键词 |
| `category_id` | string | 否 | 分类 ID |
| `min_price` | string | 否 | 最低价 |
| `max_price` | string | 否 | 最高价 |
| `tags` | string | 否 | 逗号分隔标签 |
| `sort` | string | 否 | `price_asc`、`price_desc`、`sales_desc`、`rating_desc` |
| `page` | int | 否 | 默认 1 |
| `size` | int | 否 | 默认 20 |

响应：

```json
{
  "items": [
    {
      "product_id": "10001",
      "name": "蓝牙降噪耳机",
      "price": "299.00",
      "stock": 120,
      "image_url": "https://example.com/products/10001/main.jpg",
      "sales": 320,
      "rating": 4.8
    }
  ],
  "page": 1,
  "size": 20,
  "total": 100,
  "has_next": true
}
```

### 6.2 商品详情

```http
GET /api/v1/products/{product_id}
```

权限：可匿名访问。

响应：`Product`。

### 6.3 商家商品列表

```http
GET /api/v1/merchant/products
```

权限：`MERCHANT`。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `status` | string | 否 | 商品状态 |
| `page` | int | 否 | 页码 |
| `size` | int | 否 | 每页数量 |

响应：分页 `Product` 列表。

### 6.4 商品上架

```http
POST /api/v1/merchant/products
```

权限：`MERCHANT`。

请求：

```json
{
  "name": "蓝牙降噪耳机",
  "description": "适合通勤和学习的主动降噪蓝牙耳机",
  "category_id": "c_headphone",
  "price": "299.00",
  "stock": 120,
  "tags": ["蓝牙", "降噪", "通勤"],
  "image_urls": [
    "https://example.com/products/10001/main.jpg"
  ]
}
```

响应：

```json
{
  "product_id": "10001",
  "status": "ON_SALE",
  "vector_index_status": "PENDING"
}
```

后端处理要求：

- 先写入 PostgreSQL 商品表和库存表。
- 再异步调用 AI Service 的商品向量索引接口。
- `description` 应组织为可向量化的精炼描述，至少包含商品名、用途、特点。

### 6.5 修改商品信息

```http
PATCH /api/v1/merchant/products/{product_id}
```

权限：`MERCHANT`，只能修改自己店铺商品。

请求：

```json
{
  "name": "升级款蓝牙降噪耳机",
  "description": "适合通勤和学习的主动降噪蓝牙耳机，续航更长",
  "price": "329.00",
  "tags": ["蓝牙", "降噪", "长续航"],
  "image_urls": [
    "https://example.com/products/10001/main.jpg"
  ]
}
```

响应：`Product`。

后端处理要求：

- 如果 `name`、`description` 或 `tags` 改变，需要重新调用 AI 商品索引接口。

### 6.6 商品补货

```http
POST /api/v1/merchant/products/{product_id}/restock
```

权限：`MERCHANT`。

请求：

```json
{
  "quantity": 50,
  "remark": "补货入库"
}
```

响应：

```json
{
  "product_id": "10001",
  "stock": 170
}
```

### 6.7 商品下架或删除

```http
DELETE /api/v1/merchant/products/{product_id}
```

权限：`MERCHANT`。

响应：

```json
{
  "product_id": "10001",
  "status": "OFF_SALE",
  "vector_index_status": "DELETE_PENDING"
}
```

后端处理要求：

- 初期建议实现为逻辑下架，而不是物理删除。
- 下架后调用 AI Service 删除向量索引，避免继续被语义搜索命中。

## 7. 搜索和推荐接口

### 7.1 语义搜索

```http
POST /api/v1/search/semantic
```

权限：登录用户，匿名用户可作为后续扩展。

请求：

```json
{
  "query": "300元以内适合通勤的蓝牙降噪耳机",
  "filters": {
    "category_id": "c_headphone",
    "min_price": "100.00",
    "max_price": "300.00",
    "in_stock": true
  },
  "distance_threshold": 0.9,
  "limit": 20
}
```

响应：

```json
{
  "query": "300元以内适合通勤的蓝牙降噪耳机",
  "relaxed": false,
  "items": [
    {
      "product_id": "10001",
      "name": "蓝牙降噪耳机",
      "price": "299.00",
      "stock": 120,
      "image_url": "https://example.com/products/10001/main.jpg",
      "detail_url": "https://example.com/products/10001",
      "score": 0.93,
      "reason": "语义匹配通勤、降噪和预算要求"
    }
  ]
}
```

后端处理流程：

1. 记录搜索行为。
2. 调用 AI Service 的文本检索接口。
3. 按 AI 返回的商品 ID 到 PostgreSQL 查询商品详情。
4. 过滤已下架、无库存或不符合筛选条件的商品。
5. 如果结果少于 1 条，可放宽 `distance_threshold` 或返回热门商品兜底。
6. 更新用户画像标签。

### 7.2 视觉识图搜索

```http
POST /api/v1/search/image
```

权限：登录用户。

请求类型：`multipart/form-data`

字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `image` | file | 是 | JPG、PNG，建议不超过 10MB |
| `limit` | int | 否 | 默认 20 |

响应：

```json
{
  "detected_object": "耳机",
  "items": [
    {
      "product_id": "10001",
      "name": "蓝牙降噪耳机",
      "price": "299.00",
      "image_url": "https://example.com/products/10001/main.jpg",
      "score": 0.88,
      "reason": "外观与上传图片相似"
    }
  ]
}
```

当前状态：

- 需求文档要求支持视觉识图。
- `readme.txt` 尚未给出图片检索 Python 函数。
- 本接口先作为前后端和架构预留接口，AI 组后续需要补充对应内部接口，建议命名为 `image_search(image_path_or_url, user_id, limit)`。

### 7.3 首页个性化推荐

```http
GET /api/v1/recommendations/home
```

权限：登录用户。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `limit` | int | 否 | 默认 20 |

响应：

```json
{
  "strategy": "USER_PROFILE",
  "items": [
    {
      "product_id": "10001",
      "name": "蓝牙降噪耳机",
      "price": "299.00",
      "image_url": "https://example.com/products/10001/main.jpg",
      "detail_url": "https://example.com/products/10001",
      "score": 0.91,
      "reason": "根据你的搜索和浏览偏好推荐"
    }
  ]
}
```

降级策略：

- 用户标签充分时，调用 AI Service 的用户推荐接口。
- 用户标签不足、AI 超时或结果为空时，返回销量最高或热门商品。

### 7.4 记录用户行为

```http
POST /api/v1/behavior-events
```

权限：登录用户。

请求：

```json
{
  "event_type": "VIEW",
  "product_id": "10001",
  "query": null,
  "metadata": {
    "page": "product_detail"
  }
}
```

响应：

```json
{
  "accepted": true
}
```

说明：

- 浏览、点击、收藏、搜索、下单等行为都应进入行为日志。
- 行为日志后续用于更新用户标签和推荐模型。

## 8. AI 助手接口

### 8.1 创建 AI 对话会话

```http
POST /api/v1/ai/chat/sessions
```

权限：登录用户。

请求：

```json
{
  "title": "耳机选购"
}
```

响应：

```json
{
  "session_id": "s10001",
  "title": "耳机选购",
  "created_at": "2026-05-26T10:30:00+08:00"
}
```

### 8.2 发送 AI 助手消息

```http
POST /api/v1/ai/chat/sessions/{session_id}/messages
```

权限：登录用户。

请求：

```json
{
  "content": "我想买一款适合上班通勤的蓝牙耳机，预算 300 元以内"
}
```

响应：

```json
{
  "session_id": "s10001",
  "answer": "这里是推荐结果...",
  "image_list": [
    "/images/prod_123.jpg"
  ],
  "link_list": [
    "https://example.com/products/10001"
  ],
  "related_products": [
    {
      "product_id": "10001",
      "name": "蓝牙降噪耳机",
      "price": "299.00",
      "image_url": "https://example.com/products/10001/main.jpg",
      "detail_url": "https://example.com/products/10001"
    }
  ]
}
```

前端渲染注意：

- `readme.txt` 中的 `chat()` 会返回带 `<img>` 和 `<a>` 标签的 `answer`。
- 如果前端使用 `v-html` 渲染，主后端必须做 HTML 白名单过滤，至少只允许 `img`、`a`、`br`、`p` 等安全标签。
- 链接必须限制协议为 `https://` 或站内路径，防止 XSS。

### 8.3 清除 AI 对话历史

```http
DELETE /api/v1/ai/chat/sessions/{session_id}/history
```

权限：登录用户。

响应：

```json
{
  "session_id": "s10001",
  "history_deleted": true
}
```

## 9. 订单和支付接口

### 9.1 创建订单

```http
POST /api/v1/orders
```

权限：`CUSTOMER`。

请求：

```json
{
  "items": [
    {
      "product_id": "10001",
      "quantity": 2
    }
  ],
  "receiver": {
    "name": "张三",
    "phone": "13800000000",
    "address": "浙江省杭州市..."
  }
}
```

响应：`Order`。

后端处理要求：

- 创建订单和扣减库存必须在同一事务中完成。
- 必须校验库存，避免超卖。
- 下单成功后记录 `ORDER` 行为并更新用户画像。

### 9.2 查询订单列表

```http
GET /api/v1/orders
```

权限：登录用户。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `status` | string | 否 | 订单状态 |
| `page` | int | 否 | 页码 |
| `size` | int | 否 | 每页数量 |

响应：分页 `Order` 列表。

### 9.3 查询订单详情

```http
GET /api/v1/orders/{order_id}
```

权限：登录用户，只能查询自己的订单；管理员可查询全部。

响应：`Order`。

### 9.4 发起支付

```http
POST /api/v1/orders/{order_id}/pay
```

权限：`CUSTOMER`。

请求：

```json
{
  "payment_method": "BALANCE"
}
```

响应：

```json
{
  "payment_id": "p10001",
  "order_id": "o10001",
  "payment_status": "PAID",
  "paid_at": "2026-05-26T10:30:00+08:00"
}
```

说明：

- 课程原型可以先实现余额支付。
- 后续接第三方支付时，需要增加支付网关回调接口，并对回调签名做校验。

## 10. 管理员接口

### 10.1 用户管理列表

```http
GET /api/v1/admin/users
```

权限：`ADMIN`。

查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `role` | string | 否 | 用户角色 |
| `keyword` | string | 否 | 用户名或手机号 |
| `page` | int | 否 | 页码 |
| `size` | int | 否 | 每页数量 |

响应：分页 `User` 列表。

### 10.2 修改用户状态

```http
PATCH /api/v1/admin/users/{user_id}/status
```

权限：`ADMIN`。

请求：

```json
{
  "status": "DISABLED",
  "reason": "异常访问"
}
```

响应：

```json
{
  "user_id": "u10001",
  "status": "DISABLED"
}
```

### 10.3 平台监控概览

```http
GET /api/v1/admin/metrics/overview
```

权限：`ADMIN`。

响应：

```json
{
  "user_count": 10000,
  "product_count": 5000,
  "order_count_today": 300,
  "search_count_today": 1200,
  "ai_chat_count_today": 260,
  "ai_service_status": "UP",
  "vector_db_status": "UP"
}
```

## 11. 内部 AI Service 接口

本章接口由 Python AI Service 实现。主后端调用这些接口，不直接调用 Python 函数。

### 11.1 商品向量索引新增或更新

```http
POST /internal/v1/ai/products/{product_id}/index
```

对应 Python 函数：

```python
prod_add_product(str product_id, str description)
```

请求：

```json
{
  "description": "蓝牙降噪耳机。用途：通勤、学习、会议。特点：主动降噪、长续航、300元以内。",
  "metadata": {
    "name": "蓝牙降噪耳机",
    "category_id": "c_headphone",
    "tags": ["蓝牙", "降噪", "通勤"]
  }
}
```

响应：

```json
{
  "product_id": "10001",
  "accepted": true,
  "index_status": "PENDING"
}
```

说明：

- AI 函数本身为非阻塞异步写入，接口可直接返回 `PENDING`。
- 如果 Python 函数抛错，返回 `AI_INDEX_FAILED`。

### 11.2 删除商品向量索引

```http
DELETE /internal/v1/ai/products/{product_id}/index
```

对应 Python 函数：

```python
prod_delete_product(str product_id)
```

响应：

```json
{
  "product_id": "10001",
  "deleted": true
}
```

### 11.3 文本语义搜索

```http
POST /internal/v1/ai/search/products
```

对应 Python 函数：

```python
prod_search(str user_id, str query, int distance_threshold = 0.9, int limit = 50)
```

请求：

```json
{
  "user_id": "u10001",
  "query": "适合通勤的蓝牙降噪耳机",
  "distance_threshold": 0.9,
  "limit": 50
}
```

响应：

```json
{
  "product_ids": ["10001", "10002", "10003"]
}
```

说明：

- `readme.txt` 中说明 `distance_threshold` 范围为 `0~4`，越小越严格。
- 主后端收到商品 ID 后，应到 PostgreSQL 查询商品详情并做状态、库存、价格筛选。

### 11.4 根据用户画像推荐商品

```http
GET /internal/v1/ai/users/{user_id}/recommendations
```

对应 Python 函数：

```python
user_search(user_id: str, maxnum: int = 5)
```

查询参数：

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `maxnum` | int | 否 | Python 函数最终约返回 `maxnum * 10` 个商品 ID |

响应：

```json
{
  "user_id": "u10001",
  "product_ids": ["10001", "10002", "10003"]
}
```

### 11.5 AI 助手对话

```http
POST /internal/v1/ai/chat/messages
```

对应 Python 函数：

```python
chat(str content, str user_id, str session_id)
```

请求：

```json
{
  "content": "推荐 300 元以内的蓝牙耳机",
  "user_id": "u10001",
  "session_id": "s10001"
}
```

响应：

```json
{
  "answer": "这里是为你找到的商品...",
  "image_list": [
    "/images/prod_123.jpg"
  ],
  "link_list": [
    "https://example.com/products/10001"
  ],
  "raw": {}
}
```

说明：

- Python 返回值为 dict，至少应包含 `answer`。
- `answer` 可能包含 HTML 标签，主后端对外返回前应做安全过滤。

### 11.6 清除 AI 对话历史

```http
DELETE /internal/v1/ai/chat/history
```

对应 Python 函数：

```python
delete_history(str user_id, str session_id)
```

请求：

```json
{
  "user_id": "u10001",
  "session_id": "s10001"
}
```

响应：

```json
{
  "deleted": true
}
```

### 11.7 视觉搜索内部接口

```http
POST /internal/v1/ai/search/image
```

当前状态：待 AI 组补充实现。

建议 Python 函数：

```python
image_search(str user_id, str image_path_or_url, int limit = 20)
```

请求：

```json
{
  "user_id": "u10001",
  "image_path_or_url": "s3://temp-search/u10001/xxx.jpg",
  "limit": 20
}
```

响应：

```json
{
  "detected_object": "耳机",
  "product_ids": ["10001", "10002"],
  "scores": {
    "10001": 0.88,
    "10002": 0.81
  }
}
```

隐私要求：

- 主后端不应长期保存用户上传原图。
- 可以保存匿名化特征、搜索日志和已脱敏行为数据。

## 12. Python AI Service 需要主后端提供的内部接口

`readme.txt` 中提到 AI 侧需要函数：

```python
search(str id)
```

含义：根据商品 ID 得到所有相关信息，返回结构化字符串，例如“商品名 xx，价格 xx，网页链接 xx”。

建议不要让 Python AI Service 直接查询 PostgreSQL，而是由主后端提供内部接口。

### 12.1 查询单个商品 AI 摘要

```http
GET /internal/v1/products/{product_id}/ai-summary
```

调用方：Python AI Service。  
被调用方：主后端。

响应：

```json
{
  "product_id": "10001",
  "summary_text": "商品名：蓝牙降噪耳机；价格：299.00元；库存：120；特点：主动降噪、长续航、适合通勤；网页链接：https://example.com/products/10001",
  "product": {
    "product_id": "10001",
    "name": "蓝牙降噪耳机",
    "price": "299.00",
    "stock": 120,
    "detail_url": "https://example.com/products/10001",
    "image_url": "https://example.com/products/10001/main.jpg"
  }
}
```

Python 侧可将该接口封装为：

```python
def search(product_id: str) -> str:
    # 调用 GET /internal/v1/products/{product_id}/ai-summary
    # 返回 response["summary_text"]
    ...
```

### 12.2 批量查询商品 AI 摘要

```http
POST /internal/v1/products/ai-summaries
```

请求：

```json
{
  "product_ids": ["10001", "10002", "10003"]
}
```

响应：

```json
{
  "items": [
    {
      "product_id": "10001",
      "summary_text": "商品名：蓝牙降噪耳机；价格：299.00元；网页链接：https://example.com/products/10001"
    }
  ]
}
```

说明：

- AI 助手生成推荐理由时会频繁查询多个商品，批量接口可以减少网络开销。

## 13. 对象存储和图片上传接口

### 13.1 上传商品图片

```http
POST /api/v1/uploads/product-images
```

权限：`MERCHANT`。

请求类型：`multipart/form-data`

字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `file` | file | 是 | JPG、PNG，建议不超过 10MB |

响应：

```json
{
  "url": "https://example.com/products/tmp/xxx.jpg",
  "object_key": "products/tmp/xxx.jpg"
}
```

### 13.2 上传视觉搜索临时图片

```http
POST /api/v1/uploads/search-images
```

权限：登录用户。

响应：

```json
{
  "temp_url": "s3://temp-search/u10001/xxx.jpg",
  "expires_at": "2026-05-26T11:30:00+08:00"
}
```

说明：

- 该接口可被 `/api/v1/search/image` 内部复用，也可由前端先上传再发起搜索。
- 临时图片应设置自动清理策略。

## 14. 关键业务流程

### 14.1 商品上架流程

```text
商家前端
  -> POST /api/v1/merchant/products
主后端
  -> 校验商家权限和商品字段
  -> 写入 PostgreSQL
  -> 提交商品向量索引任务
  -> POST /internal/v1/ai/products/{product_id}/index
Python AI Service
  -> prod_add_product(product_id, description)
  -> 写入 Chroma/Milvus
主后端
  -> 返回上架成功，vector_index_status=PENDING
```

### 14.2 语义搜索流程

```text
用户前端
  -> POST /api/v1/search/semantic
主后端
  -> 记录 SEARCH 行为
  -> POST /internal/v1/ai/search/products
Python AI Service
  -> prod_search(user_id, query, distance_threshold, limit)
  -> 返回 product_ids
主后端
  -> 查询 PostgreSQL 商品详情
  -> 过滤下架、无库存、价格不匹配商品
  -> 记录用户标签更新
  -> 返回商品列表
```

### 14.3 AI 助手流程

```text
用户前端
  -> POST /api/v1/ai/chat/sessions/{session_id}/messages
主后端
  -> 校验登录态和 session 归属
  -> POST /internal/v1/ai/chat/messages
Python AI Service
  -> chat(content, user_id, session_id)
  -> 如需商品详情，调用 GET /internal/v1/products/{id}/ai-summary
  -> 返回 answer、image_list、link_list
主后端
  -> HTML 安全过滤
  -> 记录 CHAT 行为
  -> 返回给前端
```

### 14.4 商品购买流程

```text
用户前端
  -> POST /api/v1/orders
主后端
  -> 开启事务
  -> 校验商品存在和库存充足
  -> 扣减库存
  -> 创建订单和订单明细
  -> 提交事务
  -> 记录 ORDER 行为
  -> 返回订单
```

## 15. 初步数据库表建议

| 表名 | 说明 |
|---|---|
| `users` | 用户账号、角色、状态 |
| `user_profiles` | 用户昵称、头像、联系方式等资料 |
| `merchants` | 商家店铺信息 |
| `products` | 商品主表 |
| `product_images` | 商品图片 |
| `inventory_logs` | 库存变化记录 |
| `orders` | 订单主表 |
| `order_items` | 订单明细 |
| `payments` | 支付记录 |
| `behavior_logs` | 用户行为日志 |
| `user_tags` | 用户画像标签 |
| `chat_sessions` | AI 对话会话 |
| `chat_messages` | AI 对话消息记录，可只保存必要内容 |
| `audit_logs` | 登录、支付、上架、库存修改等审计日志 |

向量数据库集合建议：

| 集合 | 主键 | 内容 |
|---|---|---|
| `product_text_vectors` | `product_id` | 商品名称、描述、标签生成的文本向量 |
| `product_image_vectors` | `product_id` 或 `image_id` | 商品图片特征向量 |
| `user_profile_vectors` | `user_id` | 用户画像向量 |

## 16. 性能和超时约定

根据需求规格说明，初步建议：

| 接口类别 | 目标响应时间 | 最大可接受时间 |
|---|---:|---:|
| 登录、注册 | 3 秒以内 | 5 秒 |
| 普通商品查询 | 2 秒以内 | 5 秒 |
| 语义搜索 | 3 秒以内 | 5 秒 |
| 视觉搜索 | 4 秒以内 | 8 秒 |
| AI 助手对话 | 5 秒以内 | 10 秒 |
| 下单和库存扣减 | 3 秒以内 | 5 秒 |
| 商品上架 | 3 秒以内 | 5 秒 |

后端调用 AI Service 的初步超时：

| AI 接口 | 连接超时 | 读取超时 | 降级方式 |
|---|---:|---:|---|
| 商品索引 | 1 秒 | 3 秒 | 记录失败任务，后台重试 |
| 文本搜索 | 1 秒 | 3 秒 | 放宽为普通关键词搜索或热门商品 |
| 用户推荐 | 1 秒 | 3 秒 | 热门商品推荐 |
| AI 对话 | 1 秒 | 8 秒 | 返回 AI 暂不可用提示 |
| 视觉搜索 | 1 秒 | 5 秒 | 返回图片搜索失败提示 |

## 17. 安全要求

- 所有公开接口必须使用 HTTPS。
- 登录密码只存储哈希值，不存储明文。
- 商品上架、补货、下架必须校验商家商品归属。
- 订单查询必须校验订单归属。
- AI 返回 HTML 内容必须经过主后端白名单过滤。
- 图片上传必须校验 MIME 类型、文件扩展名、文件大小和真实文件头。
- 搜索、登录、注册、AI 对话、图片上传应做限流。
- 支付、库存修改、商品上下架、权限修改必须写入审计日志。
- 内部接口不得暴露到公网。

## 18. 待确认问题

以下问题会影响最终接口细节，但不阻塞初步架构搭建：

1. 主后端最终技术栈是否确定为 Java Spring Boot。如果不是，接口定义仍可复用，但 DTO 和代码生成方式需要调整。
2. 用户是否有余额账户。如果没有，支付接口应直接对接第三方支付或只保留模拟支付。
3. 视觉识图搜索的 AI 函数尚未在 `readme.txt` 中出现，需要 AI 组补充实现。
4. 商品是否需要审核流程。如果需要，应增加 `PENDING_REVIEW` 商品状态和管理员审核接口。
5. 用户标签由 AI 模块维护还是主后端维护。当前建议行为日志在主后端落库，标签和向量由 AI Service 生成或更新。
6. AI 助手返回 HTML 是否必须保留。如果不必须，建议后续改成结构化消息，由前端自己渲染图片和链接，安全性更好。

## 19. 建议的下一步

1. 将本文档整理成 OpenAPI 3.0 YAML。
2. 根据第 4 章核心对象生成前后端 DTO。
3. 搭建 Spring Boot 主后端基础模块：Auth、Product、Order、Search、AI Gateway。
4. 搭建 Python FastAPI AI Service，并封装 `readme.txt` 中已有函数。
5. 先完成注册、登录、商品上架、语义搜索、首页推荐、AI 对话、下单的闭环。
6. 再补充视觉识图、管理员监控、支付网关、审计和性能优化。
