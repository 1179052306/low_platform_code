package com.api.lowcode.dataimport;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.common.BizException;
import com.api.lowcode.meta.BizMeta;
import com.api.lowcode.meta.FieldMeta;
import com.api.lowcode.meta.MetaRegistry;
import com.server.db.MultiDataSourceHolder;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导入模板生成：读 BizMeta + 页面 schema 明细表定义 + base_data_ref（含 codeField），
 * POI 生成 xlsx（数据导入 Sheet + 填写说明 Sheet + 基础资料对照 Sheet）。
 * 列头显示字段 label（表单标签/列标题），无 label 回退字段名；字段名写入批注与填写说明。
 */
@Slf4j
@Service
public class ImportTemplateService {

    @Resource
    private MetaRegistry metaRegistry;

    @Resource
    private SqlEngineJson helper;

    @Resource(name = "multiDataSource")
    private DataSource dataSource;

    /** 明细表定义（页面 schema 中 data-grid 的导入视角） */
    public static class DetailView {
        public String tableName;
        public String tableComment;
        public String parentType;
        /** 明细表主键字段（props.keyExpr，默认 id） */
        public String keyExpr = "id";
        public List<String> columnFields = new ArrayList<>();
        /** 明细字段 → 列标题 caption（模板列头显示名） */
        public Map<String, String> captionByField = new LinkedHashMap<>();
    }

    /** 基础资料引用列信息（base_data_ref 解析结果） */
    public static class RefField {
        public String fieldName;
        public String refPageId;
        public String valueField;
        public String displayField;
        public String codeField;
    }

    /** 生成模板并返回文件字节 */
    public byte[] generate(String bizCode) throws IOException {
        BizMeta meta = metaRegistry.getMeta(bizCode);
        if (meta == null) {
            throw new BizException("META_NOT_FOUND", "业务元数据不存在: " + bizCode + "，请先保存对应页面");
        }
        JSONObject schema = loadPageSchema(bizCode);
        List<DetailView> details = schema != null ? collectDetails(schema) : new ArrayList<>();
        Map<String, String> labels = schema != null ? collectFieldLabels(schema) : new LinkedHashMap<>();
        Map<String, RefField> refFields = loadRefFields(bizCode);
        Map<String, RefField> byTable = refTableMapping(refFields);
        Map<String, List<String[]>> refData = loadRefData(byTable);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet dataSheet = buildDataSheet(wb, meta, details, refFields, byTable, refData, labels);
            buildGuideSheet(wb, meta, details, refFields, labels);
            if (!refData.isEmpty()) {
                buildRefSheet(wb, byTable, refData);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 收集字段显示名映射（字段名 → label）：表单控件 props.label 优先，表格列 caption 兜底。
     * label 重复时丢弃（避免模板列头歧义），无 label 时回退字段名本身。
     */
    public Map<String, String> collectFieldLabels(JSONObject schema) {
        Map<String, String> labels = new LinkedHashMap<>();
        Map<String, Integer> labelCounts = new HashMap<>();
        Object rootObj = schema.get("root");
        if (rootObj instanceof JSONObject) {
            collectLabelNodes((JSONObject) rootObj, labels, labelCounts);
        } else if (rootObj instanceof JSONArray) {
            JSONArray rootArr = (JSONArray) rootObj;
            for (int i = 0; i < rootArr.size(); i++) {
                collectLabelNodes(rootArr.getJSONObject(i), labels, labelCounts);
            }
        }
        JSONObject bizSchemas = schema.getJSONObject("bizSchemas");
        if (bizSchemas != null) {
            JSONArray form = bizSchemas.getJSONArray("form");
            if (form != null) {
                for (int i = 0; i < form.size(); i++) {
                    collectLabelNodes(form.getJSONObject(i), labels, labelCounts);
                }
            }
            JSONArray table = bizSchemas.getJSONArray("table");
            if (table != null) {
                for (int i = 0; i < table.size(); i++) {
                    collectLabelNodes(table.getJSONObject(i), labels, labelCounts);
                }
            }
        }
        // 重复 label 的字段回退字段名（保证列头唯一可解析）
        labels.entrySet().removeIf(e -> labelCounts.getOrDefault(e.getValue(), 0) > 1 && !e.getKey().equals(e.getValue()));
        log.info("[ImportTemplate] 字段显示名收集: count={}, labels={}", labels.size(), labels);
        log.info("[ImportTemplate] 诊断: rootType={}, bizSchemasNull={}, formSize={}, tableSize={}, schemaKeys={}",
                rootObj == null ? "null" : rootObj.getClass().getSimpleName(),
                bizSchemas == null,
                bizSchemas != null && bizSchemas.getJSONArray("form") != null ? bizSchemas.getJSONArray("form").size() : -1,
                bizSchemas != null && bizSchemas.getJSONArray("table") != null ? bizSchemas.getJSONArray("table").size() : -1,
                schema.keySet());
        try {
            JSONObject form0 = bizSchemas != null && bizSchemas.getJSONArray("form") != null
                    && !bizSchemas.getJSONArray("form").isEmpty()
                    ? bizSchemas.getJSONArray("form").getJSONObject(0) : null;
            if (form0 != null) {
                String dumped = form0.toJSONString();
                log.info("[ImportTemplate] form[0] dump(前800字): {}", dumped.length() > 800 ? dumped.substring(0, 800) : dumped);
                JSONArray c1 = form0.getJSONArray("children");
                log.info("[ImportTemplate] form[0].children.size={}", c1 != null ? c1.size() : -1);
                if (c1 != null && !c1.isEmpty()) {
                    JSONObject col = c1.getJSONObject(0);
                    JSONArray c2 = col != null ? col.getJSONArray("children") : null;
                    log.info("[ImportTemplate] col[0].children.size={}", c2 != null ? c2.size() : -1);
                    if (c2 != null && !c2.isEmpty()) {
                        JSONObject box = c2.getJSONObject(0);
                        log.info("[ImportTemplate] box.type={}, boxKeys={}, dbField={}, props.label={}",
                                box != null ? box.getString("type") : null,
                                box != null ? box.keySet() : null,
                                box != null ? box.getString("dbField") : null,
                                box != null && box.getJSONObject("props") != null ? box.getJSONObject("props").getString("label") : null);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ImportTemplate] form[0] dump 失败: {}", e.getMessage());
        }
        return labels;
    }

    /** 递归收集节点树中的字段显示名（表单控件 props.label + 表格列 caption 统一收集，互不干扰） */
    private void collectLabelNodes(JSONObject node, Map<String, String> labels,
                                   Map<String, Integer> labelCounts) {
        if (node == null) return;
        String dbField = node.getString("dbField");
        JSONObject props = node.getJSONObject("props");
        if (dbField != null && !dbField.trim().isEmpty() && !dbField.contains(".")) {
            String label = null;
            if (props != null) {
                label = props.getString("label");
            }
            if (label != null && !label.trim().isEmpty()) {
                String trimmed = label.trim();
                String field = dbField.trim();
                // root 与 bizSchemas.form 是同一棵树的两份快照：
                // 字段首次登记才计入 labelCounts，重复出现不再计数（避免双计数触发误去重）
                if (!labels.containsKey(field)) {
                    labels.put(field, trimmed);
                    labelCounts.merge(trimmed, 1, Integer::sum);
                }
            }
        }
        // 表格列 caption
        if (props != null) {
            JSONArray columns = props.getJSONArray("columns");
            if (columns != null) {
                for (int j = 0; j < columns.size(); j++) {
                    JSONObject col = columns.getJSONObject(j);
                    String dataField = col.getString("dataField");
                    String caption = col.getString("caption");
                    if (dataField != null && !dataField.contains(".") && caption != null && !caption.trim().isEmpty()) {
                        String trimmed = caption.trim();
                        String field = dataField.trim();
                        if (!labels.containsKey(field)) {
                            labels.put(field, trimmed);
                            labelCounts.merge(trimmed, 1, Integer::sum);
                        }
                    }
                }
            }
        }
        JSONArray children = node.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                collectLabelNodes(children.getJSONObject(i), labels, labelCounts);
            }
        }
    }

    // ==================== 数据构建 ====================

    /** 查页面 schema（按表名匹配，table_name 精确）—— 流水线复用 */
    public JSONObject loadPageSchema(String bizCode) {
        MultiDataSourceHolder.setDatasource("lowcode");
        try {
            List<String> rows = new JdbcTemplate(dataSource).queryForList(
                    "SELECT schema_json FROM lowcode_page WHERE table_name = ?", String.class, bizCode);
            if (rows.isEmpty()) return null;
            return JSONObject.parseObject(rows.get(0));
        } catch (Exception e) {
            log.warn("[ImportTemplate] 查询页面 schema 失败: bizCode={}, {}", bizCode, e.getMessage());
            return null;
        } finally {
            MultiDataSourceHolder.clearDataSource();
        }
    }

    /** 递归收集 data-grid 明细表（一级明细，parentType=page）—— 流水线复用 */
    public List<DetailView> collectDetails(JSONObject schema) {
        List<DetailView> result = new ArrayList<>();
        Object rootObj = schema.get("root");
        if (rootObj instanceof JSONObject) {
            collectDetailNodes((JSONObject) rootObj, result);
        } else if (rootObj instanceof JSONArray) {
            JSONArray rootArr = (JSONArray) rootObj;
            for (int i = 0; i < rootArr.size(); i++) {
                collectDetailNodes(rootArr.getJSONObject(i), result);
            }
        }
        return result;
    }

    private void collectDetailNodes(JSONObject node, List<DetailView> result) {
        if (node == null) return;
        if ("data-grid".equals(node.getString("type"))) {
            JSONObject props = node.getJSONObject("props");
            if (props != null) {
                String tableName = props.getString("tableName");
                if (tableName != null && !tableName.trim().isEmpty()) {
                    DetailView dv = new DetailView();
                    dv.tableName = tableName.trim();
                    dv.tableComment = props.getString("tableComment");
                    dv.parentType = props.getString("parentType");
                    String keyExpr = props.getString("keyExpr");
                    if (keyExpr != null && !keyExpr.trim().isEmpty()) {
                        dv.keyExpr = keyExpr.trim();
                    }
                    JSONArray columns = props.getJSONArray("columns");
                    if (columns != null) {
                        for (int i = 0; i < columns.size(); i++) {
                            JSONObject col = columns.getJSONObject(i);
                            String dataField = col.getString("dataField");
                            if (dataField != null && !dataField.trim().isEmpty() && !dataField.contains(".")) {
                                dv.columnFields.add(dataField.trim());
                                String caption = col.getString("caption");
                                if (caption != null && !caption.trim().isEmpty()) {
                                    dv.captionByField.put(dataField.trim(), caption.trim());
                                }
                            }
                        }
                    }
                    result.add(dv);
                }
            }
        }
        JSONArray children = node.getJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.size(); i++) {
                collectDetailNodes(children.getJSONObject(i), result);
            }
        }
    }

    /** 读 lc_field_meta 的 base_data_ref（含 codeField），key=字段名 */
    private Map<String, RefField> loadRefFields(String bizCode) {
        Map<String, RefField> map = new LinkedHashMap<>();
        try {
            JSONArray rows = helper.query("lc_field_meta", new String[]{"field_name", "base_data_ref"},
                    helper.eq("biz_code", bizCode), null, false);
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String refJson = row.getString("BASE_DATA_REF");
                if (refJson == null || refJson.trim().isEmpty()) continue;
                try {
                    JSONObject ref = JSONObject.parseObject(refJson);
                    RefField rf = new RefField();
                    rf.fieldName = row.getString("FIELD_NAME");
                    rf.refPageId = ref.getString("refPageId");
                    rf.valueField = ref.getString("valueField");
                    rf.displayField = ref.getString("displayField");
                    rf.codeField = ref.getString("codeField");
                    if (rf.refPageId != null && !rf.refPageId.isEmpty()) {
                        map.put(rf.fieldName, rf);
                    }
                } catch (Exception ignore) {
                    // base_data_ref 非法 JSON 跳过
                }
            }
        } catch (Exception e) {
            log.warn("[ImportTemplate] 加载 base_data_ref 失败: {}", e.getMessage());
        }
        return map;
    }

    /** 加载基础资料对照数据（key=基础资料表名，value=[编码,名称] 行），超 2000 行截断 */
    private Map<String, List<String[]>> loadRefData(Map<String, RefField> byTable) {
        Map<String, List<String[]>> data = new LinkedHashMap<>();
        MultiDataSourceHolder.setDatasource("lowcode");
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            for (Map.Entry<String, RefField> entry : byTable.entrySet()) {
                String refTable = entry.getKey();
                RefField rf = entry.getValue();
                try {
                    List<Map<String, Object>> rows = jdbc.queryForList("SELECT * FROM " + refTable);
                    List<String[]> pairs = new ArrayList<>();
                    for (Map<String, Object> row : rows) {
                        if (pairs.size() >= 2000) break;
                        String code = stringVal(row, rf.codeField);
                        String display = stringVal(row, firstDisplay(rf.displayField));
                        if (code != null && !code.isEmpty()) {
                            pairs.add(new String[]{code, display != null ? display : ""});
                        }
                    }
                    data.put(refTable, pairs);
                } catch (Exception e) {
                    log.warn("[ImportTemplate] 加载基础资料对照失败: table={}, {}", refTable, e.getMessage());
                }
            }
        } finally {
            MultiDataSourceHolder.clearDataSource();
        }
        return data;
    }

    /** 基础资料表名 → RefField 映射（多个字段引用同一表时共享） */
    private Map<String, RefField> refTableMapping(Map<String, RefField> refFields) {
        Map<String, RefField> byTable = new LinkedHashMap<>();
        for (RefField rf : refFields.values()) {
            if (rf.codeField == null || rf.codeField.isEmpty()) continue;
            String refTable = refTableName(rf.refPageId);
            if (refTable != null && !byTable.containsKey(refTable)) {
                byTable.put(refTable, rf);
            }
        }
        return byTable;
    }

    private String refTableName(String refPageId) {
        MultiDataSourceHolder.setDatasource("lowcode");
        try {
            List<String> rows = new JdbcTemplate(dataSource).queryForList(
                    "SELECT table_name FROM lowcode_page WHERE page_id = ?", String.class, refPageId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            return null;
        } finally {
            MultiDataSourceHolder.clearDataSource();
        }
    }

    private String firstDisplay(String displayField) {
        if (displayField == null || displayField.isEmpty()) return "name";
        int comma = displayField.indexOf(',');
        return comma > 0 ? displayField.substring(0, comma).trim() : displayField.trim();
    }

    /** 大小写不敏感取列值（JdbcTemplate 返回的 key 大小写随库变化） */
    private String stringVal(Map<String, Object> row, String column) {
        if (column == null) return null;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (e.getKey().equalsIgnoreCase(column)) {
                return e.getValue() != null ? String.valueOf(e.getValue()) : null;
            }
        }
        return null;
    }

    // ==================== Sheet 构建 ====================

    private Sheet buildDataSheet(XSSFWorkbook wb, BizMeta meta, List<DetailView> details,
                                 Map<String, RefField> refFields, Map<String, RefField> byTable,
                                 Map<String, List<String[]>> refData, Map<String, String> labels) {
        Sheet sheet = wb.createSheet("数据导入");
        Font headFont = wb.createFont();
        headFont.setBold(true);
        CellStyle headStyle = wb.createCellStyle();
        headStyle.setFont(headFont);
        Font requiredFont = wb.createFont();
        requiredFont.setBold(true);
        requiredFont.setColor(Font.COLOR_RED);
        CellStyle requiredStyle = wb.createCellStyle();
        requiredStyle.setFont(requiredFont);
        CreationHelper helper_ = wb.getCreationHelper();
        Drawing<?> drawing = sheet.createDrawingPatriarch();

        List<String> masterFields = new ArrayList<>();
        for (FieldMeta f : meta.getFields()) {
            if (isAuditField(f.getFieldName())) continue;
            masterFields.add(f.getFieldName());
        }

        int col = 0;
        if (!details.isEmpty()) {
            // 主从模板：第0行区块标注，第1行表头
            Row blockRow = sheet.createRow(0);
            int masterCols = masterFields.size();
            for (DetailView dv : details) {
                blockRow.createCell(masterCols + col).setCellValue("明细：" + label(dv));
                sheet.addMergedRegion(new CellRangeAddress(0, 0, masterCols + col,
                        masterCols + col + Math.max(dv.columnFields.size(), 1) - 1));
                col += Math.max(dv.columnFields.size(), 1);
            }
            blockRow.createCell(0).setCellValue("主表：" + meta.getBizName() + "（" + meta.getMainTable() + "）");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, Math.max(masterCols - 1, 0)));
            blockRow.getCell(0).setCellStyle(headStyle);
        }

        Row header = sheet.createRow(details.isEmpty() ? 0 : 1);
        int hc = 0;
        for (FieldMeta f : meta.getFields()) {
            if (isAuditField(f.getFieldName())) continue;
            // 列头显示 label（表单标签/列标题），无 label 回退字段名；字段名进批注
            String display = displayTitle(labels, f.getFieldName());
            boolean required = Boolean.TRUE.equals(f.getIsRequired());
            Cell cell = header.createCell(hc);
            cell.setCellValue(required ? "*" + display : display);
            cell.setCellStyle(required ? requiredStyle : headStyle);
            String comment = fieldComment(f, refFields.get(f.getFieldName()));
            if (!display.equals(f.getFieldName())) {
                comment = "数据库字段: " + f.getFieldName() + "\n" + comment;
            }
            // 主从模板第一列：合并分组锚点说明（POI 一个单元格只允许一个批注）
            if (hc == 0 && !details.isEmpty()) {
                comment = comment + "\n主从分组锚点列：一条主记录第一行填值，后续明细行此列留空";
            }
            attachComment(drawing, helper_, cell, comment);
            sheet.setColumnWidth(hc, 16 * 256);
            hc++;
        }
        for (DetailView dv : details) {
            for (String field : dv.columnFields) {
                String display = dv.captionByField.getOrDefault(field, field);
                Cell cell = header.createCell(hc);
                cell.setCellValue("*" + display);
                cell.setCellStyle(requiredStyle);
                String comment = "明细表 " + dv.tableName + " 字段（必填）";
                if (!display.equals(field)) {
                    comment = "数据库字段: " + field + "\n" + comment;
                }
                attachComment(drawing, helper_, cell, comment);
                sheet.setColumnWidth(hc, 16 * 256);
                hc++;
            }
        }

        // 基础资料列下拉（可选值总长 ≤255 字符时）
        addRefValidation(sheet, header, meta, refFields, byTable, refData, masterFields.size() + totalDetailCols(details));
        sheet.createFreezePane(0, details.isEmpty() ? 1 : 2);
        return sheet;
    }

    private int totalDetailCols(List<DetailView> details) {
        int n = 0;
        for (DetailView dv : details) n += Math.max(dv.columnFields.size(), 1);
        return n;
    }

    private String label(DetailView dv) {
        return (dv.tableComment != null && !dv.tableComment.isEmpty()) ? dv.tableComment : dv.tableName;
    }

    private String fieldComment(FieldMeta f, RefField rf) {
        StringBuilder sb = new StringBuilder();
        sb.append("类型: ").append(f.getDataType() != null ? f.getDataType() : "VARCHAR");
        if (Boolean.TRUE.equals(f.getIsRequired())) sb.append("，必填");
        if (Boolean.TRUE.equals(f.getIsUnique())) sb.append("，不可重复");
        if (rf != null) {
            if (rf.codeField == null || rf.codeField.isEmpty()) {
                sb.append("。注意：该字段未配置编码字段，导入反查不可用（请到设计器补配）");
            } else {
                sb.append("。基础资料列：填编码（").append(rf.codeField).append("），系统自动转换");
            }
        }
        return sb.toString();
    }

    private void attachComment(Drawing<?> drawing, CreationHelper helper, Cell cell, String text) {
        if (text == null || text.isEmpty()) return;
        ClientAnchor anchor = helper.createClientAnchor();
        anchor.setCol1(cell.getColumnIndex());
        anchor.setCol2(cell.getColumnIndex() + 3);
        anchor.setRow1(cell.getRowIndex());
        anchor.setRow2(cell.getRowIndex() + 3);
        Comment comment = drawing.createCellComment(anchor);
        comment.setString(helper.createRichTextString(text));
        cell.setCellComment(comment);
    }

    /** 基础资料编码列数据验证（显式列表 ≤255 字符） */
    private void addRefValidation(Sheet sheet, Row header, BizMeta meta, Map<String, RefField> refFields,
                                  Map<String, RefField> byTable, Map<String, List<String[]>> refData, int totalCols) {
        int idx = 0;
        for (FieldMeta f : meta.getFields()) {
            if (isAuditField(f.getFieldName())) continue;
            RefField rf = refFields.get(f.getFieldName());
            if (rf != null && rf.codeField != null && !rf.codeField.isEmpty()) {
                String refTable = refTableName(rf.refPageId);
                if (refTable != null) {
                    List<String[]> pairs = refData.get(refTable);
                    if (pairs != null && !pairs.isEmpty()) {
                        StringBuilder list = new StringBuilder();
                        for (String[] pair : pairs) {
                            if (list.length() > 0) list.append(',');
                            list.append(pair[0]);
                        }
                        if (list.length() <= 255) {
                            DataValidationHelper dv = sheet.getDataValidationHelper();
                            List<String> values = new ArrayList<>();
                            for (String[] pair : pairs) values.add(pair[0]);
                            DataValidationConstraint constraint = dv.createExplicitListConstraint(
                                    values.toArray(new String[0]));
                            CellRangeAddressList region = new CellRangeAddressList(
                                    header.getRowNum() + 1, 65535, idx, idx);
                            DataValidation validation = dv.createValidation(constraint, region);
                            validation.setSuppressDropDownArrow(true);
                            validation.setShowErrorBox(false);
                            sheet.addValidationData(validation);
                        }
                    }
                }
            }
            idx++;
        }
    }

    private void buildGuideSheet(XSSFWorkbook wb, BizMeta meta, List<DetailView> details,
                                 Map<String, RefField> refFields, Map<String, String> labels) {
        Sheet sheet = wb.createSheet("填写说明");
        CellStyle bold = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        bold.setFont(font);
        Row r0 = sheet.createRow(0);
        r0.createCell(0).setCellValue("【填写规则】");
        r0.getCell(0).setCellStyle(bold);
        String[] rules;
        if (details.isEmpty()) {
            rules = new String[]{
                    "1. 带 * 号为必填列",
                    "2. 唯一字段在 Excel 内及数据库内均不可重复",
                    "3. 基础资料列填编码，系统按编码反查转换",
                    "4. 日期格式：YYYY-MM-DD；数值不留千分位",
                    "5. 校验以系统数据库实时数据为准"};
        } else {
            rules = new String[]{
                    "1. 带 * 号为必填列",
                    "2. 主从分组：一条主记录第一行填主表列，其明细行主表列留空",
                    "3. 明细行之前必须存在主记录行（主表第一列锚点非空开新组）",
                    "4. 基础资料列填编码，系统按编码反查转换",
                    "5. 日期格式：YYYY-MM-DD；数值不留千分位",
                    "6. 校验以系统数据库实时数据为准"};
        }
        for (int i = 0; i < rules.length; i++) {
            sheet.createRow(i + 1).createCell(0).setCellValue(rules[i]);
        }
        int rowIdx = rules.length + 2;
        Row colHead = sheet.createRow(rowIdx);
        colHead.createCell(0).setCellValue("【列说明】");
        colHead.getCell(0).setCellStyle(bold);
        rowIdx++;
        Row head = sheet.createRow(rowIdx);
        String[] heads = {"列头显示名", "数据库字段", "所属", "必填", "类型", "说明"};
        for (int i = 0; i < heads.length; i++) head.createCell(i).setCellValue(heads[i]);
        rowIdx++;
        for (FieldMeta f : meta.getFields()) {
            if (isAuditField(f.getFieldName())) continue;
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(displayTitle(labels, f.getFieldName()));
            row.createCell(1).setCellValue(f.getFieldName());
            row.createCell(2).setCellValue("主表 " + meta.getMainTable());
            row.createCell(3).setCellValue(Boolean.TRUE.equals(f.getIsRequired()) ? "是" : "否");
            row.createCell(4).setCellValue(f.getDataType() != null ? f.getDataType() : "VARCHAR");
            RefField rf = refFields.get(f.getFieldName());
            if (rf != null) {
                row.createCell(5).setCellValue(rf.codeField != null && !rf.codeField.isEmpty()
                        ? "基础资料：填编码字段 " + rf.codeField
                        : "基础资料（未配置编码字段，导入不可用）");
            }
        }
        for (DetailView dv : details) {
            for (String field : dv.columnFields) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(dv.captionByField.getOrDefault(field, field));
                row.createCell(1).setCellValue(field);
                row.createCell(2).setCellValue("明细表 " + dv.tableName);
                row.createCell(3).setCellValue("是");
                row.createCell(4).setCellValue("VARCHAR");
            }
        }
        sheet.setColumnWidth(0, 20 * 256);
        sheet.setColumnWidth(1, 20 * 256);
        sheet.setColumnWidth(5, 40 * 256);
    }

    /** 列头显示名：label 映射优先，无则回退字段名 */
    private String displayTitle(Map<String, String> labels, String fieldName) {
        String label = labels.get(fieldName);
        return label != null && !label.trim().isEmpty() ? label.trim() : fieldName;
    }

    /**
     * 平台审计/软删字段（建表时自动添加，不由用户填写）——模板、校验、解析均跳过。
     * 注意：不能按 is_system_field 元数据标记判断（存量数据标记混乱，如 po_main.status 被误标）。
     */
    public static boolean isAuditField(String fieldName) {
        if (fieldName == null) return false;
        String n = fieldName.toLowerCase();
        return n.equals("created_at") || n.equals("updated_at") || n.equals("deleted")
                || n.equals("created_by") || n.equals("updated_by");
    }

    private void buildRefSheet(XSSFWorkbook wb, Map<String, RefField> byTable, Map<String, List<String[]>> refData) {
        Sheet sheet = wb.createSheet("基础资料对照");
        CellStyle bold = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        bold.setFont(font);
        int rowIdx = 0;
        Row title = sheet.createRow(rowIdx++);
        title.createCell(0).setCellValue("【基础资料对照】（供填写查阅；实际校验以系统数据库实时数据为准）");
        title.getCell(0).setCellStyle(bold);
        rowIdx++;
        for (Map.Entry<String, List<String[]>> entry : refData.entrySet()) {
            String refTable = entry.getKey();
            RefField rf = byTable.get(refTable);
            Row head = sheet.createRow(rowIdx++);
            head.createCell(0).setCellValue(refTable + " · 编码"
                    + (rf != null && rf.codeField != null ? "(" + rf.codeField + ")" : ""));
            head.createCell(1).setCellValue("名称"
                    + (rf != null ? "(" + firstDisplay(rf.displayField) + ")" : ""));
            head.getCell(0).setCellStyle(bold);
            head.getCell(1).setCellStyle(bold);
            for (String[] pair : entry.getValue()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(pair[0]);
                row.createCell(1).setCellValue(pair[1]);
            }
            rowIdx++;
        }
        sheet.setColumnWidth(0, 24 * 256);
        sheet.setColumnWidth(1, 24 * 256);
    }
}