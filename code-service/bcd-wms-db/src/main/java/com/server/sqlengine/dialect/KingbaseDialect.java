package com.server.sqlengine.dialect;

import com.server.db.DbType;
import org.springframework.stereotype.Component;

/**
 * 电科金仓（KingbaseES）方言实现。
 * <p>
 * 金仓兼容 PostgreSQL 语法，继承 {@link PostgresSqlDialect}，仅覆盖 dbType。
 * </p>
 *
 * @author liwei
 * @date 2026/9/9
 */
@Component
public class KingbaseDialect extends PostgresSqlDialect {

    @Override
    public String dbType() {
        return DbType.KINGBASE;
    }
}