package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("question_hit_record")
public class QuestionHitRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long sessionId;
    private String originalQuestion;
    private String rewriteQuestion;
    private String moduleType;
    private BigDecimal topScore;
    private Integer hitStatus; // 0未命中 1弱命中 2强命中
    private Long knowledgeId;
    private Long responseTimeMs;
    private LocalDateTime createdAt;
}
