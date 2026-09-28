# 状态变更触发动作 + 审批流设计文档

> 版本：v1.0  
> 日期：2026-09-22  
> 模块：bcd-code（端口 3015）  
> 依赖：MetaEngineService（元数据驱动 API）、RocketMQ、L1CaffeineCache、BatchFieldTransformer

---

## 一、背景与目标

### 1.1 需求

单据状态变更时（如下达：`draft → confirmed`），通过前端配置触发一组动作：

| 动作类型                | 场景                                 | 执行方式         |
| ----------------------- | ------------------------------------ | ---------------- |
| `docConvert` 单据转换   | 入库单 → 出库单，字段映射生成新单据  | 同步             |
| `httpCall` 调用外部接口 | 通知 WCS/ERP 系统                    | 异步（RocketMQ） |
| `approval` 触发审批     | 单据进入审批流，审批通过自动流转状态 | 同步             |
| `custom` 自定义代码     | 用户自己写的 Java handler            | 同步/异步可选    |
| `mqSend` 发消息         | 通知下游微服务                       | 异步             |
| `updateField` 联动更新  | 状态变更时同步更新其他字段           | 同步             |
| `notify` 通知           | 站内信/邮件                          | 异步             |

### 1.2 确认的约束

1. 审批流：**从零开始**（无现有审批系统）
2. 单据转换字段映射：**复用 BatchFieldTransformer**（含 code→id 转换）
3. custom 动作安全：**先无需限制**（可调任意 Spring Bean）

### 1.3 目标

- 状态变更触发动作全配置化，前端配置存元数据
- 动作类型可扩展（实现 ActionExecutor 接口即可）
- 审批流从零实现，MVP 支持线性审批（按节点顺序逐个审批）
- 同步动作事务内执行，异步动作走 RocketMQ
- 复用现有 MetaEngineService、BatchFieldTransformer、三级缓存、RocketMQ

---

## 二、整体架构

```
外部调用 POST /api/open/** （update 操作，含 status 字段变更）
  ↓
MetaEngineService.doUpdate
  ↓ ① 查变更前数据 oldData
  ↓ ② 执行 facade.update（主操作）
  ↓ ③ StatusChangeDetector 检测 status 字段变更
  ↓ ④ 查 lowcode_status_trigger（触发配置，走 L1CaffeineCache）
  ↓
TriggerDispatcher 分发
  ↓ 按 action.execMode 分流
  │
  ├── sync（同步，主操作后立即执行）
  │   ├── DocConvertActionExecutor    → 复用 MetaEngineService.insert + BatchFieldTransformer
  │   ├── ApprovalActionExecutor      → 创建审批实例，单据状态改"审批中"
  │   ├── UpdateFieldActionExecutor   → 联动更新字段
  │   └── CustomActionExecutor        → ApplicationContext.getBean + 反射调方法
  │   失败 → failStrategy: rollback(回退状态) / ignore(忽略)
  │
  └── async（异步，发 RocketMQ）
      topic: lowcode-trigger-event
      消费者 TriggerEventConsumer 收到后执行：
      ├── HttpCallActionExecutor      → RestTemplate HTTP 调用
      ├── MqSendActionExecutor        → RocketMQ 发送
      ├── NotifyActionExecutor        → 通知
      └── CustomActionExecutor        → 自定义异步逻辑
      失败 → RocketMQ 重试(最多3次) → 死信队列
```

### 审批流闭环

```
状态变更 confirmed → 触发 approval 动作
  ↓
ApprovalActionExecutor 创建审批实例，单据状态 → "approving"
  ↓
审批人操作 POST /api/lowcode/approval/approve
  ↓
全部节点通过 → 单据状态 → onApprove(如 "approved") → 可能再触发新动作
任一节点拒绝 → 单据状态 → onReject(如 "draft")  → 可能再触发新动作
```

---

## 三、数据库设计（DDL）

追加到 `lowcode_tables.sql`：

### 3.1 状态触发配置表

```sql
CREATE TABLE IF NOT EXISTS lowcode_status_trigger (
    trigger_id      VARCHAR(64) PRIMARY KEY,
    model_code      VARCHAR(128) NOT NULL,
    trigger_name    VARCHAR(255),
    from_status     VARCHAR(64),
    to_status       VARCHAR(64),
    status_field    VARCHAR(128) DEFAULT 'status',
    actions_json    TEXT,
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      VARCHAR(64),
    updated_at      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_trigger_model_status
    ON lowcode_status_trigger(model_code, from_status, to_status);
```

`actions_json` 结构：

```json
[
  {
    "actionType": "docConvert",
    "name": "生成出库单",
    "execMode": "sync",
    "failStrategy": "rollback",
    "config": {
      "targetModel": "wms_outbound_order",
      "fieldMapping": {
        "src_order_no": "order_no",
        "warehouse_id": "warehouse_id"
      },
      "statusInit": "draft",
      "copyDetails": true,
      "detailSourceTable": "wms_inbound_order_detail",
      "detailTargetTable": "wms_outbound_order_detail",
      "detailMapping": { "material_id": "material_id", "qty": "qty" }
    }
  },
  {
    "actionType": "approval",
    "name": "提交审批",
    "execMode": "sync",
    "failStrategy": "rollback",
    "config": {
      "flowCode": "inbound_approval",
      "onApprove": "approved",
      "onReject": "draft"
    }
  },
  {
    "actionType": "httpCall",
    "name": "通知WCS",
    "execMode": "async",
    "failStrategy": "ignore",
    "config": {
      "url": "http://wcs.example.com/api/notify",
      "method": "POST",
      "headers": { "Content-Type": "application/json" },
      "bodyTemplate": "{\"orderNo\":\"${order_no}\",\"status\":\"${status}\"}"
    }
  },
  {
    "actionType": "custom",
    "name": "扣减库存",
    "execMode": "sync",
    "failStrategy": "rollback",
    "config": { "beanName": "inboundOrderHandler", "method": "onConfirm" }
  }
]
```

### 3.2 审批流表（从零设计，4 张表）

```sql
-- 审批流定义
CREATE TABLE IF NOT EXISTS lowcode_approval_flow (
    flow_code       VARCHAR(64) PRIMARY KEY,
    flow_name       VARCHAR(255),
    model_code      VARCHAR(128),
    enabled         BOOLEAN DEFAULT TRUE,
    created_at      VARCHAR(64)
);

-- 审批节点（线性顺序，MVP 不做会签/分支）
CREATE TABLE IF NOT EXISTS lowcode_approval_node (
    node_id         VARCHAR(64) PRIMARY KEY,
    flow_code       VARCHAR(64) NOT NULL,
    node_name       VARCHAR(255),
    node_order      INT NOT NULL,
    approver_type   VARCHAR(32),
    approver_value  VARCHAR(512),
    created_at      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_approval_node_flow ON lowcode_approval_node(flow_code, node_order);

-- 审批实例（单据提交审批时创建）
CREATE TABLE IF NOT EXISTS lowcode_approval_instance (
    instance_id     VARCHAR(64) PRIMARY KEY,
    flow_code       VARCHAR(64) NOT NULL,
    model_code      VARCHAR(128) NOT NULL,
    biz_id          VARCHAR(64) NOT NULL,
    current_node_id VARCHAR(64),
    status          VARCHAR(32) DEFAULT 'pending',
    created_at      VARCHAR(64),
    updated_at      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_approval_instance_biz ON lowcode_approval_instance(model_code, biz_id);

-- 审批记录（审批历史）
CREATE TABLE IF NOT EXISTS lowcode_approval_record (
    record_id       VARCHAR(64) PRIMARY KEY,
    instance_id     VARCHAR(64) NOT NULL,
    node_id         VARCHAR(64) NOT NULL,
    approver        VARCHAR(64) NOT NULL,
    action          VARCHAR(32) NOT NULL,
    comment         TEXT,
    created_at      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_approval_record_instance ON lowcode_approval_record(instance_id);
```

`approver_type` 取值：

- `user`：指定用户，`approver_value` = 用户ID（逗号分隔多个）
- `role`：指定角色，`approver_value` = 角色码，运行时查该角色下的用户
- `dynamic`：动态指定，`approver_value` = Spring Bean 名称，运行时调 bean 解析审批人

### 3.3 触发日志表（审计）

```sql
CREATE TABLE IF NOT EXISTS lowcode_trigger_log (
    log_id          VARCHAR(64) PRIMARY KEY,
    trigger_id      VARCHAR(64),
    model_code      VARCHAR(128),
    biz_id          VARCHAR(64),
    from_status     VARCHAR(64),
    to_status       VARCHAR(64),
    action_type     VARCHAR(32),
    action_name     VARCHAR(255),
    exec_mode       VARCHAR(32),
    status          VARCHAR(32),
    error_msg       TEXT,
    cost_ms         INT,
    created_at      VARCHAR(64)
);
CREATE INDEX IF NOT EXISTS idx_trigger_log_biz ON lowcode_trigger_log(model_code, biz_id);
```

---

## 四、核心类设计

新增文件均在 `bcd-code/src/main/java/com/api/lowcode/`：

```
trigger/
├── TriggerDispatcher.java          -- 触发分发器
├── StatusChangeDetector.java       -- 状态变更检测（嵌入 MetaEngineService）
├── TriggerConfigService.java       -- 触发配置 CRUD + 缓存
├── TriggerEventConsumer.java       -- RocketMQ 异步动作消费者
├── TriggerLogService.java          -- 触发日志
└── action/
    ├── ActionExecutor.java             -- 动作执行器接口
    ├── ActionContext.java              -- 动作上下文
    ├── DocConvertActionExecutor.java   -- 单据转换
    ├── HttpCallActionExecutor.java     -- 调用外部接口
    ├── ApprovalActionExecutor.java     -- 触发审批
    ├── CustomActionExecutor.java       -- 自定义代码
    ├── MqSendActionExecutor.java       -- 发消息
    ├── UpdateFieldActionExecutor.java  -- 联动更新字段
    └── NotifyActionExecutor.java       -- 通知
approval/
├── ApprovalController.java         -- 审批接口
├── ApprovalService.java            -- 审批核心逻辑
├── ApprovalFlowService.java        -- 审批流定义 CRUD
└── ApproverResolver.java           -- 审批人解析（user/role/dynamic）
```

### 4.1 ActionExecutor 接口（可扩展）

```java
public interface ActionExecutor {
    String getType();
    Object execute(JSONObject config, ActionContext ctx) throws Exception;
}

public class ActionContext {
    private String modelCode;
    private JSONObject oldData;
    private JSONObject newData;
    private String fromStatus;
    private String toStatus;
    private String dbName;
    private String bizId;
    private List<String> triggerChain;  // 触发链，防环
    private Map<String, Object> variables;  // 动作间传递变量
}
```

**新增动作类型只需实现接口 + 注册为 @Component**，TriggerDispatcher 自动发现。

### 4.2 TriggerDispatcher

```java
@Service
public class TriggerDispatcher {
    @Resource private Map<String, ActionExecutor> executors;  // Spring 自动注入所有实现
    @Resource private TriggerLogService logService;
    @Resource private RocketMQTemplate mqTemplate;  // 项目已有

    public void dispatch(JSONObject trigger, ActionContext ctx) {
        JSONArray actions = JSON.parseArray(trigger.getString("actionsJson"));
        for (int i = 0; i < actions.size(); i++) {
            JSONObject action = actions.getJSONObject(i);
            String type = action.getString("actionType");
            String execMode = action.getString("execMode");
            ActionExecutor executor = executors.get(type);

            if ("async".equals(execMode)) {
                sendAsync(action, ctx);  // 发 RocketMQ
            } else {
                execSync(action, executor, ctx);  // 同步执行
            }
        }
    }

    private void execSync(JSONObject action, ActionExecutor executor, ActionContext ctx) {
        long start = System.currentTimeMillis();
        try {
            executor.execute(action.getJSONObject("config"), ctx);
            logService.success(action, ctx, System.currentTimeMillis() - start);
        } catch (Exception e) {
            logService.fail(action, ctx, e, System.currentTimeMillis() - start);
            String failStrategy = action.getString("failStrategy");
            if ("rollback".equals(failStrategy)) {
                // 回退单据状态到 fromStatus
                rollbackStatus(ctx);
                throw new RuntimeException("动作[" + action.getString("name") + "]失败，已回滚", e);
            }
            // ignore = 继续执行下一个动作
        }
    }

    private void sendAsync(JSONObject action, ActionContext ctx) {
        Message<JSONObject> msg = MessageBuilder.withPayload(action).build();
        msg.getPayload().put("_ctx", JSON.toJSONString(ctx));
        mqTemplate.asyncSend("lowcode-trigger-event", msg, ...);
    }
}
```

### 4.3 StatusChangeDetector（嵌入 MetaEngineService）

```java
@Service
public class StatusChangeDetector {
    @Resource private TriggerConfigService triggerConfigService;
    @Resource private TriggerDispatcher dispatcher;
    @Resource private SqlEngineFacade facade;

    public void detectAndFire(String modelCode, String tableName, String statusField,
                              JSONArray conditions, JSONObject newData, String dbName,
                              List<String> triggerChain) throws Exception {
        // 1. 查变更前数据
        JSONObject oldData = queryOldData(tableName, conditions, dbName);
        if (oldData == null) return;

        // 2. 比较状态字段
        String oldStatus = oldData.getString(statusField);
        String newStatus = newData.getString(statusField);
        if (oldStatus == null || oldStatus.equals(newStatus)) return;

        // 3. 防环检测
        String triggerKey = modelCode + ":" + oldStatus + "->" + newStatus;
        if (triggerChain.contains(triggerKey)) {
            log.warn("检测到触发环，中止: {}", triggerKey);
            return;
        }
        triggerChain.add(triggerKey);

        // 4. 查触发配置（走缓存）
        JSONArray triggers = triggerConfigService.findByStatus(modelCode, oldStatus, newStatus);
        if (triggers == null || triggers.isEmpty()) return;

        // 5. 构建上下文，分发
        for (int i = 0; i < triggers.size(); i++) {
            JSONObject trigger = triggers.getJSONObject(i);
            ActionContext ctx = new ActionContext();
            ctx.setModelCode(modelCode);
            ctx.setOldData(oldData);
            ctx.setNewData(newData);
            ctx.setFromStatus(oldStatus);
            ctx.setToStatus(newStatus);
            ctx.setDbName(dbName);
            ctx.setBizId(extractBizId(oldData));
            ctx.setTriggerChain(triggerChain);
            dispatcher.dispatch(trigger, ctx);
        }
    }
}
```

`MetaEngineService.doUpdate` 改造：

```java
private int doUpdate(...) throws Exception {
    // ... 原有校验和转换 ...
    int result = facade.update(req, dbName);
    // 状态变更检测
    statusChangeDetector.detectAndFire(
        apiMeta.getString("modelCode"), tableName, statusField,
        conditions, data, dbName, new ArrayList<>()
    );
    return result;
}
```

### 4.4 各 ActionExecutor 实现

#### DocConvertActionExecutor（单据转换，复用 BatchFieldTransformer）

```java
@Component
public class DocConvertActionExecutor implements ActionExecutor {
    @Resource private MetaCacheService metaCache;
    @Resource private BatchFieldTransformer batchTransformer;
    @Resource private SqlEngineFacade facade;

    @Override
    public String getType() { return "docConvert"; }

    @Override
    public Object execute(JSONObject config, ActionContext ctx) throws Exception {
        String targetModelCode = config.getString("targetModel");
        JSONObject targetModel = metaCache.getModel(targetModelCode);
        String targetTable = targetModel.getString("tableName");
        String targetDbName = targetModel.getString("dbName");

        // 1. 字段映射：源单据字段 → 目标单据字段
        JSONObject fieldMapping = config.getJSONObject("fieldMapping");
        JSONObject targetData = new JSONObject();
        for (String srcField : fieldMapping.keySet()) {
            targetData.put(fieldMapping.getString(srcField), ctx.getNewData().get(srcField));
        }
        // 设置初始状态
        targetData.put("status", config.getString("statusInit"));

        // 2. code→id 转换（复用 BatchFieldTransformer）
        JSONArray fields = JSON.parseArray(targetModel.getString("fieldsJson"));
        JSONObject paramMapping = buildParamMapping(targetModel);
        JSONArray rows = new JSONArray();
        rows.add(targetData);
        batchTransformer.transformBatch(rows, paramMapping, targetDbName);

        // 3. 插入目标单据
        JSONObject req = new JSONObject();
        req.put("tableName", targetTable);
        req.put("data", rows.getJSONObject(0));
        facade.insert(req, targetDbName);

        // 4. 复制明细（如果配置了）
        if (config.getBooleanValue("copyDetails")) {
            copyDetails(config, ctx, targetDbName);
        }
        return null;
    }
}
```

#### ApprovalActionExecutor（触发审批）

```java
@Component
public class ApprovalActionExecutor implements ActionExecutor {
    @Resource private ApprovalService approvalService;

    @Override
    public String getType() { return "approval"; }

    @Override
    public Object execute(JSONObject config, ActionContext ctx) throws Exception {
        String flowCode = config.getString("flowCode");
        String onApprove = config.getString("onApprove");
        String onReject = config.getString("onReject");
        // 创建审批实例，单据状态改为 "approving"
        approvalService.createInstance(flowCode, ctx.getModelCode(),
            ctx.getBizId(), onApprove, onReject, ctx.getDbName());
        return null;
    }
}
```

#### CustomActionExecutor（自定义代码，反射调 Spring Bean）

```java
@Component
public class CustomActionExecutor implements ActionExecutor {
    @Resource private ApplicationContext applicationContext;

    @Override
    public String getType() { return "custom"; }

    @Override
    public Object execute(JSONObject config, ActionContext ctx) throws Exception {
        String beanName = config.getString("beanName");
        String method = config.getString("method");
        Object bean = applicationContext.getBean(beanName);
        Method m = bean.getClass().getMethod(method, ActionContext.class);
        return m.invoke(bean, ctx);
    }
}
```

用户自己写的 handler 示例：

```java
@Component("inboundOrderHandler")
public class InboundOrderHandler {
    @Resource private SqlEngineFacade facade;

    public void onConfirm(ActionContext ctx) throws Exception {
        JSONObject newData = ctx.getNewData();
        // 自定义逻辑：扣减库存、写日志等
    }
}
```

#### HttpCallActionExecutor（调用外部接口）

```java
@Component
public class HttpCallActionExecutor implements ActionExecutor {
    @Resource private RestTemplate restTemplate;

    @Override
    public String getType() { return "httpCall"; }

    @Override
    public Object execute(JSONObject config, ActionContext ctx) throws Exception {
        String url = config.getString("url");
        String method = config.getString("method");
        // 模板渲染：${order_no} → ctx.newData.order_no
        String body = renderTemplate(config.getString("bodyTemplate"), ctx);
        HttpHeaders headers = buildHeaders(config.getJSONObject("headers"));
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.valueOf(method), entity, String.class);
        return resp.getBody();
    }
}
```

### 4.5 审批流核心（ApprovalService）

```java
@Service
public class ApprovalService {
    @Resource private LowcodeHelper helper;
    @Resource private SqlEngineFacade facade;
    @Resource private ApproverResolver approverResolver;
    @Resource private StatusChangeDetector statusChangeDetector;

    // 创建审批实例
    public void createInstance(String flowCode, String modelCode, String bizId,
                               String onApprove, String onReject, String dbName) throws Exception {
        // 1. 查流程第一个节点
        JSONArray nodes = helper.queryAndMap("lowcode_approval_node", NODE_COLS,
            helper.eq("flow_code", flowCode), "node_order", true, NODE_MAPPING);
        if (nodes.isEmpty()) throw new IllegalArgumentException("审批流无节点: " + flowCode);

        // 2. 创建审批实例
        JSONObject instance = new JSONObject();
        instance.put("instance_id", UUID.randomUUID().toString());
        instance.put("flow_code", flowCode);
        instance.put("model_code", modelCode);
        instance.put("biz_id", bizId);
        instance.put("current_node_id", nodes.getJSONObject(0).getString("nodeId"));
        instance.put("status", "pending");
        helper.insert("lowcode_approval_instance", instance);

        // 3. 单据状态改为 "approving"
        updateBizStatus(modelCode, bizId, "approving", dbName);
    }

    // 审批通过/拒绝
    public void approve(String instanceId, String approver, String action, String comment) throws Exception {
        JSONObject instance = loadInstance(instanceId);
        String modelCode = instance.getString("modelCode");
        String bizId = instance.getString("bizId");

        // 1. 记录审批记录
        saveApprovalRecord(instanceId, instance.getString("currentNodeId"), approver, action, comment);

        if ("reject".equals(action)) {
            // 拒绝：单据状态 → onReject
            helper.update("lowcode_approval_instance", updateStatus("rejected"), helper.eq("instance_id", instanceId));
            String onReject = getOnReject(instance);
            updateBizStatus(modelCode, bizId, onReject, dbName);
            // onReject 状态变更可能再触发新动作
            fireStatusChange(modelCode, bizId, "approving", onReject, dbName);
        } else {
            // 通过：检查是否有下一节点
            JSONObject nextNode = getNextNode(instance);
            if (nextNode == null) {
                // 全部通过：单据状态 → onApprove
                helper.update("lowcode_approval_instance", updateStatus("approved"), helper.eq("instance_id", instanceId));
                String onApprove = getOnApprove(instance);
                updateBizStatus(modelCode, bizId, onApprove, dbName);
                fireStatusChange(modelCode, bizId, "approving", onApprove, dbName);
            } else {
                // 流转到下一节点
                helper.update("lowcode_approval_instance", updateCurrentNode(nextNode.getString("nodeId")), ...);
            }
        }
    }
}
```

### 4.6 异步动作消费者（RocketMQ）

```java
@Component
@RocketMQMessageListener(topic = "lowcode-trigger-event", consumerGroup = "lowcode-trigger-consumer")
public class TriggerEventConsumer implements RocketMQListener<MessageExt> {
    @Resource private Map<String, ActionExecutor> executors;
    @Resource private TriggerLogService logService;

    @Override
    public void onMessage(MessageExt message) {
        JSONObject payload = JSON.parseObject(new String(message.getBody()));
        JSONObject action = payload.getJSONObject("action");
        ActionContext ctx = JSON.parseObject(payload.getString("_ctx"), ActionContext.class);
        String type = action.getString("actionType");
        ActionExecutor executor = executors.get(type);
        try {
            executor.execute(action.getJSONObject("config"), ctx);
            logService.success(action, ctx, 0);
        } catch (Exception e) {
            logService.fail(action, ctx, e, 0);
            throw e;  // 抛出触发 RocketMQ 重试
        }
    }
}
```

---

## 五、接口定义

### 5.1 触发配置管理（设计器用）

| 接口                               | 方法   | 入参                 | 说明               |
| ---------------------------------- | ------ | -------------------- | ------------------ |
| `/api/lowcode/trigger/list`        | POST   | `{modelCode?}`       | 触发配置列表       |
| `/api/lowcode/trigger`             | POST   | JSONObject           | upsert，evict 缓存 |
| `/api/lowcode/trigger/{triggerId}` | DELETE | path                 | 删除，evict 缓存   |
| `/api/lowcode/trigger/test`        | POST   | `{triggerId, bizId}` | 模拟触发，调试用   |

### 5.2 审批流管理（设计器用）

| 接口                                    | 方法   | 入参       | 说明             |
| --------------------------------------- | ------ | ---------- | ---------------- |
| `/api/lowcode/approval/flow/list`       | POST   | 无         | 审批流列表       |
| `/api/lowcode/approval/flow`            | POST   | JSONObject | upsert 流程+节点 |
| `/api/lowcode/approval/flow/{flowCode}` | DELETE | path       | 删除流程         |
| `/api/lowcode/approval/flow/{flowCode}` | POST   | path       | 查流程详情+节点  |

### 5.3 审批操作（业务用户用）

| 接口                            | 方法 | 入参                              | 说明       |
| ------------------------------- | ---- | --------------------------------- | ---------- |
| `/api/lowcode/approval/pending` | POST | `{modelCode, approver}`           | 待审批列表 |
| `/api/lowcode/approval/approve` | POST | `{instanceId, approver, comment}` | 通过       |
| `/api/lowcode/approval/reject`  | POST | `{instanceId, approver, comment}` | 拒绝       |
| `/api/lowcode/approval/history` | POST | `{modelCode, bizId}`              | 审批历史   |

---

## 六、多角色评审

### 6.1 产品经理

| 维度   | 评估                                                                                                                                                                                |
| ------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 可行性 | ✅ 高。状态触发配置化，新增触发零代码；custom 动作兜底保证灵活性                                                                                                                    |
| 关注点 | ① 审批流 MVP 只做线性审批，会签/分支后续扩展，需明确告知用户；② 触发配置的调试能力重要（trigger/test 接口）；③ 审批人解析（role/dynamic）需要权限系统配合，当前阶段先支持 user 类型 |
| 建议   | MVP 审批人类型只做 `user`（指定用户ID），`role`/`dynamic` 后续接权限系统                                                                                                            |

### 6.2 开发经理

| 维度   | 评估                                                                                                                                                                                                                                             |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 可行性 | ✅ 高。ActionExecutor 接口可扩展，Spring 自动注入所有实现；复用 MetaEngineService + BatchFieldTransformer + RocketMQ                                                                                                                             |
| 关注点 | ① 无 @Transactional，同步动作的 rollback 只能回退状态（非严格事务回滚），custom handler 里用户自己保证一致性；② 审批通过/拒绝会触发新状态变更（递归），防环用 triggerChain 检测；③ DocConvert 复用 BatchFieldTransformer 做 code→id 转换，已确认 |
| 建议   | rollback 策略明确为"回退单据状态 + 记录异常日志"，不追求跨表严格回滚；防环在 ActionContext.triggerChain 里记录，检测重复中止                                                                                                                     |

### 6.3 安全经理

| 维度   | 评估                                                                                                                                                                                                                                       |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 可行性 | ⚠️ 中。custom 动作无限制（用户确认），但需审计                                                                                                                                                                                             |
| 关注点 | ① custom 可调任意 Spring Bean，先不限制但必须记审计日志（lowcode_trigger_log）；② 审批操作需校验审批人身份（当前阶段先不接鉴权，但 approve 接口的 approver 参数后续要从登录态取）；③ HttpCall 外部接口防 SSRF（先不做 url 白名单，后续补） |
| 建议   | lowcode_trigger_log 全量记录（谁/何时/哪个动作/成功失败/耗时）；approve 接口 approver 参数后续改为从 Token 解析                                                                                                                            |

### 6.4 架构师

| 维度   | 评估                                                                                                                                                                                   |
| ------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 可行性 | ✅ 高。事件驱动 + 动作编排是成熟模式，同步/异步分离清晰                                                                                                                                |
| 关注点 | ① 触发配置走 L1CaffeineCache，变更时 evict + RocketMQ 跨实例广播；② 异步动作的 RocketMQ 消息体含 ActionContext，需注意序列化大小；③ 审批流从零做，4 张表 + ApprovalService，工作量可控 |
| 建议   | ActionContext 序列化用 JSON，oldData/newData 可能较大，RocketMQ 消息体建议限制 4KB 以内，超出只传 bizId 让消费者自己查                                                                 |

### 6.5 DBA

| 维度   | 评估                                                                                   |
| ------ | -------------------------------------------------------------------------------------- |
| 可行性 | ✅ 高。DDL 用 IF NOT EXISTS，索引齐全                                                  |
| 关注点 | ① lowcode_trigger_log 会持续增长，需定期清理或分区；② 审批实例查 biz_id 需索引（已建） |
| 建议   | lowcode_trigger_log 加 created_at 索引，按月清理；其余可接受                           |

### 6.6 运维

| 维度   | 评估                                                                                            |
| ------ | ----------------------------------------------------------------------------------------------- |
| 可行性 | ✅ 高。异步动作走 RocketMQ 有重试和死信队列                                                     |
| 关注点 | ① 异步动作失败靠 RocketMQ 重试（默认最多 3 次），死信队列需监控；② 同步动作失败回退状态，需告警 |
| 建议   | 死信队列 `lowcode-trigger-event` 监控接入；同步动作失败打 ERROR 日志                            |

### 6.7 评审综合结论

**方案可行**。核心是 ActionExecutor 接口可扩展 + 同步/异步分离 + 审批流从零 MVP。

**MVP 范围明确**：

1. 审批人类型只做 `user`（指定用户ID），`role`/`dynamic` 后续
2. 审批流只做线性审批（按节点顺序），会签/分支后续
3. custom 动作不限制，但全量审计日志
4. HttpCall 不做 url 白名单，后续补 SSRF 防护
5. rollback 策略为回退状态 + 记录异常，非严格事务回滚

---

## 七、落地步骤

| 阶段 | 内容                                                                                          | 文件                               | 依赖                       |
| ---- | --------------------------------------------------------------------------------------------- | ---------------------------------- | -------------------------- |
| 1    | DDL：触发配置 + 审批流 4 表 + 触发日志                                                        | `lowcode_tables.sql`               | 无                         |
| 2    | 动作接口：ActionExecutor + ActionContext                                                      | `trigger/action/` 新增 2 文件      | 无                         |
| 3    | 动作执行器：7 种实现                                                                          | `trigger/action/` 新增 7 文件      | 阶段 2 + MetaEngineService |
| 4    | 触发核心：TriggerDispatcher + StatusChangeDetector + TriggerConfigService + TriggerLogService | `trigger/` 新增 4 文件             | 阶段 3                     |
| 5    | MetaEngineService 改造：doUpdate 嵌入状态检测                                                 | `engine/MetaEngineService.java` 改 | 阶段 4                     |
| 6    | 异步消费者：TriggerEventConsumer                                                              | `trigger/` 新增 1 文件             | 阶段 4 + RocketMQ          |
| 7    | 审批流：ApprovalService + ApprovalController + ApprovalFlowService + ApproverResolver         | `approval/` 新增 4 文件            | 阶段 4                     |
| 8    | 触发配置管理：TriggerController                                                               | `trigger/` 新增 1 文件             | 阶段 4                     |
| 9    | 前端：触发配置页 + 审批流设计页 + 审批操作页                                                  | `web-code`                         | 阶段 7,8                   |
| 10   | 验证：配触发 → 模拟状态变更 → 验证同步/异步动作 → 审批闭环                                    | -                                  | 阶段 7,8                   |

---

## 八、风险与对策

| 风险                   | 等级 | 对策                                                                |
| ---------------------- | ---- | ------------------------------------------------------------------- |
| 触发环（A→B→A）        | 高   | ActionContext.triggerChain 记录触发链，检测重复中止                 |
| 同步动作失败数据不一致 | 中   | rollback 回退状态 + 记录异常日志；custom handler 用户自己保证一致性 |
| 异步动作消息丢失       | 中   | RocketMQ 持久化 + 重试 3 次 + 死信队列监控                          |
| custom 动作滥用        | 中   | 先不限制，全量审计日志（lowcode_trigger_log）                       |
| 审批人解析失败         | 低   | MVP 只做 user 类型，直接用用户ID                                    |
| HttpCall SSRF          | 中   | 先不做 url 白名单，后续补                                           |
| 触发日志膨胀           | 低   | 按月清理，created_at 索引                                           |
| RocketMQ 消息体过大    | 中   | ActionContext 超出 4KB 只传 bizId，消费者自查                       |

---

## 九、阶段边界

| 阶段             | 能做                                                                 | 不能做                                                                    |
| ---------------- | -------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| 第一版（本设计） | 状态触发配置化、7 种动作、线性审批、同步/异步分离、custom 自定义代码 | 会签/分支审批、审批人 role/dynamic 解析、HttpCall SSRF 防护、严格事务回滚 |
| 第二版（后续）   | 会签/分支、role/dynamic 审批人、SSRF 白名单、审批委托/转签           | -                                                                         |

---

## 十、附录：与元数据驱动 API 的集成点

| 集成点                       | 说明                                             |
| ---------------------------- | ------------------------------------------------ |
| `MetaEngineService.doUpdate` | 嵌入 StatusChangeDetector，update 后检测状态变更 |
| `MetaEngineService`          | DocConvertActionExecutor 复用其 insert 能力      |
| `BatchFieldTransformer`      | DocConvertActionExecutor 复用其 code→id 批量转换 |
| `MetaCacheService`           | 触发配置 + 审批流配置走 L1CaffeineCache          |
| `SqlEngineFacade`            | custom handler 可注入使用                        |
| RocketMQ                     | 异步动作 + 跨实例缓存失效广播                    |
| `lowcode_model`              | 触发配置 + 审批流关联 modelCode                  |
