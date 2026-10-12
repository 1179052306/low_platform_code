package com.api.lowcode.engine.event;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Component
@RocketMQMessageListener(
        topic = "lowcode_event_STATE_CHANGE",
        consumerGroup = "lowcode_event_state_change_group"
)
public class StateChangeAsyncConsumer implements RocketMQListener<String> {

    @Resource
    private EventSubscriptionService subscriptionService;

    @Resource
    private EventHandlerDispatcher handlerDispatcher;

    @Override
    public void onMessage(String message) {
        try {
            JSONObject eventJson = JSONObject.parseObject(message);
            String bizCode = eventJson.getString("bizCode");
            String eventType = eventJson.getString("type");
            String businessId = eventJson.getString("businessId");
            String messageKey = bizCode + ":" + eventType + ":" + businessId;
            if (subscriptionService.isConsumed(messageKey)) {
                log.debug("[AsyncConsumer] 消息已消费，跳过: {}", messageKey);
                return;
            }
            List<EventSubscription> subs = subscriptionService.findSubscriptions(bizCode, eventType);
            if (subs.isEmpty()) {
                log.debug("[AsyncConsumer] 无订阅配置 bizCode={} eventType={}", bizCode, eventType);
            }
            for (EventSubscription sub : subs) {
                handlerDispatcher.handle(sub, eventJson);
            }
            subscriptionService.markConsumed(messageKey, "lowcode_event_STATE_CHANGE");
            log.info("[AsyncConsumer] 消费完成 bizCode={} eventType={} businessId={}", bizCode, eventType,
                    businessId);
        } catch (Exception e) {
            log.error("[AsyncConsumer] 消费异常 message={}", message, e);
        }
    }
}