package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stat_hot_question")
public class StatHotQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String questionText;
    private String answerText;
    private String moduleType;
    private Integer frequency;
    private LocalDateTime lastHitTime;
    private LocalDate statDate;
}
