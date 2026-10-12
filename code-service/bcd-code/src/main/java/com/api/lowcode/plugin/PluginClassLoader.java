package com.api.lowcode.plugin;

import lombok.extern.slf4j.Slf4j;

import java.net.URL;
import java.net.URLClassLoader;

@Slf4j
public class PluginClassLoader extends URLClassLoader {

    private static final String[] PLATFORM_PREFIXES = {
            "com.api.lowcode.plugin.spi.",
            "com.api.lowcode.tx.ActionContext",
            "com.api.lowcode.engine.event.",
            "com.alibaba.fastjson2.",
            "org.springframework.",
            "java.",
            "javax.",
            "sun.",
            "com.sun.",
            "jdk."
    };

    public PluginClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> c = findLoadedClass(name);
            if (c != null) {
                return c;
            }
            if (isPlatformClass(name)) {
                return super.loadClass(name, resolve);
            }
            try {
                c = findClass(name);
                if (resolve) {
                    resolveClass(c);
                }
                log.debug("[PluginClassLoader] child-first 加载: {}", name);
                return c;
            } catch (ClassNotFoundException e) {
                return super.loadClass(name, resolve);
            }
        }
    }

    private boolean isPlatformClass(String name) {
        for (String prefix : PLATFORM_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}