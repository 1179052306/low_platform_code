package com.api.lowcode.dataimport.resolve;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.common.BizException;
import com.api.lowcode.dataimport.ImportErrorCollector;
import com.api.lowcode.dataimport.ImportProperties;
import com.api.lowcode.dataimport.parse.ParsedDocument;
import com.server.db.MultiDataSourceHolder;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基础资料反查：Excel 编码值 → 基础资料主键（批量 IN 查询，任务级缓存）。
 * codeField 是唯一匹配依据；未配置 codeField 的引用列整单报错（设计器前端已强制必配，此处兜底）。
 */
@Slf4j
@Component
public class BaseDataReverseResolver {

    @Resource
    private SqlEngineJson helper;

    @Resource(name = "multiDataSource")
    private DataSource dataSource;

    @Resource
    private ImportProperties properties;

    /** 引用列定义（lc_field_meta.base_data_ref 解析） */
    public static class RefField {
        public String fieldName;
        public String refPageId;
        public String valueField;
        public String displayField;
        public String codeField;
        public String refTable;
    }

    /** 反查：替换主表与明细行中的编码值为主键值，未命中/多命中行级错误 */
    public void resolve(String bizCode, List<ParsedDocument.MasterRecord> records,
                         ImportErrorCollector collector) throws Exception {
        Map<String, RefField> refFields = loadRefFields(bizCode);
        if (refFields.isEmpty()) return;

        for (RefField rf : refFields.values()) {
            if (rf.codeField == null || rf.codeField.trim().isEmpty()) {
                throw new BizException("CODE_FIELD_MISSING",
                        "字段 " + rf.fieldName + " 引用基础资料但未配置编码字段，请到设计器补配并保存页面");
            }
        }

        // 收集各引用列的编码值（主表 + 明细）
        Map<String, Set<String>> codeValuesByField = new HashMap<>();
        for (RefField rf : refFields.values()) {
            Set<String> values = new HashSet<>();
            for (ParsedDocument.MasterRecord rec : records) {
                collectValues(rec.master, rf.fieldName, values);
                for (List<Map<String, Object>> details : rec.detailsByTable.values()) {
                    for (Map<String, Object> detail : details) {
                        collectValues(detail, rf.fieldName, values);
                    }
                }
            }
            if (!values.isEmpty()) {
                codeValuesByField.put(rf.fieldName, values);
            }
        }

        // 批量反查：refTable → Map<编码, 主键>
        Map<String, Map<String, Object>> codeToPkByField = new HashMap<>();
        Map<String, Set<String>> multiMatchByField = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : codeValuesByField.entrySet()) {
            RefField rf = refFields.get(entry.getKey());
            Map<String, Object> codeMap = new HashMap<>();
            Set<String> multi = new HashSet<>();
            List<String> valueList = new ArrayList<>(entry.getValue());
            for (int from = 0; from < valueList.size(); from += properties.getInShardSize()) {
                int to = Math.min(valueList.size(), from + properties.getInShardSize());
                List<String> shard = valueList.subList(from, to);
                JSONArray rows = helper.query(rf.refTable,
                        new String[]{rf.codeField, rf.valueField},
                        helper.in(rf.codeField, shard), null, false);
                for (int i = 0; i < rows.size(); i++) {
                    JSONObject row = rows.getJSONObject(i);
                    String code = stringIgnoreCase(row, rf.codeField);
                    Object pk = row.get(rf.valueField.toUpperCase());
                    if (code == null || pk == null) continue;
                    if (codeMap.containsKey(code)) {
                        multi.add(code);
                    } else {
                        codeMap.put(code, pk);
                    }
                }
            }
            codeToPkByField.put(entry.getKey(), codeMap);
            multiMatchByField.put(entry.getKey(), multi);
        }

        // 逐行替换
        Set<String> invalidRows = new HashSet<>();
        for (ParsedDocument.MasterRecord rec : records) {
            replaceValue(rec.master, refFields, codeToPkByField, multiMatchByField, rec.excelRowNo, collector, invalidRows);
            for (Map.Entry<String, List<Map<String, Object>>> dt : rec.detailsByTable.entrySet()) {
                List<Integer> rowNos = rec.detailRowNos.get(dt.getKey());
                List<Map<String, Object>> details = dt.getValue();
                for (int i = 0; i < details.size(); i++) {
                    int rowNo = rowNos != null && i < rowNos.size() ? rowNos.get(i) : rec.excelRowNo;
                    replaceValue(details.get(i), refFields, codeToPkByField, multiMatchByField, rowNo, collector, invalidRows);
                }
            }
        }
        records.removeIf(r -> invalidRows.contains(String.valueOf(r.excelRowNo)));
    }

    private void collectValues(Map<String, Object> data, String fieldName, Set<String> values) {
        Object v = data.get(fieldName);
        if (v != null && !String.valueOf(v).trim().isEmpty()) {
            values.add(String.valueOf(v).trim());
        }
    }

    private void replaceValue(Map<String, Object> data, Map<String, RefField> refFields,
                              Map<String, Map<String, Object>> codeToPkByField,
                              Map<String, Set<String>> multiMatchByField,
                              int rowNo, ImportErrorCollector collector, Set<String> invalidRows) {
        for (Map.Entry<String, RefField> entry : refFields.entrySet()) {
            String fieldName = entry.getKey();
            RefField rf = entry.getValue();
            Object v = data.get(fieldName);
            if (v == null || String.valueOf(v).trim().isEmpty()) continue;
            String code = String.valueOf(v).trim();
            Map<String, Object> codeMap = codeToPkByField.get(fieldName);
            if (codeMap == null || !codeMap.containsKey(code)) {
                collector.add(rowNo, fieldName, "基础资料中不存在编码 '" + code + "'");
                invalidRows.add(String.valueOf(rowNo));
                continue;
            }
            if (multiMatchByField.get(fieldName) != null && multiMatchByField.get(fieldName).contains(code)) {
                collector.add(rowNo, fieldName, "编码 '" + code + "' 匹配到多条基础资料记录，请检查数据");
                invalidRows.add(String.valueOf(rowNo));
                continue;
            }
            data.put(fieldName, codeMap.get(code));
        }
    }

    /** 大小写不敏感取列值（helper.query 返回大写列名） */
    private String stringIgnoreCase(JSONObject row, String column) {
        String v = row.getString(column.toUpperCase());
        return v != null ? v : row.getString(column);
    }

    /** 读 lc_field_meta.base_data_ref（含 codeField）并解析 refTable */
    private Map<String, RefField> loadRefFields(String bizCode) {
        Map<String, RefField> map = new HashMap<>();
        try {
            JSONArray rows = helper.query("lc_field_meta", new String[]{"field_name", "base_data_ref"},
                    helper.eq("biz_code", bizCode), null, false);
            MultiDataSourceHolder.setDatasource("lowcode");
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            try {
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
                        if (rf.refPageId == null || rf.refPageId.isEmpty()) continue;
                        List<String> tables = jdbc.queryForList(
                                "SELECT table_name FROM lowcode_page WHERE page_id = ?", String.class, rf.refPageId);
                        if (tables.isEmpty()) continue;
                        rf.refTable = tables.get(0);
                        map.put(rf.fieldName, rf);
                    } catch (Exception ignore) {
                        // 非法 JSON 跳过
                    }
                }
            } finally {
                MultiDataSourceHolder.clearDataSource();
            }
        } catch (Exception e) {
            log.warn("[ReverseResolver] 加载 base_data_ref 失败: {}", e.getMessage());
        }
        return map;
    }
}