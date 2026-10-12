package com.api.lowcode.gateway;

import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.common.BizException;
import com.api.lowcode.engine.CrudExecutor;
import com.api.lowcode.engine.ExternalApiExecutor;
import com.api.lowcode.engine.StateMachine;
import com.api.lowcode.engine.event.EventBus;
import com.api.lowcode.engine.event.StateChangeEvent;
import com.api.lowcode.meta.ActionMeta;
import com.api.lowcode.meta.BizMeta;
import com.api.lowcode.meta.MetaRegistry;
import com.api.lowcode.plugin.PluginContainer;
import com.api.lowcode.monitor.GatewayMonitor;
import com.api.lowcode.tx.ActionContext;
import com.api.lowcode.tx.TransactionOrchestrator;
import com.api.lowcode.workflow.WorkflowService;
import com.common.returns.ResMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/lowcode/gateway")
public class GenericGatewayController {

    @Resource
    private MetaRegistry metaRegistry;

    @Resource
    private CrudExecutor crudExecutor;

    @Resource
    private ExternalApiExecutor externalApiExecutor;

    @Resource
    private StateMachine stateMachine;

    @Resource
    private EventBus eventBus;

    @Resource
    private TransactionOrchestrator txOrchestrator;

    @Resource
    private PluginContainer pluginContainer;

    @Resource
    private GatewayMonitor gatewayMonitor;

    @Resource
    private WorkflowService workflowService;

    @PostMapping
    public ResMsg handle(@RequestBody JSONObject req) {
        ResMsg r = new ResMsg();
        String bizCode = req.getString("bizCode");
        String action = req.getString("action");
        JSONObject payload = req.getJSONObject("payload");
        JSONObject context = req.getJSONObject("context");
        if (bizCode == null || bizCode.isEmpty()) {
            r.setRes(false);
            r.setCode("400");
            r.setMsg("bizCode 不能为空");
            return r;
        }
        if (action == null || action.isEmpty()) {
            r.setRes(false);
            r.setCode("400");
            r.setMsg("action 不能为空");
            return r;
        }
        if (payload == null) {
            payload = new JSONObject();
        }
        BizMeta meta = metaRegistry.getMeta(bizCode);
        if (meta == null) {
            r.setRes(false);
            r.setCode("404");
            r.setMsg("未找到业务元数据: " + bizCode);
            return r;
        }
        ActionContext ctx = new ActionContext();
        ctx.setBizCode(bizCode);
        ctx.setAction(action);
        ctx.setPayload(payload);
        ctx.setContext(context);
        long startTime = System.currentTimeMillis();
        boolean success = false;
        try {
            Object result = txOrchestrator.run(bizCode, action, ctx, ac -> {
                pluginContainer.invokePreHooks(bizCode, action, ac);
                ActionMeta am = meta.getAction(action);
                Object crudResult;
                if (am != null && "EXTERNAL_API".equals(am.getStrategyType())) {
                    crudResult = externalApiExecutor.execute(meta, action, ac);
                } else {
                    crudResult = crudExecutor.execute(meta, action, ac);
                }
                List<StateChangeEvent> stateEvents = stateMachine.transitionIfNeed(meta, action, ac);
                for (StateChangeEvent event : stateEvents) {
                    eventBus.fireSync(event);
                    eventBus.fireAsync(event);
                }
                pluginContainer.invokePostHooks(bizCode, action, ac);
                JSONObject combined = new JSONObject();
                if (crudResult instanceof JSONObject) {
                    combined.putAll((JSONObject) crudResult);
                } else {
                    combined.put("data", crudResult);
                }
                if (!stateEvents.isEmpty()) {
                    combined.put("stateChanged", true);
                    StateChangeEvent first = stateEvents.get(0);
                    combined.put("fromState", first.getFromState());
                    combined.put("toState", first.getToState());
                }
                return combined;
            });
            r.setData(result);
            success = true;
            log.info("[Gateway] bizCode={} action={} 成功", bizCode, action);
            try {
                String bizId = payload.getString("id");
                if (bizId == null && context != null) bizId = context.getString("bizId");
                String operator = context != null ? context.getString("operator") : null;
                if (operator == null) operator = "system";
                workflowService.checkBizFlowTrigger(bizCode, action, bizId, operator);
            } catch (Exception te) {
                log.warn("[Gateway] 业务流触发失败（不影响主操作）: bizCode={} action={} error={}", bizCode, action, te.getMessage());
            }
        } catch (BizException e) {
            r.setRes(false);
            r.setCode(e.getCode());
            r.setMsg(e.getMessage());
            log.warn("[Gateway] bizCode={} action={} 业务异常: {} - {}", bizCode, action, e.getCode(), e.getMessage());
        } catch (IllegalArgumentException e) {
            r.setRes(false);
            r.setCode("400");
            r.setMsg(e.getMessage());
            log.warn("[Gateway] bizCode={} action={} 参数错误: {}", bizCode, action, e.getMessage());
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
            log.error("[Gateway] bizCode={} action={} 执行失败", bizCode, action, e);
        }
        gatewayMonitor.recordRequest(bizCode, action, System.currentTimeMillis() - startTime, success);
        return r;
    }
}
