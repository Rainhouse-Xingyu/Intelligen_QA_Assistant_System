# 智能问答辅助系统 API 接口文档

**公共返回结构说明:**
系统统一使用 `Result<T>` 泛型作为返回值包装类：
```json
{
  "code": 200,      // 业务状态码 (200: 成功, 401: 未授权, 500: 服务器错误)
  "message": "操作成功", // 提示信息
  "data": {}        // 泛型数据实际内容
}
```

---

## 1. 认证模块 (Auth)

### 1.1 系统登录
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.AuthController.login`
- **请求方式与路径**: `POST /api/auth/login`
- **方法注释**: 基础账号密码登录。
- **入参含义**:
  - `params` (Map，RequestBody): 包含 `username` (用户名) 和 `password` (密码)。
- **返回值含义**: 返回登录成功后的JWT Token。
- **返回值结构**: `Result<Map<String, String>>`
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": { "token": "eyJhbGciOi..." }
  }
  ```

### 1.2 大连东软信息学院一网通登录回调
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.AuthController.ssoCallback`
- **请求方式与路径**: `POST /api/auth/sso/callback`
- **方法注释**: 大连东软信息学院一网通登录回调，换取系统Token。
- **入参含义**:
  - `params` (Map，RequestBody): 包含 `ssoToken` (前端获取到的SSO凭证)。
- **返回值含义**: 返回系统级JWT Token。
- **返回值结构**: `Result<Map<String, String>>`
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": { "token": "eyJhbGciOi..." }
  }
  ```

### 1.3 获取当前用户信息
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.AuthController.getUserInfo`
- **请求方式与路径**: `GET /api/auth/info`
- **方法注释**: 获取登录用户的基本信息。
- **入参含义**: 
  - `userId` (Long，从内置RequestAttribute拦截推出): 当前登录用户ID。
- **返回值含义**: 当前用户的账户信息（密码已脱敏）。
- **返回值结构**: `Result<SysUser>`
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": {
       "id": 1,
       "username": "tester",
       "role": "student"
    }
  }
  ```

### 1.4 用户登出
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.AuthController.logout`
- **请求方式与路径**: `POST /api/auth/logout`
- **方法注释**: 登出并注销当前Token。
- **入参含义**: 
  - `authHeader` (String，Header): `Authorization` 头部信息。
- **返回值含义**: 登出是否成功。
- **返回值结构**: `Result<Void>`
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": null
  }
  ```

---

## 2. 聊天与对话模块 (Chat)

### 2.1 发送文本消息
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.ChatController.sendText`
- **请求方式与路径**: `POST /api/chat/text`
- **方法注释**: 发送文本消息与扣子(Coze)对话。
- **入参含义**:
  - `query` (String，RequestParam): 用户提问的内容。
  - `needTts` (Boolean，RequestParam): 是否需要TTS语音转换（默认false）。
- **返回值含义**: 机器人的文本类型答复。
- **返回值结构**: `Result<String>`
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": "您好，这是我的回答..."
  }
  ```

### 2.2 发送语音消息
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.ChatController.sendVoice`
- **请求方式与路径**: `POST /api/chat/voice`
- **方法注释**: 发送语音消息。
- **入参含义**:
  - `audioFile` (MultipartFile，RequestParam): 录音文件资源。
- **返回值含义**: AI对该语语言内容的文字类型答复。
- **返回值结构**: `Result<String>`

### 2.3 心理咨询工作流调用
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.ChatController.psychologicalCounseling`
- **请求方式与路径**: `POST /api/chat/psychological`
- **方法注释**: 调用 Psychological_Counseling 工作流，接收 reply_text 与 stress_level 输出。
- **入参含义**:
  - `studentMsg` (String，RequestParam): 学生的反馈信息。
- **返回值含义**: 工作流处理后的心理安抚指导或话术回复。
- **返回值结构**: `Result<String>`

### 2.4 用户申请转人工客服
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.ChatController.transferToHuman`
- **请求方式与路径**: `POST /api/chat/transfer-to-human`
- **方法注释**: 【2.3模块】用户申请转人工客服。
- **入参含义**: 无（依赖上下文授权Request）。
- **返回值含义**: 状态提示（转接成功/处理中）。
- **返回值结构**: `Result<String>`

### 2.5 客服台人工回复
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.ChatController.adminReply`
- **请求方式与路径**: `POST /api/chat/admin/reply`
- **方法注释**: 【2.3模块】客服在控制台给用户发送回复。
- **入参含义**:
  - `sessionId` (Long, RequestParam): 聊天会话ID。
  - `content` (String, RequestParam): 答复文本。
- **返回值含义**: 答复下发状态。
- **返回值结构**: `Result<String>`

### 2.6 取消人工，交还AI
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.ChatController.adminFinish`
- **请求方式与路径**: `POST /api/chat/admin/finish`
- **方法注释**: 【2.3模块】客服结束会话，交还给AI。
- **入参含义**:
  - `sessionId` (Long, RequestParam): 聊天会话ID。
- **返回值含义**: 状态提示。
- **返回值结构**: `Result<String>`

### 2.7 获取初始推荐提问问题
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.ChatController.getSuggestedQuestions`
- **请求方式与路径**: `GET /api/chat/suggested-questions`
- **方法注释**: 适合在用户初次打开对话窗口时拉取，作为快速点击的 Suggestion 气泡。
- **入参含义**: 无。
- **返回值含义**: 返回近期（7天）热度排名前列的提问推荐。
- **返回值结构**: `Result<List<Map<String, Object>>>`

### 2.8 上报未识别兜底话术问题 (点踩/错漏报告)
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.ChatController.reportUnrecognized`
- **请求方式与路径**: `POST /api/chat/report-unrecognized`
- **方法注释**: 机器人回答 "我不知道" 之类的兜底话术后，给用户一个“点踩反馈/上报错漏”按钮。
- **入参含义**:
  - `query` (String, RequestParam): 触发不知道的用户提问内容。
- **返回值含义**: 反馈记录成功的提醒文字。
- **返回值结构**: `Result<String>`

---

## 3. 知识库管理模块 (KnowledgeBase)

### 3.1 上传并解析文件
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.KnowledgeBaseController.uploadDocument`
- **请求方式与路径**: `POST /api/kb/upload`
- **方法注释**: 【3.1模块】基于 Apache POI 上传并解析文件。
- **入参含义**:
  - `file` (MultipartFile, RequestParam): Excel知识文件（需为.xlsx类型）。
- **返回值含义**: 解析后落库的主文档记录信息。
- **返回值结构**: `Result<KbDocument>`

### 3.2 查询所有已落库的问答词条
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.KnowledgeBaseController.listEntries`
- **请求方式与路径**: `GET /api/kb/entries`
- **方法注释**: 【3.2模块】查询所有已落库的问答词条。
- **入参含义**: 无。
- **返回值含义**: 查询到的QA（问答）词条列。
- **返回值结构**: `Result<List<KbQaEntry>>`

### 3.3 手动逐条新增知识库问答
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.KnowledgeBaseController.createEntry`
- **请求方式与路径**: `POST /api/kb/entries`
- **方法注释**: 【3.2模块】手动逐条新增知识库问答。
- **入参含义**:
  - `kbQaEntry` (KbQaEntry, RequestBody): 预新增的问答对象 (含Question和Answer)。
- **返回值含义**: 新增结果。
- **返回值结构**: `Result<String>`

---

## 4. 管理员数据及未识别查询 (Admin / Data)

### 4.1 获取热点提问分析数据
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.DataStatController.getHotQuestions`
- **请求方式与路径**: `GET /api/stat/hot-questions`
- **方法注释**: 【4.1 核心功能】获取热点咨询问题，供前端 ECharts (如词云数据) 接入。
- **入参含义**:
  - `days` (int, RequestParam): 过去天数（默认7）。
  - `limit` (int, RequestParam): 拉取上限数量（默认20）。
- **返回值含义**: 标签(热词)及其发生频率记录，用于展示可视化热图。
- **返回值结构**: `Result<List<Map<String, Object>>>`

### 4.2 未识别问题列表查询
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.UnrecognizedQueryController.list`
- **请求方式与路径**: `GET /api/admin/unrecognized/list`
- **方法注释**: 【4.3 模块】面向管理员的未识别问题列表分页。
- **入参含义**:
  - `current` (Integer, RequestParam): 当前页数（默认1）。
  - `size` (Integer, RequestParam): 每页记录数（默认10）。
  - `status` (Integer, RequestParam): 处理状态（可选）。
- **返回值含义**: 带分页的未识别问题列表。
- **返回值结构**: `Result<Page<UnrecognizedQuery>>`

### 4.3 未识别问题状态处理更新
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.UnrecognizedQueryController.updateStatus`
- **请求方式与路径**: `POST /api/admin/unrecognized/update-status`
- **方法注释**: 【4.3 模块】面向管理员：处理/更新问题状态。
- **入参含义**:
  - `id` (Long, RequestParam): 未识别记录ID。
  - `status` (Integer, RequestParam): 修改后的目标状态。
- **返回值含义**: 状态更新是否成功。
- **返回值结构**: `Result<String>`

---

## 5. 学业帮扶模块 (Academic Support)

### 5.1 生成学业帮扶策略记录
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.AcademicSupportController.generateWarning`
- **请求方式与路径**: `POST /api/academic/generate-warning`
- **方法注释**: 【5.1模块】教务老师/辅导员触发：要求AI诊断某个学生的学业并生成帮扶策略。
- **入参含义**:
  - `studentId` (Long, RequestParam): 学生ID。
  - `term` (String, RequestParam): 学期范围。
- **返回值含义**: 生成的学业预警干预记录信息与策略结果。
- **返回值结构**: `Result<AcademicWarningRecord>`

### 5.2 更新学生画像与学业成绩
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.AcademicSupportController.updateProfile`
- **请求方式与路径**: `POST /api/academic/profile/update`
- **方法注释**: 【辅助接口】初始化灌入挂科和GPA数据。
- **入参含义**:
  - `profile` (StudentProfile, RequestBody): 学生画像成绩等数据集合。
- **返回值含义**: 导入成功状态。
- **返回值结构**: `Result<String>`

### 5.3 导出/生成帮扶报告 PDF
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.AcademicSupportController.generatePdfReport`
- **请求方式与路径**: `POST /api/academic/generate-pdf-report`
- **方法注释**: 【5.3模块】辅导员/管理员一键生成帮扶成效报告PDF。
- **入参含义**:
  - `recordId` (Long, RequestParam): 评估干预阶段生成的记录ID。
- **返回值含义**: 生成的PDF资源可下载URL地址。
- **返回值结构**: `Result<String>`

---

## 6. 微信公众号模块 (Wechat)

### 6.1 微信公众号开发者认证
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.WechatController.authGet`
- **请求方式与路径**: `GET /api/wechat/portal`
- **方法注释**: 微信认证开发者服务器验证签名配置。
- **入参含义**:
  - `signature`, `timestamp`, `nonce`, `echostr` (均 String，RequestParam)。
- **返回值含义**: 鉴权通过后的 `echostr` 原样回传给微信服务器。
- **返回值结构**: `String` (文本直接打出)

### 6.2 微信公众号消息事件接收
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.WechatController.post`
- **请求方式与路径**: `POST /api/wechat/portal`
- **方法注释**: 接收微信公众号用户的消息或推送事件（关注、取消等）。
- **入参含义**:
  - `requestBody` (String, RequestBody): XML正文消息。
  - 微信通用验证参数 `signature`, `timestamp`, `nonce`, `openid`。
- **返回值含义**: 系统异步处理消息后的确认回复或自动响应XML。
- **返回值结构**: `String` (纯XML文本结构)

### 6.3 微信公众号前端OAuth登录回调
- **全路径类名+方法名**: `me.rainhouse.qasystem.controller.WechatController.login`
- **请求方式与路径**: `GET /api/wechat/login`
- **方法注释**: 前端通过微信授权带上 code 访问此接口换取系统的 Token。
- **入参含义**:
  - `code` (String, RequestParam): 用户OAuth同意授权后的微信code凭据。
- **返回值含义**: 颁发的包含系统权限及用户信息的系统JWT Token。
- **返回值结构**: `Result<Map<String, String>>`
  ```json
  {
    "code": 200,
    "message": "操作成功",
    "data": { "token": "..." }
  }
  ```