/**
 * 认证 API（bcd-auth :8010，经网关 :8000 /api/auth/**）
 * 错误语义契约：认证/授权错误走 HTTP 401/403；业务错误沿用 ResMsg（res=false）
 */
import { httpRequest } from '@/utils/request'

export interface LoginResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: {
    id: string
    username: string
    realName?: string
    roles: string[]
  }
  permCodes: string[]
}

export interface RefreshResult {
  accessToken: string
  refreshToken: string
  expiresIn: number
}

export interface LoginResponseData {
  res?: boolean
  code?: string
  msg?: string
  data?: LoginResult
}

export async function loginRequest(
  username: string,
  password: string,
  deviceId: string
): Promise<LoginResult> {
  const res = await httpRequest('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password, deviceId }),
  })
  const body = (res.data || {}) as LoginResponseData
  if (!res.ok || body.res === false || !body.data) {
    throw new Error(body.msg || '登录失败')
  }
  return body.data
}

export async function refreshRequest(
  refreshToken: string,
  deviceId: string
): Promise<RefreshResult> {
  const res = await httpRequest('/api/auth/refresh', {
    method: 'POST',
    body: JSON.stringify({ refreshToken, deviceId }),
  })
  const body = (res.data || {}) as { res?: boolean; msg?: string; data?: RefreshResult }
  if (!res.ok || body.res === false || !body.data) {
    throw new Error(body.msg || '刷新失败')
  }
  return body.data
}

export async function logoutRequest(): Promise<void> {
  try {
    await httpRequest('/api/auth/logout', { method: 'POST' })
  } catch {
    // 登出失败不阻塞本地清理
  }
}

/** 当前用户菜单树（后端已按角色过滤；nameKey 供 t() 多语言渲染） */
export async function fetchMyMenus(): Promise<unknown[]> {
  const res = await httpRequest('/api/sys/profile/menus', { method: 'GET' })
  if (!res.ok) return []
  const body = res.data as { data?: unknown[] }
  return Array.isArray(body?.data) ? body.data : []
}