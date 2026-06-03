package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.rainhouse.qasystem.common.utils.JwtUtils;
import me.rainhouse.qasystem.entity.SysUser;
import me.rainhouse.qasystem.mapper.SysUserMapper;
import me.rainhouse.qasystem.service.CampusSsoService;
import me.rainhouse.qasystem.service.SysUserService;
import me.rainhouse.qasystem.common.dto.CampusSsoUserDTO;
import me.rainhouse.qasystem.common.utils.PasswordUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CampusSsoService campusSsoService;

    @Override
    public Map<String, Object> login(String username, String password) {
        String failKey = "auth:login_fail:" + username;
        String failCountStr = redisTemplate.opsForValue().get(failKey);
        int failCount = failCountStr != null ? Integer.parseInt(failCountStr) : 0;
        
        // 限制15分钟内最多试错5次
        if (failCount >= 5) {
            throw new RuntimeException("连续密码错误次数过多，账号已被锁定15分钟");
        }

        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        SysUser user = getOne(queryWrapper);
        
        boolean isFirstLogin = false;
        boolean isPasswordMatch = false;

        if (user != null) {
            // 首先判断是否为已加密密码
            String encryptedInput = PasswordUtils.encrypt(password);
            if (encryptedInput.equals(user.getPassword())) {
                isPasswordMatch = true;
            } else if (password.equals(user.getPassword())) {
                // 如果未匹配加密，但匹配明文（说明是学校导入初始密码/首次登录）
                isPasswordMatch = true;
                isFirstLogin = true;
            }
        }
        
        if (!isPasswordMatch) {
            // 记录失败次数并设置过期时间
            redisTemplate.opsForValue().increment(failKey);
            if (failCount == 0) {
                redisTemplate.expire(failKey, 15, TimeUnit.MINUTES);
            }
            throw new RuntimeException("用户名或密码错误，连续错误5次将被锁定");
        }
        
        // 登录成功，清除错误记录
        redisTemplate.delete(failKey);
        
        String token = JwtUtils.generateToken(user.getId(), user.getRole());
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("needChangePassword", isFirstLogin);
        return result;
    }

    @Override
    public String ssoLogin(String ssoToken) {
        // 调用大连东软信息学院一网通验证接口，解析为传输实体
        CampusSsoUserDTO ssoUser = campusSsoService.verifyToken(ssoToken);
        
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("campus_sso_id", ssoUser.getCampusSsoId());
        SysUser user = getOne(queryWrapper);
        
        if (user == null) {
            // 果是一网通首次登录，根据返回的信息自动注册映射到系统
            user = new SysUser();
            // 如果学校返回了真实的学号即写入，否则使用随机分配
            user.setUsername(ssoUser.getUsername() != null ? ssoUser.getUsername() : ("u_" + System.currentTimeMillis()));
            user.setRealName(ssoUser.getRealName());
            user.setCampusSsoId(ssoUser.getCampusSsoId());
            
            // 简单角色映射："TEACHER" 判定为 2-教师，否则默认为 1-学生
            if ("TEACHER".equalsIgnoreCase(ssoUser.getIdentityType())) {
                user.setRole(2);
            } else {
                user.setRole(1);
            }
            
            user.setCreatedAt(LocalDateTime.now());
            user.setUpdatedAt(LocalDateTime.now());
            save(user);
        }
        
        // 签发本系统的无状态凭证
        return JwtUtils.generateToken(user.getId(), user.getRole());
    }
}
