CREATE TABLE IF NOT EXISTS `hot_question_config` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `question_text` varchar(500) NOT NULL COMMENT '手选热门问题',
  `answer_text` text NOT NULL COMMENT '直接回答内容',
  `module_type` varchar(50) DEFAULT NULL COMMENT '所属模块',
  `sort_order` int DEFAULT 0 COMMENT '排序值',
  `valid_until` datetime DEFAULT NULL COMMENT '有效截止时间，过期后不再优先展示',
  `enabled` tinyint DEFAULT 1 COMMENT '是否启用：0否 1是',
  `created_by` bigint(20) DEFAULT NULL COMMENT '创建人ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_enabled_valid_until` (`enabled`, `valid_until`),
  KEY `idx_sort_order` (`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员手选热门问题配置表';
