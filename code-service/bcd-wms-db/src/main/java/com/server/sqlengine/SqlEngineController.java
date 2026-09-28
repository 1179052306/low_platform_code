package com.server.sqlengine;

import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * SQL 引擎 REST 接口 —— 前端通过 JSON 调用增删改查。
 *
 * <p>
 * 所有接口接受 {@code JSONObject} 请求体，通过 {@link SqlEngineFacade} 编译执行，
 * 统一返回 {@link ResMsg}。
 * </p>
 *
 * @author liwei
 * @date 2026/9/9
 */
@Slf4j
@RestController
@RequestMapping("/api/sql-engine")
public class SqlEngineController {

    @Autowired
    private SqlEngineFacade facade;

    @PostMapping("/query")
    public ResMsg query(@RequestBody JSONObject req,
            @RequestHeader(value = "X-DB-Name", required = false) String dbName) {
        return facade.query(req, dbName);
    }

    @PostMapping("/insert")
    public ResMsg insert(@RequestBody JSONObject req,
            @RequestHeader(value = "X-DB-Name", required = false) String dbName) {
        return facade.insert(req, dbName);
    }

    @PostMapping("/batch-insert")
    public ResMsg batchInsert(@RequestBody JSONObject req,
            @RequestHeader(value = "X-DB-Name", required = false) String dbName) {
        return facade.batchInsert(req, dbName);
    }

    @PostMapping("/update")
    public ResMsg update(@RequestBody JSONObject req,
            @RequestHeader(value = "X-DB-Name", required = false) String dbName) {
        return facade.update(req, dbName);
    }

    @PostMapping("/delete")
    public ResMsg delete(@RequestBody JSONObject req,
            @RequestHeader(value = "X-DB-Name", required = false) String dbName) {
        return facade.delete(req, dbName);
    }

    @PostMapping("/batch-update")
    public ResMsg batchUpdate(@RequestBody JSONObject req,
            @RequestHeader(value = "X-DB-Name", required = false) String dbName) {
        return facade.batchUpdate(req, dbName);
    }

    @PostMapping("/batch-delete")
    public ResMsg batchDelete(@RequestBody JSONObject req,
            @RequestHeader(value = "X-DB-Name", required = false) String dbName) {
        return facade.batchDelete(req, dbName);
    }

    @PostMapping("/batch-query")
    public ResMsg batchQuery(@RequestBody JSONObject req,
            @RequestHeader(value = "X-DB-Name", required = false) String dbName) {
        return facade.batchQuery(req, dbName);
    }

    @PostMapping("/count")
    public ResMsg count(@RequestBody JSONObject req,
            @RequestHeader(value = "X-DB-Name", required = false) String dbName) {
        return facade.count(req, dbName);
    }

    @PostMapping("/template-query")
    public ResMsg templateQuery(@RequestBody JSONObject req,
            @RequestHeader(value = "X-DB-Name", required = false) String dbName) {
        return facade.query(req, dbName);
    }
}
