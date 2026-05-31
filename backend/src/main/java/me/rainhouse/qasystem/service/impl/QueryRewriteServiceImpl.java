package me.rainhouse.qasystem.service.impl;

import me.rainhouse.qasystem.config.AiModelProperties;
import me.rainhouse.qasystem.service.QueryRewriteService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class QueryRewriteServiceImpl implements QueryRewriteService {

    public QueryRewriteServiceImpl(AiModelProperties aiModelProperties) {
        // 预留 Qwen3-0.6B 本地改写模型接入点；当前实现保持纯 Java 可运行。
        String ignoredModelPath = aiModelProperties.getQwenCleanerPath();
    }

    @Override
    public String rewrite(String query) {
        if (!StringUtils.hasText(query)) {
            return "";
        }
        String rewritten = query.replace('\r', '\n')
                .replaceAll("\\s+", " ")
                .replaceAll("^[你好请问一下,，。\\s]+", "")
                .trim();
        if (!rewritten.endsWith("？") && !rewritten.endsWith("?")) {
            rewritten = rewritten + "？";
        }
        return rewritten.length() <= 500 ? rewritten : rewritten.substring(0, 500);
    }
}
