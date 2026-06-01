# 前端 API 接口文档

本文档根据当前后端 Controller 代码整理，供前端页面、管理端页面、微信公众号 H5 和调试页面对接使用。

## 1. 通用约定

### 1.1 基础地址

```text
本地开发: http://localhost:8080
接口前缀: /api
```

### 1.2 鉴权

除以下接口外，其余 `/api/**` 接口均需要携带 JWT：

- `POST /api/auth/login`
- `POST /api/auth/sso/callback`
- `GET /api/wechat/portal`
- `POST /api/wechat/portal`
- `GET /api/wechat/login`

请求头：

```http
Authorization: Bearer <token>
```

### 1.3 统一返回结构

后端统一返回类：

```text
me.rainhouse.qasystem.common.result.Result<T>
```

字段含义：

| 字段 | 类型 | 含义 |
|---|---|---|
| `code` | number | 业务状态码。`200` 成功，`401` 未授权，`500` 业务失败 |
| `message` | string | 返回消息 |
| `data` | T | 业务数据 |

结构示例：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

### 1.4 分页返回结构

MyBatis-Plus `Page<T>` 常用字段：

| 字段 | 类型 | 含义 |
|---|---|---|
| `records` | array | 当前页数据 |
| `total` | number | 总条数 |
| `size` | number | 每页条数 |
| `current` | number | 当前页 |
| `pages` | number | 总页数 |

## 2. 登录与用户

### 2.1 账号密码登录

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.AuthController.login` |
| 方法注释 | 用户通过账号密码登录，返回系统 JWT |
| 请求方式 | `POST` |
| 请求路径 | `/api/auth/login` |
| Content-Type | `application/json` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `username` | body | string | 是 | 登录账号 |
| `password` | body | string | 是 | 登录密码 |

请求示例：

```json
{
  "username": "student001",
  "password": "123456"
}
```

返回值含义：登录成功后返回 JWT。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "jwt-token"
  }
}
```

### 2.2 一网通登录回调

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.AuthController.ssoCallback` |
| 方法注释 | 大连东软信息学院一网通登录回调，用 SSO token 换系统 JWT |
| 请求方式 | `POST` |
| 请求路径 | `/api/auth/sso/callback` |
| Content-Type | `application/json` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `ssoToken` | body | string | 是 | 一网通返回的登录凭证 |

返回值含义：系统 JWT。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "jwt-token"
  }
}
```

### 2.3 获取当前用户信息

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.AuthController.getUserInfo` |
| 方法注释 | 获取当前登录用户信息，密码字段脱敏为空 |
| 请求方式 | `GET` |
| 请求路径 | `/api/auth/info` |

入参含义：无显式入参，后端从 JWT 中解析 `userId`。

返回值含义：当前用户信息。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "username": "student001",
    "password": null,
    "realName": "张三",
    "role": 1,
    "phone": "13800000000",
    "campusSsoId": "sso-id",
    "wechatOpenid": "openid",
    "avatarUrl": "https://...",
    "createdAt": "2026-06-01T10:00:00",
    "updatedAt": "2026-06-01T10:00:00"
  }
}
```

字段说明：

| 字段 | 类型 | 含义 |
|---|---|---|
| `role` | number | `1` 学生，`2` 教师，`3` 管理员 |

### 2.4 退出登录

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.AuthController.logout` |
| 方法注释 | 将当前 JWT 加入 Redis 黑名单 |
| 请求方式 | `POST` |
| 请求路径 | `/api/auth/logout` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `Authorization` | header | string | 否 | `Bearer <token>` |

返回值含义：退出成功，无业务数据。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": null
}
```

## 3. 智能问答与人工客服

### 3.1 发送文本消息

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.ChatController.sendText` |
| 方法注释 | 发送文本问题；AI 托管状态下走智能问答核心链路，人工状态下转交客服 |
| 请求方式 | `POST` |
| 请求路径 | `/api/chat/text` |
| Content-Type | `application/x-www-form-urlencoded` 或 query params |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `query` | requestParam | string | 是 | 用户问题 |
| `moduleType` | requestParam | string | 否 | 用户主动选择的模块，如 `考务通知`、`教学运行`、`学业帮扶`、`心理辅导` |
| `needTts` | requestParam | boolean | 否 | 是否需要生成语音播报，默认 `false` |

返回值含义：AI 答案文本；若当前会话处于人工状态，则返回“消息已转交人工客服”。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "这里是 AI 回复内容"
}
```

### 3.2 智能问答核心调试接口

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.ChatController.aiCore` |
| 方法注释 | 返回改写、分类、命中、引用和答案详情，适合前端调试和展示解释链路 |
| 请求方式 | `POST` |
| 请求路径 | `/api/chat/ai-core` |
| Content-Type | `application/json` |

入参结构：

```text
me.rainhouse.qasystem.common.dto.AiChatRequest
```

入参含义：

| 参数 | 类型 | 必填 | 含义 |
|---|---|---|---|
| `query` | string | 是 | 用户原始问题 |
| `moduleType` | string | 否 | 用户选择模块，传入后优先使用 |
| `needTts` | boolean | 否 | 当前接口未使用，预留字段 |

请求示例：

```json
{
  "query": "挂科了怎么办",
  "moduleType": "学业帮扶"
}
```

返回值含义：智能问答核心完整处理结果。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "originalQuestion": "挂科了怎么办",
    "rewriteQuestion": "挂科后应该如何处理？",
    "moduleType": "学业帮扶",
    "hitStatus": 2,
    "hitLabel": "强命中",
    "topScore": 0.91,
    "topKnowledgeId": 1001,
    "answer": "建议先查看补考或重修安排...",
    "answerSource": "RAG",
    "responseTimeMs": 320,
    "references": [
      {
        "knowledgeId": 1001,
        "question": "挂科后如何处理",
        "answer": "可以参加补考或重修...",
        "moduleType": "学业帮扶",
        "sourceType": "manual",
        "vectorScore": 0.88,
        "rerankScore": 0.92,
        "finalScore": 0.91
      }
    ]
  }
}
```

字段说明：

| 字段 | 类型 | 含义 |
|---|---|---|
| `hitStatus` | number | `0` 未命中，`1` 弱命中，`2` 强命中 |
| `answerSource` | string | 答案来源，如 `RAG`、`Coze` |
| `references` | array | Top 知识库引用列表 |

### 3.3 智能问答核心 SSE 流式接口

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.ChatController.aiCoreStream` |
| 方法注释 | 智能问答核心 SSE 接口，按 `metadata`、`answer`、`done` 事件返回 |
| 请求方式 | `POST` |
| 请求路径 | `/api/chat/ai-core/stream` |
| Content-Type | `application/json` |
| Accept | `text/event-stream` |

入参同 `AiChatRequest`。

返回值含义：SSE 事件流，不包裹 `Result`。

事件结构：

```text
event: metadata
data: {"rewriteQuestion":"...","moduleType":"...","hitStatus":2,"hitLabel":"强命中","topScore":0.91,"answerSource":"RAG"}

event: answer
data: 分片文本

event: done
data: AiChatResponse 完整 JSON
```

### 3.4 发送语音消息

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.ChatController.sendVoice` |
| 方法注释 | 上传录音文件，后端 ASR 识别后进入智能问答核心链路，并生成 TTS 语音回复 |
| 请求方式 | `POST` |
| 请求路径 | `/api/chat/voice` |
| Content-Type | `multipart/form-data` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `audioFile` | formData | file | 是 | 用户录音文件，建议 mp3/wav/ogg |

返回值含义：识别文本、AI 回复和语音播放地址拼接后的文本。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "识别内容: [文本] \nAI回复: ... \n播放地址: /media/audio/xxx.mp3"
}
```

### 3.5 心理倾诉工作流

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.ChatController.psychologicalCounseling` |
| 方法注释 | 调用 Psychological_Counseling 工作流，接收心理咨询回复 |
| 请求方式 | `POST` |
| 请求路径 | `/api/chat/psychological` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `studentMsg` | requestParam | string | 是 | 学生心理倾诉内容 |

返回值含义：心理咨询回复文本。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "心理辅导回复内容"
}
```

### 3.6 用户申请转人工客服

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.ChatController.transferToHuman` |
| 方法注释 | 用户申请人工客服介入；若有在线客服则修改会话状态并通过 WebSocket 通知客服 |
| 请求方式 | `POST` |
| 请求路径 | `/api/chat/transfer-to-human` |

入参含义：无显式入参，后端从 JWT 解析用户。

返回值含义：转人工结果提示。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "已成功为您转接人工客服，正在排队等待回复..."
}
```

### 3.7 客服回复用户

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.ChatController.adminReply` |
| 方法注释 | 客服在控制台给用户发送回复，并通过 WebSocket 推送给用户 |
| 请求方式 | `POST` |
| 请求路径 | `/api/chat/admin/reply` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `sessionId` | requestParam | number | 是 | 会话 ID |
| `content` | requestParam | string | 是 | 客服回复内容 |

返回值含义：回复处理结果。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "回复成功"
}
```

### 3.8 客服结束人工会话

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.ChatController.adminFinish` |
| 方法注释 | 客服结束会话，将会话交回 AI 托管 |
| 请求方式 | `POST` |
| 请求路径 | `/api/chat/admin/finish` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `sessionId` | requestParam | number | 是 | 会话 ID |

返回值含义：结束人工会话结果。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "已结束人工干预，会话交回AI"
}
```

### 3.9 获取推荐热点问题

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.ChatController.getSuggestedQuestions` |
| 方法注释 | 主动推送热点问题，供前端展示快速点击气泡 |
| 请求方式 | `GET` |
| 请求路径 | `/api/chat/suggested-questions` |

入参含义：无。

返回值含义：近 7 天热度最高的 5 个问题。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "name": "补考什么时候报名",
      "moduleType": "考务通知",
      "answerText": "补考报名通常...",
      "value": 12,
      "lastHitTime": "2026-06-01T10:00:00"
    }
  ]
}
```

### 3.10 上报未识别问题

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.ChatController.reportUnrecognized` |
| 方法注释 | 用户点踩或反馈机器人兜底回答时，记录未识别问题 |
| 请求方式 | `POST` |
| 请求路径 | `/api/chat/report-unrecognized` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `query` | requestParam | string | 是 | 用户反馈的问题文本 |

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "已记录反馈，管理员将尽快根据您的提问完善系统知识库"
}
```

## 4. 知识库管理

### 4.1 上传并解析知识库文档

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.KnowledgeBaseController.uploadDocument` |
| 方法注释 | 上传并解析 Word/Excel/PDF/TXT 文件，清洗为 FAQ 后落库 |
| 请求方式 | `POST` |
| 请求路径 | `/api/kb/upload` |
| Content-Type | `multipart/form-data` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `file` | formData | file | 是 | 知识库文档 |
| `moduleType` | formData | string | 否 | 文档所属模块 |

返回值含义：文档解析记录。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "fileName": "faq.xlsx",
    "fileUrl": "uploads/kb/xxx.xlsx",
    "uploaderId": 1,
    "processStatus": 2,
    "processMessage": "解析成功",
    "createdAt": "2026-06-01T10:00:00"
  }
}
```

字段说明：

| 字段 | 类型 | 含义 |
|---|---|---|
| `processStatus` | number | `0` 待解析，`1` 解析中，`2` 成功，`3` 失败 |

### 4.2 查询文档解析记录

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.KnowledgeBaseController.listDocuments` |
| 方法注释 | 查询知识库文档解析记录 |
| 请求方式 | `GET` |
| 请求路径 | `/api/kb/documents` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `processStatus` | query | number | 否 | 解析状态过滤 |

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1,
      "fileName": "faq.xlsx",
      "fileUrl": "uploads/kb/xxx.xlsx",
      "uploaderId": 1,
      "processStatus": 2,
      "processMessage": "解析成功",
      "createdAt": "2026-06-01T10:00:00"
    }
  ]
}
```

### 4.3 查询问答词条

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.KnowledgeBaseController.listEntries` |
| 方法注释 | 查询已落库问答词条，支持关键字、模块、状态、来源过滤 |
| 请求方式 | `GET` |
| 请求路径 | `/api/kb/entries` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `keyword` | query | string | 否 | 按问题或答案关键字搜索 |
| `moduleType` | query | string | 否 | 模块过滤 |
| `status` | query | number | 否 | `0` 禁用，`1` 启用 |
| `sourceType` | query | string | 否 | 来源过滤，如 `manual`、`document` |

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "id": 1001,
      "documentId": 1,
      "question": "补考如何报名",
      "answer": "请在教务系统报名...",
      "status": 1,
      "moduleType": "考务通知",
      "sourceType": "document",
      "createdBy": 1,
      "createdAt": "2026-06-01T10:00:00",
      "updatedAt": "2026-06-01T10:00:00"
    }
  ]
}
```

### 4.4 修改问答词条

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.KnowledgeBaseController.updateEntry` |
| 方法注释 | 修改指定问答词条，并同步更新向量索引 |
| 请求方式 | `PUT` |
| 请求路径 | `/api/kb/entries` |
| Content-Type | `application/json` |

入参结构：`me.rainhouse.qasystem.entity.KbQaEntry`

入参含义：

| 参数 | 类型 | 必填 | 含义 |
|---|---|---|---|
| `id` | number | 是 | 词条 ID |
| `question` | string | 否 | 标准问题 |
| `answer` | string | 否 | 标准答案 |
| `status` | number | 否 | `0` 禁用，`1` 启用 |
| `moduleType` | string | 否 | 所属模块 |
| `sourceType` | string | 否 | 来源类型 |

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "词条更新成功"
}
```

### 4.5 新增问答词条

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.KnowledgeBaseController.createEntry` |
| 方法注释 | 手动逐条新增知识库问答，并同步写入向量索引 |
| 请求方式 | `POST` |
| 请求路径 | `/api/kb/entries` |
| Content-Type | `application/json` |

入参结构：`me.rainhouse.qasystem.entity.KbQaEntry`

请求示例：

```json
{
  "question": "补考如何报名",
  "answer": "请在教务系统中查看补考报名通知并按时提交。",
  "moduleType": "考务通知",
  "sourceType": "manual",
  "status": 1
}
```

返回值含义：创建后的词条。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1001,
    "documentId": null,
    "question": "补考如何报名",
    "answer": "请在教务系统中查看补考报名通知并按时提交。",
    "status": 1,
    "moduleType": "考务通知",
    "sourceType": "manual",
    "createdBy": 1,
    "createdAt": "2026-06-01T10:00:00",
    "updatedAt": "2026-06-01T10:00:00"
  }
}
```

### 4.6 禁用或删除词条

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.KnowledgeBaseController.disableEntry` |
| 方法注释 | 禁用或删除知识库词条，并从向量索引移除 |
| 请求方式 | `DELETE` |
| 请求路径 | `/api/kb/entries/{id}` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `id` | path | number | 是 | 词条 ID |

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "词条已成功禁用/删除"
}
```

## 5. 向量检索

### 5.1 重建向量索引

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.VectorSearchController.rebuildIndex` |
| 方法注释 | 根据启用的知识库词条重建向量索引 |
| 请求方式 | `POST` |
| 请求路径 | `/api/vector/rebuild` |

入参含义：无。

返回值含义：索引中的词条数量。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": 128
}
```

### 5.2 向量检索

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.VectorSearchController.search` |
| 方法注释 | 对问题执行 embedding、向量召回、重排序和命中判断 |
| 请求方式 | `POST` |
| 请求路径 | `/api/vector/search` |
| Content-Type | `application/json` |

入参结构：

```text
me.rainhouse.qasystem.common.dto.VectorSearchRequest
```

入参含义：

| 参数 | 类型 | 必填 | 含义 |
|---|---|---|---|
| `query` | string | 是 | 检索问题 |
| `moduleType` | string | 否 | 限制检索模块 |
| `topK` | number | 否 | 返回结果数量 |
| `sessionId` | number | 否 | 当前会话 ID，用于命中流水记录 |

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "query": "挂科怎么办",
    "moduleType": "学业帮扶",
    "hitStatus": 2,
    "hitLabel": "强命中",
    "topScore": 0.91,
    "topKnowledgeId": 1001,
    "answer": "可以参加补考或重修...",
    "responseTimeMs": 120,
    "results": [
      {
        "knowledgeId": 1001,
        "question": "挂科后如何处理",
        "answer": "可以参加补考或重修...",
        "moduleType": "学业帮扶",
        "sourceType": "manual",
        "vectorScore": 0.88,
        "rerankScore": 0.92,
        "finalScore": 0.91
      }
    ]
  }
}
```

## 6. 数据统计与兜底闭环

### 6.1 获取热点咨询问题

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.DataStatController.getHotQuestions` |
| 方法注释 | 获取热点咨询问题，供 ECharts 词云或榜单接入 |
| 请求方式 | `GET` |
| 请求路径 | `/api/stat/hot-questions` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 默认 | 含义 |
|---|---|---|---|---|---|
| `days` | query | number | 否 | `7` | 统计最近多少天 |
| `limit` | query | number | 否 | `20` | 返回条数 |

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": [
    {
      "name": "补考什么时候报名",
      "moduleType": "考务通知",
      "answerText": "补考报名说明...",
      "value": 12,
      "lastHitTime": "2026-06-01T10:00:00"
    }
  ]
}
```

### 6.2 手动重建热点问题统计

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.DataStatController.rebuildHotQuestions` |
| 方法注释 | 手动重建热点问题统计，便于管理员立即刷新报表 |
| 请求方式 | `POST` |
| 请求路径 | `/api/stat/hot-questions/rebuild` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `statDate` | query | string | 否 | 指定统计日期，格式 `YYYY-MM-DD` |
| `days` | query | number | 否 | 重建最近 N 天。传入后优先于 `statDate` |

返回值含义：写入的热点统计条数。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": 20
}
```

### 6.3 获取兜底闭环概览

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.DataStatController.getFallbackOverview` |
| 方法注释 | 获取命中分布、未命中待处理量和 Top 未识别问题 |
| 请求方式 | `GET` |
| 请求路径 | `/api/stat/fallback-overview` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 默认 | 含义 |
|---|---|---|---|---|---|
| `days` | query | number | 否 | `7` | 统计最近多少天 |

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "days": 7,
    "noHitCount": 3,
    "weakHitCount": 8,
    "strongHitCount": 80,
    "unrecognizedTotal": 5,
    "unrecognizedPending": 2,
    "topUnrecognized": [
      {
        "questionText": "奖学金怎么评",
        "moduleType": "学业帮扶",
        "topScore": 0.42,
        "frequency": 3,
        "firstSeenTime": "2026-06-01T09:00:00",
        "lastSeenTime": "2026-06-01T12:00:00"
      }
    ]
  }
}
```

### 6.4 未识别问题列表

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.UnrecognizedQueryController.list` |
| 方法注释 | 面向管理员的未识别问题分页列表 |
| 请求方式 | `GET` |
| 请求路径 | `/api/admin/unrecognized/list` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 默认 | 含义 |
|---|---|---|---|---|---|
| `current` | query | number | 否 | `1` | 当前页 |
| `size` | query | number | 否 | `10` | 每页数量 |
| `status` | query | number | 否 | 无 | 状态过滤。`0` 未处理，`1` 已处理 |

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "id": 1,
        "questionText": "奖学金怎么评",
        "moduleType": "学业帮扶",
        "topScore": 0.42,
        "frequency": 3,
        "status": 0,
        "processUser": null,
        "processTime": null,
        "createTime": "2026-06-01T10:00:00"
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

### 6.5 更新未识别问题状态

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.UnrecognizedQueryController.updateStatus` |
| 方法注释 | 管理员处理或更新未识别问题状态 |
| 请求方式 | `POST` |
| 请求路径 | `/api/admin/unrecognized/update-status` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `id` | requestParam | number | 是 | 未识别问题 ID |
| `status` | requestParam | number | 是 | 处理状态，`0` 未处理，`1` 已处理 |

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "状态更新成功"
}
```

## 7. 学业帮扶

### 7.1 生成学业预警与帮扶策略

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.AcademicSupportController.generateWarning` |
| 方法注释 | 教务老师/辅导员触发，要求 AI 诊断某个学生的学业并生成帮扶策略 |
| 请求方式 | `POST` |
| 请求路径 | `/api/academic/generate-warning` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `studentId` | requestParam | number | 是 | 学生用户 ID |
| `term` | requestParam | string | 是 | 学期，如 `2025-2026-1` |

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": 1,
    "studentId": 1001,
    "term": "2025-2026-1",
    "warningReason": "GPA 低于要求...",
    "aiSuggestedPlan": "建议每周进行课程复盘...",
    "reportPdfUrl": null,
    "createdAt": "2026-06-01T10:00:00",
    "createdUser": "system"
  }
}
```

### 7.2 更新学生画像

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.AcademicSupportController.updateProfile` |
| 方法注释 | 初始化或更新学生 GPA、挂科数、心理标签、风险等级等画像数据 |
| 请求方式 | `POST` |
| 请求路径 | `/api/academic/profile/update` |
| Content-Type | `application/json` |

入参结构：`me.rainhouse.qasystem.entity.StudentProfile`

入参含义：

| 参数 | 类型 | 必填 | 含义 |
|---|---|---|---|
| `id` | number | 否 | 画像记录 ID |
| `userId` | number | 是 | 用户 ID |
| `maskingId` | string | 否 | 脱敏 ID |
| `gpa` | number | 否 | 当前 GPA |
| `requiredGpa` | number | 否 | 要求 GPA |
| `failedCoursesCnt` | number | 否 | 挂科数量 |
| `psychologicalTag` | string | 否 | 心理标签 |
| `riskLevel` | number | 否 | `0` 无风险，`1` 橙色预警，`2` 红色预警 |
| `counselor` | string | 否 | 辅导员/素质教师姓名 |

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "画像数据更新成功"
}
```

### 7.3 生成帮扶成效 PDF 报告

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.AcademicSupportController.generatePdfReport` |
| 方法注释 | 辅导员/管理员一键生成帮扶成效报告 PDF |
| 请求方式 | `POST` |
| 请求路径 | `/api/academic/generate-pdf-report` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `recordId` | requestParam | number | 是 | 学业预警记录 ID |

返回值含义：PDF 文件访问地址。

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": "/reports/academic-warning-1.pdf"
}
```

## 8. 微信公众号

### 8.1 微信服务器认证

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.WechatController.authGet` |
| 方法注释 | 微信认证开发者服务器 |
| 请求方式 | `GET` |
| 请求路径 | `/api/wechat/portal` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `signature` | query | string | 否 | 微信签名 |
| `timestamp` | query | string | 否 | 时间戳 |
| `nonce` | query | string | 否 | 随机数 |
| `echostr` | query | string | 否 | 微信回显字符串 |

返回值含义：认证成功返回 `echostr`，失败返回 `非法请求`。

返回值结构：

```text
echostr
```

### 8.2 接收微信公众号消息或事件

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.WechatController.post` |
| 方法注释 | 接收微信公众号用户的消息/事件，路由后返回 XML 回复 |
| 请求方式 | `POST` |
| 请求路径 | `/api/wechat/portal` |
| Content-Type | `application/xml` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `requestBody` | body | string | 是 | 微信 XML 请求体 |
| `signature` | query | string | 是 | 微信签名 |
| `timestamp` | query | string | 是 | 时间戳 |
| `nonce` | query | string | 是 | 随机数 |
| `openid` | query | string | 是 | 微信 OpenID |
| `encrypt_type` | query | string | 否 | 加密类型，`aes` 表示加密消息 |
| `msg_signature` | query | string | 否 | 加密消息签名 |

返回值含义：微信 XML 回复字符串。

返回值结构：

```xml
<xml>...</xml>
```

### 8.3 微信免密登录

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.controller.WechatController.login` |
| 方法注释 | 前端通过微信授权 code 换取系统 JWT |
| 请求方式 | `GET` |
| 请求路径 | `/api/wechat/login` |

入参含义：

| 参数 | 位置 | 类型 | 必填 | 含义 |
|---|---|---|---|---|
| `code` | query | string | 是 | 微信 OAuth2 授权 code |

返回值结构：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "token": "jwt-token"
  }
}
```

## 9. WebSocket

### 9.1 聊天即时推送连接

| 项 | 内容 |
|---|---|
| 全路径类名+方法名 | `me.rainhouse.qasystem.websocket.ChatWebSocketServer.onOpen` |
| 方法注释 | 建立用户或客服 WebSocket 连接，用于后端主动推送人工客服消息、任务通知和系统通知 |
| 连接路径 | `/ws/chat/{role}/{userId}` |

路径参数：

| 参数 | 类型 | 必填 | 含义 |
|---|---|---|---|
| `role` | string | 是 | `admin` 表示客服端；其他值视为用户端 |
| `userId` | string | 是 | 用户 ID 或客服 ID |

服务端下行消息示例：

```json
{
  "type": "NEW_TASK",
  "sessionId": 1,
  "userId": 1001,
  "msg": "用户请求人工介入"
}
```

```json
{
  "type": "ADMIN_REPLY",
  "adminId": 2,
  "content": "您好，我来为您处理。"
}
```

```json
{
  "type": "SYSTEM_NOTICE",
  "content": "人工服务已结束，已为您重新交接给AI托管模式。"
}
```

