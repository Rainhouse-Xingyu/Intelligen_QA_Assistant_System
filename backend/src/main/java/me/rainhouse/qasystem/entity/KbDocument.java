package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_document")
public class KbDocument {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String fileName;
    private String fileUrl;
    private String uploaderId;
    private Integer processStatus; // 0-待解析, 1-解析中, 2-成功, 3-失败
    private String processMessage;
    private LocalDateTime createdAt;
}
