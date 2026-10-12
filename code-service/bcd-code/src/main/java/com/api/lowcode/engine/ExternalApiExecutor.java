package com.api.lowcode.engine;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.meta.ActionMeta;
import com.api.lowcode.meta.BizMeta;
import com.api.lowcode.tx.ActionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;

@Slf4j
@Component
public class ExternalApiExecutor {

    @Resource
    private RestTemplate restTemplate;

    public Object execute(BizMeta meta, String action, ActionContext ctx) throws Exception {
        ActionMeta actionMeta = meta.getAction(action);
        if (actionMeta == null) {
            throw new IllegalArgumentException("业务 " + meta.getBizCode() + " 不支持操作: " + action);
        }
        String url = actionMeta.getExtApiUrl();
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("外部 API 地址未配置: bizCode=" + meta.getBizCode() + " action=" + action);
        }
        String method = actionMeta.getExtApiMethod();
        if (method == null || method.isEmpty()) {
            method = "GET";
        }
        JSONObject payload = ctx.getPayload();
        String dataPath = actionMeta.getExtDataPath() != null ? actionMeta.getExtDataPath() : "data";
        String totalPath = actionMeta.getExtTotalPath() != null ? actionMeta.getExtTotalPath() : "totalCount";

        log.info("[ExternalApi] bizCode={} action={} url={} method={}", meta.getBizCode(), action, url, method);

        ResponseEntity<String> resp;
        if ("POST".equalsIgnoreCase(method)) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<JSONObject> entity = new HttpEntity<>(payload, headers);
            resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        } else {
            StringBuilder sb = new StringBuilder(url);
            if (url.contains("?")) {
                sb.append("&");
            } else {
                sb.append("?");
            }
            for (String key : payload.keySet()) {
                Object val = payload.get(key);
                if (val != null) {
                    sb.append(key).append("=").append(val).append("&");
                }
            }
            String fullUrl = sb.toString();
            if (fullUrl.endsWith("&")) {
                fullUrl = fullUrl.substring(0, fullUrl.length() - 1);
            }
            resp = restTemplate.exchange(fullUrl, HttpMethod.GET, null, String.class);
        }

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new RuntimeException("外部 API 调用失败: HTTP " + resp.getStatusCodeValue());
        }

        JSONObject respJson = JSONObject.parseObject(resp.getBody());
        JSONArray data = extractPath(respJson, dataPath, new JSONArray());
        int totalCount = extractPath(respJson, totalPath, data.size());

        JSONObject result = new JSONObject();
        result.put("data", data);
        result.put("totalCount", totalCount);
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> T extractPath(JSONObject json, String path, T defaultValue) {
        if (path == null || path.isEmpty()) {
            return (T) json;
        }
        String[] parts = path.split("\\.");
        Object current = json;
        for (String part : parts) {
            if (current instanceof JSONObject) {
                current = ((JSONObject) current).get(part);
            } else {
                return defaultValue;
            }
            if (current == null) {
                return defaultValue;
            }
        }
        if (current == null) {
            return defaultValue;
        }
        return (T) current;
    }
}