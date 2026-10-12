package com.api.lowcode.meta;

import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/lowcode/meta")
public class MetaController {

    @Resource
    private MetaService metaService;

    @GetMapping("/biz/list")
    public ResMsg listBizMeta() {
        ResMsg r = new ResMsg();
        try {
            r.setData(metaService.listBizMeta());
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @GetMapping("/biz/{bizCode}")
    public ResMsg getBizMeta(@PathVariable String bizCode) {
        ResMsg r = new ResMsg();
        try {
            r.setData(metaService.getBizMeta(bizCode));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/biz")
    public ResMsg saveBizMeta(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            metaService.saveBizMeta(data);
            r.setMsg("保存成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @DeleteMapping("/biz/{bizCode}")
    public ResMsg deleteBizMeta(@PathVariable String bizCode) {
        ResMsg r = new ResMsg();
        try {
            metaService.deleteBizMeta(bizCode);
            r.setMsg("删除成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @GetMapping("/field/list")
    public ResMsg listFieldMeta(@RequestParam String bizCode) {
        ResMsg r = new ResMsg();
        try {
            r.setData(metaService.listFieldMeta(bizCode));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/field")
    public ResMsg saveFieldMeta(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            metaService.saveFieldMeta(data);
            r.setMsg("保存成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @DeleteMapping("/field/{id}")
    public ResMsg deleteFieldMeta(@PathVariable String id) {
        ResMsg r = new ResMsg();
        try {
            metaService.deleteFieldMeta(id);
            r.setMsg("删除成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @GetMapping("/action/list")
    public ResMsg listActionMeta(@RequestParam String bizCode) {
        ResMsg r = new ResMsg();
        try {
            r.setData(metaService.listActionMeta(bizCode));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/action")
    public ResMsg saveActionMeta(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            metaService.saveActionMeta(data);
            r.setMsg("保存成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @DeleteMapping("/action/{id}")
    public ResMsg deleteActionMeta(@PathVariable String id) {
        ResMsg r = new ResMsg();
        try {
            metaService.deleteActionMeta(id);
            r.setMsg("删除成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @GetMapping("/stateMachine/list")
    public ResMsg listStateMachine(@RequestParam String bizCode) {
        ResMsg r = new ResMsg();
        try {
            r.setData(metaService.listStateMachine(bizCode));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/stateMachine")
    public ResMsg saveStateMachine(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            metaService.saveStateMachine(data);
            r.setMsg("保存成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @DeleteMapping("/stateMachine/{id}")
    public ResMsg deleteStateMachine(@PathVariable String id) {
        ResMsg r = new ResMsg();
        try {
            metaService.deleteStateMachine(id);
            r.setMsg("删除成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/merged")
    public ResMsg getMergedSchema(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            r.setData(metaService.getMergedSchema(data.getString("pageId")));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/version-history")
    public ResMsg getVersionHistory(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            r.setData(metaService.getVersionHistory(data.getString("metaKey")));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/version-schema")
    public ResMsg getVersionSchema(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            r.setData(metaService.getVersionSchema(data.getString("pageId"), data.getString("versionId")));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/rollback")
    public ResMsg rollback(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            metaService.rollback(data.getString("metaKey"), data.getString("versionId"));
            r.setMsg("回滚成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/check-breaking")
    public ResMsg checkBreaking(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            r.setData(metaService.checkBreaking(data.getString("metaKey"), data.getJSONObject("newContent")));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/list")
    public ResMsg listMetaRegistry(@RequestBody(required = false) JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            r.setData(metaService.listMetaRegistry(data));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/save")
    public ResMsg saveMetaRegistry(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            metaService.saveMetaRegistry(data);
            r.setMsg("保存成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public ResMsg deleteMetaRegistry(@PathVariable String id) {
        ResMsg r = new ResMsg();
        try {
            metaService.deleteMetaRegistry(id);
            r.setMsg("删除成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }
}