package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("hot_question_config")
public class HotQuestionConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String questionText;
    private String answerText;
    private String moduleType;
    private Integer sortOrder;
    private LocalDateTime validUntil;
    private Integer enabled;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
