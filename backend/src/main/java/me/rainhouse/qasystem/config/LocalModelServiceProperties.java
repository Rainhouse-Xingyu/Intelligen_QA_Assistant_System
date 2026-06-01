package me.rainhouse.qasystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.models.local-service")
public class LocalModelServiceProperties {
    private boolean enabled = true;
    private String baseUrl = "http://127.0.0.1:18080";
    private int timeoutMs = 600000;
}
