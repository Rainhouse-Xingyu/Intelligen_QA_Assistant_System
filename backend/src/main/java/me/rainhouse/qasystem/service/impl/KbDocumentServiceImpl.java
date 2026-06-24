package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import me.rainhouse.qasystem.entity.KbDocument;
import me.rainhouse.qasystem.mapper.KbDocumentMapper;
import me.rainhouse.qasystem.service.KbDocumentService;
import me.rainhouse.qasystem.service.KnowledgeBaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class KbDocumentServiceImpl extends ServiceImpl<KbDocumentMapper, KbDocument> implements KbDocumentService {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Override
    public KbDocument uploadAndParse(MultipartFile file, String uploaderId) {
        return knowledgeBaseService.importDocument(file, uploaderId, null);
    }
}
