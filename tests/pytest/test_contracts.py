import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(relative_path: str) -> str:
    return (ROOT / relative_path).read_text(encoding='utf-8')


def test_frontend_api_paths_match_backend_controller_paths():
    frontend_api = read('frontend/src/services/api.ts')
    backend_controllers = '\n'.join(path.read_text(encoding='utf-8') for path in (ROOT / 'backend/src/main/java/com/aishop/modules').glob('*/*Controller.java'))

    checks = [
        ('login', "/auth/login", 'RequestMapping("/api/v1/auth")', 'PostMapping("/login")'),
        ('register', "/auth/register", 'RequestMapping("/api/v1/auth")', 'PostMapping("/register")'),
        ('product list', "`/products", 'GetMapping("/api/v1/products")', None),
        ('product detail', "`/products/${productId}`", 'GetMapping("/api/v1/products/{productId}")', None),
        ('semantic search', "/search/semantic", 'RequestMapping("/api/v1/search")', 'PostMapping("/semantic")'),
        ('home recommendation', "/recommendations/home", 'RequestMapping("/api/v1/recommendations")', 'GetMapping("/home")'),
        ('chat session', "/ai/chat/sessions", 'RequestMapping("/api/v1/ai/chat")', 'PostMapping("/sessions")'),
        ('chat message', "/ai/chat/sessions/${sessionId}/messages", 'RequestMapping("/api/v1/ai/chat")', 'PostMapping("/sessions/{sessionId}/messages")'),
        ('create order', "/orders", 'RequestMapping("/api/v1/orders")', 'PostMapping'),
        ('pay order', "/orders/${orderId}/pay", 'RequestMapping("/api/v1/orders")', 'PostMapping("/{orderId}/pay")'),
        ('merchant products', "/merchant/products", 'GetMapping("/api/v1/merchant/products")', 'PostMapping("/api/v1/merchant/products")'),
        ('admin users', "/admin/users", 'RequestMapping("/api/v1/admin")', 'GetMapping("/users")'),
        ('admin metrics', "/admin/metrics/overview", 'RequestMapping("/api/v1/admin")', 'GetMapping("/metrics/overview")'),
        ('product image upload', "/uploads/product-images", 'RequestMapping("/api/v1/uploads")', 'PostMapping("/product-images")'),
        ('search image upload', "/uploads/search-images", 'RequestMapping("/api/v1/uploads")', 'PostMapping("/search-images")'),
    ]

    for name, frontend_fragment, backend_fragment_a, backend_fragment_b in checks:
        assert frontend_fragment in frontend_api, f'missing frontend API path for {name}'
        assert backend_fragment_a in backend_controllers, f'missing backend controller path for {name}'
        if backend_fragment_b is not None:
            assert backend_fragment_b in backend_controllers, f'missing backend method mapping for {name}'


def test_upload_multipart_field_names_match_backend_request_parts():
    frontend_api = read('frontend/src/services/api.ts')
    upload_controller = read('backend/src/main/java/com/aishop/modules/upload/UploadController.java')
    search_controller = read('backend/src/main/java/com/aishop/modules/search/SearchController.java')

    assert "fd.append('image', file)" in frontend_api
    assert '@RequestPart("image") MultipartFile image' in upload_controller
    assert '@RequestPart("image") MultipartFile image' in search_controller


def test_backend_ai_client_paths_match_python_ai_service_routes():
    ai_client = read('backend/src/main/java/com/aishop/infrastructure/ai/AiServiceClient.java')
    py_api = read('ai-service/app/api/py_api_server.py')

    shared_routes = [
        '/health',
        '/internal/v1/ai/search/products',
        '/internal/v1/ai/search/image',
        '/internal/v1/ai/chat/messages',
        '/internal/v1/ai/chat/history',
    ]
    for route in shared_routes:
        assert route in ai_client
        assert route in py_api

    assert '/internal/v1/ai/users/{userId}/recommendations' in ai_client
    assert 'user_prefix = "/internal/v1/ai/users/"' in py_api
    assert 'user_suffix = "/recommendations"' in py_api


def test_postman_and_dev_script_ai_port_match_current_service_defaults():
    collection = json.loads(read('tests/postman/ai-shopping-system.postman_collection.json'))
    variables = {item['key']: item['value'] for item in collection['variable']}
    app_config = read('backend/src/main/resources/application.yml')
    py_api = read('ai-service/app/api/py_api_server.py')
    dev_script = read('scripts/dev.sh')

    assert variables['ai_base_url'] == 'http://localhost:9000'
    assert 'base-url: http://127.0.0.1:9000' in app_config
    assert 'PORT = int(os.getenv("PY_API_PORT", "9000"))' in py_api
    assert 'cd ai-service/app && ../.venv/bin/python -m api.py_api_server' in dev_script


def test_internal_token_gap_is_documented_by_static_contract_check():
    security_config = read('backend/src/main/java/com/aishop/common/security/SecurityConfig.java')
    ai_client = read('backend/src/main/java/com/aishop/infrastructure/ai/AiServiceClient.java')
    py_api = read('ai-service/app/api/py_api_server.py')

    assert 'permitAll()' in security_config
    assert 'X-Internal-Token' not in ai_client
    assert 'X-Internal-Token' not in py_api
