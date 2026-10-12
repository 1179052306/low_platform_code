package com.api.lowcode.dict;

import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/lowcode/dict")
public class DictController {

    @Resource
    private DictService dictService;

    @PostMapping("/list")
    public ResMsg listDict() {
        ResMsg r = new ResMsg();
        try {
            r.setData(dictService.listDict());
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/save")
    public ResMsg saveDict(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            dictService.saveDict(data);
            r.setMsg("保存成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/delete")
    public ResMsg deleteDict(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            dictService.deleteDict(data.getString("code"));
            r.setMsg("删除成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/item/list")
    public ResMsg listDictItems(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            r.setData(dictService.listDictItems(data.getString("dictCode")));
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/item/save")
    public ResMsg saveDictItem(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            dictService.saveDictItem(data);
            r.setMsg("保存成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }

    @PostMapping("/item/delete")
    public ResMsg deleteDictItem(@RequestBody JSONObject data) {
        ResMsg r = new ResMsg();
        try {
            dictService.deleteDictItem(data.getString("id"));
            r.setMsg("删除成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setCode("500");
            r.setMsg(e.getMessage());
        }
        return r;
    }
}