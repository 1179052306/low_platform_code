package com.api.lowcode.plugin;

import com.api.lowcode.plugin.spi.PluginLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginLoggerImpl implements PluginLogger {

    private final Logger logger;

    public PluginLoggerImpl(String pluginId) {
        this.logger = LoggerFactory.getLogger("plugin." + pluginId);
    }

    @Override
    public void info(String format, Object... args) {
        logger.info(format(format, args));
    }

    @Override
    public void warn(String format, Object... args) {
        logger.warn(format(format, args));
    }

    @Override
    public void error(String format, Object... args) {
        logger.error(format(format, args));
    }

    @Override
    public void debug(String format, Object... args) {
        logger.debug(format(format, args));
    }

    private String format(String format, Object... args) {
        if (args == null || args.length == 0) {
            return format;
        }
        String result = format;
        for (Object arg : args) {
            int idx = result.indexOf("{}");
            if (idx < 0) {
                break;
            }
            result = result.substring(0, idx) + String.valueOf(arg) + result.substring(idx + 2);
        }
        return result;
    }
}