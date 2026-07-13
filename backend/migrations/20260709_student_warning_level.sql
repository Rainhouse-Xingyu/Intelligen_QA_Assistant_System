CREATE TABLE IF NOT EXISTS `student_warning_level` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `student_no` varchar(50) NOT NULL COMMENT '学号',
  `class_name` varchar(100) DEFAULT NULL COMMENT '班级',
  `student_name` varchar(50) NOT NULL COMMENT '学生姓名',
  `warning_level` varchar(20) NOT NULL COMMENT '预警等级：正常/黄色预警/橙色预警/红色预警',
  `warning_reason` varchar(500) DEFAULT NULL COMMENT '预警原因',
  `weakness_items` varchar(500) DEFAULT NULL COMMENT '主要弱项',
  `help_measures` varchar(1000) DEFAULT NULL COMMENT '建议帮扶措施',
  `counselor` varchar(50) DEFAULT NULL COMMENT '辅导员',
  `contact_phone` varchar(50) DEFAULT NULL COMMENT '联系电话',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_no` (`student_no`),
  KEY `idx_class_name` (`class_name`),
  KEY `idx_warning_level` (`warning_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生预警等级表';
