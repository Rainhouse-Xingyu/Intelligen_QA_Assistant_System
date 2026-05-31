package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("unrecognized_query")
public class UnrecognizedQuery {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String questionText;
    private String moduleType;
    private BigDecimal topScore;
    private Integer frequency;
    private Integer status;
    private Long processUser;
    private LocalDateTime processTime;
    private LocalDateTime createTime;
}
