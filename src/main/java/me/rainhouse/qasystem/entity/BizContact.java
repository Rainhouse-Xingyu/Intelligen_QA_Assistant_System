package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("biz_contact")
public class BizContact {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String bizModule; // 业务模块(如：选课、重修)
    private String teacherName;
    private String phoneNumber;
    private LocalDateTime createdAt;
}