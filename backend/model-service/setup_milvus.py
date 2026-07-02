"""Initialize the Milvus collection used by the QA system.

This script creates ``knowledge_vector_v2`` with the layout used by the
three-level FAQ knowledge-base design:

- id: Int64 primary key, auto_id disabled
- embedding: FloatVector(768), AUTOINDEX, COSINE
- module_type: VarChar(50)
- question: VarChar(1000), AUTOINDEX
- knowledge_id: Int64, AUTOINDEX
- category_l1_id/category_l2_id/category_l3_id: Int64, AUTOINDEX
- category_path: VarChar(300)
- status: Int64, AUTOINDEX

If an incompatible empty collection already exists, it is recreated. If the
collection contains data, set RESET_MILVUS_COLLECTION=true to force recreation.
"""

import os
from typing import Dict, Tuple

from pymilvus import (
    Collection,
    CollectionSchema,
    DataType,
    FieldSchema,
    connections,
    utility,
)


COLLECTION_NAME = os.getenv("MILVUS_COLLECTION_NAME", "knowledge_vector_v2")
VECTOR_DIM = 768
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
    "embedding": {
        "index_type": "AUTOINDEX",
        "metric_type": "COSINE",
    },
    "question": {"index_type": "AUTOINDEX"},
    "knowledge_id": {"index_type": "AUTOINDEX"},
    "category_l1_id": {"index_type": "AUTOINDEX"},
    "category_l2_id": {"index_type": "AUTOINDEX"},
    "category_l3_id": {"index_type": "AUTOINDEX"},
    "status": {"index_type": "AUTOINDEX"},
}


def connect() -> None:
    connections.connect("default", host="localhost", port="19530")


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
        description="QA knowledge vector collection",
        enable_dynamic_field=False,
    )
    collection = Collection(
        name=COLLECTION_NAME,
        schema=schema,
        consistency_level="Bounded",
        shards_num=1,
        properties={"timezone": "UTC"},
    )

    collection.create_index("embedding", EXPECTED_INDEXES["embedding"], index_name="embedding")
    collection.create_index("question", EXPECTED_INDEXES["question"], index_name="question")
    collection.create_index("knowledge_id", EXPECTED_INDEXES["knowledge_id"], index_name="knowledge_id")
    collection.create_index("category_l1_id", EXPECTED_INDEXES["category_l1_id"], index_name="category_l1_id")
    collection.create_index("category_l2_id", EXPECTED_INDEXES["category_l2_id"], index_name="category_l2_id")
    collection.create_index("category_l3_id", EXPECTED_INDEXES["category_l3_id"], index_name="category_l3_id")
    collection.create_index("status", EXPECTED_INDEXES["status"], index_name="status")
    collection.load()
    return collection


def explain_mismatch(collection: Collection) -> None:
    print("Existing collection does not match knowledge_vector.json.")
    print("Current fields:")
    for name, dtype, params, is_primary in existing_field_signature(collection):
        print(f"  - {name}: dtype={dtype}, params={params}, primary={is_primary}")
    print("Current indexes:")
    for field_name, params in existing_indexes(collection).items():
        print(f"  - {field_name}: {params}")


def should_force_reset() -> bool:
    return os.getenv(RESET_ENV, "").strip().lower() in {"1", "true", "yes", "y"}


def recreate_collection(reason: str) -> Collection:
    print(f"Dropping collection '{COLLECTION_NAME}' ({reason}).")
    utility.drop_collection(COLLECTION_NAME)
    return create_collection()


def main() -> None:
    connect()

    if utility.has_collection(COLLECTION_NAME):
        collection = Collection(COLLECTION_NAME)
        if is_expected_schema(collection):
            collection.load()
            print(f"Collection '{COLLECTION_NAME}' already matches knowledge_vector.json.")
            return

        explain_mismatch(collection)
        row_count = collection.num_entities
        if row_count == 0:
            recreate_collection("schema mismatch and row count is 0")
            print(f"Collection '{COLLECTION_NAME}' recreated successfully.")
            return
        if should_force_reset():
            recreate_collection(f"schema mismatch and {RESET_ENV}=true")
            print(f"Collection '{COLLECTION_NAME}' recreated successfully.")
            return

        raise SystemExit(
            f"Collection '{COLLECTION_NAME}' has {row_count} rows. "
            f"Set {RESET_ENV}=true if you want to drop and recreate it."
        )

    create_collection()
    print(f"Collection '{COLLECTION_NAME}' created successfully.")


if __name__ == "__main__":
    main()
