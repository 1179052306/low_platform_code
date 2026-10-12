package com.api.lowcode.workflow;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class WorkflowScheduler {

    @Resource
    private WorkflowService workflowService;

    @Scheduled(fixedDelay = 10000)
    public void resumeWaitingInstances() {
        try {
            workflowService.resumeWaitingInstances();
        } catch (Exception e) {
            log.error("[WorkflowScheduler] 恢复等待实例失败", e);
        }
    }
}