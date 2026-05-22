package me.rainhouse.qasystem.controller;

import me.rainhouse.qasystem.common.result.Result;
import me.rainhouse.qasystem.entity.KbDocument;
import me.rainhouse.qasystem.entity.KbQaEntry;
import me.rainhouse.qasystem.service.KbDocumentService;
import me.rainhouse.qasystem.service.KbQaEntryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    @Autowired
    private KbDocumentService kbDocumentService;

    @Autowired
    private KbQaEntryService kbQaEntryService;

    // 从请求 attributes 中获取 userId (模拟后台管理员)
    private Long getAdminIdOpt(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj != null) {
            return Long.valueOf(userIdObj.toString());
        }
        return 0L; 
    }

    /**
     * 【3.1模块】基于 Apache POI 上传并解析文件
     */
    @PostMapping("/upload")
    public Result<KbDocument> uploadDocument(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.endsWith(".xlsx")) {
            return Result.error("当前仅支持解析 .xlsx 类型的 Excel 文件。");
        }

        Long adminId = getAdminIdOpt(request);
        KbDocument doc = kbDocumentService.uploadAndParse(file, adminId);
        
        if (doc.getProcessStatus() == 2) {
            return Result.success(doc);
        } else {
            return Result.error("文件解析失败，请检查文件格式是否规范。");
        }
    }

    /**
     * 【3.2模块】查询所有已落库的问答词条
     */
    @GetMapping("/entries")
    public Result<List<KbQaEntry>> listEntries() {
        return Result.success(kbQaEntryService.list());
    }

    /**
     * 【3.2模块】对指定词条进行修改
     */
    @PutMapping("/entries")
    public Result<String> updateEntry(@RequestBody KbQaEntry kbQaEntry) {
        if (kbQaEntry.getId() == null) {
            return Result.error("缺少词条ID");
        }
        kbQaEntryService.updateById(kbQaEntry);
        // 此处可调用 Coze OpenAPI 进行工作流知识库同步更新，以便真正生效
        return Result.success("词条更新成功");
    }

    /**
     * 【3.2模块】手动逐条新增知识库问答
     */
    @PostMapping("/entries")
    public Result<String> createEntry(@RequestBody KbQaEntry kbQaEntry, HttpServletRequest request) {
        Long adminId = getAdminIdOpt(request);
        kbQaEntry.setCreatedBy(adminId);
        kbQaEntryService.save(kbQaEntry);
        // 此处亦可调用 Coze OpenAPI 往远端知识库添加记录
        return Result.success("词条创建成功");
    }

    /**
     * 【3.2模块】禁用或删除知识库词条
     */
    @DeleteMapping("/entries/{id}")
    public Result<String> disableEntry(@PathVariable Long id) {
        KbQaEntry entry = kbQaEntryService.getById(id);
        if (entry != null) {
            // 逻辑删除或禁用
            entry.setStatus(0);
            kbQaEntryService.updateById(entry);
            return Result.success("词条已成功禁用/删除");
        }
        return Result.error("词条不存在");
    }
}