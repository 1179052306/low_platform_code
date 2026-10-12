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
    private Boolean isSystemField;
    private String permissionExpr;
    private String defaultValueExpr;
    private String validationExpr;
    private Integer sortNo;
}