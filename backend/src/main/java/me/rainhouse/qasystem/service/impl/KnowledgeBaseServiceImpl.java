package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.entity.KbAnswerTemplate;
import me.rainhouse.qasystem.entity.KbCategory;
import me.rainhouse.qasystem.entity.KbDocument;
import me.rainhouse.qasystem.entity.KbQaSourceRef;
import me.rainhouse.qasystem.entity.KbQaEntry;
import me.rainhouse.qasystem.entity.KbSourceReference;
import me.rainhouse.qasystem.mapper.KbAnswerTemplateMapper;
import me.rainhouse.qasystem.mapper.KbCategoryMapper;
import me.rainhouse.qasystem.mapper.KbDocumentMapper;
import me.rainhouse.qasystem.mapper.KbQaSourceRefMapper;
import me.rainhouse.qasystem.mapper.KbSourceReferenceMapper;
import me.rainhouse.qasystem.service.KbQaEntryService;
import me.rainhouse.qasystem.service.KnowledgeBaseService;
import me.rainhouse.qasystem.service.VectorSearchService;
import me.rainhouse.qasystem.service.kb.DataCleanService;
import me.rainhouse.qasystem.service.kb.DocumentParserUtil;
import me.rainhouse.qasystem.service.kb.FaqItem;
import me.rainhouse.qasystem.service.kb.KnowledgeChunker;
import me.rainhouse.qasystem.service.kb.ParsedDocument;
import me.rainhouse.qasystem.service.kb.StructuredFaqExcelParser;
import me.rainhouse.qasystem.service.kb.StructuredFaqItem;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KbDocumentMapper, KbDocument> implements KnowledgeBaseService {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s，。；;）)】\\]>]+");

    @Value("${knowledge.upload-dir:uploads/kb}")
    private String uploadDir;

    @Autowired
    private KbQaEntryService kbQaEntryService;

    @Autowired
    private KbCategoryMapper kbCategoryMapper;

    @Autowired
    private KbAnswerTemplateMapper kbAnswerTemplateMapper;

    @Autowired
    private KbSourceReferenceMapper kbSourceReferenceMapper;

    @Autowired
    private KbQaSourceRefMapper kbQaSourceRefMapper;

    @Autowired
    private DocumentParserUtil documentParserUtil;

    @Autowired
    private DataCleanService dataCleanService;

    @Autowired
    private KnowledgeChunker knowledgeChunker;

    @Autowired
    private StructuredFaqExcelParser structuredFaqExcelParser;

    @Autowired
    private VectorSearchService vectorSearchService;

    @Override
    public KbDocument importDocument(MultipartFile file, String uploaderId, String moduleType) {
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

            Path storedFilePath = resolveStoredPath(storedPath);
            if (structuredFaqExcelParser.supports(document.getFileName())) {
                List<StructuredFaqItem> structuredItems = structuredFaqExcelParser.parse(storedFilePath);
                if (!structuredItems.isEmpty()) {
                    List<KbQaEntry> entries = saveStructuredFaqItems(structuredItems, document.getId(), uploaderId, "Excel");
                    vectorSearchService.upsertEntries(entries);
                    document.setProcessStatus(2);
                    document.setProcessMessage("结构化 FAQ 导入成功，已生成 " + entries.size() + " 条知识库词条");
                    this.updateById(document);
                    log.info("结构化 FAQ Excel 导入成功: documentId={}, entries={}", document.getId(), entries.size());
                    return document;
                }
            }

            ParsedDocument parsedDocument = documentParserUtil.parse(file);
            List<FaqItem> faqItems = dataCleanService.cleanToFaq(parsedDocument.text(), document.getFileName(), categoryPathOptions());
            List<KbQaEntry> entries = knowledgeChunker.chunk(
                    faqItems,
                    document.getId(),
                    uploaderId,
                    normalizeModuleType(moduleType),
                    parsedDocument.sourceType()
            );
            entries.forEach(entry -> normalizeEntryForDocumentCategory(entry, moduleType));

            if (entries.isEmpty()) {
                document.setProcessStatus(3);
                document.setProcessMessage("未能从文档中提取到有效 FAQ 条目");
                this.updateById(document);
                return document;
            }

            kbQaEntryService.saveBatch(entries);
            entries.forEach(this::syncSourceReferences);
            vectorSearchService.upsertEntries(entries);
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
    public KbDocument reprocessDocument(Long documentId, String operatorId, String moduleType) {
        if (documentId == null) {
            throw new IllegalArgumentException("缺少文档ID");
        }
        KbDocument document = this.getById(documentId);
        if (document == null) {
            throw new IllegalArgumentException("解析记录不存在");
        }
        if (!StringUtils.hasText(document.getFileUrl())) {
            throw new IllegalArgumentException("原始文件路径不存在，请重新上传");
        }

        Path storedPath = resolveStoredPath(document.getFileUrl());
        if (!Files.exists(storedPath)) {
            throw new IllegalArgumentException("原始文件已丢失，请重新上传");
        }

        document.setProcessStatus(1);
        document.setProcessMessage("正在重新解析");
        this.updateById(document);

        String createdBy = StringUtils.hasText(operatorId) ? operatorId : document.getUploaderId();
        try {
            if (structuredFaqExcelParser.supports(document.getFileName())) {
                List<StructuredFaqItem> structuredItems = structuredFaqExcelParser.parse(storedPath);
                if (!structuredItems.isEmpty()) {
                    removeEntriesByDocumentId(document.getId());
                    List<KbQaEntry> entries = saveStructuredFaqItems(structuredItems, document.getId(), createdBy, "Excel");
                    vectorSearchService.upsertEntries(entries);
                    document.setProcessStatus(2);
                    document.setProcessMessage("结构化 FAQ 重新导入成功，已生成 " + entries.size() + " 条知识库词条");
                    this.updateById(document);
                    log.info("结构化 FAQ Excel 重新导入成功 documentId={}, entries={}", document.getId(), entries.size());
                    return document;
                }
            }

            ParsedDocument parsedDocument = documentParserUtil.parse(storedPath, document.getFileName());
            List<FaqItem> faqItems = dataCleanService.cleanToFaq(parsedDocument.text(), document.getFileName(), categoryPathOptions());
            List<KbQaEntry> entries = knowledgeChunker.chunk(
                    faqItems,
                    document.getId(),
                    createdBy,
                    normalizeModuleType(moduleType),
                    parsedDocument.sourceType()
            );
            entries.forEach(entry -> normalizeEntryForDocumentCategory(entry, moduleType));

            if (entries.isEmpty()) {
                document.setProcessStatus(3);
                document.setProcessMessage("未能从文档中提取到有效 FAQ 条目");
                this.updateById(document);
                return document;
            }

            removeEntriesByDocumentId(document.getId());
            kbQaEntryService.saveBatch(entries);
            entries.forEach(this::syncSourceReferences);
            vectorSearchService.upsertEntries(entries);
            document.setProcessStatus(2);
            document.setProcessMessage("解析成功，已生成 " + entries.size() + " 条知识库词条");
            this.updateById(document);
            log.info("知识库文档重新解析成功 documentId={}, entries={}", document.getId(), entries.size());
            return document;
        } catch (Exception e) {
            log.error("知识库文档重新解析失败 documentId={}, file={}", document.getId(), document.getFileName(), e);
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

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int deleteDocuments(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<Long> distinctIds = ids.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return 0;
        }

        List<KbDocument> documents = this.listByIds(distinctIds);
        if (documents.isEmpty()) {
            return 0;
        }
        List<Long> existingDocumentIds = documents.stream()
                .map(KbDocument::getId)
                .toList();

        List<KbQaEntry> relatedEntries = kbQaEntryService.list(new LambdaQueryWrapper<KbQaEntry>()
                .in(KbQaEntry::getDocumentId, existingDocumentIds));
        if (!relatedEntries.isEmpty()) {
            List<Long> relatedEntryIds = relatedEntries.stream()
                    .map(KbQaEntry::getId)
                    .toList();
            kbQaEntryService.removeByIds(relatedEntryIds);
            relatedEntryIds.forEach(vectorSearchService::removeEntry);
        }

        boolean removed = this.removeByIds(existingDocumentIds);
        if (removed) {
            documents.forEach(this::deleteStoredFileQuietly);
        }
        return removed ? existingDocumentIds.size() : 0;
    }

    @Override
    public List<KbQaEntry> listEntries(String keyword,
                                       String moduleType,
                                       Integer status,
                                       String sourceType,
                                       String categoryL1,
                                       String categoryL2,
                                       String categoryL3) {
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
        if (StringUtils.hasText(categoryL1)) {
            wrapper.eq(KbQaEntry::getCategoryL1Name, categoryL1.trim());
        }
        if (StringUtils.hasText(categoryL2)) {
            wrapper.eq(KbQaEntry::getCategoryL2Name, categoryL2.trim());
        }
        if (StringUtils.hasText(categoryL3)) {
            wrapper.eq(KbQaEntry::getCategoryL3Name, categoryL3.trim());
        }
        return kbQaEntryService.list(wrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public KbQaEntry createEntry(KbQaEntry entry, String operatorId) {
        validateEntry(entry);
        entry.setId(null);
        entry.setDocumentId(null);
        entry.setQuestion(entry.getQuestion().trim());
        entry.setAnswer(entry.getAnswer().trim());
        entry.setStatus(entry.getStatus() == null ? 1 : entry.getStatus());
        entry.setModuleType(normalizeModuleType(entry.getModuleType()));
        entry.setSourceType(StringUtils.hasText(entry.getSourceType()) ? entry.getSourceType().trim() : "人工录入");
        normalizeEntryCategory(entry);
        normalizeEntryTemplate(entry);
        entry.setCreatedBy(operatorId);
        entry.setCreatedAt(LocalDateTime.now());
        entry.setUpdatedAt(LocalDateTime.now());
        kbQaEntryService.save(entry);
        syncSourceReferences(entry);
        vectorSearchService.upsertEntry(entry);
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
        if (StringUtils.hasText(entry.getCategoryL1Name())) {
            existing.setCategoryL1Name(entry.getCategoryL1Name().trim());
        }
        if (StringUtils.hasText(entry.getCategoryL2Name())) {
            existing.setCategoryL2Name(entry.getCategoryL2Name().trim());
        }
        if (StringUtils.hasText(entry.getCategoryL3Name())) {
            existing.setCategoryL3Name(entry.getCategoryL3Name().trim());
        }
        if (StringUtils.hasText(entry.getTemplateCode())) {
            existing.setTemplateCode(entry.getTemplateCode().trim());
        }
        if (StringUtils.hasText(entry.getSourceTitle())) {
            existing.setSourceTitle(entry.getSourceTitle().trim());
        }
        if (StringUtils.hasText(entry.getSourceUrl())) {
            existing.setSourceUrl(entry.getSourceUrl().trim());
        }
        normalizeEntryCategory(existing);
        normalizeEntryTemplate(existing);
        existing.setUpdatedAt(LocalDateTime.now());
        validateEntry(existing);
        kbQaEntryService.updateById(existing);
        syncSourceReferences(existing);
        vectorSearchService.upsertEntry(existing);
        return existing;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean deleteEntry(Long id) {
        if (id == null) {
            return false;
        }
        KbQaEntry entry = kbQaEntryService.getById(id);
        if (entry == null) {
            return false;
        }
        boolean removed = kbQaEntryService.removeById(entry.getId());
        if (removed) {
            kbQaSourceRefMapper.delete(new LambdaQueryWrapper<KbQaSourceRef>().eq(KbQaSourceRef::getQaEntryId, entry.getId()));
            vectorSearchService.removeEntry(entry.getId());
        }
        return removed;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int deleteEntries(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<Long> distinctIds = ids.stream()
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return 0;
        }

        List<KbQaEntry> existingEntries = kbQaEntryService.listByIds(distinctIds);
        if (existingEntries.isEmpty()) {
            return 0;
        }

        List<Long> existingIds = existingEntries.stream()
                .map(KbQaEntry::getId)
                .toList();
        boolean removed = kbQaEntryService.removeByIds(existingIds);
        if (removed) {
            kbQaSourceRefMapper.delete(new LambdaQueryWrapper<KbQaSourceRef>().in(KbQaSourceRef::getQaEntryId, existingIds));
            existingIds.forEach(vectorSearchService::removeEntry);
        }
        return removed ? existingIds.size() : 0;
    }

    private List<KbQaEntry> saveStructuredFaqItems(List<StructuredFaqItem> items,
                                                   Long documentId,
                                                   String createdBy,
                                                   String sourceType) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        List<KbQaEntry> entries = new ArrayList<>();
        for (StructuredFaqItem item : items) {
            KbQaEntry entry = new KbQaEntry();
            entry.setDocumentId(documentId);
            entry.setCategoryL1Name(item.categoryL1());
            entry.setCategoryL2Name(item.categoryL2());
            entry.setCategoryL3Name(item.categoryL3());
            entry.setQuestion(item.question());
            entry.setAnswer(item.answer());
            entry.setTemplateCode(item.templateCode());
            entry.setStatus(1);
            entry.setModuleType(item.categoryL1());
            entry.setSourceType(sourceType);
            entry.setCreatedBy(createdBy);
            entry.setCreatedAt(now);
            entry.setUpdatedAt(now);
            normalizeEntryCategory(entry);
            normalizeEntryTemplate(entry);
            validateEntry(entry);
            entries.add(entry);
        }
        kbQaEntryService.saveBatch(entries);
        entries.forEach(this::syncSourceReferences);
        return entries;
    }

    private void normalizeEntryForDocumentCategory(KbQaEntry entry, String moduleType) {
        if (hasCompleteCategoryNames(entry) && categoryPathExists(entry)) {
            normalizeEntryCategory(entry);
            normalizeEntryTemplate(entry);
            return;
        }
        String fallbackL1 = StringUtils.hasText(moduleType) ? moduleType.trim() : "待归类";
        entry.setCategoryL1Name(fallbackL1);
        entry.setCategoryL2Name("待归类");
        entry.setCategoryL3Name("待归类");
        normalizeEntryCategory(entry);
        normalizeEntryTemplate(entry);
    }

    private boolean hasCompleteCategoryNames(KbQaEntry entry) {
        return entry != null
                && StringUtils.hasText(entry.getCategoryL1Name())
                && StringUtils.hasText(entry.getCategoryL2Name())
                && StringUtils.hasText(entry.getCategoryL3Name());
    }

    private boolean categoryPathExists(KbQaEntry entry) {
        KbCategory l1 = findCategory(null, entry.getCategoryL1Name().trim(), 1);
        if (l1 == null) {
            return false;
        }
        KbCategory l2 = findCategory(l1.getId(), entry.getCategoryL2Name().trim(), 2);
        if (l2 == null) {
            return false;
        }
        return findCategory(l2.getId(), entry.getCategoryL3Name().trim(), 3) != null;
    }

    private List<String> categoryPathOptions() {
        List<KbCategory> categories = kbCategoryMapper.selectList(new LambdaQueryWrapper<KbCategory>()
                .eq(KbCategory::getStatus, 1)
                .orderByAsc(KbCategory::getLevel)
                .orderByAsc(KbCategory::getSortOrder)
                .orderByAsc(KbCategory::getId));
        if (categories.isEmpty()) {
            return List.of();
        }
        Map<Long, KbCategory> byId = categories.stream()
                .collect(Collectors.toMap(KbCategory::getId, Function.identity(), (left, right) -> left));
        return categories.stream()
                .filter(category -> Integer.valueOf(3).equals(category.getLevel()))
                .map(l3 -> {
                    KbCategory l2 = byId.get(l3.getParentId());
                    KbCategory l1 = l2 == null ? null : byId.get(l2.getParentId());
                    if (l1 == null || l2 == null) {
                        return null;
                    }
                    return l1.getName() + " > " + l2.getName() + " > " + l3.getName();
                })
                .filter(StringUtils::hasText)
                .distinct()
                .limit(120)
                .toList();
    }

    private void normalizeEntryCategory(KbQaEntry entry) {
        if (entry == null) {
            return;
        }
        if (!StringUtils.hasText(entry.getCategoryL1Name()) && StringUtils.hasText(entry.getModuleType())) {
            entry.setCategoryL1Name(entry.getModuleType().trim());
        }
        if (!StringUtils.hasText(entry.getCategoryL1Name())) {
            return;
        }
        KbCategory l1 = ensureCategory(null, entry.getCategoryL1Name().trim(), 1);
        entry.setCategoryL1Id(l1.getId());
        entry.setCategoryL1Name(l1.getName());
        entry.setModuleType(l1.getName());

        if (!StringUtils.hasText(entry.getCategoryL2Name())) {
            entry.setCategoryId(null);
            return;
        }
        KbCategory l2 = ensureCategory(l1.getId(), entry.getCategoryL2Name().trim(), 2);
        entry.setCategoryL2Id(l2.getId());
        entry.setCategoryL2Name(l2.getName());

        if (!StringUtils.hasText(entry.getCategoryL3Name())) {
            entry.setCategoryId(null);
            return;
        }
        KbCategory l3 = ensureCategory(l2.getId(), entry.getCategoryL3Name().trim(), 3);
        entry.setCategoryL3Id(l3.getId());
        entry.setCategoryL3Name(l3.getName());
        entry.setCategoryId(l3.getId());
    }

    private KbCategory ensureCategory(Long parentId, String name, int level) {
        KbCategory existing = findCategory(parentId, name, level);
        if (existing != null) {
            return existing;
        }
        KbCategory category = new KbCategory();
        category.setParentId(parentId);
        category.setName(name);
        category.setLevel(level);
        category.setSortOrder(0);
        category.setStatus(1);
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        kbCategoryMapper.insert(category);
        return category;
    }

    private KbCategory findCategory(Long parentId, String name, int level) {
        LambdaQueryWrapper<KbCategory> wrapper = new LambdaQueryWrapper<KbCategory>()
                .eq(KbCategory::getName, name)
                .eq(KbCategory::getLevel, level);
        if (parentId == null) {
            wrapper.isNull(KbCategory::getParentId);
        } else {
            wrapper.eq(KbCategory::getParentId, parentId);
        }
        return kbCategoryMapper.selectOne(wrapper.last("limit 1"));
    }

    private void normalizeEntryTemplate(KbQaEntry entry) {
        if (entry == null || !StringUtils.hasText(entry.getTemplateCode())) {
            return;
        }
        String templateCode = entry.getTemplateCode().trim();
        KbAnswerTemplate template = kbAnswerTemplateMapper.selectOne(new LambdaQueryWrapper<KbAnswerTemplate>()
                .eq(KbAnswerTemplate::getTemplateCode, templateCode)
                .last("limit 1"));
        if (template == null) {
            template = new KbAnswerTemplate();
            template.setTemplateCode(templateCode);
            template.setTemplateName(templateCode);
            template.setTemplateContent("<答案>");
            template.setStatus(1);
            template.setCreatedAt(LocalDateTime.now());
            template.setUpdatedAt(LocalDateTime.now());
            kbAnswerTemplateMapper.insert(template);
        }
        entry.setTemplateId(template.getId());
        entry.setTemplateCode(template.getTemplateCode());
    }

    private void syncSourceReferences(KbQaEntry entry) {
        if (entry == null || entry.getId() == null) {
            return;
        }
        kbQaSourceRefMapper.delete(new LambdaQueryWrapper<KbQaSourceRef>().eq(KbQaSourceRef::getQaEntryId, entry.getId()));
        List<String> urls = extractUrls(entry);
        if (urls.isEmpty()) {
            return;
        }
        for (int i = 0; i < urls.size(); i++) {
            KbSourceReference source = ensureSourceReference(entry.getSourceTitle(), urls.get(i), entry.getSourceType());
            KbQaSourceRef ref = new KbQaSourceRef();
            ref.setQaEntryId(entry.getId());
            ref.setSourceId(source.getId());
            ref.setSortOrder(i);
            kbQaSourceRefMapper.insert(ref);
        }
        if (!StringUtils.hasText(entry.getSourceUrl())) {
            entry.setSourceUrl(urls.get(0));
            kbQaEntryService.updateById(entry);
        }
    }

    private KbSourceReference ensureSourceReference(String title, String url, String sourceType) {
        KbSourceReference existing = kbSourceReferenceMapper.selectOne(new LambdaQueryWrapper<KbSourceReference>()
                .eq(KbSourceReference::getUrl, url)
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }
        KbSourceReference source = new KbSourceReference();
        source.setTitle(StringUtils.hasText(title) ? title.trim() : null);
        source.setUrl(url);
        source.setSourceType(StringUtils.hasText(sourceType) ? sourceType.trim() : "official_site");
        source.setCreatedAt(LocalDateTime.now());
        kbSourceReferenceMapper.insert(source);
        return source;
    }

    private List<String> extractUrls(KbQaEntry entry) {
        List<String> urls = new ArrayList<>();
        if (StringUtils.hasText(entry.getSourceUrl())) {
            urls.add(entry.getSourceUrl().trim());
        }
        if (StringUtils.hasText(entry.getAnswer())) {
            Matcher matcher = URL_PATTERN.matcher(entry.getAnswer());
            while (matcher.find()) {
                String url = matcher.group();
                if (!urls.contains(url)) {
                    urls.add(url);
                }
            }
        }
        return urls;
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

    private void removeEntriesByDocumentId(Long documentId) {
        List<KbQaEntry> relatedEntries = kbQaEntryService.list(new LambdaQueryWrapper<KbQaEntry>()
                .eq(KbQaEntry::getDocumentId, documentId));
        if (relatedEntries.isEmpty()) {
            return;
        }
        List<Long> relatedEntryIds = relatedEntries.stream()
                .map(KbQaEntry::getId)
                .toList();
        kbQaEntryService.removeByIds(relatedEntryIds);
        kbQaSourceRefMapper.delete(new LambdaQueryWrapper<KbQaSourceRef>().in(KbQaSourceRef::getQaEntryId, relatedEntryIds));
        relatedEntryIds.forEach(vectorSearchService::removeEntry);
    }

    private Path resolveStoredPath(String fileUrl) {
        Path path = Paths.get(fileUrl);
        if (!path.isAbsolute()) {
            path = Paths.get(System.getProperty("user.dir")).resolve(path);
        }
        return path.normalize();
    }

    private void deleteStoredFileQuietly(KbDocument document) {
        if (document == null || !StringUtils.hasText(document.getFileUrl())) {
            return;
        }
        try {
            Path path = Paths.get(document.getFileUrl());
            if (!path.isAbsolute()) {
                path = Paths.get(System.getProperty("user.dir")).resolve(path);
            }
            Files.deleteIfExists(path.normalize());
        } catch (Exception e) {
            log.warn("删除知识库上传文件失败: documentId={}, fileUrl={}, error={}",
                    document.getId(),
                    document.getFileUrl(),
                    e.getMessage());
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
