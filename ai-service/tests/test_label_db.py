import importlib.util
import sys
import types
from pathlib import Path


class FakeEmbedding:
    def __truediv__(self, _value):
        return self

    def tolist(self):
        return [0.1, 0.2, 0.3]


class FakeModel:
    def encode(self, _values):
        return [FakeEmbedding()]


class FakeCollection:
    def __init__(self):
        self.upsert_calls = []

    def upsert(self, **kwargs):
        self.upsert_calls.append(kwargs)


def load_label_db_module(monkeypatch):
    chromadb = types.ModuleType("chromadb")
    numpy = types.ModuleType("numpy")
    sentence_transformers = types.ModuleType("sentence_transformers")

    numpy.linalg = types.SimpleNamespace(norm=lambda _embedding: 1)
    sentence_transformers.SentenceTransformer = object

    monkeypatch.setitem(sys.modules, "chromadb", chromadb)
    monkeypatch.setitem(sys.modules, "numpy", numpy)
    monkeypatch.setitem(sys.modules, "sentence_transformers", sentence_transformers)

    module_path = Path(__file__).resolve().parents[1] / "app" / "core" / "labelDB.py"
    spec = importlib.util.spec_from_file_location("label_db_under_test", module_path)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    spec.loader.exec_module(module)
    return module


def test_product_index_uses_upsert_so_updates_replace_existing_vector(monkeypatch):
    module = load_label_db_module(monkeypatch)
    label_db = object.__new__(module.LabelDB)
    label_db.model = FakeModel()
    label_db.prod_collection = FakeCollection()

    label_db._do_prod_add_product("p10001", "更新后的商品描述")

    assert label_db.prod_collection.upsert_calls == [{
        "embeddings": [[0.1, 0.2, 0.3]],
        "documents": ["更新后的商品描述"],
        "ids": ["p10001"],
    }]
