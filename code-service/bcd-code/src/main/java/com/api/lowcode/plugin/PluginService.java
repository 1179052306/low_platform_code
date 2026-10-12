package com.api.lowcode.plugin;

import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.engine.CrudExecutor;
import com.api.lowcode.engine.event.SyncEventBus;
import com.api.lowcode.meta.MetaRegistry;
import com.common.returns.ResMsg;
import com.server.sqlengine.SqlEngineJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@Slf4j
@Service
public class PluginService {

    @Resource
    private PluginContainer pluginContainer;

    @Resource
    private SqlEngineJson helper;

    private static final String PLUGIN_DIR = "plugins";

    public ResMsg upload(MultipartFile file) {
        ResMsg r = new ResMsg();
        try {
            String originalName = file.getOriginalFilename();
            if (originalName == null || !originalName.endsWith(".jar")) {
                r.setRes(false);
                r.setMsg("仅支持 .jar 文件");
                return r;
            }
            File tempJar = File.createTempFile("plugin_upload_", ".jar");
            file.transferTo(tempJar);
            PluginDescriptor descriptor = parseDescriptor(tempJar);
            descriptor.validate();
            String pluginId = descriptor.getPluginId();
            String version = descriptor.getVersion();
            Path pluginDir = Paths.get(PLUGIN_DIR, pluginId, version);
            Files.createDirectories(pluginDir);
            Path jarPath = pluginDir.resolve(originalName);
            Files.copy(tempJar.toPath(), jarPath, StandardCopyOption.REPLACE_EXISTING);
            String absJarPath = jarPath.toAbsolutePath().toString();
            registerInDb(pluginId, version, descriptor, absJarPath);
            pluginContainer.load(pluginId, version);
            tempJar.delete();
            JSONObject data = new JSONObject();
            data.put("pluginId", pluginId);
            data.put("version", version);
            data.put("jarPath", jarPath);
            r.setData(data);
            r.setMsg("插件上传并加载成功");
            log.info("[PluginService] 插件上传成功: {} v{}", pluginId, version);
        } catch (Exception e) {
            r.setRes(false);
            r.setMsg("插件上传失败: " + e.getMessage());
            log.error("[PluginService] 插件上传失败", e);
        }
        return r;
    }

    private PluginDescriptor parseDescriptor(File jarFile) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry("plugin.yml");
            if (entry == null) {
                throw new IllegalArgumentException("jar 中缺少 plugin.yml");
            }
            try (InputStream is = jar.getInputStream(entry)) {
                return PluginDescriptor.parse(is);
            }
        }
    }

    private void registerInDb(String pluginId, String version, PluginDescriptor desc, String jarPath)
            throws Exception {
        String id = UUID.randomUUID().toString().replace("-", "");
        String now = Instant.now().toString();
        JSONObject data = new JSONObject();
        data.put("id", id);
        data.put("plugin_id", pluginId);
        data.put("version", version);
        data.put("name", desc.getName());
        data.put("entry_class", desc.getEntryClass());
        data.put("jar_path", jarPath);
        data.put("status", "LOADED");
        data.put("enabled", true);
        data.put("created_at", now);
        data.put("updated_at", now);
        ResMsg r = helper.upsert("lc_plugin_registry", data, helper.eq("plugin_id", pluginId));
        SqlEngineJson.check(r);
    }

    public ResMsg unload(String pluginId) {
        ResMsg r = new ResMsg();
        try {
            pluginContainer.unload(pluginId);
            r.setMsg("插件卸载成功: " + pluginId);
        } catch (Exception e) {
            r.setRes(false);
            r.setMsg("插件卸载失败: " + e.getMessage());
        }
        return r;
    }

    public ResMsg list() {
        ResMsg r = new ResMsg();
        java.util.List<String> loaded = pluginContainer.listLoadedPlugins();
        JSONObject data = new JSONObject();
        data.put("loaded", loaded);
        r.setData(data);
        return r;
    }
}