/**
 * 画布拖拽共享状态 — CanvasPanel 写入，CanvasNode 读取
 *
 * 当拖拽在容器内部进行时，CanvasNode (FREE_POSITION_CONTAINERS)
 * 通过此状态获取对齐辅助线并渲染到容器内部。
 */
import { ref, type Ref } from 'vue'
import type { GuideLine } from '@/utils/canvas-utils'

export interface ContainerDragGuides {
  /** 正在拖拽的容器 ID（null = 根画布拖拽） */
  containerId: string | null
  /** 当前对齐辅助线 */
  guides: GuideLine[]
}

export const containerDragGuides: Ref<ContainerDragGuides | null> = ref(null)

/** 设置当前拖拽容器的辅助线 */
export function setDragGuides(containerId: string | null, guides: GuideLine[]): void {
  containerDragGuides.value = { containerId, guides }
}

/** 清除拖拽辅助线 */
export function clearDragGuides(): void {
  containerDragGuides.value = null
}
