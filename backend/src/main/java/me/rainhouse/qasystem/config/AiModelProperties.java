package me.rainhouse.qasystem.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "ai.models")
public class AiModelProperties {

    private String basePath;
    private String qwenCleanerPath;
    private String qwenGeneratorPath;
    private String embeddingPath;
    private String rerankerPath;
    private String intentClassifierPath;
}
