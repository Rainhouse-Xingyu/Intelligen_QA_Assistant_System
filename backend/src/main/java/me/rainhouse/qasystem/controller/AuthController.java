package me.rainhouse.qasystem.controller;

import me.rainhouse.qasystem.common.result.Result;
import me.rainhouse.qasystem.entity.SysUser;
import me.rainhouse.qasystem.service.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private SysUserService sysUserService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @PostMapping("/login")
    public Result<Map<String, String>> login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");
        try {
            String token = sysUserService.login(username, password);
            Map<String, String> data = new HashMap<>();
            data.put("token", token);
            return Result.success(data);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 大连东软信息学院一网通登录回调
     */
    @PostMapping("/sso/callback")
    public Result<Map<String, String>> ssoCallback(@RequestBody Map<String, String> params) {
        String ssoToken = params.get("ssoToken");
        try {
            String sysToken = sysUserService.ssoLogin(ssoToken);
            Map<String, String> data = new HashMap<>();
            data.put("token", sysToken);
            return Result.success(data);
        } catch (Exception e) {
            return Result.error("一网通登录失败: " + e.getMessage());
        }
    }

    @GetMapping("/info")
    public Result<SysUser> getUserInfo(@RequestAttribute("userId") Long userId) {
        SysUser user = sysUserService.getById(userId);
        if (user != null) {
            user.setPassword(null); // 安全脱敏
            return Result.success(user);
        }
        return Result.error("用户信息不存在");
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            // 登出时将Token加入黑名单，防止其在有效期内被恶意重放（过期时间与 JWT 一致即可，如24小时）
            redisTemplate.opsForValue().set("auth:blacklist:" + token, "1", 24, TimeUnit.HOURS);
        }
        return Result.success(null);
    }
}
