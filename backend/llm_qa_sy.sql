-- ==========================================================
-- 数据库：llm_qa_sy (智能问答助手系统)
-- 包含：用户与权限、智能问答与客服、知识库、统计分析、学业帮扶
-- ==========================================================

CREATE DATABASE IF NOT EXISTS `llm_qa_sy` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `llm_qa_sy`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. 用户与权限模块
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '登录账号(系统内流转用)',
  `password` varchar(100) DEFAULT NULL COMMENT '密码(SSO登录时可为空)',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `role` tinyint(4) NOT NULL DEFAULT '1' COMMENT '角色: 1-学生, 2-教师, 3-管理员',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `campus_sso_id` varchar(100) DEFAULT NULL COMMENT '大连东软信息学院一网通SSO唯一标识',
  `wechat_openid` varchar(100) DEFAULT NULL COMMENT '微信公众号开发OpenID',
  `avatar_url` varchar(255) DEFAULT NULL COMMENT '专属形象/头像预留',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_campus_sso_id` (`campus_sso_id`),
  UNIQUE KEY `uk_wechat_openid` (`wechat_openid`),
  KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ----------------------------
-- 2. 智能问答模块 (对话历史与人工客服)
-- ----------------------------
DROP TABLE IF EXISTS `chat_session`;
CREATE TABLE `chat_session` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '提问用户ID',
  `status` tinyint(2) DEFAULT '0' COMMENT '会话状态: 0-AI托管, 1-转人工, 2-已结束',
  `admin_id` bigint(20) DEFAULT NULL COMMENT '接单客服/管理员ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '会话开始时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后交互时间',
  `answer_source` varchar(20) DEFAULT 'AI' COMMENT '答案来源: FAQ/RAG/Coze/人工',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `session_id` bigint(20) NOT NULL COMMENT '所属会话ID',
  `sender_type` tinyint(2) NOT NULL COMMENT '发送方: 1-用户, 2-AI, 3-人工客服',
  `msg_type` tinyint(2) NOT NULL DEFAULT '1' COMMENT '消息类型: 1-文本, 2-语音',
  `content` text COMMENT '文本内容',
  `media_url` varchar(255) DEFAULT NULL COMMENT '语音或富媒体文件链接',
  `intent_tag` varchar(50) DEFAULT NULL COMMENT 'AI识别的意图/分类标签',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天消息记录表';

DROP TABLE IF EXISTS `biz_contact`;
CREATE TABLE `biz_contact` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `biz_module` varchar(50) NOT NULL COMMENT '业务模块(如：选课、重修)',
  `teacher_name` varchar(50) NOT NULL COMMENT '负责教师姓名',
  `phone_number` varchar(20) NOT NULL COMMENT '办公座机',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务线联系方式表(AI推送用)';

-- ----------------------------
-- 3. 知识库管理模块
-- ----------------------------
DROP TABLE IF EXISTS `kb_document`;
CREATE TABLE `kb_document` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `file_name` varchar(100) NOT NULL COMMENT '原始文件名(Excel/Word)',
  `file_url` varchar(255) DEFAULT NULL COMMENT '文件存储地址',
  `uploader_id` bigint(20) NOT NULL COMMENT '上传者(管理员)ID',
  `process_status` tinyint(2) DEFAULT '0' COMMENT '解析状态: 0-待解析, 1-解析中, 2-成功, 3-失败',
  `process_message` varchar(500) DEFAULT NULL COMMENT '解析结果说明',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库源文档表';

DROP TABLE IF EXISTS `kb_qa_entry`;
CREATE TABLE `kb_qa_entry` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `document_id` bigint(20) DEFAULT NULL COMMENT '来源文档ID(若手动添加则为空)',
  `question` varchar(500) NOT NULL COMMENT '标准问题',
  `answer` text NOT NULL COMMENT '标准答案',
  `status` tinyint(2) DEFAULT '1' COMMENT '状态: 0-禁用, 1-启用',
  `module_type` varchar(50) DEFAULT NULL COMMENT '所属模块(考务通知/教学运行/学业帮扶/心理辅导)',
  `source_type` varchar(20) DEFAULT 'manual' COMMENT '来源类型: FAQ/Word/Excel/人工录入/AI生成',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_question` (`question`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库问答词条表';

-- ----------------------------
-- 4. 数据统计与推送模块
-- ----------------------------
DROP TABLE IF EXISTS `stat_hot_question`;
CREATE TABLE `stat_hot_question` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `question_text` varchar(500) NOT NULL COMMENT '标准问题',
  `answer_text` text COMMENT '标准答案',
  `module_type` varchar(50) DEFAULT NULL COMMENT '一级分类(考务通知/教学运行/学业帮扶)',
  `category_l2` varchar(50) DEFAULT NULL COMMENT '二级分类',
  `category_l3` varchar(50) DEFAULT NULL COMMENT '三级分类',
  `frequency` int(11) DEFAULT '1' COMMENT '提问次数',
  `last_hit_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '最后命中时间',
  `stat_date` date NOT NULL COMMENT '统计日期',
  PRIMARY KEY (`id`),
  KEY `idx_stat_date` (`stat_date`),
  KEY `idx_frequency` (`frequency`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热点问题统计表';

DROP TABLE IF EXISTS `unrecognized_query`;
CREATE TABLE unrecognized_query (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    question_text TEXT COMMENT '未命中的问题',
    module_type VARCHAR(50) COMMENT '所属模块',
    top_score DECIMAL(5,4) COMMENT '最高匹配分数',
    frequency INT DEFAULT 1 COMMENT '出现次数',
    status TINYINT DEFAULT 0 COMMENT '0未处理 1已处理',
    process_user BIGINT COMMENT '处理管理员',
    process_time DATETIME COMMENT '处理时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------
-- 5. 学业帮扶模块
-- ----------------------------
DROP TABLE IF EXISTS `student_profile`;
CREATE TABLE `student_profile` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '外键：系统用户表ID(学生)',
  `masking_id` varchar(50) NOT NULL COMMENT '脱敏ID(传给AI时使用代替真实学号)',
  `gpa` decimal(4,2) DEFAULT NULL COMMENT '学分绩点',
  `required_gpa` decimal(4,2) DEFAULT NULL COMMENT '学分绩点',
  `failed_courses_cnt` int(11) DEFAULT '0' COMMENT '挂科数',
  `psychological_tag` varchar(100) DEFAULT NULL COMMENT '心理/性格标签(评估后生成)',
  `risk_level` tinyint(2) DEFAULT '0' COMMENT '风险等级: 0-无风险, 1-橙色预警, 2-红色预警',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '信息同步时间',
  `counselor` varchar(50) DEFAULT NULL COMMENT '素质教师姓名',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`),
  UNIQUE KEY `uk_masking_id` (`masking_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生画像(学业与心理)基础表';

DROP TABLE IF EXISTS `academic_warning_record`;
CREATE TABLE `academic_warning_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `student_id` bigint(20) NOT NULL COMMENT '学生用户ID',
  `term` varchar(20) NOT NULL COMMENT '学期(如: 2026-春)',
  `warning_reason` text COMMENT '预警分析(大模型生成或规则生成)',
  `ai_suggested_plan` text COMMENT 'AI生成的帮扶方案',
  `report_pdf_url` varchar(255) DEFAULT NULL COMMENT '帮扶成效报告PDF存档链接',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录生成时间',
  `created_user` varchar(50) DEFAULT NULL COMMENT '记录生成人',
  PRIMARY KEY (`id`),
  KEY `idx_student_term` (`student_id`,`term`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学业预警与帮扶记录表';

-- ----------------------------
-- 6. 核心业务视图与其他强化索引约束
-- ----------------------------
DROP VIEW IF EXISTS `v_student_academic_profile`;
CREATE VIEW `v_student_academic_profile` AS
SELECT 
    u.id AS user_id,
    u.username AS student_no,
    u.real_name,
    u.campus_sso_id,
    p.masking_id,
    p.gpa,
    p.failed_courses_cnt,
    p.risk_level,
    p.psychological_tag
FROM sys_user u
JOIN student_profile p ON u.id = p.user_id
WHERE u.role = 1;

DROP TABLE IF EXISTS `student_growth_archive`;
CREATE TABLE `student_growth_archive` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键，自动递增',
  `real_student_id` varchar(50) NOT NULL COMMENT '真实学号（供老师在管理后台检索筛选）',
  `masking_id` varchar(50) NOT NULL COMMENT '脱敏ID(传给AI时使用代替真实学号)',
  `warning_level` varchar(20) DEFAULT NULL COMMENT '对应的预警级别',
  `survey_indicator` varchar(255) DEFAULT NULL COMMENT '调查问卷暴露的各项指标变化',
  `help_plan` text COMMENT 'Coze大模型生成的个性化一对一帮扶方案',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '记录生成时间，方便以学期为单位导出成效报告',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生成长档案表';

SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------
-- 7. 原始问题表
-- ----------------------------
CREATE TABLE `question_raw` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20),
  `session_id` bigint(20),
  `original_question` text NOT NULL COMMENT '学生原始提问',
  `module_type` varchar(50) COMMENT '学生选择模块',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='原始问题记录表';

-- ----------------------------
-- 8. 问题命中记录表
-- ----------------------------
CREATE TABLE `question_hit_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `user_id` bigint(20),
  `session_id` bigint(20),
  `original_question` text,
  `rewrite_question` text COMMENT '改写后的标准问题',
  `module_type` varchar(50) COMMENT '所属模块',
  `top_score` decimal(5,4) COMMENT '向量检索最高匹配分数',
  `hit_status` tinyint(2) DEFAULT 0 COMMENT '0未命中 1弱命中 2强命中',
  `knowledge_id` bigint(20) COMMENT '命中的知识库条目ID(kb_qa_entry.id)',
  `response_time_ms` bigint(20) COMMENT '响应时间(毫秒)',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG命中记录表';

-- ----------------------------
-- 9. 心理咨询模块
-- ----------------------------
CREATE TABLE psychological_chat_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL COMMENT '学生ID',
  session_id BIGINT COMMENT '关联会话',
  risk_level TINYINT DEFAULT 0
  COMMENT '0正常 1关注 2高风险',
  user_message TEXT
  COMMENT '学生输入',
  ai_response TEXT
  COMMENT 'AI回复',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- ----------------------------
-- 10. 问卷调查模块
-- ----------------------------
DROP TABLE IF EXISTS `survey_question`;
CREATE TABLE `survey_question` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `question_no` varchar(20) NOT NULL COMMENT '问题序号',
  `question_desc` text NOT NULL COMMENT '问题描述',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_question_no` (`question_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问卷调查问题表';

DROP TABLE IF EXISTS `student_survey_record`;
CREATE TABLE `student_survey_record` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `student_name` varchar(50) NOT NULL COMMENT '学生姓名',
  `class_name` varchar(100) DEFAULT NULL COMMENT '班级',
  `student_no` varchar(50) DEFAULT NULL COMMENT '学号(预留)',
  `question_no` varchar(20) NOT NULL COMMENT '问题序号',
  `answer` text COMMENT '答案',
  `answer_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '作答时间',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_student_name` (`student_name`),
  KEY `idx_class_name` (`class_name`),
  KEY `idx_student_no` (`student_no`),
  KEY `idx_question_no` (`question_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生学业问卷作答记录表';

DROP TABLE IF EXISTS `student_warning_level`;
CREATE TABLE `student_warning_level` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `student_name` varchar(50) NOT NULL COMMENT '学生姓名',
  `class_name` varchar(100) DEFAULT NULL COMMENT '班级',
  `student_no` varchar(50) DEFAULT NULL COMMENT '学号(预留)',
  `warning_level` varchar(20) NOT NULL COMMENT '预警等级',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_student_name` (`student_name`),
  KEY `idx_class_name` (`class_name`),
  KEY `idx_student_no` (`student_no`),
  KEY `idx_warning_level` (`warning_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生预警等级表';
