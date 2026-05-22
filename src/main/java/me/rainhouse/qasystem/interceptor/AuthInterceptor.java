package me.rainhouse.qasystem.interceptor;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.rainhouse.qasystem.common.utils.JwtUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redisTemplate;

    public AuthInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行OPTIONS预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
            
            // 防护检测：检查是否已被登出而拉入Redis黑名单
            if (Boolean.TRUE.equals(redisTemplate.hasKey("auth:blacklist:" + token))) {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\":401,\"message\":\"登录已失效，请重新登录\"}");
                return false;
            }

            Claims claims = JwtUtils.parseToken(token);
            if (claims != null) {
                // 将信息存入request供Controller使用
                request.setAttribute("userId", Long.valueOf(claims.getSubject()));
                request.setAttribute("role", claims.get("role", Integer.class));
                return true;
            }
        }

        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write("{\"code\":401,\"message\":\"登录已过期或未授权，请重新登录\"}");
        return false;
    }
}
