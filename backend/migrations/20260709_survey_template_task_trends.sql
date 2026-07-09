CREATE TABLE IF NOT EXISTS `survey_template` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '模板ID',
  `name` varchar(200) NOT NULL COMMENT '模板名称',
  `description` text COMMENT '模板说明',
  `file_name` varchar(255) DEFAULT NULL COMMENT '来源文件名',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问卷模板表';

CREATE TABLE IF NOT EXISTS `survey_template_question` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '模板题目ID',
  `template_id` bigint(20) NOT NULL COMMENT '模板ID',
  `question_no` int NOT NULL COMMENT '题目序号',
  `question_code` varchar(100) NOT NULL COMMENT '稳定题目编码',
  `indicator_name` varchar(200) DEFAULT NULL COMMENT '趋势指标名称',
  `question_text` text NOT NULL COMMENT '题干',
  `question_type` tinyint NOT NULL DEFAULT 1 COMMENT '题型：1量表题 2文本题',
  `required` tinyint NOT NULL DEFAULT 1 COMMENT '是否必填：0否 1是',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_question_no` (`template_id`,`question_no`),
  KEY `idx_template_question_code` (`template_id`,`question_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='问卷模板题目表';

-- Add these columns if they are missing. For MySQL versions without
-- ALTER TABLE ... ADD COLUMN IF NOT EXISTS, run the guarded statements
-- in deploy tooling or apply each ALTER only once.
ALTER TABLE `survey` ADD COLUMN `purpose` text COMMENT '问卷目的' AFTER `description`;
ALTER TABLE `survey` ADD COLUMN `subject` varchar(100) DEFAULT NULL COMMENT '科目' AFTER `title`;
ALTER TABLE `survey` ADD COLUMN `template_id` bigint(20) DEFAULT NULL COMMENT '模板ID' AFTER `status`;
ALTER TABLE `survey` ADD COLUMN `scope_text` varchar(500) DEFAULT NULL COMMENT '覆盖范围说明' AFTER `scope_type`;
ALTER TABLE `survey` ADD COLUMN `publisher_id` bigint(20) DEFAULT NULL COMMENT '发布人ID' AFTER `scope_text`;
ALTER TABLE `survey` ADD COLUMN `academic_year` int DEFAULT NULL COMMENT '学年起始年份' AFTER `publisher_id`;
ALTER TABLE `survey` ADD COLUMN `term_no` tinyint DEFAULT NULL COMMENT '学期：1-3' AFTER `academic_year`;
ALTER TABLE `survey` ADD COLUMN `start_time` datetime DEFAULT NULL COMMENT '开始时间' AFTER `published_at`;
ALTER TABLE `survey` ADD COLUMN `end_time` datetime DEFAULT NULL COMMENT '结束时间' AFTER `start_time`;

ALTER TABLE `survey_question` ADD COLUMN `template_question_id` bigint(20) DEFAULT NULL COMMENT '模板题目ID' AFTER `survey_id`;
ALTER TABLE `survey_question` ADD COLUMN `question_code` varchar(100) DEFAULT NULL COMMENT '稳定题目编码' AFTER `question_no`;
ALTER TABLE `survey_question` ADD COLUMN `indicator_name` varchar(200) DEFAULT NULL COMMENT '趋势指标名称' AFTER `question_code`;

ALTER TABLE `survey_answer` ADD COLUMN `question_code` varchar(100) DEFAULT NULL COMMENT '稳定题目编码' AFTER `user_id`;
ALTER TABLE `survey_answer` ADD COLUMN `indicator_name` varchar(200) DEFAULT NULL COMMENT '趋势指标名称' AFTER `question_code`;

UPDATE `survey_question`
SET `question_code` = CONCAT('q_', CRC32(`question_text`)),
    `indicator_name` = LEFT(`question_text`, 100)
WHERE `question_code` IS NULL OR `question_code` = '';

UPDATE `survey_answer` a
JOIN `survey_question` q ON q.id = a.question_id
SET a.`question_code` = q.`question_code`,
    a.`indicator_name` = q.`indicator_name`
WHERE a.`question_code` IS NULL OR a.`question_code` = '';
