package com.server.basedata.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 管理接口权限校验器 — 通过 X-Admin-Token 请求头验证管理操作权限。
 * <p>
 * 生产环境应通过配置 {@code basedata.admin-token} 设置强随机 token，
 * 并仅授予运维人员。未配置 token 时拒绝所有管理操作（安全默认）。
 */
@Component
public class AdminTokenValidator {

  /** 配置的管理 token，从 application.yml 读取 */
  @Value("${basedata.admin-token:}")
  private String configuredToken;

  /**
   * 校验请求中的 admin token 是否合法。
   *
   * @param requestToken 请求头 X-Admin-Token 的值
   * @return 合法返回 true；未配置 token 或 token 不匹配返回 false
   */
  public boolean validate(String requestToken) {
    if (configuredToken == null || configuredToken.isEmpty()) {
      // 未配置管理 token，拒绝所有管理操作（安全默认）
      return false;
    }
    return configuredToken.equals(requestToken);
  }
}