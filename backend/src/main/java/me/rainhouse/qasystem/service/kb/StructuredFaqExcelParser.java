package me.rainhouse.qasystem.service.kb;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class StructuredFaqExcelParser {

    private final DataFormatter dataFormatter = new DataFormatter();

    public boolean supports(String filename) {
        if (!StringUtils.hasText(filename)) {
            return false;
        }
        String lowerName = filename.toLowerCase(Locale.ROOT);
        return lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls");
    }

    public List<StructuredFaqItem> parse(Path path) {
        try (InputStream inputStream = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() == 0 ? null : workbook.getSheetAt(0);
            if (sheet == null || !looksLikeStructuredFaqHeader(sheet.getRow(sheet.getFirstRowNum()))) {
                return List.of();
            }
            String defaultCategoryL1 = firstNonBlankCategoryL1(sheet);
            String currentCategoryL1 = defaultCategoryL1;
            String currentCategoryL2 = "";
            String currentCategoryL3 = "";
            List<StructuredFaqItem> items = new ArrayList<>();
            for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isBlankRow(row)) {
                    continue;
                }
                String categoryL1 = readCell(row.getCell(0));
                String categoryL2 = readCell(row.getCell(1));
                String categoryL3 = readCell(row.getCell(2));
                String question = readCell(row.getCell(3));
                String answer = readCell(row.getCell(4));
                String templateCode = readCell(row.getCell(5));

                if (StringUtils.hasText(categoryL1)) {
                    currentCategoryL1 = categoryL1.trim();
                }
                if (StringUtils.hasText(categoryL2)) {
                    currentCategoryL2 = categoryL2.trim();
                }
                if (StringUtils.hasText(categoryL3)) {
                    currentCategoryL3 = categoryL3.trim();
                }
                categoryL1 = currentCategoryL1;
                categoryL2 = currentCategoryL2;
                categoryL3 = currentCategoryL3;

                if (!StringUtils.hasText(categoryL1)
                        || !StringUtils.hasText(categoryL2)
                        || !StringUtils.hasText(categoryL3)
                        || !StringUtils.hasText(question)
                        || !StringUtils.hasText(answer)) {
                    throw new IllegalArgumentException("结构化 FAQ 模板第 " + (i + 1) + " 行缺少一级分类、二级分类、三级分类、常见问题或答案");
                }
                items.add(new StructuredFaqItem(
                        categoryL1.trim(),
                        categoryL2.trim(),
                        categoryL3.trim(),
                        question.trim(),
                        answer.trim(),
                        StringUtils.hasText(templateCode) ? templateCode.trim() : null
                ));
            }
            return items;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("结构化 FAQ Excel 解析失败: " + e.getMessage(), e);
        }
    }

    private boolean looksLikeStructuredFaqHeader(Row header) {
        if (header == null) {
            return false;
        }
        return "一级分类".equals(readCell(header.getCell(0)))
                && "二级分类".equals(readCell(header.getCell(1)))
                && "三级分类".equals(readCell(header.getCell(2)))
                && "常见问题".equals(readCell(header.getCell(3)))
                && "答案".equals(readCell(header.getCell(4)));
    }

    private String firstNonBlankCategoryL1(Sheet sheet) {
        for (int i = sheet.getFirstRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            String categoryL1 = readCell(row.getCell(0));
            if (StringUtils.hasText(categoryL1)) {
                return categoryL1.trim();
            }
        }
        return "";
    }

    private boolean isBlankRow(Row row) {
        for (int i = 0; i <= 5; i++) {
            if (StringUtils.hasText(readCell(row.getCell(i)))) {
                return false;
            }
        }
        return true;
    }

    private String readCell(Cell cell) {
        if (cell == null) {
            return "";
        }
        return dataFormatter.formatCellValue(cell).trim();
    }
}
