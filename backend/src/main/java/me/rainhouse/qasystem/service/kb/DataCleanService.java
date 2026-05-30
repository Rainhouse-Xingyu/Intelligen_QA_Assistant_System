package me.rainhouse.qasystem.service.kb;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DataCleanService {

    private static final Pattern QA_BLOCK_PATTERN = Pattern.compile(
            "(?is)(?:^|\\n)\\s*(?:Q|问题|问)[:：]\\s*(.+?)\\s*(?:\\n|\\r\\n)\\s*(?:A|答案|答)[:：]\\s*(.+?)(?=(?:\\n\\s*(?:Q|问题|问)[:：])|\\z)"
    );

    public List<FaqItem> cleanToFaq(String rawText, String fallbackTitle) {
        String normalized = normalize(rawText);
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }

        List<FaqItem> faqItems = parseExplicitQa(normalized);
        if (!faqItems.isEmpty()) {
            return faqItems;
        }

        faqItems = parseDelimitedRows(normalized);
        if (!faqItems.isEmpty()) {
            return faqItems;
        }

        return buildFaqFromParagraphs(normalized, fallbackTitle);
    }

    private List<FaqItem> parseExplicitQa(String text) {
        List<FaqItem> items = new ArrayList<>();
        Matcher matcher = QA_BLOCK_PATTERN.matcher(text);
        while (matcher.find()) {
            addIfValid(items, matcher.group(1), matcher.group(2));
        }
        return items;
    }

    private List<FaqItem> parseDelimitedRows(String text) {
        List<FaqItem> items = new ArrayList<>();
        for (String line : text.split("\\n")) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            String[] parts = splitLine(trimmed);
            if (parts != null) {
                addIfValid(items, parts[0], parts[1]);
            }
        }
        return items;
    }

    private List<FaqItem> buildFaqFromParagraphs(String text, String fallbackTitle) {
        List<String> paragraphs = new ArrayList<>();
        for (String part : text.split("\\n{2,}")) {
            String paragraph = part.trim();
            if (StringUtils.hasText(paragraph)) {
                paragraphs.add(paragraph);
            }
        }

        List<FaqItem> items = new ArrayList<>();
        for (String paragraph : paragraphs) {
            List<String> lines = paragraph.lines()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
            if (lines.isEmpty()) {
                continue;
            }

            String firstLine = lines.get(0);
            String answer = lines.size() > 1
                    ? String.join("\n", lines.subList(1, lines.size()))
                    : firstLine;
            String question = inferQuestion(firstLine, fallbackTitle);
            addIfValid(items, question, answer);
        }

        if (items.isEmpty()) {
            String title = StringUtils.hasText(fallbackTitle) ? fallbackTitle : "导入文档";
            addIfValid(items, title + "的主要内容是什么？", text);
        }
        return items;
    }

    private String normalize(String rawText) {
        if (rawText == null) {
            return "";
        }
        return rawText.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String[] splitLine(String line) {
        String[] delimiters = {"\\|", "\t", "，答案[:：]", ",答案[:：]", " 答案[:：]"};
        for (String delimiter : delimiters) {
            String[] parts = line.split(delimiter, 2);
            if (parts.length == 2 && StringUtils.hasText(parts[0]) && StringUtils.hasText(parts[1])) {
                return parts;
            }
        }
        return null;
    }

    private String inferQuestion(String title, String fallbackTitle) {
        String cleanTitle = title.replaceAll("^#+\\s*", "").trim();
        if (cleanTitle.endsWith("？") || cleanTitle.endsWith("?")) {
            return cleanTitle;
        }
        if (cleanTitle.length() > 80 && StringUtils.hasText(fallbackTitle)) {
            cleanTitle = fallbackTitle;
        }
        if (cleanTitle.endsWith("规定") || cleanTitle.endsWith("流程") || cleanTitle.endsWith("通知")
                || cleanTitle.endsWith("说明") || cleanTitle.endsWith("办法")) {
            return cleanTitle + "是什么？";
        }
        return "关于" + cleanTitle + "，需要了解什么？";
    }

    private void addIfValid(List<FaqItem> items, String question, String answer) {
        if (!StringUtils.hasText(question) || !StringUtils.hasText(answer)) {
            return;
        }
        String cleanQuestion = question.replaceAll("^[-*\\d.、\\s]+", "").trim();
        String cleanAnswer = answer.trim();
        if (StringUtils.hasText(cleanQuestion) && StringUtils.hasText(cleanAnswer)) {
            items.add(new FaqItem(cleanQuestion, cleanAnswer));
        }
    }
}
