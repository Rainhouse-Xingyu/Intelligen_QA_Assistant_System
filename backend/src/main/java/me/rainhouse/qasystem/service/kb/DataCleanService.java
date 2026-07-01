package me.rainhouse.qasystem.service.kb;

import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.service.localmodel.LocalModelClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class DataCleanService {

    private static final int MAX_POLICY_BLOCK_LENGTH = 900;
    private static final int MAX_MODEL_BLOCK_LENGTH = 2800;
    private static final int MIN_MODEL_BLOCK_LENGTH = 120;
    private static final int MAX_MODEL_ITEMS_PER_BLOCK = 6;
    private static final int MAX_TITLE_LENGTH = 48;
    private static final String HEADING_MARKER_REGEX = "(?:第[一二三四五六七八九十百千万零〇0-9]+[章节条款项]|[一二三四五六七八九十]+[、.]|[（(][一二三四五六七八九十0-9]+[）)]|\\d+(?:\\.\\d+)+[、.．]?|\\d+[、.．])";

    private static final Pattern QA_BLOCK_PATTERN = Pattern.compile(
            "(?is)(?:^|\\n|\\s{2,})\\s*(?:Q|问题|问)[:：]\\s*(.+?)\\s*(?:\\n\\s*)?(?:A|答案|答)[:：]\\s*(.+?)(?=(?:\\n|\\s{2,})\\s*(?:Q|问题|问)[:：]|\\z)"
    );
    private static final Pattern INLINE_QA_PATTERN = Pattern.compile(
            "(?is)^\\s*(?:Q|问题|问)[:：]\\s*(.+?)\\s+(?:A|答案|答)[:：]\\s*(.+?)\\s*$"
    );
    private static final Pattern STRUCTURE_MARKER_PATTERN = Pattern.compile(
            "(?m)(?<!^)(?=" + HEADING_MARKER_REGEX + "\\s*\\S{2,})"
    );
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "^" + HEADING_MARKER_REGEX + "\\s*\\S{2,}.*"
    );
    private static final Pattern HEADING_PREFIX_PATTERN = Pattern.compile("^" + HEADING_MARKER_REGEX + "\\s*");
    private static final Pattern SENTENCE_BOUNDARY_PATTERN = Pattern.compile("(?<=[。！？；;])");
    private static final Pattern TRAILING_ORPHAN_MARKER_PATTERN = Pattern.compile(
            "(?:\\s+|^)(?:\\d+(?:\\.\\d+)*[、.．]?|[一二三四五六七八九十]+[、.．])\\s*$"
    );

    private final LocalModelClient localModelClient;

    public DataCleanService() {
        this.localModelClient = null;
    }

    @Autowired
    public DataCleanService(LocalModelClient localModelClient) {
        this.localModelClient = localModelClient;
    }

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

        faqItems = parseWithLocalModel(normalized, fallbackTitle);
        if (!faqItems.isEmpty()) {
            return faqItems;
        }

        faqItems = parsePolicySections(normalized, fallbackTitle);
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
        List<String> paragraphs = splitPolicyBlocks(text);

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
            if (lines.size() == 1 && looksLikeHeadingOnly(firstLine)) {
                continue;
            }
            String question = inferQuestion(firstLine, fallbackTitle);
            addIfValid(items, question, answer);
        }

        if (items.isEmpty()) {
            String title = StringUtils.hasText(fallbackTitle) ? fallbackTitle : "导入文档";
            addIfValid(items, title + "的主要内容是什么？", text);
        }
        return items;
    }

    private List<FaqItem> parseWithLocalModel(String text, String fallbackTitle) {
        if (localModelClient == null || !localModelClient.enabled() || text.length() < 120) {
            return List.of();
        }

        List<FaqItem> items = new ArrayList<>();
        for (String block : splitModelBlocks(text)) {
            String cleanBlock = block.trim();
            if (!shouldUseModelBlock(cleanBlock)) {
                continue;
            }
            try {
                List<FaqItem> modelItems = localModelClient.chunkToFaq(
                        cleanBlock,
                        modelBlockTitle(cleanBlock, fallbackTitle),
                        MAX_MODEL_ITEMS_PER_BLOCK
                );
                for (FaqItem item : modelItems) {
                    addModelItemIfValid(items, item.question(), item.answer());
                }
            } catch (Exception e) {
                log.warn("本地模型文档切片失败，回退规则切片: {}", e.getMessage());
                return List.of();
            }
        }
        return items;
    }

    private boolean shouldUseModelBlock(String block) {
        if (!StringUtils.hasText(block) || block.length() < MIN_MODEL_BLOCK_LENGTH) {
            return false;
        }
        long lineCount = block.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .count();
        return lineCount > 1 || !looksLikeHeadingOnly(block);
    }

    private List<String> splitModelBlocks(String text) {
        String markedText = STRUCTURE_MARKER_PATTERN.matcher(text).replaceAll("\n");
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String rawLine : markedText.split("\\n+")) {
            String line = rawLine.trim();
            if (!StringUtils.hasText(line)) {
                flushModelBlock(blocks, current);
                continue;
            }

            boolean startsNewBlock = HEADING_PATTERN.matcher(line).find();
            if ((startsNewBlock && current.length() > 0)
                    || current.length() + line.length() + 1 > MAX_MODEL_BLOCK_LENGTH) {
                flushModelBlock(blocks, current);
            }

            if (line.length() > MAX_MODEL_BLOCK_LENGTH) {
                splitFixedLength(blocks, line, MAX_MODEL_BLOCK_LENGTH);
                continue;
            }

            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);
        }
        flushModelBlock(blocks, current);
        return blocks;
    }

    private void flushModelBlock(List<String> blocks, StringBuilder current) {
        if (current.length() > 0) {
            blocks.add(current.toString().trim());
            current.setLength(0);
        }
    }

    private String modelBlockTitle(String block, String fallbackTitle) {
        String firstLine = block.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
        String sectionTitle = inferQuestion(firstLine, fallbackTitle)
                .replaceAll("[？?]$", "");
        if (StringUtils.hasText(fallbackTitle)) {
            return fallbackTitle + " / " + sectionTitle;
        }
        return sectionTitle;
    }

    private List<FaqItem> parsePolicySections(String text, String fallbackTitle) {
        List<String> blocks = splitPolicyBlocks(text);
        if (blocks.size() <= 1 && text.length() <= MAX_POLICY_BLOCK_LENGTH) {
            return List.of();
        }

        List<FaqItem> items = new ArrayList<>();
        for (String block : blocks) {
            List<String> lines = block.lines()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
            if (lines.isEmpty()) {
                continue;
            }

            String title = lines.get(0);
            String answer = lines.size() > 1 ? String.join("\n", lines) : block;
            if (lines.size() == 1 && looksLikeHeadingOnly(title)) {
                continue;
            }
            addIfValid(items, inferQuestion(title, fallbackTitle), answer);
        }
        return items;
    }

    private List<String> splitPolicyBlocks(String text) {
        String markedText = STRUCTURE_MARKER_PATTERN.matcher(text).replaceAll("\n");
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String rawLine : markedText.split("\\n+")) {
            String line = rawLine.trim();
            if (!StringUtils.hasText(line)) {
                flushPolicyBlock(blocks, current);
                continue;
            }

            boolean startsNewBlock = HEADING_PATTERN.matcher(line).find();
            if (startsNewBlock && current.length() > 0) {
                flushPolicyBlock(blocks, current);
            }

            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);

            if (current.length() >= MAX_POLICY_BLOCK_LENGTH) {
                flushPolicyBlock(blocks, current);
            }
        }
        flushPolicyBlock(blocks, current);

        if (blocks.size() <= 1 && text.length() > MAX_POLICY_BLOCK_LENGTH) {
            blocks.clear();
            splitLongText(blocks, text);
        }
        return blocks;
    }

    private void flushPolicyBlock(List<String> blocks, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        splitLongText(blocks, current.toString().trim());
        current.setLength(0);
    }

    private void splitLongText(List<String> blocks, String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        if (text.length() <= MAX_POLICY_BLOCK_LENGTH) {
            blocks.add(text.trim());
            return;
        }

        StringBuilder current = new StringBuilder();
        for (String sentence : SENTENCE_BOUNDARY_PATTERN.split(text)) {
            String cleanSentence = sentence.trim();
            if (!StringUtils.hasText(cleanSentence)) {
                continue;
            }
            if (cleanSentence.length() > MAX_POLICY_BLOCK_LENGTH) {
                flushTextChunk(blocks, current);
                splitFixedLength(blocks, cleanSentence);
                continue;
            }
            if (current.length() + cleanSentence.length() > MAX_POLICY_BLOCK_LENGTH) {
                flushTextChunk(blocks, current);
            }
            current.append(cleanSentence);
        }
        flushTextChunk(blocks, current);
    }

    private void flushTextChunk(List<String> blocks, StringBuilder current) {
        if (current.length() > 0) {
            blocks.add(current.toString().trim());
            current.setLength(0);
        }
    }

    private void splitFixedLength(List<String> blocks, String text) {
        splitFixedLength(blocks, text, MAX_POLICY_BLOCK_LENGTH);
    }

    private void splitFixedLength(List<String> blocks, String text, int maxLength) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLength, text.length());
            blocks.add(text.substring(start, end).trim());
            start = end;
        }
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
        Matcher qaMatcher = INLINE_QA_PATTERN.matcher(line);
        if (qaMatcher.matches()) {
            return new String[]{qaMatcher.group(1), qaMatcher.group(2)};
        }
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
        String cleanTitle = title.replaceAll("^#+\\s*", "")
                .replaceAll("^" + HEADING_MARKER_REGEX + "\\s*", "")
                .replaceAll("[。；;，,、\\s]+$", "")
                .trim();
        if (cleanTitle.endsWith("？") || cleanTitle.endsWith("?")) {
            return cleanTitle;
        }
        if (!StringUtils.hasText(cleanTitle) && StringUtils.hasText(fallbackTitle)) {
            cleanTitle = fallbackTitle.replaceAll("\\.[^.]+$", "");
        }

        String topic = extractTopic(cleanTitle);
        if (!StringUtils.hasText(topic)) {
            topic = StringUtils.hasText(fallbackTitle) ? fallbackTitle.replaceAll("\\.[^.]+$", "") : "该政策";
        }

        if (topic.contains("条件") || topic.contains("对象") || topic.contains("范围") || topic.contains("资格")) {
            return topic + "有哪些？";
        }
        if (topic.contains("要求")) {
            return topic + "有哪些？";
        }
        if (topic.contains("时间") || topic.contains("期限") || topic.contains("截止")) {
            return topic + "有什么要求？";
        }
        if (topic.endsWith("流程") || topic.contains("办理") || topic.contains("申请")) {
            return topic + "如何办理？";
        }
        return topic + "是如何规定的？";
    }

    private String extractTopic(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim();
        if (normalized.contains("校内外指导教师") && (normalized.contains("培训") || normalized.contains("专题讲座"))) {
            return "校内外指导教师确定和培训安排";
        }
        if (normalized.contains("指导教师拟题") && normalized.contains("任务书")) {
            return "指导教师拟题和任务书下达";
        }
        if (normalized.contains("毕业设计（论文）") && normalized.contains("检查")) {
            return "毕业设计（论文）过程检查";
        }

        String[] parts = normalized.split("[，,；;。]");
        String topic = parts.length > 0 ? parts[0].trim() : normalized;
        if (topic.length() > MAX_TITLE_LENGTH) {
            int cut = firstDelimiter(topic, "、及和与");
            if (cut > 6) {
                topic = topic.substring(0, cut);
            }
        }
        if (topic.length() > MAX_TITLE_LENGTH) {
            topic = topic.substring(0, MAX_TITLE_LENGTH);
        }
        return topic.replaceAll("[。；;，,、\\s]+$", "").trim();
    }

    private int firstDelimiter(String text, String delimiters) {
        int result = -1;
        for (int i = 0; i < delimiters.length(); i++) {
            int index = text.indexOf(delimiters.charAt(i));
            if (index >= 0 && (result < 0 || index < result)) {
                result = index;
            }
        }
        return result;
    }

    private void addIfValid(List<FaqItem> items, String question, String answer) {
        if (!StringUtils.hasText(question) || !StringUtils.hasText(answer)) {
            return;
        }
        String cleanQuestion = question.replaceAll("^[-*\\d.、\\s]+", "").trim();
        String cleanAnswer = cleanAnswer(answer);
        if (StringUtils.hasText(cleanQuestion) && StringUtils.hasText(cleanAnswer)) {
            items.add(new FaqItem(cleanQuestion, cleanAnswer));
        }
    }

    private void addModelItemIfValid(List<FaqItem> items, String question, String answer) {
        if (!StringUtils.hasText(question) || !StringUtils.hasText(answer)) {
            return;
        }
        String cleanQuestion = normalizeModelQuestion(question, answer);
        String cleanAnswer = cleanAnswer(answer);
        if (!StringUtils.hasText(cleanQuestion) || !StringUtils.hasText(cleanAnswer)) {
            return;
        }
        if (isLowQualityModelAnswer(cleanAnswer)) {
            log.info("丢弃低质量模型切片: question={}, answer={}",
                    cleanQuestion,
                    cleanAnswer.length() > 80 ? cleanAnswer.substring(0, 80) + "..." : cleanAnswer);
            return;
        }
        if (!hasReliableQuestionAnswerAlignment(cleanQuestion, cleanAnswer)) {
            log.info("丢弃问题答案不匹配的模型切片: question={}, answer={}",
                    cleanQuestion,
                    cleanAnswer.length() > 80 ? cleanAnswer.substring(0, 80) + "..." : cleanAnswer);
            return;
        }
        items.add(new FaqItem(cleanQuestion, cleanAnswer));
    }

    private String normalizeModelQuestion(String question, String answer) {
        String cleanQuestion = question.replaceAll("^[-*\\d.、\\s]+", "")
                .replaceAll("\\s+", " ")
                .trim();
        String answerHeading = extractAnswerHeading(answer);
        if (StringUtils.hasText(answerHeading) && shouldPreferAnswerHeading(cleanQuestion, answerHeading)) {
            return inferQuestion(answerHeading, null);
        }
        return cleanQuestion.replaceAll("^(.{2,40})[:：]\\s*\\1", "$1").trim();
    }

    private boolean shouldPreferAnswerHeading(String question, String answerHeading) {
        if (!StringUtils.hasText(question)) {
            return true;
        }
        if (question.contains("如何规定") || question.contains("是如何规定") || question.contains("相关规定")
                || question.contains("有哪些要求") || question.contains("要求有哪些")) {
            return true;
        }
        String headingCore = answerHeading.replaceAll("[：:（()].*$", "").trim();
        return StringUtils.hasText(headingCore) && !question.contains(headingCore) && headingCore.length() >= 2;
    }

    private String extractAnswerHeading(String answer) {
        if (!StringUtils.hasText(answer)) {
            return "";
        }
        String firstLine = answer.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
        String withoutPrefix = firstLine
                .replaceAll("^" + HEADING_MARKER_REGEX + "\\s*", "")
                .replaceAll("^[-*\\s]+", "")
                .trim();
        int colon = firstDelimiter(withoutPrefix, "：:");
        if (colon > 0 && colon <= 40) {
            return withoutPrefix.substring(0, colon).trim();
        }
        if (withoutPrefix.length() <= 36 && looksLikeHeadingOnly(withoutPrefix)) {
            return withoutPrefix;
        }
        return "";
    }

    private boolean isLowQualityModelAnswer(String answer) {
        String cleanAnswer = answer.trim();
        if (cleanAnswer.length() < 30) {
            return true;
        }
        if (cleanAnswer.endsWith(":") || cleanAnswer.endsWith("：") || cleanAnswer.endsWith("、")
                || cleanAnswer.endsWith("；") || cleanAnswer.endsWith(";")) {
            return true;
        }
        List<String> lines = cleanAnswer.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        return lines.size() == 1 && looksLikeHeadingOnly(lines.get(0));
    }

    private boolean looksLikeHeadingOnly(String text) {
        String cleanText = text.replaceAll("^" + HEADING_MARKER_REGEX + "\\s*", "").trim();
        if (!StringUtils.hasText(cleanText)) {
            return true;
        }
        if (cleanText.length() <= 16 && !cleanText.matches(".*[。！？；;，,].*")) {
            return true;
        }
        return cleanText.matches(".*(要求|办法|规定|流程|材料|附件|参考文献|结束语|致谢|格式要求)$")
                && !cleanText.matches(".*[。！？；;].*");
    }

    private boolean hasReliableQuestionAnswerAlignment(String question, String answer) {
        List<String> terms = significantTerms(question);
        if (terms.isEmpty()) {
            return true;
        }
        int overlap = 0;
        for (String term : terms) {
            if (answer.contains(term)) {
                overlap++;
            }
        }
        return overlap >= 1;
    }

    private List<String> significantTerms(String question) {
        String normalized = question
                .replaceAll("(什么|怎么|如何|哪些|是否|可以|需要|应该|进行|办理|申请|流程|条件|材料|时间|要求|注意事项|规定|政策|相关|情况|方式|入口|查询|查看|有哪些|是什么|怎么办)", "")
                .replaceAll("[的了和及与或、，。！？?：:；;（）()\\s]+", " ")
                .trim();
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }

        List<String> terms = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            addSignificantTerm(terms, token);
            if (isMostlyCjk(token) && token.length() > 2) {
                for (int i = 0; i + 2 <= token.length(); i++) {
                    addSignificantTerm(terms, token.substring(i, i + 2));
                }
            }
        }
        return terms.stream().distinct().limit(8).toList();
    }

    private void addSignificantTerm(List<String> terms, String term) {
        if (!StringUtils.hasText(term)) {
            return;
        }
        String cleanTerm = term.trim();
        if (cleanTerm.length() < 2 || cleanTerm.length() > 30 || isWeakTerm(cleanTerm)) {
            return;
        }
        terms.add(cleanTerm);
    }

    private boolean isWeakTerm(String term) {
        return List.of("学生", "老师", "学校", "学院", "教务", "系统", "通知", "管理", "工作", "有关", "内容")
                .contains(term);
    }

    private boolean isMostlyCjk(String text) {
        int cjkCount = 0;
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(text.charAt(i));
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                cjkCount++;
            }
        }
        return cjkCount >= Math.max(2, text.length() / 2);
    }

    private String cleanAnswer(String answer) {
        String cleanAnswer = answer.trim();
        String previous;
        do {
            previous = cleanAnswer;
            cleanAnswer = TRAILING_ORPHAN_MARKER_PATTERN.matcher(cleanAnswer).replaceFirst("").trim();
        } while (!previous.equals(cleanAnswer));
        return cleanAnswer;
    }
}
