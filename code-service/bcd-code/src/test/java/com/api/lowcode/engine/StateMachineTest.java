package com.api.lowcode.engine;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.common.BizException;
import com.api.lowcode.engine.event.StateChangeEvent;
import com.api.lowcode.engine.spel.SpelExecutor;
import com.api.lowcode.meta.BizMeta;
import com.api.lowcode.meta.FieldMeta;
import com.api.lowcode.meta.StateMachineMeta;
import com.api.lowcode.tx.ActionContext;
import com.common.returns.ResMsg;
import com.server.sqlengine.SqlEngineJson;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * StateMachine 单元测试。
 *
 * <p>覆盖 transitionIfNeed 全分支：无迁移配置、状态字段缺失、缺 id、记录不存在、
 * 状态不匹配、guard 条件失败、并发冲突、正常迁移、guard 为空跳过、禁用迁移跳过。</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class StateMachineTest {

    @Mock
    private SqlEngineJson helper;
    @Mock
    private SpelExecutor spelExecutor;

    @InjectMocks
    private StateMachine stateMachine;

    @Before
    public void setUp() throws Exception {
        lenient().when(helper.eq(anyString(), any())).thenCallRealMethod();
        lenient().when(spelExecutor.evaluateBoolean(anyString(), anyMap())).thenReturn(true);
    }

    private ResMsg okRes(Object data) {
        ResMsg r = new ResMsg();
        r.setData(data);
        return r;
    }

    private BizMeta baseMeta() {
        BizMeta m = new BizMeta();
        m.setBizCode("B1");
        m.setMainTable("t_order");
        m.setStatusField("status");
        m.setFields(new ArrayList<FieldMeta>());
        m.setStateTransitions(new ArrayList<StateMachineMeta>());
        return m;
    }

    private StateMachineMeta transition(String action, String from, String to, String event, String guard, Boolean enabled) {
        StateMachineMeta t = new StateMachineMeta();
        t.setAction(action);
        t.setCurrentState(from);
        t.setNextState(to);
        t.setEvent(event);
        t.setGuardExpr(guard);
        t.setEnabled(enabled);
        return t;
    }

    private ActionContext ctxWithId(String action, String id) {
        ActionContext c = new ActionContext();
        c.setBizCode("B1");
        c.setAction(action);
        JSONObject payload = new JSONObject();
        if (id != null) {
            payload.put("id", id);
        }
        c.setPayload(payload);
        return c;
    }

    private JSONArray rowsWithStatus(String statusField, String statusValue) {
        JSONObject row = new JSONObject();
        row.put(statusField.toUpperCase(), statusValue);
        JSONArray rows = new JSONArray();
        rows.add(row);
        return rows;
    }

    // 测试场景: 无匹配的迁移配置时返回空列表
    @Test
    public void transitionIfNeedNoTransitionReturnsEmpty() throws Exception {
        BizMeta meta = baseMeta();
        List<StateChangeEvent> events = stateMachine.transitionIfNeed(meta, "submit", ctxWithId("submit", "123"));
        assertTrue(events.isEmpty());
    }

    // 测试场景: 状态字段为空时返回空列表
    @Test
    public void transitionIfNeedStatusFieldEmptyReturnsEmpty() throws Exception {
        BizMeta meta = baseMeta();
        meta.setStatusField("");
        meta.getStateTransitions().add(transition("submit", "DRAFT", "SUBMITTED", "evt", null, true));
        List<StateChangeEvent> events = stateMachine.transitionIfNeed(meta, "submit", ctxWithId("submit", "123"));
        assertTrue(events.isEmpty());
    }

    // 测试场景: 缺少 id 抛 BizException(SM_MISSING_ID)
    @Test
    public void transitionIfNeedMissingIdThrows() {
        BizMeta meta = baseMeta();
        meta.getStateTransitions().add(transition("submit", "DRAFT", "SUBMITTED", "evt", null, true));
        try {
            stateMachine.transitionIfNeed(meta, "submit", ctxWithId("submit", null));
            fail("应抛异常");
        } catch (BizException e) {
            assertEquals("SM_MISSING_ID", e.getCode());
        } catch (Exception e) {
            fail("应为 BizException");
        }
    }

    // 测试场景: 记录不存在（查询返回空）抛 BizException(SM_RECORD_NOT_FOUND)
    @Test
    public void transitionIfNeedRecordNotFoundThrows() throws Exception {
        BizMeta meta = baseMeta();
        meta.getStateTransitions().add(transition("submit", "DRAFT", "SUBMITTED", "evt", null, true));
        when(helper.query(anyString(), any(String[].class), any(JSONArray.class), any(), anyBoolean()))
                .thenReturn(new JSONArray());
        try {
            stateMachine.transitionIfNeed(meta, "submit", ctxWithId("submit", "123"));
            fail("应抛异常");
        } catch (BizException e) {
            assertEquals("SM_RECORD_NOT_FOUND", e.getCode());
        } catch (Exception e) {
            fail("应为 BizException");
        }
    }

    // 测试场景: 当前状态与迁移要求状态不匹配抛 BizException(SM_INVALID_STATE)
    @Test
    public void transitionIfNeedInvalidStateThrows() throws Exception {
        BizMeta meta = baseMeta();
        meta.getStateTransitions().add(transition("submit", "DRAFT", "SUBMITTED", "evt", null, true));
        when(helper.query(anyString(), any(String[].class), any(JSONArray.class), any(), anyBoolean()))
                .thenReturn(rowsWithStatus("status", "APPROVED"));
        try {
            stateMachine.transitionIfNeed(meta, "submit", ctxWithId("submit", "123"));
            fail("应抛异常");
        } catch (BizException e) {
            assertEquals("SM_INVALID_STATE", e.getCode());
            assertTrue(e.getMessage().contains("APPROVED"));
        } catch (Exception e) {
            fail("应为 BizException");
        }
    }

    // 测试场景: guard 条件不满足抛 BizException(SM_GUARD_FAILED)
    @Test
    public void transitionIfNeedGuardFailedThrows() throws Exception {
        BizMeta meta = baseMeta();
        meta.getStateTransitions().add(transition("submit", "DRAFT", "SUBMITTED", "evt", "value > 100", true));
        when(helper.query(anyString(), any(String[].class), any(JSONArray.class), any(), anyBoolean()))
                .thenReturn(rowsWithStatus("status", "DRAFT"));
        when(spelExecutor.evaluateBoolean(eq("value > 100"), anyMap())).thenReturn(false);
        try {
            stateMachine.transitionIfNeed(meta, "submit", ctxWithId("submit", "123"));
            fail("应抛异常");
        } catch (BizException e) {
            assertEquals("SM_GUARD_FAILED", e.getCode());
        } catch (Exception e) {
            fail("应为 BizException");
        }
    }

    // 测试场景: guard 通过且更新影响行数非 0，正常迁移并生成事件
    @Test
    public void transitionIfNeedGuardPassesStateUpdated() throws Exception {
        BizMeta meta = baseMeta();
        meta.getStateTransitions().add(transition("submit", "DRAFT", "SUBMITTED", "submitted", "value > 100", true));
        when(helper.query(anyString(), any(String[].class), any(JSONArray.class), any(), anyBoolean()))
                .thenReturn(rowsWithStatus("status", "DRAFT"));
        when(helper.update(anyString(), any(JSONObject.class), any(JSONArray.class))).thenReturn(okRes(1));

        List<StateChangeEvent> events = stateMachine.transitionIfNeed(meta, "submit", ctxWithId("submit", "123"));

        assertEquals(1, events.size());
        StateChangeEvent evt = events.get(0);
        assertEquals("B1", evt.getBizCode());
        assertEquals("submit", evt.getAction());
        assertEquals("123", evt.getBusinessId());
        assertEquals("DRAFT", evt.getFromState());
        assertEquals("SUBMITTED", evt.getToState());
        assertEquals("submitted", evt.getEvent());
    }

    // 测试场景: guard 为空时跳过 guard 校验，直接迁移
    @Test
    public void transitionIfNeedNoGuardStateUpdated() throws Exception {
        BizMeta meta = baseMeta();
        meta.getStateTransitions().add(transition("submit", "DRAFT", "SUBMITTED", "submitted", null, true));
        when(helper.query(anyString(), any(String[].class), any(JSONArray.class), any(), anyBoolean()))
                .thenReturn(rowsWithStatus("status", "DRAFT"));
        when(helper.update(anyString(), any(JSONObject.class), any(JSONArray.class))).thenReturn(okRes(1));

        List<StateChangeEvent> events = stateMachine.transitionIfNeed(meta, "submit", ctxWithId("submit", "123"));

        assertEquals(1, events.size());
        assertEquals("SUBMITTED", events.get(0).getToState());
    }

    // 测试场景: 更新影响行数为 0 抛 BizException(SM_CONCURRENT_CONFLICT)
    @Test
    public void transitionIfNeedConcurrentConflictThrows() throws Exception {
        BizMeta meta = baseMeta();
        meta.getStateTransitions().add(transition("submit", "DRAFT", "SUBMITTED", "submitted", null, true));
        when(helper.query(anyString(), any(String[].class), any(JSONArray.class), any(), anyBoolean()))
                .thenReturn(rowsWithStatus("status", "DRAFT"));
        when(helper.update(anyString(), any(JSONObject.class), any(JSONArray.class))).thenReturn(okRes(0));
        try {
            stateMachine.transitionIfNeed(meta, "submit", ctxWithId("submit", "123"));
            fail("应抛异常");
        } catch (BizException e) {
            assertEquals("SM_CONCURRENT_CONFLICT", e.getCode());
        } catch (Exception e) {
            fail("应为 BizException");
        }
    }

    // 测试场景: 更新返回 data 为 null 视为 0 行，抛并发冲突
    @Test
    public void transitionIfNeedNullAffectedThrowsConflict() throws Exception {
        BizMeta meta = baseMeta();
        meta.getStateTransitions().add(transition("submit", "DRAFT", "SUBMITTED", "submitted", null, true));
        when(helper.query(anyString(), any(String[].class), any(JSONArray.class), any(), anyBoolean()))
                .thenReturn(rowsWithStatus("status", "DRAFT"));
        when(helper.update(anyString(), any(JSONObject.class), any(JSONArray.class))).thenReturn(okRes(null));
        try {
            stateMachine.transitionIfNeed(meta, "submit", ctxWithId("submit", "123"));
            fail("应抛异常");
        } catch (BizException e) {
            assertEquals("SM_CONCURRENT_CONFLICT", e.getCode());
        } catch (Exception e) {
            fail("应为 BizException");
        }
    }

    // 测试场景: 禁用的迁移配置被跳过，返回空列表
    @Test
    public void transitionIfNeedDisabledTransitionSkipped() throws Exception {
        BizMeta meta = baseMeta();
        meta.getStateTransitions().add(transition("submit", "DRAFT", "SUBMITTED", "submitted", null, false));
        List<StateChangeEvent> events = stateMachine.transitionIfNeed(meta, "submit", ctxWithId("submit", "123"));
        assertTrue(events.isEmpty());
    }
}