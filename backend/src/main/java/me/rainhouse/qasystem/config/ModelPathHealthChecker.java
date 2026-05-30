package me.rainhouse.qasystem.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class ModelPathHealthChecker implements ApplicationRunner {

    private final AiModelProperties aiModelProperties;

    public ModelPathHealthChecker(AiModelProperties aiModelProperties) {
        this.aiModelProperties = aiModelProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, String> modelPaths = new LinkedHashMap<>();
        modelPaths.put("Qwen 清洗模型", aiModelProperties.getQwenCleanerPath());
        modelPaths.put("Qwen 生成模型", aiModelProperties.getQwenGeneratorPath());
        modelPaths.put("向量模型", aiModelProperties.getEmbeddingPath());
        modelPaths.put("重排模型", aiModelProperties.getRerankerPath());
        modelPaths.put("意图分类模型", aiModelProperties.getIntentClassifierPath());

        modelPaths.forEach((name, path) -> {
            if (path == null || path.isBlank()) {
                log.warn("{} 路径未配置", name);
                return;
            }
            Path modelPath = Path.of(path);
            if (Files.isDirectory(modelPath)) {
                log.info("{} 已就绪: {}", name, modelPath.toAbsolutePath());
            } else {
                log.warn("{} 路径不存在: {}", name, modelPath.toAbsolutePath());
            }
        });
    }
}
