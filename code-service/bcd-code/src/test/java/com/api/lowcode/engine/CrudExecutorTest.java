package com.api.lowcode.engine;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.common.BizException;
import com.api.lowcode.engine.spel.SpelExecutor;
import com.api.lowcode.meta.ActionMeta;
import com.api.lowcode.meta.BizMeta;
import com.api.lowcode.meta.FieldMeta;
import com.api.lowcode.tx.ActionContext;
import com.common.returns.ResMsg;
import com.server.sqlengine.SqlEngineFacade;
import com.server.sqlengine.SqlEngineJson;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CrudExecutor 单元测试。
 *
 * <p>覆盖 bug 修复1：update 部分更新时 validateFields(checkRequired=false) 不校验缺失必填字段；
 * bug 修复2：submit/revoke 等状态迁移动作走 doStatusTransition 占位方法。</p>
 */
@RunWith(MockitoJUnitRunner.class)
public class CrudExecutorTest {

    @Mock
    private SqlEngineJson helper;
    @Mock
    private SqlEngineFacade sqlEngineFacade;
    @Mock
    private SpelExecutor spelExecutor;

    @InjectMocks
    private CrudExecutor crudExecutor;

    @Before
    public void setUp() throws Exception {
        // eq 走真实逻辑（纯构造，不依赖 facade）
        lenient().when(helper.eq(anyString(), any())).thenCallRealMethod();
        // 默认校验/权限通过
        lenient().when(spelExecutor.evaluateBoolean(anyString(), anyMap())).thenReturn(true);
    }

    private ResMsg okRes() {
        return new ResMsg();
    }

    private ResMsg failRes(String msg) {
        ResMsg r = new ResMsg();
        r.setRes(false);
        r.setMsg(msg);
        return r;
    }

    private BizMeta baseMeta(String bizCode, String table) {
        BizMeta m = new BizMeta();
        m.setBizCode(bizCode);
        m.setMainTable(table);
        m.setFields(new ArrayList<FieldMeta>());
        m.setActions(new LinkedHashMap<String, ActionMeta>());
        return m;
    }

    private ActionMeta enabledAction(String action) {
        ActionMeta a = new ActionMeta();
        a.setAction(action);
        a.setEnabled(true);
        return a;
    }

    private ActionContext ctx(String action, JSONObject payload) {
        ActionContext c = new ActionContext();
        c.setBizCode("B1");
        c.setAction(action);
        c.setPayload(payload);
        return c;
    }

    private FieldMeta field(String name) {
        FieldMeta f = new FieldMeta();
        f.setFieldName(name);
        return f;
    }

    // ==================== execute 分发 ====================

    // 测试场景: actionMeta 为 null 时抛 IllegalArgumentException
    @Test
    public void executeActionMetaNullThrows() {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.setActions(new LinkedHashMap<String, ActionMeta>());
        try {
            crudExecutor.execute(meta, "create", ctx("create", new JSONObject()));
            fail("应抛异常");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("不支持操作"));
        } catch (Exception e) {
            fail("应为 IllegalArgumentException");
        }
    }

    // 测试场景: actionMeta 被禁用时抛 IllegalArgumentException
    @Test
    public void executeActionDisabledThrows() {
        BizMeta meta = baseMeta("B1", "t_order");
        ActionMeta a = enabledAction("create");
        a.setEnabled(false);
        meta.getActions().put("create", a);
        try {
            crudExecutor.execute(meta, "create", ctx("create", new JSONObject()));
            fail("应抛异常");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("不支持操作"));
        } catch (Exception e) {
            fail("应为 IllegalArgumentException");
        }
    }

    // ==================== create ====================

    // 测试场景: create 正常流程，自动生成 id 并返回
    @Test
    public void createReturnsGeneratedId() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getFields().add(field("name"));
        meta.getActions().put("create", enabledAction("create"));
        JSONObject payload = new JSONObject();
        payload.put("name", "abc");
        when(helper.insert(anyString(), any(JSONObject.class))).thenReturn(okRes());

        Object result = crudExecutor.execute(meta, "create", ctx("create", payload));

        assertTrue(result instanceof JSONObject);
        JSONObject json = (JSONObject) result;
        assertNotNull(json.getString("id"));
        assertEquals(32, json.getString("id").length());
        verify(helper).insert(eq("t_order"), any(JSONObject.class));
    }

    // 测试场景: create 时缺失必填字段抛 BizException(FIELD_REQUIRED)
    @Test
    public void createMissingRequiredFieldThrows() {
        BizMeta meta = baseMeta("B1", "t_order");
        FieldMeta name = field("name");
        name.setIsRequired(true);
        meta.getFields().add(field("id"));
        meta.getFields().add(name);
        meta.getActions().put("create", enabledAction("create"));
        JSONObject payload = new JSONObject();
        try {
            crudExecutor.execute(meta, "create", ctx("create", payload));
            fail("应抛异常");
        } catch (BizException e) {
            assertEquals("FIELD_REQUIRED", e.getCode());
        } catch (Exception e) {
            fail("应为 BizException");
        }
    }

    // 测试场景: create 时 validationExpr 不满足抛 BizException(VALIDATION_FAILED)
    @Test
    public void createValidationExprFailsThrows() {
        BizMeta meta = baseMeta("B1", "t_order");
        FieldMeta age = field("age");
        age.setValidationExpr("value >= 0");
        meta.getFields().add(field("id"));
        meta.getFields().add(age);
        meta.getActions().put("create", enabledAction("create"));
        JSONObject payload = new JSONObject();
        payload.put("age", -1);
        when(spelExecutor.evaluateBoolean(eq("value >= 0"), anyMap())).thenReturn(false);
        try {
            crudExecutor.execute(meta, "create", ctx("create", payload));
            fail("应抛异常");
        } catch (BizException e) {
            assertEquals("VALIDATION_FAILED", e.getCode());
        } catch (Exception e) {
            fail("应为 BizException");
        }
    }

    // 测试场景: create 时 helper.insert 失败抛异常
    @Test
    public void createInsertFailsThrows() {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getActions().put("create", enabledAction("create"));
        when(helper.insert(anyString(), any(JSONObject.class))).thenReturn(failRes("db error"));
        try {
            crudExecutor.execute(meta, "create", ctx("create", new JSONObject()));
            fail("应抛异常");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("db error"));
        }
    }

    // 测试场景: create 时 applyDefaultValues 覆盖 now()/true/false/字面量/spel/catch 各分支
    @Test
    public void createApplyDefaultValuesAllBranches() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getFields().add(field("created_at"));
        meta.getFields().add(field("updated_at"));
        FieldMeta fNow = field("f_now");
        fNow.setDefaultValueExpr("now()");
        FieldMeta fTrue = field("f_true");
        fTrue.setDefaultValueExpr("true");
        FieldMeta fFalse = field("f_false");
        fFalse.setDefaultValueExpr("false");
        FieldMeta fLit = field("f_lit");
        fLit.setDefaultValueExpr("'abc'");
        FieldMeta fSpel = field("f_spel");
        fSpel.setDefaultValueExpr("1+1");
        FieldMeta fBad = field("f_bad");
        fBad.setDefaultValueExpr("bad");
        meta.getFields().add(fNow);
        meta.getFields().add(fTrue);
        meta.getFields().add(fFalse);
        meta.getFields().add(fLit);
        meta.getFields().add(fSpel);
        meta.getFields().add(fBad);
        meta.getActions().put("create", enabledAction("create"));
        when(helper.insert(anyString(), any(JSONObject.class))).thenReturn(okRes());
        when(spelExecutor.evaluate(eq("1+1"), any(JSONObject.class))).thenReturn(2);
        when(spelExecutor.evaluate(eq("bad"), any(JSONObject.class))).thenThrow(new RuntimeException("parse fail"));

        crudExecutor.execute(meta, "create", ctx("create", new JSONObject()));

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(helper).insert(eq("t_order"), captor.capture());
        JSONObject data = captor.getValue();
        assertNotNull(data.getString("created_at"));
        assertNotNull(data.getString("updated_at"));
        assertNotNull(data.getString("f_now"));
        assertEquals(Boolean.TRUE, data.get("f_true"));
        assertEquals(Boolean.FALSE, data.get("f_false"));
        assertEquals("abc", data.getString("f_lit"));
        assertEquals(2, data.get("f_spel"));
        // f_bad 求值抛异常，未写入
        assertTrue(!data.containsKey("f_bad"));
    }

    // 测试场景: create 时 filterWritableFields 移除无写权限字段
    @Test
    public void createFilterWritableFieldsRemovesField() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        FieldMeta secret = field("secret");
        secret.setPermissionExpr("mode == 'write' && action == 'admin'");
        meta.getFields().add(secret);
        meta.getActions().put("create", enabledAction("create"));
        JSONObject payload = new JSONObject();
        payload.put("secret", "s");
        when(helper.insert(anyString(), any(JSONObject.class))).thenReturn(okRes());
        when(spelExecutor.evaluateBoolean(eq("mode == 'write' && action == 'admin'"), anyMap())).thenReturn(false);

        crudExecutor.execute(meta, "create", ctx("create", payload));

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(helper).insert(eq("t_order"), captor.capture());
        assertTrue(!captor.getValue().containsKey("secret"));
    }

    // ==================== update ====================

    // 测试场景: bug 修复1 —— update 部分更新时缺失必填字段不抛异常
    @Test
    public void updatePartialUpdateSkipsRequiredCheck() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        FieldMeta name = field("name");
        name.setIsRequired(true);
        meta.getFields().add(field("id"));
        meta.getFields().add(name);
        meta.getActions().put("update", enabledAction("update"));
        JSONObject payload = new JSONObject();
        payload.put("id", "123");
        ResMsg r = okRes();
        r.setData(1);
        when(helper.update(anyString(), any(JSONObject.class), any(JSONArray.class))).thenReturn(r);

        Object result = crudExecutor.execute(meta, "update", ctx("update", payload));

        JSONObject json = (JSONObject) result;
        assertEquals("123", json.getString("id"));
        assertEquals(1, json.get("affectedRows"));
        verify(helper).update(eq("t_order"), any(JSONObject.class), any(JSONArray.class));
    }

    // 测试场景: update 缺少 id 抛 IllegalArgumentException
    @Test
    public void updateMissingIdThrows() {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getActions().put("update", enabledAction("update"));
        try {
            crudExecutor.execute(meta, "update", ctx("update", new JSONObject()));
            fail("应抛异常");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("缺少 id"));
        } catch (Exception e) {
            fail("应为 IllegalArgumentException");
        }
    }

    // 测试场景: update 时 validationExpr 不满足抛 BizException
    @Test
    public void updateValidationExprFailsThrows() {
        BizMeta meta = baseMeta("B1", "t_order");
        FieldMeta age = field("age");
        age.setValidationExpr("value >= 0");
        meta.getFields().add(field("id"));
        meta.getFields().add(age);
        meta.getActions().put("update", enabledAction("update"));
        JSONObject payload = new JSONObject();
        payload.put("id", "123");
        payload.put("age", -1);
        when(spelExecutor.evaluateBoolean(eq("value >= 0"), anyMap())).thenReturn(false);
        try {
            crudExecutor.execute(meta, "update", ctx("update", payload));
            fail("应抛异常");
        } catch (BizException e) {
            assertEquals("VALIDATION_FAILED", e.getCode());
        } catch (Exception e) {
            fail("应为 BizException");
        }
    }

    // ==================== delete ====================

    // 测试场景: delete 物理删除（logicDeleteField 为空）
    @Test
    public void deletePhysical() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getActions().put("delete", enabledAction("delete"));
        JSONObject payload = new JSONObject();
        payload.put("id", "123");
        when(helper.delete(anyString(), any(JSONArray.class))).thenReturn(okRes());

        Object result = crudExecutor.execute(meta, "delete", ctx("delete", payload));

        JSONObject json = (JSONObject) result;
        assertEquals("123", json.getString("id"));
        verify(helper).delete(eq("t_order"), any(JSONArray.class));
    }

    // 测试场景: delete 逻辑删除（logicDeleteField 非空，调用 update）
    @Test
    public void deleteLogical() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.setLogicDeleteField("is_deleted");
        meta.getFields().add(field("id"));
        meta.getActions().put("delete", enabledAction("delete"));
        JSONObject payload = new JSONObject();
        payload.put("id", "123");
        when(helper.update(anyString(), any(JSONObject.class), any(JSONArray.class))).thenReturn(okRes());

        Object result = crudExecutor.execute(meta, "delete", ctx("delete", payload));

        JSONObject json = (JSONObject) result;
        assertEquals("123", json.getString("id"));
        verify(helper).update(eq("t_order"), any(JSONObject.class), any(JSONArray.class));
    }

    // 测试场景: delete 缺少 id 抛 IllegalArgumentException
    @Test
    public void deleteMissingIdThrows() {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getActions().put("delete", enabledAction("delete"));
        try {
            crudExecutor.execute(meta, "delete", ctx("delete", new JSONObject()));
            fail("应抛异常");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("缺少 id"));
        } catch (Exception e) {
            fail("应为 IllegalArgumentException");
        }
    }

    // ==================== query ====================

    // 测试场景: query 基本流程，返回数据与 totalCount
    @Test
    public void queryBasic() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.setMainTableAlias("T");
        meta.getFields().add(field("id"));
        meta.getFields().add(field("name"));
        meta.getActions().put("query", enabledAction("query"));
        JSONObject payload = new JSONObject();
        JSONArray rows = new JSONArray();
        rows.add(new JSONObject());
        rows.add(new JSONObject());
        ResMsg r = okRes();
        r.setData(rows);
        when(sqlEngineFacade.query(any(JSONObject.class), anyString())).thenReturn(r);

        Object result = crudExecutor.execute(meta, "query", ctx("query", payload));

        JSONObject json = (JSONObject) result;
        assertEquals(2, json.get("totalCount"));
        assertNotNull(json.getJSONArray("data"));
    }

    // 测试场景: query 带分页参数
    @Test
    public void queryWithPaging() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getActions().put("query", enabledAction("query"));
        JSONObject payload = new JSONObject();
        payload.put("page", 1);
        payload.put("pageSize", 10);
        ResMsg r = okRes();
        r.setData(new JSONArray());
        when(sqlEngineFacade.query(any(JSONObject.class), anyString())).thenReturn(r);

        Object result = crudExecutor.execute(meta, "query", ctx("query", payload));

        JSONObject json = (JSONObject) result;
        assertEquals(0, json.get("totalCount"));
    }

    // 测试场景: query 带 filterData 与 logicDeleteField 条件
    @Test
    public void queryWithFilterDataAndLogicDelete() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.setMainTableAlias("T");
        meta.setLogicDeleteField("is_deleted");
        meta.getFields().add(field("id"));
        meta.getActions().put("query", enabledAction("query"));
        JSONObject payload = new JSONObject();
        payload.put("filterData", "[{\"Id\":\"name\",\"Symbol\":\"=\",\"Val\":\"abc\"}]");
        ResMsg r = okRes();
        r.setData(new JSONArray());
        when(sqlEngineFacade.query(any(JSONObject.class), anyString())).thenReturn(r);

        Object result = crudExecutor.execute(meta, "query", ctx("query", payload));

        JSONObject json = (JSONObject) result;
        assertEquals(0, json.get("totalCount"));
    }

    // 测试场景: query facade 返回失败抛 Exception
    @Test
    public void queryFacadeFailsThrows() {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getActions().put("query", enabledAction("query"));
        when(sqlEngineFacade.query(any(JSONObject.class), anyString())).thenReturn(failRes("query fail"));
        try {
            crudExecutor.execute(meta, "query", ctx("query", new JSONObject()));
            fail("应抛异常");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("query fail"));
        }
    }

    // 测试场景: query 时字段 permissionExpr 求值为 false 被排除可读列
    @Test
    public void queryReadableColumnsFilteredByPermission() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        FieldMeta secret = field("secret");
        secret.setPermissionExpr("mode == 'read' && action == 'admin'");
        meta.getFields().add(secret);
        meta.getActions().put("query", enabledAction("query"));
        when(spelExecutor.evaluateBoolean(eq("mode == 'read' && action == 'admin'"), anyMap())).thenReturn(false);
        ResMsg r = okRes();
        r.setData(new JSONArray());
        when(sqlEngineFacade.query(any(JSONObject.class), anyString())).thenReturn(r);

        Object result = crudExecutor.execute(meta, "query", ctx("query", new JSONObject()));

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(sqlEngineFacade).query(captor.capture(), anyString());
        JSONObject req = captor.getValue();
        JSONArray cols = req.getJSONArray("columns");
        assertNotNull(cols);
        assertTrue(cols.contains("id"));
        assertTrue(!cols.contains("secret"));
        JSONObject json = (JSONObject) result;
        assertEquals(0, json.get("totalCount"));
    }

    // ==================== 状态迁移动作（bug 修复2） ====================

    // 测试场景: bug 修复2 —— submit 状态迁移动作走 doStatusTransition 占位方法
    @Test
    public void executeStatusTransitionActionReturnsTransitionResult() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        ActionMeta submit = enabledAction("submit");
        submit.setStatusTransition("DRAFT->SUBMITTED");
        meta.getActions().put("submit", submit);
        JSONObject payload = new JSONObject();
        payload.put("id", "123");

        Object result = crudExecutor.execute(meta, "submit", ctx("submit", payload));

        JSONObject json = (JSONObject) result;
        assertEquals("123", json.getString("id"));
        assertEquals("submit", json.getString("action"));
        assertEquals("DRAFT->SUBMITTED", json.getString("statusTransition"));
    }

    // 测试场景: 状态迁移动作缺少 id 抛 IllegalArgumentException
    @Test
    public void executeStatusTransitionMissingIdThrows() {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        ActionMeta submit = enabledAction("submit");
        submit.setStatusTransition("DRAFT->SUBMITTED");
        meta.getActions().put("submit", submit);
        try {
            crudExecutor.execute(meta, "submit", ctx("submit", new JSONObject()));
            fail("应抛异常");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("缺少 id"));
        } catch (Exception e) {
            fail("应为 IllegalArgumentException");
        }
    }

    // 测试场景: 未知 action 且无 statusTransition 抛 IllegalArgumentException
    @Test
    public void executeUnknownActionThrows() {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getActions().put("foo", enabledAction("foo"));
        try {
            crudExecutor.execute(meta, "foo", ctx("foo", new JSONObject()));
            fail("应抛异常");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("未知的 action"));
        } catch (Exception e) {
            fail("应为 IllegalArgumentException");
        }
    }

    // ==================== 补充分支覆盖 ====================

    // 测试场景: create 时 payload 已含 id，不重新生成
    @Test
    public void createWithExistingId() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getActions().put("create", enabledAction("create"));
        JSONObject payload = new JSONObject();
        payload.put("id", "fixed-id-123");
        when(helper.insert(anyString(), any(JSONObject.class))).thenReturn(okRes());

        Object result = crudExecutor.execute(meta, "create", ctx("create", payload));

        assertEquals("fixed-id-123", ((JSONObject) result).getString("id"));
    }

    // 测试场景: query 仅传 pageSize（无 page）覆盖 else if 分支
    @Test
    public void queryPageSizeOnly() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getActions().put("query", enabledAction("query"));
        JSONObject payload = new JSONObject();
        payload.put("pageSize", 10);
        ResMsg r = okRes();
        r.setData(new JSONArray());
        when(sqlEngineFacade.query(any(JSONObject.class), anyString())).thenReturn(r);

        Object result = crudExecutor.execute(meta, "query", ctx("query", payload));

        assertEquals(0, ((JSONObject) result).get("totalCount"));
    }

    // 测试场景: query 无表别名覆盖 alias 为空分支
    @Test
    public void queryAliasNull() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getActions().put("query", enabledAction("query"));
        ResMsg r = okRes();
        r.setData(new JSONArray());
        when(sqlEngineFacade.query(any(JSONObject.class), anyString())).thenReturn(r);

        Object result = crudExecutor.execute(meta, "query", ctx("query", new JSONObject()));

        assertEquals(0, ((JSONObject) result).get("totalCount"));
    }

    // 测试场景: query 字段为空时 readableColumns 为空，不输出 columns
    @Test
    public void queryEmptyFields() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getActions().put("query", enabledAction("query"));
        ResMsg r = okRes();
        r.setData(new JSONArray());
        when(sqlEngineFacade.query(any(JSONObject.class), anyString())).thenReturn(r);

        Object result = crudExecutor.execute(meta, "query", ctx("query", new JSONObject()));

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(sqlEngineFacade).query(captor.capture(), anyString());
        assertTrue(!captor.getValue().containsKey("columns"));
        assertEquals(0, ((JSONObject) result).get("totalCount"));
    }

    // 测试场景: query 时字段 permissionExpr 求值为 true，字段加入可读列
    @Test
    public void queryReadableColumnPermissionTrue() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        FieldMeta secret = field("secret");
        secret.setPermissionExpr("mode == 'read'");
        meta.getFields().add(secret);
        meta.getActions().put("query", enabledAction("query"));
        ResMsg r = okRes();
        r.setData(new JSONArray());
        when(sqlEngineFacade.query(any(JSONObject.class), anyString())).thenReturn(r);

        crudExecutor.execute(meta, "query", ctx("query", new JSONObject()));

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(sqlEngineFacade).query(captor.capture(), anyString());
        JSONArray cols = captor.getValue().getJSONArray("columns");
        assertTrue(cols.contains("id"));
        assertTrue(cols.contains("secret"));
    }

    // 测试场景: query 时字段 permissionExpr 求值抛异常，字段被跳过
    @Test
    public void queryReadableColumnPermissionException() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        FieldMeta secret = field("secret");
        secret.setPermissionExpr("badExpr");
        meta.getFields().add(secret);
        meta.getActions().put("query", enabledAction("query"));
        when(spelExecutor.evaluateBoolean(eq("badExpr"), anyMap())).thenThrow(new RuntimeException("eval fail"));
        ResMsg r = okRes();
        r.setData(new JSONArray());
        when(sqlEngineFacade.query(any(JSONObject.class), anyString())).thenReturn(r);

        crudExecutor.execute(meta, "query", ctx("query", new JSONObject()));

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(sqlEngineFacade).query(captor.capture(), anyString());
        JSONArray cols = captor.getValue().getJSONArray("columns");
        assertTrue(cols.contains("id"));
        assertTrue(!cols.contains("secret"));
    }

    // 测试场景: query 时 actionMeta 含 dataScopeExpr，覆盖 buildDataScopeConditions 日志分支
    @Test
    public void queryWithDataScopeExpr() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        ActionMeta query = enabledAction("query");
        query.setDataScopeExpr("dept == ctx.deptId");
        meta.getActions().put("query", query);
        ResMsg r = okRes();
        r.setData(new JSONArray());
        when(sqlEngineFacade.query(any(JSONObject.class), anyString())).thenReturn(r);

        Object result = crudExecutor.execute(meta, "query", ctx("query", new JSONObject()));

        assertEquals(0, ((JSONObject) result).get("totalCount"));
    }

    // 测试场景: create 时 filterWritableFields permissionExpr 求值抛异常，字段保留
    @Test
    public void createFilterWritablePermissionException() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        FieldMeta secret = field("secret");
        secret.setPermissionExpr("badExpr");
        meta.getFields().add(secret);
        meta.getActions().put("create", enabledAction("create"));
        JSONObject payload = new JSONObject();
        payload.put("secret", "s");
        when(helper.insert(anyString(), any(JSONObject.class))).thenReturn(okRes());
        when(spelExecutor.evaluateBoolean(eq("badExpr"), anyMap())).thenThrow(new RuntimeException("eval fail"));

        crudExecutor.execute(meta, "create", ctx("create", payload));

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(helper).insert(eq("t_order"), captor.capture());
        assertTrue(captor.getValue().containsKey("secret"));
    }

    // 测试场景: create 时字段有 permissionExpr 但 data 不含该字段，跳过过滤
    @Test
    public void createFilterWritableFieldNotInData() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        FieldMeta secret = field("secret");
        secret.setPermissionExpr("mode == 'write'");
        meta.getFields().add(secret);
        meta.getActions().put("create", enabledAction("create"));
        when(helper.insert(anyString(), any(JSONObject.class))).thenReturn(okRes());

        crudExecutor.execute(meta, "create", ctx("create", new JSONObject()));

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(helper).insert(eq("t_order"), captor.capture());
        assertTrue(!captor.getValue().containsKey("secret"));
    }

    // 测试场景: update 时 updated_at 已有值，applyDefaultValues 不覆盖
    @Test
    public void updateWithUpdatedAtExisting() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getFields().add(field("updated_at"));
        meta.getActions().put("update", enabledAction("update"));
        JSONObject payload = new JSONObject();
        payload.put("id", "123");
        payload.put("updated_at", "existing-ts");
        ResMsg r = okRes();
        r.setData(1);
        when(helper.update(anyString(), any(JSONObject.class), any(JSONArray.class))).thenReturn(r);

        crudExecutor.execute(meta, "update", ctx("update", payload));

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(helper).update(eq("t_order"), captor.capture(), any(JSONArray.class));
        assertEquals("existing-ts", captor.getValue().getString("updated_at"));
    }

    // 测试场景: update 返回 affectedRows 为 null，result 中 affectedRows 为 null
    @Test
    public void updateAffectedRowsNull() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getActions().put("update", enabledAction("update"));
        JSONObject payload = new JSONObject();
        payload.put("id", "123");
        when(helper.update(anyString(), any(JSONObject.class), any(JSONArray.class))).thenReturn(okRes());

        Object result = crudExecutor.execute(meta, "update", ctx("update", payload));

        JSONObject json = (JSONObject) result;
        assertEquals("123", json.getString("id"));
        assertTrue(!json.containsKey("affectedRows") || json.get("affectedRows") == null);
    }

    // 测试场景: create 时 payload 已含 created_at，applyDefaultValues 不覆盖
    @Test
    public void createWithCreatedAtExisting() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getFields().add(field("created_at"));
        meta.getActions().put("create", enabledAction("create"));
        JSONObject payload = new JSONObject();
        payload.put("created_at", "existing-ts");
        when(helper.insert(anyString(), any(JSONObject.class))).thenReturn(okRes());

        crudExecutor.execute(meta, "create", ctx("create", payload));

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(helper).insert(eq("t_order"), captor.capture());
        assertEquals("existing-ts", captor.getValue().getString("created_at"));
    }

    // 测试场景: create 时 ctx.context 非空，filterWritableFields 走 vars.putAll 分支
    @Test
    public void createWithContextForWritableFilter() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        FieldMeta secret = field("secret");
        secret.setPermissionExpr("mode == 'write'");
        meta.getFields().add(secret);
        meta.getActions().put("create", enabledAction("create"));
        JSONObject payload = new JSONObject();
        payload.put("secret", "s");
        ActionContext c = ctx("create", payload);
        JSONObject context = new JSONObject();
        context.put("role", "admin");
        c.setContext(context);
        when(helper.insert(anyString(), any(JSONObject.class))).thenReturn(okRes());

        crudExecutor.execute(meta, "create", c);

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(helper).insert(eq("t_order"), captor.capture());
        assertTrue(captor.getValue().containsKey("secret"));
    }

    // 测试场景: query 时 ctx.context 非空，getReadableColumns 走 vars.putAll 分支
    @Test
    public void queryWithContextForReadableColumns() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        FieldMeta secret = field("secret");
        secret.setPermissionExpr("mode == 'read'");
        meta.getFields().add(secret);
        meta.getActions().put("query", enabledAction("query"));
        ActionContext c = ctx("query", new JSONObject());
        JSONObject context = new JSONObject();
        context.put("role", "user");
        c.setContext(context);
        ResMsg r = okRes();
        r.setData(new JSONArray());
        when(sqlEngineFacade.query(any(JSONObject.class), anyString())).thenReturn(r);

        crudExecutor.execute(meta, "query", c);

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(sqlEngineFacade).query(captor.capture(), anyString());
        JSONArray cols = captor.getValue().getJSONArray("columns");
        assertTrue(cols.contains("id"));
        assertTrue(cols.contains("secret"));
    }

    // 测试场景: delete 时 logicDeleteField 为空字符串，走物理删除
    @Test
    public void deleteLogicDeleteFieldEmptyString() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.setLogicDeleteField("");
        meta.getFields().add(field("id"));
        meta.getActions().put("delete", enabledAction("delete"));
        JSONObject payload = new JSONObject();
        payload.put("id", "123");
        when(helper.delete(anyString(), any(JSONArray.class))).thenReturn(okRes());

        Object result = crudExecutor.execute(meta, "delete", ctx("delete", payload));

        assertEquals("123", ((JSONObject) result).getString("id"));
        verify(helper).delete(eq("t_order"), any(JSONArray.class));
    }

    // 测试场景: update 时 updated_at 字段无值，applyDefaultValues 自动填充
    @Test
    public void updateWithUpdatedAtNull() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getFields().add(field("updated_at"));
        meta.getActions().put("update", enabledAction("update"));
        JSONObject payload = new JSONObject();
        payload.put("id", "123");
        ResMsg r = okRes();
        r.setData(1);
        when(helper.update(anyString(), any(JSONObject.class), any(JSONArray.class))).thenReturn(r);

        crudExecutor.execute(meta, "update", ctx("update", payload));

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(helper).update(eq("t_order"), captor.capture(), any(JSONArray.class));
        assertNotNull(captor.getValue().getString("updated_at"));
    }

    // 测试场景: query 仅传 page（无 pageSize），不输出分页
    @Test
    public void queryPageOnlyNoPageSize() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getActions().put("query", enabledAction("query"));
        JSONObject payload = new JSONObject();
        payload.put("page", 1);
        ResMsg r = okRes();
        r.setData(new JSONArray());
        when(sqlEngineFacade.query(any(JSONObject.class), anyString())).thenReturn(r);

        Object result = crudExecutor.execute(meta, "query", ctx("query", payload));

        assertEquals(0, ((JSONObject) result).get("totalCount"));
    }

    // 测试场景: create 时字段有 validationExpr 但 data 无该字段值，跳过校验
    @Test
    public void createValidationExprFieldNoData() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        FieldMeta age = field("age");
        age.setValidationExpr("value >= 0");
        meta.getFields().add(age);
        meta.getActions().put("create", enabledAction("create"));
        when(helper.insert(anyString(), any(JSONObject.class))).thenReturn(okRes());

        Object result = crudExecutor.execute(meta, "create", ctx("create", new JSONObject()));

        assertNotNull(((JSONObject) result).getString("id"));
    }

    // 测试场景: create 时 defaultValueExpr 走 spel 求值返回 null，不写入
    @Test
    public void createDefaultValueSpelNullResult() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        FieldMeta fSpel = field("f_spel");
        fSpel.setDefaultValueExpr("1+1");
        meta.getFields().add(fSpel);
        meta.getActions().put("create", enabledAction("create"));
        when(helper.insert(anyString(), any(JSONObject.class))).thenReturn(okRes());
        when(spelExecutor.evaluate(eq("1+1"), any(JSONObject.class))).thenReturn(null);

        crudExecutor.execute(meta, "create", ctx("create", new JSONObject()));

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(helper).insert(eq("t_order"), captor.capture());
        assertTrue(!captor.getValue().containsKey("f_spel"));
    }

    // 测试场景: query 时 actionMeta dataScopeExpr 为空字符串
    @Test
    public void queryWithDataScopeExprEmpty() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        ActionMeta query = enabledAction("query");
        query.setDataScopeExpr("");
        meta.getActions().put("query", query);
        ResMsg r = okRes();
        r.setData(new JSONArray());
        when(sqlEngineFacade.query(any(JSONObject.class), anyString())).thenReturn(r);

        Object result = crudExecutor.execute(meta, "query", ctx("query", new JSONObject()));

        assertEquals(0, ((JSONObject) result).get("totalCount"));
    }

    // 测试场景: actionMeta enabled 为 null 时视为启用
    @Test
    public void executeActionWithEnabledNull() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        ActionMeta a = new ActionMeta();
        a.setAction("create");
        // enabled 不设置，为 null
        meta.getActions().put("create", a);
        when(helper.insert(anyString(), any(JSONObject.class))).thenReturn(okRes());

        Object result = crudExecutor.execute(meta, "create", ctx("create", new JSONObject()));

        assertNotNull(((JSONObject) result).getString("id"));
    }

    // 测试场景: update 时 id 为空字符串抛 IllegalArgumentException
    @Test
    public void updateWithEmptyIdThrows() {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getActions().put("update", enabledAction("update"));
        JSONObject payload = new JSONObject();
        payload.put("id", "");
        try {
            crudExecutor.execute(meta, "update", ctx("update", payload));
            fail("应抛异常");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("缺少 id"));
        } catch (Exception e) {
            fail("应为 IllegalArgumentException");
        }
    }

    // 测试场景: delete 时 id 为空字符串抛 IllegalArgumentException
    @Test
    public void deleteWithEmptyIdThrows() {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        meta.getActions().put("delete", enabledAction("delete"));
        JSONObject payload = new JSONObject();
        payload.put("id", "");
        try {
            crudExecutor.execute(meta, "delete", ctx("delete", payload));
            fail("应抛异常");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("缺少 id"));
        } catch (Exception e) {
            fail("应为 IllegalArgumentException");
        }
    }

    // 测试场景: 状态迁移动作 id 为空字符串抛 IllegalArgumentException
    @Test
    public void statusTransitionWithEmptyIdThrows() {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        ActionMeta submit = enabledAction("submit");
        submit.setStatusTransition("DRAFT->SUBMITTED");
        meta.getActions().put("submit", submit);
        JSONObject payload = new JSONObject();
        payload.put("id", "");
        try {
            crudExecutor.execute(meta, "submit", ctx("submit", payload));
            fail("应抛异常");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("缺少 id"));
        } catch (Exception e) {
            fail("应为 IllegalArgumentException");
        }
    }

    // 测试场景: create 时字段 permissionExpr 为空字符串，跳过过滤保留字段
    @Test
    public void createFilterWritablePermissionExprEmpty() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        FieldMeta secret = field("secret");
        secret.setPermissionExpr("");
        meta.getFields().add(secret);
        meta.getActions().put("create", enabledAction("create"));
        JSONObject payload = new JSONObject();
        payload.put("secret", "s");
        when(helper.insert(anyString(), any(JSONObject.class))).thenReturn(okRes());

        crudExecutor.execute(meta, "create", ctx("create", payload));

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(helper).insert(eq("t_order"), captor.capture());
        assertTrue(captor.getValue().containsKey("secret"));
    }

    // 测试场景: query 时字段 permissionExpr 为空字符串，字段直接加入可读列
    @Test
    public void queryReadablePermissionExprEmpty() throws Exception {
        BizMeta meta = baseMeta("B1", "t_order");
        meta.getFields().add(field("id"));
        FieldMeta secret = field("secret");
        secret.setPermissionExpr("");
        meta.getFields().add(secret);
        meta.getActions().put("query", enabledAction("query"));
        ResMsg r = okRes();
        r.setData(new JSONArray());
        when(sqlEngineFacade.query(any(JSONObject.class), anyString())).thenReturn(r);

        crudExecutor.execute(meta, "query", ctx("query", new JSONObject()));

        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(sqlEngineFacade).query(captor.capture(), anyString());
        JSONArray cols = captor.getValue().getJSONArray("columns");
        assertTrue(cols.contains("id"));
        assertTrue(cols.contains("secret"));
    }
}