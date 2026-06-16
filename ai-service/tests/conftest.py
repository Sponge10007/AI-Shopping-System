import importlib.util
import sys
import types
from pathlib import Path

import pytest


@pytest.fixture()
def api_module(monkeypatch):
    class FakeLabelDBClass:
        DEFAULT_SEARCH_LIMIT = 50

    core_pkg = types.ModuleType('core')
    image_ai_module = types.ModuleType('core.image_ai')
    label_db_module = types.ModuleType('core.labelDB')
    llmchat_module = types.ModuleType('core.llmchat')

    image_ai_module.ImageAI = object
    label_db_module.LabelDB = FakeLabelDBClass
    llmchat_module.init = lambda **_kwargs: None

    monkeypatch.setitem(sys.modules, 'core', core_pkg)
    monkeypatch.setitem(sys.modules, 'core.image_ai', image_ai_module)
    monkeypatch.setitem(sys.modules, 'core.labelDB', label_db_module)
    monkeypatch.setitem(sys.modules, 'core.llmchat', llmchat_module)

    module_name = 'py_api_server_under_test'
    sys.modules.pop(module_name, None)
    module_path = Path(__file__).resolve().parents[1] / 'app' / 'api' / 'py_api_server.py'
    spec = importlib.util.spec_from_file_location(module_name, module_path)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    spec.loader.exec_module(module)
    return module
