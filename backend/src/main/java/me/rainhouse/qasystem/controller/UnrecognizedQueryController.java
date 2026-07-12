package me.rainhouse.qasystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import me.rainhouse.qasystem.common.result.Result;
import me.rainhouse.qasystem.entity.UnrecognizedQuery;
import me.rainhouse.qasystem.service.UnrecognizedQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/admin/unrecognized")
public class UnrecognizedQueryController {

    @Autowired
    private UnrecognizedQueryService unrecognizedQueryService;

    /**
     * 【4.3 模块】面向管理员的未识别问题列表分页
     */
    @GetMapping("/list")
    public Result<Page<UnrecognizedQuery>> list(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer status) {
            
        QueryWrapper<UnrecognizedQuery> qw = new QueryWrapper<>();
        if (status != null) {
            qw.eq("status", status);
        }
        qw.orderByDesc("create_time");
        
        Page<UnrecognizedQuery> page = unrecognizedQueryService.page(new Page<>(current, size), qw);
        return Result.success(page);
    }

    /**
     * 【4.3 模块】面向管理员：处理/更新问题状态
     */
    @PostMapping("/update-status")
    public Result<String> updateStatus(
            @RequestParam("id") Long id,
            @RequestParam("status") Integer status,
            HttpServletRequest request) {
        unrecognizedQueryService.updateState(id, status, getUserIdOpt(request));
        return Result.success("状态更新成功");
    }

    @PostMapping("/batch-update-status")
    public Result<Integer> batchUpdateStatus(@RequestBody BatchStatusRequest body,
                                             HttpServletRequest request) {
        if (body == null || body.getIds() == null || body.getIds().isEmpty()) {
            return Result.error("请选择要处理的问题");
        }
        Long processUser = getUserIdOpt(request);
        int count = 0;
        for (Long id : body.getIds()) {
            if (id == null) {
                continue;
            }
            unrecognizedQueryService.updateState(id, body.getStatus() == null ? 1 : body.getStatus(), processUser);
            count++;
        }
        return Result.success(count);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String ids,
                                         @RequestParam(required = false) Integer status) throws IOException {
        QueryWrapper<UnrecognizedQuery> qw = new QueryWrapper<>();
        List<Long> idList = parseIds(ids);
        if (!idList.isEmpty()) {
            qw.in("id", idList);
        }
        if (status != null) {
            qw.eq("status", status);
        }
        qw.orderByDesc("create_time");
        List<UnrecognizedQuery> rows = unrecognizedQueryService.list(qw);
        byte[] bytes = buildExcel(rows);
        String filename = URLEncoder.encode("未识别问题列表.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    private List<Long> parseIds(String ids) {
        if (ids == null || ids.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .map(text -> {
                    try {
                        return Long.valueOf(text);
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private byte[] buildExcel(List<UnrecognizedQuery> rows) throws IOException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("未识别问题");
            String[] headers = {"ID", "问题", "模块", "最高相似度", "频率", "状态", "创建时间", "处理时间", "处理人"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            for (int i = 0; i < rows.size(); i++) {
                UnrecognizedQuery item = rows.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(item.getId() == null ? "" : String.valueOf(item.getId()));
                row.createCell(1).setCellValue(nullToEmpty(item.getQuestionText()));
                row.createCell(2).setCellValue(nullToEmpty(item.getModuleType()));
                row.createCell(3).setCellValue(item.getTopScore() == null ? "" : item.getTopScore().toPlainString());
                row.createCell(4).setCellValue(item.getFrequency() == null ? 0 : item.getFrequency());
                row.createCell(5).setCellValue(item.getStatus() != null && item.getStatus() == 1 ? "已处理" : "未处理");
                row.createCell(6).setCellValue(item.getCreateTime() == null ? "" : formatter.format(item.getCreateTime()));
                row.createCell(7).setCellValue(item.getProcessTime() == null ? "" : formatter.format(item.getProcessTime()));
                row.createCell(8).setCellValue(item.getProcessUser() == null ? "" : String.valueOf(item.getProcessUser()));
            }
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Long getUserIdOpt(HttpServletRequest request) {
        Object userIdObj = request.getAttribute("userId");
        if (userIdObj != null) {
            return Long.valueOf(userIdObj.toString());
        }
        return null;
    }

    public static class BatchStatusRequest {
        private List<Long> ids;
        private Integer status;

        public List<Long> getIds() {
            return ids;
        }

        public void setIds(List<Long> ids) {
            this.ids = ids;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }
    }
}
