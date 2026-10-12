package com.api.lowcode.monitor;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class GatewayMonitor {

    private final ConcurrentMap<String, AtomicLong> requestCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> successCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> failureCounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> totalDurations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> pluginExecCounts = new ConcurrentHashMap<>();

    public void recordRequest(String bizCode, String action, long durationMs, boolean success) {
        String key = bizCode + ":" + action;
        requestCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        totalDurations.computeIfAbsent(key, k -> new AtomicLong(0)).addAndGet(durationMs);
        if (success) {
            successCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        } else {
            failureCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    public void recordPluginExec(String pluginId, String hookType) {
        String key = pluginId + ":" + hookType;
        pluginExecCounts.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    public JSONObject getStats() {
        JSONObject stats = new JSONObject();
        JSONObject actions = new JSONObject();
        for (String key : requestCounts.keySet()) {
            JSONObject item = new JSONObject();
            long total = requestCounts.get(key).get();
            long success = successCounts.getOrDefault(key, new AtomicLong(0)).get();
            long failure = failureCounts.getOrDefault(key, new AtomicLong(0)).get();
            long duration = totalDurations.getOrDefault(key, new AtomicLong(0)).get();
            item.put("total", total);
            item.put("success", success);
            item.put("failure", failure);
            item.put("avgDurationMs", total > 0 ? duration / total : 0);
            item.put("successRate", total > 0 ? (double) success / total : 0);
            actions.put(key, item);
        }
        stats.put("actions", actions);
        JSONObject plugins = new JSONObject();
        for (String key : pluginExecCounts.keySet()) {
            plugins.put(key, pluginExecCounts.get(key).get());
        }
        stats.put("pluginExecs", plugins);
        return stats;
    }

    public void reset() {
        requestCounts.clear();
        successCounts.clear();
        failureCounts.clear();
        totalDurations.clear();
        pluginExecCounts.clear();
        log.info("[GatewayMonitor] 监控数据已重置");
    }
}