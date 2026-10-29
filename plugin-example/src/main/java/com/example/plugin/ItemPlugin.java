package com.example.plugin;

import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.tx.ActionContext;
import com.api.lowcode.plugin.spi.BizPlugin;

/**
 * 物料业务插件 —— 一个类处理 B_ITEM 的所有操作。
 *
 * 包含：
 *   - 保存前：校验 + 自动填充审计字段 + 查重
 *   - 保存后（新增）：联动写库存初始化 + 操作日志
 *   - 保存后（修改）：写操作日志
 *   - 事务提交后（新增）：发通知（失败不影响主操作）
 *   - 自定义按钮"审批"：onApprove
 */
public class ItemPlugin extends BizPlugin {

    public ItemPlugin() {
        super("item-plugin");
    }

    @Override
    protected void register() {
        before(new String[]{"create", "update"}, this::beforeSave);
        after("create", this::afterCreate);
        after("update", this::afterUpdate);
        afterCommit("create", this::notifyOnCreate);
        action("approve", this::onApprove);
    }

    // ================ 保存前 ================

    private void beforeSave(ActionContext ac) throws Exception {
        JSONObject payload = ac.getPayload();
        String action = ac.getAction();
        String userId = ac.getUserId();

        ctx.logger().info("[物料保存前] action={}, userId={}", action, userId);

        // 校验
        String itemName = payload.getString("item_name");
        if (itemName == null || itemName.trim().isEmpty()) {
            throw new IllegalArgumentException("物料名称不能为空");
        }

        // 自动填充审计字段
        long now = System.currentTimeMillis();
        if ("create".equals(action)) {
            payload.put("created_by", userId != null ? userId : "system");
            payload.put("created_time", now);
        }
        payload.put("updated_by", userId != null ? userId : "system");
        payload.put("updated_time", now);

        // 查重
        String itemCode = payload.getString("item_code");
        if (itemCode != null && "create".equals(action)) {
            JSONObject existing = ctx.queryById("B_ITEM", itemCode);
            if (existing != null) {
                throw new IllegalArgumentException("物料编码已存在: " + itemCode);
            }
        }
    }

    // ================ 保存后 ================

    private void afterCreate(ActionContext ac) throws Exception {
        JSONObject payload = ac.getPayload();
        String itemId = payload.getString("id");
        String userId = ac.getUserId();

        ctx.logger().info("[物料新增后] itemId={}", itemId);

        // 联动写库存初始化
        JSONObject stockInit = new JSONObject();
        stockInit.put("item_id", itemId);
        stockInit.put("quantity", 0);
        stockInit.put("init_time", System.currentTimeMillis());
        stockInit.put("init_by", userId != null ? userId : "system");
        try {
            ctx.insert("B_STOCK_INIT", stockInit);
        } catch (Exception e) {
            ctx.logger().info("[物料新增后] 库存初始化跳过: {}", e.getMessage());
        }

        writeOpLog("create", itemId, "新增物料: " + payload.getString("item_name"), userId);
    }

    private void afterUpdate(ActionContext ac) throws Exception {
        JSONObject payload = ac.getPayload();
        writeOpLog("update", payload.getString("id"),
                "修改物料: " + payload.getString("item_name"), ac.getUserId());
    }

    // ================ 事务提交后 ================

    private void notifyOnCreate(ActionContext ac) throws Exception {
        JSONObject payload = ac.getPayload();
        String itemId = payload.getString("id");
        String itemName = payload.getString("item_name");
        ctx.logger().info("[事务已提交] 发送新增物料通知: id={}, name={}", itemId, itemName);
    }

    // ================ 自定义按钮：审批 ================

    private void onApprove(ActionContext ac) throws Exception {
        JSONObject payload = ac.getPayload();
        String itemId = payload.getString("id");
        String userId = ac.getUserId();

        ctx.logger().info("[物料审批] itemId={}, userId={}", itemId, userId);

        // 查当前数据
        JSONObject item = ctx.queryById("B_ITEM", itemId);
        if (item == null) {
            throw new IllegalArgumentException("物料不存在: " + itemId);
        }

        // 检查状态
        String status = item.getString("status");
        if (!"DRAFT".equals(status)) {
            throw new IllegalStateException("只有草稿状态才能审批，当前状态: " + status);
        }

        // 更新状态
        JSONObject updateData = new JSONObject();
        updateData.put("status", "APPROVED");
        updateData.put("approved_by", userId);
        updateData.put("approved_time", System.currentTimeMillis());
        JSONObject conditions = new JSONObject();
        conditions.put("id", itemId);
        ctx.update("B_ITEM", updateData, conditions);

        writeOpLog("approve", itemId, "审批通过", userId);
    }

    // ================ 公共方法 ================

    private void writeOpLog(String action, String recordId, String summary, String userId) {
        JSONObject log = new JSONObject();
        log.put("biz_code", "B_ITEM");
        log.put("action", action);
        log.put("record_id", recordId);
        log.put("operator", userId != null ? userId : "system");
        log.put("operate_time", System.currentTimeMillis());
        log.put("summary", summary);
        try {
            ctx.insert("B_OP_LOG", log);
        } catch (Exception e) {
            ctx.logger().info("[操作日志] 写入跳过: {}", e.getMessage());
        }
    }
}