/**
 * 登录态管理（后台登录接口就绪前的本地会话形态）。
 *
 * 【后台切换位】login 内部当前为本地校验（演示账号）。后台就绪后替换为：
 *   POST /api/auth/login { username, password }
 *   → 200 { token, username, displayName, roles }
 * 成功后本函数存 token，httpRequest 统一带 Authorization 头，签名不变。
 * 登录成功后调用 loadPermissions() 拉取权限元数据，驱动渲染层权限过滤（G3）。
 */
import { ref } from 'vue'
import type { PermissionMeta } from '@/types/schema'
import { httpRequest } from '@/utils/request'

const AUTH_KEY = 'lowcode:auth'

/** 权限元数据（登录后拉取，驱动渲染层权限过滤：字段/按钮/操作/数据范围） */
export const permissions = ref<PermissionMeta | null>(null)

/**
 * 拉取权限元数据（后端 /api/auth/permissions 就绪后调用）。
 * 后端未就绪时静默失败，permissions 保持 null（权限检查函数统一放行，后端兜底）。
 */
export async function loadPermissions(): Promise<void> {
  try {
    const res = await httpRequest('/api/auth/permissions', { method: 'GET' })
    if (!res.ok) return
    const raw = res.data as { data?: PermissionMeta } | PermissionMeta | undefined
    if (raw && typeof raw === 'object' && 'data' in raw && raw.data) {
      permissions.value = raw.data
    } else if (raw && typeof raw === 'object') {
      permissions.value = raw as PermissionMeta
    }
  } catch {
    permissions.value = null
  }
}

/** 演示账号（后台接口就绪前的本地校验凭据） */
const DEMO_USERNAME = 'admin'
const DEMO_PASSWORD = '123456'

export interface AuthSession {
  username: string
  loginAt: string
  token?: string
}

export function getSession(): AuthSession | null {
  try {
    const raw = localStorage.getItem(AUTH_KEY)
    return raw ? (JSON.parse(raw) as AuthSession) : null
  } catch {
    return null
  }
}

export function isLoggedIn(): boolean {
  return !!getSession()
}

/** 登录：失败抛错（错误信息直接展示在登录页） */
export function login(username: string, password: string): AuthSession {
  const name = username.trim()
  if (!name) throw new Error('请输入用户名')
  if (!password) throw new Error('请输入密码')
  if (name !== DEMO_USERNAME || password !== DEMO_PASSWORD) {
    throw new Error('用户名或密码错误')
  }
  const session: AuthSession = {
    username: name,
    loginAt: new Date().toISOString(),
  }
  localStorage.setItem(AUTH_KEY, JSON.stringify(session))
  return session
}

export function logout(): void {
  localStorage.removeItem(AUTH_KEY)
  permissions.value = null
}