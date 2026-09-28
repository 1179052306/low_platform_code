package com.server.basedata.version;

import com.server.basedata.metadata.MetadataCompiler;
import com.server.basedata.metadata.MetadataLoader;
import com.server.basedata.metadata.RuntimeMetadata;
import com.server.basedata.metadata.VersionManager;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Schema 版本管理器。
 * <p>
 * 编排元数据的加载→编译→发布流程，并维护上一版本快照以支持回滚。版本号通过自增计数器生成，
 * 每次发布新版本时将旧版本存入 previous，发布失败或需回滚时可恢复到上一版本。
 */
public class SchemaVersionManager {

  /** 元数据加载器 */
  private final MetadataLoader loader;
  /** 元数据编译器 */
  private final MetadataCompiler compiler;
  /** 版本管理器（持有当前生效元数据） */
  private final VersionManager versionManager;
  /** 上一版本元数据快照，用于回滚 */
  private final AtomicReference<RuntimeMetadata> previous = new AtomicReference<RuntimeMetadata>();
  /** 版本号自增计数器 */
  private final AtomicLong versionCounter = new AtomicLong(0);

  /**
   * 构造版本管理器。
   *
   * @param loader         元数据加载器
   * @param compiler       元数据编译器
   * @param versionManager 版本管理器
   */
  public SchemaVersionManager(MetadataLoader loader, MetadataCompiler compiler,
      VersionManager versionManager) {
    this.loader = loader;
    this.compiler = compiler;
    this.versionManager = versionManager;
  }

  /**
   * 加载并发布新版本元数据。
   * <p>
   * 流程：加载原始元数据 → 自增版本号 → 编译 → 保存旧版本到 previous → 替换当前版本。
   *
   * @return 发布成功返回 true
   * @throws Exception 加载或编译异常
   */
  public boolean publishNewMetadata() throws Exception {
    boolean result = false;
    MetadataLoader.RawMetadata raw = loader.load();
    long newVersion = versionCounter.incrementAndGet();
    RuntimeMetadata newMetadata = compiler.compile(raw, newVersion);
    // 保存当前版本作为回滚点
    RuntimeMetadata old = versionManager.getCurrent();
    previous.set(old);
    versionManager.swap(newMetadata);
    result = true;
    return result;
  }

  /**
   * 回滚到上一版本。
   *
   * @return 回滚成功返回 true；无上一版本返回 false
   */
  public boolean rollback() {
    boolean result = false;
    RuntimeMetadata prev = previous.get();
    if (prev != null) {
      versionManager.swap(prev);
      result = true;
    }
    return result;
  }

  /**
   * 获取当前版本号。
   *
   * @return 当前版本号；尚未发布返回 0
   */
  public long getCurrentVersion() {
    long result;
    RuntimeMetadata current = versionManager.getCurrent();
    if (current == null) {
      result = 0;
    } else {
      result = current.getVersion();
    }
    return result;
  }

  /**
   * 获取当前生效的运行时元数据。
   *
   * @return 当前元数据；尚未发布返回 null
   */
  public RuntimeMetadata getCurrentMetadata() {
    return versionManager.getCurrent();
  }

  /**
   * 判断元数据是否已就绪。
   *
   * @return 已发布返回 true
   */
  public boolean isReady() {
    return versionManager.isReady();
  }
}
