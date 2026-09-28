<script setup lang="ts">
/**
 * 分割面板子组件 — 对齐 Element Plus <el-splitter-panel>
 * 作为 el-splitter 的子节点使用，支持 size / min / max / collapsible 等配置
 *
 * 内置拖拽手柄 (handle)：
 * - 鼠标拖拽手柄可调整相邻两个 panel 的尺寸
 * - 键盘方向键可精确调整（10px/步，Shift+方向键 20px/步）
 * - 通过 stopPropagation 防止外层 CanvasNode 的拖拽/选中逻辑干扰
 */
import { ref, computed, onBeforeUnmount } from 'vue'
import type { ComponentSchema } from '@/types/schema'
import { designerStore, findNode } from '@/store/designer'

const props = defineProps<{
  size?: number
  min?: number
  max?: number
  collapsible?: boolean
  padding?: string
  background?: string
  showHandle?: boolean
  layout?: string
  isLastPanel?: boolean
  panelIndex?: number
  parentNodeId?: string
}>()

const isVertical = computed(() => props.layout === 'vertical')
const isDragging = ref(false)
const isFocused = ref(false)

const panelStyle = computed(() => {
  const style: Record<string, string> = {}
  if (props.padding) style.padding = props.padding
  if (props.background) style.background = props.background
  return style
})

/** 拖拽状态 */
interface DragState {
  startPos: number
  leftSize: number
  rightSize: number
  leftEl: HTMLElement
  rightEl: HTMLElement
  minSize: number
  direction: 'horizontal' | 'vertical'
}
let dragState: DragState | null = null

/** 获取 panel 的 min 属性 */
function getMinSize(panel: ComponentSchema | undefined): number {
  const min = panel?.props?.min as number | undefined
  return typeof min === 'number' && min > 0 ? min : 40
}

/** 从 schema 获取相邻两个 panel 的最小尺寸约束 */
function getMinConstraint(): number {
  if (!props.parentNodeId || props.panelIndex === undefined) return 40
  const parent = findNode(designerStore.schema, props.parentNodeId)
  if (!parent) return 40
  const leftPanel = parent.node.children[props.panelIndex]
  const rightPanel = parent.node.children[props.panelIndex + 1]
  return Math.min(getMinSize(leftPanel), getMinSize(rightPanel))
}

/** 通过 DOM 找到当前 panel 和下一个 panel 的外层包装元素（设计器 .canvas-node 或预览 .renderer-node） */
function findAdjacentCanvasNodes(handleEl: HTMLElement): { left: HTMLElement; right: HTMLElement } | null {
  // 从 handle 向上找到 .lc-el-splitter-panel
  const panelEl = handleEl.closest('.lc-el-splitter-panel')
  if (!panelEl) return null
  // 再向上找到外层包装（设计器用 .canvas-node，预览用 .renderer-node）
  const leftCanvasNode = (panelEl.closest('.canvas-node') || panelEl.closest('.renderer-node')) as HTMLElement | null
  if (!leftCanvasNode) return null
  // 下一个兄弟节点就是相邻 panel
  const rightCanvasNode = leftCanvasNode.nextElementSibling as HTMLElement | null
  if (!rightCanvasNode) return null
  return { left: leftCanvasNode, right: rightCanvasNode }
}

/** ============ 鼠标拖拽 ============ */

function onHandleMouseDown(e: MouseEvent): void {
  if (!props.showHandle || props.isLastPanel) return
  if (e.button !== 0) return

  // 关键：阻止事件冒泡到 CanvasNode，防止触发节点选中/拖拽
  e.stopPropagation()
  e.preventDefault()

  const handle = e.currentTarget as HTMLElement
  const nodes = findAdjacentCanvasNodes(handle)
  if (!nodes) return

  const direction = isVertical.value ? 'vertical' : 'horizontal'
  const leftSize = direction === 'vertical' ? nodes.left.offsetHeight : nodes.left.offsetWidth
  const rightSize = direction === 'vertical' ? nodes.right.offsetHeight : nodes.right.offsetWidth

  dragState = {
    startPos: direction === 'vertical' ? e.clientY : e.clientX,
    leftSize,
    rightSize,
    leftEl: nodes.left,
    rightEl: nodes.right,
    minSize: getMinConstraint(),
    direction,
  }

  isDragging.value = true
  document.body.style.cursor = direction === 'vertical' ? 'row-resize' : 'col-resize'
  document.body.style.userSelect = 'none'

  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
}

function onMouseMove(e: MouseEvent): void {
  if (!dragState) return

  const { startPos, leftSize, rightSize, leftEl, rightEl, minSize, direction } = dragState
  const delta = (direction === 'vertical' ? e.clientY : e.clientX) - startPos

  let newLeft = leftSize + delta
  let newRight = rightSize - delta

  // 最小尺寸约束
  if (newLeft < minSize) {
    newLeft = minSize
    newRight = leftSize + rightSize - minSize
  }
  if (newRight < minSize) {
    newRight = minSize
    newLeft = leftSize + rightSize - minSize
  }

  leftEl.style.flex = `0 0 ${newLeft}px`
  rightEl.style.flex = `0 0 ${newRight}px`
}

function onMouseUp(): void {
  if (!dragState) return

  const { leftEl, rightEl, direction } = dragState
  const leftSize = direction === 'vertical' ? leftEl.offsetHeight : leftEl.offsetWidth
  const rightSize = direction === 'vertical' ? rightEl.offsetHeight : rightEl.offsetWidth

  // 同步尺寸到 schema
  syncToSchema(leftSize, rightSize)

  // 清除拖拽时直接设置在 DOM 上的 inline flex 样式，让 Vue :style 绑定接管
  leftEl.style.flex = ''
  rightEl.style.flex = ''

  isDragging.value = false
  dragState = null
  document.body.style.cursor = ''
  document.body.style.userSelect = ''

  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
}

/** ============ 键盘操作 ============ */

function onHandleKeyDown(e: KeyboardEvent): void {
  if (!props.showHandle || props.isLastPanel) return

  const step = e.shiftKey ? 20 : 10
  let delta = 0
  let handled = false

  if (isVertical.value) {
    if (e.key === 'ArrowUp') { delta = -step; handled = true }
    else if (e.key === 'ArrowDown') { delta = step; handled = true }
  } else {
    if (e.key === 'ArrowLeft') { delta = -step; handled = true }
    else if (e.key === 'ArrowRight') { delta = step; handled = true }
  }

  if (!handled) return

  // 阻止事件冒泡到 CanvasNode 的键盘处理
  e.preventDefault()
  e.stopPropagation()

  const handle = e.currentTarget as HTMLElement
  const nodes = findAdjacentCanvasNodes(handle)
  if (!nodes) return

  const direction = isVertical.value ? 'vertical' : 'horizontal'
  const leftSize = direction === 'vertical' ? nodes.left.offsetHeight : nodes.left.offsetWidth
  const rightSize = direction === 'vertical' ? nodes.right.offsetHeight : nodes.right.offsetWidth

  let newLeft = leftSize + delta
  let newRight = rightSize - delta

  const minSize = getMinConstraint()
  if (newLeft < minSize) {
    newLeft = minSize
    newRight = leftSize + rightSize - minSize
  }
  if (newRight < minSize) {
    newRight = minSize
    newLeft = leftSize + rightSize - minSize
  }

  nodes.left.style.flex = `0 0 ${newLeft}px`
  nodes.right.style.flex = `0 0 ${newRight}px`

  syncToSchema(newLeft, newRight)

  // 清除 inline flex 样式，让 Vue :style 绑定接管
  nodes.left.style.flex = ''
  nodes.right.style.flex = ''
}

/** ============ Schema 同步 ============ */

function syncToSchema(leftSize: number, rightSize: number): void {
  if (!props.parentNodeId || props.panelIndex === undefined) return
  const parent = findNode(designerStore.schema, props.parentNodeId)
  if (!parent) return

  const leftPanel = parent.node.children[props.panelIndex]
  const rightPanel = parent.node.children[props.panelIndex + 1]

  // 存储为百分比，使容器尺寸变化时面板自动按比例适应
  const total = leftSize + rightSize
  if (total > 0) {
    if (leftPanel) {
      leftPanel.props.size = Math.round((leftSize / total) * 100)
      leftPanel.props.sizeUnit = '%'
    }
    if (rightPanel) {
      rightPanel.props.size = Math.round((rightSize / total) * 100)
      rightPanel.props.sizeUnit = '%'
    }
  }

  // 触发响应式更新
  designerStore.schemaRevision++
}

onBeforeUnmount(() => {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
  document.body.style.cursor = ''
  document.body.style.userSelect = ''
})
</script>

<template>
  <div class="el-splitter-panel lc-el-splitter-panel" :style="panelStyle">
    <slot />
    <!-- 拖拽手柄：仅在非最后一个 panel 上显示 -->
    <div
      v-if="showHandle && !isLastPanel"
      class="panel-handle"
      :class="[
        isVertical ? 'handle-bottom' : 'handle-right',
        { 'handle-dragging': isDragging },
        { 'handle-focused': isFocused }
      ]"
      draggable="false"
      tabindex="0"
      :title="`拖拽调整面板尺寸 | 方向键${isVertical ? '↑↓' : '←→'}精确调整(10px) | Shift+方向键大步长(20px)`"
      @mousedown.stop.prevent="onHandleMouseDown"
      @keydown.stop="onHandleKeyDown"
      @click.stop
      @dragstart.prevent
      @focus="isFocused = true"
      @blur="isFocused = false"
    >
      <div class="handle-grip"></div>
    </div>
  </div>
</template>

<style scoped>
.lc-el-splitter-panel {
  height: 100%;
  box-sizing: border-box;
  min-height: 40px;
  min-width: 40px;
  position: relative;
  flex-shrink: 0;
  overflow: visible;
}

/* 手柄基础样式 */
.panel-handle {
  position: absolute;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.15s ease;
  outline: none;
  user-select: none;
}

.panel-handle:focus-visible {
  box-shadow: 0 0 0 2px #1976d2;
  background: rgba(25, 118, 210, 0.15);
}

.panel-handle:hover {
  background: rgba(25, 118, 210, 0.2);
}

.panel-handle:hover .handle-grip {
  background: #1976d2;
}

.panel-handle.handle-focused {
  background: rgba(25, 118, 210, 0.2);
}

.panel-handle.handle-focused .handle-grip {
  background: #1976d2;
}

.panel-handle.handle-dragging {
  background: rgba(25, 118, 210, 0.3) !important;
}

.panel-handle.handle-dragging .handle-grip {
  background: #1976d2;
}

/* 水平布局：手柄在 panel 右侧（竖线） */
.handle-right {
  right: -5px;
  top: 0;
  bottom: 0;
  width: 10px;
  transform: translateX(0);
  cursor: col-resize;
}

.handle-right .handle-grip {
  width: 3px;
  height: 40px;
  background: #c0c4cc;
  border-radius: 2px;
  transition: background-color 0.15s ease, height 0.15s ease;
}

.handle-right:hover .handle-grip,
.handle-right:focus-visible .handle-grip {
  height: 60px;
  background: #1976d2;
}

/* 垂直布局：手柄在 panel 底部（横线） */
.handle-bottom {
  bottom: -5px;
  left: 0;
  right: 0;
  height: 10px;
  transform: translateY(0);
  cursor: row-resize;
}

.handle-bottom .handle-grip {
  height: 3px;
  width: 40px;
  background: #c0c4cc;
  border-radius: 2px;
  transition: background-color 0.15s ease, width 0.15s ease;
}

.handle-bottom:hover .handle-grip,
.handle-bottom:focus-visible .handle-grip {
  width: 60px;
  background: #1976d2;
}
</style>
