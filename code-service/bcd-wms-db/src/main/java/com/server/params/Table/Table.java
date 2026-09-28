package com.server.params.Table;

import java.util.Map;

/**
 * @author lw
 * @date: 2026/4/10
 * @description:
 **/
public class Table {

    public Table(Map<String, TableNode> columnMap) {
        this.columnMap = columnMap;
    }

    private final Map<String, TableNode> columnMap;
    public TableNode getColumn(String columnName) {
        return columnMap.get(columnName);
    }
}
