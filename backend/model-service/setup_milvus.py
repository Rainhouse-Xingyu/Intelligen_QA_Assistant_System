"""
Milvus 本地初始化脚本 —— 创建 knowledge_vector collection。
运行前请确认：
  1. docker-compose up -d 已启动（localhost:19530）
  2. pip install pymilvus
"""
from pymilvus import connections, Collection, CollectionSchema, FieldSchema, DataType, utility

connections.connect("default", host="localhost", port="19530")

# 如果已存在则跳过
if utility.has_collection("knowledge_vector"):
    print("Collection 'knowledge_vector' 已存在，跳过创建。")
    Collection("knowledge_vector").load()
else:
    fields = [
        FieldSchema(name="id", dtype=DataType.INT64, is_primary=True),
        FieldSchema(name="knowledge_id", dtype=DataType.INT64),
        FieldSchema(name="question", dtype=DataType.VARCHAR, max_length=1000),
        FieldSchema(name="module_type", dtype=DataType.VARCHAR, max_length=50),
        FieldSchema(name="embedding", dtype=DataType.FLOAT_VECTOR, dim=768),
    ]
    schema = CollectionSchema(fields, description="知识库向量表")
    collection = Collection("knowledge_vector", schema)

    index_params = {
        "index_type": "IVF_FLAT",
        "metric_type": "COSINE",
        "params": {"nlist": 128},
    }
    collection.create_index("embedding", index_params)
    collection.load()

print("Collection 'knowledge_vector' 就绪，向量维度: 768，度量方式: COSINE")
