package com.server.basedata.protection;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 单飞合并器（SingleFlight），用于缓存击穿防护。
 * <p>
 * 当多个线程并发请求同一 key 时，仅允许首个线程执行实际加载，其余线程等待其结果复用，
 * 避免大量并发请求同时穿透到数据库造成压力骤增。基于 {@link ConcurrentHashMap} +
 * {@link CompletableFuture} 实现，加载完成后移除在途记录，允许后续请求重新触发加载。
 *
 * @param <T> 加载结果类型
 */
public class SingleFlight<T> {

  /** 在途请求映射：key → 加载 Future */
  private final ConcurrentHashMap<String, CompletableFuture<T>> flights = new ConcurrentHashMap<String, CompletableFuture<T>>();

  /**
   * 按 key 执行加载，并发请求合并为单次实际执行。
   * <p>
   * 若 key 已有在途请求，则等待其结果；否则尝试注册新 Future 并执行 supplier。
   * 通过 putIfAbsent 保证仅一个线程成为执行者，其余线程 join 到已注册的 Future。
   *
   * @param key      合并键
   * @param supplier 实际加载逻辑
   * @return 加载结果
   */
  public T execute(String key, Supplier<T> supplier) {
    CompletableFuture<T> existing = flights.get(key);
    T result;
    if (existing != null) {
      // 已有在途请求，直接等待复用
      result = existing.join();
    } else {
      // 尝试注册新的在途 Future
      CompletableFuture<T> newFuture = new CompletableFuture<T>();
      CompletableFuture<T> actual = flights.putIfAbsent(key, newFuture);
      if (actual != null) {
        // 并发竞争失败，复用已注册的 Future
        result = actual.join();
      } else {
        // 竞争成功，执行实际加载
        try {
          T value = supplier.get();
          newFuture.complete(value);
          result = value;
        } catch (RuntimeException e) {
          // 异常传播给等待方
          newFuture.completeExceptionally(e);
          throw e;
        } finally {
          // 加载完成（无论成功失败）后移除在途记录
          flights.remove(key, newFuture);
        }
      }
    }
    return result;
  }

  /**
   * 判断指定 key 是否有在途请求。
   *
   * @param key 合并键
   * @return 在途返回 true
   */
  public boolean isInFlight(String key) {
    return flights.containsKey(key);
  }

  /**
   * 获取当前在途请求数量。
   *
   * @return 在途请求数
   */
  public int getInFlightCount() {
    return flights.size();
  }
}
