package com.api.lowcode.tx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.annotation.Resource;

@Slf4j
@Component
public class TransactionOrchestrator {

    @Resource
    private PlatformTransactionManager txManager;

    private int defaultTimeout = 30;

    public Object run(String bizCode, String action, ActionContext ctx, Action body) throws Exception {
        DefaultTransactionDefinition def = new DefaultTransactionDefinition(
                TransactionDefinition.PROPAGATION_REQUIRED);
        def.setTimeout(defaultTimeout);
        TransactionStatus status = txManager.getTransaction(def);
        try {
            Object result = body.execute(ctx);
            txManager.commit(status);
            log.debug("[TxOrchestrator] 事务提交成功 bizCode={} action={}", bizCode, action);
            return result;
        } catch (Throwable e) {
            try {
                txManager.rollback(status);
            } catch (Exception rollbackEx) {
                log.error("[TxOrchestrator] 回滚失败 bizCode={} action={}", bizCode, action, rollbackEx);
            }
            log.debug("[TxOrchestrator] 事务回滚 bizCode={} action={} reason={}", bizCode, action, e.getMessage());
            if (e instanceof Exception) {
                throw (Exception) e;
            }
            throw new RuntimeException(e);
        }
    }

    public void setDefaultTimeout(int seconds) {
        this.defaultTimeout = seconds;
    }
}