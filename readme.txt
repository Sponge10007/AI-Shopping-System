注：里面的数据库只包含向量数据库，不包含普通的



labelDB.py:

prod_add_product(str product_id, str description)
功能: 非阻塞异步将商品描述向量化并写入数据库
product_id: 商品唯一数字ID
description: （关键）商品的精炼描述文本，需要包含商品名，用处，特点。建议由商家上架时给出
返回值: 无 (None)
用法：商品上架时直接使用。

prod_delete_product(str product_id)
功能: 从向量数据库中同步移除指定商品索引
product_id: 需要删除的商品数字ID
返回值: 无 (None)
用法：商品删除时直接使用。

prod_search(str user_id ,str query,int distance_threshold=0.9，int limit=50)
功能: 语义搜索相似度大于similarity的商品
query: 用户的自然语言搜索词或文本
user_id:用户的id
distance_threshold：0~4，越小搜索越相似，越严格
limit:搜索返回的最大商品数
返回值: 匹配的商品ID列表 (List[int])
用法：搜索时先调用此函数返回id，然后根据id再去数据库中检索

user_search(self, user_id: str, maxnum: int = 5)
功能：搜索根据用户标签推荐的商品
user_id:用户的id
maxnum:最后会得出maxnum*10个商品
返回值：商品的id列表


llmChat.py:

chat(str content, str user_id，str session_id)
功能: 同步调用大模型进行对话。系统会自动结合该用户的历史上下文发送请求，并在内部完成解析，将大模型返回的图片路径和链接自动拼接成前端可直接渲染的 HTML 标签放入回复文本中。
content: 用户的当前输入内容/提问。
user_id: 用户的唯一字符串标识。
session_id:唯一对话标识符，用于在数据库中隔离并读取不同对话的历史记忆。
返回值: 包含解析后对话结果的字典 (dict)。其中 `answer` 字段为包含完整 HTML 标签（如 <img> 和 <a>）的最终回复文本；同时还包含原始的 `image_list` 和 `link_list` 数据。
用法：在用户发送聊天消息时调用，并将返回字典中的 `answer` 字段内容直接交由前端（如 v-html）进行渲染。
输出样例 (返回值):
"这里是为你找到的商品图片 <img src=\"/images/prod_123.jpg\" alt=\"图片\" style=\"max-width: 100%; height: auto;\" />，你可以点击这里 <a href=\"https://example.com/item/123\" target=\"_blank\" style=\"color: blue; text-decoration: underline;\">相关链接</a> 查看详情，或者参考此评测 <a href=\"https://example.com/review\" target=\"_blank\" style=\"color: blue; text-decoration: underline;\">相关链接</a>。"

delete_history(str user_id,str session_id)
功能: 从数据库中同步清除指定对话的历史聊天上下文记忆。
user_id: 需要被清除记忆的用户唯一标识。
session_id:删除的对话标识符
返回值: 无 (None)
建议用法：当用户点击“新建对话”、“清除记忆”按钮，或需要重置大模型上下文时调用。


需要的函数search(str id):根据商品id得到所有相关信息。返回值是结构化的string，如：商品名xx,价格xx,网页链接：xxx
