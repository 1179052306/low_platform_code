package com.api.lowcode.w3d;

import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/w3d")
public class W3DController {

    @Resource
    private W3DLayoutService layoutService;

    @GetMapping("/layout/{warehouseId}")
    public ResMsg getLayout(@PathVariable String warehouseId) {
        ResMsg r = new ResMsg();
        try {
            r.setData(layoutService.getLayout(warehouseId));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PutMapping("/layout/{warehouseId}")
    public ResMsg saveLayout(@PathVariable String warehouseId, @RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            String changeLog = data.getString("changeLog");
            String operator = data.getString("operator");
            if (operator == null) operator = "system";
            layoutService.saveLayout(warehouseId, data, changeLog, operator);
            r.setMsg("保存成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @GetMapping("/snapshot/list/{warehouseId}")
    public ResMsg listSnapshots(@PathVariable String warehouseId) {
        ResMsg r = new ResMsg();
        try {
            r.setData(layoutService.listSnapshots(warehouseId));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/snapshot/{warehouseId}")
    public ResMsg createSnapshot(@PathVariable String warehouseId, @RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            String description = data.getString("description");
            String operator = data.getString("operator");
            if (operator == null) operator = "system";
            W3DWarehouse wh = layoutService.getLayout(warehouseId);
            String snapId = layoutService.createSnapshot(warehouseId, description, operator,
                    JSONObject.from(wh));
            r.setData(snapId);
            r.setMsg("快照创建成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/snapshot/rollback")
    public ResMsg rollback(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            String warehouseId = data.getString("warehouseId");
            String snapshotId = data.getString("snapshotId");
            String operator = data.getString("operator");
            if (operator == null) operator = "system";
            r.setData(layoutService.rollback(warehouseId, snapshotId, operator));
            r.setMsg("回滚成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }
}