package me.rainhouse.qasystem.service.impl;

import me.rainhouse.qasystem.config.AiModelProperties;
import me.rainhouse.qasystem.service.QueryRewriteService;
import me.rainhouse.qasystem.service.localmodel.LocalModelClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class QueryRewriteServiceImpl implements QueryRewriteService {

    private final LocalModelClient localModelClient;

    public QueryRewriteServiceImpl(AiModelProperties aiModelProperties,
                                   LocalModelClient localModelClient) {
        this.localModelClient = localModelClient;
        aiModelProperties.getQwenCleanerPath();
    }

    @Override
    public String rewrite(String query) {
        if (!StringUtils.hasText(query)) {
            return "";
        }
        if (localModelClient.enabled()) {
            String rewritten = localModelClient.rewrite(query);
            if (StringUtils.hasText(rewritten)) {
                return rewritten.length() <= 500 ? rewritten : rewritten.substring(0, 500);
            }
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
