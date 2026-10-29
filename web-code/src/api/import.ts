/**
 * 数据导入 API：模板下载 / 执行导入 / 任务进度 / 错误文件 / 取消 / 历史。
 * 后端契约见 docs/import-design/后端详细设计-数据导入.md §8。
 */
import { httpRequest } from '@/utils/request'
import { t } from '@/store/lang'

/** 执行导入响应（异步：taskId；同步路径一期未启用，预留类型） */
export interface ImportExecuteResult {
  sync: boolean
  taskId?: string
  queuePosition?: number
  result?: ImportTaskSummary
}

export interface ImportTaskSummary {
  totalRows: number
  successRows: number
  failedRows: number
}

export interface ImportTaskView {
  taskId: string
  bizCode: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'PARTIAL' | 'FAILED' | 'CANCELLED'
  totalRows: number
  processedRows: number
  successRows: number
  failedRows: number
  queuePosition: number
  errorMsg?: string
  hasErrorFile: boolean
  createdAt?: string
  startedAt?: string
  finishedAt?: string
}

interface ResMsgShape {
  res?: boolean
  msg?: string
  data?: unknown
}

/** 解包 ResMsg：res=false 抛错透传后端信息 */
function unwrap<T>(data: unknown): T {
  const r = data as ResMsgShape
  if (r && r.res === false) {
    throw new Error(String(r.msg || t('操作失败')))
  }
  return (r?.data ?? r) as T
}

/** 执行导入（multipart 上传） */
export async function executeImport(file: File, bizCode: string, userId = ''): Promise<ImportExecuteResult> {
  const fd = new FormData()
  fd.append('file', file)
  fd.append('bizCode', bizCode)
  if (userId) fd.append('userId', userId)
  const result = await httpRequest('/api/lowcode/import/execute', {
    method: 'POST',
    body: fd,
    jsonContentType: false,
  })
  if (!result.ok) throw new Error(t('上传失败') + ` (${result.status})`)
  return unwrap<ImportExecuteResult>(result.data)
}

/** 任务进度 */
export async function getImportTask(taskId: string): Promise<ImportTaskView> {
  const result = await httpRequest(`/api/lowcode/import/task/${encodeURIComponent(taskId)}`, { method: 'GET' })
  if (!result.ok) throw new Error(t('查询任务失败') + ` (${result.status})`)
  return unwrap<ImportTaskView>(result.data)
}

/** 取消任务 */
export async function cancelImportTask(taskId: string): Promise<boolean> {
  const result = await httpRequest(`/api/lowcode/import/task/${encodeURIComponent(taskId)}/cancel`, { method: 'POST' })
  if (!result.ok) throw new Error(t('取消失败') + ` (${result.status})`)
  const r = unwrap<{ ok: boolean }>(result.data)
  return r.ok !== false
}

/** 历史任务列表（当前用户） */
export async function listImportTasks(
  bizCode: string,
  pageNum = 1,
  pageSize = 10,
): Promise<{ total: number; list: ImportTaskView[] }> {
  const qs = `bizCode=${encodeURIComponent(bizCode)}&pageNum=${pageNum}&pageSize=${pageSize}`
  const result = await httpRequest(`/api/lowcode/import/tasks?${qs}`, { method: 'GET' })
  if (!result.ok) throw new Error(t('查询历史失败') + ` (${result.status})`)
  return unwrap<{ total: number; list: ImportTaskView[] }>(result.data)
}

/** 服务端文件下载（模板/错误报告，fetch blob → 浏览器另存） */
async function downloadFromServer(url: string, fallbackName: string, errorPrefix: string): Promise<void> {
  const resp = await window.fetch(url)
  if (!resp.ok) {
    const text = await resp.text().catch(() => '')
    throw new Error(`${errorPrefix}: ${text || resp.status}`)
  }
  const blob = await resp.blob()
  const blobUrl = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = blobUrl
  const disposition = resp.headers.get('Content-Disposition') || ''
  const match = /filename\*=UTF-8''([^;]+)/.exec(disposition)
  a.download = match ? decodeURIComponent(match[1]) : fallbackName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(blobUrl), 3000)
}

/** 下载导入模板 */
export function downloadImportTemplate(bizCode: string): Promise<void> {
  return downloadFromServer(
    `/api/lowcode/import/template?bizCode=${encodeURIComponent(bizCode)}`,
    `导入模板-${bizCode}.xlsx`,
    t('模板下载失败'),
  )
}

/** 下载错误报告文件 */
export function downloadErrorFile(taskId: string): Promise<void> {
  return downloadFromServer(
    `/api/lowcode/import/task/${encodeURIComponent(taskId)}/errors`,
    `导入错误报告-${taskId}.xlsx`,
    t('错误报告下载失败'),
  )
}