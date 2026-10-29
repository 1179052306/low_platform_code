package com.api.lowcode.engine;

import lombok.Data;

/**
 * 数据修改审计记录（只记录 UPDATE 操作的变更字段 diff）。
 */
@Data
public class DataAuditRecord {

    private String id;
    private String bizCode;
    private String tableName;
    private String recordId;
    private String action;
    private String changedFields;
    private String beforeData;
    private String afterData;
    private String operator;
    private String operatedAt;
    private String ip;

    public static DataAuditRecord create(String bizCode, String tableName, String recordId,
            String action, String changedFields, String beforeData, String afterData,
            String operator, String ip) {
        DataAuditRecord r = new DataAuditRecord();
        r.setId(java.util.UUID.randomUUID().toString().replace("-", ""));
        r.setBizCode(bizCode);
        r.setTableName(tableName);
        r.setRecordId(recordId);
        r.setAction(action);
        r.setChangedFields(changedFields);
        r.setBeforeData(beforeData);
        r.setAfterData(afterData);
        r.setOperator(operator != null && !operator.isEmpty() ? operator : "system");
        r.setOperatedAt(java.time.Instant.now().toString());
        r.setIp(ip);
        return r;
    }
}