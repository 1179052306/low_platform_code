package com.server.basedata.protection;

/**
 * 熔断器，用于下游故障隔离。
 * <p>
 * 采用三态模型：CLOSED（正常放行）、OPEN（熔断拒绝）、HALF_OPEN（半开放行试探）。
 * 当连续失败次数达到阈值时进入 OPEN；OPEN 状态经过恢复时间后进入 HALF_OPEN 放行一次试探，
 * 试探成功则回到 CLOSED，失败则重回 OPEN。避免下游持续故障时反复重试造成级联雪崩。
 * <p>
 * 状态与计数使用 volatile 保证可见性，适用于单实例场景。
 */
public class CircuitBreaker {

  /**
   * 熔断状态枚举。
   */
  public enum State {
    /** 闭合：正常放行请求 */
    CLOSED,
    /** 打开：熔断拒绝请求 */
    OPEN,
    /** 半开：放行试探请求 */
    HALF_OPEN
  }

  /** 当前状态 */
  private volatile State state = State.CLOSED;
  /** 进入 OPEN 的失败次数阈值 */
  private final int failureThreshold;
  /** OPEN 到 HALF_OPEN 的恢复等待时间（毫秒） */
  private final long recoveryTimeoutMs;
  /** 当前连续失败次数 */
  private volatile int failureCount = 0;
  /** 最近一次失败时间戳（毫秒） */
  private volatile long lastFailureTimeMs = 0;

  /**
   * 构造熔断器。
   *
   * @param failureThreshold  失败次数阈值
   * @param recoveryTimeoutMs 恢复等待时间（毫秒）
   */
  public CircuitBreaker(int failureThreshold, long recoveryTimeoutMs) {
    this.failureThreshold = failureThreshold;
    this.recoveryTimeoutMs = recoveryTimeoutMs;
  }

  /**
   * 判断当前是否允许放行请求。
   * <p>
   * CLOSED 直接放行；OPEN 在恢复时间到达后切换到 HALF_OPEN 并放行试探，否则拒绝；
   * HALF_OPEN 放行。
   *
   * @return 允许放行返回 true，拒绝返回 false
   */
  public boolean allowRequest() {
    boolean result;
    State currentState = state;
    if (currentState == State.CLOSED) {
      // 正常状态，放行
      result = true;
    } else if (currentState == State.OPEN) {
      // 熔断状态，判断是否到恢复时间
      long elapsed = System.currentTimeMillis() - lastFailureTimeMs;
      if (elapsed >= recoveryTimeoutMs) {
        // 进入半开，放行试探
        state = State.HALF_OPEN;
        result = true;
      } else {
        // 未到恢复时间，拒绝
        result = false;
      }
    } else {
      // 半开状态，放行试探
      result = true;
    }
    return result;
  }

  /**
   * 记录一次成功：重置失败计数并回到 CLOSED。
   */
  public void recordSuccess() {
    failureCount = 0;
    state = State.CLOSED;
  }

  /**
   * 记录一次失败：累加失败计数，达到阈值时进入 OPEN。
   */
  public void recordFailure() {
    int count = failureCount + 1;
    failureCount = count;
    lastFailureTimeMs = System.currentTimeMillis();
    if (count >= failureThreshold) {
      // 达到阈值，熔断
      state = State.OPEN;
    }
  }

  /**
   * 获取当前状态。
   *
   * @return 当前状态
   */
  public State getState() {
    return state;
  }

  /**
   * 获取当前失败次数。
   *
   * @return 失败次数
   */
  public int getFailureCount() {
    return failureCount;
  }

  /**
   * 重置熔断器到初始 CLOSED 状态。
   */
  public void reset() {
    state = State.CLOSED;
    failureCount = 0;
    lastFailureTimeMs = 0;
  }
}
