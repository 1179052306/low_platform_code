package com.server.basedata.codec;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES-CBC 字段加密器 — 对敏感字段执行 AES-128-CBC 加解密。
 * <p>
 * 密钥从构造函数传入，IV 固定为密钥前 16 字节（适用于单机缓存场景；
 * 分布式场景应改用随机 IV + 密文前缀 IV，此处保持简单）。
 * <p>
 * 加密结果以 Base64 编码，便于 JSON 序列化存储到 Redis。
 */
public class AesFieldEncryptor implements FieldEncryptor {

  /** 加密算法：AES/CBC/PKCS5Padding */
  private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
  /** 密钥算法 */
  private static final String KEY_ALGORITHM = "AES";

  /** 加密 Cipher（线程安全：每次调用 clone 或重新 init） */
  private final SecretKeySpec keySpec;
  /** IV 参数（固定使用密钥前 16 字节） */
  private final IvParameterSpec ivSpec;

  /**
   * 构造 AES 加密器。
   *
   * @param secretKey 密钥（至少 16 字节，取前 16 字节作为 AES-128 密钥）
   */
  public AesFieldEncryptor(byte[] secretKey) {
    byte[] keyBytes = new byte[16];
    int copyLen = Math.min(secretKey.length, 16);
    System.arraycopy(secretKey, 0, keyBytes, 0, copyLen);
    this.keySpec = new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    this.ivSpec = new IvParameterSpec(keyBytes);
  }

  @Override
  public String encrypt(String plainValue) {
    if (plainValue == null) {
      return null;
    }
    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
      byte[] encrypted = cipher.doFinal(plainValue.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(encrypted);
    } catch (Exception e) {
      throw new RuntimeException("字段加密失败", e);
    }
  }

  @Override
  public String decrypt(String cipherValue) {
    if (cipherValue == null) {
      return null;
    }
    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
      byte[] decoded = Base64.getDecoder().decode(cipherValue);
      byte[] decrypted = cipher.doFinal(decoded);
      return new String(decrypted, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException("字段解密失败", e);
    }
  }
}