package com.server.basedata.projection;

import com.server.basedata.api.CompactRow;
import com.server.basedata.metadata.CompiledProjection;

/**
 * 投影裁剪器。
 * <p>
 * 根据 {@link CompiledProjection} 提供的字段下标数组，从完整 {@link CompactRow} 中提取子集，
 * 生成只包含所需字段的紧凑行。支持单行、列表、映射三种形式的批量裁剪。
 * <p>
 * 投影裁剪在内存中完成，避免为不同字段组合重复回源或缓存多份完整数据。
 */
public class ProjectionResolver {

  /**
   * 对单行执行投影裁剪。
   * <p>
   * 当 row 为 null 时返回 null；当 projection 为 null 时返回原行（不裁剪）。
   *
   * @param row        紧凑行
   * @param projection 预编译投影，null 表示不裁剪
   * @return 裁剪后的紧凑行；row 为 null 时返回 null
   */
  public CompactRow project(CompactRow row, CompiledProjection projection) {
    CompactRow result;
    if (row == null) {
      // 空行直接返回 null
      result = null;
    } else if (projection == null) {
      // 无投影，返回原行
      result = row;
    } else {
      // 按投影下标提取字段值
      int[] indexes = projection.getFieldIndexes();
      if (indexes == null || indexes.length == 0) {
        // 投影下标为空，返回原行（防御性处理）
        result = row;
      } else {
        Object[] projected = new Object[indexes.length];
        for (int i = 0; i < indexes.length; i++) {
          projected[i] = row.get(indexes[i]);
        }
        result = new CompactRow(row.getId(), projected,
            row.getSchemaVersion(), row.getDataVersion());
      }
    }
    return result;
  }

  /**
   * 对行列表批量执行投影裁剪。
   *
   * @param rows       紧凑行列表
   * @param projection 预编译投影
   * @return 裁剪后的列表；rows 为 null 时返回空列表
   */
  public java.util.List<CompactRow> projectAll(
      java.util.List<CompactRow> rows, CompiledProjection projection) {
    java.util.List<CompactRow> result = new java.util.ArrayList<CompactRow>();
    if (rows != null) {
      for (CompactRow row : rows) {
        CompactRow projected = project(row, projection);
        result.add(projected);
      }
    }
    return result;
  }

  /**
   * 对行映射批量执行投影裁剪，保持原 key 不变。
   *
   * @param rowMap     主键到紧凑行的映射
   * @param projection 预编译投影
   * @return 裁剪后的映射；rowMap 为 null 时返回空映射
   */
  public java.util.Map<Object, CompactRow> projectAll(
      java.util.Map<Object, CompactRow> rowMap, CompiledProjection projection) {
    java.util.Map<Object, CompactRow> result = new java.util.HashMap<Object, CompactRow>();
    if (rowMap != null) {
      for (java.util.Map.Entry<Object, CompactRow> entry : rowMap.entrySet()) {
        CompactRow projected = project(entry.getValue(), projection);
        result.put(entry.getKey(), projected);
      }
    }
    return result;
  }
}
