/**
 * 登录态管理（真实后端认证：bcd-auth 登录/刷新/登出 + 网关 JWT）。
 *
 * - accessToken/refreshToken/deviceId 持久化 localStorage（key: lowcode:auth）
 * - deviceId 首次访问生成 UUID（多端并存标识，登出仅清当前设备）
 * - permCodes：功能权限码集合（管按钮/界面显隐；'*' 为 ADMIN）
 * - permissions：业务权限元数据（bizCode×action，管业务数据，来自 bcd-code）
 */
import { ref } from 'vue'
import type { PermissionMeta } from '@/types/schema'
import { httpRequest } from '@/utils/request'
import {
  loginRequest,
  refreshRequest,
  logoutRequest,
  fetchMyMenus,
} from '@/api/auth'

const AUTH_KEY = 'lowcode:auth'
const DEVICE_KEY = 'lowcode:deviceId'

/** 业务权限元数据（登录后拉取，驱动渲染层权限过滤：字段/按钮/操作/数据范围） */
export const permissions = ref<PermissionMeta | null>(null)

/** 功能权限码集合（管界面：v-perm / hasPerm） */
export const permCodes = ref<string[]>([])

/** 当前用户菜单树（登录后拉取，驱动 HomeView 动态菜单） */
export const menus = ref<unknown[]>([])

export interface AuthSession {
  username: string
  loginAt: string
  accessToken: string
  refreshToken: string
  userId?: string
  realName?: string
  roles?: string[]
}

/** 多端并存设备标识（UUID，localStorage 持久化） */
export function getDeviceId(): string {
  let id = localStorage.getItem(DEVICE_KEY)
  if (!id) {
    id = 'dev-' + Math.random().toString(36).slice(2) + Date.now().toString(36)
    localStorage.setItem(DEVICE_KEY, id)
  }
  return id
}

export function getSession(): AuthSession | null {
  try {
    const raw = localStorage.getItem(AUTH_KEY)
    return raw ? (JSON.parse(raw) as AuthSession) : null
  } catch {
    return null
  }
}

export function getAccessToken(): string | null {
  return getSession()?.accessToken ?? null
}

export function getRefreshToken(): string | null {
  return getSession()?.refreshToken ?? null
}

export function isLoggedIn(): boolean {
  return !!getAccessToken()
}

export function isAdmin(): boolean {
  const s = getSession()
  return !!s?.roles?.includes('ADMIN')
}

/** 功能权限码判断（'*' 短路放行，与后端 @RequirePerm 的 ADMIN 语义一致） */
export function hasPerm(code: string): boolean {
  if (isAdmin()) return true
  return permCodes.value.includes(code)
}

function saveSession(session: AuthSession): void {
  localStorage.setItem(AUTH_KEY, JSON.stringify(session))
}

/** 登录：失败抛错（错误信息直接展示在登录页） */
export async function login(username: string, password: string): Promise<AuthSession> {
  const name = username.trim()
  if (!name) throw new Error('请输入用户名')
  if (!password) throw new Error('请输入密码')
  const result = await loginRequest(name, password, getDeviceId())
  const session: AuthSession = {
    username: result.user.username,
    userId: result.user.id,
    realName: result.user.realName,
    roles: result.user.roles,
    loginAt: new Date().toISOString(),
    accessToken: result.accessToken,
    refreshToken: result.refreshToken,
  }
  saveSession(session)
  permCodes.value = result.permCodes || []
  await Promise.all([loadPermissions(), loadMenus()])
  return session
}

/** 刷新会话（401 统一出口调用；成功返回新 session） */
export async function refreshSession(): Promise<AuthSession | null> {
  const cur = getSession()
  if (!cur?.refreshToken) return null
  try {
    const result = await refreshRequest(cur.refreshToken, getDeviceId())
    const next: AuthSession = { ...cur, accessToken: result.accessToken, refreshToken: result.refreshToken }
    saveSession(next)
    return next
  } catch {
    clearSession()
    return null
  }
}

/** 登出（仅当前设备）：调后端清会话 + 本地清理 */
export async function logout(): Promise<void> {
  await logoutRequest()
  clearSession()
}

export function clearSession(): void {
  localStorage.removeItem(AUTH_KEY)
  permissions.value = null
  permCodes.value = []
  menus.value = []
}

/**
 * 拉取业务权限元数据（bcd-code /api/lowcode/auth/permissions）。
 * 失败静默，permissions 保持 null（权限检查函数统一放行，后端兜底）。
 */
export async function loadPermissions(): Promise<void> {
  try {
    const res = await httpRequest('/api/lowcode/auth/permissions', { method: 'GET' })
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

/** 拉取当前用户菜单树（登录后/守卫注册动态路由时） */
export async function loadMenus(): Promise<unknown[]> {
  try {
    menus.value = await fetchMyMenus()
  } catch {
    menus.value = []
  }
  return menus.value
}

/** 页面刷新后恢复（main.ts 调用）：总是重拉菜单（侧边栏 RBAC 依赖），权限元数据按需 */
export function restoreSession(): void {
  const s = getSession()
  if (!s?.userId) {
    return
  }
  if (permCodes.value.length === 0) {
    loadPermissions().catch(() => undefined)
  }
  if (!menus.value || menus.value.length === 0) {
    loadMenus().catch(() => undefined)
  }
}