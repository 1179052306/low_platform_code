/**
 * 层切换逻辑（方向一：分层继承）
 *
 * 封装 designerStore 的层相关方法，跨组件复用（DesignerView 层选择器、PropertyPanel 覆盖标记等）。
 * 遵循现有 useTheme.ts 的 composable 模式。
 */
import {
  designerStore,
  setLayer,
  setMergePreview,
  overrideProp,
  restoreProp,
  isPropOverridden,
  getOverridePropValue,
} from '@/store/designer'
import type { MetaLayer } from '@/types/schema'

export function useLayer() {
  return {
    currentLayer: designerStore.currentLayer,
    mergePreview: designerStore.mergePreview,
    setLayer,
    setMergePreview,
    overrideProp,
    restoreProp,
    isPropOverridden,
    getOverridePropValue,
  }
}

/** 层选项（供层选择器展示） */
export const LAYER_OPTIONS: { value: MetaLayer; label: string }[] = [
  { value: 'PLATFORM', label: '平台层' },
  { value: 'INDUSTRY', label: '行业层' },
  { value: 'CUSTOMER', label: '客户层' },
]