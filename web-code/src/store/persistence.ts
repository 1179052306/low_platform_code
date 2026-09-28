/**
 * 页面元数据持久化存储层。
 * 直接与后端 API 交互，不使用 localStorage 缓存。
 * 所有操作均为异步，后端不可用时操作失败并抛错。
 */
import type { PageSchema, ComponentSchema, PageType } from '@/types/schema'
import { httpRequest } from '@/utils/request'
import { t } from '@/store/lang'

/** 已保存页面的摘要信息（用于列表展示） */
export interface SavedPageMeta {
  pageId: string
  pageName: string
  systemId?: string
  moduleId?: string
  /** 页面业务类型：document=单据，basedata=基础资料 */
  pageType?: PageType
  savedAt: string
  /** 是否启用 */
  enabled: boolean
  /** 软删除时间戳（ISO 字符串），未删除则无此字段 */
  deletedAt?: string
  /** 后端建表同步结果消息（null=未执行建表，有值=同步结果或警告） */
  tableSyncMsg?: string
}

/** 模块（页面分类）信息，支持树形结构 */
export interface ModuleInfo {
  id: string
  name: string
  sort: number
  /** 父模块 ID，根模块无此字段 */
  parentId?: string
}

/** 系统信息（最高层级分类） */
export interface SystemInfo {
  id: string
  name: string
  sort: number
}

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

/** 递归清理节点中不需要保存的属性（如 DataGrid 静态数据源） */
function cleanNodeForSave(nodes: ComponentSchema[]): ComponentSchema[] {
  return nodes.map((node) => {
    const cleaned: ComponentSchema = { ...node }
    if (cleaned.type === 'dx-data-grid' && cleaned.props) {
      const props = { ...cleaned.props }
      if (props.dataSourceType === 'static' && props.dataSource) {
        props.dataSource = []
      }
      cleaned.props = props
    }
    if (cleaned.children && cleaned.children.length) {
      cleaned.children = cleanNodeForSave(cleaned.children)
    }
    return cleaned
  })
}

// ================ 系统管理 ================

/** 列出所有系统（保证至少有一个默认系统） */
export async function listSystems(): Promise<SystemInfo[]> {
  const data = await apiCall('/api/lowcode/system/list', { method: 'POST' })
  const systems = (data as SystemInfo[]) || []
  return systems.sort((a, b) => a.sort - b.sort)
}

/** 保存系统（新增或更新） */
export async function saveSystem(system: SystemInfo): Promise<void> {
  await apiCall('/api/lowcode/system', { body: JSON.stringify(system) })
}

/** 新增系统 */
export async function addSystem(name: string): Promise<SystemInfo> {
  const systems = await listSystems()
  const system: SystemInfo = {
    id: `sys_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 6)}`,
    name: name.trim() || '新系统',
    sort: systems.length,
  }
  await saveSystem(system)
  return system
}

/** 重命名系统 */
export async function renameSystem(id: string, name: string): Promise<void> {
  const systems = await listSystems()
  const s = systems.find((x) => x.id === id)
  if (s) {
    s.name = name.trim() || s.name
    await saveSystem(s)
  }
}

/** 删除系统 */
export async function deleteSystem(id: string): Promise<void> {
  if (id === 'sys_default') return
  await apiCall(`/api/lowcode/system/${id}`, { method: 'DELETE' })
}

// ================ 模块管理 ================

/** 列出所有模块（保证至少有一个默认模块） */
export async function listModules(): Promise<ModuleInfo[]> {
  const data = await apiCall('/api/lowcode/module/tree', { method: 'POST' })
  const modules = (data as ModuleInfo[]) || []
  return modules.sort((a, b) => a.sort - b.sort)
}

/** 保存模块（新增或更新） */
export async function saveModule(module: ModuleInfo): Promise<void> {
  await apiCall('/api/lowcode/module', { body: JSON.stringify(module) })
}

/** 新增模块（支持指定父模块，构建树形结构） */
export async function addModule(
  name: string,
  parentId?: string,
): Promise<ModuleInfo> {
  const modules = await listModules()
  const module: ModuleInfo = {
    id: `mod_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 6)}`,
    name: name.trim() || '新模块',
    sort: modules.length,
    parentId,
  }
  await saveModule(module)
  return module
}

/** 重命名模块 */
export async function renameModule(id: string, name: string): Promise<void> {
  const modules = await listModules()
  const m = modules.find((x) => x.id === id)
  if (m) {
    m.name = name.trim() || m.name
    await saveModule(m)
  }
}

/** 删除模块：后端递归删除子模块，其下页面移至默认模块 */
export async function deleteModule(id: string): Promise<void> {
  if (id === 'mod_default') return
  await apiCall(`/api/lowcode/module/${id}`, { method: 'DELETE' })
}

// ================ 页面管理 ================

/** 保存页面元数据 */
export async function savePage(schema: PageSchema): Promise<SavedPageMeta> {
  const cleanedSchema: PageSchema = {
    ...schema,
    root: cleanNodeForSave(schema.root),
  }
  const data = await apiCall('/api/lowcode/page', { body: JSON.stringify(cleanedSchema) }) as
    { pageId?: string; savedAt?: string; tableSyncMsg?: string } | undefined
  return {
    pageId: data?.pageId || schema.pageId,
    pageName: schema.pageName,
    moduleId: schema.moduleId,
    pageType: schema.pageType,
    savedAt: data?.savedAt || new Date().toISOString(),
    enabled: schema.enabled !== false,
    tableSyncMsg: data?.tableSyncMsg,
  }
}

/** 按 pageId 加载页面元数据 */
export async function loadPage(pageId: string): Promise<PageSchema | null> {
  try {
    const data = await apiCall(`/api/lowcode/page/${pageId}`, { method: 'POST' })
    return (data as PageSchema) ?? null
  } catch {
    return null
  }
}

/** 加载最近一次保存的页面 */
export async function loadLatestPage(): Promise<PageSchema | null> {
  const pages = await listSavedPages()
  if (!pages.length) return null
  return loadPage(pages[0].pageId)
}

/** 列出所有已保存页面的摘要（不含已软删除） */
export async function listSavedPages(): Promise<SavedPageMeta[]> {
  const data = await apiCall('/api/lowcode/page/list?deleted=false', {
    method: 'POST',
  })
  return (data as SavedPageMeta[]) || []
}

/** 按模块列出页面摘要 */
export async function listPagesByModule(
  moduleId: string,
): Promise<SavedPageMeta[]> {
  const all = await listSavedPages()
  return all.filter((p) => (p.moduleId || 'mod_default') === moduleId)
}

/** 软删除页面（标记 deletedAt，不移除数据） */
export async function deletePage(pageId: string): Promise<void> {
  await apiCall(`/api/lowcode/page/${pageId}`, { method: 'DELETE' })
}

/** 设置页面启用/禁用 */
export async function setPageEnabled(
  pageId: string,
  enabled: boolean,
): Promise<void> {
  await apiCall(`/api/lowcode/page/${pageId}/status`, {
    method: 'PUT',
    body: JSON.stringify({ enabled }),
  })
}

/** 生成新页面 ID */
export function genPageId(): string {
  return `page_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 6)}`
}

/** 复制页面（深拷贝 schema，使用指定的新 pageId 和名称） */
export async function copyPage(
  sourcePageId: string,
  options: {
    newPageId: string
    pageName: string
    systemId?: string
    moduleId?: string
    pageType?: PageType
  },
): Promise<SavedPageMeta | null> {
  const source = await loadPage(sourcePageId)
  if (!source) return null
  const newSchema: PageSchema = {
    ...JSON.parse(JSON.stringify(source)),
    pageId: options.newPageId,
    pageName: options.pageName,
    systemId: options.systemId || source.systemId,
    moduleId: options.moduleId || source.moduleId,
    pageType: options.pageType || source.pageType,
    deletedAt: undefined,
    enabled: true,
  }
  return savePage(newSchema)
}

/** 列出已软删除的页面摘要 */
export async function listDeletedPages(): Promise<SavedPageMeta[]> {
  const data = await apiCall('/api/lowcode/page/list?deleted=true', {
    method: 'POST',
  })
  return (data as SavedPageMeta[]) || []
}

/** 恢复已软删除的页面（清除 deletedAt） */
export async function restorePage(pageId: string): Promise<void> {
  await apiCall(`/api/lowcode/page/${pageId}/restore`, { method: 'PUT' })
}

/** 彻底删除页面（硬删除，不可恢复） */
export async function hardDeletePage(pageId: string): Promise<void> {
  await apiCall(`/api/lowcode/page/${pageId}?hard=true`, { method: 'DELETE' })
}
// ================ 页面权限码 ================

export interface PagePermItem {
  id: number
  pageId: string
  permType: string
  itemKey: string
  itemLabel: string
  permCode: string
  sortOrder: number
  updatedAt: string
}

/** 查询某页面的所有字段/按钮权限码 */
export async function listPagePerms(pageId: string): Promise<PagePermItem[]> {
  const data = await apiCall(`/api/lowcode/perm/list?pageId=${encodeURIComponent(pageId)}`, { method: 'POST' })
  return (data as unknown) as PagePermItem[]
}
// ================ 孤儿字段管理 ================

/** 孤儿字段信息（数据库有但 schema 已删除的字段） */
export interface OrphanField {
  name: string
  hasData: boolean
}

/** 查询孤儿字段：数据库有但 schema 中没有的业务字段 */
export async function getOrphanFields(
  tableName: string,
  dbFields: string[],
): Promise<OrphanField[]> {
  const data = await apiCall('/api/lowcode/page/orphan-fields', {
    body: JSON.stringify({ tableName, dbFields }),
  })
  return (data as OrphanField[]) || []
}

/** 删除数据库字段（仅当字段无数据时允许） */
export async function dropField(
  tableName: string,
  columnName: string,
): Promise<void> {
  await apiCall('/api/lowcode/page/drop-field', {
    body: JSON.stringify({ tableName, columnName }),
  })
}
// ================ 基础资料数据查询 ================

/** 查询基础资料页面数据（通用数据查询） */
export async function queryPageData(
  pageId: string,
  options: { skip?: number; take?: number; filterData?: string } = {},
): Promise<{ data: Record<string, unknown>[]; totalCount: number }> {
  const data = await apiCall('/api/lowcode/page/query-data', {
    body: JSON.stringify({ pageId, ...options }),
  }) as { data?: Record<string, unknown>[]; totalCount?: number } | undefined
  return {
    data: data?.data ?? [],
    totalCount: data?.totalCount ?? 0,
  }
}

/** 列出所有基础资料类型页面（pageType='basedata'） */
export async function listBaseDataPages(): Promise<SavedPageMeta[]> {
  const all = await listSavedPages()
  return all.filter((p) => p.pageType === 'basedata')
}
