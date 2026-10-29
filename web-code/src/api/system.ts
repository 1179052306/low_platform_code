/**
 * 系统管理 API（bcd-auth :8010，经网关 :8000 /api/sys/**）
 */
import { httpRequest, extractPayload } from '@/utils/request'


interface SysResponse {
  res?: boolean
  code?: string
  msg?: string
  data?: unknown
}

async function sysCall(url: string, method: string, body?: unknown): Promise<unknown> {
  const res = await httpRequest(url, {
    method,
    body: body === undefined ? undefined : JSON.stringify(body),
  })
  const bodyObj = (res.data || {}) as SysResponse
  if (!res.ok || bodyObj.res === false) {
    throw new Error(bodyObj.msg || `请求失败(${res.status})`)
  }
  return bodyObj.data
}

// ================ 用户管理 ================

export interface SysUser {
  id: string
  username: string
  realName?: string
  email?: string
  mobile?: string
  status: string
  lastLoginAt?: string
  createdAt?: string
  roleCodes?: string
}

export function listUsers(keyword: string, page: number, size: number) {
  const kw = encodeURIComponent(keyword || '')
  return sysCall(`/api/sys/user/list?keyword=${kw}&page=${page}&size=${size}`, 'GET') as Promise<{ data: SysUser[]; totalCount: number }>
}

export function createUser(body: Record<string, unknown>): Promise<unknown> {
  return sysCall('/api/sys/user/create', 'POST', body)
}

export function updateUser(body: Record<string, unknown>): Promise<unknown> {
  return sysCall('/api/sys/user/update', 'POST', body)
}

export function changeUserStatus(id: string, status: string): Promise<unknown> {
  return sysCall('/api/sys/user/changeStatus', 'POST', { id, status })
}

export function deleteUser(id: string): Promise<unknown> {
  return sysCall('/api/sys/user/delete', 'POST', { id })
}

export function resetUserPassword(id: string): Promise<string> {
  return sysCall('/api/sys/user/resetPassword', 'POST', { id }) as Promise<string>
}

export function assignUserRoles(id: string, roleCodes: string[]): Promise<unknown> {
  return sysCall('/api/sys/user/assignRoles', 'POST', { id, roleCodes })
}

// ================ 角色管理 ================

export interface SysRole {
  code: string
  name?: string
  enabled?: boolean
  createdAt?: string
  userCount?: number
  menuCount?: number
}

export function listRoles(): Promise<SysRole[]> {
  return sysCall('/api/sys/role/list', 'GET') as Promise<SysRole[]>
}

export function saveRole(body: Record<string, unknown>): Promise<unknown> {
  return sysCall('/api/sys/role/save', 'POST', body)
}

export function deleteRole(code: string): Promise<unknown> {
  return sysCall('/api/sys/role/delete', 'POST', { code })
}

export function roleMenus(roleCode: string): Promise<string[]> {
  return sysCall(`/api/sys/role/menus?roleCode=${encodeURIComponent(roleCode)}`, 'GET') as Promise<string[]>
}

export function assignRoleMenus(roleCode: string, menuIds: string[]): Promise<unknown> {
  return sysCall('/api/sys/role/assignMenus', 'POST', { roleCode, menuIds })
}

export function roleUsers(roleCode: string): Promise<SysUser[]> {
  return sysCall(`/api/sys/role/users?roleCode=${encodeURIComponent(roleCode)}`, 'GET') as Promise<SysUser[]>
}

// ================ 菜单管理 ================

export interface SysMenu {
  id: string
  parentId?: string
  menuType: 'dir' | 'menu' | 'button'
  menuName: string
  nameKey?: string
  menuCode: string
  routePath?: string
  component?: string
  permissionCode?: string
  icon?: string
  sortNo?: number
  visible?: boolean
  status?: string
  children?: SysMenu[]
}

export function menuTree(): Promise<SysMenu[]> {
  return sysCall('/api/sys/menu/tree', 'GET') as Promise<SysMenu[]>
}

export function saveMenu(body: Record<string, unknown>): Promise<unknown> {
  return sysCall('/api/sys/menu/save', 'POST', body)
}

export function deleteMenu(id: string): Promise<unknown> {
  return sysCall('/api/sys/menu/delete', 'POST', { id })
}

// ================ 在线会话 / 操作日志 ================

export interface OnlineSession {
  jti: string
  uid: string
  username: string
  deviceId: string
  ip?: string
  ua?: string
  loginAt: string
}

export function onlineList(): Promise<OnlineSession[]> {
  return sysCall('/api/sys/online/list', 'GET') as Promise<OnlineSession[]>
}

export function kickSession(userId: string, jti?: string): Promise<unknown> {
  return sysCall('/api/sys/online/kick', 'POST', { userId, jti })
}

export interface OpLog {
  id: string
  userId: string
  username?: string
  module: string
  action: string
  paramsDigest?: string
  success: boolean
  message?: string
  ip?: string
  operatedAt: string
}

export function opLogList(page: number, size: number): Promise<OpLog[]> {
  return sysCall(`/api/sys/oplog/list?page=${page}&size=${size}`, 'GET') as Promise<OpLog[]>
}

// ================ 已配置页面（菜单 component 双模式 page:{pageId} 选择用） ================

export interface PageBrief {
  id: string
  name: string
}

export async function listPagesForMenu(): Promise<PageBrief[]> {
  const res = await httpRequest('/api/lowcode/page/list', { method: 'GET' })
  if (!res.ok) return []
  const { data } = extractPayload(res.data)
  return data as PageBrief[]
}