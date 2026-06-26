# 智能问答助手系统 — 开发日志

> 说明: 本文档按时间顺序记录系统每次开发迭代的内容，追溯功能演进与变更历史。

---

## 2026-06-22 — 功能全景梳理与文档归整

### 变更内容
- 对比 `docs/项目功能文档.md`、`docs/前端功能文档v0.1~v0.3`、`docs/后端重构计划.md` 与代码库实际实现
- 创建 `docs/development_document.md`，记录完整的功能模块状态
- 创建 `docs/development_log.md`（本文件）

### 数据来源
- 后端 Java 全部 29 个 Controller API 接口
- 前端 9 个 `.vue` 页面文件
- Python model_server 全部 7 个 API 端点
- MySQL 14 张业务表

### 评估结论
- 后端核心逻辑基本覆盖设计文档的全部功能
- 前端管理端页面（dashboard、knowledge、chat、academic、vector）均已实现
- 登录页已恢复，学生端首页、对话页正常运行
- 缺失功能主要集中在 **安全脱敏**、**客服排队/断线重连**、**数据可视化** 三个领域

---

## 2026-06-11 — 修复 LoginPage 版本不兼容问题

### 变更内容
- 发现 `LoginPage.vue` 依赖 `vue-router`（`useRouter`），但项目未使用 vue-router（手动 hash 路由）
- 从 commit `d47bdafb` 恢复兼容版本的 `LoginPage.vue`，改用 `emit('login-success')` + `adminApi` 方式

### 影响范围
- `frontend/src/views/LoginPage.vue`

---

## 2026-06-11 — 恢复首页登录入口

### 变更内容
- 从 commit `d47bdafb` 恢复了因回退丢失的登录功能
- `App.vue` — 添加 LoginPage 导入、路由映射、`navigate-login` 事件和 `onLoginSuccess` 回调
- `home.vue` — 恢复 navbar 上的登录按钮、学生登录后的侧边栏布局
- `home.js` — 恢复完整的登录状态管理、对话历史管理、退出登录逻辑

### 影响范围
- `frontend/src/App.vue`
- `frontend/src/views/home.vue`
- `frontend/src/js/home.js`

---

## 2026-06-11 — 修复 QuestionExpander 缺少 expandQuestions 方法

### 变更内容
- `QuestionExpander.java` 调用 `localModelClient.expandQuestions()` 但接口和实现中均不存在
- 在 `LocalModelClient` 接口添加 `expandQuestions(String, String)` 方法
- 在 `LocalModelClientImpl` 实现 POST `/expand` 调用
- Python model_server 新增 `/expand` 端点，使用 Qwen3.5-4B 生成 3 个替代问题表述

### 影响范围
- `backend/model-service/model_server.py`
- `backend/src/.../LocalModelClient.java`
- `backend/src/.../LocalModelClientImpl.java`

---

## Commit: d47bdafb — 连接远程向量数据库 & 登录功能重构

### 变更内容
- 添加远程 Milvus 向量数据库连接功能
- 修改登录页、首页和管理端前端路由
- App.vue 支持路径映射（`/admin/knowledge` 等）
- 首页 navbar 添加登录按钮
- 学生登录后显示侧边栏（对话历史管理）
- 实现 `onLoginSuccess` 根据角色路由（role=1→学生端, role=2→教师, role=3→管理员）

### 影响范围
- 前端: App.vue, home.vue, home.js, LoginPage.vue
- 后端: 向量数据库相关

---

## Commit: 5b9b09ae — 新增管理端页面和密码加密

### 变更内容
- 新增管理端各功能页面（dashboard, chat, knowledge, academic, vector）
- 实现 BCrypt 密码加密处理

### 影响范围
- `frontend/src/views/dashboard.vue`
- `frontend/src/views/chat.vue`
- `frontend/src/views/knowledge.vue`
- `frontend/src/views/academic.vue`
- `frontend/src/views/vector.vue`
- `backend/.../PasswordUtils.java`

---

## Commit: c7cde27c — 新增首页和对话页

### 变更内容
- 实现学生端首页 `home.vue`（类别选择、问题输入、导航到对话）
- 实现对话页 `dialogue.vue`（AI 问答交互）
- App.vue 中实现手动页面路由切换

### 影响范围
- `frontend/src/views/home.vue`
- `frontend/src/views/dialogue.vue`
- `frontend/src/App.vue`

---

## Commit: d010fe97 — 前端基本样式模板

### 变更内容
- 初始化 Vue 3 + Vite 项目结构
- 搭建前端样式模板

---

## Commit: c151fb2c — 上传全面的 API 接口文档

### 变更内容
- 编写涵盖身份认证、用户管理、聊天、知识库、向量搜索、学术支持等全部 API 的接口文档

---

## Commit: d22d3037 — 添加 Python 脚本运行本地模型

### 变更内容
- 创建 `model-service/model_server.py`
- 实现 FastAPI 服务，内嵌加载 BGE、Qwen、MacBERT 模型
- 提供 embed、rerank、rewrite、classify、generate 五个推理端点

---

## Commit: 0f73011c — 集成火山引擎语音服务

### 变更内容
- 集成火山引擎 ASR（语音识别）和 TTS（语音合成）
- 前端支持语音输入和语音播报
- `POST /api/chat/voice`

### 影响范围
- `backend/.../AudioService.java`
- `backend/.../AudioServiceImpl.java`
- `ChatController.java` voice 端点

---

## Commit: 250f5c4c — 数据统计与热门问题管理

### 变更内容
- 实现 `StatHotQuestion` 实体、Mapper、Service、Controller
- 实现热点统计、重建、兜底概览
- `DataStatController` / `DataStatService`

---

## Commit: 58f293db — 核心 AI 聊天功能

### 变更内容
- 实现 `ChatController` 全部聊天端点（text, ai-core, stream, voice, psychological, transfer-to-human, admin/reply, admin/finish, suggested-questions, report-unrecognized）
- 实现 `AiChatService` / `AiChatServiceImpl`
- 实现 `CozeService` 对接 Coze 平台

---

## Commit: 50b42475 — 嵌入与重排搜索

### 变更内容
- 实现 BGE 本地嵌入（`EmbeddingService`）
- 实现 BGE-reranker 精排（`RerankService`）
- 实现 Milvus 向量存储/检索（`MilvusClientManager`）

---

## Commit: 83df922f — 知识库管理重构

### 变更内容
- 实现 `KbDocumentService` / `KbQaEntryService` / `KnowledgeBaseService`
- 实现 `DocumentParserUtil` (Apache POI)
- 实现 `DataCleanService` (Qwen3-0.6B 清洗)
- 实现 `KnowledgeChunker` (语义分块)

---

## Commit: 389daa89 — WebSocket 与转人工功能

### 变更内容
- 实现 `ChatWebSocketServer`，路径 `/ws/chat/{role}/{userId}`
- 实现用户/客服连接池、在线客服集合
- `POST /api/chat/transfer-to-human`
- `POST /api/chat/admin/reply`
- `POST /api/chat/admin/finish`

---

## Commit: 2c5d0905 — 学业帮扶模块

### 变更内容
- 实现 `AcademicSupportController` / `AcademicSupportServiceImpl`
- 生成学业预警、更新学生画像、生成 PDF 报告
- 实现 `StudentProfile` / `AcademicWarningRecord` / `StudentGrowthArchive` 实体

---

## Commit: fa000be8 — 未识别问题管理

### 变更内容
- 实现 `UnrecognizedQueryController` / `UnrecognizedQueryServiceImpl`
- 未识别问题列表 + 状态更新

---

## Commit: 8a4a7548 — BizContact 业务联系

### 变更内容
- 实现 `BizContact` 实体、Mapper、Service
- 业务关联联系人表驱动推送

---

## Commit: aa8e9546 — 项目结构重构

### 变更内容
- 重构 Spring Boot 项目包结构
- 确立 `me.rainhouse.qasystem` 基础包

---

## Commit: 01a5bf30 — 首次提交

### 变更内容
- 初始化 Spring Boot 项目
- MySQL 建表 SQL
- 基础配置
