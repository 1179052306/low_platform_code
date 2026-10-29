package com.api.lowcode.dataimport.parse;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.ooxml.util.SAXHelper;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POI SAX 流式解析：逐行读 xlsx（不整本驻留内存），
 * 表头映射 + 主从分组（主表区任一列非空开新组，留空归属上一组）。
 * 输出 ParsedDocument（值为 trim 后字符串，类型转换在校验阶段按元数据执行）。
 */
@Slf4j
@Component
public class ImportParseService {

    /** 解析上下文（模板结构同源信息） */
    public static class ParseContext {
        public String bizCode;
        public List<String> masterFields = new ArrayList<>();
        /** 明细表名 → 字段列表（按模板生成顺序） */
        public List<Map.Entry<String, List<String>>> detailTables = new ArrayList<>();
        /** 错误收集器（解析错误 + 原始行缓存） */
        public com.api.lowcode.dataimport.ImportErrorCollector errorCollector;
        /** 列头显示名 → 字段名映射（模板列头为 label 时反查；字段名表头也兼容） */
        public Map<String, String> headerLabels = new LinkedHashMap<>();
    }

    private static class SheetRow {
        int rowIndex;
        Map<Integer, String> cells = new LinkedHashMap<>();
    }

    public ParsedDocument parse(InputStream in, ParseContext ctx) throws Exception {
        ParsedDocument doc = new ParsedDocument();
        List<String> headerOrder = new ArrayList<>();
        headerOrder.addAll(ctx.masterFields);
        for (Map.Entry<String, List<String>> dt : ctx.detailTables) {
            doc.detailTableOrder.add(dt.getKey());
            headerOrder.addAll(dt.getValue());
        }

        OPCPackage pkg = OPCPackage.open(in);
        try {
            ReadOnlySharedStringsTable sst = new ReadOnlySharedStringsTable(pkg);
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator) reader.getSheetsData();
            if (!iter.hasNext()) {
                throw new IllegalArgumentException("Excel 文件无工作表");
            }
            try (InputStream sheetStream = iter.next()) {
                InputSource source = new InputSource(sheetStream);
                XMLReader parser = SAXHelper.newXMLReader();
                ContentHandler handler = new XSSFSheetXMLHandler(
                        styles, null, sst, new RowHandler(doc, ctx, headerOrder),
                        new DataFormatter(), false);
                parser.setContentHandler(handler);
                parser.parse(source);
            }
        } finally {
            try {
                pkg.close();
            } catch (Exception ignore) {
                // 关闭失败不影响解析结果
            }
        }
        return doc;
    }

    /** SAX 行回调：逐行处理（表头识别 → 类型判断 → 主从分组） */
    private class RowHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final ParsedDocument doc;
        private final ParseContext ctx;
        private final List<String> headerOrder;
        private SheetRow current;
        /** 表头行号（单表 0，主从 1），-1 未识别 */
        private int headerRowIdx = -1;
        /** 是否主从模式 */
        private boolean masterDetailMode;
        /** 表头列索引 → 字段名 */
        private final Map<Integer, String> colField = new LinkedHashMap<>();
        /** 主表列数（主从分组边界） */
        private int masterColCount;
        /** 当前主记录（明细行归属） */
        private ParsedDocument.MasterRecord currentRecord;
        private long dataRowCount = 0;
        /** 最近一次见到的行号（跳过空行时保持正确行号） */
        private int lastRowSeen = -1;

        RowHandler(ParsedDocument doc, ParseContext ctx, List<String> headerOrder) {
            this.doc = doc;
            this.ctx = ctx;
            this.headerOrder = headerOrder;
            this.masterDetailMode = !ctx.detailTables.isEmpty();
            this.masterColCount = ctx.masterFields.size();
        }

        @Override
        public void startRow(int rowIndex) {
            current = new SheetRow();
            current.rowIndex = rowIndex;
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int col = new CellReference(cellReference).getCol();
            if (formattedValue != null && !formattedValue.trim().isEmpty()) {
                current.cells.put(col, formattedValue.trim());
            }
        }

        @Override
        public void endRow(int rowIndex) {
            lastRowSeen = rowIndex;
            handleRow(current);
            current = null;
        }

        private void handleRow(SheetRow row) {
            // 表头未识别：前两行内识别（主从 row0 是区块标注行，row1 表头；单表 row0 表头）
            if (headerRowIdx == -1) {
                String first = row.cells.get(0);
                if (masterDetailMode && first != null && first.startsWith("主表：")) {
                    return; // 区块标注行，跳过（表头在下一行）
                }
                // row0 无区块标注：row0 即表头（兼容用户自制/删掉区块行的文件）
                buildHeader(row);
                return;
            }
            if (row.cells.isEmpty()) {
                return; // 空行跳过
            }
            dataRowCount++;
            doc.estimatedTotal = dataRowCount;

            // 判定主表区是否有值（列索引 < masterColCount）
            boolean masterHasValue = false;
            for (int col : row.cells.keySet()) {
                if (col < masterColCount) {
                    masterHasValue = true;
                    break;
                }
            }

            String[] rawCells = new String[Math.max(row.cells.size(), colField.size())];
            int masterRowNo;

            if (!masterDetailMode) {
                // 单表模式：每行都是主记录
                ParsedDocument.MasterRecord rec = new ParsedDocument.MasterRecord();
                rec.excelRowNo = rowIndex() + 1;
                fillRecord(row, rec);
                doc.records.add(rec);
                currentRecord = rec;
                masterRowNo = rec.excelRowNo;
            } else if (masterHasValue) {
                // 新主记录行（可能同时携带明细数据）
                ParsedDocument.MasterRecord rec = new ParsedDocument.MasterRecord();
                rec.excelRowNo = rowIndex() + 1;
                fillRecord(row, rec);
                doc.records.add(rec);
                currentRecord = rec;
                masterRowNo = rec.excelRowNo;
                if (rowHasDetailCells(row)) {
                    appendDetail(row, rec);
                }
            } else if (currentRecord != null) {
                // 明细行：归属当前主记录
                appendDetail(row, currentRecord);
                masterRowNo = currentRecord.excelRowNo;
            } else {
                ctx.errorCollector.add(rowIndex() + 1, "", "明细行缺少主记录（主表列全为空且无前置主记录）");
                masterRowNo = rowIndex() + 1;
            }

            // 原始行缓存（错误报告）
            for (Map.Entry<Integer, String> e : row.cells.entrySet()) {
                int idx = e.getKey();
                if (idx < rawCells.length) rawCells[idx] = e.getValue();
            }
            ctx.errorCollector.addRawRow(rowIndex() + 1, rawCells, masterRowNo);
        }

        private boolean rowHasDetailCells(SheetRow row) {
            for (int col : row.cells.keySet()) {
                if (col >= masterColCount) return true;
            }
            return false;
        }

        private int rowIndex() {
            return current != null ? current.rowIndex : lastRowSeen;
        }

        private void buildHeader(SheetRow row) {
            headerRowIdx = row.rowIndex;
            for (Map.Entry<Integer, String> e : row.cells.entrySet()) {
                String title = e.getValue().replaceFirst("^\\*", "").trim();
                colField.put(e.getKey(), resolveFieldName(title));
            }
            // 与模板字段顺序对齐：按列索引排序补充未出现的字段
            int expected = headerOrder.size();
            for (int i = 0; i < expected; i++) {
                if (!colField.containsValue(headerOrder.get(i)) && !colField.containsKey(i)) {
                    colField.put(i, headerOrder.get(i));
                }
            }
            log.info("[ImportParse] 表头识别 row={} 列数={}", headerRowIdx, colField.size());
        }

        /** 表头文本 → 字段名：label 映射优先，其次按字段名本身（兼容新旧两种模板） */
        private String resolveFieldName(String title) {
            String mapped = ctx.headerLabels != null ? ctx.headerLabels.get(title) : null;
            if (mapped != null && !mapped.isEmpty()) {
                return mapped;
            }
            return title;
        }

        private void fillRecord(SheetRow row, ParsedDocument.MasterRecord rec) {
            for (Map.Entry<Integer, String> e : row.cells.entrySet()) {
                if (e.getKey() >= masterColCount) continue;
                String field = colField.get(e.getKey());
                if (field != null) {
                    rec.master.put(field, e.getValue());
                }
            }
        }

        private void appendDetail(SheetRow row, ParsedDocument.MasterRecord rec) {
            // 列区间分配：主表列之后的列按明细表定义顺序切分
            int colCursor = masterColCount;
            for (Map.Entry<String, List<String>> dt : ctx.detailTables) {
                List<Map<String, Object>> rows = rec.detailsByTable.get(dt.getKey());
                List<Integer> rowNos = rec.detailRowNos.get(dt.getKey());
                boolean hasValue = false;
                Map<String, Object> detail = new LinkedHashMap<>();
                for (int i = 0; i < dt.getValue().size(); i++) {
                    String value = row.cells.get(colCursor + i);
                    if (value != null) {
                        detail.put(dt.getValue().get(i), value);
                        hasValue = true;
                    }
                }
                colCursor += dt.getValue().size();
                if (hasValue) {
                    if (rows == null) {
                        rows = new ArrayList<>();
                        rec.detailsByTable.put(dt.getKey(), rows);
                        rowNos = new ArrayList<>();
                        rec.detailRowNos.put(dt.getKey(), rowNos);
                    }
                    rows.add(detail);
                    rowNos.add(rowIndex() + 1);
                }
            }
        }
    }

    /** dimension 估算（解析后为实际数据行数） */
    public long estimatedRows(JSONObject taskMeta) {
        return taskMeta.getLongValue("totalRows");
    }
}