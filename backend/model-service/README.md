# 本地模型服务

这个服务负责真实加载 `/opt/aige-models` 下的五个 HuggingFace 模型，Spring Boot 后端通过 `LOCAL_MODEL_SERVICE_URL` 调用它。

## 模型映射

- `Qwen3-0.6B`: 问题规范化改写
- `Qwen3.5-4B`: 基于 Top-3 知识片段生成最终答案
- `bge-base-zh-v1.5`: 向量化
- `bge-reranker-large`: 重排序
- `chinese-macbert-base`: 意图分类语义匹配

## 启动

建议使用 Python 3.10 或 3.11。PyTorch 对过新的 Python 版本可能没有可用 wheel。

```bash
cd backend/model-service
python3.11 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
./start.sh
```

`start.sh` 会优先使用当前目录的 `.venv/bin/python`；如需指定其他解释器，可通过 `PYTHON_BIN=/path/to/python ./start.sh` 覆盖。

后端默认访问：

```properties
ai.models.local-service.base-url=http://127.0.0.1:18080
```
