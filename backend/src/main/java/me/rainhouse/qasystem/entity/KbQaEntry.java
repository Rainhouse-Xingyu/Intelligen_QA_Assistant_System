package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_qa_entry")
public class KbQaEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long documentId;
    private String question;
    private String answer;
    private Integer status; // 0-禁用, 1-启用
    private String moduleType;
    private String sourceType;
    private String createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
