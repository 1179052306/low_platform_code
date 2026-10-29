package com.api.lowcode.plugin.spi;

import java.util.List;

/**
 * 多钩子插件接口 —— 一个插件类注册多个 ActionHook。
 *
 * PluginContainer 加载时若发现插件实现了 MultiHook，
 * 会调用 getHooks() 拿到多个 ActionHook 分别注册到 preHooks/postHooks。
 *
 * 开发者通常不直接实现此接口，而是继承 {@link BizPlugin}。
 */
public interface MultiHook extends Plugin {
    List<ActionHook> getHooks();
}