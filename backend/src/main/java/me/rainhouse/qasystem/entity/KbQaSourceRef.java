package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("kb_qa_source_ref")
public class KbQaSourceRef {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long qaEntryId;
    private Long sourceId;
    private Integer sortOrder;
}
