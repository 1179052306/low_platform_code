/**
 * 版本历史逻辑（FE-S4 方向3）。
 * 封装版本 API 调用：获取历史/回滚/BREAKING 检测。
 * 后端未就绪时静默失败。
 */
import { ref } from 'vue'
import { httpRequest } from '@/utils/request'
import type { MetaVersion, BreakingChange } from '@/types/schema'

export function useVersionHistory() {
  const versions = ref<MetaVersion[]>([])
  const loading = ref(false)

  async function getVersionHistory(metaKey: string): Promise<MetaVersion[]> {
    loading.value = true
    try {
      const res = await httpRequest('/api/lowcode/meta/version-history', {
        method: 'POST',
        body: JSON.stringify({ metaKey }),
      })
      if (res.ok) {
        const body = res.data as { data?: MetaVersion[] }
        versions.value = body.data || []
      }
    } catch {
      versions.value = []
    } finally {
      loading.value = false
    }
    return versions.value
  }

  async function rollbackVersion(metaKey: string, versionId: string): Promise<boolean> {
    try {
      const res = await httpRequest('/api/lowcode/meta/rollback', {
        method: 'POST',
        body: JSON.stringify({ metaKey, versionId }),
      })
      return res.ok
    } catch {
      return false
    }
  }

  async function checkBreakingChange(
    metaKey: string,
    newContent: Record<string, unknown>,
  ): Promise<BreakingChange[]> {
    try {
      const res = await httpRequest('/api/lowcode/meta/check-breaking', {
        method: 'POST',
        body: JSON.stringify({ metaKey, newContent }),
      })
      if (res.ok) {
        const body = res.data as { data?: BreakingChange[] }
        return body.data || []
      }
    } catch {
      /* ignore */
    }
    return []
  }

  /** 加载特定版本的页面 schema（用于 ?version= 预览） */
  async function loadVersionSchema(
    pageId: string,
    versionId: string,
  ): Promise<Record<string, unknown> | null> {
    try {
      const res = await httpRequest('/api/lowcode/meta/version-schema', {
        method: 'POST',
        body: JSON.stringify({ pageId, versionId }),
      })
      if (res.ok) {
        const body = res.data as { data?: Record<string, unknown> }
        return body.data || null
      }
    } catch {
      /* ignore */
    }
    return null
  }

  return {
    versions,
    loading,
    getVersionHistory,
    rollbackVersion,
    checkBreakingChange,
    loadVersionSchema,
  }
}