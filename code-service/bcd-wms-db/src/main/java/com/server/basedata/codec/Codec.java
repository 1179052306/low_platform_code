package com.server.basedata.codec;

import com.server.basedata.api.CompactRow;
import com.server.basedata.metadata.CompiledEntity;

/**
 * 编解码接口。
 * <p>
 * 定义 {@link CompactRow} 与可持久化字符串形式（如 JSON）之间的双向转换契约，
 * 供 L2 Redis 缓存读写、跨进程传输等场景使用。编解码需依赖
 * {@link CompiledEntity} 提供的字段顺序与命名信息，以保证数组下标与字段名对齐。
 */
public interface Codec {

  /**
   * 将紧凑行编码为字符串。
   *
   * @param row    紧凑行
   * @param entity 预编译实体，提供字段名顺序
   * @return 编码后的字符串（通常为 JSON）
   */
  String encode(CompactRow row, CompiledEntity entity);

  /**
   * 将字符串解码为紧凑行。
   *
   * @param data   编码后的字符串
   * @param entity 预编译实体，提供字段名顺序
   * @return 解码后的紧凑行
   */
  CompactRow decode(String data, CompiledEntity entity);
}
