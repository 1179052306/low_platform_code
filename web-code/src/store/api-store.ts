/**
 * API 服务注册表持久化层
 *
 * 直接与后端 API 交互，不使用 localStorage 缓存。
 * 所有操作均为异步，后端不可用时操作失败并抛错。
 */
import type { ApiService } from '@/api/registry'
import { registerApi } from '@/api/registry'
import type { GatewayPermissionRule, FieldPermissionRule } from '@/types/schema'
import type { DataDict, DataDictItem } from '@/types/dict'
import { httpRequest } from '@/utils/request'
import { t } from '@/store/lang'

/** 统一 API 调用：检查响应状态，失败时抛错 */
async function apiCall(
  url: string,
  options: { method?: string; body?: string } = {},
): Promise<unknown> {
  const res = await httpRequest(url, {
    method: options.method || 'POST',
    body: options.body,
  })
  if (!res.ok) {
    throw new Error(t('后端连接异常'))
  }
  const body = res.data as { res?: boolean; msg?: string; data?: unknown }
  if (body && body.res === false) {
    throw new Error(t(body.msg || '操作失败'))
  }
  return body?.data
}

/** 列出所有 API 服务 */
export async function listApiServices(): Promise<ApiService[]> {
  const data = await apiCall('/api/lowcode/api-registry/list', { method: 'POST' })
  return (data as ApiService[]) || []
}

/** 新增 API 服务（id 重复则抛错） */
export async function addApiService(service: ApiService): Promise<void> {
  const list = await listApiServices()
  if (list.some((s) => s.id === service.id)) {
    throw new Error(`API 服务 ID 已存在: ${service.id}`)
  }
  list.push(service)
  await apiCall('/api/lowcode/api-registry', { body: JSON.stringify(list) })
  registerApi(service)
}

/** 更新 API 服务 */
export async function updateApiService(service: ApiService): Promise<void> {
  const list = await listApiServices()
  const idx = list.findIndex((s) => s.id === service.id)
  if (idx < 0) throw new Error(`API 服务不存在: ${service.id}`)
  list[idx] = service
  await apiCall('/api/lowcode/api-registry', { body: JSON.stringify(list) })
  registerApi(service)
}

/** 启动时加载所有 API 到内存注册表 */
export async function loadApiRegistry(): Promise<void> {
  const list = await listApiServices()
  for (const s of list) registerApi(s)
}
// ================ 网关权限矩阵 + 字段权限管理（FE-S9） ================

/** 查询网关权限矩阵列表 */
export async function listGatewayPermissions(): Promise<GatewayPermissionRule[]> {
  const data = await apiCall('/api/lowcode/permission/gateway/list', { method: 'POST' })
  return (data as GatewayPermissionRule[]) || []
}

/** 保存网关权限规则（新增或更新） */
export async function saveGatewayPermission(rule: GatewayPermissionRule): Promise<void> {
  await apiCall('/api/lowcode/permission/gateway/save', {
    body: JSON.stringify(rule),
  })
}

/** 删除网关权限规则 */
export async function deleteGatewayPermission(id: string): Promise<void> {
  await apiCall('/api/lowcode/permission/gateway/delete', {
    body: JSON.stringify({ id }),
  })
}

/** 查询字段权限列表 */
export async function listFieldPermissions(): Promise<FieldPermissionRule[]> {
  const data = await apiCall('/api/lowcode/permission/field/list', { method: 'POST' })
  return (data as FieldPermissionRule[]) || []
}

/** 保存字段权限规则（新增或更新） */
export async function saveFieldPermission(rule: FieldPermissionRule): Promise<void> {
  await apiCall('/api/lowcode/permission/field/save', {
    body: JSON.stringify(rule),
  })
}

/** 删除字段权限规则 */
export async function deleteFieldPermission(id: string): Promise<void> {
  await apiCall('/api/lowcode/permission/field/delete', {
    body: JSON.stringify({ id }),
  })
}

/** 查询角色列表（权限配置选择用） */
export async function listRoles(): Promise<{ code: string; name: string }[]> {
  const data = await apiCall('/api/lowcode/role/list', { method: 'POST' })
  return (data as { code: string; name: string }[]) || []
}
// ================ 数据字典管理（FE-S10 P2） ================

/** 查询数据字典列表 */
export async function listDataDicts(): Promise<DataDict[]> {
  const data = await apiCall('/api/lowcode/dict/list', { method: 'POST' })
  return (data as DataDict[]) || []
}

/** 保存数据字典 */
export async function saveDataDict(dict: DataDict): Promise<void> {
  await apiCall('/api/lowcode/dict/save', { body: JSON.stringify(dict) })
}

/** 删除数据字典 */
export async function deleteDataDict(code: string): Promise<void> {
  await apiCall('/api/lowcode/dict/delete', { body: JSON.stringify({ code }) })
}

/** 查询字典项列表 */
export async function listDictItems(dictCode: string): Promise<DataDictItem[]> {
  const data = await apiCall('/api/lowcode/dict/item/list', {
    body: JSON.stringify({ dictCode }),
  })
  return (data as DataDictItem[]) || []
}

/** 保存字典项 */
export async function saveDictItem(item: DataDictItem): Promise<void> {
  await apiCall('/api/lowcode/dict/item/save', { body: JSON.stringify(item) })
}

/** 删除字典项 */
export async function deleteDictItem(id: string): Promise<void> {
  await apiCall('/api/lowcode/dict/item/delete', { body: JSON.stringify({ id }) })
}
