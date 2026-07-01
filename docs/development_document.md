# 智能问答助手系统 — 功能开发文档

> 版本: v1.0
> 更新日期: 2026-06-22
> 说明: 本文档记载系统的功能全景、已完成模块、未完成模块及后续开发计划。

---

## 一、架构总览

系统采用前后端分离架构：

| 层级 | 技术栈 | 说明 |
|------|--------|------|
| 前端 | Vue 3 (Vite) | PC Web + 移动端响应式 |
| 后端 | Spring Boot 3 + MyBatis | RESTful API + WebSocket |
| 模型服务 | Python FastAPI (model_server.py) | 本地加载 HuggingFace 模型推理 |
| 向量数据库 | Milvus (远程) | 文本向量存储与检索 |
| 数据库 | MySQL | 业务数据持久化 |

### 模型依赖

| 模型 | 用途 | 路径 |
|------|------|------|
| BGE-small-zh-v1.5 | 文本嵌入/向量化 | `/opt/aige-models/bge-small-zh-v1.5` |
| BGE-reranker-large | 搜索结果重排序 | `/opt/aige-models/bge-reranker-large` |
| Qwen3-0.6B | 问题改写 | `/opt/aige-models/Qwen3-0.6B` |
| Qwen3.5-4B | 答案生成 + 问题扩展 | `/opt/aige-models/Qwen3.5-4B` |
| Chinese-MacBERT | 意图分类 | `/opt/aige-models/chinese-macbert-base` |

---

## 二、模块详细状态

### 1. 用户认证模块 (Auth)

| 功能 | 状态 | 备注 |
|------|------|------|
| 账号密码登录 | ✅ 完成 | `POST /api/auth/login` + JWT |
| SSO 一网通登录 | ✅ 完成 | `POST /api/auth/sso/callback`, `CampusSsoService` |
| 微信 OAuth 登录 | ✅ 完成 | `WechatController`, `WechatService` |
| 获取用户信息 | ✅ 完成 | `GET /api/auth/info` |
| 登出 | ✅ 完成 | `POST /api/auth/logout` |
| 修改密码 | ✅ 完成 | `POST /api/auth/updatePassword` |
| RBAC 权限拦截器 | ✅ 完成 | `AuthInterceptor` 实现 |
| 登录页 | ✅ 完成 | `LoginPage.vue` |

### 2. 智能问答模块 (AI Chat)

| 功能 | 状态 | 备注 |
|------|------|------|
| 发送文本消息 | ✅ 完成 | `POST /api/chat/text` |
| AI 核心推理 | ✅ 完成 | `POST /api/chat/ai-core` — 走 Coze / 本地模型 |
| 流式回复 | ✅ 完成 | `POST /api/chat/ai-core/stream` (SSE) |
| 语音消息 | ✅ 完成 | `POST /api/chat/voice` (火山引擎 ASR + TTS) |
| 心理倾诉 | ✅ 完成 | `POST /api/chat/psychological` |
| 问题改写 | ✅ 完成 | `QueryRewriteService` + model_server `/rewrite` |
| 意图分类 | ✅ 完成 | `IntentClassifierService` + model_server `/classify` |
| 向量检索 (Embed + Rerank) | ✅ 完成 | `VectorSearchService`, Milvus |
| 答案生成 (本地模型) | ✅ 完成 | `AnswerGeneratorService` + model_server `/generate` |
| Coze 对接 | ✅ 完成 | `CozeService`, `CozeServiceImpl` |
| 对话页 (前端) | ✅ 完成 | `dialogue.vue` |
| 首页 (前端) | ✅ 完成 | `home.vue` |
| 问题扩展 | ✅ 完成 | `QuestionExpander` + model_server `/expand` |

### 3. 知识库管理 (Knowledge Base)

| 功能 | 状态 | 备注 |
|------|------|------|
| 文档上传 | ✅ 完成 | `POST /api/kb/upload` + `KbDocumentService` |
| 文档解析 (Word/Excel) | ✅ 完成 | `DocumentParserUtil` (Apache POI) |
| 数据清洗 (本地 LLM) | ✅ 完成 | `DataCleanService` |
| 问答词条列表 | ✅ 完成 | `GET /api/kb/entries` |
| 新增词条 | ✅ 完成 | `POST /api/kb/entries` |
| 编辑词条 | ✅ 完成 | `PUT /api/kb/entries` |
| 删除词条 | ✅ 完成 | `DELETE /api/kb/entries/{id}` |
| 文档列表 | ✅ 完成 | `GET /api/kb/documents` |
| 知识库管理页 (前端) | ✅ 完成 | `knowledge.vue` |

### 4. 数据统计模块 (Data Statistics)

| 功能 | 状态 | 备注 |
|------|------|------|
| 常见问题列表 | ✅ 完成 | `GET /api/stat/hot-questions` |
| 重建常见问题 | ✅ 完成 | `POST /api/stat/hot-questions/rebuild` |
| 兜底数据概览 | ✅ 完成 | `GET /api/stat/fallback-overview` |
| 数据看板页 (前端) | ✅ 完成 | `dashboard.vue` |
| 未识别问题列表 | ✅ 完成 | `GET /api/admin/unrecognized/list` |
| 未识别问题状态更新 | ✅ 完成 | `POST /api/admin/unrecognized/update-status` |
| 常见问题主动推送 | ❌ 未开发 | 计划: 登录 WebSocket 推送 "猜你想问" |
| 常见问题词云/趋势图 (ECharts) | ❌ 未开发 | 计划: dashboard 前端接入 ECharts |

### 5. 人工客服模块 (Human Customer Service)

| 功能 | 状态 | 备注 |
|------|------|------|
| WebSocket 双向通信 | ✅ 完成 | `ChatWebSocketServer`, 路径 `/ws/chat/{role}/{userId}` |
| 转人工申请 | ✅ 完成 | `POST /api/chat/transfer-to-human` |
| 客服回复 | ✅ 完成 | `POST /api/chat/admin/reply` |
| 客服结束会话 | ✅ 完成 | `POST /api/chat/admin/finish` |
| 人工客服工作台 (前端) | ✅ 完成 | `chat.vue` (AdminChat) |
| 排队/派单逻辑 | ⚠️ 部分完成 | WebSocket 有 onlineAdmins 集合，但缺乏完整排队队列 |
| 断线重连 | ❌ 未开发 | 计划: WebSocket 自动重连机制 |
| 会话状态机 (AI→人工→结束) | ⚠️ 部分完成 | 后端 status 字段 0/1/2 已定义，但状态流转未严格校验 |

### 6. 学业帮扶模块 (Academic Support)

| 功能 | 状态 | 备注 |
|------|------|------|
| 生成学业预警 | ✅ 完成 | `POST /api/academic/generate-warning` |
| 更新学生画像 | ✅ 完成 | `POST /api/academic/profile/update` |
| 生成 PDF 报告 | ✅ 完成 | `POST /api/academic/generate-pdf-report` (iText) |
| 学生画像管理 | ⚠️ 部分完成 | `StudentProfile` 实体已建，前端页面 `academic.vue` 已接入 |
| 学业预警列表 | ⚠️ 部分完成 | `academic.vue` 已有列表展示 |
| 学业帮扶页 (前端) | ✅ 完成 | `academic.vue` |
| AI 共情 Prompt 增强 | ❌ 未开发 | 计划: 将学生画像标签注入 Coze 心理对话上下文 |
| 成长档案自动化生成 | ❌ 未开发 | `StudentGrowthArchiveService` 已有，但缺定时生成流程 |

### 7. 向量检索调试 (Vector Search Debug)

| 功能 | 状态 | 备注 |
|------|------|------|
| 重建向量索引 | ✅ 完成 | `POST /api/vector/rebuild` |
| 向量检索调试 | ✅ 完成 | `POST /api/vector/search` |
| 向量调试页 (前端) | ✅ 完成 | `vector.vue` |

### 8. 微信集成

| 功能 | 状态 | 备注 |
|------|------|------|
| 微信公众号验证 | ✅ 完成 | `GET /api/wechat/portal` (Token 校验) |
| 微信消息接收 | ✅ 完成 | `POST /api/wechat/portal` (XML 消息处理) |
| 微信 OAuth 登录 | ✅ 完成 | `GET /api/wechat/login` |
| 微信消息回复 | ✅ 完成 | `WechatServiceImpl` |

### 9. 安全与合规

| 功能 | 状态 | 备注 |
|------|------|------|
| JWT 鉴权 | ✅ 完成 | `JwtUtil`, `AuthInterceptor` |
| 密码加密 | ✅ 完成 | `PasswordUtils` (BCrypt) |
| 数据脱敏拦截器 | ❌ 未开发 | 计划: AOP 拦截器对出栈数据脱敏 (学号/姓名/电话) |
| 前端脱敏展示 | ❌ 未开发 | 计划: 管理端列表对敏感字段做掩码 |

---

## 三、数据库表

| 表名 | 用途 | 状态 |
|------|------|------|
| `sys_user` | 系统用户 | ✅ |
| `chat_session` | 对话会话 | ✅ |
| `chat_message` | 聊天消息 | ✅ |
| `biz_contact` | 业务联系教师 | ✅ |
| `kb_document` | 知识库原始文档 | ✅ |
| `kb_qa_entry` | 问答词条 | ✅ |
| `stat_hot_question` | 常见问题统计 | ✅ |
| `unrecognized_query` | 未识别问题 | ✅ |
| `student_profile` | 学生画像 | ✅ |
| `academic_warning_record` | 学业预警记录 | ✅ |
| `student_growth_archive` | 学生成长档案 | ✅ |
| `question_raw` | 原始问题 | ✅ |
| `question_hit_record` | 问题命中记录 | ✅ |
| `psychological_chat_record` | 心理聊天记录 | ✅ |

---

## 四、未开发功能总览

### 优先级高

1. **数据脱敏拦截器** — 安全底线。AOP 拦截所有出栈请求，对学号、姓名、电话、邮箱做正则替换，防止裸数据流出系统
2. **WebSocket 断线重连** — 影响客服模块稳定性。前端建立 WebSocket 自动重连逻辑 + 用户提示
3. **常见问题主动推送** — 用户登录/打开首页时，WebSocket 下推常见问题列表

### 优先级中

4. **ECharts 数据可视化** — 数据看板接入热词云图、趋势折线图、环比图
5. **AI 共情 Prompt 增强** — 心理倾诉时携带学生画像标签，提升 Coze Agent 回复质量
6. **人工客服排队/派单系统** — 完善等待队列，支持排队序号展示、客服空闲分配

### 优先级低

7. **成长档案定时生成** — 学期末自动生成 PDF 成长报告
8. **前端脱敏展示** — 管理端列表对敏感字段做星号掩码
9. **会话状态机严格校验** — chat_session.status 的 0→1→2 流转增加校验逻辑
10. **SSO / 微信免密登录优化** — 统一身份绑定流程
