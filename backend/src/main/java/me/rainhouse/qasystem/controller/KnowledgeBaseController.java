package me.rainhouse.qasystem.controller;

import me.rainhouse.qasystem.common.result.Result;
import me.rainhouse.qasystem.entity.KbDocument;
import me.rainhouse.qasystem.entity.KbQaEntry;
import me.rainhouse.qasystem.service.KnowledgeBaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    // 从请求 attributes 中获取 userId (模拟后台管理员)
    private Long getAdminIdOpt(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj != null) {
            return Long.valueOf(userIdObj.toString());
        }
        return 0L; 
    }

    /**
     * 【3.1模块】上传并解析 Word/Excel/PDF/TXT 文件，清洗为 FAQ 后落库。
     */
    @PostMapping("/upload")
    public Result<KbDocument> uploadDocument(@RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "moduleType", required = false) String moduleType,
                                             HttpServletRequest request) {
        try {
            Long adminId = getAdminIdOpt(request);
            KbDocument doc = knowledgeBaseService.importDocument(file, adminId, moduleType);
            if (doc.getProcessStatus() == 2) {
                return Result.success(doc);
            }
            return Result.error(doc.getProcessMessage() != null ? doc.getProcessMessage() : "文件解析失败");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 【3.1模块】查询文档解析记录。
     */
    @GetMapping("/documents")
    public Result<List<KbDocument>> listDocuments(@RequestParam(value = "processStatus", required = false) Integer processStatus) {
        return Result.success(knowledgeBaseService.listDocuments(processStatus));
    }

    /**
     * 【3.2模块】查询已落库的问答词条，支持关键字、模块、状态、来源过滤。
     */
    @GetMapping("/entries")
    public Result<List<KbQaEntry>> listEntries(@RequestParam(value = "keyword", required = false) String keyword,
                                               @RequestParam(value = "moduleType", required = false) String moduleType,
                                               @RequestParam(value = "status", required = false) Integer status,
                                               @RequestParam(value = "sourceType", required = false) String sourceType) {
        return Result.success(knowledgeBaseService.listEntries(keyword, moduleType, status, sourceType));
    }

    /**
     * 【3.2模块】对指定词条进行修改
     */
    @PutMapping("/entries")
    public Result<String> updateEntry(@RequestBody KbQaEntry kbQaEntry) {
        try {
            knowledgeBaseService.updateEntry(kbQaEntry);
            return Result.success("词条更新成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 【3.2模块】手动逐条新增知识库问答
     */
    @PostMapping("/entries")
    public Result<KbQaEntry> createEntry(@RequestBody KbQaEntry kbQaEntry, HttpServletRequest request) {
        try {
            Long adminId = getAdminIdOpt(request);
            return Result.success(knowledgeBaseService.createEntry(kbQaEntry, adminId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 【3.2模块】禁用或删除知识库词条
     */
    @DeleteMapping("/entries/{id}")
    public Result<String> disableEntry(@PathVariable Long id) {
        if (knowledgeBaseService.disableEntry(id)) {
            return Result.success("词条已成功禁用/删除");
        }
        return Result.error("词条不存在");
    }
}
