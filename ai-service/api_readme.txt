

# Python AI 服务接口说明

Python 服务启动命令：

```bash
python -m api.py_api_server
```

默认服务地址：

```text
http://127.0.0.1:9000
```

统一响应格式：

```json
{
  "ok": true,
  "data": {}
}
```

除 `GET /health` 外，所有 `/internal/v1/ai/**` 请求必须携带：

```http
X-Internal-Token: <AI_INTERNAL_TOKEN 环境变量>
```

错误响应格式：

```json
{
  "ok": false,
  "error": "错误信息"
}
```

## 1. 健康检查

```http
GET /health
```

响应：

```json
{
  "ok": true,
  "service": "python-api"
}
```

## 2. 商品向量索引新增或更新

```http
POST /internal/v1/ai/products/{product_id}/index
Content-Type: application/json
```

请求：

```json
{
  "description": "蓝牙降噪耳机，黑色头戴式，适合通勤、学习、办公，支持主动降噪和长续航"
}
```

响应：

```json
{
  "ok": true,
  "data": {
    "message": "商品向量写入任务已提交"
  }
}
```

说明：

- `product_id` 放在 URL 路径中。
- `description` 是商品精炼描述，建议包含商品名、用途、外观、特点。
- 该接口内部调用 `core.labelDB.LabelDB.prod_add_product(str product_id, str description)`。

## 3. 删除商品向量索引

```http
DELETE /internal/v1/ai/products/{product_id}/index
```

响应：

```json
{
  "ok": true,
  "data": {
    "message": "商品向量已删除"
  }
}
```

说明：

- 该接口内部调用 `core.labelDB.LabelDB.prod_delete_product(str product_id)`。

## 4. 文本语义搜索

```http
POST /internal/v1/ai/search/products
Content-Type: application/json
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
  "ok": true,
  "data": [
    "10001",
    "10008",
    "10021"
  ]
}
```

说明：

- `user_id` 可选，默认 `"-1"`。传 `"-1"` 表示 AI 调用，不写入用户标签。
- `distance_threshold` 是 ChromaDB 距离阈值，越小越严格。
- `limit` 是向量库召回数量上限。
- 该接口只返回商品 ID。Java 侧可再根据商品 ID 查询普通商品表。
- 该接口内部调用 `core.labelDB.LabelDB.prod_search(...)`。

## 5. 视觉搜索

```http
POST /internal/v1/ai/search/image
Content-Type: application/json
```

请求：

```json
{
  "user_id": "u10001",
  "image_path_or_url": "https://example.com/uploads/query.jpg",
  "limit": 20,
  "distance_threshold": 0.9
}
```

也支持：

```json
{
  "user_id": "u10001",
  "image_url": "https://example.com/uploads/query.jpg",
  "limit": 20
}
```

Python 当前实际响应：

```json
{
  "ok": true,
  "data": {
    "keywords": [
      "耳机",
      "蓝牙",
      "黑色",
      "头戴式",
      "降噪",
      "通勤"
    ],
    "query": "耳机 蓝牙 黑色 头戴式 降噪 通勤",
    "product_ids": [
      "10001",
      "10008",
      "10021"
    ]
  }
}
```

Java 侧可根据 `product_ids` 补齐商品详情后，对外组装为类似格式：

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

说明：

- Python 侧先调用支持识图的 AI，将图片转为 10 个以内关键词。
- 关键词主要包含：名字、外观、用途、特征。
- 然后将关键词拼接为 `query`，调用 `core.labelDB.LabelDB.prod_search(...)`。
- `image_path_or_url` 可以是图片 URL、本地绝对路径或本地相对路径。
- 若使用本地路径，图片文件必须存在于 Python 服务所在机器。
- 视觉模型配置：

```bash
set IMAGE_AI_API_KEY=你的视觉模型Key
set IMAGE_AI_MODEL=支持识图的模型名
set IMAGE_AI_BASE_URL=模型base_url
```

如果未设置 `IMAGE_AI_*`，默认复用：

```bash
DEEPSEEK_API_KEY
DEEPSEEK_MODEL
DEEPSEEK_BASE_URL
```

## 6. 用户画像推荐

```http
GET /internal/v1/ai/users/{user_id}/recommendations?maxnum=5
```

响应：

```json
{
  "ok": true,
  "data": [
    "10001",
    "10008",
    "10021"
  ]
}
```

说明：

- `maxnum` 可选，默认 `5`。
- 该接口根据用户历史标签推荐商品 ID。
- 该接口内部调用 `core.labelDB.LabelDB.user_search(user_id: str, maxnum: int)`。

## 7. AI 助手对话

```http
POST /internal/v1/ai/chat/messages
Content-Type: application/json
```

请求：

```json
{
  "content": "帮我找一款适合通勤的降噪耳机",
  "user_id": "u10001",
  "session_id": "s202405310001"
}
```

响应：

```json
{
  "ok": true,
  "data": {
    "answer": "这里是为你找到的商品 <a href=\"https://example.com/products/10001\" target=\"_blank\" style=\"color: blue; text-decoration: underline;\">相关链接</a>",
    "image_list": [
      "https://example.com/products/10001/main.jpg"
    ],
    "link_list": [
      "https://example.com/products/10001"
    ],
    "raw_answer": "{\"answer\":\"这里是为你找到的商品\",\"image_list\":[\"https://example.com/products/10001/main.jpg\"],\"link_list\":[\"https://example.com/products/10001\"]}"
  }
}
```

说明：

- `user_id + session_id` 共同隔离聊天记忆。
- Python 侧会把模型返回的图片和链接拼接成前端可直接渲染的 HTML。
- 该接口内部调用 `core.llmchat.llmChat.chat(...)`。

## 8. 清除对话历史

```http
DELETE /internal/v1/ai/chat/history
Content-Type: application/json
```

请求：

```json
{
  "user_id": "u10001",
  "session_id": "s202405310001"
}
```

也支持 query 参数：

```http
DELETE /internal/v1/ai/chat/history?user_id=u10001&session_id=s202405310001
```

响应：

```json
{
  "ok": true,
  "data": {
    "message": "聊天历史已删除"
  }
}
```

说明：

- 只删除指定 `user_id + session_id` 的聊天历史。
- 该接口内部调用 `core.llmchat.llmChat.delete_history(str user_id, str session_id)`。

## 9. Python 调 Java 商品详情接口

AI 聊天工具需要根据商品 ID 查询完整商品信息。Python 会调用 Java 侧接口：

```bash
set JAVA_PRODUCT_SEARCH_URL=http://127.0.0.1:8080/product/searchById
```

Python 请求 Java：

```json
{
  "id": "10001"
}
```

Java 可以返回纯文本：

```json
"商品名：蓝牙降噪耳机，价格：299.00，网页链接：https://example.com/products/10001"
```

也可以返回 JSON：

```json
{
  "data": "商品名：蓝牙降噪耳机，价格：299.00，网页链接：https://example.com/products/10001"
}
```
