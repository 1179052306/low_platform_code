/**
 * 画布工具函数 — 参数化版本，支持根画布和容器内部嵌套画布
 *
 * 所有函数接受 `siblings`（兄弟节点列表）和 `bounds`（边界尺寸）参数，
 * 不再硬编码 designerStore.schema 或 canvasPageRef。
 */
import type { ComponentSchema } from '@/types/schema'

export interface GuideLine {
  type: 'v' | 'h'
  pos: number
}

export interface Bounds {
  width: number
  height: number
}

export const SNAP_DISTANCE = 5
export const ROW_STEP = 10
export const GAP = 4

/** 获取节点的位置和尺寸（带默认值） */
export function getNodeRect(node: ComponentSchema): { x: number; y: number; w: number; h: number } {
  return {
    x: node.x ?? 0,
    y: node.y ?? 0,
    w: node.width ?? 240,
    h: node.height ?? 120,
  }
}

/**
 * 计算拖拽吸附后的位置，并返回需要显示的辅助线
 * @param siblings 同层的兄弟节点列表（用于对齐目标）
 * @param bounds 画布/容器的边界尺寸（用于边界吸附）
 */
export function snapRect(
  x: number,
  y: number,
  width: number,
  height: number,
  excludeIds: Set<string>,
  siblings: ComponentSchema[],
  bounds: Bounds,
): { x: number; y: number; guides: GuideLine[] } {
  const guides: GuideLine[] = []
  const xs = new Set<number>([0, bounds.width])
  const ys = new Set<number>([0, bounds.height])

  for (const node of siblings) {
    if (excludeIds.has(node.id)) continue
    const { x: nx, y: ny, w: nw, h: nh } = getNodeRect(node)
    xs.add(nx).add(nx + nw / 2).add(nx + nw)
    ys.add(ny).add(ny + nh / 2).add(ny + nh)
  }

  let snapX: number | null = null
  let guideX: number | null = null
  let minDx = SNAP_DISTANCE + 1
  for (const target of xs) {
    const candidates = [
      { edge: x, offset: 0 },
      { edge: x + width / 2, offset: width / 2 },
      { edge: x + width, offset: width },
    ]
    for (const c of candidates) {
      const d = Math.abs(c.edge - target)
      if (d < minDx) {
        minDx = d
        snapX = target - c.offset
        guideX = target
      }
    }
  }
  if (snapX !== null) {
    x = snapX
    guides.push({ type: 'v', pos: guideX as number })
  }

  let snapY: number | null = null
  let guideY: number | null = null
  let minDy = SNAP_DISTANCE + 1
  for (const target of ys) {
    const candidates = [
      { edge: y, offset: 0 },
      { edge: y + height / 2, offset: height / 2 },
      { edge: y + height, offset: height },
    ]
    for (const c of candidates) {
      const d = Math.abs(c.edge - target)
      if (d < minDy) {
        minDy = d
        snapY = target - c.offset
        guideY = target
      }
    }
  }
  if (snapY !== null) {
    y = snapY
    guides.push({ type: 'h', pos: guideY as number })
  }

  return { x, y, guides }
}

/** 检查矩形是否与兄弟节点重叠 */
export function isOverlapping(
  x: number,
  y: number,
  w: number,
  h: number,
  excludeIds: Set<string>,
  siblings: ComponentSchema[],
): boolean {
  for (const node of siblings) {
    if (excludeIds.has(node.id)) continue
    const { x: nx, y: ny, w: nw, h: nh } = getNodeRect(node)
    if (x < nx + nw && x + w > nx && y < ny + nh && y + h > ny) return true
  }
  return false
}

/** 寻找不与现有控件重叠的放置位置（行扫描） */
export function findNonOverlappingPosition(
  dropX: number,
  dropY: number,
  w: number,
  h: number,
  excludeIds: Set<string>,
  siblings: ComponentSchema[],
  bounds: Bounds,
  allowExpandY = false,
): { x: number; y: number } {
  const maxX = Math.max(0, bounds.width - w)
  const maxY = allowExpandY ? Math.max(0, bounds.height + 2000) : Math.max(0, bounds.height - h)

  const obstacles: { x: number; y: number; w: number; h: number }[] = []
  for (const node of siblings) {
    if (excludeIds.has(node.id)) continue
    obstacles.push(getNodeRect(node))
  }

  let x = Math.max(0, Math.min(dropX - w / 2, maxX))
  let y = Math.max(0, Math.min(dropY - h / 2, maxY))
  if (!isOverlapping(x, y, w, h, excludeIds, siblings)) return { x, y }

  for (let rowY = Math.max(0, dropY - h); rowY <= maxY; rowY += ROW_STEP) {
    for (let rowX = 0; rowX <= maxX; rowX += ROW_STEP) {
      let collides = false
      for (const obs of obstacles) {
        if (
          rowX < obs.x + obs.w + GAP &&
          rowX + w + GAP > obs.x &&
          rowY < obs.y + obs.h + GAP &&
          rowY + h + GAP > obs.y
        ) {
          collides = true
          break
        }
      }
      if (!collides) return { x: rowX, y: rowY }
    }
  }
  return { x: Math.max(0, Math.min(dropX - w / 2, maxX)), y: maxY }
}

/** 从 DOM 元素获取边界尺寸 */
export function getBoundsFromEl(el: HTMLElement | null, fallback: Bounds): Bounds {
  if (el) return { width: el.clientWidth, height: el.clientHeight }
  return fallback
}

/** 鼠标事件转换为元素内相对坐标 */
export function pointFromElement(element: HTMLElement, e: MouseEvent): { x: number; y: number } {
  const rect = element.getBoundingClientRect()
  return { x: e.clientX - rect.left, y: e.clientY - rect.top }
}

/**
 * 检查拖拽位置是否超出边界
 * @returns 超出边界时返回 true
 */
export function isOutOfBounds(
  x: number,
  y: number,
  w: number,
  bounds: Bounds,
  allowExpandY = false,
): boolean {
  if (x < 0 || x + w > bounds.width) return true
  if (y < 0) return true
  if (!allowExpandY && y + 0 > bounds.height) return true
  return false
}
