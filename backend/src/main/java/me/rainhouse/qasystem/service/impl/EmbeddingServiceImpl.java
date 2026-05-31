package me.rainhouse.qasystem.service.impl;

import me.rainhouse.qasystem.config.AiModelProperties;
import me.rainhouse.qasystem.service.EmbeddingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private final int dimension;

    public EmbeddingServiceImpl(@Value("${vector.search.dimension:768}") int dimension,
                                AiModelProperties aiModelProperties) {
        this.dimension = dimension;
        // 预留真实 BGE 模型接入点；当前实现保持纯 Java 可运行。
        String ignoredModelPath = aiModelProperties.getEmbeddingPath();
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        if (!StringUtils.hasText(text)) {
            return vector;
        }

        List<String> tokens = tokenize(text);
        for (String token : tokens) {
            addToken(vector, token, 1.0f);
        }
        for (int i = 0; i + 1 < tokens.size(); i++) {
            addToken(vector, tokens.get(i) + "_" + tokens.get(i + 1), 0.65f);
        }
        normalize(vector);
        return vector;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    private List<String> tokenize(String text) {
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{IsPunctuation}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        List<String> tokens = new ArrayList<>();
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
        return tokens;
    }

    private boolean isCjk(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    private void flushAscii(List<String> tokens, StringBuilder asciiWord) {
        if (asciiWord.isEmpty()) {
            return;
        }
        tokens.add(asciiWord.toString());
        asciiWord.setLength(0);
    }

    private void addToken(float[] vector, String token, float weight) {
        int hash = hash(token);
        int index = Math.floorMod(hash, vector.length);
        vector[index] += weight;
        int signedIndex = Math.floorMod(hash / 31, vector.length);
        vector[signedIndex] += (hash & 1) == 0 ? weight * 0.35f : -weight * 0.35f;
    }

    private int hash(String token) {
        CRC32 crc32 = new CRC32();
        crc32.update(token.getBytes(StandardCharsets.UTF_8));
        return (int) crc32.getValue();
    }

    private void normalize(float[] vector) {
        double sum = 0.0;
        for (float value : vector) {
            sum += value * value;
        }
        if (sum == 0.0) {
            return;
        }
        float norm = (float) Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] /= norm;
        }
    }
}
