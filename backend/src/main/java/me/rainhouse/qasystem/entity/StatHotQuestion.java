package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("stat_hot_question")
public class StatHotQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String questionText;
    private Integer frequency;
    private LocalDate statDate;
}