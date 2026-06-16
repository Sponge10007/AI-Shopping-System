# AI 智能购物系统测试报告

## 1. 测试目标

本次测试目标是覆盖仓库主要代码，而不是只测试后端。测试对象包括 `frontend/`、`backend/`、`ai-service/`、根目录 `tests/`、`scripts/` 和 `database/`，重点验证前端页面与 API 层、Spring Boot 主后端、Python AI Service 路由、跨服务接口一致性、脚本/迁移文件可检查性以及安全缺口。

响应时间不作为本次测试通过或失败标准。接口文档和实际实现不一致时，以当前实际实现为准，并在本报告记录差异。

## 2. 测试环境

| 项目 | 内容 |
|---|---|
| 执行目录 | `/mnt/d/code/SE/AI-Shopping-System` |
| 执行环境 | WSL2：`Linux myk 6.6.114.1-microsoft-standard-WSL2 x86_64` |
| 前端运行时 | npm `10.9.2`，实际 Node 可执行文件 `/mnt/d/nodejs/node.exe`，版本 `v22.14.0`；命令均从 WSL 路径执行 |
| 前端技术栈 | Vue 3、Vite、Vue Router、Vitest、@vue/test-utils、jsdom |
| 后端运行时 | OpenJDK `17.0.19`、Apache Maven `3.8.7` |
| 后端技术栈 | Spring Boot 3.3.5、Spring MVC、Validation、Security、JPA、JdbcTemplate、JUnit 5、MockMvc、Mockito |
| AI Service 运行时 | Python `3.12.3`；已创建 `ai-service/.venv`，pip `26.1.2`，pytest `8.3.3`；系统 Python 仍不安装 pytest，测试使用 `.venv` 内 Python |
| AI Service 技术栈 | 实际入口为标准库 `ThreadingHTTPServer` 的 `app/api/py_api_server.py`，不是当前脚本中写的 FastAPI/uvicorn `app.main` |
| 数据库/脚本 | PostgreSQL SQL migrations；`scripts/dev.sh` |

实际执行过的关键命令：

| 目录 | 命令 | 结果 |
|---|---|---|
| `frontend/` | `npm install` | 通过；新增 Vitest/jsdom 依赖；npm audit 报 5 个漏洞 |
| `frontend/` | `npm run build` | 第一次因 `AdminUsersView.vue` 可选字段类型失败；修复后通过 |
| `frontend/` | `npm run test` | 通过，10 个测试文件、24 个测试 |
| `backend/` | `mvn test` | 通过，24 个测试，0 failures/errors/skipped |
| `ai-service/` | `python3 -m venv --clear .venv` | 通过；已创建可用虚拟环境 |
| `ai-service/` | `./.venv/bin/python -m pytest -s tests` | 通过，7 个测试通过 |
| `ai-service/` | `./.venv/bin/python -m pip install --upgrade pip`、`./.venv/bin/python -m pip install -r requirements.txt` | 通过；pytest 等依赖已安装在 `.venv` 内 |
| `ai-service/` | Python 临时 harness 直接调用 AI Service 测试函数 | 保留为补充验证；正式 pytest 已通过 |
| 根目录 | `ai-service/.venv/bin/python -m pytest -s tests/pytest` | 通过，5 个跨服务契约测试通过 |
| 根目录 | Python 临时 harness 直接调用 `tests/pytest/test_contracts.py` | 通过，5 个跨服务契约测试断言通过 |
| 根目录 | `bash -n scripts/dev.sh` | 通过，脚本语法可解析 |
| 根目录 | Python 静态检查 `database/migrations/*.sql` | 通过，3 个 migration 文件非空且包含 SQL 语句终止符 |

## 3. 仓库结构与测试对象

| 目录 | 测试对象 | 本次处理 |
|---|---|---|
| `frontend/` | Vue 3 + Vite 前端、API 层、路由、购物/订单/AI/商家/管理员页面、公共组件 | 新增 Vitest 配置和 10 个测试文件；执行 build/test |
| `backend/` | Spring Boot 主后端接口、服务层、安全缺口、MockMvc 契约 | 复用工作区已有后端测试目录并执行 `mvn test` |
| `ai-service/` | Python 标准库 HTTP AI Service 路由、向量索引、搜索、推荐、AI 对话、历史清除、异常封装 | 替换原来不匹配的 FastAPI 测试，新增 mock-based pytest 测试；正式 pytest 已通过 |
| `tests/` | 跨服务路径、字段、接口契约和现有 Postman 资产 | 新增静态契约 pytest；pytest 断言通过 |
| `scripts/` | `scripts/dev.sh` | `bash -n` 通过；内容发现 AI Service 启动命令过期 |
| `database/` | SQL migrations 和 seed | 静态检查通过；未连接真实 PostgreSQL 执行迁移 |
| `docs/` | 测试报告 | 重写为全项目报告 |

## 4. 前端测试结果

| 编号 | 页面/模块 | 测试点 | 涉及文件 | 预期结果 | 实际结果 | 是否通过 |
|---|---|---|---|---|---|---|
| FE01 | API 调用层 | 登录、注册 JSON 请求路径和 payload | `frontend/src/services/api.ts`、`frontend/src/services/api.test.ts` | 调用 `/api/v1/auth/register`、`/api/v1/auth/login` | Vitest 断言通过 | 通过 |
| FE02 | API 调用层 | 商品列表、商品详情、语义搜索、首页推荐 fallback | 同上 | 后端不可用时返回本地 fallback 数据 | Vitest 断言通过 | 通过 |
| FE03 | API 调用层 | AI 对话、创建订单、支付 fallback | 同上 | 后端不可用时页面可得到可渲染结果 | Vitest 断言通过 | 通过 |
| FE04 | API 调用层 | 商家、管理员、上传接口路径 | 同上 | 调用商家/管理员/上传实际后端路径 | Vitest 断言通过 | 通过 |
| FE05 | API 调用层 | 商品图片 multipart 字段 | `frontend/src/services/api.ts` | 字段名应为后端要求的 `image` | 已从 `file` 修正为 `image`，测试通过 | 通过 |
| FE06 | 购物页 | 商品列表、名称、价格、图片、推荐渲染 | `frontend/src/views/ShoppingView.vue`、`ShoppingView.test.ts` | 页面渲染商品和推荐 | Vitest 断言通过 | 通过 |
| FE07 | 购物页 | 搜索输入和点击搜索调用语义搜索 API | 同上 | 输入关键词后调用 `semanticSearch` | Vitest 断言通过 | 通过 |
| FE08 | 购物页 | 推荐为空、商品为空、搜索失败提示 | 同上 | 显示空状态或错误提示，不崩溃 | Vitest 断言通过 | 通过 |
| FE09 | 订单页 | 订单列表渲染、支付按钮调用 API | `frontend/src/views/OrdersView.vue`、`OrdersView.test.ts` | 渲染订单并调用 `payOrder` | Vitest 断言通过 | 通过 |
| FE10 | 订单页 | API 失败错误提示 | 同上 | 显示错误 banner，不崩溃 | Vitest 断言通过 | 通过 |
| FE11 | AI 助手页 | 创建会话、发送消息、显示回复和相关商品 | `frontend/src/views/AIChatView.vue`、`AIChatView.test.ts` | AI 回复和相关商品可渲染 | Vitest 断言通过 | 通过 |
| FE12 | AI 助手页 | 发送失败 fallback | 同上 | 显示“AI 助手暂时无法响应” | Vitest 断言通过 | 通过 |
| FE13 | 商家列表页 | 商品列表、下架确认、API 失败提示 | `MerchantView.vue`、`MerchantView.test.ts` | 渲染商品、下架调用 API、失败 alert | Vitest 断言通过 | 通过 |
| FE14 | 商家上架页 | 名称、价格、库存校验 | `MerchantUploadView.vue`、`MerchantUploadView.test.ts` | 空名称、非法价格、负库存给出错误 | Vitest 断言通过 | 通过 |
| FE15 | 商家上架页 | 合法商品上架调用 API | 同上 | 提交名称、描述、价格、库存、标签、图片 | Vitest 断言通过 | 通过 |
| FE16 | 商家补货页 | 补货数量校验和补货 API | `MerchantRestockView.vue`、`MerchantRestockView.test.ts` | 非正数拒绝，正数调用 `restockProduct` | Vitest 断言通过 | 通过 |
| FE17 | 管理员入口页 | 用户管理和平台监控入口渲染 | `AdminView.vue`、`AdminView.test.ts` | 管理入口可见 | Vitest 断言通过 | 通过 |
| FE18 | 管理员用户页 | 用户按角色渲染、状态修改 API | `AdminUsersView.vue`、`AdminView.test.ts` | 管理员/商家/普通用户列表可见，按钮调用 API | Vitest 断言通过 | 通过 |
| FE19 | 管理员指标页 | 平台监控指标和服务状态 | `AdminMetricsView.vue`、`AdminView.test.ts` | 指标和 AI/Vector 状态可见 | Vitest 断言通过 | 通过 |
| FE20 | 公共组件 | 商品网格有商品、无商品、图片/名称/价格、点击事件 | `ProductGrid.vue`、`ProductGrid.test.ts` | 渲染核心字段，空状态可见，触发 `select/add` | 补充空状态和事件后通过 | 通过 |
| FE21 | 路由权限 | 未登录访问订单页、普通用户访问管理员页、管理员访问管理员页 | `frontend/src/router/index.ts`、`index.test.ts` | 未登录跳登录，普通用户跳首页，管理员可进入 | Vitest 断言通过 | 通过 |
| FE22 | 构建检查 | `vue-tsc --noEmit && vite build` | `frontend/` | 构建成功 | 修复 `AdminUsersView` 可选字段类型后通过 | 通过 |

前端命令结果：`npm run test` 最终 10 files / 24 tests passed；`npm run build` 最终通过。

## 5. 后端测试结果

| 编号 | 模块 | 测试点 | 请求接口 | 预期结果 | 实际结果 | 是否通过 |
|---|---|---|---|---|---|---|
| BE01 | 账号 | 注册缺少用户名参数校验 | `POST /api/v1/auth/register` | 400，`INVALID_ARGUMENT` | MockMvc 通过 | 通过 |
| BE02 | 账号 | 登录统一成功响应 | `POST /api/v1/auth/login` | 200，返回 access token | MockMvc 通过 | 通过 |
| BE03 | 商品 | 商品列表分页响应 | `GET /api/v1/products` | 200，返回商品分页 | MockMvc 通过 | 通过 |
| BE04 | 商家商品 | 创建商品非法价格 | `POST /api/v1/merchant/products` | 400，参数错误 | MockMvc 通过 | 通过 |
| BE05 | 商家权限缺口 | 匿名访问商家商品接口当前行为 | `GET /api/v1/merchant/products` | 记录当前匿名可访问 | MockMvc 通过，暴露权限缺口 | 通过 |
| BE06 | 搜索 | 空 query 语义搜索 | `POST /api/v1/search/semantic` | 400，参数错误 | MockMvc 通过 | 通过 |
| BE07 | 行为日志 | 非法 event type 当前行为 | `POST /api/v1/behavior-events` | 记录当前接受任意非空事件 | MockMvc 通过，暴露校验缺口 | 通过 |
| BE08 | AI 助手 | 空消息参数校验 | `POST /api/v1/ai/chat/sessions/{id}/messages` | 400，参数错误 | MockMvc 通过 | 通过 |
| BE09 | 订单 | 创建订单 items 为空 | `POST /api/v1/orders` | 400，参数错误 | MockMvc 通过 | 通过 |
| BE10 | 管理员权限缺口 | 匿名访问管理员用户列表当前行为 | `GET /api/v1/admin/users` | 记录当前匿名可访问 | MockMvc 通过，暴露权限缺口 | 通过 |
| BE11 | 内部接口缺口 | 缺少内部 Token 访问商品摘要 | `GET /internal/v1/products/{id}/ai-summary` | 记录当前匿名可访问 | MockMvc 通过，暴露内部鉴权缺口 | 通过 |
| BE12 | 上传 | 非图片 multipart 当前行为 | `POST /api/v1/uploads/product-images` | 记录当前未校验 MIME/文件头 | MockMvc 通过，暴露上传校验缺口 | 通过 |
| BE13 | 搜索服务 | AI 返回 ID 后按价格/库存过滤并记录行为 | `SearchService.semanticSearch` | 只返回符合条件商品 | Mockito 单测通过 | 通过 |
| BE14 | 搜索服务 | AI 无结果降级热门商品 | `SearchService.semanticSearch` | `relaxed=true` 且返回热门商品 | Mockito 单测通过 | 通过 |
| BE15 | 图片搜索 | 图片搜索返回检测对象和商品摘要 | `SearchService.imageSearch` | 返回关键词和商品列表 | Mockito 单测通过 | 通过 |
| BE16 | AI 助手安全 | 过滤 `<script>`、事件属性、`javascript:` | `AiChatService.sendMessage` | 返回安全 HTML/链接/图片 | Mockito 单测通过 | 通过 |
| BE17 | AI 助手降级 | AI Service 不可用 | `AiChatService.sendMessage` | 返回可理解 fallback | Mockito 单测通过 | 通过 |
| BE18 | 订单 | DB 不可用时订单列表降级 | `OrderService.listOrders` | 返回样例订单 | Mockito 单测通过 | 通过 |
| BE19 | 订单 | DB 不可用且订单不存在 | `OrderService.getOrder` | 抛出 `RESOURCE_NOT_FOUND` | Mockito 单测通过 | 通过 |
| BE20 | 管理员 | DB 和 AI 健康检查失败时指标降级 | `AdminService.overview` | 样例计数、AI `DOWN`、Vector `UNKNOWN` | Mockito 单测通过 | 通过 |
| BE21 | 上传 | 上传服务当前不校验文件头 | `UploadService.uploadProductImage` | 记录当前行为 | Mockito 单测通过 | 通过 |
| BE22 | 内部摘要 | 单个商品 AI 摘要字段 | `InternalProductService.getAiSummary` | 包含商品摘要，不暴露 merchantId | Mockito 单测通过 | 通过 |
| BE23 | 内部摘要 | 批量商品 AI 摘要顺序 | `InternalProductService.getAiSummaries` | 保留请求 ID 顺序 | Mockito 单测通过 | 通过 |

后端命令结果：`mvn test` 通过，24 tests run，0 failures，0 errors，0 skipped。

## 6. AI Service 测试结果

| 编号 | 模块 | 测试点 | 接口/函数 | 预期结果 | 实际结果 | 是否通过 |
|---|---|---|---|---|---|---|
| AI01 | 路由基础 | JSON 响应封装 | `json_response` | 返回 UTF-8 JSON 和状态码 | pytest 断言通过 | 通过 |
| AI02 | 商品向量索引 | 新增/更新商品索引 | `POST /internal/v1/ai/products/{product_id}/index` | 调用 `prod_add_product(product_id, description)` | mock 断言通过 | 通过 |
| AI03 | 商品向量索引 | 删除商品索引 | `DELETE /internal/v1/ai/products/{product_id}/index` | 调用 `prod_delete_product(product_id)` | mock 断言通过 | 通过 |
| AI04 | 语义搜索 | 文本语义搜索参数和默认值 | `POST /internal/v1/ai/search/products` | 调用 `prod_search` 并返回商品 ID | mock 断言通过 | 通过 |
| AI05 | 语义搜索 | 缺少 query 参数 | `search_product_ids` | 抛出 `ValueError` 并可转为 400 | mock 断言通过 | 通过 |
| AI06 | 用户画像推荐 | 推荐接口 maxnum | `GET /internal/v1/ai/users/{user_id}/recommendations` | 调用 `user_search(user_id, maxnum)` | mock 断言通过 | 通过 |
| AI07 | 视觉搜索 | 图片搜索路由 | `POST /internal/v1/ai/search/image` | 调用 `ImageAI.image_search` | mock 断言通过；接口存在但依赖真实 ImageAI/LLM 时仍需环境 | 通过 |
| AI08 | AI 对话 | 对话消息路由 | `POST /internal/v1/ai/chat/messages` | 调用 `chat(content, user_id, session_id)` | mock 断言通过 | 通过 |
| AI09 | AI 对话 | 清除历史 | `DELETE /internal/v1/ai/chat/history` | 调用 `delete_history(user_id, session_id)` | mock 断言通过 | 通过 |
| AI10 | 异常封装 | AI/向量函数异常 | `do_POST` | 返回 `{ok:false,error:...}`，不让异常逃逸 | mock 断言通过 | 通过 |
| AI11 | 内部 Token | 内部接口 Token 校验 | `/internal/v1/ai/**` | 应校验 `X-Internal-Token` | 当前未校验；测试记录当前缺口 | 未实现 |

AI Service 正式 pytest 已通过：`./.venv/bin/python -m pytest -s tests` 收集 7 个测试并全部通过。测试通过 mock 避免真实 LLM、Chroma、SentenceTransformer 和网络服务。默认 capture 模式曾在 `/mnt/d` 挂载盘触发 pytest 临时文件错误，因此本次正式结果采用 `-s` 关闭 capture。

真实 API 冒烟测试：`GET /health` 返回正常；`POST /internal/v1/ai/chat/messages` 已能快速返回结构化结果。当前本地 `.env` 中配置的 DeepSeek/OpenAI-compatible key 被远端返回 401 invalid api key，因此真实模型回答尚未通过，需要更换同一服务商的有效 API key/base URL/model 组合。

## 7. 跨服务接口一致性测试结果

| 编号 | 链路 | 前端调用 | 后端接口 | AI Service 接口 | 是否一致 | 说明 |
|---|---|---|---|---|---|---|
| CS01 | 登录 | `/api/v1/auth/login` | `POST /api/v1/auth/login` | 不涉及 | 一致 | 静态契约断言通过 |
| CS02 | 注册 | `/api/v1/auth/register` | `POST /api/v1/auth/register` | 不涉及 | 一致 | 静态契约断言通过 |
| CS03 | 商品列表/详情 | `/api/v1/products`、`/api/v1/products/{id}` | `GET /api/v1/products`、`GET /api/v1/products/{productId}` | 不涉及 | 一致 | 静态契约断言通过 |
| CS04 | 语义搜索 | `/api/v1/search/semantic` | `POST /api/v1/search/semantic` | 后端调用 `/internal/v1/ai/search/products` | 一致 | 前端-后端、后端-AI 路径均匹配 |
| CS05 | 视觉搜索 | `/api/v1/search/image`、`/api/v1/uploads/search-images` | `POST /api/v1/search/image`、`POST /api/v1/uploads/search-images` | `/internal/v1/ai/search/image` | 一致 | multipart 字段 `image` 匹配 |
| CS06 | 首页推荐 | `/api/v1/recommendations/home` | `GET /api/v1/recommendations/home` | `/internal/v1/ai/users/{userId}/recommendations` | 一致 | 路径匹配 |
| CS07 | AI 对话 | `/api/v1/ai/chat/sessions/{sessionId}/messages` | `POST /api/v1/ai/chat/sessions/{sessionId}/messages` | `/internal/v1/ai/chat/messages` | 一致 | 路径匹配 |
| CS08 | 清除 AI 历史 | `/api/v1/ai/chat/sessions/{sessionId}/history` | `DELETE /api/v1/ai/chat/sessions/{sessionId}/history` | `/internal/v1/ai/chat/history` | 一致 | 路径匹配 |
| CS09 | 创建/支付订单 | `/api/v1/orders`、`/api/v1/orders/{orderId}/pay` | `POST /api/v1/orders`、`POST /api/v1/orders/{orderId}/pay` | 不涉及 | 一致 | 静态契约断言通过 |
| CS10 | 商家商品管理 | `/api/v1/merchant/products/**` | `/api/v1/merchant/products/**` | 商品 AI 索引应调用 `/internal/v1/ai/products/{id}/index` | 部分一致 | 前后端路径一致；后端 ProductService 当前未实际调用 AI 索引服务 |
| CS11 | 管理员 | `/api/v1/admin/users`、`/api/v1/admin/metrics/overview` | 对应后端接口 | 不涉及 | 一致 | 静态契约断言通过 |
| CS12 | 商品图片上传 | `/api/v1/uploads/product-images` | `POST /api/v1/uploads/product-images`，字段 `image` | 不涉及 | 一致 | 本次修复前端字段名，从 `file` 改为 `image` |
| CS13 | Postman/dev script | Postman `ai_base_url=http://localhost:9000` | 后端配置 `http://127.0.0.1:9000` | Python 默认端口 `9000` | 一致 | 已同步 Postman 和 `scripts/dev.sh` 到标准库 HTTP Server/9000 |
| CS14 | 内部 Token | 前端不涉及 | 后端 `/internal/v1/products/**` | AI `/internal/v1/ai/**` | 不一致/未实现 | 后端和 AI Service 均未发现 `X-Internal-Token` 强制校验 |

正式跨服务测试已通过：`ai-service/.venv/bin/python -m pytest -s tests/pytest` 收集 5 个测试并全部通过。

## 8. 接口文档与实际实现差异

| 编号 | 模块 | 文档/期望 | 当前实际实现 | 处理结果 |
|---|---|---|---|---|
| D01 | 前端上传 | 商品图片上传字段应匹配后端 | 前端原为 `file`，后端要求 `image` | 已修复并加测试 |
| D02 | AI Service 技术栈 | README/脚本倾向 FastAPI/uvicorn `app.main` | 实际入口是 `app/api/py_api_server.py` 标准库 HTTP Server | `scripts/dev.sh` 已修正为实际启动命令 |
| D03 | AI Service 端口 | Postman/dev script、后端配置、Python 默认端口应一致 | 当前均为 `9000` | 已修复并由跨服务测试验证 |
| D04 | AI Service 依赖 | `requirements.txt` 应覆盖运行时导入 | 已补充 LangChain/向量搜索相关依赖；聊天核心改为轻量 HTTP 直连以降低启动依赖 | AI pytest 通过，真实向量/视觉能力仍依赖本地模型和 Chroma 环境 |
| D05 | 内部 Token | `/internal/v1/**` 应校验 `X-Internal-Token` | 后端 SecurityConfig 放行，AI Service 未校验 | 后端/跨服务/AI 测试均记录为缺口 |
| D06 | 权限 | 商家、管理员接口应按登录态和角色限制 | 后端 `/api/v1/**` 当前 `permitAll`，前端路由守卫已实现 | 前端路由测试通过；后端接口权限缺口记录 |
| D07 | 上传安全 | 应校验 MIME、扩展名、大小、文件头 | 后端 UploadService 当前直接返回 URL | 后端测试记录当前行为，列为安全问题 |
| D08 | 行为日志 | `event_type` 应限制白名单 | 当前只校验非空 | 后端测试记录当前行为，列为问题 |
| D09 | 商品 AI 索引 | 商品上架/编辑/下架应真实调用 AI Service 索引 | 后端当前只返回 `vector_index_status` 文本 | 记录为暂未完整联调 |
| D10 | 完整订单交易 | 应覆盖真实库存扣减、重复支付、非法支付方式 | 当前完整交易依赖真实 PostgreSQL，单测主要覆盖参数和降级 | 记录为未完整覆盖 |
| D11 | 个人资料 | 用户修改昵称、手机号、头像后应持久化 | `UserService.updateCurrentUser` 仅返回临时对象，`getCurrentUser` 刷新后仍返回固定样例 Alice | 记录为未持久化缺陷 |
| D12 | AI 购物基因 | 应基于用户搜索、浏览、购买或 AI 行为生成画像 | `ProfileView.vue` 中标签、次数和画像文案为前端静态 mock | 记录为静态演示功能 |
| D13 | 愿望清单 | 应支持收藏、取消收藏、跳转商品详情和状态同步 | `ProfileView.vue` 中愿望清单为 `v-for="item in 4"` 静态展示，未发现 wishlist/favorite 后端接口和数据表 | 记录为交互未实现 |
| D14 | AI 对比 | 应支持选择商品、调用 AI 对比、加入购物车/保存报告 | `CompareView.vue` 为固定耳机场景静态页面，按钮无实际 API 或路由逻辑 | 记录为静态演示功能 |

## 9. 安全测试结果

| 安全项 | 测试方式 | 当前结果 | 结论 |
|---|---|---|---|
| 前端路由权限 | Vitest 路由守卫测试 | 未登录访问订单跳登录，普通用户访问管理员跳首页，管理员可进入 | 前端通过 |
| 后端接口鉴权 | MockMvc 当前行为测试、静态检查 | `/api/v1/admin/users`、`/api/v1/merchant/products` 当前匿名可访问 | 后端未实现，需修复 |
| 内部 Token | MockMvc、AI route 测试、静态契约 | `/internal/v1/products/**` 和 `/internal/v1/ai/**` 未校验 `X-Internal-Token` | 未实现，需修复 |
| 上传类型校验 | MockMvc 和服务层测试 | `text/plain` 可被当商品图片接受 | 未实现，需修复 |
| AI HTML/XSS | `AiChatServiceTest` | `<script>`、事件属性、`javascript:` 被过滤 | 后端通过 |
| 行为事件白名单 | MockMvc 当前行为测试 | 任意非空 `event_type` 可接受 | 未实现，需修复 |
| npm 依赖漏洞 | `npm install` 输出 | 5 vulnerabilities：2 moderate、2 high、1 critical | 需要后续 audit 和升级评估 |
| Python 包安装安全 | venv/pip 尝试 | 已使用 `.venv` 安装依赖，未破坏系统 Python；PEP 668 仍保护系统 Python | 通过 |

## 10. 发现的问题

| 问题编号 | 严重程度 | 模块 | 问题描述 | 证据/命令 | 建议 |
|---|---|---|---|---|---|
| BUG-01 | 高 | 后端权限 | `SecurityConfig` 对 `/api/v1/**`、`/internal/v1/**` 当前放行，管理员/商家/内部接口可匿名访问 | `mvn test` 中当前行为测试通过并暴露缺口 | 接入 token 解析、角色授权和内部 Token Filter |
| BUG-02 | 高 | 上传安全 | 后端上传服务不校验 MIME、扩展名、大小和文件头 | `UploadServiceTest`、`ApiControllerContractTest` | 增加文件类型、大小、magic number 校验 |
| BUG-03 | 高 | AI/后端内部接口 | 后端调用 AI Service 未携带 `X-Internal-Token`，AI Service 也未校验 | `tests/pytest/test_contracts.py` 静态检查 | 为 RestClient 增加 Header，AI Service 入口统一校验 |
| FIX-04 | 已修复 | AI Service 环境 | 安装 `python3.12-venv` 后，`.venv`、pip 依赖安装和正式 pytest 已可用 | `python3 -m venv --clear .venv`、`pip install -r requirements.txt`、`pytest -s tests` | 已完成；后续继续使用 `.venv` 内 Python 执行 pytest |
| BUG-05 | 中 | AI Service 配置 | `scripts/dev.sh` 和 Postman 仍使用 FastAPI `app.main`/端口 `8001`，实际服务为 `py_api_server.py`/`9000` | 跨服务静态测试 | 更新启动脚本和 Postman 变量 |
| BUG-06 | 中 | AI Service 依赖 | `requirements.txt` 未列出实际导入的 Chroma、SentenceTransformer、LangChain、LangGraph 等依赖 | 静态检查 | 补齐依赖或拆分轻量测试依赖和运行依赖 |
| BUG-07 | 中 | 行为日志 | `event_type` 没有白名单校验 | MockMvc 当前行为测试 | 改为 enum/custom validator |
| BUG-08 | 中 | 商家/AI 索引联动 | 商品上架/编辑/下架当前未真实调用 AI 索引接口 | 跨服务契约检查 | 在 ProductService 中接入 AI 索引 client 并补集成测试 |
| BUG-09 | 中 | 前端依赖 | npm audit 输出 5 个漏洞 | `npm install` 输出 | 执行 `npm audit` 分析，谨慎升级，避免破坏 Vite/Vue 版本 |
| BUG-10 | 中 | 个人资料 | 修改个人信息保存后刷新会复原，后端未持久化用户资料 | `backend/src/main/java/com/aishop/modules/user/UserService.java` 中 `getCurrentUser()` 固定返回 `sampleUser("Alice")` | 接入 `users`/`user_profiles` 表，更新接口写库，查询接口读库 |
| BUG-11 | 中 | 个人中心 AI 购物基因 | “AI购物基因”标签、检索次数和偏好文案均为静态 mock，未接用户行为或 AI Service | `frontend/src/views/ProfileView.vue` 中 `tags` 和画像文案写死 | 增加用户画像接口，基于行为日志/搜索/推荐记录生成画像；或临时标注为演示数据 |
| BUG-12 | 中 | 愿望清单 | 愿望清单仅静态展示，无法新增、删除、跳转或同步收藏状态 | `ProfileView.vue` 中愿望清单使用 `v-for="item in 4"`；未发现 wishlist/favorite 接口 | 新增收藏表和 API，前端接入收藏/取消收藏/商品详情跳转 |
| BUG-13 | 中 | AI 对比 | AI 对比页面为固定耳机 demo，按钮无实际功能 | `frontend/src/views/CompareView.vue` 中商品、结论、按钮均写死 | 增加商品选择、AI 对比接口、报告保存和加入购物车/下单逻辑 |
| FIX-01 | 已修复 | 前端上传 | `uploadProductImage` multipart 字段从 `file` 改为 `image` | 前端 API 测试和跨服务测试通过 | 已完成 |
| FIX-02 | 已修复 | 前端构建 | `AdminUsersView.vue` 将可选 `phone/created_at` 当必填导致 build 失败 | 第一次 `npm run build` 失败，修复后通过 | 已完成 |
| FIX-03 | 已修复 | ProductGrid | 无空状态、无点击事件测试钩子 | 新增空状态、`select/add` 事件和测试 | 已完成 |

## 11. 暂未覆盖或暂无法测试的内容

| 内容 | 原因 | 后续建议 |
|---|---|---|
| AI Service 默认 capture 模式 | `./.venv/bin/python -m pytest` 在 `/mnt/d` 挂载盘曾触发 pytest capture 临时文件 `FileNotFoundError` | 使用 `./.venv/bin/python -m pytest -s tests`；已通过 |
| 根目录默认系统 Python pytest | 系统 Python 仍不安装 pytest，项目采用 `ai-service/.venv` 提供 pytest | 使用 `ai-service/.venv/bin/python -m pytest -s tests/pytest`；已通过 |
| AI Service 真实 Chroma/Embedding/LLM 调用 | 测试要求避免外部 LLM、向量库和网络服务；当前采用 mock | 后续可用 Testcontainers/本地 fake Chroma 或分层集成测试 |
| 完整跨服务联调 | 未启动 PostgreSQL、后端、AI Service、前端四服务 | 补 Docker Compose 或一键脚本后跑黑盒 API/e2e |
| 数据库迁移真实执行 | 未连接 PostgreSQL 实例 | 在 CI 中用 PostgreSQL 容器执行 migrations |
| 订单完整交易 | 真实库存、支付状态机、重复支付依赖持久化环境 | 引入 H2/Testcontainers 或仓储层测试夹具 |
| 后端真实认证授权 | 当前后端未实现 token 解析和角色授权 | 实现后补充 401/403、安全上下文和越权测试 |
| npm audit 修复 | 自动修复可能引入破坏性升级 | 单独做依赖升级任务并回归前端 build/test |
| 个人中心真实数据闭环 | AI购物基因、愿望清单、个人资料持久化当前未形成完整前后端闭环 | 补用户画像、收藏和用户资料持久化接口后做端到端测试 |
| AI 对比真实业务闭环 | 当前 AI 对比为静态 demo 页面，未接商品选择、AI Service 或报告保存 | 补 AI 对比后端/AI Service 接口与前端交互后做联调测试 |

## 12. 测试结论

本次已完成全项目测试资产补充和主要命令验证：

- 前端：新增 Vitest/jsdom 测试配置和 10 个测试文件，覆盖 API 层、购物、订单、AI 助手、商家、管理员、公共组件和路由权限；`npm run build` 与 `npm run test` 最终通过。
- 后端：复用并验证 8 个后端测试文件，覆盖控制器契约、服务降级、搜索、AI 聊天、订单、管理员、上传和内部摘要；`mvn test` 通过 24 个测试。
- AI Service：将测试改为匹配真实 `py_api_server.py` 的 mock-based pytest；正式 `./.venv/bin/python -m pytest -s tests` 已通过 7 个测试。
- 跨服务：新增静态契约测试，验证前端 API、后端 Controller、后端 AI Client、Python AI Service 路由和上传字段；正式 `ai-service/.venv/bin/python -m pytest -s tests/pytest` 已通过 5 个测试。
- 脚本和数据库：`scripts/dev.sh` 语法检查通过，SQL migration 静态检查通过；同时发现 dev script/Postman AI Service 端口和启动方式已过期。

当前系统前端可构建、后端自动化测试通过，核心接口契约大体一致；但后端/AI 内部鉴权、上传安全、AI Service 运行依赖完整性、个人中心真实数据闭环、AI 对比交互闭环和真实跨服务联调仍是进入更大范围联调前的主要风险。

本次新增或纳入的测试文件：

| 范围 | 文件 |
|---|---|
| 前端 | `frontend/vitest.config.ts`、`frontend/src/test/setup.ts`、`frontend/src/services/api.test.ts`、`frontend/src/components/ProductGrid.test.ts`、`frontend/src/views/ShoppingView.test.ts`、`frontend/src/views/OrdersView.test.ts`、`frontend/src/views/AIChatView.test.ts`、`frontend/src/views/MerchantView.test.ts`、`frontend/src/views/MerchantUploadView.test.ts`、`frontend/src/views/MerchantRestockView.test.ts`、`frontend/src/views/AdminView.test.ts`、`frontend/src/router/index.test.ts` |
| 后端 | `backend/src/test/java/com/aishop/support/TestFixtures.java`、`backend/src/test/java/com/aishop/ApiControllerContractTest.java`、`backend/src/test/java/com/aishop/SearchServiceTest.java`、`backend/src/test/java/com/aishop/AiChatServiceTest.java`、`backend/src/test/java/com/aishop/OrderServiceTest.java`、`backend/src/test/java/com/aishop/AdminServiceTest.java`、`backend/src/test/java/com/aishop/UploadServiceTest.java`、`backend/src/test/java/com/aishop/InternalProductServiceTest.java` |
| AI Service | `ai-service/tests/conftest.py`、`ai-service/tests/test_health.py` |
| 跨服务 | `tests/pytest/test_contracts.py` |
