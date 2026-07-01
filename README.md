# 智能问答助手系统 (Intelligent Q&A Assistant System)

> **注意**：本项目目前正在开发中（WIP），部分功能和文档可能尚不完善。

## 📖 项目简介

本项目是一个基于大语言模型（LLM）的智能问答助手系统。系统不仅支持即时的智能对话交互，还集成了知识库管理、微信平台接入以及学术预警等功能，旨在提供高效、全方位的智能问答支持。

## 🏗 项目结构

系统主要由以下三大核心模块构成：

- **`backend/`** (后端服务)
  - 基于 Java Spring Boot 框架开发的 RESTful API 与 WebSocket 服务。
  - 核心业务包含：聊天问答对话（LLM 对接）、数据大屏统计、知识库文档管理、微信公众号/小程序接入、学业预警支持等。
  - 数据库初始化脚本：`llm_qa_sy.sql`。
- **`frontend/`** (前端页面)
  - 基于 Vue.js 3 和 Vite 构建的用户交互界面。
- **`docs/`** (说明文档)
  - 记录了关键设计疑点、多版本前端功能文档、API 接口文档以及智能体对接规范。

## 🛠 技术栈

### 后端 (Backend)
- **核心框架**: Java / Spring Boot
- **数据库**: MySQL
- **实时通信**: WebSocket (用于流式问答对话)
- **第三方接入**: WeChat SDK (微信生态对接)

### 前端 (Frontend)
- **核心框架**: Vue.js
- **构建工具**: Vite

## 🚀 快速开始

### Docker 一键部署

项目已提供完整 Docker Compose 编排，可启动 MySQL、Redis、Milvus、本地模型服务、后端和前端：

```bash
cp .env.example .env
docker compose up -d --build
```

默认前端访问地址：`http://localhost`。模型目录、端口和第三方密钥配置请参考 `docs/docker-deploy.md`。

### 1. 数据库准备
在 MySQL 中新建数据库，并执行 `backend/llm_qa_sy.sql` 脚本以创建所需的表结构及初始数据。

### 2. 后端运行
1. 进入 `backend` 目录。
2. 将 `config/application-secrets.example.properties` 复制并重命名为 `application-secrets.properties`，并补充实际的数据库密码、大模型 API Key 及微信相关密钥。
3. 运行项目：
   ```bash
   ./mvnw spring-boot:run
   ```

### 3. 前端运行
1. 进入 `frontend` 目录。
2. 安装依赖：
   ```bash
   npm install
   ```
3. 启动本地开发服务器：
   ```bash
   npm run dev
   ```

## 📝 详细文档
更多详情和项目设计规划，请参考 `docs/` 目录下的相关 Markdown 文件（如 `API接口文档.md`、`智能体部分对接文档.md` 等）。
