package me.rainhouse.qasystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.models.deepseek")
public class DeepSeekModelProperties {
    private boolean enabled;
    private String apiKey;
    private String baseUrl = "https://api.deepseek.com/chat/completions";
    private String model = "deepseek-reasoner";
    private Integer timeoutMs = 120000;
    private Integer maxTokens = 1200;
}
