# Docker 一键部署说明

> 校内服务器交接、SSH Deploy Key、后续 `git pull` 更新流程请优先参考：`docs/server-deployment-handover.md`。

本项目提供完整 Docker Compose 编排，可一次性启动：

- MySQL 8.0
- Redis 7
- Milvus standalone（etcd + MinIO + Milvus）
- 本地模型服务 FastAPI
- Spring Boot 后端
- Vue 前端 Nginx 静态站点与反向代理

## 1. 准备环境变量

在项目根目录执行：

```bash
cp .env.example .env
```

然后编辑 `.env`，至少修改：

```properties
MYSQL_ROOT_PASSWORD=你的MySQL密码
REDIS_PASSWORD=你的Redis密码
AIGE_MODEL_BASE_HOST=/opt/aige-models
```

`AIGE_MODEL_BASE_HOST` 是宿主机上的模型目录，目录下需要包含：

```text
Qwen3-0.6B
Qwen2.5-1.5B-Instruct
bge-base-zh-v1.5
bge-reranker-large
chinese-macbert-base
```

如果没有火山语音或 Coze 凭证，可以先保持为空；相关能力调用时会受影响。

## 2. 启动

```bash
docker compose up -d --build
```

启动后默认访问：

- 前端：http://localhost
- 后端：http://localhost:8080
- 本地模型服务：http://localhost:18080/health
- Milvus：http://localhost:19530

前端 Nginx 已内置反向代理：

- `/api/*` -> `backend:8080`
- `/ws/*` -> `backend:8080`
- `/media/*` -> `backend:8080`

## 3. 查看日志

```bash
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f model-service
```

## 4. 停止服务

```bash
docker compose down
```

如果需要清空数据库、Redis、Milvus 和上传文件：

```bash
docker compose down -v
```

注意：MySQL 初始化脚本 `backend/llm_qa_sy.sql` 只会在 MySQL 数据卷首次创建时执行。已有数据卷时修改 SQL 不会自动重放；需要手动迁移或执行 `docker compose down -v` 后重建。

## 5. GPU 说明

默认 `.env.example` 使用：

```properties
AIGE_MODEL_DEVICE=cpu
```

如果部署机器已安装 NVIDIA Container Toolkit，可将其改为：

```properties
AIGE_MODEL_DEVICE=cuda
```

并根据服务器 Docker 环境给 `model-service` 增加 GPU 运行配置。不同服务器的 GPU runtime 配置差异较大，当前默认 compose 不强制占用 GPU，以保证 CPU 环境可直接启动。
