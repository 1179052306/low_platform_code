package com.api.lowcode.plugin.spi;

import com.api.lowcode.tx.ActionContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 业务插件基类 —— 一个类处理一个业务的所有操作（保存前、保存后、自定义按钮等）。
 *
 * 用法：
 *   public class ItemPlugin extends BizPlugin {
 *       public ItemPlugin() {
 *           super("item-plugin", "B_ITEM");
 *       }
 *
 *       @Override
 *       protected void register() {
 *           before("create", "update", this::beforeSave);    // 保存前
 *           after("create", this::afterCreate);              // 保存后
 *           after("approve", this::onApprove);               // 自定义按钮
 *       }
 *
 *       private void beforeSave(ActionContext ac) throws Exception { ... }
 *       private void afterCreate(ActionContext ac) throws Exception { ... }
 *       private void onApprove(ActionContext ac) throws Exception { ... }
 *   }
 *
 * 如果要对所有业务生效：super("my-plugin") 不传 bizCode。
 */
public abstract class BizPlugin implements MultiHook {

    protected PluginContext ctx;

    private final String pluginId;
    private final String bizCode;
    private final List<HookEntry> entries = new ArrayList<>();

    /** 处理指定业务 */
    public BizPlugin(String pluginId, String bizCode) {
        this.pluginId = pluginId;
        this.bizCode = bizCode;
    }

    /** 处理所有业务（bizCode 为 null，在 handler 里自行判断） */
    public BizPlugin(String pluginId) {
        this.pluginId = pluginId;
        this.bizCode = null;
    }

    /** handler 函数式接口（支持抛异常） */
    @FunctionalInterface
    public interface Handler {
        void handle(ActionContext ac) throws Exception;
    }

    private static class HookEntry {
        final HookPhase phase;
        final Set<String> actions;
        final Handler handler;

        HookEntry(HookPhase phase, Set<String> actions, Handler handler) {
            this.phase = phase;
            this.actions = actions;
            this.handler = handler;
        }
    }

    // ================ 注册 API（在 register() 中调用） ================

    /** 保存前钩子（CRUD 执行之前） */
    protected void before(String action, Handler h) {
        before(new String[]{action}, h);
    }

    /** 保存前钩子（匹配多个 action） */
    protected void before(String[] actions, Handler h) {
        entries.add(new HookEntry(HookPhase.PRE, new HashSet<>(Arrays.asList(actions)), h));
    }

    /** 保存后钩子（CRUD 执行之后） */
    protected void after(String action, Handler h) {
        after(new String[]{action}, h);
    }

    /** 保存后钩子（匹配多个 action） */
    protected void after(String[] actions, Handler h) {
        entries.add(new HookEntry(HookPhase.POST, new HashSet<>(Arrays.asList(actions)), h));
    }

    /** 自定义按钮 action（CRUD 执行之后触发） */
    protected void action(String action, Handler h) {
        entries.add(new HookEntry(HookPhase.POST, new HashSet<>(Arrays.asList(action)), h));
    }

    /** 子类实现：在此方法中用 before()/after()/action() 注册各 handler */
    protected abstract void register();

    // ================ Plugin / Lifecycle 实现 ================

    @Override
    public final void init(PluginContext ctx) {
        this.ctx = ctx;
        register();
    }

    @Override
    public void destroy() {}

    @Override
    public final String pluginId() {
        return pluginId;
    }

    @Override
    public String version() {
        return "1.0.0";
    }

    // ================ MultiHook 实现 ================

    @Override
    public final List<ActionHook> getHooks() {
        List<ActionHook> hooks = new ArrayList<>();
        for (HookEntry e : entries) {
            hooks.add(new InternalHook(pluginId, bizCode, e.phase, e.actions, e.handler));
        }
        return hooks;
    }

    /** 内部 ActionHook 适配器 */
    private static class InternalHook implements ActionHook {
        private final String pluginId;
        private final String bizCode;
        private final HookPhase phase;
        private final Set<String> actions;
        private final Handler handler;

        InternalHook(String pluginId, String bizCode, HookPhase phase, Set<String> actions, Handler handler) {
            this.pluginId = pluginId;
            this.bizCode = bizCode;
            this.phase = phase;
            this.actions = actions;
            this.handler = handler;
        }

        @Override public void init(PluginContext ctx) {}
        @Override public void destroy() {}
        @Override public String pluginId() { return pluginId; }
        @Override public String version() { return "1.0.0"; }
        @Override public HookPhase phase() { return phase; }

        @Override
        public boolean support(String bc, String act) {
            if (bizCode != null && !bizCode.equals(bc)) return false;
            return actions.contains(act);
        }

        @Override
        public void invoke(ActionContext ac) throws Exception {
            handler.handle(ac);
        }
    }
}