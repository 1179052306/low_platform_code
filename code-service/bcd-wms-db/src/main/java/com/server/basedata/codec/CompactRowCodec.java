package com.server.basedata.codec;

import com.alibaba.fastjson2.JSONObject;
import com.server.basedata.api.CompactRow;
import com.server.basedata.metadata.CompiledEntity;

/**
 * CompactRow ↔ JSON 编解码实现。
 * <p>
 * 使用 fastjson2 将紧凑行序列化为 JSON 字符串，便于在 L2 Redis 中以字符串形式存储与跨进程传输。
 * <p>
 * JSON 结构约定：
 * <ul>
 * <li>{@code _ID} — 主键值</li>
 * <li>{@code _SV} — 结构版本号</li>
 * <li>{@code _DV} — 数据版本号</li>
 * <li>其余键 — 按 {@link CompiledEntity#getFieldNames()} 顺序排列的业务字段</li>
 * </ul>
 * 字段名以下划线前缀区分元数据字段与业务字段，避免命名冲突。
 * <p>
 * 对标记为 IS_ENCRYPTED 的字段，编码时加密、解码时解密，避免敏感数据明文存入 Redis。
 */
public class CompactRowCodec implements Codec {

  /** JSON 中结构版本号的键名 */
  private static final String KEY_SCHEMA_VERSION = "_SV";
  /** JSON 中数据版本号的键名 */
  private static final String KEY_DATA_VERSION = "_DV";
  /** JSON 中主键的键名 */
  private static final String KEY_ID = "_ID";

  /** 字段加密器，对 IS_ENCRYPTED 标记的字段执行加解密 */
  private final FieldEncryptor fieldEncryptor;

  /**
   * 使用指定加密器构造。
   *
   * @param fieldEncryptor 字段加密器（null 时使用 NoOpFieldEncryptor，不加密）
   */
  public CompactRowCodec(FieldEncryptor fieldEncryptor) {
    if (fieldEncryptor != null) {
      this.fieldEncryptor = fieldEncryptor;
    } else {
      this.fieldEncryptor = new NoOpFieldEncryptor();
    }
  }

  /** 默认构造，使用 NoOpFieldEncryptor（不加密，向后兼容） */
  public CompactRowCodec() {
    this.fieldEncryptor = new NoOpFieldEncryptor();
  }

  /**
   * 将紧凑行编码为 JSON 字符串。
   * <p>
   * 先写入主键，再按实体字段名顺序写入各字段值（加密字段先加密再写入），
   * 最后追加结构版本与数据版本。
   *
   * @param row    紧凑行
   * @param entity 预编译实体，提供字段名顺序与加密标记
   * @return JSON 字符串
   */
  @Override
  public String encode(CompactRow row, CompiledEntity entity) {
    JSONObject json = new JSONObject();
    json.put(KEY_ID, row.getId());
    String[] fieldNames = entity.getFieldNames();
    boolean[] encryptedFlags = entity.getEncryptedFlags();
    if (fieldNames != null) {
      // 按字段名顺序写入对应下标的值，加密字段先加密
      for (int i = 0; i < fieldNames.length; i++) {
        Object value = row.get(i);
        if (encryptedFlags != null && i < encryptedFlags.length && encryptedFlags[i]
            && value != null) {
          // 敏感字段加密后存储
          value = fieldEncryptor.encrypt(String.valueOf(value));
        }
        json.put(fieldNames[i], value);
      }
    }
    json.put(KEY_SCHEMA_VERSION, row.getSchemaVersion());
    json.put(KEY_DATA_VERSION, row.getDataVersion());
    return json.toJSONString();
  }

  /**
   * 将 JSON 字符串解码为紧凑行。
   * <p>
   * 按实体字段名顺序从 JSON 中读取值（加密字段先解密），并解析主键与两个版本号。
   *
   * @param data   JSON 字符串
   * @param entity 预编译实体，提供字段名顺序与加密标记
   * @return 解码后的紧凑行
   */
  @Override
  public CompactRow decode(String data, CompiledEntity entity) {
    JSONObject json = JSONObject.parseObject(data);
    String[] fieldNames = entity.getFieldNames();
    boolean[] encryptedFlags = entity.getEncryptedFlags();
    Object[] values = null;
    if (fieldNames != null) {
      // 按字段名顺序从 JSON 中读取值，加密字段先解密
      values = new Object[fieldNames.length];
      for (int i = 0; i < fieldNames.length; i++) {
        Object value = json.get(fieldNames[i]);
        if (encryptedFlags != null && i < encryptedFlags.length && encryptedFlags[i]
            && value != null) {
          // 敏感字段解密后使用
          value = fieldEncryptor.decrypt(String.valueOf(value));
        }
        values[i] = value;
      }
    }
    Object id = json.get(KEY_ID);
    int schemaVersion = json.getIntValue(KEY_SCHEMA_VERSION);
    int dataVersion = json.getIntValue(KEY_DATA_VERSION);
    return new CompactRow(id, values, schemaVersion, dataVersion);
  }
}
