package com.api.lowcode.engine.event;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class EventHandlerDispatcher {

    @Autowired(required = false)
    private RestTemplate restTemplate;

    public void handle(EventSubscription subscription, JSONObject eventJson) {
        String handlerType = subscription.getHandlerType();
        try {
            switch (handlerType) {
                case "NOTIFY":
                    handleNotify(subscription, eventJson);
                    break;
                case "WEBHOOK":
                    handleWebhook(subscription, eventJson);
                    break;
                case "MQ_TOPIC":
                    handleMqTopic(subscription, eventJson);
                    break;
                default:
                    log.warn("[EventHandler] 未知 handlerType={} subscription={}", handlerType,
                            subscription.getId());
            }
        } catch (Exception e) {
            log.error("[EventHandler] 处理失败 handlerType={} subscription={}", handlerType,
                    subscription.getId(), e);
        }
    }

    private void handleNotify(EventSubscription sub, JSONObject eventJson) {
        log.info("[EventHandler] NOTIFY subscription={} bizCode={} event={}", sub.getId(),
                sub.getBizCode(), eventJson.getString("type"));
    }

    private void handleWebhook(EventSubscription sub, JSONObject eventJson) {
        String config = sub.getHandlerConfig();
        if (config == null || config.isEmpty()) {
            log.warn("[EventHandler] WEBHOOK 缺少 handlerConfig subscription={}", sub.getId());
            return;
        }
        JSONObject cfg = JSONObject.parseObject(config);
        String url = cfg.getString("url");
        if (url == null || url.isEmpty()) {
            log.warn("[EventHandler] WEBHOOK 缺少 url subscription={}", sub.getId());
            return;
        }
        if (restTemplate == null) {
            log.warn("[EventHandler] RestTemplate 未注入，WEBHOOK 跳过 subscription={}", sub.getId());
            return;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(eventJson.toJSONString(), headers);
        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        log.info("[EventHandler] WEBHOOK 回调完成 subscription={} status={}", sub.getId(),
                resp.getStatusCodeValue());
    }

    private void handleMqTopic(EventSubscription sub, JSONObject eventJson) {
        log.info("[EventHandler] MQ_TOPIC subscription={} bizCode={} event={} (待实现转发逻辑)", sub.getId(),
                sub.getBizCode(), eventJson.getString("type"));
    }
}