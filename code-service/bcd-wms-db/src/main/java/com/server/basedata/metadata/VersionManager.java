package com.server.basedata.metadata;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 元数据版本管理器。
 * <p>
 * 以无锁方式持有当前生效的 {@link RuntimeMetadata}，通过 {@link AtomicReference}
 * 实现元数据的热切换：编译出新版本后原子替换引用，读侧无需加锁即可拿到一致快照。
 * <p>
 * 支持普通替换与 CAS 替换两种方式，CAS 用于并发场景下避免覆盖更新的版本。
 */
public class VersionManager {

  /** 当前生效的运行时元数据 */
  private final AtomicReference<RuntimeMetadata> current = new AtomicReference<RuntimeMetadata>();

  /**
   * 获取当前生效的运行时元数据。
   *
   * @return 当前元数据；尚未初始化时返回 null
   */
  public RuntimeMetadata getCurrent() {
    return current.get();
  }

  /**
   * 无条件替换当前元数据。
   * <p>
   * 适用于单写者或确认不会覆盖更新的场景。
   *
   * @param newMetadata 新元数据
   */
  public void swap(RuntimeMetadata newMetadata) {
    current.set(newMetadata);
  }

  /**
   * CAS 替换当前元数据。
   * <p>
   * 仅当当前引用与 expected 相同时才替换为 update，避免并发覆盖。
   *
   * @param expected 期望的旧元数据
   * @param update   新元数据
   * @return 替换成功返回 true，否则返回 false
   */
  public boolean compareAndSwap(RuntimeMetadata expected, RuntimeMetadata update) {
    return current.compareAndSet(expected, update);
  }

  /**
   * 判断元数据是否已就绪。
   *
   * @return 已加载返回 true，尚未初始化返回 false
   */
  public boolean isReady() {
    return current.get() != null;
  }
}
