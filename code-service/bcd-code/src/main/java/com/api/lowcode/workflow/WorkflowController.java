package com.api.lowcode.workflow;

import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/lowcode")
public class WorkflowController {

    @Resource
    private WorkflowService workflowService;

    @PostMapping("/workflow")
    public ResMsg workflow(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            String op = data.getString("op");
            if ("save".equals(op)) {
                workflowService.saveWorkflow(data);
                r.setMsg("保存成功");
            } else if ("delete".equals(op)) {
                workflowService.deleteWorkflow(data.getString("id"));
                r.setMsg("删除成功");
            } else if ("validate".equals(op)) {
                r.setData(workflowService.validateWorkflow(data));
            } else if ("publish".equals(op)) {
                workflowService.publishWorkflow(data);
                r.setMsg("发布成功");
            } else if ("start".equals(op)) {
                String instanceId = workflowService.startWorkflow(
                    data.getString("workflowCode"),
                    data.getString("bizCode"),
                    data.getString("bizId"),
                    data.getString("initiator"));
                r.setData(instanceId);
                r.setMsg("流程已启动");
            } else {
                r.setData(workflowService.listWorkflows());
            }
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/approval/action")
    public ResMsg approvalAction(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            workflowService.approvalAction(data);
            r.setMsg("操作成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/approval/history")
    public ResMsg approvalHistory(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            r.setData(workflowService.listApprovalHistory(
                data.getString("bizCode"), data.getString("bizId")));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/todo/list")
    public ResMsg listTodo(@RequestBody(required = false) JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            String assignee = data != null ? data.getString("assignee") : null;
            String type = data != null ? data.getString("type") : null;
            r.setData(workflowService.listTodo(assignee, type));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }
}