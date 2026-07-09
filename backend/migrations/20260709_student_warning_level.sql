CREATE TABLE IF NOT EXISTS `student_warning_level` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `student_no` varchar(50) NOT NULL COMMENT '学号',
  `class_name` varchar(100) DEFAULT NULL COMMENT '班级',
  `student_name` varchar(50) NOT NULL COMMENT '学生姓名',
  `warning_level` varchar(20) NOT NULL COMMENT '预警等级：正常/黄色预警/橙色预警/红色预警',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_student_no` (`student_no`),
  KEY `idx_class_name` (`class_name`),
  KEY `idx_warning_level` (`warning_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学生预警等级表';
