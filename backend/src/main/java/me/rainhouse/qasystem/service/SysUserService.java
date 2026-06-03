package me.rainhouse.qasystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.rainhouse.qasystem.entity.SysUser;

import java.util.Map;

public interface SysUserService extends IService<SysUser> {
    /**
     * 账号密码登录
     */
    Map<String, Object> login(String username, String password);
    
    /**
     * 大连东软一网通SSO登录回调
     * @param ssoToken 一网通的认证Token
     * @return 系统的JWT Token
     */
    String ssoLogin(String ssoToken);
}
