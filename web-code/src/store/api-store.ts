/**
 * API 服务注册表持久化层
 *
 * 直接与后端 API 交互，不使用 localStorage 缓存。
 * 所有操作均为异步，后端不可用时操作失败并抛错。
 */
import type { ApiService } from '@/api/registry'
import { registerApi } from '@/api/registry'
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
