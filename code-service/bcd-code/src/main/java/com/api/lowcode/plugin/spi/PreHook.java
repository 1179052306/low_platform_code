package com.api.lowcode.plugin.spi;

import com.api.lowcode.plugin.spi.ActionHook;
import com.api.lowcode.plugin.spi.HookPhase;
import com.api.lowcode.plugin.spi.PluginContext;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 保存前钩子基类 —— 开发者继承此类，只需写构造器 + invoke()。
 *
 * 用法：
 *   public class MyHook extends PreHook {
 *       public MyHook() {
 *           super("my-hook", "B_ITEM", "create", "update");
 *       }
 *       @Override
 *       public void invoke(ActionContext ac) throws Exception {
 *           // 只写业务逻辑
 *       }
 *   }
 *
 * 如果要对所有业务生效：super("my-hook") 不传 bizCode/action，在 invoke 里自己判断。
 */
public abstract class PreHook implements ActionHook {

    protected PluginContext ctx;

    private final String pluginId;
    private final String bizCode;
    private final Set<String> actions;

    /** 匹配指定 bizCode + 指定 action（可多个） */
    public PreHook(String pluginId, String bizCode, String... actions) {
        this.pluginId = pluginId;
        this.bizCode = bizCode;
        this.actions = new HashSet<>(Arrays.asList(actions));
    }

    /** 匹配所有 bizCode 所有 action，在 invoke 里自行判断 */
    public PreHook(String pluginId) {
        this.pluginId = pluginId;
        this.bizCode = null;
        this.actions = null;
    }

    @Override
    public final void init(PluginContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public final void destroy() {}

    @Override
    public final String pluginId() {
        return pluginId;
    }

    @Override
    public String version() {
        return "1.0.0";
    }

    @Override
    public final HookPhase phase() {
        return HookPhase.PRE;
    }

    @Override
    public final boolean support(String bizCode, String action) {
        if (this.bizCode == null) return true;
        return this.bizCode.equals(bizCode)
                && (actions == null || actions.contains(action));
    }
}