package com.server.sqlengine.dialect;

import com.server.db.DbType;
import org.springframework.stereotype.Component;

/**
 * 达梦（DM）方言实现。
 * <p>
 * DM 兼容 Oracle 语法，继承 {@link OracleDialect}，仅覆盖 dbType 和时间字面量。
 * </p>
 *
 * @author liwei
 * @date 2026/9/9
 */
@Component
public class DmDialect extends OracleDialect {

    @Override
    public String dbType() {
        return DbType.DM;
    }

    @Override
    public String currentTimestamp() {
        return "SYSDATE";
    }
}
