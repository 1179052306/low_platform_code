package com.api.lowcode.meta;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class BizMeta {

    private String id;
    private String bizCode;
    private String bizName;
    private String mainTable;
    private String mainTableAlias;
    private String statusField;
    private String logicDeleteField;
    private String primaryKeyField;
    private String keyStrategy;
    private String tenantId;
    private Integer version;
    private String remark;

    private List<FieldMeta> fields = new ArrayList<>();
    private Map<String, ActionMeta> actions = new LinkedHashMap<>();
    private List<StateMachineMeta> stateTransitions = new ArrayList<>();

    public ActionMeta getAction(String action) {
        return actions.get(action);
    }

    public FieldMeta getField(String fieldName) {
        if (fields == null) {
            return null;
        }
        for (FieldMeta f : fields) {
            if (f.getFieldName().equals(fieldName)) {
                return f;
            }
        }
        return null;
    }
}