/**
 * 元数据 store（方向二：一切皆元数据统一驱动）
 *
 * 复用 designerStore 模式：独立 TS 模块 + ref/reactive，非 Pinia/Vuex。
 * 后端 lc_meta_registry 就绪后对接，当前为骨架。
 */
import { ref } from 'vue'
import { httpRequest, extractPayload } from '@/utils/request'
import { t } from '@/store/lang'
import type { MetaItem, MetaDomain } from '@/types/meta'

/** 元数据列表 */
const metaList = ref<MetaItem[]>([])
/** 加载状态 */
const loading = ref(false)
/** 当前选中域（过滤用） */
const currentDomain = ref<MetaDomain | ''>('')
/** 当前选中类型（过滤用） */
const currentMetaType = ref('')

/**
 * 加载元数据列表（后端 /api/lowcode/meta/list 就绪后对接）。
 * 后端未就绪时返回空列表，不抛错（MetaManageView 显示空状态）。
 */
export async function loadMetaList(filter?: {
  domain?: MetaDomain
  metaType?: string
}): Promise<MetaItem[]> {
  loading.value = true
  try {
    const res = await httpRequest('/api/lowcode/meta/list', {
      method: 'POST',
      body: JSON.stringify(filter ?? {}),
    })
    if (res.ok) {
      const payload = extractPayload(res.data)
      metaList.value = payload.data as MetaItem[]
      return metaList.value
    }
  } catch {
    // 后端未就绪，静默返回空列表
  } finally {
    loading.value = false
  }
  metaList.value = []
  return []
}

/** 保存元数据项（新增或更新） */
export async function saveMetaItem(item: MetaItem): Promise<void> {
  const res = await httpRequest('/api/lowcode/meta/save', {
    method: 'POST',
    body: JSON.stringify(item),
  })
  if (!res.ok) {
    throw new Error(t('保存元数据失败：后端连接异常'))
  }
  const body = res.data as { res?: boolean; msg?: string }
  if (body && body.res === false) {
    throw new Error(t(body.msg || '保存元数据失败'))
  }
}

/** 删除元数据项 */
export async function deleteMetaItem(id: string): Promise<void> {
  const res = await httpRequest(`/api/lowcode/meta/${id}`, { method: 'DELETE' })
  if (!res.ok) {
    throw new Error(t('删除元数据失败：后端连接异常'))
  }
}

/** 按域过滤的元数据列表（computed 辅助） */
export function filterByDomain(
  list: MetaItem[],
  domain: MetaDomain | '',
  metaType: string,
): MetaItem[] {
  return list.filter((m) => {
    if (domain && m.domain !== domain) return false
    if (metaType && m.metaType !== metaType) return false
    return true
  })
}

export const metaStore = {
  metaList,
  loading,
  currentDomain,
  currentMetaType,
}