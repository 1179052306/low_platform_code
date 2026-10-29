package com.api.system.common;

import com.alibaba.fastjson2.JSONObject;

/** 请求体键名兼容工具：配置页表单 payload 键为蛇形（dbField），历史接口读驼峰——双键兼容（驼峰优先，蛇形兜底） */
public final class BodyKeys {

    private BodyKeys() {
    }

    public static String str(JSONObject body, String camel, String snake) {
        String v = body.getString(camel);
        return v != null ? v : body.getString(snake);
    }

    public static Integer integer(JSONObject body, String camel, String snake) {
        Integer v = body.getInteger(camel);
        return v != null ? v : body.getInteger(snake);
    }

    public static Boolean bool(JSONObject body, String camel, String snake, boolean dft) {
        if (body.containsKey(camel)) {
            return body.getBooleanValue(camel, dft);
        }
        if (body.containsKey(snake)) {
            return body.getBooleanValue(snake, dft);
        }
        return dft;
    }
}
