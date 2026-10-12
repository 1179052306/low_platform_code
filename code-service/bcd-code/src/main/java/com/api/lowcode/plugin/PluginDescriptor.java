package com.api.lowcode.plugin;

import lombok.Data;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class PluginDescriptor {

    private String pluginId;
    private String version;
    private String name;
    private String author;
    private String minPlatformVersion;
    private String entryClass;
    private List<PluginDependency> dependencies = new ArrayList<>();
    private List<String> permissions = new ArrayList<>();
    private List<String> hooks = new ArrayList<>();
    private List<String> listeners = new ArrayList<>();

    @Data
    public static class PluginDependency {
        private String pluginId;
        private String version;
    }

    @SuppressWarnings("unchecked")
    public static PluginDescriptor parse(InputStream yamlStream) {
        Yaml yaml = new Yaml();
        Map<String, Object> root = yaml.load(yamlStream);
        PluginDescriptor d = new PluginDescriptor();
        d.pluginId = (String) root.get("pluginId");
        d.version = (String) root.get("version");
        d.name = (String) root.get("name");
        d.author = (String) root.get("author");
        d.minPlatformVersion = (String) root.get("minPlatformVersion");
        d.entryClass = (String) root.get("entryClass");
        List<Object> deps = (List<Object>) root.get("dependencies");
        if (deps != null) {
            for (Object dep : deps) {
                Map<String, Object> depMap = (Map<String, Object>) dep;
                PluginDependency pd = new PluginDependency();
                pd.pluginId = (String) depMap.get("pluginId");
                pd.version = (String) depMap.get("version");
                d.dependencies.add(pd);
            }
        }
        List<Object> perms = (List<Object>) root.get("permissions");
        if (perms != null) {
            for (Object perm : perms) {
                d.permissions.add((String) perm);
            }
        }
        List<Object> hookList = (List<Object>) root.get("hooks");
        if (hookList != null) {
            for (Object hook : hookList) {
                Map<String, Object> hookMap = (Map<String, Object>) hook;
                d.hooks.add((String) hookMap.get("impl"));
            }
        }
        List<Object> listenerList = (List<Object>) root.get("listeners");
        if (listenerList != null) {
            for (Object listener : listenerList) {
                Map<String, Object> listenerMap = (Map<String, Object>) listener;
                d.listeners.add((String) listenerMap.get("impl"));
            }
        }
        return d;
    }

    public void validate() {
        if (pluginId == null || pluginId.isEmpty()) {
            throw new IllegalArgumentException("plugin.yml 缺少 pluginId");
        }
        if (version == null || version.isEmpty()) {
            throw new IllegalArgumentException("plugin.yml 缺少 version");
        }
        if (entryClass == null || entryClass.isEmpty()) {
            throw new IllegalArgumentException("plugin.yml 缺少 entryClass");
        }
    }
}