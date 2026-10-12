/**
 * 应用包 store（FE-S5 方向4）。
 * 仿 designerStore 模式，含撤销栈。
 */
import { ref, reactive } from 'vue'
import type { AppPackage } from '@/types/schema'
import { httpRequest } from '@/utils/request'

export const packageList = ref<AppPackage[]>([])
export const currentPackage = ref<AppPackage | null>(null)

const _history: AppPackage[][] = []
let _historyIndex = -1

function pushHistory(): void {
  _history.splice(_historyIndex + 1)
  _history.push(JSON.parse(JSON.stringify(packageList.value)))
  _historyIndex = _history.length - 1
}

export function undo(): void {
  if (_historyIndex > 0) {
    _historyIndex--
    packageList.value = JSON.parse(JSON.stringify(_history[_historyIndex]))
  }
}

export function redo(): void {
  if (_historyIndex < _history.length - 1) {
    _historyIndex++
    packageList.value = JSON.parse(JSON.stringify(_history[_historyIndex]))
  }
}

async function apiCall(url: string, body?: string): Promise<unknown> {
  const res = await httpRequest(url, { method: 'POST', body })
  if (!res.ok) throw new Error('请求失败')
  const data = res.data as { res?: boolean; data?: unknown }
  return data?.data
}

export async function listPackages(): Promise<AppPackage[]> {
  try {
    const data = await apiCall('/api/lowcode/package/list')
    packageList.value = (data as AppPackage[]) || []
    return packageList.value
  } catch {
    return []
  }
}

export async function savePackage(pkg: AppPackage): Promise<void> {
  pushHistory()
  await apiCall('/api/lowcode/package/save', JSON.stringify(pkg))
}

export async function deletePackage(id: string): Promise<void> {
  pushHistory()
  await apiCall('/api/lowcode/package/delete', JSON.stringify({ id }))
  packageList.value = packageList.value.filter((p) => p.id !== id)
}