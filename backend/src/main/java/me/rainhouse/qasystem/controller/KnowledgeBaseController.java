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
    private String getAdminIdOpt(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj != null) {
            return userIdObj.toString();
        }
        return "0";
    }

    /**
     * 【3.1模块】上传并解析 Word/Excel/PDF/TXT 文件，清洗为 FAQ 后落库。
     */
    @PostMapping("/upload")
    public Result<KbDocument> uploadDocument(@RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "moduleType", required = false) String moduleType,
                                             HttpServletRequest request) {
        try {
            String adminId = getAdminIdOpt(request);
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

    @PostMapping("/documents/{id}/retry")
    public Result<KbDocument> retryDocument(@PathVariable Long id,
                                            @RequestParam(value = "moduleType", required = false) String moduleType,
                                            HttpServletRequest request) {
        try {
            String adminId = getAdminIdOpt(request);
            KbDocument doc = knowledgeBaseService.reprocessDocument(id, adminId, moduleType);
            if (doc.getProcessStatus() == 2) {
                return Result.success(doc);
            }
            return Result.error(doc.getProcessMessage() != null ? doc.getProcessMessage() : "文件重新解析失败");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 【3.1模块】批量删除解析记录，并同步删除该文档生成的问答词条和向量数据。
     */
    @PostMapping("/documents/batch-delete")
    public Result<Integer> deleteDocuments(@RequestBody List<Long> ids) {
        int removedCount = knowledgeBaseService.deleteDocuments(ids);
        if (removedCount > 0) {
            return Result.success(removedCount);
        }
        return Result.error("未找到可删除的解析记录");
    }

    /**
     * 【3.2模块】查询已落库的问答词条，支持关键字、模块、状态、来源过滤。
     */
    @GetMapping("/entries")
    public Result<List<KbQaEntry>> listEntries(@RequestParam(value = "keyword", required = false) String keyword,
                                               @RequestParam(value = "moduleType", required = false) String moduleType,
                                               @RequestParam(value = "status", required = false) Integer status,
                                               @RequestParam(value = "sourceType", required = false) String sourceType,
                                               @RequestParam(value = "categoryL1", required = false) String categoryL1,
                                               @RequestParam(value = "categoryL2", required = false) String categoryL2,
                                               @RequestParam(value = "categoryL3", required = false) String categoryL3) {
        return Result.success(knowledgeBaseService.listEntries(
                keyword,
                moduleType,
                status,
                sourceType,
                categoryL1,
                categoryL2,
                categoryL3
        ));
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
            String adminId = getAdminIdOpt(request);
            return Result.success(knowledgeBaseService.createEntry(kbQaEntry, adminId));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 【3.2模块】真实删除知识库词条，并同步删除向量库数据
     */
    @DeleteMapping("/entries/{id}")
    public Result<String> deleteEntry(@PathVariable Long id) {
        if (knowledgeBaseService.deleteEntry(id)) {
            return Result.success("词条已成功删除");
        }
        return Result.error("词条不存在");
    }

    /**
     * 【3.2模块】批量真实删除知识库词条，并同步删除向量库数据
     */
    @PostMapping("/entries/batch-delete")
    public Result<Integer> deleteEntries(@RequestBody List<Long> ids) {
        int removedCount = knowledgeBaseService.deleteEntries(ids);
        if (removedCount > 0) {
            return Result.success(removedCount);
        }
        return Result.error("未找到可删除的词条");
    }
}
