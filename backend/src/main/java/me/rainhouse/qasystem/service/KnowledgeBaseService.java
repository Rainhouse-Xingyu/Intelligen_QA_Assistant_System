package me.rainhouse.qasystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.rainhouse.qasystem.entity.KbCategory;
import me.rainhouse.qasystem.entity.KbDocument;
import me.rainhouse.qasystem.entity.KbQaEntry;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeBaseService extends IService<KbDocument> {

    KbDocument importDocument(MultipartFile file, String uploaderId, String moduleType);

    KbDocument importCommonQuestions(MultipartFile file, String uploaderId, String moduleType);

    KbDocument reprocessDocument(Long documentId, String operatorId, String moduleType);

    List<KbDocument> listDocuments(Integer processStatus);

    List<KbCategory> listCategories();

    int deleteDocuments(List<Long> ids);

    List<KbQaEntry> listEntries(String keyword,
                                String moduleType,
                                Integer status,
                                String sourceType,
                                String categoryL1,
                                String categoryL2,
                                String categoryL3);

    KbQaEntry createEntry(KbQaEntry entry, String operatorId);

    KbQaEntry updateEntry(KbQaEntry entry);

    boolean deleteEntry(Long id);

    int deleteEntries(List<Long> ids);
}
