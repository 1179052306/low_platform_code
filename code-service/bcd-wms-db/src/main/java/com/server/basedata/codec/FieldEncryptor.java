package com.server.basedata.codec;

/**
 * 字段加密器接口 — 对标记为 IS_ENCRYPTED 的字段进行加解密。
 * <p>
 * 在 CompactRowCodec 编码到 Redis 前加密敏感字段，解码时解密，
 * 避免敏感数据（如密码、token）以明文存储在 L2 Redis 中。
 */
public interface FieldEncryptor {

  /**
   * 加密字段值。
   *
   * @param plainValue 明文值（已转为 String）
   * @return 密文（Base64 编码），null 输入返回 null
   */
  String encrypt(String plainValue);

  /**
   * 解密字段值。
   *
   * @param cipherValue 密文（Base64 编码），null 输入返回 null
   * @return 明文值
   */
  String decrypt(String cipherValue);
}