package com.server.params.Table;

/**
 * @author lw
 * @date: 2026/4/10
 * @description:
 **/
public class TableNode {
    private final String oracleType;
    private final String oracleSize;

    public TableNode(String oracleType, String oracleSize) {
        this.oracleType = oracleType;
        this.oracleSize = oracleSize;
    }

    // getters
    public String getOracleType() { return oracleType; }
    public String getOracleSize() { return oracleSize; }
}
