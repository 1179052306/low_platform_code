package com.api.lowcode.plugin.dev;

import com.alibaba.fastjson2.JSONObject;
import com.common.returns.ResMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/lowcode/dev-agent")
public class DevAgentController {

    private static final String DEV_AGENT_DIR = "dev_agent_classes";
    private final List<String> debugLogs = new CopyOnWriteArrayList<>();

    @PostMapping("/push")
    public ResMsg push(@RequestBody JSONObject req) {
        ResMsg r = new ResMsg();
        try {
            String pluginId = req.getString("pluginId");
            String className = req.getString("className");
            String classData = req.getString("classData");
            if (pluginId == null || className == null || classData == null) {
                r.setRes(false);
                r.setMsg("缺少 pluginId/className/classData");
                return r;
            }
            byte[] bytes = Base64.getDecoder().decode(classData);
            Path dir = Paths.get(DEV_AGENT_DIR, pluginId);
            Files.createDirectories(dir);
            String classFile = className.replace('.', '/') + ".class";
            Path filePath = dir.resolve(classFile);
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, bytes);
            log.info("[DevAgent] 接收 class: {} -> {}", className, filePath);
            JSONObject data = new JSONObject();
            data.put("pluginId", pluginId);
            data.put("className", className);
            data.put("size", bytes.length);
            r.setData(data);
            r.setMsg("代码推送成功");
        } catch (Exception e) {
            r.setRes(false);
            r.setMsg("代码推送失败: " + e.getMessage());
            log.error("[DevAgent] 代码推送失败", e);
        }
        return r;
    }

    @PostMapping("/load")
    public ResMsg load(@RequestParam String pluginId) {
        ResMsg r = new ResMsg();
        try {
            String dir = Paths.get(DEV_AGENT_DIR, pluginId).toAbsolutePath().toString();
            JSONObject data = new JSONObject();
            data.put("pluginId", pluginId);
            data.put("classDir", dir);
            r.setData(data);
            r.setMsg("调试代码已就绪，请通过 JDWP 连接");
            log.info("[DevAgent] 调试代码就绪 pluginId={} dir={}", pluginId, dir);
        } catch (Exception e) {
            r.setRes(false);
            r.setMsg("加载失败: " + e.getMessage());
        }
        return r;
    }

    @GetMapping("/logs")
    public ResMsg logs(@RequestParam String pluginId) {
        ResMsg r = new ResMsg();
        JSONObject data = new JSONObject();
        data.put("pluginId", pluginId);
        data.put("logs", debugLogs);
        r.setData(data);
        return r;
    }
}