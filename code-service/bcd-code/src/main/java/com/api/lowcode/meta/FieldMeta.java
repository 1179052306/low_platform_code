package com.api.lowcode.meta;

import lombok.Data;

@Data
public class FieldMeta {

    private String id;
    private String bizCode;
    private String fieldName;
    private String columnType;
    private String dataType;
    private Boolean isRequired;
    private Boolean isUnique;
    /** 组合唯一字段列表（含自身，JSON 数组字符串，与 __uniqueChecks 的 fields 语义一致） */
    private String uniqueGroup;
    private Boolean isSystemField;
    private String permissionExpr;
    private String defaultValueExpr;
    private String validationExpr;
    private Integer sortNo;
}