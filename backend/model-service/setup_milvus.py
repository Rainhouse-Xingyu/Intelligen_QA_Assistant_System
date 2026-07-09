"""Ensure the Milvus collection for standard-question vectors exists."""

import os
import time
from typing import Dict, Tuple

from pymilvus import (
    Collection,
    CollectionSchema,
    DataType,
    FieldSchema,
    connections,
    utility,
)


COLLECTION_NAME = os.getenv("MILVUS_COLLECTION_NAME", "knowledge_question_vector")
MILVUS_HOST = os.getenv("MILVUS_HOST", "localhost")
MILVUS_PORT = os.getenv("MILVUS_PORT", "19530")
VECTOR_DIM = int(os.getenv("MILVUS_VECTOR_DIM", "768"))
RESET_ENV = "RESET_MILVUS_COLLECTION"

EXPECTED_FIELDS: Tuple[Tuple[str, DataType, Dict[str, object], bool], ...] = (
    ("id", DataType.INT64, {}, True),
    ("embedding", DataType.FLOAT_VECTOR, {"dim": VECTOR_DIM}, False),
    ("module_type", DataType.VARCHAR, {"max_length": 50}, False),
    ("question", DataType.VARCHAR, {"max_length": 1000}, False),
    ("knowledge_id", DataType.INT64, {}, False),
    ("category_l1_id", DataType.INT64, {}, False),
    ("category_l2_id", DataType.INT64, {}, False),
    ("category_l3_id", DataType.INT64, {}, False),
    ("category_path", DataType.VARCHAR, {"max_length": 300}, False),
    ("status", DataType.INT64, {}, False),
)

EXPECTED_INDEXES: Dict[str, Dict[str, object]] = {
    "embedding": {"index_type": "AUTOINDEX", "metric_type": "COSINE"},
    "question": {"index_type": "AUTOINDEX"},
    "knowledge_id": {"index_type": "AUTOINDEX"},
    "category_l1_id": {"index_type": "AUTOINDEX"},
    "category_l2_id": {"index_type": "AUTOINDEX"},
    "category_l3_id": {"index_type": "AUTOINDEX"},
    "status": {"index_type": "AUTOINDEX"},
}


def connect_with_retry() -> None:
    last_error: Exception | None = None
    for attempt in range(1, 31):
        try:
            connections.connect("default", host=MILVUS_HOST, port=MILVUS_PORT)
            return
        except Exception as exc:  # pragma: no cover - startup guard
            last_error = exc
            print(f"[Milvus] waiting for {MILVUS_HOST}:{MILVUS_PORT}, attempt {attempt}/30: {exc}")
            time.sleep(2)
    raise RuntimeError(f"Milvus is not ready at {MILVUS_HOST}:{MILVUS_PORT}") from last_error


def normalize_params(params: Dict[str, object]) -> Dict[str, str]:
    normalized: Dict[str, str] = {}
    for key, value in params.items():
        if isinstance(value, dict):
            for nested_key, nested_value in value.items():
                normalized[nested_key] = str(nested_value).lower()
        else:
            normalized[key] = str(value).lower()
    return normalized


def existing_field_signature(collection: Collection) -> Tuple[Tuple[str, DataType, Dict[str, object], bool], ...]:
    signature = []
    for field in collection.schema.fields:
        params = dict(field.params)
        if "dim" in params:
            params["dim"] = int(params["dim"])
        if "max_length" in params:
            params["max_length"] = int(params["max_length"])
        signature.append((field.name, field.dtype, params, field.is_primary))
    return tuple(signature)


def existing_indexes(collection: Collection) -> Dict[str, Dict[str, str]]:
    indexes: Dict[str, Dict[str, str]] = {}
    for index in collection.indexes:
        indexes[index.field_name] = normalize_params(index.params)
    return indexes


def is_expected_schema(collection: Collection) -> bool:
    if existing_field_signature(collection) != EXPECTED_FIELDS:
        return False
    indexes = existing_indexes(collection)
    for field_name, expected_params in EXPECTED_INDEXES.items():
        existing = indexes.get(field_name)
        if not existing:
            return False
        expected = normalize_params(expected_params)
        for key, value in expected.items():
            if existing.get(key) != value:
                return False
    return True


def create_collection() -> Collection:
    fields = [
        FieldSchema(name="id", dtype=DataType.INT64, is_primary=True, auto_id=False),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=VECTOR_DIM),
        FieldSchema(name="module_type", dtype=DataType.VARCHAR, max_length=50),
        FieldSchema(name="question", dtype=DataType.VARCHAR, max_length=1000),
        FieldSchema(name="knowledge_id", dtype=DataType.INT64),
        FieldSchema(name="category_l1_id", dtype=DataType.INT64),
        FieldSchema(name="category_l2_id", dtype=DataType.INT64),
        FieldSchema(name="category_l3_id", dtype=DataType.INT64),
        FieldSchema(name="category_path", dtype=DataType.VARCHAR, max_length=300),
        FieldSchema(name="status", dtype=DataType.INT64),
    ]
    schema = CollectionSchema(
        fields=fields,
        description="QA standard-question vector collection",
        enable_dynamic_field=False,
    )
    collection = Collection(
        name=COLLECTION_NAME,
        schema=schema,
        consistency_level="Bounded",
        shards_num=1,
    )
    for field_name, index_params in EXPECTED_INDEXES.items():
        collection.create_index(field_name, index_params, index_name=field_name)
    collection.load()
    return collection


def should_force_reset() -> bool:
    return os.getenv(RESET_ENV, "").strip().lower() in {"1", "true", "yes", "y"}


def recreate_collection(reason: str) -> Collection:
    print(f"[Milvus] dropping collection '{COLLECTION_NAME}' ({reason}).")
    utility.drop_collection(COLLECTION_NAME)
    return create_collection()


def main() -> None:
    connect_with_retry()

    if utility.has_collection(COLLECTION_NAME):
        collection = Collection(COLLECTION_NAME)
        if is_expected_schema(collection):
            collection.load()
            print(f"[Milvus] collection '{COLLECTION_NAME}' is ready.")
            return

        row_count = collection.num_entities
        if row_count == 0:
            recreate_collection("schema mismatch and row count is 0")
            print(f"[Milvus] collection '{COLLECTION_NAME}' recreated.")
            return
        if should_force_reset():
            recreate_collection(f"schema mismatch and {RESET_ENV}=true")
            print(f"[Milvus] collection '{COLLECTION_NAME}' recreated.")
            return
        raise SystemExit(
            f"Collection '{COLLECTION_NAME}' schema does not match and contains {row_count} rows. "
            f"Set {RESET_ENV}=true to recreate it."
        )

    create_collection()
    print(f"[Milvus] collection '{COLLECTION_NAME}' created.")


if __name__ == "__main__":
    main()
