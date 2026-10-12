package com.api.lowcode.engine;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.common.BizException;
import com.api.lowcode.engine.event.StateChangeEvent;
import com.api.lowcode.engine.spel.SpelExecutor;
import com.api.lowcode.meta.BizMeta;
import com.api.lowcode.meta.StateMachineMeta;
import com.api.lowcode.tx.ActionContext;
import com.common.returns.ResMsg;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class StateMachine {

    @Resource
    private SqlEngineJson helper;

    @Resource
    private SpelExecutor spelExecutor;

    public List<StateChangeEvent> transitionIfNeed(BizMeta meta, String action, ActionContext ctx) throws Exception {
        StateMachineMeta transition = findTransition(meta, action);
        if (transition == null) {
            return Collections.emptyList();
        }
        String bizCode = meta.getBizCode();
        String statusField = meta.getStatusField();
        if (statusField == null || statusField.isEmpty()) {
            return Collections.emptyList();
        }
        JSONObject payload = ctx.getPayload();
        String businessId = payload.getString("id");
        if (businessId == null || businessId.isEmpty()) {
            throw new BizException("SM_MISSING_ID", "状态迁移操作缺少 id");
        }
        String currentState = loadCurrentState(meta, businessId, statusField);
        if (currentState == null) {
            throw new BizException("SM_RECORD_NOT_FOUND", "记录不存在: " + businessId);
        }
        if (!currentState.equals(transition.getCurrentState())) {
            throw new BizException("SM_INVALID_STATE",
                    String.format("状态不匹配，当前=%s，需要=%s", currentState, transition.getCurrentState()));
        }
        if (transition.getGuardExpr() != null && !transition.getGuardExpr().isEmpty()) {
            Map<String, Object> vars = new HashMap<>(payload);
            vars.put("currentState", currentState);
            vars.put("bizCode", bizCode);
            if (!spelExecutor.evaluateBoolean(transition.getGuardExpr(), vars)) {
                throw new BizException("SM_GUARD_FAILED", "状态迁移守卫条件不满足: " + transition.getGuardExpr());
            }
        }
        String nextState = transition.getNextState();
        JSONObject data = new JSONObject();
        data.put(statusField, nextState);
        JSONArray conditions = helper.eq("id", businessId);
        ResMsg r = helper.update(meta.getMainTable(), data, conditions);
        SqlEngineJson.check(r);
        String affected = r.getData() != null ? r.getData().toString() : "0";
        if ("0".equals(affected)) {
            throw new BizException("SM_CONCURRENT_CONFLICT",
                    "状态已被其他操作修改，请刷新后重试: " + businessId);
        }
        log.info("[StateMachine] 状态迁移成功 bizCode={} id={} {} -> {}", bizCode, businessId, currentState, nextState);
        List<StateChangeEvent> events = new ArrayList<>();
        events.add(new StateChangeEvent(bizCode, action, businessId, currentState, nextState, transition.getEvent()));
        return events;
    }

    private StateMachineMeta findTransition(BizMeta meta, String action) {
        for (StateMachineMeta sm : meta.getStateTransitions()) {
            if (sm.isEnabled() && action.equals(sm.getAction())) {
                return sm;
            }
        }
        return null;
    }

    private String loadCurrentState(BizMeta meta, String businessId, String statusField) throws Exception {
        String[] cols = { statusField };
        JSONArray rows = helper.query(meta.getMainTable(), cols, helper.eq("id", businessId), null, false);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        JSONObject row = rows.getJSONObject(0);
        String key = statusField.toUpperCase();
        return row.getString(key);
    }
}