-- Knowledge-base category tree, answer template, source references, and FAQ v2 columns.
-- Run this on an existing development database before rebuilding the Milvus index.

CREATE TABLE IF NOT EXISTS `kb_category` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `parent_id` bigint(20) DEFAULT NULL COMMENT '父分类ID',
  `name` varchar(100) NOT NULL COMMENT '分类名称',
  `level` tinyint(2) NOT NULL COMMENT '分类层级: 1一级 2二级 3三级',
  `sort_order` int DEFAULT '0' COMMENT '排序值',
  `status` tinyint(2) DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_parent_name` (`parent_id`, `name`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库分类树表';

CREATE TABLE IF NOT EXISTS `kb_answer_template` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `template_code` varchar(50) NOT NULL COMMENT '模板编码',
  `template_name` varchar(100) DEFAULT NULL COMMENT '模板名称',
  `template_content` text NOT NULL COMMENT '模板内容，使用 <答案> 作为占位符',
  `status` tinyint(2) DEFAULT '1' COMMENT '状态: 0禁用 1启用',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_code` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库回答模板表';

CREATE TABLE IF NOT EXISTS `kb_source_reference` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(200) DEFAULT NULL COMMENT '来源标题',
  `url` varchar(500) NOT NULL COMMENT '来源网址',
  `source_type` varchar(30) DEFAULT 'official_site' COMMENT '来源类型',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_url` (`url`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库来源链接表';

CREATE TABLE IF NOT EXISTS `kb_qa_source_ref` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `qa_entry_id` bigint(20) NOT NULL COMMENT 'FAQ词条ID',
  `source_id` bigint(20) NOT NULL COMMENT '来源链接ID',
  `sort_order` int DEFAULT '0' COMMENT '排序值',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_qa_source` (`qa_entry_id`, `source_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='FAQ与来源链接关联表';

ALTER TABLE `kb_qa_entry`
  ADD COLUMN `category_id` bigint(20) DEFAULT NULL COMMENT '三级分类ID(kb_category.id)' AFTER `document_id`,
  ADD COLUMN `category_l1_id` bigint(20) DEFAULT NULL COMMENT '一级分类ID' AFTER `category_id`,
  ADD COLUMN `category_l2_id` bigint(20) DEFAULT NULL COMMENT '二级分类ID' AFTER `category_l1_id`,
  ADD COLUMN `category_l3_id` bigint(20) DEFAULT NULL COMMENT '三级分类ID' AFTER `category_l2_id`,
  ADD COLUMN `category_l1_name` varchar(100) DEFAULT NULL COMMENT '一级分类名称快照' AFTER `category_l3_id`,
  ADD COLUMN `category_l2_name` varchar(100) DEFAULT NULL COMMENT '二级分类名称快照' AFTER `category_l1_name`,
  ADD COLUMN `category_l3_name` varchar(100) DEFAULT NULL COMMENT '三级分类名称快照' AFTER `category_l2_name`,
  ADD COLUMN `template_id` bigint(20) DEFAULT NULL COMMENT '回答模板ID' AFTER `answer`,
  ADD COLUMN `template_code` varchar(50) DEFAULT NULL COMMENT '回答模板编码' AFTER `template_id`,
  ADD COLUMN `source_title` varchar(200) DEFAULT NULL COMMENT '来源标题快照' AFTER `source_type`,
  ADD COLUMN `source_url` varchar(500) DEFAULT NULL COMMENT '来源网址快照' AFTER `source_title`;

ALTER TABLE `kb_qa_entry`
  ADD KEY `idx_category_id` (`category_id`),
  ADD KEY `idx_category_path` (`category_l1_id`, `category_l2_id`, `category_l3_id`);
