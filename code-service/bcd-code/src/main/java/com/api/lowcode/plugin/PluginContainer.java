package com.api.lowcode.plugin;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.api.lowcode.engine.CrudExecutor;
import com.api.lowcode.engine.event.SyncEventBus;
import com.api.lowcode.meta.MetaRegistry;
import com.api.lowcode.plugin.spi.ActionHook;
import com.api.lowcode.plugin.spi.HookPhase;
import com.api.lowcode.plugin.spi.MultiHook;
import com.api.lowcode.plugin.spi.Plugin;
import com.api.lowcode.plugin.spi.PluginContext;
import com.api.lowcode.plugin.spi.StateChangeListener;
import com.api.lowcode.tx.ActionContext;
import com.server.sqlengine.SqlEngineJson;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class PluginContainer {

    @Resource
    private SqlEngineJson helper;

    @Resource
    private CrudExecutor crudExecutor;

    @Resource
    private MetaRegistry metaRegistry;

    @Resource
    private SyncEventBus eventBus;

    private final ConcurrentMap<String, PluginEntry> plugins = new ConcurrentHashMap<>();
    private final List<ActionHook> preHooks = new CopyOnWriteArrayList<>();
    private final List<ActionHook> postHooks = new CopyOnWriteArrayList<>();
    private final List<ActionHook> afterCommitHooks = new CopyOnWriteArrayList<>();
    private final List<StateChangeListener> stateListeners = new CopyOnWriteArrayList<>();

    public void load(String pluginId, String version) throws Exception {
        if (plugins.containsKey(pluginId)) {
            log.warn("[PluginContainer] 插件已加载，先卸载旧版本: {}", pluginId);
            unload(pluginId);
        }
        String[] cols = { "id", "plugin_id", "version", "jar_path", "entry_class" };
        JSONArray conditions = helper.eq("plugin_id", pluginId);
        JSONArray rows = helper.query("lc_plugin_registry", cols, conditions, null, false);
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("插件未注册: " + pluginId);
        }
        JSONObject row = rows.getJSONObject(0);
        String jarPath = row.getString("JAR_PATH");
        if (jarPath == null || !new File(jarPath).exists()) {
            throw new IllegalArgumentException("插件 jar 不存在: " + jarPath);
        }
        URL[] urls = { new File(jarPath).toURI().toURL() };
        PluginClassLoader classLoader = new PluginClassLoader(urls, getClass().getClassLoader());
        String entryClass = row.getString("ENTRY_CLASS");
        if (entryClass == null || entryClass.isEmpty()) {
            try (FileInputStream fis = new FileInputStream(jarPath)) {
                java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarPath);
                java.util.jar.JarEntry ymlEntry = jarFile.getJarEntry("plugin.yml");
                if (ymlEntry != null) {
                    PluginDescriptor desc = PluginDescriptor.parse(jarFile.getInputStream(ymlEntry));
                    desc.validate();
                    entryClass = desc.getEntryClass();
                }
                jarFile.close();
            }
        }
        Class<?> clazz = classLoader.loadClass(entryClass);
        Plugin plugin = (Plugin) clazz.getDeclaredConstructor().newInstance();
        PluginContext ctx = new PluginContextImpl(pluginId, helper, crudExecutor, metaRegistry);
        plugin.init(ctx);
        PluginEntry entry = new PluginEntry();
        entry.pluginId = pluginId;
        entry.version = version;
        entry.classLoader = classLoader;
        entry.pluginInstance = plugin;
        entry.context = ctx;
        registerHooks(plugin);
        registerStateListeners(plugin);
        plugins.put(pluginId, entry);
        log.info("[PluginContainer] 插件加载成功: {} v{}", pluginId, version);
    }

    private void registerHooks(Plugin plugin) {
        if (plugin instanceof MultiHook) {
            for (ActionHook hook : ((MultiHook) plugin).getHooks()) {
                registerSingleHook(hook);
            }
        } else if (plugin instanceof ActionHook) {
            registerSingleHook((ActionHook) plugin);
        }
    }

    private void registerSingleHook(ActionHook hook) {
        if (hook.phase() == HookPhase.PRE) {
            preHooks.add(hook);
            log.info("[PluginContainer] 注册前置钩子: {}", hook.getClass().getName());
        } else if (hook.phase() == HookPhase.AFTER_COMMIT) {
            afterCommitHooks.add(hook);
            log.info("[PluginContainer] 注册事务提交后钩子: {}", hook.getClass().getName());
        } else {
            postHooks.add(hook);
            log.info("[PluginContainer] 注册后置钩子: {}", hook.getClass().getName());
        }
    }

    private void registerStateListeners(Plugin plugin) {
        if (plugin instanceof StateChangeListener) {
            StateChangeListener listener = (StateChangeListener) plugin;
            stateListeners.add(listener);
            eventBus.registerListener(new com.api.lowcode.engine.event.BizEventListener() {
                @Override
                public boolean support(com.api.lowcode.engine.event.BizEvent event) {
                    return "STATE_CHANGE".equals(event.getType())
                            && listener.support(event.getBizCode(),
                                    ((com.api.lowcode.engine.event.StateChangeEvent) event).getEvent());
                }

                @Override
                public void onEvent(com.api.lowcode.engine.event.BizEvent event) throws Exception {
                    listener.onStateChange((com.api.lowcode.engine.event.StateChangeEvent) event);
                }
            });
            log.info("[PluginContainer] 注册状态监听器: {}", listener.getClass().getName());
        }
    }

    public void unload(String pluginId) {
        PluginEntry entry = plugins.remove(pluginId);
        if (entry == null) {
            return;
        }
        try {
            entry.pluginInstance.destroy();
        } catch (Exception e) {
            log.warn("[PluginContainer] 插件 destroy 异常: {}", pluginId, e);
        }
        preHooks.removeIf(h -> h.pluginId().equals(pluginId));
        postHooks.removeIf(h -> h.pluginId().equals(pluginId));
        afterCommitHooks.removeIf(h -> h.pluginId().equals(pluginId));
        stateListeners.removeIf(l -> l.pluginId().equals(pluginId));
        try {
            entry.classLoader.close();
        } catch (Exception e) {
            log.warn("[PluginContainer] ClassLoader 关闭异常: {}", pluginId, e);
        }
        entry.classLoader = null;
        System.gc();
        log.info("[PluginContainer] 插件卸载成功: {}", pluginId);
    }

    public void invokePreHooks(String bizCode, String action, ActionContext ctx) throws Exception {
        for (ActionHook hook : preHooks) {
            if (hook.support(bizCode, action)) {
                log.debug("[PluginContainer] 前置钩子执行: {} bizCode={} action={}", hook.getClass().getName(),
                        bizCode, action);
                hook.invoke(ctx);
            }
        }
    }

    public void invokePostHooks(String bizCode, String action, ActionContext ctx) throws Exception {
        for (ActionHook hook : postHooks) {
            if (hook.support(bizCode, action)) {
                log.debug("[PluginContainer] 后置钩子执行: {} bizCode={} action={}", hook.getClass().getName(),
                        bizCode, action);
                hook.invoke(ctx);
            }
        }
    }

    /**
     * 按 pluginId 查找已加载的钩子。
     */
    public ActionHook findHookById(String pluginId, HookPhase phase) {
        List<ActionHook> hooks;
        if (phase == HookPhase.PRE) {
            hooks = preHooks;
        } else if (phase == HookPhase.AFTER_COMMIT) {
            hooks = afterCommitHooks;
        } else {
            hooks = postHooks;
        }
        for (ActionHook hook : hooks) {
            if (hook.pluginId().equals(pluginId)) {
                return hook;
            }
        }
        return null;
    }

    /**
     * 按页面绑定的插件列表执行钩子（不再全局遍历）。
     *
     * @param bindings JSON 数组：[{pluginId, sortOrder, enabled}]
     */
    public void invokeBoundHooks(String bizCode, String action, ActionContext ctx,
                                 JSONArray bindings, HookPhase phase) throws Exception {
        if (bindings == null || bindings.isEmpty()) return;

        List<JSONObject> sorted = new java.util.ArrayList<>();
        for (int i = 0; i < bindings.size(); i++) {
            JSONObject b = bindings.getJSONObject(i);
            if (b.getBooleanValue("enabled")) {
                sorted.add(b);
            }
        }
        sorted.sort(java.util.Comparator.comparingInt(b -> b.getIntValue("sortOrder", 0)));

        for (JSONObject b : sorted) {
            String pluginId = b.getString("pluginId");
            ActionHook hook = findHookById(pluginId, phase);
            if (hook == null) {
                log.warn("[PluginContainer] 绑定的插件未加载，跳过: {} phase={}", pluginId, phase);
                continue;
            }
            log.debug("[PluginContainer] 绑定钩子执行: {} bizCode={} action={} phase={}", pluginId, bizCode, action, phase);
            hook.invoke(ctx);
        }
    }

    public List<String> listLoadedPlugins() {
        return new java.util.ArrayList<>(plugins.keySet());
    }

    @Data
    static class PluginEntry {
        String pluginId;
        String version;
        PluginClassLoader classLoader;
        Plugin pluginInstance;
        PluginContext context;
    }
}