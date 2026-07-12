package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import me.rainhouse.qasystem.config.AiModelProperties;
import me.rainhouse.qasystem.entity.KbCategory;
import me.rainhouse.qasystem.mapper.KbCategoryMapper;
import me.rainhouse.qasystem.service.IntentClassifierService;
import me.rainhouse.qasystem.service.localmodel.LocalModelClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class IntentClassifierServiceImpl implements IntentClassifierService {

    private static final int MAX_CANDIDATES = 2;
    private static final int MAX_CATEGORY_TERMS = 12;
    private static final double AMBIGUITY_SCORE_MARGIN = 0.08;

    private final LocalModelClient localModelClient;
    private final KbCategoryMapper kbCategoryMapper;

    public IntentClassifierServiceImpl(AiModelProperties aiModelProperties,
                                       LocalModelClient localModelClient,
                                       KbCategoryMapper kbCategoryMapper) {
        this.localModelClient = localModelClient;
        this.kbCategoryMapper = kbCategoryMapper;
        aiModelProperties.getIntentClassifierPath();
    }

    @Override
    public String classify(String query, String userSelectedModule) {
        return classifyCandidates(query, userSelectedModule).stream().findFirst().orElse(null);
    }

    @Override
    public List<String> classifyCandidates(String query, String userSelectedModule) {
        if (StringUtils.hasText(userSelectedModule)) {
            return List.of(userSelectedModule.trim());
        }
        String currentQuestion = extractCurrentQuestion(query);
        Map<String, String> labels = categoryLabels();
        if (labels.isEmpty() || !localModelClient.enabled()) {
            return List.of();
        }

        Map<String, Double> scores = localModelClient.classifyScores(currentQuestion, List.copyOf(labels.keySet()));
        List<Map.Entry<String, Double>> ranked = scores.entrySet().stream()
                .filter(entry -> labels.containsKey(entry.getKey()))
                .filter(entry -> entry.getValue() != null && Double.isFinite(entry.getValue()))
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .toList();
        if (ranked.isEmpty()) {
            return List.of();
        }

        List<String> candidates = new ArrayList<>();
        candidates.add(labels.get(ranked.get(0).getKey()));
        if (ranked.size() > 1
                && ranked.get(0).getValue() - ranked.get(1).getValue() <= AMBIGUITY_SCORE_MARGIN) {
            candidates.add(labels.get(ranked.get(1).getKey()));
        }
        return candidates.stream().filter(Objects::nonNull).distinct().limit(MAX_CANDIDATES).toList();
    }

    private String extractCurrentQuestion(String query) {
        if (!StringUtils.hasText(query)) {
            return "";
        }
        String marker = "当前问题：";
        int index = query.lastIndexOf(marker);
        if (index >= 0) {
            return query.substring(index + marker.length()).trim();
        }
        return query.trim();
    }

    private Map<String, String> categoryLabels() {
        List<KbCategory> categories = kbCategoryMapper.selectList(new LambdaQueryWrapper<KbCategory>()
                .eq(KbCategory::getStatus, 1)
                .orderByAsc(KbCategory::getLevel)
                .orderByAsc(KbCategory::getSortOrder)
                .orderByAsc(KbCategory::getId));
        if (categories == null || categories.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<KbCategory>> children = categories.stream()
                .filter(category -> category.getParentId() != null)
                .collect(Collectors.groupingBy(KbCategory::getParentId));
        Map<String, String> labels = new LinkedHashMap<>();
        categories.stream()
                .filter(category -> Integer.valueOf(1).equals(category.getLevel()))
                .sorted(Comparator.comparing(KbCategory::getSortOrder).thenComparing(KbCategory::getId))
                .forEach(root -> {
                    List<String> terms = descendantNames(root.getId(), children);
                    String label = terms.isEmpty()
                            ? root.getName()
                            : root.getName() + "：" + String.join("、", terms.stream().limit(MAX_CATEGORY_TERMS).toList());
                    labels.put(label, root.getName());
                });
        return labels;
    }

    private List<String> descendantNames(Long parentId, Map<Long, List<KbCategory>> children) {
        List<String> names = new ArrayList<>();
        for (KbCategory child : children.getOrDefault(parentId, List.of())) {
            if (StringUtils.hasText(child.getName())) {
                names.add(child.getName().trim());
            }
            names.addAll(descendantNames(child.getId(), children));
        }
        return names.stream().distinct().toList();
    }
}
