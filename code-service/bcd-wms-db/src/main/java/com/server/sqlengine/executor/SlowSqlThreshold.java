package com.server.sqlengine.executor;

import com.alibaba.fastjson2.JSONArray;
import com.server.sqlengine.model.CompiledSql;
import com.server.sqlengine.model.SqlParam;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 慢 SQL 阈值配置 —— 可动态调整告警阈值（毫秒）。
 *
 * @author liwei
 * @date 2026/9/9
 */
public class SlowSqlThreshold {

    private volatile long thresholdMillis = 3000L;

    public long getThresholdMillis() {
        return thresholdMillis;
    }

    public void setThresholdMillis(long thresholdMillis) {
        this.thresholdMillis = thresholdMillis;
    }
}
