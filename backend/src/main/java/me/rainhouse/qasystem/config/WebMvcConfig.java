package me.rainhouse.qasystem.config;

import me.rainhouse.qasystem.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(redisTemplate))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login", 
                        "/api/auth/sso/callback",
                        "/api/wechat/portal",   // 接收微信事件
                        "/api/wechat/login"     // 微信免密授权
                ); 
    }
}
