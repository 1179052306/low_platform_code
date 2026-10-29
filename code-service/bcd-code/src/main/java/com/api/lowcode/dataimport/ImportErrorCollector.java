package com.api.lowcode.dataimport;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.dataimport.parse.ParsedDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 错误收集与错误报告文件生成。
 * 内存错误上限 5000 条（超出聚合计数），终态时生成"原始数据 + 错误原因"列的 xlsx。
 */
@Slf4j
@Component
public class ImportErrorCollector {

    private static final int MAX_ERRORS = 5000;

    private final List<ParsedDocument.RowError> errors = new ArrayList<>();
    private int overflowCount = 0;
    /** 原始数据行缓存（错误行重导需要，行号→行内容 + 归属主记录行号） */
    private final List<Object[]> rawRows = new ArrayList<>();
    private String[] headerLine;

    public void reset(String[] header) {
        errors.clear();
        overflowCount = 0;
        rawRows.clear();
        headerLine = header;
    }

    public void add(int rowNo, String fieldName, String reason) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(new ParsedDocument.RowError(rowNo, fieldName, reason));
        } else {
            overflowCount++;
        }
    }

    public void addRawRow(int rowNo, String[] cells, int masterRowNo) {
        Object[] entry = new Object[cells.length + 2];
        entry[0] = rowNo;
        System.arraycopy(cells, 0, entry, 1, cells.length);
        entry[cells.length + 1] = masterRowNo;
        rawRows.add(entry);
    }

    public boolean hasErrors() {
        return !errors.isEmpty() || overflowCount > 0;
    }

    public int size() {
        return errors.size() + overflowCount;
    }

    public List<ParsedDocument.RowError> getErrors() {
        return errors;
    }

    /** 错误消息聚合（透传前端，超限聚合提示） */
    public List<String> messages(int limit) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < Math.min(errors.size(), limit); i++) {
            list.add(errors.get(i).message());
        }
        if (overflowCount > 0) {
            list.add("另有 " + overflowCount + " 条错误未展示（下载错误报告查看全部）");
        }
        return list;
    }

    /** 行号 → 错误原因串（同一行多错误分号连接，带字段名定位） */
    private String errorsAtRow(int rowNo) {
        StringBuilder sb = new StringBuilder();
        for (ParsedDocument.RowError e : errors) {
            if (e.rowNo == rowNo) {
                if (sb.length() > 0) sb.append("；");
                if (e.fieldName != null && !e.fieldName.isEmpty()) {
                    sb.append("「").append(e.fieldName).append("」");
                }
                sb.append(e.reason);
            }
        }
        return sb.toString();
    }

    /** 生成错误报告 xlsx（原始行 + 追加错误原因列），返回文件字节；无错误返回 null */
    public byte[] buildErrorFile() throws Exception {
        if (!hasErrors()) return null;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("错误报告");
            Font headFont = wb.createFont();
            headFont.setBold(true);
            headFont.setColor(Font.COLOR_RED);
            CellStyle headStyle = wb.createCellStyle();
            headStyle.setFont(headFont);

            Row head = sheet.createRow(0);
            int col = 0;
            head.createCell(col).setCellValue("行号");
            col++;
            if (headerLine != null) {
                for (String h : headerLine) {
                    head.createCell(col).setCellValue(h);
                    col++;
                }
            }
            head.createCell(col).setCellValue("错误原因");
            head.getCell(col).setCellStyle(headStyle);
            col++;
            head.createCell(col).setCellValue("所属主记录行号");
            sheet.setColumnWidth(col - 2, 40 * 256);

            int rowIdx = 1;
            for (Object[] entry : rawRows) {
                int rowNo = (Integer) entry[0];
                String reason = errorsAtRow(rowNo);
                if (reason.isEmpty()) continue;
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(rowNo);
                int c = 1;
                for (int i = 1; i < entry.length - 1; i++) {
                    Object v = entry[i];
                    row.createCell(c).setCellValue(v != null ? String.valueOf(v) : "");
                    c++;
                }
                row.createCell(c).setCellValue(reason);
                c++;
                row.createCell(c).setCellValue(String.valueOf(entry[entry.length - 1]));
            }
            if (rowIdx == 1) {
                Row row = sheet.createRow(rowIdx);
                row.createCell(0).setCellValue("错误明细");
                row.createCell(1).setCellValue("共 " + size() + " 条");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** 错误清单 JSON（前端弹窗展示） */
    public JSONArray toJsonArray(int limit) {
        JSONArray arr = new JSONArray();
        for (int i = 0; i < Math.min(errors.size(), limit); i++) {
            arr.add(errors.get(i).toJson());
        }
        return arr;
    }

    public List<Object[]> getRawRows() {
        return rawRows;
    }

    public static InputStream fileStream(String path) throws Exception {
        return new FileInputStream(new File(path));
    }
}