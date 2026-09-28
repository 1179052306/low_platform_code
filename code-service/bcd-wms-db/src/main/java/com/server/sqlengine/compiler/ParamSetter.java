package com.server.sqlengine.compiler;

import com.server.params.Table.TableNode;
import com.server.params.Table.TableOperation;
import com.server.sqlengine.model.SqlParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 参数绑定器 —— 将原始值包装为 {@link SqlParam}，附带列类型提示。
 * <p>类型提示来自元数据缓存（{@link TableOperation}），用于方言层做类型适配
 * （如 PG 对数字列 IN 查询的 to_number 转换）。元数据未命中时 typeHint 为 null，
 * 不阻断编译——值仍作为参数传入，由 JDBC 驱动处理类型转换。</p>
 *
 * <p>相对旧实现的改进：不再在绑定期做类型校验失败即中断整条语句。
 * 类型不匹配的值留给 JDBC PreparedStatement 在执行时报错，错误定位更精准。</p>
 *
 * @author liwei
 * @date 2026/9/9
 */
@Slf4j
@Service
public class ParamSetter {

    private final TableOperation tableOperation;

    @Autowired
    public ParamSetter(TableOperation tableOperation) {
        this.tableOperation = tableOperation;
    }

    /**
     * 创建带元数据类型提示的参数。
     *
     * @param dbName    数据源名
     * @param tableName 表名
     * @param columnName 列名（大小写容错）
     * @param value     原始值
     * @return 参数对象（typeHint 可能为 null）
     */
    public SqlParam set(@Nullable String dbName, String tableName, String columnName, Object value) {
        String upperCol = columnName == null ? null : columnName.trim().toUpperCase(Locale.ROOT);
        String typeHint = lookupType(dbName, tableName, upperCol);
        return new SqlParam(upperCol, value, typeHint);
    }

    /**
     * 创建无类型提示的参数（用于原始表达式参数）。
     */
    public SqlParam setRaw(Object value) {
        return new SqlParam(null, value, null);
    }

    /**
     * 从元数据缓存查列类型；未命中返回 null（不抛异常）。
     */
    @Nullable
    private String lookupType(@Nullable String dbName, String tableName, @Nullable String upperCol) {
        if (upperCol == null || tableName == null) {
            return null;
        }
        try {
            TableNode node = tableOperation.getTableNode(dbName, tableName, upperCol);
            return node.getOracleType();
        } catch (Exception e) {
            log.debug("元数据未命中：table={}, column={}, 将以无类型提示绑定参数", tableName, upperCol);
            return null;
        }
    }
}
