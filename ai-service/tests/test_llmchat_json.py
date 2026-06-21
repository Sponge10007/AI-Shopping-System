import importlib.util
import http.client
import json
from pathlib import Path


def load_llmchat_module():
    module_path = Path(__file__).resolve().parents[1] / "app" / "core" / "llmchat.py"
    spec = importlib.util.spec_from_file_location("llmchat_json_under_test", module_path)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    spec.loader.exec_module(module)
    return module


def parser():
    module = load_llmchat_module()
    return object.__new__(module.llmChat)


def test_try_parse_json_accepts_markdown_code_block():
    result = parser()._try_parse_json('```json\n{"winner_product_id":"10001"}\n```')

    assert result == {"winner_product_id": "10001"}


def test_try_parse_json_extracts_object_after_reasoning_and_explanation():
    result = parser()._try_parse_json(
        '<think>internal reasoning</think>\n'
        '下面是结果：\n{"winner_product_id":"10002","summary":"更合适"}\n完成'
    )

    assert result == {
        "winner_product_id": "10002",
        "summary": "更合适",
    }


def completion_client(module):
    client = object.__new__(module.llmChat)
    client.api_key = "test-key"
    client.model = "test-model"
    client.base_url = "https://example.test"
    client.request_timeout = 30.0
    client.max_retries = 1
    client.retry_delay = 0
    client.disable_thinking = True
    return client


def test_chat_request_disables_thinking_and_streaming(monkeypatch):
    module = load_llmchat_module()
    client = completion_client(module)
    captured_body = {}

    class FakeResponse:
        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return False

        def read(self):
            return b'{"choices":[{"message":{"content":"ok"}}]}'

    def fake_urlopen(request, timeout):
        captured_body.update(json.loads(request.data.decode("utf-8")))
        assert timeout == 30.0
        return FakeResponse()

    monkeypatch.setattr(module.url_request, "urlopen", fake_urlopen)

    result = client._call_chat_completions(
        [{"role": "user", "content": "hello"}],
    )

    assert result == "ok"
    assert captured_body["stream"] is False
    assert captured_body["thinking"] == {"type": "disabled"}


def test_incomplete_chunked_response_retries_once(monkeypatch):
    module = load_llmchat_module()
    client = completion_client(module)
    call_count = 0

    class FakeResponse:
        def __init__(self, should_fail):
            self.should_fail = should_fail

        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return False

        def read(self):
            if self.should_fail:
                raise http.client.IncompleteRead(b"")
            return b'{"choices":[{"message":{"content":"retry ok"}}]}'

    def fake_urlopen(_request, timeout):
        nonlocal call_count
        assert timeout == 30.0
        call_count += 1
        return FakeResponse(should_fail=call_count == 1)

    monkeypatch.setattr(module.url_request, "urlopen", fake_urlopen)

    result = client._call_chat_completions(
        [{"role": "user", "content": "hello"}],
    )

    assert result == "retry ok"
    assert call_count == 2


def test_complete_json_from_incomplete_read_is_recovered(monkeypatch):
    module = load_llmchat_module()
    client = completion_client(module)
    partial = b'{"choices":[{"message":{"content":"partial ok"}}]}'

    class FakeResponse:
        def __enter__(self):
            return self

        def __exit__(self, *_args):
            return False

        def read(self):
            raise http.client.IncompleteRead(partial)

    monkeypatch.setattr(
        module.url_request,
        "urlopen",
        lambda _request, timeout: FakeResponse(),
    )

    result = client._call_chat_completions(
        [{"role": "user", "content": "hello"}],
    )

    assert result == "partial ok"


def test_stored_assistant_json_is_normalized_to_visible_answer():
    module = load_llmchat_module()
    client = parser()

    result = client._normalize_stored_message(
        json.dumps({
            "role": "assistant",
            "content": json.dumps({
                "answer": "只保留正文",
                "image_list": [],
                "link_list": [],
            }, ensure_ascii=False),
        }, ensure_ascii=False),
    )

    assert result == {"role": "assistant", "content": "只保留正文"}


def test_grounded_chat_prompt_only_allows_database_candidates():
    module = load_llmchat_module()
    client = parser()
    client.system_prompt = "你是购物助手。"
    client._search_products_for_ai = lambda **_kwargs: [
        "商品名称：数据库耳机\n价格：299 元\n详情页：/api/v1/products/p10001"
    ]

    messages = client._build_grounded_chat_messages(
        history=[{"role": "user", "content": "预算300元"}],
        user_message={"role": "user", "content": "推荐耳机"},
        streaming=True,
    )

    system_prompt = messages[0]["content"]
    assert "数据库耳机" in system_prompt
    assert "只能使用<catalog> 中出现的数据库商品" in system_prompt
    assert "必须原样附上" in system_prompt
    assert messages[-1] == {"role": "user", "content": "推荐耳机"}


def test_grounded_chat_without_candidates_forbids_specific_products():
    module = load_llmchat_module()
    client = parser()
    client.system_prompt = "你是购物助手。"
    client._search_products_for_ai = lambda **_kwargs: []

    messages = client._build_grounded_chat_messages(
        history=[],
        user_message={"role": "user", "content": "推荐耳机"},
        streaming=True,
    )

    assert "不得推荐任何具体品牌、型号或商品" in messages[0]["content"]


def test_parse_model_answer_extracts_relative_product_link():
    module = load_llmchat_module()
    client = parser()

    result = client._parse_model_answer(
        "推荐数据库耳机，详情：/api/v1/products/p10001"
    )

    assert result["link_list"] == ["/api/v1/products/p10001"]
    assert 'href="/api/v1/products/p10001"' in result["answer"]
