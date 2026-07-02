package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_source_reference")
public class KbSourceReference {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String url;
    private String sourceType;
    private LocalDateTime createdAt;
}
