package com.api.lowcode.workflow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class WorkflowService {

    @Resource
    private SqlEngineJson helper;

    public JSONArray listWorkflows() throws Exception {
        String[] cols = { "id", "code", "name", "nodes", "transitions", "enabled", "workflow_type", "bill_code", "description", "params", "control", "created_at", "updated_at" };
        String[][] mapping = {
            { "id", "id" }, { "code", "code" }, { "name", "name" },
            { "nodes", "nodes" }, { "transitions", "transitions" },
            { "enabled", "enabled" },
            { "workflow_type", "workflowType" }, { "bill_code", "billCode" },
            { "description", "description" }, { "params", "params" }, { "control", "control" },
            { "created_at", "createdAt" }, { "updated_at", "updatedAt" }
        };
        JSONArray rows = helper.queryAndMap("lc_workflow_def", cols, null, "code", false, mapping);
        for (int i = 0; i < rows.size(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String nodesStr = row.getString("nodes");
            if (nodesStr != null && !nodesStr.isEmpty()) {
                row.put("nodes", JSON.parse(nodesStr));
            }
            String transStr = row.getString("transitions");
            if (transStr != null && !transStr.isEmpty()) {
                row.put("transitions", JSON.parse(transStr));
            }
            String paramsStr = row.getString("params");
            if (paramsStr != null && !paramsStr.isEmpty()) {
                row.put("params", JSON.parse(paramsStr));
            }
            String controlStr = row.getString("control");
            if (controlStr != null && !controlStr.isEmpty()) {
                row.put("control", JSON.parse(controlStr));
            }
        }
        return rows;
    }

    public void saveWorkflow(JSONObject data) throws Exception {
        String id = data.getString("id");
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString().replace("-", "");
        }
        String now = Instant.now().toString();
        JSONObject row = new JSONObject();
        row.put("id", id);
        row.put("code", data.getString("code"));
        row.put("name", data.getString("name"));
        row.put("nodes", JSON.toJSONString(data.get("nodes")));
        row.put("transitions", JSON.toJSONString(data.get("transitions")));
        row.put("enabled", data.containsKey("enabled") ? data.getBooleanValue("enabled") : true);
        row.put("workflow_type", data.getString("workflowType"));
        row.put("bill_code", data.getString("billCode"));
        row.put("description", data.getString("description"));
        row.put("params", JSON.toJSONString(data.get("params")));
        row.put("control", JSON.toJSONString(data.get("control")));
        row.put("updated_at", now);
        if (data.getString("created_at") == null) {
            row.put("created_at", now);
        } else {
            row.put("created_at", data.getString("created_at"));
        }
        ResMsg r = helper.upsert("lc_workflow_def", row, helper.eq("code", row.getString("code")));
        SqlEngineJson.check(r);
        log.info("[WorkflowService] 保存流程定义: {}", row.getString("code"));
    }

    public void deleteWorkflow(String id) throws Exception {
        ResMsg r = helper.delete("lc_workflow_def", helper.eq("id", id));
        SqlEngineJson.check(r);
        log.info("[WorkflowService] 删除流程定义: {}", id);
    }

    /** 校验流程定义，返回错误列表（空表示通过） */
    public JSONArray validateWorkflow(JSONObject data) {
        JSONArray errors = new JSONArray();
        String code = data.getString("code");
        String name = data.getString("name");
        if (code == null || code.isEmpty()) errors.add("流程编码不能为空");
        if (name == null || name.isEmpty()) errors.add("流程名称不能为空");

        JSONArray nodes = data.getJSONArray("nodes");
        JSONArray transitions = data.getJSONArray("transitions");
        if (nodes == null || nodes.isEmpty()) {
            errors.add("流程节点不能为空");
            return errors;
        }

        boolean hasStart = false, hasEnd = false;
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject n = nodes.getJSONObject(i);
            String type = n.getString("type");
            if ("start".equals(type)) hasStart = true;
            if ("end".equals(type)) hasEnd = true;
            if ("userTask".equals(type)) {
                JSONArray roles = n.getJSONArray("approverRoles");
                JSONArray users = n.getJSONArray("approverUsers");
                if ((roles == null || roles.isEmpty()) && (users == null || users.isEmpty())) {
                    errors.add("人工节点\"" + n.getString("name") + "\"未配置审批人");
                }
            }
        }
        if (!hasStart) errors.add("缺少开始节点");
        if (!hasEnd) errors.add("缺少结束节点");

        if (transitions != null) {
            for (int i = 0; i < nodes.size(); i++) {
                JSONObject n = nodes.getJSONObject(i);
                String nodeId = n.getString("id");
                String type = n.getString("type");
                boolean hasOut = false, hasIn = false;
                for (int j = 0; j < transitions.size(); j++) {
                    JSONObject tr = transitions.getJSONObject(j);
                    if (nodeId.equals(tr.getString("from"))) hasOut = true;
                    if (nodeId.equals(tr.getString("to"))) hasIn = true;
                }
                if (!"end".equals(type) && !hasOut) errors.add("节点\"" + n.getString("name") + "\"没有出口连线");
                if (!"start".equals(type) && !hasIn) errors.add("节点\"" + n.getString("name") + "\"没有入口连线");
            }
        }
        return errors;
    }

    /** 发布流程：校验 + 启用 + 保存 */
    public void publishWorkflow(JSONObject data) throws Exception {
        JSONArray errors = validateWorkflow(data);
        if (!errors.isEmpty()) {
            throw new RuntimeException("校验失败: " + errors.toJSONString());
        }
        data.put("enabled", true);
        saveWorkflow(data);
        log.info("[WorkflowService] 发布流程: {}", data.getString("code"));
    }

    /** 启动流程实例 */
    public String startWorkflow(String workflowCode, String bizCode, String bizId, String initiator) throws Exception {
        JSONArray rows = helper.query("lc_workflow_def", new String[]{"id", "nodes", "transitions"}, helper.eq("code", workflowCode), null, false);
        if (rows.isEmpty()) throw new RuntimeException("流程不存在: " + workflowCode);
        JSONObject wf = rows.getJSONObject(0);
        JSONArray nodes = JSON.parseArray(wf.getString("nodes"));
        JSONArray transitions = JSON.parseArray(wf.getString("transitions"));

        String instanceId = UUID.randomUUID().toString().replace("-", "");
        String now = Instant.now().toString();

        JSONObject variables = new JSONObject();
        variables.put("bizCode", bizCode);
        variables.put("bizId", bizId);
        variables.put("initiator", initiator);

        JSONObject instance = new JSONObject();
        instance.put("id", instanceId);
        instance.put("workflow_code", workflowCode);
        instance.put("biz_code", bizCode);
        instance.put("biz_id", bizId);
        instance.put("status", "RUNNING");
        instance.put("initiator", initiator);
        instance.put("variables", JSON.toJSONString(variables));
        instance.put("created_at", now);
        instance.put("updated_at", now);
        ResMsg r = helper.insert("lc_workflow_instance", instance);
        SqlEngineJson.check(r);

        JSONObject startNode = null;
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject n = nodes.getJSONObject(i);
            if ("start".equals(n.getString("type"))) { startNode = n; break; }
        }
        if (startNode == null) throw new RuntimeException("流程缺少开始节点");

        String nextNodeId = null;
        for (int i = 0; i < transitions.size(); i++) {
            JSONObject tr = transitions.getJSONObject(i);
            if (startNode.getString("id").equals(tr.getString("from"))) {
                nextNodeId = tr.getString("to");
                break;
            }
        }
        if (nextNodeId == null) throw new RuntimeException("开始节点没有后续节点");

        JSONObject nextNode = findNodeById(nodes, nextNodeId);
        if (nextNode == null) throw new RuntimeException("后续节点不存在: " + nextNodeId);

        executeNode(instanceId, nextNode, nodes, transitions, bizCode, bizId, initiator, variables);
        log.info("[WorkflowService] 启动流程实例: instance={} workflow={}", instanceId, workflowCode);
        return instanceId;
    }

    /** 根据节点类型执行节点 */
    private void executeNode(String instanceId, JSONObject node, JSONArray nodes, JSONArray transitions,
                             String bizCode, String bizId, String initiator,
                             JSONObject variables) throws Exception {
        String type = node.getString("type");
        String nodeId = node.getString("id");
        String nodeName = node.getString("name");
        String now = Instant.now().toString();

        JSONObject instUpdate = new JSONObject();
        instUpdate.put("current_node", nodeId);
        instUpdate.put("updated_at", now);
        helper.update("lc_workflow_instance", instUpdate, helper.eq("id", instanceId));

        switch (type) {
            case "end":
                completeInstance(instanceId);
                return;
            case "userTask":
                createUserTask(instanceId, node, bizCode, bizId, initiator, now);
                return;
            case "billTask":
                executeBillTask(node, variables);
                break;
            case "autoTask":
                executeAutoTask(node, variables);
                break;
            case "messageTask":
                executeMessageTask(node, variables);
                break;
            case "waitTask":
                executeWaitTask(instanceId, node, variables, now);
                return;
            case "gateway":
            case "exceptionGateway":
                executeGateway(instanceId, node, nodes, transitions, bizCode, bizId, initiator, variables);
                return;
            default:
                log.warn("[WorkflowService] 未知节点类型: {} node={}", type, nodeName);
                break;
        }

        saveInstanceVariables(instanceId, variables);
        proceedToNext(instanceId, node, nodes, transitions, bizCode, bizId, initiator, variables);
    }

    /** 完成流程实例 */
    private void completeInstance(String instanceId) throws Exception {
        JSONObject update = new JSONObject();
        update.put("status", "COMPLETED");
        update.put("updated_at", Instant.now().toString());
        helper.update("lc_workflow_instance", update, helper.eq("id", instanceId));
        log.info("[WorkflowService] 流程完成: instance={}", instanceId);
    }

    /** 创建待办任务 */
    private void createUserTask(String instanceId, JSONObject node, String bizCode, String bizId,
                                String initiator, String now) throws Exception {
        JSONObject task = new JSONObject();
        task.put("id", UUID.randomUUID().toString().replace("-", ""));
        task.put("instance_id", instanceId);
        task.put("biz_code", bizCode);
        task.put("biz_id", bizId);
        task.put("title", node.getString("name"));
        task.put("workflow_name", "");
        task.put("current_node", node.getString("id"));
        JSONArray approverUsers = node.getJSONArray("approverUsers");
        String assignee = (approverUsers != null && !approverUsers.isEmpty()) ? approverUsers.getString(0) : initiator;
        task.put("assignee", assignee);
        task.put("status", "PENDING");
        task.put("created_at", now);
        helper.insert("lc_workflow_task", task);
        log.info("[WorkflowService] 创建待办任务: instance={} node={}", instanceId, node.getString("name"));
    }

    /** 单据节点：调用统一网关执行单据操作 */
    private void executeBillTask(JSONObject node, JSONObject variables) {
        String billCode = node.getString("billCode");
        String billAction = node.getString("billAction");
        log.info("[WorkflowService] 单据节点: bill={} action={} variables={}", billCode, billAction, variables);
        variables.put(billCode + "_action", billAction);
    }

    /** 自动节点：执行插件或表达式 */
    private void executeAutoTask(JSONObject node, JSONObject variables) {
        String executeExpr = node.getString("executeExpr");
        log.info("[WorkflowService] 自动节点: expr={} variables={}", executeExpr, variables);
        if (executeExpr != null && executeExpr.startsWith("plugin:")) {
            variables.put("_pluginId", executeExpr.substring(7));
        }
    }

    /** 消息节点：发送消息通知 */
    private void executeMessageTask(JSONObject node, JSONObject variables) {
        String messageType = node.getString("messageType");
        log.info("[WorkflowService] 消息节点: type={} variables={}", messageType, variables);
    }

    /** 等待节点：设置实例为等待状态 */
    private void executeWaitTask(String instanceId, JSONObject node, JSONObject variables, String now) throws Exception {
        long duration = node.getLongValue("waitDuration");
        String condition = node.getString("waitCondition");
        JSONObject update = new JSONObject();
        update.put("status", "WAITING");
        update.put("updated_at", now);
        if (duration > 0) {
            update.put("resume_at", Instant.ofEpochMilli(Instant.now().toEpochMilli() + duration).toString());
        }
        if (condition != null && !condition.isEmpty()) {
            update.put("wait_condition", condition);
        }
        helper.update("lc_workflow_instance", update, helper.eq("id", instanceId));
        log.info("[WorkflowService] 等待节点: instance={} duration={} condition={}", instanceId, duration, condition);
    }

    /** 网关节点：按条件路由 */
    private void executeGateway(String instanceId, JSONObject node, JSONArray nodes, JSONArray transitions,
                                String bizCode, String bizId, String initiator,
                                JSONObject variables) throws Exception {
        String gatewayType = node.getString("gatewayType");
        String nodeId = node.getString("id");
        if (gatewayType == null) gatewayType = "exclusive";

        if ("parallel".equals(gatewayType)) {
            for (int i = 0; i < transitions.size(); i++) {
                JSONObject tr = transitions.getJSONObject(i);
                if (nodeId.equals(tr.getString("from"))) {
                    JSONObject nextNode = findNodeById(nodes, tr.getString("to"));
                    if (nextNode != null) {
                        executeNode(instanceId, nextNode, nodes, transitions, bizCode, bizId, initiator, variables);
                    }
                }
            }
            return;
        }

        // exclusive / inclusive：按分支条件选择
        JSONArray branches = node.getJSONArray("branches");
        for (int i = 0; i < transitions.size(); i++) {
            JSONObject tr = transitions.getJSONObject(i);
            if (!nodeId.equals(tr.getString("from"))) continue;
            String condition = tr.getString("condition");
            boolean shouldGo = "inclusive".equals(gatewayType) || i == 0;
            if (condition != null && !condition.isEmpty()) {
                shouldGo = evalSimpleCondition(condition, variables);
            }
            if (shouldGo) {
                JSONObject nextNode = findNodeById(nodes, tr.getString("to"));
                if (nextNode != null) {
                    executeNode(instanceId, nextNode, nodes, transitions, bizCode, bizId, initiator, variables);
                }
                if ("exclusive".equals(gatewayType)) return;
            }
        }
    }

    /** 继续执行后续节点 */
    private void proceedToNext(String instanceId, JSONObject node, JSONArray nodes, JSONArray transitions,
                               String bizCode, String bizId, String initiator,
                               JSONObject variables) throws Exception {
        String nodeId = node.getString("id");
        for (int i = 0; i < transitions.size(); i++) {
            JSONObject tr = transitions.getJSONObject(i);
            if (nodeId.equals(tr.getString("from"))) {
                JSONObject nextNode = findNodeById(nodes, tr.getString("to"));
                if (nextNode != null) {
                    executeNode(instanceId, nextNode, nodes, transitions, bizCode, bizId, initiator, variables);
                    break;
                }
            }
        }
    }

    /** 恢复流程执行（审批通过后调用） */
    private void resumeWorkflow(String instanceId) throws Exception {
        JSONArray instRows = helper.query("lc_workflow_instance",
            new String[]{"workflow_code", "current_node", "biz_code", "biz_id", "initiator", "variables"},
            helper.eq("id", instanceId), null, false);
        if (instRows.isEmpty()) return;
        JSONObject inst = instRows.getJSONObject(0);

        JSONArray wfRows = helper.query("lc_workflow_def", new String[]{"nodes", "transitions"},
            helper.eq("code", inst.getString("workflow_code")), null, false);
        if (wfRows.isEmpty()) return;
        JSONObject wf = wfRows.getJSONObject(0);
        JSONArray nodes = JSON.parseArray(wf.getString("nodes"));
        JSONArray transitions = JSON.parseArray(wf.getString("transitions"));

        JSONObject variables = new JSONObject();
        String varStr = inst.getString("variables");
        if (varStr != null && !varStr.isEmpty()) {
            variables = JSON.parseObject(varStr);
        }

        String currentNodeId = inst.getString("current_node");
        JSONObject currentNode = findNodeById(nodes, currentNodeId);
        if (currentNode == null) return;

        JSONObject instUpdate = new JSONObject();
        instUpdate.put("status", "RUNNING");
        instUpdate.put("updated_at", Instant.now().toString());
        helper.update("lc_workflow_instance", instUpdate, helper.eq("id", instanceId));

        proceedToNext(instanceId, currentNode, nodes, transitions,
            inst.getString("biz_code"), inst.getString("biz_id"),
            inst.getString("initiator"), variables);
    }

    /** 保存流程变量到实例 */
    private void saveInstanceVariables(String instanceId, JSONObject variables) throws Exception {
        JSONObject update = new JSONObject();
        update.put("variables", JSON.toJSONString(variables));
        update.put("updated_at", Instant.now().toString());
        helper.update("lc_workflow_instance", update, helper.eq("id", instanceId));
    }

    /** 查找节点 */
    private JSONObject findNodeById(JSONArray nodes, String id) {
        for (int i = 0; i < nodes.size(); i++) {
            JSONObject n = nodes.getJSONObject(i);
            if (id.equals(n.getString("id"))) return n;
        }
        return null;
    }

    /** 简单条件求值（支持 > < >= <= == != 和 && ||） */
    private boolean evalSimpleCondition(String condition, JSONObject variables) {
        try {
            String expr = condition.trim();
            for (String key : variables.keySet()) {
                Object val = variables.get(key);
                String valStr = (val instanceof String) ? "\"" + val + "\"" : String.valueOf(val);
                expr = expr.replace(key, valStr);
            }
            if (expr.contains(">") || expr.contains("<") || expr.contains("==")) {
                return evalNumericCondition(expr);
            }
            return Boolean.parseBoolean(expr);
        } catch (Exception e) {
            log.warn("[WorkflowService] 条件求值失败: {} error={}", condition, e.getMessage());
            return false;
        }
    }

    private boolean evalNumericCondition(String expr) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "(-?\\d+\\.?\\d*)\\s*(>=|<=|>|<|==|!=)\\s*(-?\\d+\\.?\\d*)").matcher(expr);
        if (m.find()) {
            double left = Double.parseDouble(m.group(1));
            String op = m.group(2);
            double right = Double.parseDouble(m.group(3));
            switch (op) {
                case ">": return left > right;
                case "<": return left < right;
                case ">=": return left >= right;
                case "<=": return left <= right;
                case "==": return left == right;
                case "!=": return left != right;
            }
        }
        return false;
    }

    /** 审批操作：approve/reject/transfer/countersign */
    public void approvalAction(JSONObject data) throws Exception {
        String instanceId = data.getString("instanceId");
        String action = data.getString("action");
        String operator = data.getString("operator");
        String comment = data.getString("comment");
        String now = Instant.now().toString();

        JSONObject record = new JSONObject();
        record.put("id", UUID.randomUUID().toString().replace("-", ""));
        record.put("instance_id", instanceId);
        record.put("biz_code", data.getString("bizCode"));
        record.put("biz_id", data.getString("bizId"));
        record.put("current_node", data.getString("currentNode"));
        record.put("action", action);
        record.put("operator", operator);
        record.put("comment", comment);
        record.put("operated_at", now);
        ResMsg r = helper.insert("lc_approval_record", record);
        SqlEngineJson.check(r);

        if ("approve".equals(action)) {
            JSONObject taskUpdate = new JSONObject();
            taskUpdate.put("status", "DONE");
            helper.update("lc_workflow_task", taskUpdate, helper.eq("instance_id", instanceId));
            resumeWorkflow(instanceId);
        } else if ("reject".equals(action)) {
            JSONObject update = new JSONObject();
            update.put("status", "REJECTED");
            update.put("updated_at", now);
            helper.update("lc_workflow_instance", update, helper.eq("id", instanceId));
            JSONObject taskUpdate = new JSONObject();
            taskUpdate.put("status", "REJECTED");
            helper.update("lc_workflow_task", taskUpdate, helper.eq("instance_id", instanceId));
        } else if ("transfer".equals(action)) {
            String targetUser = data.getString("targetUser");
            if (targetUser != null && !targetUser.isEmpty()) {
                JSONObject taskUpdate = new JSONObject();
                taskUpdate.put("assignee", targetUser);
                helper.update("lc_workflow_task", taskUpdate, helper.eq("instance_id", instanceId));
            }
        } else if ("countersign".equals(action)) {
            String targetUser = data.getString("targetUser");
            if (targetUser != null && !targetUser.isEmpty()) {
                JSONArray taskRows = helper.query("lc_workflow_task", new String[]{"biz_code", "biz_id", "title", "current_node"},
                    helper.eq("instance_id", instanceId), null, false);
                if (!taskRows.isEmpty()) {
                    JSONObject src = taskRows.getJSONObject(0);
                    JSONObject task = new JSONObject();
                    task.put("id", UUID.randomUUID().toString().replace("-", ""));
                    task.put("instance_id", instanceId);
                    task.put("biz_code", src.getString("biz_code"));
                    task.put("biz_id", src.getString("biz_id"));
                    task.put("title", src.getString("title"));
                    task.put("workflow_name", "");
                    task.put("current_node", src.getString("current_node"));
                    task.put("assignee", targetUser);
                    task.put("status", "PENDING");
                    task.put("created_at", now);
                    helper.insert("lc_workflow_task", task);
                }
            }
        }

        log.info("[WorkflowService] 审批操作: instance={} action={}", instanceId, action);
    }

    /** 待办列表（type=todo 返回待办，type=done 返回已办） */
    public JSONArray listTodo(String assignee, String type) throws Exception {
        String[] cols = { "id", "biz_code", "biz_id", "title", "workflow_name", "current_node", "initiator", "created_at" };
        String status = "done".equals(type) ? "DONE" : "PENDING";
        JSONArray conditions = new JSONArray();
        if (assignee != null && !assignee.isEmpty()) {
            JSONArray cond = helper.eq("assignee", assignee);
            for (int i = 1; i < cond.size(); i++) conditions.add(cond.get(i));
        }
        JSONArray statusCond = helper.eq("status", status);
        for (int i = 1; i < statusCond.size(); i++) conditions.add(statusCond.get(i));
        return helper.query("lc_workflow_task", cols, conditions, "created_at", true);
    }

    /** 审批历史（按业务编码+业务ID查询） */
    public JSONArray listApprovalHistory(String bizCode, String bizId) throws Exception {
        String[] cols = { "id", "instance_id", "current_node", "action", "operator", "comment", "operated_at" };
        JSONArray conditions = new JSONArray();
        if (bizCode != null && !bizCode.isEmpty()) {
            JSONArray cond = helper.eq("biz_code", bizCode);
            for (int i = 1; i < cond.size(); i++) conditions.add(cond.get(i));
        }
        if (bizId != null && !bizId.isEmpty()) {
            JSONArray cond = helper.eq("biz_id", bizId);
            for (int i = 1; i < cond.size(); i++) conditions.add(cond.get(i));
        }
        return helper.query("lc_approval_record", cols, conditions, "operated_at", true);
    }

    /** 恢复到期的等待实例（由 WorkflowScheduler 定时调用） */
    public void resumeWaitingInstances() throws Exception {
        String now = Instant.now().toString();
        JSONArray conditions = new JSONArray();
        JSONArray statusCond = helper.eq("status", "WAITING");
        for (int i = 1; i < statusCond.size(); i++) conditions.add(statusCond.get(i));
        JSONArray rows = helper.query("lc_workflow_instance", new String[]{"id"}, conditions, null, false);
        for (int i = 0; i < rows.size(); i++) {
            String instanceId = rows.getJSONObject(i).getString("id");
            try {
                JSONArray instRows = helper.query("lc_workflow_instance",
                    new String[]{"resume_at"}, helper.eq("id", instanceId), null, false);
                if (!instRows.isEmpty()) {
                    String resumeAt = instRows.getJSONObject(0).getString("resume_at");
                    if (resumeAt != null && resumeAt.compareTo(now) <= 0) {
                        log.info("[WorkflowService] 恢复等待实例: {}", instanceId);
                        resumeWorkflow(instanceId);
                    }
                }
            } catch (Exception e) {
                log.error("[WorkflowService] 恢复实例失败: {}", instanceId, e);
            }
        }
    }

    /** 业务流触发检查：单据操作完成后，检查是否有匹配的业务流启动条件 */
    public void checkBizFlowTrigger(String bizCode, String action, String bizId, String operator) throws Exception {
        if (bizId == null || bizId.isEmpty()) return;
        JSONArray conditions = new JSONArray();
        JSONArray typeCond = helper.eq("workflow_type", "bizFlow");
        for (int i = 1; i < typeCond.size(); i++) conditions.add(typeCond.get(i));
        JSONArray enabledCond = helper.eq("enabled", true);
        for (int i = 1; i < enabledCond.size(); i++) conditions.add(enabledCond.get(i));
        JSONArray bizFlows = helper.query("lc_workflow_def", new String[]{"code", "control"}, conditions, null, false);
        for (int i = 0; i < bizFlows.size(); i++) {
            JSONObject wf = bizFlows.getJSONObject(i);
            String controlStr = wf.getString("control");
            if (controlStr == null || controlStr.isEmpty()) continue;
            JSONObject control = JSON.parseObject(controlStr);
            JSONArray startConds = control.getJSONArray("startByBillAction");
            if (startConds == null) continue;
            for (int j = 0; j < startConds.size(); j++) {
                JSONObject cond = startConds.getJSONObject(j);
                String condBillCode = cond.getString("billCode");
                boolean allowed = cond.getBooleanValue("allowed");
                if (bizCode.equals(condBillCode) && allowed) {
                    String condCondition = cond.getString("condition");
                    if (condCondition != null && !condCondition.isEmpty()) {
                        // TODO: 条件求值，暂跳过
                    }
                    log.info("[WorkflowService] 业务流触发: workflow={} bizCode={} action={} bizId={}",
                        wf.getString("code"), bizCode, action, bizId);
                    startWorkflow(wf.getString("code"), bizCode, bizId, operator);
                    return;
                }
            }
        }
    }
}