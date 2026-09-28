<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import CanvasNode from './CanvasNode.vue'
import { getConfig } from '@/materials'
import { t } from '@/store/lang'
import {
  designerStore,
  addNode,
  moveNode,
  removeNode,
  updateNodePosition,
  resizeNode,
  pushHistory,
  setSelection,
  selectSingleNode,
  toggleSelectNode,
  clearSelection,
  isNodeSelected,
  findNode,
  updateNodeCss,
  isFullPercent,
} from '@/store/designer'
import { FREE_POSITION_CONTAINERS } from '@/constants/container-types'
import { applyTheme, clearTheme, getThemeDetail } from '@/composables/useTheme'
import {
  snapRect as snapRectUtil,
  isOverlapping as isOverlappingUtil,
  findNonOverlappingPosition as findNonOverlappingPosUtil,
  getBoundsFromEl,
  isOutOfBounds,
  type GuideLine,
  type Bounds,
} from '@/utils/canvas-utils'
import {
  setDragGuides,
  clearDragGuides,
} from '@/composables/useCanvasDragState'
import type { ComponentSchema } from '@/types/schema'

/** 面包屑路径项 */
interface BreadcrumbItem {
  id: string | null
  label: string
}

/** 当前选中控件的层级路径（页面 → 布局 → 子控件） */
const breadcrumbPath = computed<BreadcrumbItem[]>(() => {
  const path: BreadcrumbItem[] = [{ id: null, label: t('页面') }]
  const selectedId = designerStore.selectedId
  if (!selectedId) return path
  const found = findNode(designerStore.schema, selectedId)
  if (!found) return path
  // 向上遍历构建路径
  const chain: { id: string; label: string }[] = []
  let current: ComponentSchema | null = found.node
  let parent = found.parent
  while (current) {
    const cfg = getConfig(current.type)
    const name = current.fieldName
      ? `${cfg?.title ?? current.type} (${current.fieldName})`
      : cfg?.title ?? current.type
    chain.unshift({ id: current.id, label: name })
    current = parent
    if (parent) {
      const parentFound = findNode(designerStore.schema, parent.id)
      parent = parentFound?.parent ?? null
    }
  }
  for (const item of chain) {
    path.push(item)
  }
  return path
})

/** 点击面包屑项选中对应节点 */
function onBreadcrumbClick(item: BreadcrumbItem): void {
  if (item.id === null) {
    clearSelection()
  } else {
    selectSingleNode(item.id)
  }
}

interface DragState {
  draggedId: string
  nodeIds: string[]
  origins: { id: string; x: number; y: number }[]
  startClientX: number
  startClientY: number
  width: number
  height: number
  x: number
  y: number
  guides: GuideLine[]
  parentId: string | null
}

interface ResizeState {
  nodeId: string
  startClientX: number
  startClientY: number
  origX: number
  origY: number
  origWidth: number
  origHeight: number
  dir: string
}

const MIN_PAGE_WIDTH = 900
const MIN_PAGE_HEIGHT = 700

const dragState = ref<DragState | null>(null)
const resizeState = ref<ResizeState | null>(null)
const emptyDropActive = ref(false)
const marquee = ref<{
  startX: number
  startY: number
  curX: number
  curY: number
} | null>(null)
const canvasScrollRef = ref<HTMLElement | null>(null)
const canvasPageRef = ref<HTMLElement | null>(null)
const viewport = ref({ width: 0, height: 0 })
let marqueePageElement: HTMLElement | null = null
let skipNextBackgroundClick = false

const isEmpty = computed(() => designerStore.schema.length === 0)

function measureViewport(): void {
  const el = canvasScrollRef.value
  if (!el) return
  viewport.value = {
    width: el.clientWidth - 20,
    height: el.clientHeight - 20,
  }
}

let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  measureViewport()
  window.addEventListener('resize', measureViewport)
  // 监听画布容器尺寸变化（面板隐藏/显示时自动调整）
  if (canvasScrollRef.value) {
    resizeObserver = new ResizeObserver(() => {
      measureViewport()
    })
    resizeObserver.observe(canvasScrollRef.value)
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', measureViewport)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
})

const pageSize = computed(() => {
  const width = Math.max(MIN_PAGE_WIDTH, viewport.value.width)
  let height = Math.max(MIN_PAGE_HEIGHT, viewport.value.height)
  // 高度允许向下扩展以容纳控件，宽度固定不扩展
  for (const node of designerStore.schema) {
    const bottom = (node.y ?? 0) + (node.height ?? 120) + 48
    if (bottom > height) height = bottom
  }
  return { width, height: Math.ceil(height) }
})

const pageStyle = computed(() => ({
  width: `${pageSize.value.width}px`,
  height: `${pageSize.value.height}px`,
  background: designerStore.pageCss.background || 'var(--lc-page-bg, #ffffff)',
  backgroundImage: `linear-gradient(to right, #e0e0e0 1px, transparent 1px), linear-gradient(to bottom, #e0e0e0 1px, transparent 1px)`,
  backgroundSize: '20px 20px',
}))

function onBackgroundClick(): void {
  if (skipNextBackgroundClick) {
    skipNextBackgroundClick = false
    return
  }
  clearSelection()
}

/** 单选 / Shift（Ctrl）加选 */
function onSelectSingle(payload: { id: string; additive: boolean }): void {
  if (payload.additive) toggleSelectNode(payload.id)
  else selectSingleNode(payload.id)
}

let marqueeRect: { left: number; top: number } | null = null
let marqueeRafId: number | null = null

function onPageMouseDown(e: MouseEvent): void {
  if (e.button !== 0) return
  marqueePageElement = e.currentTarget as HTMLElement
  const rect = marqueePageElement.getBoundingClientRect()
  marqueeRect = { left: rect.left, top: rect.top }
  const point = { x: e.clientX - rect.left, y: e.clientY - rect.top }
  marquee.value = {
    startX: point.x,
    startY: point.y,
    curX: point.x,
    curY: point.y,
  }
  window.addEventListener('mousemove', onPageMouseMove)
  window.addEventListener('mouseup', onPageMouseUp)
}

function onPageMouseMove(e: MouseEvent): void {
  if (!marquee.value) return
  if (!marqueeRect) return
  marquee.value.curX = e.clientX - marqueeRect.left
  marquee.value.curY = e.clientY - marqueeRect.top
  if (marqueeRafId === null) {
    marqueeRafId = requestAnimationFrame(() => {
      marqueeRafId = null
      updateMarqueeSelection()
    })
  }
}

let lastSelectedIds: string = ''

function updateMarqueeSelection(): void {
  if (!marquee.value) return
  const left = Math.min(marquee.value.startX, marquee.value.curX)
  const right = Math.max(marquee.value.startX, marquee.value.curX)
  const top = Math.min(marquee.value.startY, marquee.value.curY)
  const bottom = Math.max(marquee.value.startY, marquee.value.curY)
  const ids: string[] = []
  for (const node of designerStore.schema) {
    const x = node.x ?? 0
    const y = node.y ?? 0
    const w = node.width ?? 240
    const h = node.height ?? 120
    if (x + w >= left && x <= right && y + h >= top && y <= bottom) {
      ids.push(node.id)
    }
  }
  const idsKey = ids.join(',')
  if (idsKey !== lastSelectedIds) {
    lastSelectedIds = idsKey
    setSelection(ids)
  }
}

function onPageMouseUp(e: MouseEvent): void {
  window.removeEventListener('mousemove', onPageMouseMove)
  window.removeEventListener('mouseup', onPageMouseUp)
  if (marqueeRafId !== null) {
    cancelAnimationFrame(marqueeRafId)
    marqueeRafId = null
  }
  if (!marquee.value) return
  const dx = Math.abs(marquee.value.curX - marquee.value.startX)
  const dy = Math.abs(marquee.value.curY - marquee.value.startY)
  if (dx < 4 && dy < 4) {
    clearSelection()
  } else {
    skipNextBackgroundClick = true
  }
  marquee.value = null
  marqueePageElement = null
  marqueeRect = null
  lastSelectedIds = ''
}

const marqueeStyle = computed(() => {
  if (!marquee.value) return {}
  const left = Math.min(marquee.value.startX, marquee.value.curX)
  const top = Math.min(marquee.value.startY, marquee.value.curY)
  const width = Math.abs(marquee.value.curX - marquee.value.startX)
  const height = Math.abs(marquee.value.curY - marquee.value.startY)
  return {
    left: `${left}px`,
    top: `${top}px`,
    width: `${width}px`,
    height: `${height}px`,
  }
})

/** 获取拖拽作用域的兄弟节点和边界 */
function getDragScope(parentId: string | null): {
  siblings: ComponentSchema[]
  boundsEl: HTMLElement | null
  bounds: Bounds
} {
  if (parentId) {
    const found = findNode(designerStore.schema, parentId)
    if (found) {
      const containerEl = document.getElementById(`canvas-node-${parentId}`)
      const bounds = getBoundsFromEl(containerEl, {
        width: found.node.width ?? 300,
        height: found.node.height ?? 200,
      })
      return {
        siblings: found.node.children ?? [],
        boundsEl: containerEl,
        bounds,
      }
    }
  }
  const bounds = getBaseCanvasSize()
  return {
    siblings: designerStore.schema,
    boundsEl: canvasPageRef.value,
    bounds,
  }
}

function onNodeDragStart(payload: {
  event: MouseEvent
  nodeId: string
  x: number
  y: number
  width: number
  height: number
  parentId: string | null
}): void {
  if (payload.event.button !== 0) return
  const isMulti =
    isNodeSelected(payload.nodeId) && designerStore.selectedIds.length > 1
  const nodeIds = isMulti ? [...designerStore.selectedIds] : [payload.nodeId]
  if (!isMulti) selectSingleNode(payload.nodeId)
  // 递归搜索整棵树查找节点（支持容器内定位子节点）
  const origins = nodeIds.map((id) => {
    const found = findNode(designerStore.schema, id)
    return { id, x: found?.node.x ?? 0, y: found?.node.y ?? 0 }
  })
  pushHistory()
  dragState.value = {
    draggedId: payload.nodeId,
    nodeIds,
    origins,
    startClientX: payload.event.clientX,
    startClientY: payload.event.clientY,
    width: payload.width,
    height: payload.height,
    x: payload.x,
    y: payload.y,
    guides: [],
    parentId: payload.parentId,
  }
  // 拖拽时将被拖拽节点提升到最上层
  for (const id of nodeIds) {
    const el = document.getElementById(`canvas-node-${id}`)
    if (el) el.style.zIndex = '9999'
  }
  window.addEventListener('mousemove', onDragMove)
  window.addEventListener('mouseup', onDragEnd)
}

/** 获取容器在画布中的实际位置和尺寸（从 DOM 读取） */
function getContainerRect(container: ComponentSchema): {
  left: number
  top: number
  width: number
  height: number
} {
  const containerEl = document.getElementById(`canvas-node-${container.id}`)
  if (containerEl && canvasPageRef.value) {
    const canvasRect = canvasPageRef.value.getBoundingClientRect()
    const elRect = containerEl.getBoundingClientRect()
    return {
      left: elRect.left - canvasRect.left,
      top: elRect.top - canvasRect.top,
      width: elRect.width,
      height: elRect.height,
    }
  }
  return {
    left: container.x ?? 0,
    top: container.y ?? 0,
    width: container.width ?? 240,
    height: container.height ?? 120,
  }
}

/** 画布基础尺寸：宽度固定不扩展，高度允许向下扩展（用于边界检查） */
function getBaseCanvasSize(): { width: number; height: number } {
  return {
    width: Math.max(MIN_PAGE_WIDTH, viewport.value.width),
    height: pageSize.value.height,
  }
}

/** 重叠提示（浮动消息，3秒后自动消失） */
const overlapWarning = ref(false)
let overlapWarningTimer: ReturnType<typeof setTimeout> | null = null
function showOverlapWarning(): void {
  overlapWarning.value = true
  if (overlapWarningTimer) clearTimeout(overlapWarningTimer)
  overlapWarningTimer = setTimeout(() => {
    overlapWarning.value = false
  }, 3000)
}

const boundaryWarning = ref(false)
let boundaryWarningTimer: ReturnType<typeof setTimeout> | null = null
function showBoundaryWarning(): void {
  boundaryWarning.value = true
  if (boundaryWarningTimer) clearTimeout(boundaryWarningTimer)
  boundaryWarningTimer = setTimeout(() => {
    boundaryWarning.value = false
  }, 3000)
}

// 监听 store 中的边界警告触发（键盘移动等外部触发）
watch(
  () => designerStore.boundaryWarningTick,
  (tick) => {
    if (tick > 0) showBoundaryWarning()
  }
)

function onDragMove(e: MouseEvent): void {
  if (!dragState.value) return
  const state = dragState.value
  const dx = e.clientX - state.startClientX
  const dy = e.clientY - state.startClientY
  const draggedOrigin = state.origins.find(
    (origin) => origin.id === state.draggedId
  )
  if (!draggedOrigin) return
  let rawX = draggedOrigin.x + dx
  let rawY = draggedOrigin.y + dy

  // 宽/高为 100% 的控件已撑满父容器：锁定对应轴的偏移，
  // 否则拖一下又会超出父容器画布（右/下溢出）。
  const draggedCss = findNode(designerStore.schema, state.draggedId)?.node.css
  if (isFullPercent(draggedCss?.width)) rawX = 0
  if (isFullPercent(draggedCss?.height)) rawY = 0

  let finalX: number
  let finalY: number
  let guides: GuideLine[] = []

  // 统一使用参数化 snapRect，支持根画布和容器内部
  const scope = getDragScope(state.parentId)
  const result = snapRectUtil(
    rawX,
    rawY,
    state.width,
    state.height,
    new Set(state.nodeIds),
    scope.siblings,
    scope.bounds
  )
  finalX = result.x
  finalY = result.y
  guides = result.guides

  // 更新共享辅助线状态（CanvasNode 会读取并在容器内渲染）
  if (state.parentId) {
    setDragGuides(state.parentId, guides)
  } else {
    setDragGuides(null, guides)
  }

  // 边界检查：超出则撤销整个拖拽操作
  // 注：控件宽于容器时（ow >= bounds.width）不做宽度溢出检查，否则任何位置都会被拒绝
  const moveDX = finalX - draggedOrigin.x
  const moveDY = finalY - draggedOrigin.y
  // 自由定位容器（el-splitter-panel / el-tab-pane）内的子节点允许拖出容器：
  // 四个方向都不做边界限制，否则往左/上拖会触发越界中止，导致「拖不出来」。
  // 最终归属（留在原容器 / 移到其它容器 / 移到画布根级）由 onDragEnd 决定。
  const parentFound = state.parentId
    ? findNode(designerStore.schema, state.parentId)
    : null
  const isPositionedChild =
    !!parentFound && FREE_POSITION_CONTAINERS.has(parentFound.node.type)
  let outOfBounds = false
  if (!isPositionedChild) {
    for (const origin of state.origins) {
      const ox = origin.x + moveDX
      const oy = origin.y + moveDY
      const found = findNode(designerStore.schema, origin.id)
      const ow = found?.node.width ?? state.width
      if (ox < 0 || oy < 0) {
        outOfBounds = true
        break
      }
      // 注：控件宽于容器时（ow >= bounds.width）不做宽度溢出检查，否则任何位置都会被拒绝
      if (ow < scope.bounds.width && ox + ow > scope.bounds.width) {
        outOfBounds = true
        break
      }
    }
  }
  if (outOfBounds) {
    for (const origin of state.origins) {
      updateNodePosition(origin.id, origin.x, origin.y)
    }
    for (const id of state.nodeIds) {
      const el = document.getElementById(`canvas-node-${id}`)
      if (el) el.style.zIndex = ''
    }
    dragState.value = null
    clearDragGuides()
    window.removeEventListener('mousemove', onDragMove)
    window.removeEventListener('mouseup', onDragEnd)
    showBoundaryWarning()
    return
  }

  state.x = finalX
  state.y = finalY
  state.guides = guides
  for (const origin of state.origins) {
    // 自由定位子节点不钳制坐标，节点才能跟随光标移出容器（视觉上脱离父容器）
    updateNodePosition(
      origin.id,
      origin.x + moveDX,
      origin.y + moveDY,
      !isPositionedChild
    )
  }
}

function onDragEnd(e: MouseEvent): void {
  window.removeEventListener('mousemove', onDragMove)
  window.removeEventListener('mouseup', onDragEnd)
  clearDragGuides()
  const state = dragState.value
  dragState.value = null
  if (!state) return

  // 恢复被拖拽节点的 z-index
  for (const id of state.nodeIds) {
    const el = document.getElementById(`canvas-node-${id}`)
    if (el) el.style.zIndex = ''
  }

  // 鼠标几乎没有移动（点击而非拖拽），不做容器检测
  const dx = Math.abs(e.clientX - state.startClientX)
  const dy = Math.abs(e.clientY - state.startClientY)
  if (dx < 4 && dy < 4) return

  // 使用 elementFromPoint 检测鼠标下方实际 DOM 元素，避免 schema 高度与实际 DOM 高度不一致
  const excludeIdSet = new Set(state.nodeIds)
  // 临时隐藏被拖拽节点（以便 elementFromPoint 检测到下方的容器）
  const draggedEl = document.getElementById(`canvas-node-${state.draggedId}`)
  const prevDisplay = draggedEl?.style.display
  if (draggedEl) draggedEl.style.display = 'none'

  const elBelow = document.elementFromPoint(e.clientX, e.clientY)
  if (draggedEl && prevDisplay !== undefined)
    draggedEl.style.display = prevDisplay

  let droppedOnSameContainer = false

  if (elBelow) {
    // 向上查找最近的 canvas-node 容器（跳过被拖拽节点自身）
    let target: HTMLElement | null = elBelow as HTMLElement
    while (target) {
      const nodeId = target.id?.replace('canvas-node-', '')
      if (nodeId && nodeId !== state.draggedId && !excludeIdSet.has(nodeId)) {
        const found = findNode(designerStore.schema, nodeId)
        if (found && getConfig(found.node.type)?.isContainer) {
          if (state.parentId === found.node.id) {
            // 同一容器内：保持位置，进入重叠检测
            droppedOnSameContainer = true
            break
          }
          // 不同容器：移动到新容器
          moveNode(state.draggedId, found.node.id, found.node.children.length)
          // 设置在新容器中的位置（基于鼠标在容器内的相对坐标）
          const parentEl = document.getElementById(
            `canvas-node-${found.node.id}`
          )
          const parentRect = parentEl?.getBoundingClientRect()
          if (parentRect) {
            const newX = Math.max(
              0,
              e.clientX - parentRect.left - state.width / 2
            )
            const newY = Math.max(
              0,
              e.clientY - parentRect.top - state.height / 2
            )
            updateNodePosition(state.draggedId, newX, newY)
          }
          return
        }
      }
      target = target.parentElement
    }
  }

  // 从容器内拖出到画布根级（未落在任何容器上）
  if (state.parentId && !droppedOnSameContainer) {
    const canvasRect = canvasPageRef.value?.getBoundingClientRect()
    if (canvasRect) {
      const newX = Math.max(0, e.clientX - canvasRect.left - state.width / 2)
      const newY = Math.max(0, e.clientY - canvasRect.top - state.height / 2)
      moveNode(state.draggedId, null, designerStore.schema.length)
      updateNodePosition(state.draggedId, newX, newY)
    }
    return
  }

  // 节点仍留在原容器内：校正在拖拽过程中为了「拖出容器」而写入的负坐标，
  // 否则节点会停在容器外侧，看起来像凭空消失。
  for (const id of state.nodeIds) {
    const stayed = findNode(designerStore.schema, id)?.node
    if (stayed && ((stayed.x ?? 0) < 0 || (stayed.y ?? 0) < 0)) {
      updateNodePosition(
        id,
        Math.max(0, stayed.x ?? 0),
        Math.max(0, stayed.y ?? 0)
      )
    }
  }

  // 重叠检测：如果与兄弟控件重叠，回退到拖拽前的原始位置
  const foundNode = findNode(designerStore.schema, state.draggedId)
  const draggedNode = foundNode?.node
  if (draggedNode) {
    const finalX = draggedNode.x ?? 0
    const finalY = draggedNode.y ?? 0
    const w = state.width
    const h = state.height
    if (state.parentId || droppedOnSameContainer) {
      // 容器内子节点：检查与同容器兄弟节点的重叠
      const parent = foundNode?.parent
      if (
        parent &&
        isOverlappingUtil(
          finalX,
          finalY,
          w,
          h,
          excludeIdSet,
          parent.children ?? []
        )
      ) {
        for (const origin of state.origins) {
          updateNodePosition(origin.id, origin.x, origin.y)
        }
      }
    } else {
      // 根级节点：检查与其他根级控件的重叠
      if (
        isOverlappingUtil(
          finalX,
          finalY,
          w,
          h,
          excludeIdSet,
          designerStore.schema
        )
      ) {
        for (const origin of state.origins) {
          updateNodePosition(origin.id, origin.x, origin.y)
        }
      }
    }
  }
}

function onNodeResizeStart(payload: {
  event: MouseEvent
  nodeId: string
  dir: string
  x: number
  y: number
  width: number
  height: number
}): void {
  if (payload.event.button !== 0) return
  pushHistory()
  resizeState.value = {
    nodeId: payload.nodeId,
    startClientX: payload.event.clientX,
    startClientY: payload.event.clientY,
    origX: payload.x,
    origY: payload.y,
    origWidth: payload.width,
    origHeight: payload.height,
    dir: payload.dir,
  }
  window.addEventListener('mousemove', onResizeMove)
  window.addEventListener('mouseup', onResizeEnd)
}

function onResizeMove(e: MouseEvent): void {
  if (!resizeState.value) return
  const state = resizeState.value
  const dx = e.clientX - state.startClientX
  const dy = e.clientY - state.startClientY
  let x = state.origX
  let y = state.origY
  let width = state.origWidth
  let height = state.origHeight

  if (state.dir.includes('e')) width = state.origWidth + dx
  if (state.dir.includes('s')) height = state.origHeight + dy
  if (state.dir.includes('w')) {
    width = Math.max(20, state.origWidth - dx)
    x = state.origX + state.origWidth - width
  }
  if (state.dir.includes('n')) {
    height = Math.max(20, state.origHeight - dy)
    y = state.origY + state.origHeight - height
  }
  width = Math.max(20, width)
  height = Math.max(20, height)

  // 检查节点是否为定位节点（画布根级）
  const found = findNode(designerStore.schema, state.nodeId)
  if (found && !found.parent) {
    // 根级定位节点：宽度不能超出画布边界
    const baseSize = getBaseCanvasSize()
    if (state.dir.includes('e')) {
      width = Math.min(width, baseSize.width - state.origX)
    }
    if (state.dir.includes('w')) {
      // 向左调整时：x 不能小于 0，且宽度至少 20px
      const minX = 0
      if (x < minX) {
        width = state.origX + state.origWidth - minX
        x = minX
      }
    }
    // 定位节点：修改 x/y/width/height
    updateNodePosition(state.nodeId, x, y)
    // 仅当拖拽方向包含高度变化时才标记 heightAuto=false
    const heightChanged = state.dir.includes('s') || state.dir.includes('n')
    resizeNode(state.nodeId, width, height, heightChanged)
  } else if (found && found.parent) {
    const isPositionedChild = FREE_POSITION_CONTAINERS.has(found.parent.type)
    if (isPositionedChild) {
      // 自由定位容器内的子节点：按定位节点处理，修改 x/y/width/height
      const containerRect = getContainerRect(found.parent)
      if (state.dir.includes('e')) {
        width = Math.min(width, containerRect.width - state.origX)
      }
      if (state.dir.includes('s')) {
        height = Math.min(height, containerRect.height - state.origY)
      }
      if (state.dir.includes('w')) {
        if (x < 0) {
          width = state.origX + state.origWidth
          x = 0
        }
      }
      if (state.dir.includes('n')) {
        if (y < 0) {
          height = state.origY + state.origHeight
          y = 0
        }
      }
      updateNodePosition(state.nodeId, x, y)
      resizeNode(state.nodeId, width, height)
    } else {
      // 普通容器内子节点：宽度不能超出容器边界
      if (FREE_POSITION_CONTAINERS.has(found.parent.type)) {
        const containerRect = getContainerRect(found.parent)
        if (state.dir.includes('e')) {
          width = Math.min(width, containerRect.width - state.origX)
        }
        if (state.dir.includes('w')) {
          if (x < 0) {
            width = state.origX + state.origWidth
            x = 0
          }
        }
      }
      // 非定位子节点：修改 CSS width/height（不影响 x/y）
      updateNodeCss(state.nodeId, 'width', `${Math.round(width)}px`)
      updateNodeCss(state.nodeId, 'height', `${Math.round(height)}px`)
    }
  } else {
    // 非定位子节点：修改 CSS width/height（不影响 x/y）
    updateNodeCss(state.nodeId, 'width', `${Math.round(width)}px`)
    updateNodeCss(state.nodeId, 'height', `${Math.round(height)}px`)
  }
}

function onResizeEnd(): void {
  window.removeEventListener('mousemove', onResizeMove)
  window.removeEventListener('mouseup', onResizeEnd)
  resizeState.value = null
}

/** 查找落点下的容器：先用 DOM elementFromPoint 查找最深层容器，回退到根级 AABB 检测 */
function findContainerAt(x: number, y: number): ComponentSchema | null {
  // 方案1：通过 DOM elementFromPoint 查找鼠标下最深层容器节点
  const canvasRect = canvasPageRef.value?.getBoundingClientRect()
  if (canvasRect) {
    const el = document.elementFromPoint(
      canvasRect.left + x,
      canvasRect.top + y
    )
    if (el) {
      // 从内到外查找最近的容器 canvas-node
      let cur: HTMLElement | null = el.closest('.canvas-node')
      while (cur) {
        const id = cur.id?.replace('canvas-node-', '')
        if (id) {
          const found = findNode(designerStore.schema, id)
          if (found && getConfig(found.node.type)?.isContainer) {
            return found.node
          }
        }
        cur = cur.parentElement?.closest('.canvas-node') ?? null
      }
    }
  }
  // 方案2：回退到根级 AABB 检测
  for (let i = designerStore.schema.length - 1; i >= 0; i -= 1) {
    const node = designerStore.schema[i]
    if (!getConfig(node.type)?.isContainer) continue
    const nx = node.x ?? 0
    const ny = node.y ?? 0
    const nw = node.width ?? 240
    const nh = node.height ?? 120
    if (x >= nx && x <= nx + nw && y >= ny && y <= ny + nh) return node
  }
  return null
}

function onCanvasDragOver(e: DragEvent): void {
  e.preventDefault()
  emptyDropActive.value = true
}

function onCanvasDragLeave(): void {
  emptyDropActive.value = false
}

function onCanvasDrop(e: DragEvent): void {
  e.preventDefault()
  emptyDropActive.value = false
  const materialType = e.dataTransfer?.getData('application/x-material')
  const nodeId = e.dataTransfer?.getData('application/x-node-id')
  if (!materialType && !nodeId) return

  const target = e.currentTarget as HTMLElement
  const rect = target.getBoundingClientRect()
  const dropX = e.clientX - rect.left
  const dropY = e.clientY - rect.top

  // 落点在容器内 → 作为容器的子节点（容器维度）
  const container = findContainerAt(dropX, dropY)

  if (materialType) {
    if (container) {
      // 获取容器在画布中的实际位置和尺寸（从 DOM 读取）
      const containerRect = getContainerRect(container)
      const isFreePos = FREE_POSITION_CONTAINERS.has(container.type)

      addNode(materialType, container.id, container.children.length)
      const added = container.children[container.children.length - 1]
      if (added) {
        let w = added.width ?? 240
        let h = added.height ?? 120
        const containerBounds = {
          width: containerRect.width,
          height: containerRect.height,
        }
        if (isFreePos) {
          // 控件尺寸大于容器时收缩以适应容器（避免溢出导致无法拖动）
          const fitW = Math.min(w, Math.max(60, containerBounds.width - 8))
          const fitH = Math.min(h, Math.max(60, containerBounds.height - 8))
          if (fitW !== w) {
            added.width = fitW
            w = fitW
          }
          if (fitH !== h) {
            added.height = fitH
            h = fitH
          }
        }
        // 自由定位容器：坐标从 0 开始；普通容器：坐标从容器偏移开始
        const relDropX = isFreePos ? dropX - containerRect.left : dropX
        const relDropY = isFreePos ? dropY - containerRect.top : dropY
        // 使用参数化函数寻找放置位置
        const pos = findNonOverlappingPosUtil(
          relDropX,
          relDropY,
          w,
          h,
          new Set([added.id]),
          container.children ?? [],
          containerBounds
        )
        // 最终重叠检查：找不到不重叠位置则拒绝放置
        if (
          isOverlappingUtil(
            pos.x,
            pos.y,
            w,
            h,
            new Set([added.id]),
            container.children ?? []
          )
        ) {
          removeNode(added.id)
          showOverlapWarning()
          return
        }
        updateNodePosition(added.id, pos.x, pos.y)
      }
    } else {
      // 画布根级放置
      const cfg = getConfig(materialType)
      const defaultW =
        typeof cfg?.defaultProps?.width === 'number'
          ? cfg.defaultProps.width
          : 240
      const defaultH =
        typeof cfg?.defaultProps?.height === 'number'
          ? cfg.defaultProps.height
          : 120
      const baseSize = getBaseCanvasSize()
      const pos = findNonOverlappingPosUtil(
        dropX,
        dropY,
        defaultW,
        defaultH,
        new Set(),
        designerStore.schema,
        baseSize,
        true // allowExpandY
      )
      if (isOutOfBounds(pos.x, pos.y, defaultW, baseSize, true)) {
        showBoundaryWarning()
        return
      }
      addNode(materialType, null, designerStore.schema.length)
      const added = designerStore.schema[designerStore.schema.length - 1]
      if (added) {
        updateNodePosition(added.id, pos.x, pos.y)
      }
    }
  } else if (nodeId) {
    if (container && container.id !== nodeId) {
      moveNode(nodeId, container.id, container.children.length)
      const movedResult = findNode(designerStore.schema, nodeId)
      if (movedResult) {
        const containerRect = getContainerRect(container)
        const isFreePos = FREE_POSITION_CONTAINERS.has(container.type)
        const containerBounds = {
          width: containerRect.width,
          height: containerRect.height,
        }
        let w = movedResult.node.width ?? 240
        let h = movedResult.node.height ?? 120
        if (isFreePos) {
          // 控件尺寸大于容器时收缩以适应容器
          const fitW = Math.min(w, Math.max(60, containerBounds.width - 8))
          const fitH = Math.min(h, Math.max(60, containerBounds.height - 8))
          if (fitW !== w) {
            movedResult.node.width = fitW
            w = fitW
          }
          if (fitH !== h) {
            movedResult.node.height = fitH
            h = fitH
          }
        }
        const relDropX = isFreePos ? dropX - containerRect.left : dropX
        const relDropY = isFreePos ? dropY - containerRect.top : dropY
        const pos = findNonOverlappingPosUtil(
          relDropX,
          relDropY,
          w,
          h,
          new Set([nodeId]),
          container.children ?? [],
          containerBounds
        )
        updateNodePosition(nodeId, pos.x, pos.y)
      }
    } else if (!container) {
      // 用 findNode 递归搜索（控件可能在容器内嵌套）
      const movedResult = findNode(designerStore.schema, nodeId)
      if (movedResult) {
        const w = movedResult.node.width ?? 240
        const h = movedResult.node.height ?? 120
        const baseSize = getBaseCanvasSize()
        const pos = findNonOverlappingPosUtil(
          dropX,
          dropY,
          w,
          h,
          new Set([nodeId]),
          designerStore.schema,
          baseSize,
          true
        )
        if (isOutOfBounds(pos.x, pos.y, w, baseSize, true)) {
          showBoundaryWarning()
          return
        }
        moveNode(nodeId, null, designerStore.schema.length)
        updateNodePosition(nodeId, pos.x, pos.y)
      }
    }
  }
}
watch(
  () => designerStore.themeId,
  async (themeId) => {
    if (!canvasPageRef.value) return
    if (!themeId) {
      clearTheme(canvasPageRef.value)
      return
    }
    try {
      const theme = await getThemeDetail(themeId)
      applyTheme(canvasPageRef.value, theme)
    } catch {
      // ignore
    }
  }
)

onMounted(async () => {
  if (canvasPageRef.value && designerStore.themeId) {
    try {
      const theme = await getThemeDetail(designerStore.themeId)
      applyTheme(canvasPageRef.value, theme)
    } catch {
      // ignore
    }
  }
})
</script>

<template>
  <main class="canvas-panel" @click="onBackgroundClick">

    <div class="canvas-scroll" ref="canvasScrollRef">
      <div
        class="canvas-page"
        ref="canvasPageRef"
        :class="{
          'canvas-drop-active': emptyDropActive,
          'lc-theme-page': true,
          'canvas-mobile-frame':
            designerStore.mode === 'biz' && designerStore.bizMode === 'mobile',
        }"
        :style="pageStyle"
        @mousedown.self="onPageMouseDown"
        @dragover="onCanvasDragOver"
        @dragleave="onCanvasDragLeave"
        @drop="onCanvasDrop"
      >
        <CanvasNode
          v-for="(node, i) in designerStore.schema"
          :key="node.id"
          :node="node"
          :parent-id="null"
          :index="i"
          :positioned="true"
          :selected="isNodeSelected(node.id)"
          @drag-start="onNodeDragStart"
          @resize-start="onNodeResizeStart"
          @select-single="onSelectSingle"
        />

        <div v-if="isEmpty" class="canvas-empty">
          {{ t('从左侧拖拽组件到这里开始搭建页面') }}
        </div>

        <template v-if="dragState">
          <div
            v-for="guide in dragState.guides"
            :key="`${guide.type}-${guide.pos}`"
            class="guide-line"
            :class="guide.type"
            :style="
              guide.type === 'v'
                ? { left: guide.pos + 'px' }
                : { top: guide.pos + 'px' }
            "
          />
        </template>

        <div v-if="marquee" class="marquee-box" :style="marqueeStyle" />
      </div>
    </div>

    <!-- 层级面包屑 -->
    <div class="breadcrumb-bar">
      <template v-for="(item, i) in breadcrumbPath" :key="item.id ?? 'page'">
        <span v-if="i > 0" class="breadcrumb-sep">→</span>
        <span
          class="breadcrumb-item"
          :class="{ 'breadcrumb-active': i === breadcrumbPath.length - 1 }"
          @click="onBreadcrumbClick(item)"
          >{{ item.label }}</span
        >
      </template>
    </div>

    <!-- 重叠提示 -->
    <Transition name="overlap-fade">
      <div v-if="overlapWarning" class="overlap-warning">
        {{ t('该位置已有控件，无法放置，请选择空白区域') }}
      </div>
    </Transition>

    <!-- 边界提示 -->
    <Transition name="overlap-fade">
      <div v-if="boundaryWarning" class="overlap-warning">
        {{ t('超出画布边界，已撤销此操作') }}
      </div>
    </Transition>
  </main>
</template>

<style scoped>
.canvas-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: #e8ebef;
  overflow: hidden;
}
.canvas-title {
  flex: 0 0 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  color: #606266;
  border-bottom: 1px solid #e4e7ed;
}
.canvas-scroll {
  flex: 1;
  overflow: auto;
  padding: 10px;
}
.canvas-mobile-frame {
  max-width: 375px;
  margin: 0 auto;
  min-height: 667px;
  border: 8px solid #2c3e50;
  border-radius: 28px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  box-sizing: border-box;
}
.breadcrumb-bar {
  flex: 0 0 30px;
  display: flex;
  align-items: center;
  padding: 0 12px;
  background: #f5f7fa;
  border-top: 1px solid #e4e7ed;
  font-size: 12px;
  color: #606266;
  overflow: hidden;
  white-space: nowrap;
}
.breadcrumb-item {
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 3px;
  transition: background 0.15s;
}
.breadcrumb-item:hover {
  background: #e6f0ff;
  color: #1976d2;
}
.breadcrumb-active {
  color: #1976d2;
  font-weight: 600;
}
.breadcrumb-sep {
  margin: 0 2px;
  color: #c0c4cc;
}
.canvas-page {
  position: relative;
  margin: 0 auto;
  min-width: 900px;
  min-height: 700px;
  border: 1px solid #dfe3e8;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  transition: border-color 0.15s;
}
.canvas-drop-active {
  border-color: #1976d2;
}
.canvas-empty {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #c0c4cc;
  text-align: center;
  font-size: 13px;
  pointer-events: none;
}
.guide-line {
  position: absolute;
  z-index: 30;
  pointer-events: none;
}
.guide-line.v {
  top: 0;
  bottom: 0;
  border-left: 1px dashed #1976d2;
}
.guide-line.h {
  left: 0;
  right: 0;
  border-top: 1px dashed #1976d2;
}
.marquee-box {
  position: absolute;
  z-index: 25;
  pointer-events: none;
  border: 1px solid #1976d2;
  background: rgba(25, 118, 210, 0.08);
}
.overlap-warning {
  position: fixed;
  top: 60px;
  left: 50%;
  transform: translateX(-50%);
  background: #fef0f0;
  color: #f56c6c;
  border: 1px solid #fbc4c4;
  border-radius: 6px;
  padding: 10px 20px;
  font-size: 13px;
  z-index: 10000;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}
.overlap-fade-enter-active,
.overlap-fade-leave-active {
  transition: opacity 0.3s, transform 0.3s;
}
.overlap-fade-enter-from,
.overlap-fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(-10px);
}
</style>
