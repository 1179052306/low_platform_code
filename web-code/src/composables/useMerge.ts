/**
 * 合并预览逻辑（方向一：分层继承）
 *
 * 封装合并后 schema 加载与层间 diff，供 DesignerView 合并预览、LayerDiffDialog 使用。
 * 后端合并接口未就绪时 loadMerged 抛错，调用方 fallback 到当前层 schema。
 */
import { ref } from 'vue'
import { designerStore, loadMergedSchema } from '@/store/designer'
import type { PageSchema, ComponentSchema } from '@/types/schema'

export function useMerge() {
  const merging = ref(false)
  const mergedSchema = ref<PageSchema | null>(null)

  /** 加载合并后 schema（分层继承后运行时渲染用） */
  async function loadMerged(): Promise<PageSchema> {
    merging.value = true
    try {
      const schema = await loadMergedSchema(designerStore.pageId)
      mergedSchema.value = schema
      return schema
    } finally {
      merging.value = false
    }
  }

  /**
   * 层间 diff：对比当前层 schema 与合并后 schema，返回差异节点列表。
   * 简化实现：按 id 匹配，找出 props/overrideProps 不同的节点。
   */
  function diffLayers(merged: ComponentSchema[], current: ComponentSchema[]): LayerDiffItem[] {
    const result: LayerDiffItem[] = []
    const currentMap = new Map(current.map((n) => [n.id, n]))
    for (const m of merged) {
      const c = currentMap.get(m.id)
      if (!c) {
        result.push({ id: m.id, type: m.type, status: 'added' })
        continue
      }
      if (JSON.stringify(m.props) !== JSON.stringify(c.props)) {
        result.push({ id: m.id, type: m.type, status: 'modified' })
      }
    }
    for (const c of current) {
      if (!merged.find((m) => m.id === c.id)) {
        result.push({ id: c.id, type: c.type, status: 'removed' })
      }
    }
    return result
  }

  return {
    merging,
    mergedSchema,
    loadMerged,
    diffLayers,
  }
}

/** 层间 diff 项 */
export interface LayerDiffItem {
  id: string
  type: string
  status: 'added' | 'modified' | 'removed'
}