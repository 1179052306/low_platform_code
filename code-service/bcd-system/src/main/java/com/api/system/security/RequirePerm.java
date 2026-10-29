package com.api.system.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 功能权限注解：校验当前用户角色拥有该按钮权限码（ADMIN 短路放行） */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePerm {
    String value();
}
