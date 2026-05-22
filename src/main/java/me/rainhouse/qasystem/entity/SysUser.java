package me.rainhouse.qasystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    // 登录账号(系统内流转用)
    private String username;
    
    // 密码(SSO登录时可为空)
    private String password;
    
    // 真实姓名
    private String realName;
    
    // 角色: 1-学生, 2-教师, 3-管理员
    private Integer role;
    
    // 联系电话
    private String phone;
    
    // 大连东软信息学院一网通SSO唯一标识
    private String campusSsoId;
    
    // 微信公众号开发OpenID
    private String wechatOpenid;
    
    // 专属形象/头像预留
    private String avatarUrl;
    
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
}
