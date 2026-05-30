package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.entity.KbDocument;
import me.rainhouse.qasystem.entity.KbQaEntry;
import me.rainhouse.qasystem.mapper.KbDocumentMapper;
import me.rainhouse.qasystem.service.KbQaEntryService;
import me.rainhouse.qasystem.service.KnowledgeBaseService;
import me.rainhouse.qasystem.service.kb.DataCleanService;
import me.rainhouse.qasystem.service.kb.DocumentParserUtil;
import me.rainhouse.qasystem.service.kb.FaqItem;
import me.rainhouse.qasystem.service.kb.KnowledgeChunker;
import me.rainhouse.qasystem.service.kb.ParsedDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KbDocumentMapper, KbDocument> implements KnowledgeBaseService {

    @Value("${knowledge.upload-dir:uploads/kb}")
    private String uploadDir;

    @Autowired
    private KbQaEntryService kbQaEntryService;

    @Autowired
    private DocumentParserUtil documentParserUtil;

    @Autowired
    private DataCleanService dataCleanService;

    @Autowired
    private KnowledgeChunker knowledgeChunker;

    @Override
    public KbDocument importDocument(MultipartFile file, Long uploaderId, String moduleType) {
        validateFile(file);

        KbDocument document = new KbDocument();
        document.setFileName(file.getOriginalFilename());
        document.setUploaderId(uploaderId);
        document.setProcessStatus(1);
        document.setProcessMessage("文件已上传，正在解析清洗");
        document.setCreatedAt(LocalDateTime.now());
        this.save(document);

        try {
            String storedPath = storeFile(file, document.getId());
            document.setFileUrl(storedPath);
            this.updateById(document);

            ParsedDocument parsedDocument = documentParserUtil.parse(file);
            List<FaqItem> faqItems = dataCleanService.cleanToFaq(parsedDocument.text(), document.getFileName());
            List<KbQaEntry> entries = knowledgeChunker.chunk(
                    faqItems,
                    document.getId(),
                    uploaderId,
                    normalizeModuleType(moduleType),
                    parsedDocument.sourceType()
            );

            if (entries.isEmpty()) {
                document.setProcessStatus(3);
                document.setProcessMessage("未能从文档中提取到有效 FAQ 条目");
                this.updateById(document);
                return document;
            }

            kbQaEntryService.saveBatch(entries);
            document.setProcessStatus(2);
            document.setProcessMessage("解析成功，已生成 " + entries.size() + " 条知识库词条");
            this.updateById(document);
            log.info("知识库文档导入成功: documentId={}, entries={}", document.getId(), entries.size());
            return document;
        } catch (Exception e) {
            log.error("知识库文档导入失败: documentId={}, file={}", document.getId(), document.getFileName(), e);
            document.setProcessStatus(3);
            document.setProcessMessage(limitMessage(e.getMessage()));
            this.updateById(document);
            return document;
        }
    }

    @Override
    public List<KbDocument> listDocuments(Integer processStatus) {
        LambdaQueryWrapper<KbDocument> wrapper = new LambdaQueryWrapper<KbDocument>()
                .orderByDesc(KbDocument::getCreatedAt);
        if (processStatus != null) {
            wrapper.eq(KbDocument::getProcessStatus, processStatus);
        }
        return this.list(wrapper);
    }

    @Override
    public List<KbQaEntry> listEntries(String keyword, String moduleType, Integer status, String sourceType) {
        LambdaQueryWrapper<KbQaEntry> wrapper = new LambdaQueryWrapper<KbQaEntry>()
                .orderByDesc(KbQaEntry::getUpdatedAt)
                .orderByDesc(KbQaEntry::getCreatedAt);
        if (StringUtils.hasText(keyword)) {
            String trimmedKeyword = keyword.trim();
            wrapper.and(w -> w.like(KbQaEntry::getQuestion, trimmedKeyword)
                    .or()
                    .like(KbQaEntry::getAnswer, trimmedKeyword));
        }
        if (StringUtils.hasText(moduleType)) {
            wrapper.eq(KbQaEntry::getModuleType, moduleType.trim());
        }
        if (status != null) {
            wrapper.eq(KbQaEntry::getStatus, status);
        }
        if (StringUtils.hasText(sourceType)) {
            wrapper.eq(KbQaEntry::getSourceType, sourceType.trim());
        }
        return kbQaEntryService.list(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public KbQaEntry createEntry(KbQaEntry entry, Long operatorId) {
        validateEntry(entry);
        entry.setId(null);
        entry.setDocumentId(null);
        entry.setQuestion(entry.getQuestion().trim());
        entry.setAnswer(entry.getAnswer().trim());
        entry.setStatus(entry.getStatus() == null ? 1 : entry.getStatus());
        entry.setModuleType(normalizeModuleType(entry.getModuleType()));
        entry.setSourceType(StringUtils.hasText(entry.getSourceType()) ? entry.getSourceType().trim() : "人工录入");
        entry.setCreatedBy(operatorId);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());
        kbQaEntryService.save(entry);
        return entry;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public KbQaEntry updateEntry(KbQaEntry entry) {
        if (entry.getId() == null) {
            throw new IllegalArgumentException("缺少词条ID");
        }
        KbQaEntry existing = kbQaEntryService.getById(entry.getId());
        if (existing == null) {
            throw new IllegalArgumentException("词条不存在");
        }

        if (StringUtils.hasText(entry.getQuestion())) {
            existing.setQuestion(entry.getQuestion().trim());
        }
        if (StringUtils.hasText(entry.getAnswer())) {
            existing.setAnswer(entry.getAnswer().trim());
        }
        if (entry.getStatus() != null) {
            existing.setStatus(entry.getStatus());
        }
        if (StringUtils.hasText(entry.getModuleType())) {
            existing.setModuleType(entry.getModuleType().trim());
        }
        if (StringUtils.hasText(entry.getSourceType())) {
            existing.setSourceType(entry.getSourceType().trim());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        validateEntry(existing);
        kbQaEntryService.updateById(existing);
        return existing;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean disableEntry(Long id) {
        if (id == null) {
            return false;
        }
        KbQaEntry entry = kbQaEntryService.getById(id);
        if (entry == null) {
            return false;
        }
        entry.setStatus(0);
        entry.setUpdatedAt(LocalDateTime.now());
        return kbQaEntryService.updateById(entry);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String lowerName = filename.toLowerCase();
        if (!(lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")
                || lowerName.endsWith(".docx") || lowerName.endsWith(".doc")
                || lowerName.endsWith(".pdf") || lowerName.endsWith(".txt")
                || lowerName.endsWith(".md"))) {
            throw new IllegalArgumentException("仅支持 xlsx、xls、docx、doc、pdf、txt、md 文件");
        }
    }

    private void validateEntry(KbQaEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("词条不能为空");
        }
        if (!StringUtils.hasText(entry.getQuestion())) {
            throw new IllegalArgumentException("标准问题不能为空");
        }
        if (!StringUtils.hasText(entry.getAnswer())) {
            throw new IllegalArgumentException("标准答案不能为空");
        }
        if (entry.getQuestion().trim().length() > 500) {
            throw new IllegalArgumentException("标准问题长度不能超过500字符");
        }
    }

    private String storeFile(MultipartFile file, Long documentId) throws Exception {
        String filename = StringUtils.cleanPath(file.getOriginalFilename());
        if (filename.contains("..")) {
            throw new IllegalArgumentException("文件名非法");
        }
        Path basePath = Paths.get(uploadDir);
        if (!basePath.isAbsolute()) {
            basePath = Paths.get(System.getProperty("user.dir")).resolve(basePath);
        }
        Files.createDirectories(basePath);

        String storedFilename = documentId + "_" + filename;
        Path target = basePath.resolve(storedFilename);
        file.transferTo(target);
        return target.toString();
    }

    private String normalizeModuleType(String moduleType) {
        return StringUtils.hasText(moduleType) ? moduleType.trim() : "通用知识库";
    }

    private String limitMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return "解析失败";
        }
        String trimmed = message.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }
}
