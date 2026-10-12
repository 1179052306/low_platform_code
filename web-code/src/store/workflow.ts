/**
 * 流程设计器 store（FE-S6 审批流）。
 * 仿 designerStore 模式，含撤销栈。
 */
import { ref } from 'vue'
import type { WorkflowDef } from '@/types/schema'
import { httpRequest } from '@/utils/request'

export const workflowList = ref<WorkflowDef[]>([])
export const currentWorkflow = ref<WorkflowDef | null>(null)

const _history: WorkflowDef[][] = []
let _historyIndex = -1

function pushHistory(): void {
  _history.splice(_historyIndex + 1)
  _history.push(JSON.parse(JSON.stringify(workflowList.value)))
  _historyIndex = _history.length - 1
}

export function undo(): void {
  if (_historyIndex > 0) {
    _historyIndex--
    workflowList.value = JSON.parse(JSON.stringify(_history[_historyIndex]))
  }
}

export function redo(): void {
  if (_historyIndex < _history.length - 1) {
    _historyIndex++
    workflowList.value = JSON.parse(JSON.stringify(_history[_historyIndex]))
  }
}

async function apiCall(url: string, body?: string): Promise<unknown> {
  const res = await httpRequest(url, { method: 'POST', body })
  if (!res.ok) {
    const errData = res.data as { msg?: string; message?: string } | undefined
    throw new Error(errData?.msg || errData?.message || `请求失败 (HTTP ${res.status})`)
  }
  const data = res.data as { res?: boolean; data?: unknown; msg?: string }
  if (data && data.res === false) {
    throw new Error(data.msg || '操作失败')
  }
  return data?.data
}

export async function listWorkflows(): Promise<WorkflowDef[]> {
  const data = await apiCall('/api/lowcode/workflow', JSON.stringify({}))
  workflowList.value = (data as WorkflowDef[]) || []
  return workflowList.value
}

export async function saveWorkflow(wf: WorkflowDef): Promise<void> {
  pushHistory()
  await apiCall('/api/lowcode/workflow', JSON.stringify({ ...wf, op: 'save' }))
}

export async function deleteWorkflow(id: string): Promise<void> {
  pushHistory()
  await apiCall('/api/lowcode/workflow', JSON.stringify({ id, op: 'delete' }))
  workflowList.value = workflowList.value.filter((w) => w.id !== id)
}
export async function validateWorkflow(wf: WorkflowDef): Promise<string[]> {
  const data = await apiCall('/api/lowcode/workflow', JSON.stringify({ ...wf, op: 'validate' }))
  return (data as string[]) || []
}

export async function publishWorkflow(wf: WorkflowDef): Promise<void> {
  await apiCall('/api/lowcode/workflow', JSON.stringify({ ...wf, op: 'publish' }))
}

export async function startWorkflow(workflowCode: string, bizCode: string, bizId: string, initiator: string): Promise<string> {
  const data = await apiCall('/api/lowcode/workflow', JSON.stringify({ workflowCode, bizCode, bizId, initiator, op: 'start' }))
  return data as string
}