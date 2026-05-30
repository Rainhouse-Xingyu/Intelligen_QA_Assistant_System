package me.rainhouse.qasystem.service;

import com.baomidou.mybatisplus.extension.service.IService;
import me.rainhouse.qasystem.entity.KbDocument;
import me.rainhouse.qasystem.entity.KbQaEntry;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeBaseService extends IService<KbDocument> {

    KbDocument importDocument(MultipartFile file, Long uploaderId, String moduleType);

    List<KbDocument> listDocuments(Integer processStatus);

    List<KbQaEntry> listEntries(String keyword, String moduleType, Integer status, String sourceType);

    KbQaEntry createEntry(KbQaEntry entry, Long operatorId);

    KbQaEntry updateEntry(KbQaEntry entry);

    boolean disableEntry(Long id);
}
