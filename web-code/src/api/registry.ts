/**
 * API 服务注册表
 *
 * 职责：
 * - 集中声明所有后端 API（id / url / method / permCode），作为唯一地址来源
 * - 提供白名单校验（URL 是否在注册表内）—— 治"任意 URL 请求 / 数据外泄 / SSRF"
 * - 提供权限校验（permCode 是否有权限）—— 治"垂直越权"
 * - 供属性面板 serviceId 选择器（listApis）和事件 api.call(serviceId)（resolveApi）使用
 *
 * 数据来源：
 * - 可前端硬编码（registerApi/registerApis）
 * - 可后端下发（运行时 fetch 注册表后 registerApis）
 * - 可管理界面维护（后续扩展）
 */

/** API 服务声明 */
export interface ApiService {
  /** 服务 ID（唯一标识，调用方用这个引用，如 'order.list'） */
  id: string
  /** 后端 URL（路径或完整 URL，如 '/api/order/list'） */
  url: string
  /** 请求方法 */
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'
  /** 权限码（垂直越权校验用，如 'order:view'） */
  permCode?: string
  /** 描述（供属性面板选择器展示） */
  description?: string
  /** 分组（供属性面板选择器分组展示） */
  group?: string
}

const _registry = new Map<string, ApiService>()

/** 注册单个 API 服务 */
export function registerApi(service: ApiService): void {
  _registry.set(service.id, service)
}

/** 批量注册 API 服务 */
export function registerApis(services: ApiService[]): void {
  for (const s of services) _registry.set(s.id, s)
}

/** 按 ID 获取 API 服务 */
export function getApi(id: string): ApiService | undefined {
  return _registry.get(id)
}

/** 列出所有已注册的 API 服务（供属性面板 serviceId 选择器） */
export function listApis(): ApiService[] {
  return Array.from(_registry.values())
}

/**
 * 按 ID 解析出实际 URL 和方法（供 api.call(serviceId) 用）
 * 未注册时抛错（强治理：调用方必须引用已注册的服务）
 */
export function resolveApi(id: string): {
  url: string
  method: string
  service: ApiService
} {
  const s = _registry.get(id)
  if (!s) throw new Error(`[API 治理] 未注册的服务 ID: ${id}`)
  return { url: s.url, method: s.method || 'GET', service: s }
}

// ---- URL 路径提取（用于白名单匹配，忽略 host/protocol） ----

/** 从 URL 中提取 path 部分，用于白名单匹配 */
export function extractPath(url: string): string {
  try {
    const u = new URL(url, window.location.origin)
    return u.pathname
  } catch {
    return url
  }
}

// ---- 治理模式 ----

export type GovernanceMode = 'off' | 'warn' | 'strict'

let _mode: GovernanceMode = 'off'

/**
 * 设置治理模式：
 * - off：不校验（默认，兼容现状）
 * - warn：白名单外放行但 console.warn（过渡模式）
 * - strict：白名单外拒绝抛错（强治理）
 */
export function setGovernanceMode(mode: GovernanceMode): void {
  _mode = mode
}

export function getGovernanceMode(): GovernanceMode {
  return _mode
}

// ---- 白名单校验 ----

/** 检查 URL 是否在白名单内（path 部分匹配注册表里某个 service） */
export function isUrlAllowed(url: string): boolean {
  if (_registry.size === 0) return true
  const targetPath = extractPath(url)
  for (const s of _registry.values()) {
    if (extractPath(s.url) === targetPath) return true
  }
  return false
}

/** 查找 URL 对应的 service（用于权限校验） */
export function findServiceByUrl(url: string): ApiService | undefined {
  const targetPath = extractPath(url)
  for (const s of _registry.values()) {
    if (extractPath(s.url) === targetPath) return s
  }
  return undefined
}

// ---- 权限校验 ----

/** 权限校验函数类型：传入 permCode，返回是否有权限 */
export type PermissionChecker = (permCode: string) => boolean

let _permChecker: PermissionChecker | null = null

/** 设置权限校验函数（从登录态取用户权限列表） */
export function setPermissionChecker(fn: PermissionChecker | null): void {
  _permChecker = fn
}

/** 校验 URL 的权限（返回错误信息，无错误返回 null） */
export function checkUrlPermission(url: string): string | null {
  if (!_permChecker) return null
  const service = findServiceByUrl(url)
  if (!service || !service.permCode) return null
  if (!_permChecker(service.permCode)) {
    return `无权限访问: ${service.permCode}`
  }
  return null
}

// ---- 治理统计（供调试/审计） ----

let _blockedCount = 0
let _warnedCount = 0

export function getGovernanceStats(): {
  blocked: number
  warned: number
} {
  return { blocked: _blockedCount, warned: _warnedCount }
}

/** 内部：记录拦截/告警计数（供 index.ts 的拦截器调用） */
export function _recordBlocked(): void {
  _blockedCount++
}
export function _recordWarned(): void {
  _warnedCount++
}