package me.rainhouse.qasystem.common.utils;

import java.util.Locale;
import java.util.regex.Pattern;

public final class AiTextSanitizer {

    private static final String THINK_CLOSE_TAG = "</think>";
    private static final Pattern THINK_BLOCK = Pattern.compile("<think\\b[^>]*>.*?</think>\\s*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern THINK_OPEN = Pattern.compile("<think\\b[^>]*>.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern THINK_CLOSE = Pattern.compile("</think>\\s*", Pattern.CASE_INSENSITIVE);

    private AiTextSanitizer() {
    }

    public static String stripThinking(String text) {
        if (text == null) {
            return "";
        }
        String result = text.trim();
        result = THINK_BLOCK.matcher(result).replaceAll("");
        result = THINK_OPEN.matcher(result).replaceAll("");

        int closeIndex = result.toLowerCase(Locale.ROOT).lastIndexOf(THINK_CLOSE_TAG);
        if (closeIndex >= 0) {
            result = result.substring(closeIndex + THINK_CLOSE_TAG.length());
        }
        result = THINK_CLOSE.matcher(result).replaceAll("");
        return result.trim();
    }
}
