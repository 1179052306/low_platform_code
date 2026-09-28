package com.server.basedata.metadata;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Formatter;
import java.util.List;

/**
 * Schema 哈希构建器。
 * <p>
 * 将一组列结构信息（{@link SchemaScanner.ColumnInfo}）按固定格式拼接后计算 SHA-256 摘要，
 * 得到该表结构的稳定哈希。结构哈希用于检测表结构是否变更：当哈希变化时触发元数据重新编译
 * 与缓存版本切换。SHA-256 不可用时回退到字符串的 hashCode 十六进制表示，保证不抛异常。
 */
public class SchemaHashBuilder {

  /**
   * 计算列结构列表的哈希。
   * <p>
   * 拼接格式：每列以 {@code 列名|类型|长度|精度|标度|可空;} 形式串联，再对整体字符串求 SHA-256。
   *
   * @param columns 列信息列表
   * @return 哈希十六进制字符串
   */
  public String buildHash(List<SchemaScanner.ColumnInfo> columns) {
    StringBuilder sb = new StringBuilder();
    if (columns != null) {
      for (SchemaScanner.ColumnInfo col : columns) {
        // 按固定顺序拼接各属性，分隔符区分字段与列
        sb.append(col.getColumnName()).append('|');
        sb.append(col.getDataType()).append('|');
        sb.append(col.getDataLength()).append('|');
        sb.append(col.getDataPrecision()).append('|');
        sb.append(col.getDataScale()).append('|');
        sb.append(col.isNullable()).append(';');
      }
    }
    String result;
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
      result = bytesToHex(hashBytes);
    } catch (Exception e) {
      // SHA-256 不可用时回退到字符串 hashCode，保证健壮性
      result = Integer.toHexString(sb.hashCode());
    }
    return result;
  }

  /**
   * 将字节数组转换为小写十六进制字符串。
   *
   * @param bytes 字节数组
   * @return 十六进制字符串
   */
  private String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder();
    Formatter formatter = new Formatter(sb);
    try {
      for (byte b : bytes) {
        // 每字节两位十六进制，不足补零
        formatter.format("%02x", b);
      }
    } finally {
      formatter.close();
    }
    return sb.toString();
  }
}
