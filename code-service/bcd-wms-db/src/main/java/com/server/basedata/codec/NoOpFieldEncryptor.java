package com.server.basedata.codec;

/**
 * 空操作加密器 — 不执行任何加解密，原样返回。
 * <p>
 * 用于未配置加密密钥的场景，保持向后兼容。敏感字段仍以明文存储，
 * 应仅在开发/测试环境使用，生产环境必须替换为 {@link AesFieldEncryptor}。
 */
public class NoOpFieldEncryptor implements FieldEncryptor {

  @Override
  public String encrypt(String plainValue) {
    return plainValue;
  }

  @Override
  public String decrypt(String cipherValue) {
    return cipherValue;
  }
}