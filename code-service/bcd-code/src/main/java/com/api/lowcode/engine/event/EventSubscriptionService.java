package com.api.lowcode.engine.event;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class EventSubscriptionService {

    @Resource
    private SqlEngineJson helper;

    public List<EventSubscription> findSubscriptions(String bizCode, String eventType) {
        List<EventSubscription> result = new ArrayList<>();
        try {
            String[] cols = { "id", "biz_code", "event_type", "handler_type", "handler_config", "enabled" };
            JSONArray conditions = helper.eq("biz_code", bizCode);
            JSONArray rows = helper.query("lc_event_subscription", cols, conditions, null, false);
            if (rows == null) {
                return result;
            }
            for (int i = 0; i < rows.size(); i++) {
                JSONObject row = rows.getJSONObject(i);
                EventSubscription sub = new EventSubscription();
                sub.setId(row.getString("ID"));
                sub.setBizCode(row.getString("BIZ_CODE"));
                sub.setEventType(row.getString("EVENT_TYPE"));
                sub.setHandlerType(row.getString("HANDLER_TYPE"));
                sub.setHandlerConfig(row.getString("HANDLER_CONFIG"));
                sub.setEnabled(row.getBoolean("ENABLED"));
                if (sub.isEnabled() && eventType.equals(sub.getEventType())) {
                    result.add(sub);
                }
            }
        } catch (Exception e) {
            log.error("[EventSubscriptionService] 查询订阅配置失败 bizCode={} eventType={}", bizCode, eventType, e);
        }
        return result;
    }

    public boolean isConsumed(String messageKey) {
        try {
            String[] cols = { "id" };
            JSONArray rows = helper.query("lc_event_consume_log", cols, helper.eq("message_key", messageKey), null,
                    false);
            return rows != null && !rows.isEmpty();
        } catch (Exception e) {
            log.warn("[EventSubscriptionService] 幂等检查异常 messageKey={}", messageKey, e);
            return false;
        }
    }

    public void markConsumed(String messageKey, String topic) {
        try {
            JSONObject data = new JSONObject();
            data.put("message_key", messageKey);
            data.put("topic", topic);
            data.put("consumed_at", java.time.Instant.now().toString());
            helper.insert("lc_event_consume_log", data);
        } catch (Exception e) {
            log.warn("[EventSubscriptionService] 幂等记录异常 messageKey={}", messageKey, e);
        }
    }
}