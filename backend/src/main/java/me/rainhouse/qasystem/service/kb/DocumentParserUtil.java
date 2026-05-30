package me.rainhouse.qasystem.service.kb;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class DocumentParserUtil {

    private final DataFormatter dataFormatter = new DataFormatter();

    public ParsedDocument parse(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String lowerName = filename.toLowerCase(Locale.ROOT);
        try {
            if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
                return new ParsedDocument(parseExcel(file.getInputStream()), "Excel");
            }
            if (lowerName.endsWith(".docx")) {
                return new ParsedDocument(parseDocx(file.getInputStream()), "Word");
            }
            if (lowerName.endsWith(".doc")) {
                return new ParsedDocument(parseDoc(file.getInputStream()), "Word");
            }
            if (lowerName.endsWith(".pdf")) {
                return new ParsedDocument(parsePdf(file.getInputStream()), "PDF");
            }
            if (lowerName.endsWith(".txt") || lowerName.endsWith(".md")) {
                return new ParsedDocument(new String(file.getBytes(), StandardCharsets.UTF_8), "FAQ");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("文件解析失败: " + e.getMessage(), e);
        }

        throw new IllegalArgumentException("暂不支持该文件类型，仅支持 xlsx、xls、docx、doc、pdf、txt、md");
    }

    private String parseExcel(InputStream inputStream) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            for (Sheet sheet : workbook) {
                for (Row row : sheet) {
                    String first = readCell(row.getCell(0));
                    String second = readCell(row.getCell(1));
                    String third = readCell(row.getCell(2));
                    if (!StringUtils.hasText(first) && !StringUtils.hasText(second)) {
                        continue;
                    }
                    if (looksLikeHeader(first, second)) {
                        continue;
                    }

                    if (StringUtils.hasText(second)) {
                        builder.append("Q: ").append(first.trim()).append('\n');
                        builder.append("A: ").append(second.trim());
                        if (StringUtils.hasText(third)) {
                            builder.append('\n').append(third.trim());
                        }
                        builder.append("\n\n");
                    } else {
                        builder.append(first.trim()).append("\n\n");
                    }
                }
            }
        }
        return builder.toString();
    }

    private String parseDocx(InputStream inputStream) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (XWPFDocument document = new XWPFDocument(inputStream)) {
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                appendLine(builder, paragraph.getText());
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    StringBuilder rowText = new StringBuilder();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        if (!rowText.isEmpty()) {
                            rowText.append(" | ");
                        }
                        rowText.append(cell.getText());
                    }
                    appendLine(builder, rowText.toString());
                }
            }
        }
        return builder.toString();
    }

    private String parseDoc(InputStream inputStream) throws Exception {
        try (HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String parsePdf(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private String readCell(Cell cell) {
        if (cell == null) {
            return "";
        }
        return dataFormatter.formatCellValue(cell).trim();
    }

    private boolean looksLikeHeader(String first, String second) {
        String text = (first + " " + second).toLowerCase(Locale.ROOT);
        return (text.contains("question") || text.contains("问题"))
                && (text.contains("answer") || text.contains("答案"));
    }

    private void appendLine(StringBuilder builder, String line) {
        if (StringUtils.hasText(line)) {
            builder.append(line.trim()).append('\n');
        }
    }
}
