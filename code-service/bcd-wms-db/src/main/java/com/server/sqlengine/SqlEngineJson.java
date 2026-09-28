package com.server.sqlengine;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 低代码平台数据访问辅助类 —— 封装 {@link SqlEngineFacade} 调用 + 列名映射（大写→camelCase）。
 *
 * <p>
 * 查询方法返回原始类型（失败抛异常），写操作方法返回 {@link ResMsg}（调用方判断成功失败）。
 * </p>
 */
@Service
public class SqlEngineJson {

    @Autowired
    private SqlEngineFacade facade;

    private static final String DB_NAME = "lowcode";

    // ==================== 工具方法 ====================

    /** 检查写操作结果，失败时抛异常（供调用方简洁处理 ResMsg） */
    public static void check(ResMsg r) throws Exception {
        if (r == null || !r.getRes()) {
            throw new Exception(r != null ? r.getMsg() : "数据库操作返回 null");
        }
    }

    // ==================== 条件构造 ====================

    /** 构造等值条件：AND column = value */
    public JSONArray eq(String column, Object value) {
        JSONArray conditions = new JSONArray();
        JSONObject connector = new JSONObject();
        connector.put("Symbol", "and");
        conditions.add(connector);
        JSONObject cond = new JSONObject();
        cond.put("Id", column);
        cond.put("Symbol", "=");
        cond.put("Val", value);
        cond.put("TableAlias", "T");
        conditions.add(cond);
        return conditions;
    }

    // ==================== 查询（返回原始类型，失败抛异常） ====================

    /** 全表查询（可选列、条件、排序），返回原始 JSONArray（列名大写） */
    public JSONArray query(String table, String[] columns, JSONArray conditions,
            String orderByCol, boolean desc) throws Exception {
        JSONObject req = new JSONObject();
        req.put("tableName", table);
        if (columns != null && columns.length > 0) {
            JSONArray cols = new JSONArray();
            for (String c : columns) {
                cols.add(c);
            }
            req.put("columns", cols);
        }
        if (conditions != null) {
            req.put("conditions", conditions);
        }
        if (orderByCol != null) {
            JSONArray orderBy = new JSONArray();
            JSONObject ob = new JSONObject();
            ob.put("column", orderByCol);
            ob.put("direction", desc ? "desc" : "asc");
            orderBy.add(ob);
            req.put("orderBy", orderBy);
        }
        ResMsg r = facade.query(req, DB_NAME);
        if (!r.getRes()) {
            throw new Exception(r.getMsg());
        }
        return (JSONArray) r.getData();
    }

    /** 查询并映射列名：mapping[i] = {大写列名, camelCase名} */
    public JSONArray queryAndMap(String table, String[] columns, JSONArray conditions,
            String orderByCol, boolean desc, String[][] mapping) throws Exception {
        JSONArray rows = query(table, columns, conditions, orderByCol, desc);
        return mapRows(rows, mapping);
    }

    // ==================== 增删改（返回 ResMsg） ====================

    public ResMsg insert(String table, JSONObject data) {
        JSONObject req = new JSONObject();
        req.put("tableName", table);
        req.put("data", data);
        return facade.insert(req, DB_NAME);
    }

    public ResMsg update(String table, JSONObject data, JSONArray conditions) {
        JSONObject req = new JSONObject();
        req.put("tableName", table);
        req.put("data", data);
        req.put("conditions", conditions);
        return facade.update(req, DB_NAME);
    }

    public ResMsg delete(String table, JSONArray conditions) {
        JSONObject req = new JSONObject();
        req.put("tableName", table);
        req.put("conditions", conditions);
        return facade.delete(req, DB_NAME);
    }

    /** upsert：先 update，影响行数为 0 则 insert；update 失败则直接返回错误 */
    public ResMsg upsert(String table, JSONObject data, JSONArray conditions) {
        ResMsg r = update(table, data, conditions);
        if (!r.getRes()) {
            return r;
        }
        Integer affected = (Integer) r.getData();
        if (affected == null || affected == 0) {
            return insert(table, data);
        }
        return r;
    }

    // ==================== 列名映射 ====================

    /** 将单行大写列名映射为 camelCase：mapping[i] = {大写列名, camelCase名} */
    public JSONObject mapRow(JSONObject row, String[][] mapping) {
        JSONObject result = new JSONObject();
        for (String[] m : mapping) {
            String upper = m[0].toUpperCase(Locale.ROOT);
            if (row.containsKey(upper)) {
                result.put(m[1], row.get(upper));
            }
        }
        return result;
    }

    /** 批量映射列名 */
    public JSONArray mapRows(JSONArray rows, String[][] mapping) {
        JSONArray result = new JSONArray();
        for (int i = 0; i < rows.size(); i++) {
            result.add(mapRow(rows.getJSONObject(i), mapping));
        }
        return result;
    }
}