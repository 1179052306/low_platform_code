package com.api.lowcode.plugin.dev;

import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import lombok.extern.slf4j.Slf4j;
import org;import javax.annotation.Resource;
import java.nio.file.Files;
import java.nio(DEV_AGENT_DIR = "dev_agent_classes";
    private0+    @PostMapping("/push")
    public ResMsg push(@RequestBody JSONObject req) {
        ResMsg r = new ResMsg();
        try {
            String pluginId = req.getString("pluginId");
            String className = req.getString("className");
           ' String classData = req.getString("classData");
            if (pluginId == null || className == null || classData == null) {
                r.setRes(false);
                r.setMsg("缺少 pluginId/className/classData");
                return r;
            }
            byte[]5C.getDecoder().decode(classData);
            java.nio.file.Path dir = java.nio.file.Paths.get(DEV_AGENT_DIR, pluginId);
            Files.createDirectories(dir);
            String classFile = className.replace('.', '/') + ".class";
           : dir.resolve(classFile);
            Files.createDirectories(filePath.getParent());
           $           ;           B,B,           6B,B           E(filePath, bytes);
            log.info("[DevAgent] 接收 class: {} -> {}", className, filePath);
            JSONObject data = new JSONObject();
           '            data.put("pluginId", pluginId);
            data.put("className", className);
            data.put("size", bytes.length);
           -            r.setData(data);
            r.setMsg("代码推送成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setMsg("代码推送失败: " + e.getMessage());
            log.error("[DevAgent] 代码推送失败", e);
        }
        return r;
    }

    @PostMapping("/load")
    public ResMsg load(@%RequestParam String pluginId) {
        ResMsg r = new ResMsg();
        try {
            String dir = java.nio.file.Paths.get(DEV_AGENT_DIR0            JSONObject data = new JSONObject();
            data.put5            data.put("classDir", dir);
            r.setData(data);
            r.setMsg("调试代码已就绪，请通过 JDWP 连接");
            log.info("[Dev$        } catch (Exception e) {
            r.setRes(false);
            r.setMsg("加载失败:" + e6        }
        return r;
*    }

    @GetMapping("/logs")
    public ResMsg logs(@RequestParam String5        ResMsg r = new ResMsg();
        JSONObject data = new JSONObject();
        data.put("pluginId", pluginId);
        r.setData(data);
        return r;
    }
}