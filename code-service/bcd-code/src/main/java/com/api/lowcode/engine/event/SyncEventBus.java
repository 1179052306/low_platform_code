package com.api.lowcode.engine.event;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class SyncEventBus implements EventBus {

    private final List<BizEventListener> listeners = new CopyOnWriteArrayList<>();

    @Autowired(required = false)
    private org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

    public void registerListener(BizEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public void fireSync(BizEvent event) throws Exception {
        log.info("[EventBus] fireSync type={} bizCode={} action={}", event.getType(), event.getBizCode(),
                event.getAction());
        for (BizEventListener listener : listeners) {
            try {
                if (listener.support(event)) {
                    listener.onEvent(event);
                }
            } catch (Exception e) {
                log.error("[EventBus] 同步监听器执行失败 listener={}", listener.getClass().getSimpleName(), e);
                throw e;
            }
        }
    }

    @Override
    public void fireAsync(BizEvent event) {
        if (rocketMQTemplate == null) {
            log.warn("[EventBus] RocketMQTemplate 未注入，fireAsync 忽略 type={} bizCode={}", event.getType(),
                    event.getBizCode());
            return;
        }
        String topic = "lowcode_event_" + event.getType();
        String json = serializeEvent(event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendAsync(topic, json, event);
                }
            });
            log.debug("[EventBus] fireAsync 注册 afterCommit topic={} bizCode={}", topic, event.getBizCode());
        } else {
            sendAsync(topic, json, event);
        }
    }

    private void sendAsync(String topic, String json, BizEvent event) {
        try {
            rocketMQTemplate.asyncSend(topic, MessageBuilder.withPayload(json).build(),
                    new org.apache.rocketmq.client.producer.SendCallback() {
                        @Override
                        public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                            log.info("[EventBus] 异步事件发送成功 topic={} bizCode={}", topic, event.getBizCode());
                        }

                        @Override
                        public void onException(Throwable e) {
                            log.error("[EventBus] 异步事件发送失败 topic={} bizCode={}", topic,
                                    event.getBizCode(), e);
                        }
                    });
        } catch (Exception e) {
            log.error("[EventBus] fireAsync 发送异常 topic={} bizCode={}", topic, event.getBizCode(), e);
        }
    }

    private String serializeEvent(BizEvent event) {
        JSONObject json = new JSONObject();
        json.put("type", event.getType());
        json.put("bizCode", event.getBizCode());
        json.put("action", event.getAction());
        json.put("timestamp", event.getTimestamp().toString());
        if (event instanceof StateChangeEvent) {
            StateChangeEvent sce = (StateChangeEvent) event;
            json.put("businessId", sce.getBusinessId());
            json.put("fromState", sce.getFromState());
            json.put("toState", sce.getToState());
            json.put("event", sce.getEvent());
        }
        return json.toJSONString();
    }
}