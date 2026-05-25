package me.rainhouse.qasystem.common.dto;

import lombok.Data;

/**
 * 校园一网通返回的用户信息传输对象
 */
@Data
public class CampusSsoUserDTO {
    // 校园唯一标识
    private String campusSsoId;
    
    // 真实姓名
    private String realName;
    
    // 学号/工号
    private String username;
    
    // 身份类型 (例如区分是教职工还是学生)
    private String identityType;
    
    // 手机号 (如果有)
    private String phone;
}
