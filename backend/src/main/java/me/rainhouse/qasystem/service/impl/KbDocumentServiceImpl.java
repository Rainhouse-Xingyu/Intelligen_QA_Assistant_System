package me.rainhouse.qasystem.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import me.rainhouse.qasystem.entity.KbDocument;
import me.rainhouse.qasystem.entity.KbQaEntry;
import me.rainhouse.qasystem.mapper.KbDocumentMapper;
import me.rainhouse.qasystem.service.KbDocumentService;
import me.rainhouse.qasystem.service.KbQaEntryService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class KbDocumentServiceImpl extends ServiceImpl<KbDocumentMapper, KbDocument> implements KbDocumentService {

    @Autowired
    private KbQaEntryService kbQaEntryService;

    @Override
    public KbDocument uploadAndParse(MultipartFile file, Long uploaderId) {
        // 1. 生成并保存一条文件记录
        KbDocument kbDocument = new KbDocument();
        kbDocument.setFileName(file.getOriginalFilename());
        // mock 保存路径
        kbDocument.setFileUrl("/uploads/" + file.getOriginalFilename());
        kbDocument.setUploaderId(uploaderId);
        kbDocument.setProcessStatus(1); // 1-解析中
        kbDocument.setCreatedAt(LocalDateTime.now());
        this.save(kbDocument);

        try {
            // 在实际项目中通常使用 @Async 或者消息队列进行大文件解析，
            // 比赛这里可为了能拿到 InputStream 数据先做同步解析（因为MultipartFile的流不宜异步滞后读）
            parseExcelQA(file.getInputStream(), kbDocument.getId(), uploaderId);
            
            // 解析成功
            kbDocument.setProcessStatus(2); // 2-成功
            this.updateById(kbDocument);
        } catch (Exception e) {
            log.error("知识库文件解析失败, 文件名: {}", file.getOriginalFilename(), e);
            kbDocument.setProcessStatus(3); // 3-失败
            this.updateById(kbDocument);
        }
        
        return kbDocument;
    }

    /**
     * 利用 Apache POI 解析 Excel (.xlsx) 为 QA 词条
     * 假设 Excel 第 1 列是 问题 (Question)，第 2 列是 答案 (Answer)
     */
    private void parseExcelQA(InputStream is, Long documentId, Long uploaderId) throws Exception {
        Workbook workbook = new XSSFWorkbook(is);
        Sheet sheet = workbook.getSheetAt(0); // 取第一个页签
        
        List<KbQaEntry> entries = new ArrayList<>();
        
        for (Row row : sheet) {
            // 跳过表头(假设第一行是表头)
            if (row.getRowNum() == 0) {
                continue;
            }
            
            Cell qCell = row.getCell(0);
            Cell aCell = row.getCell(1);
            
            if (qCell != null && aCell != null) {
                // POI 获取字符串可能会有格式问题，需视情况处理，这里粗略获取
                qCell.setCellType(CellType.STRING);
                aCell.setCellType(CellType.STRING);
                
                String qStr = qCell.getStringCellValue();
                String aStr = aCell.getStringCellValue();
                
                if (qStr != null && !qStr.trim().isEmpty() && aStr != null && !aStr.trim().isEmpty()) {
                    KbQaEntry entry = new KbQaEntry();
                    entry.setDocumentId(documentId);
                    entry.setQuestion(qStr.trim());
                    entry.setAnswer(aStr.trim());
                    entry.setStatus(1); // 默认启用
                    entry.setCreatedBy(uploaderId);
                    entry.setCreatedAt(LocalDateTime.now());
                    entry.setUpdatedAt(LocalDateTime.now());
                    entries.add(entry);
                }
            }
        }
        
        workbook.close();
        is.close();
        
        // 批量全量插入问答库 (CRUD 的 C)
        if (!entries.isEmpty()) {
            kbQaEntryService.saveBatch(entries);
            log.info("成功从文档 [id={}] 提取并保存了 {} 条问答词条", documentId, entries.size());
        }
    }
}