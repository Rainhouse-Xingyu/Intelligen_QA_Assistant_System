package me.rainhouse.qasystem.config;

import me.rainhouse.qasystem.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${volcengine.speech.tts.output-dir:uploads/audio}")
    private String audioOutputDir;

    @Value("${volcengine.speech.tts.public-path:/media/audio}")
    private String audioPublicPath;

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

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String pattern = audioPublicPath.endsWith("/**") ? audioPublicPath : audioPublicPath + "/**";
        String location = audioOutputDir.startsWith("file:")
                ? audioOutputDir
                : Path.of(audioOutputDir).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(pattern).addResourceLocations(location);
    }
}
