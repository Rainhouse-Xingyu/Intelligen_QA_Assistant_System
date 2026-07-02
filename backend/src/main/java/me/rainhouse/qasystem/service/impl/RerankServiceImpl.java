package me.rainhouse.qasystem.service.impl;

import me.rainhouse.qasystem.config.AiModelProperties;
import me.rainhouse.qasystem.service.RerankService;
import me.rainhouse.qasystem.service.localmodel.LocalModelClient;
import me.rainhouse.qasystem.service.vector.VectorDocument;
import me.rainhouse.qasystem.service.vector.VectorSearchResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class RerankServiceImpl implements RerankService {

    private final LocalModelClient localModelClient;

    public RerankServiceImpl(AiModelProperties aiModelProperties,
                             LocalModelClient localModelClient) {
        this.localModelClient = localModelClient;
        aiModelProperties.getRerankerPath();
    }

    @Override
    public List<VectorSearchResult> rerank(String query, float[] queryVector, List<VectorDocument> candidates, int topK) {
        if (localModelClient.enabled()) {
            List<String> documents = candidates.stream()
                    .map(candidate -> candidate.question() + "\n" + candidate.answer())
                    .toList();
            List<Double> scores = localModelClient.rerank(query, documents);
            return java.util.stream.IntStream.range(0, candidates.size())
                    .mapToObj(i -> toResult(query, queryVector, candidates.get(i), i < scores.size() ? scores.get(i) : 0.0))
                    .sorted(Comparator.comparingDouble(VectorSearchResult::finalScore).reversed())
                    .limit(Math.max(1, topK))
                    .toList();
        }
        return candidates.stream()
                .map(candidate -> toResult(query, queryVector, candidate))
                .sorted(Comparator.comparingDouble(VectorSearchResult::finalScore).reversed())
                .limit(Math.max(1, topK))
                .toList();
    }

    private VectorSearchResult toResult(String query, float[] queryVector, VectorDocument document, double rerankScore) {
        double vectorScore = cosine(queryVector, document.vector());
        double lexicalScore = lexicalScore(query, document.question() + "\n" + document.answer());
        double normalizedRerankScore = Math.max(0.0, Math.min(1.0, rerankScore));
        double finalScore = Math.max(
                normalizedRerankScore * 0.6 + vectorScore * 0.25 + lexicalScore * 0.15,
                lexicalScore * 0.75
        );
        return new VectorSearchResult(
                document.knowledgeId(),
                document.question(),
                document.answer(),
                document.moduleType(),
                document.categoryL1Id(),
                document.categoryL2Id(),
                document.categoryL3Id(),
                document.categoryPath(),
                document.sourceType(),
                document.sourceUrl(),
                round(vectorScore),
                round(normalizedRerankScore),
                round(finalScore)
        );
    }

    private VectorSearchResult toResult(String query, float[] queryVector, VectorDocument document) {
        double vectorScore = cosine(queryVector, document.vector());
        double lexicalScore = lexicalScore(query, document.question() + "\n" + document.answer());
        double finalScore = Math.max(
                vectorScore,
                Math.max(vectorScore * 0.65 + lexicalScore * 0.35, lexicalScore * 0.8)
        );
        return new VectorSearchResult(
                document.knowledgeId(),
                document.question(),
                document.answer(),
                document.moduleType(),
                document.categoryL1Id(),
                document.categoryL2Id(),
                document.categoryL3Id(),
                document.categoryPath(),
                document.sourceType(),
                document.sourceUrl(),
                round(vectorScore),
                round(lexicalScore),
                round(finalScore)
        );
    }

    private double lexicalScore(String query, String documentText) {
        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return 0.0;
        }
        Set<String> docTokens = tokenize(documentText);
        if (docTokens.isEmpty()) {
            return 0.0;
        }
        int overlap = 0;
        for (String token : queryTokens) {
            if (docTokens.contains(token)) {
                overlap++;
            }
        }
        return Math.min(1.0, overlap / (double) queryTokens.size());
    }

    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (!StringUtils.hasText(text)) {
            return tokens;
        }
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}]+", " ")
                .replaceAll("\\s+", " ");
        StringBuilder asciiWord = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (isCjk(ch)) {
                flushAscii(tokens, asciiWord);
                tokens.add(String.valueOf(ch));
            } else if (Character.isLetterOrDigit(ch)) {
                asciiWord.append(ch);
            } else {
                flushAscii(tokens, asciiWord);
            }
        }
        flushAscii(tokens, asciiWord);
        addCjkNgrams(tokens, normalized, 2);
        addCjkNgrams(tokens, normalized, 3);
        return tokens;
    }

    private void addCjkNgrams(Set<String> tokens, String text, int size) {
        StringBuilder cjkRun = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (isCjk(ch)) {
                cjkRun.append(ch);
            } else {
                flushCjkNgrams(tokens, cjkRun, size);
            }
        }
        flushCjkNgrams(tokens, cjkRun, size);
    }

    private void flushCjkNgrams(Set<String> tokens, StringBuilder cjkRun, int size) {
        if (cjkRun.length() < size) {
            cjkRun.setLength(0);
            return;
        }
        for (int i = 0; i + size <= cjkRun.length(); i++) {
            tokens.add(cjkRun.substring(i, i + size));
        }
        cjkRun.setLength(0);
    }

    private boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private void flushAscii(Set<String> tokens, StringBuilder asciiWord) {
        if (asciiWord.isEmpty()) {
            return;
        }
        tokens.add(asciiWord.toString());
        asciiWord.setLength(0);
    }

    private double cosine(float[] left, float[] right) {
        if (left == null || right == null || left.length != right.length) {
            return 0.0;
        }
        double dot = 0.0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
        }
        return Math.max(0.0, Math.min(1.0, dot));
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }
}
