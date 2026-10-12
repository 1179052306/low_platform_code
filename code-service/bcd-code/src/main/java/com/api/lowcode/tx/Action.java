package com.api.lowcode.tx;

@FunctionalInterface
public interface Action {

    Object execute(ActionContext ctx) throws Exception;
}