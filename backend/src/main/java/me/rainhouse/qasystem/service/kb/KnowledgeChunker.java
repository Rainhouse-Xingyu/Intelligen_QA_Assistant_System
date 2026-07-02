package me.rainhouse.qasystem.service.kb;

import me.rainhouse.qasystem.entity.KbQaEntry;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class KnowledgeChunker {

    private static final int MAX_QUESTION_LENGTH = 500;
    private static final int MAX_ANSWER_CHUNK_LENGTH = 900;
    private static final int MIN_ANSWER_CHUNK_LENGTH = 320;

    public List<KbQaEntry> chunk(List<FaqItem> faqItems,
                                 Long documentId,
                                 String createdBy,
                                 String moduleType,
                                 String sourceType) {
        List<KbQaEntry> entries = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (FaqItem item : faqItems) {
            if (!StringUtils.hasText(item.question()) || !StringUtils.hasText(item.answer())) {
                continue;
            }

            String question = limit(item.question().trim(), MAX_QUESTION_LENGTH);
            String answer = item.answer().trim();
            List<String> answerChunks = splitAnswer(answer);
            for (int i = 0; i < answerChunks.size(); i++) {
                KbQaEntry entry = new KbQaEntry();
                entry.setDocumentId(documentId);
                entry.setCategoryL1Name(item.categoryL1());
                entry.setCategoryL2Name(item.categoryL2());
                entry.setCategoryL3Name(item.categoryL3());
                entry.setQuestion(answerChunks.size() == 1 ? question : limit(question + "（第" + (i + 1) + "部分）", MAX_QUESTION_LENGTH));
                entry.setAnswer(answerChunks.get(i));
                entry.setStatus(1);
                entry.setModuleType(moduleType);
                entry.setSourceType(sourceType);
                entry.setCreatedBy(createdBy);
                entry.setCreatedAt(now);
                entry.setUpdatedAt(now);
                entries.add(entry);
            }
        }

        return entries;
    }

    private List<String> splitAnswer(String answer) {
        List<String> chunks = new ArrayList<>();
        if (answer.length() <= MAX_ANSWER_CHUNK_LENGTH) {
            chunks.add(answer);
            return chunks;
        }

        StringBuilder current = new StringBuilder();
        for (String paragraph : answer.split("\\n{2,}|(?<=。)|(?<=！)|(?<=？)|(?<=；)|(?<=;)")) {
            String cleanParagraph = paragraph.trim();
            if (!StringUtils.hasText(cleanParagraph)) {
                continue;
            }
            if (current.length() + cleanParagraph.length() + 1 > MAX_ANSWER_CHUNK_LENGTH && current.length() >= MIN_ANSWER_CHUNK_LENGTH) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            if (cleanParagraph.length() > MAX_ANSWER_CHUNK_LENGTH) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                flushLongParagraph(chunks, cleanParagraph);
            } else {
                current.append(cleanParagraph).append('\n');
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private void flushLongParagraph(List<String> chunks, String paragraph) {
        int start = 0;
        while (start < paragraph.length()) {
            int end = Math.min(start + MAX_ANSWER_CHUNK_LENGTH, paragraph.length());
            chunks.add(paragraph.substring(start, end).trim());
            start = end;
        }
    }

    private String limit(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 1) + "…";
    }

}
