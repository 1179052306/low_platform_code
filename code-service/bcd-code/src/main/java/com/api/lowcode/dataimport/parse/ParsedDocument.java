package com.api.lowcode.dataimport.parse;

import com.alibaba.fastjson2.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析结果文档：主记录列表（含明细）+ 解析期错误。
 * MasterRecord.excelRowNo 保留原始行号供错误定位。
 */
public class ParsedDocument {

    /** 一条主记录：主表字段值 + 各明细表行 + Excel 起始行号 */
    public static class MasterRecord {
        public int excelRowNo;
        public Map<String, Object> master = new LinkedHashMap<>();
        /** 明细表名 → 该主记录的明细行列表（行内 key=字段名 value=Object） */
        public Map<String, List<Map<String, Object>>> detailsByTable = new LinkedHashMap<>();
        /** 明细行号（与 detailsByTable 顺序对应，供错误定位） */
        public Map<String, List<Integer>> detailRowNos = new LinkedHashMap<>();
    }

    /** 行级错误（行号/列名/原因） */
    public static class RowError {
        public int rowNo;
        public String fieldName;
        public String reason;

        public RowError(int rowNo, String fieldName, String reason) {
            this.rowNo = rowNo;
            this.fieldName = fieldName;
            this.reason = reason;
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            o.put("rowNo", rowNo);
            o.put("fieldName", fieldName);
            o.put("reason", reason);
            return o;
        }

        public String message() {
            return "第" + rowNo + "行" + (fieldName != null && !fieldName.isEmpty() ? "「" + fieldName + "」" : "")
                    + ": " + reason;
        }
    }

    public List<MasterRecord> records = new ArrayList<>();
    public List<RowError> parseErrors = new ArrayList<>();
    /** dimension 估算总行数（不含表头） */
    public long estimatedTotal;

    /** 明细表定义（解析分组用，列名列表） */
    public List<String> detailTableOrder = new ArrayList<>();
}