package com.api.lowcode.plugin;

import com.common.returns.ResMsg;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/lowcode/plugin")
public class PluginController {

    @Resource
    private PluginService pluginService;

    @PostMapping("/upload")
    public ResMsg upload(@RequestParam("file") MultipartFile file) {
        return pluginService.upload(file);
    }

    @PostMapping("/unload")
    public ResMsg unload(@RequestParam("pluginId") String pluginId) {
        return pluginService.unload(pluginId);
    }

    @GetMapping("/list")
    public ResMsg list() {
        return pluginService.list();
    }
}