package com.api.lowcode.plugin.spi;

import lombok.Data;

@Data
public class ActionResult {

    private boolean success;
    private String code;
    private String message;
    private Object data;

    public static ActionResult ok(Object data) {
        ActionResult r = new ActionResult();
        r.success = true;
        r.data = data;
        return r;
    }

    public static ActionResult fail(String code, String message) {
        ActionResult r = new ActionResult();
        r.success = false;
        r.code = code;
        r.message = message;
        return r;
    }
}