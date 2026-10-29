package com.api.lowcode.dataimport.validate;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.dataimport.ImportErrorCollector;
import com.api.lowcode.dataimport.ImportProperties;
import com.api.lowcode.dataimport.parse.ParsedDocument;
import com.api.lowcode.meta.BizMeta;
import com.api.lowcode.meta.FieldMeta;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 导入校验：必填、类型格式、Excel 内唯一查重、库内 IN 批量预查重。
 * 校验失败的记录从写入集合剔除（行级错误），全失败由流水线终止。
 */
@Slf4j
@Component
public class ImportValidateService {

    @Resource
    private SqlEngineJson helper;

    @Resource
    private ImportProperties properties;

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyyMMdd")};

    /** 校验 + 剔除失败记录：返回可写入记录列表（原列表过滤视图，行级错误已入 collector） */
    public List<ParsedDocument.MasterRecord> validate(BizMeta meta, ParsedDocument doc,
                                                       ImportErrorCollector collector) throws Exception {
        Set<String> invalidRows = new HashSet<>();
        validateMaster(meta, doc, collector, invalidRows);
        validateUniqueInExcel(meta, doc, collector, invalidRows);
        validateUniqueInDb(meta, doc, collector, invalidRows);

        List<ParsedDocument.MasterRecord> valid = new ArrayList<>();
        for (ParsedDocument.MasterRecord rec : doc.records) {
            if (!invalidRows.contains(String.valueOf(rec.excelRowNo))) {
                valid.add(rec);
            }
        }
        return valid;
    }

    // ==================== 必填 + 类型格式 ====================

    private void validateMaster(BizMeta meta, ParsedDocument doc, ImportErrorCollector collector,
                                Set<String> invalidRows) {
        for (ParsedDocument.MasterRecord rec : doc.records) {
            for (FieldMeta f : meta.getFields()) {
                if (com.api.lowcode.dataimport.ImportTemplateService.isAuditField(f.getFieldName())) continue;
                String name = f.getFieldName();
                Object value = rec.master.get(name);
                String str = value != null ? String.valueOf(value).trim() : "";
                if (Boolean.TRUE.equals(f.getIsRequired()) && str.isEmpty()) {
                    collector.add(rec.excelRowNo, name, "必填字段为空");
                    invalidRows.add(String.valueOf(rec.excelRowNo));
                    continue;
                }
                if (str.isEmpty()) continue;
                String typeError = checkType(str, f.getDataType());
                if (typeError != null) {
                    collector.add(rec.excelRowNo, name, typeError);
                    invalidRows.add(String.valueOf(rec.excelRowNo));
                }
            }
        }
    }

    /** 类型格式预检（格式合法即可，最终类型转换由引擎 typeHint 兜底） */
    private String checkType(String value, String dataType) {
        if (dataType == null) return null;
        String t = dataType.toUpperCase();
        if (t.contains("DECIMAL") || t.contains("NUMERIC") || t.contains("INT")) {
            String cleaned = value.replace(",", "");
            try {
                new BigDecimal(cleaned);
            } catch (Exception e) {
                return "数值格式非法: '" + value + "'";
            }
            return null;
        }
        if (t.contains("TIMESTAMP") || t.contains("DATE")) {
            if (!isParsableDate(value)) {
                return "日期格式非法: '" + value + "'（支持 yyyy-MM-dd / yyyy/MM/dd）";
            }
            return null;
        }
        if (t.contains("BOOLEAN")) {
            String v = value.toLowerCase();
            if (!"true".equals(v) && !"false".equals(v) && !"1".equals(v) && !"0".equals(v)
                    && !"t".equals(v) && !"f".equals(v) && !"是".equals(value) && !"否".equals(value)) {
                return "布尔值非法: '" + value + "'（是/否/true/false/1/0）";
            }
            return null;
        }
        return null;
    }

    private boolean isParsableDate(String value) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                if (value.length() <= 10) {
                    LocalDate.parse(value, fmt);
                } else {
                    LocalDateTime.parse(value, fmt);
                }
                return true;
            } catch (Exception ignore) {
                // 尝试下一个格式
            }
        }
        return false;
    }

    // ==================== Excel 内唯一查重 ====================

    private void validateUniqueInExcel(BizMeta meta, ParsedDocument doc, ImportErrorCollector collector,
                                       Set<String> invalidRows) {
        for (UniqueGroup group : uniqueGroups(meta)) {
            Map<String, List<Integer>> seen = new HashMap<>();
            for (ParsedDocument.MasterRecord rec : doc.records) {
                StringBuilder key = new StringBuilder();
                boolean anyValue = false;
                for (String field : group.fields) {
                    Object v = rec.master.get(field);
                    String s = v != null ? String.valueOf(v).trim() : "";
                    if (!s.isEmpty()) {
                        anyValue = true;
                    }
                    key.append(s.toLowerCase()).append('\u0001');
                }
                if (anyValue) {
                    seen.computeIfAbsent(key.toString(), k -> new ArrayList<>()).add(rec.excelRowNo);
                }
            }
            for (List<Integer> rows : seen.values()) {
                if (rows.size() > 1) {
                    StringBuilder rowList = new StringBuilder();
                    for (int i : rows) {
                        if (rowList.length() > 0) rowList.append("、");
                        rowList.append(i);
                    }
                    for (int rowNo : rows) {
                        collector.add(rowNo, group.fields.get(0),
                                "Excel 内重复（组合唯一 " + String.join("+", group.fields) + "，行 " + rowList + "）");
                        invalidRows.add(String.valueOf(rowNo));
                    }
                }
            }
        }
    }

    // ==================== 库内唯一预查重 ====================

    private void validateUniqueInDb(BizMeta meta, ParsedDocument doc, ImportErrorCollector collector,
                                    Set<String> invalidRows) throws Exception {
        List<UniqueGroup> groups = uniqueGroups(meta);
        if (groups.isEmpty() || doc.records.isEmpty()) return;

        for (UniqueGroup group : groups) {
            if (group.fields.size() != 1) continue; // 组合唯一库内查重走写入时数据库约束兜底，一期只查单字段
            String field = group.fields.get(0);
            Set<String> values = new HashSet<>();
            for (ParsedDocument.MasterRecord rec : doc.records) {
                Object v = rec.master.get(field);
                if (v != null && !String.valueOf(v).trim().isEmpty()) {
                    values.add(String.valueOf(v).trim());
                }
            }
            if (values.isEmpty()) continue;
            List<String> valueList = new ArrayList<>(values);
            for (int from = 0; from < valueList.size(); from += properties.getInShardSize()) {
                int to = Math.min(valueList.size(), from + properties.getInShardSize());
                List<String> shard = valueList.subList(from, to);
                JSONArray rows = helper.query(meta.getMainTable(), new String[]{field},
                        helper.in(field, shard), null, false);
                if (!rows.isEmpty()) {
                    Set<String> existed = new HashSet<>();
                    for (int i = 0; i < rows.size(); i++) {
                        String existedVal = rows.getJSONObject(i).getString(field.toUpperCase());
                        if (existedVal != null) existed.add(existedVal);
                    }
                    for (ParsedDocument.MasterRecord rec : doc.records) {
                        Object v = rec.master.get(field);
                        if (v != null && existed.contains(String.valueOf(v).trim())) {
                            collector.add(rec.excelRowNo, field,
                                    "与数据库已有记录重复: '" + v + "'");
                            invalidRows.add(String.valueOf(rec.excelRowNo));
                        }
                    }
                }
            }
        }
    }

    // ==================== 唯一组收集 ====================

    public static class UniqueGroup {
        public List<String> fields = new ArrayList<>();
    }

    /** 元数据唯一组：is_unique 单字段 + unique_group 组合（按组键去重）；业务主键天然参与 */
    public List<UniqueGroup> uniqueGroups(BizMeta meta) {
        List<UniqueGroup> groups = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        if (meta.getFields() == null) return groups;
        for (FieldMeta f : meta.getFields()) {
            List<String> fields = new ArrayList<>();
            boolean unique = Boolean.TRUE.equals(f.getIsUnique());
            boolean isBizPk = f.getFieldName().equalsIgnoreCase(meta.getPrimaryKeyField())
                    && !"id".equalsIgnoreCase(f.getFieldName());
            if (!unique && !isBizPk) continue;
            fields.add(f.getFieldName());
            String groupJson = f.getUniqueGroup();
            if (unique && groupJson != null && !groupJson.trim().isEmpty()) {
                try {
                    JSONArray arr = JSONArray.parseArray(groupJson);
                    for (int i = 0; i < arr.size(); i++) {
                        String gf = arr.getString(i);
                        if (gf != null && !fields.contains(gf)) fields.add(gf);
                    }
                } catch (Exception ignore) {
                    // 非法 JSON 回退单字段
                }
            }
            StringBuilder key = new StringBuilder();
            List<String> sorted = new ArrayList<>(fields);
            sorted.sort(String::compareToIgnoreCase);
            for (String s : sorted) key.append(s.toLowerCase()).append(',');
            if (seenKeys.add(key.toString())) {
                UniqueGroup g = new UniqueGroup();
                g.fields = fields;
                groups.add(g);
            }
        }
        return groups;
    }
}