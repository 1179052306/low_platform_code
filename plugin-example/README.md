# 低代码平台插件示例

## 这是什么

一个可直接编译、上传、运行的后端插件示例，展示如何用**一个类**处理一个业务的所有操作（保存前、保存后、自定义按钮等）。

## 插件机制原理

```
前端提交 → 网关 GenericGatewayController → 事务开始
  → invokePreHooks(bizCode, action, ctx)     ← 保存前钩子（可修改数据、校验、中断）
  → CrudExecutor.execute()                   ← CRUD 执行（INSERT/UPDATE/DELETE）
  → StateMachine.transitionIfNeed()           ← 状态机
  → invokePostHooks(bizCode, action, ctx)    ← 保存后钩子（可联动写其他表）
  → 事务提交
```

## 最简写法（BizPlugin 基类）

**继承 `BizPlugin`，在 `register()` 中用 `before()`/`after()`/`action()` 注册各 handler，只写业务方法：**

```java
public class ItemPlugin extends BizPlugin {

    public ItemPlugin() {
        super("item-plugin", "B_ITEM");    // pluginId, bizCode
    }

    @Override
    protected void register() {
        before("create", "update", this::beforeSave);    // 保存前（create + update）
        after("create", this::afterCreate);              // 保存后（仅 create）
        action("approve", this::onApprove);              // 自定义按钮"审批"
    }

    private void beforeSave(ActionContext ac) throws Exception {
        JSONObject data = ac.getPayload();
        // 校验
        if (data.getString("item_name") == null) {
            throw new IllegalArgumentException("物料名称不能为空");
        }
        // 修改数据（会写入数据库）
        data.put("updated_by", ac.getUserId());
        data.put("updated_time", System.currentTimeMillis());
    }

    private void afterCreate(ActionContext ac) throws Exception {
        JSONObject data = ac.getPayload();
        // 联动写其他表
        JSONObject stock = new JSONObject();
        stock.put("item_id", data.getString("id"));
        stock.put("quantity", 0);
        ctx.insert("B_STOCK_INIT", stock);
    }

    private void onApprove(ActionContext ac) throws Exception {
        JSONObject data = ac.getPayload();
        // 自定义按钮逻辑
        JSONObject update = new JSONObject();
        update.put("status", "APPROVED");
        JSONObject cond = new JSONObject();
        cond.put("id", data.getString("id"));
        ctx.update("B_ITEM", update, cond);
    }
}
```

**就这些。不用写 init/destroy/pluginId/version/phase/support，全部由基类处理。**

## 操作步骤

### 1. 编译打包

```bash
cd D:\code\plugin-example
mvn clean package
```

产物：`target/item-plugin-1.0.0.jar`（注意 finalName 跟 pluginId 一致）

### 2. 上传到平台

```bash
curl -X POST http://localhost:3015/api/lowcode/plugin/upload \
  -F "file=@target/item-plugin-1.0.0.jar"
```

### 3. 测试

前端新增/修改物料时自动触发 `beforeSave` → CRUD → `afterCreate`/`afterUpdate`。
前端点"审批"按钮（action=approve）时触发 `onApprove`。

### 4. 卸载

```bash
curl -X POST http://localhost:3015/api/lowcode/plugin/unload \
  -H "Content-Type: application/json" \
  -d '{"pluginId":"item-plugin"}'
```

## API 速查

### register() 中可用的注册方法

| 方法 | 阶段 | 说明 |
|------|------|------|
| `before(action, handler)` | PRE | 保存前（CRUD 之前），可修改 payload、校验、抛异常中断 |
| `before(actions[], handler)` | PRE | 匹配多个 action |
| `after(action, handler)` | POST | 保存后（CRUD 之后），可联动写其他表 |
| `after(actions[], handler)` | POST | 匹配多个 action |
| `action(action, handler)` | POST | 自定义按钮 action（CRUD 之后触发） |

### ActionContext 能拿到什么

| 方法 | 类型 | 说明 |
|------|------|------|
| `getBizCode()` | String | 业务编码，如 `B_ITEM` |
| `getAction()` | String | 操作类型，如 `create`/`update`/`approve` |
| `getPayload()` | JSONObject | 前端提交的表单数据，**可修改** |
| `getUserId()` | String | 当前用户 ID |
| `getResult()` | Object | POST 阶段有 CRUD 返回值，PRE 阶段为 null |

### PluginContext（ctx）能调用什么

| 方法 | 说明 |
|------|------|
| `ctx.insert(bizCode, data)` | 插入数据 |
| `ctx.update(bizCode, data, conditions)` | 更新数据 |
| `ctx.queryById(bizCode, id)` | 按 ID 查询 |
| `ctx.invokeAction(bizCode, action, payload)` | 调用另一个 action |
| `ctx.getConfig(key)` | 读配置 |
| `ctx.logger()` | 插件日志 |
| `ctx.sendAsync(topic, payload)` | 发异步消息 ⚠️ 当前空壳未实现 |

## 对比：三种写法

### 写法1：BizPlugin（推荐 —— 一个类处理所有操作）

```java
public class ItemPlugin extends BizPlugin {
    public ItemPlugin() { super("item-plugin", "B_ITEM"); }

    @Override
    protected void register() {
        before("create", "update", this::beforeSave);
        after("create", this::afterCreate);
        action("approve", this::onApprove);
    }

    private void beforeSave(ActionContext ac) { ... }
    private void afterCreate(ActionContext ac) { ... }
    private void onApprove(ActionContext ac) { ... }
}
```

### 写法2：PreHook / PostHook（简单场景 —— 一个类一个阶段）

```java
public class ItemSavePlugin extends PreHook {
    public ItemSavePlugin() { super("item-save", "B_ITEM", "create", "update"); }
    @Override
    public void invoke(ActionContext ac) { ... }
}
```

### 写法3：直接实现 ActionHook（最底层 —— 需要写全部 7 个方法）

```java
public class MyHook implements ActionHook {
    public void init(PluginContext ctx) { ... }
    public void destroy() {}
    public String pluginId() { return "my-hook"; }
    public String version() { return "1.0.0"; }
    public HookPhase phase() { return HookPhase.PRE; }
    public boolean support(String bizCode, String action) { ... }
    public void invoke(ActionContext ac) { ... }
}
```

## 注意事项

1. **事务**：所有钩子在同一事务内，抛异常回滚全部
2. **自定义 action**：需在 `lc_action_meta` 表注册才能通过权限校验
3. **幂等**：POST 钩子写其他表时注意幂等
4. **递归**：`invokeAction` 会再次触发钩子，注意避免无限递归
5. **sendAsync 未实现**：当前 `ctx.sendAsync()` 是空壳