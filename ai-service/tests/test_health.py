import io
import json

import pytest


class FakeLabelDB:
    def __init__(self):
        self.added = []
        self.deleted = []
        self.search_calls = []
        self.recommendation_calls = []

    def prod_add_product(self, product_id, description):
        self.added.append((product_id, description))

    def prod_delete_product(self, product_id):
        self.deleted.append(product_id)

    def prod_search(self, user_id, query, distance_threshold, limit):
        self.search_calls.append({
            'user_id': user_id,
            'query': query,
            'distance_threshold': distance_threshold,
            'limit': limit,
        })
        return ['10001', '10002']

    def user_search(self, user_id, maxnum):
        self.recommendation_calls.append((user_id, maxnum))
        return ['10001']


class FakeImageAI:
    def __init__(self):
        self.calls = []

    def image_search(self, user_id, image_path_or_url, limit, distance_threshold):
        self.calls.append((user_id, image_path_or_url, limit, distance_threshold))
        return {'detected_object': 'headphones', 'product_ids': ['10001']}


class FakeChat:
    def __init__(self):
        self.deleted = []

    def chat(self, content, user_id, session_id):
        return {
            'answer': f'answer for {content}',
            'image_list': [],
            'link_list': [],
            'raw_answer': 'raw',
        }

    def delete_history(self, user_id, session_id):
        self.deleted.append((user_id, session_id))


def handler(api_module):
    return object.__new__(api_module.PythonApiHandler)


def call_do_post(api_module, path, body):
    payload = json.dumps(body, ensure_ascii=False).encode('utf-8')
    instance = handler(api_module)
    instance.path = path
    instance.headers = {'Content-Length': str(len(payload))}
    instance.rfile = io.BytesIO(payload)
    instance.wfile = io.BytesIO()
    instance.status = None
    instance.response_headers = []
    instance.send_response = lambda status: setattr(instance, 'status', status)
    instance.send_header = lambda key, value: instance.response_headers.append((key, value))
    instance.end_headers = lambda: None

    api_module.PythonApiHandler.do_POST(instance)
    instance.wfile.seek(0)
    return instance.status, json.loads(instance.wfile.read().decode('utf-8'))


def test_health_route_returns_standard_envelope(api_module):
    instance = handler(api_module)
    result = {'ok': True, 'service': 'python-api'}

    api_module.json_response(instance := type('Dummy', (), {
        'status': None,
        'headers': [],
        'wfile': io.BytesIO(),
        'send_response': lambda self, status: setattr(self, 'status', status),
        'send_header': lambda self, key, value: self.headers.append((key, value)),
        'end_headers': lambda self: None,
    })(), 200, result)

    instance.wfile.seek(0)
    assert instance.status == 200
    assert json.loads(instance.wfile.read().decode('utf-8')) == result


def test_product_index_add_update_and_delete_routes(api_module, monkeypatch):
    label_db = FakeLabelDB()
    monkeypatch.setattr(api_module, 'get_label_db', lambda: label_db)
    instance = handler(api_module)

    add_result = instance.route_post('/internal/v1/ai/products/10001/index', {'description': '蓝牙降噪耳机'})
    delete_result = instance.route_delete('/internal/v1/ai/products/10001/index', {}, {})

    assert add_result == {'message': '商品向量写入任务已提交'}
    assert delete_result == {'message': '商品向量已删除'}
    assert label_db.added == [('10001', '蓝牙降噪耳机')]
    assert label_db.deleted == ['10001']


def test_semantic_search_and_user_recommendation_routes(api_module, monkeypatch):
    label_db = FakeLabelDB()
    monkeypatch.setattr(api_module, 'get_label_db', lambda: label_db)
    instance = handler(api_module)

    search_result = instance.route_post('/internal/v1/ai/search/products', {
        'user_id': 'u10001',
        'query': '通勤耳机',
        'distance_threshold': 0.8,
        'limit': 5,
    })
    recommendation_result = instance.route_get('/internal/v1/ai/users/u10001/recommendations', {'maxnum': '3'})

    assert search_result == ['10001', '10002']
    assert label_db.search_calls == [{
        'user_id': 'u10001',
        'query': '通勤耳机',
        'distance_threshold': 0.8,
        'limit': 5,
    }]
    assert recommendation_result == ['10001']
    assert label_db.recommendation_calls == [('u10001', 3)]


def test_semantic_search_requires_query(api_module):
    with pytest.raises(ValueError, match='缺少必填参数: query'):
        handler(api_module).route_post('/internal/v1/ai/search/products', {'user_id': 'u10001'})


def test_image_search_chat_and_history_routes_are_mockable(api_module, monkeypatch):
    image_ai = FakeImageAI()
    chat = FakeChat()
    monkeypatch.setattr(api_module, 'get_image_ai_app', lambda: image_ai)
    monkeypatch.setattr(api_module, 'get_chat_app', lambda: chat)
    instance = handler(api_module)

    image_result = instance.route_post('/internal/v1/ai/search/image', {
        'user_id': 'u10001',
        'image_path_or_url': 'search-upload://headphones.png',
        'limit': 2,
        'distance_threshold': 0.7,
    })
    chat_result = instance.route_post('/internal/v1/ai/chat/messages', {
        'user_id': 'u10001',
        'session_id': 's10001',
        'content': '推荐耳机',
    })
    clear_result = instance.route_delete('/internal/v1/ai/chat/history', {}, {
        'user_id': 'u10001',
        'session_id': 's10001',
    })

    assert image_result == {'detected_object': 'headphones', 'product_ids': ['10001']}
    assert image_ai.calls == [('u10001', 'search-upload://headphones.png', 2, 0.7)]
    assert chat_result['answer'] == 'answer for 推荐耳机'
    assert clear_result == {'message': '聊天历史已删除'}
    assert chat.deleted == [('u10001', 's10001')]


def test_do_post_converts_ai_function_exception_to_unified_json_error(api_module, monkeypatch):
    class BrokenLabelDB:
        def prod_search(self, **_kwargs):
            raise RuntimeError('vector db down')

    monkeypatch.setattr(api_module, 'get_label_db', lambda: BrokenLabelDB())

    status, body = call_do_post(api_module, '/internal/v1/ai/search/products', {'query': '耳机'})

    assert status == 500
    assert body == {'ok': False, 'error': 'vector db down'}


def test_internal_routes_currently_do_not_enforce_token(api_module, monkeypatch):
    label_db = FakeLabelDB()
    monkeypatch.setattr(api_module, 'get_label_db', lambda: label_db)

    status, body = call_do_post(api_module, '/internal/v1/ai/search/products', {'query': '耳机'})

    assert status == 200
    assert body['ok'] is True
    assert body['data'] == ['10001', '10002']
