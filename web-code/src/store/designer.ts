import { reactive, computed, ref, type InjectionKey } from 'vue'
import type { ComponentSchema, PageSchema, PageCss, NodeCss, DefaultChildNode, BizSchemas, PageType } from '@/types/schema'
import { getConfig } from '@/materials'
import {
  LAYOUT_CONTAINER_TYPES,
  STRUCTURAL_CHILD_TYPES,
  FREE_POSITION_CONTAINERS,
  GRID_SECTION_TYPES,
  PROTECTED_LAYOUT_TYPES,
  LAYOUT_SECTION_TYPES,
  NO_SELF_NESTING_TYPES,
} from '@/constants/container-types'

let idSeed = 0

/** 生成唯一节点 id */
export function genId(): string {
  idSeed += 1
  return `node_${Date.now().toString(36)}_${idSeed}`
}

/** 递归重置节点树中所有 id */
function regenerateIds(node: ComponentSchema): void {
  node.id = genId()
  for (const child of node.children) {
    regenerateIds(child)
  }
}

/** 深拷贝节点（重置 id 由调用方决定） */
export function cloneNode(node: ComponentSchema, regenerateId = true): ComponentSchema {
  const copy: ComponentSchema = JSON.parse(JSON.stringify(node))
  if (regenerateId) {
    regenerateIds(copy)
  }
  return copy
}

/** 在节点树中查找节点及其父节点 */
export function findNode(
  list: ComponentSchema[],
  id: string,
  parent: ComponentSchema | null = null,
): { node: ComponentSchema; parent: ComponentSchema | null; list: ComponentSchema[]; index: number } | null {
  for (let i = 0; i < list.length; i++) {
    const node = list[i]
    if (node.id === id) return { node, parent, list, index: i }
    if (node.children.length) {
      const found = findNode(node.children, id, node)
      if (found) return found
    }
  }
  return null
}

/** 判断 targetId 是否为 nodeId 的后代（防止拖入自身子孙） */
function isDescendant(node: ComponentSchema, targetId: string): boolean {
  for (const child of node.children) {
    if (child.id === targetId || isDescendant(child, targetId)) return true
  }
  return false
}

/** 判断节点是否为受保护布局容器的直接子节点（需要保留结构完整性） */
export function isProtectedChild(id: string): boolean {
  const result = findNode(designerStore.schema, id)
  if (!result || !result.parent) return false
  return PROTECTED_LAYOUT_TYPES.has(result.parent.type)
}

/** 获取受保护子节点的父容器类型（用于提示信息） */
export function getProtectedContainerType(id: string): string | null {
  const result = findNode(designerStore.schema, id)
  if (!result || !result.parent) return null
  return PROTECTED_LAYOUT_TYPES.has(result.parent.type) ? result.parent.type : null
}

/** 判断将 type 类型节点加入 parentId 父节点是否会产生同类型布局嵌套（被禁止）。
 *  检查整条祖先链：例如 el-row → el-col → el-row 也属于栅格嵌套栅格，同样禁止。 */
function isForbiddenSelfNesting(type: string, parentId: string | null): boolean {
  if (parentId === null) return false
  if (!NO_SELF_NESTING_TYPES.has(type)) return false
  // 向上遍历祖先链，检查是否存在同类型布局容器
  let currentParentId: string | null = parentId
  while (currentParentId !== null) {
    const parentFound = findNode(designerStore.schema, currentParentId)
    if (!parentFound) break
    if (parentFound.node.type === type) return true
    currentParentId = parentFound.parent ? parentFound.parent.id : null
  }
  return false
}

/** 全局设计器状态 */
export const designerStore = reactive({
  pageId: `page_${Date.now().toString(36)}`,
  pageName: '未命名页面',
  /** 页面对应的数据表名 */
  tableName: '' as string,
  /** 主键字段名 */
  primaryKeyField: '' as string,
  /** 页面类型：custom=自定义，document=单据，basedata=基础资料 */
  pageType: 'custom' as PageType,
  pageCss: { background: '#ffffff' } as PageCss,
  themeId: undefined as string | undefined,
  schema: ref<ComponentSchema[]>([]),
  selectedId: null as string | null,
  /** 多选集合：批量删除/复制/剪切/粘贴的操作对象（含框选） */
  selectedIds: [] as string[],
  /** 属性或结构变化时自增，用于让画布/预览组件重新绑定 props */
  schemaRevision: 0,
  /** 剪贴板中的节点集合（支持多选复制/剪切，内部剪贴板） */
  clipboardNodes: [] as ComponentSchema[],
  /** 预览模式 */
  previewMode: false,
  /** 自定义拖拽是否正在进行（防止 HTML5 原生拖拽干扰） */
  customDragActive: false,
  /** 边界警告触发计数器（递增以触发警告显示） */
  boundaryWarningTick: 0,
  /** 设计器形态：default=自定义页面设计器，biz=业务页面设计器（单据/基础资料） */
  mode: 'default' as 'default' | 'biz',
  /** 业务形态下的子模式：form=表单页面，table=表格页面，mobile=移动端页面 */
  bizMode: 'form' as 'form' | 'table' | 'mobile',
  /** 业务形态三种子模式的独立页面内容（切换模式时 swap schema） */
  bizSchemas: {
    form: [] as ComponentSchema[],
    table: [] as ComponentSchema[],
    mobile: [] as ComponentSchema[],
  } as BizSchemas,
})

/** 历史记录（撤销/重做） */
const history = reactive({
  undoStack: [] as string[],
  redoStack: [] as string[],
})

function snapshot(): string {
  return JSON.stringify({
    schema: designerStore.schema,
    pageName: designerStore.pageName,
    tableName: designerStore.tableName,
    primaryKeyField: designerStore.primaryKeyField,
    pageCss: designerStore.pageCss,
    themeId: designerStore.themeId,
    pageId: designerStore.pageId,
  })
}

/** 记录一次历史快照（在变更前调用） */
export function pushHistory(): void {
  history.undoStack.push(snapshot())
  if (history.undoStack.length > 50) history.undoStack.shift()
  history.redoStack = []
}

export function undo(): void {
  const prev = history.undoStack.pop()
  if (!prev) return
  history.redoStack.push(snapshot())
  const state = JSON.parse(prev)
  designerStore.schema = state.schema ?? []
  designerStore.pageName = state.pageName ?? '未命名页面'
  designerStore.tableName = state.tableName ?? ''
  designerStore.primaryKeyField = state.primaryKeyField ?? ''
  designerStore.pageCss = state.pageCss ?? { background: '#ffffff' }
  designerStore.themeId = state.themeId
  designerStore.pageId = state.pageId ?? designerStore.pageId
  // 迁移属性：撤销到旧快照时也补全新增属性
  collectAllNodes(designerStore.schema).forEach(migrateNodeProps)
  designerStore.selectedId = null
  designerStore.selectedIds = []
}

export function redo(): void {
  const next = history.redoStack.pop()
  if (!next) return
  history.undoStack.push(snapshot())
  const state = JSON.parse(next)
  designerStore.schema = state.schema ?? []
  designerStore.pageName = state.pageName ?? '未命名页面'
  designerStore.tableName = state.tableName ?? ''
  designerStore.primaryKeyField = state.primaryKeyField ?? ''
  designerStore.pageCss = state.pageCss ?? { background: '#ffffff' }
  designerStore.themeId = state.themeId
  designerStore.pageId = state.pageId ?? designerStore.pageId
  // 迁移属性：重做时也补全新增属性
  collectAllNodes(designerStore.schema).forEach(migrateNodeProps)
  designerStore.selectedId = null
  designerStore.selectedIds = []
}

export const canUndo = computed(() => history.undoStack.length > 0)
export const canRedo = computed(() => history.redoStack.length > 0)

/** 收集节点树中的所有节点（含自身） */
export function collectAllNodes(list: ComponentSchema[]): ComponentSchema[] {
  const result: ComponentSchema[] = []
  const walk = (nodes: ComponentSchema[]) => {
    for (const node of nodes) {
      result.push(node)
      if (node.children.length) walk(node.children)
    }
  }
  walk(list)
  return result
}

/** 按 fieldName 查找节点（纯函数，在指定 schema 中查找） */
export function findNodeByFieldNameIn(
  schema: ComponentSchema[],
  fieldName: string,
): ComponentSchema | null {
  const all = collectAllNodes(schema)
  return all.find((n) => n.fieldName === fieldName) ?? null
}

/** 按 fieldName 查找节点（使用全局 store，向后兼容） */
export function findNodeByFieldName(fieldName: string): ComponentSchema | null {
  return findNodeByFieldNameIn(designerStore.schema, fieldName)
}

/** 根据类型生成不重复的默认字段名称 */
export function nextFieldName(type: string, extraUsed?: Set<string>): string {
  const all = collectAllNodes(designerStore.schema)
  const used = new Set(all.map((node) => node.fieldName).filter(Boolean) as string[])
  if (extraUsed) {
    for (const name of extraUsed) used.add(name)
  }
  const base = type.replace(/[^a-zA-Z0-9]/g, '_')
  let index = 1
  let name = `${base}_${index}`
  while (used.has(name)) {
    index += 1
    name = `${base}_${index}`
  }
  return name
}

/** 为新节点计算画布上的默认位置（避免全部叠在一起） */
function nextRootPosition(list: ComponentSchema[]): { x: number; y: number } {
  const count = list.length
  return {
    x: 24 + (count % 6) * 24,
    y: 24 + (count % 6) * 24,
  }
}

/** 根据物料默认 props 估算控件实际尺寸 */
function defaultNodeSize(type: string): { width: number; height: number } {
  const cfg = getConfig(type)
  const props = cfg?.defaultProps ?? {}
  const compactHeightTypes = new Set([
    'link',
    'el-row',
    'el-col',
    'dx-button',
    'dx-text-box',
    'dx-number-box',
    'dx-check-box',
    'dx-switch',
    'dx-slider',
    'dx-range-slider',
    'dx-date-box',
    'dx-color-box',
    'dx-select-box',
    'dx-lookup',
    'dx-tag-box',
    'dx-autocomplete',
    'dx-radio-group',
    'dx-drop-down-button',
    'dx-drop-down-box',
    'dx-recurrence-editor',
    'dx-progress-bar',
    'dx-base-data',
  ])
  const toNumber = (value: unknown): number | undefined => {
    if (typeof value === 'number' && Number.isFinite(value)) return value
    if (typeof value === 'string' && value.trim() && Number.isFinite(Number(value))) return Number(value)
    return undefined
  }
  const size = props.size && typeof props.size === 'object' && !Array.isArray(props.size)
    ? (props.size as Record<string, unknown>)
    : {}
  const width = toNumber(props.width) ?? toNumber(size.width) ?? 240
  const fallbackHeight = compactHeightTypes.has(type) ? 36 : 120
  const height = toNumber(props.height) ?? toNumber(size.height) ?? fallbackHeight
  return { width, height }
}

/** 由物料类型创建新节点 */
export function createNode(type: string): ComponentSchema | null {
  const cfg = getConfig(type)
  if (!cfg) return null
  const pos = nextRootPosition(designerStore.schema)
  const size = defaultNodeSize(type)
  // 布局容器（el-row / el-splitter / el-tabs）默认高度自适应子节点，宽度默认 300px
  const isLayoutContainer = LAYOUT_CONTAINER_TYPES.has(type)
  // el-tabs 需要固定高度（flex 布局链需要确定高度），默认 200px
  const needsFixedHeight = type === 'el-tabs'
  // 过滤掉 defaultProps 中的 width/height，统一由尺寸 section 控制
  const { width: _dw, height: _dh, ...filteredProps } = cfg.defaultProps
  void _dw; void _dh
  const node: ComponentSchema = {
    id: genId(),
    type,
    props: filteredProps,
    children: [],
    fieldName: nextFieldName(type),
    x: pos.x,
    y: pos.y,
    width: isLayoutContainer ? 300 : size.width,
    height: needsFixedHeight ? 200 : size.height,
    heightAuto: isLayoutContainer ? true : undefined,
  }
  // 自动创建默认子项（Row / Splitter / Container 等布局控件，支持嵌套）
  if (cfg.defaultChildren) {
    node.children = createDefaultChildren(cfg.defaultChildren)
  }
  return node
}

/** 递归创建默认子节点树 */
function createDefaultChildren(defs: DefaultChildNode[]): ComponentSchema[] {
  const usedFieldNames = new Set<string>()
  return defs.map((child) => {
    const fieldName = nextFieldName(child.type, usedFieldNames)
    usedFieldNames.add(fieldName)
    const node: ComponentSchema = {
      id: genId(),
      type: child.type,
      props: { ...child.props },
      children: child.children ? createDefaultChildren(child.children) : [],
      fieldName,
    }
    if (child.sectionKey) node.sectionKey = child.sectionKey
    // section 子节点默认 padding/margin 为0
    if (STRUCTURAL_CHILD_TYPES.has(child.type)) {
      node.css = { padding: '0', margin: '0' }
    }
    return node
  })
}

/** 拖入容器时保存原有画布宽高，并设置 CSS width/height:100% 自适应 */
function applyContainerCss(node: ComponentSchema): void {
  if (GRID_SECTION_TYPES.has(node.type)) return
  // 保存原始画布宽高
  if (node.width !== undefined && node._origWidth === undefined) node._origWidth = node.width
  if (node.height !== undefined && node._origHeight === undefined) node._origHeight = node.height
  const css = { ...node.css }
  css.width = '100%'
  css.height = '100%'
  css.padding = css.padding ?? '0'
  css.margin = css.margin ?? '0'
  node.css = css
}

/** 添加节点：parentId 为 null 表示插入根列表，index 为插入位置；selectNew 控制是否选中新节点（默认 true） */
export function addNode(type: string, parentId: string | null, index: number, selectNew: boolean = true, overrideProps?: Record<string, unknown>): void {
  // 禁止同类型布局容器嵌套自身（栅格/分割面板/布局容器）
  if (isForbiddenSelfNesting(type, parentId)) {
    console.warn(`[低代码设计器] 禁止将 ${type} 嵌套到同类型的 ${type} 布局容器内。`)
    return
  }
  const node = createNode(type)
  if (!node) return
  // 应用覆盖的 props（用于布局子节点自定义初始属性）
  if (overrideProps) {
    Object.assign(node.props, overrideProps)
  }
  // 子控件默认内边距外边距设为0
  if (parentId !== null) {
    if (STRUCTURAL_CHILD_TYPES.has(type)) {
      // section 类型：宽高由 span/flex/width/height prop 控制，仅设置 padding/margin 为0
      node.css = { padding: '0', margin: '0' }
    } else {
      // 检查父容器是否支持自由定位
      const parent = parentId !== null ? findNode(designerStore.schema, parentId) : null
      if (parent && FREE_POSITION_CONTAINERS.has(parent.node.type)) {
        // 自由定位容器：保持原有宽高，设置初始 x/y，不需要 CSS 100%
        node.x = 8
        node.y = 8
        node.css = { padding: '0', margin: '0' }
      } else {
        // 普通容器：保存原有画布宽高用于拖出时恢复，CSS 宽高设为100%自适应
        if (node.width !== undefined) node._origWidth = node.width
        if (node.height !== undefined) node._origHeight = node.height
        node.css = { width: '100%', height: '100%', padding: '0', margin: '0' }
      }
    }
  }
  pushHistory()
  // el-splitter 只允许 el-splitter-panel 作为直接子节点
  if (parentId !== null && type !== 'el-splitter-panel') {
    const parentFound = findNode(designerStore.schema, parentId)
    if (parentFound && parentFound.node.type === 'el-splitter') {
      console.warn('[低代码设计器] 分割面板只能包含 el-splitter-panel，请将控件拖入 panel 内部。')
      return
    }
  }
  // el-tabs 只允许 el-tab-pane 作为直接子节点
  if (parentId !== null && type !== 'el-tab-pane') {
    const parentFound = findNode(designerStore.schema, parentId)
    if (parentFound && parentFound.node.type === 'el-tabs') {
      console.warn('[低代码设计器] 标签页只能包含 el-tab-pane，请将控件拖入 pane 内部。')
      return
    }
  }
  // 如果父节点是 el-row 且当前类型不是 el-col，自动包裹一个 el-col
  if (parentId !== null && type !== 'el-col') {
    const parentFound = findNode(designerStore.schema, parentId)
    if (parentFound && parentFound.node.type === 'el-row') {
      const col = createNode('el-col')
      if (col) {
        col.children.push(node)
        parentFound.node.children.splice(index, 0, col)
        if (selectNew) {
          designerStore.selectedId = node.id
          designerStore.selectedIds = [node.id]
        }
        return
      }
    }
  }
  if (parentId === null) {
    designerStore.schema.splice(index, 0, node)
  } else {
    const found = findNode(designerStore.schema, parentId)
    if (found) found.node.children.splice(index, 0, node)
  }
  if (selectNew) {
    designerStore.selectedId = node.id
    designerStore.selectedIds = [node.id]
  }
}

/** 移动节点到新位置（画布内拖拽排序 / 拖入容器） */
export function moveNode(nodeId: string, parentId: string | null, index: number): boolean {
  const found = findNode(designerStore.schema, nodeId)
  if (!found) return false
  // 不能拖入自身或自身后代
  if (parentId === nodeId) return false
  if (parentId !== null) {
    const parentFound = findNode(designerStore.schema, parentId)
    if (!parentFound || isDescendant(found.node, parentId)) return false
  }
  // 禁止结构子节点（el-col/el-splitter-panel 等）移动到画布根级
  if (parentId === null && LAYOUT_SECTION_TYPES.has(found.node.type)) {
    console.warn(`[低代码设计器] 禁止将布局结构子节点 ${found.node.type} 移动到画布根级。`)
    return false
  }
  // el-splitter 只允许 el-splitter-panel 作为直接子节点
  if (parentId !== null && found.node.type !== 'el-splitter-panel') {
    const parentFound = findNode(designerStore.schema, parentId)
    if (parentFound && parentFound.node.type === 'el-splitter') {
      console.warn('[低代码设计器] 分割面板只能包含 el-splitter-panel，请将控件拖入 panel 内部。')
      return false
    }
  }
  // 禁止同类型布局容器嵌套自身（栅格/分割面板/布局容器）
  if (isForbiddenSelfNesting(found.node.type, parentId)) {
    console.warn(`[低代码设计器] 禁止将 ${found.node.type} 嵌套到同类型的 ${found.node.type} 布局容器内。`)
    return false
  }
  pushHistory()
  // 移动到画布根级时，清除容器布局设置的 CSS width/height:100%，恢复原有画布宽高
  if (parentId === null) {
    const css = { ...found.node.css }
    let cssChanged = false
    if (css.width === '100%') { delete css.width; cssChanged = true }
    if (css.height === '100%') { delete css.height; cssChanged = true }
    if (cssChanged) found.node.css = Object.keys(css).length ? css : undefined
    // 恢复拖入容器前的原始画布宽高
    if (found.node._origWidth !== undefined) {
      found.node.width = found.node._origWidth
      delete found.node._origWidth
    }
    if (found.node._origHeight !== undefined) {
      found.node.height = found.node._origHeight
      delete found.node._origHeight
    }
  }
  // 先移除
  found.list.splice(found.index, 1)
  // 修正移除后目标位置索引（同一列表且原位置在目标前）
  let insertIndex = index
  if (parentId === null && found.parent === null && found.index < index) insertIndex -= 1
  if (
    parentId !== null &&
    found.parent !== null &&
    found.parent.id === parentId &&
    found.index < index
  ) {
    insertIndex -= 1
  }
  if (parentId === null) {
    designerStore.schema.splice(insertIndex, 0, found.node)
  } else {
    const parentFound = findNode(designerStore.schema, parentId)
    if (parentFound) {
      // 如果父节点是 el-row 且当前节点不是 el-col，自动包裹一个 el-col
      if (parentFound.node.type === 'el-row' && found.node.type !== 'el-col') {
        const col = createNode('el-col')
        if (col) {
          col.children.push(found.node)
          parentFound.node.children.splice(insertIndex, 0, col)
          // 设置自适应宽高
          applyContainerCss(found.node)
          designerStore.selectedId = nodeId
          designerStore.selectedIds = [nodeId]
          return true
        }
      }
      // 拖入容器时设置自适应宽高并保存原始尺寸
      if (FREE_POSITION_CONTAINERS.has(parentFound.node.type)) {
        // 自由定位容器：保持原有宽高，设置初始 x/y
        found.node.x = 8
        found.node.y = 8
        if (found.node.css?.width === '100%') {
          const css = { ...found.node.css }
          delete css.width
          delete css.height
          found.node.css = Object.keys(css).length ? css : undefined
        }
        // 恢复原始画布宽高
        if (found.node._origWidth !== undefined) {
          found.node.width = found.node._origWidth
          delete found.node._origWidth
        }
        if (found.node._origHeight !== undefined) {
          found.node.height = found.node._origHeight
          delete found.node._origHeight
        }
      } else {
        applyContainerCss(found.node)
      }
      parentFound.node.children.splice(insertIndex, 0, found.node)
    }
  }
  designerStore.selectedId = nodeId
  designerStore.selectedIds = [nodeId]
  return true
}

/** 判断当前是否为表格模式（表格模式的根级 dx-data-grid 不可删除） */
function isTableMode(): boolean {
  return designerStore.mode === 'biz' && designerStore.bizMode === 'table'
}

/** 删除节点 */
export function removeNode(id: string): void {
  const found = findNode(designerStore.schema, id)
  if (!found) return
  // 表格模式：禁止删除根级 dx-data-grid（保证始终有一个表格）
  if (isTableMode() && !found.parent && found.node.type === 'dx-data-grid') {
    console.warn('[低代码设计器] 表格模式下的数据表格不可删除。')
    return
  }
  // 如果是 el-splitter-panel 且父节点是 el-splitter，检查是否会导致少于2个面板
  if (found.node.type === 'el-splitter-panel' && found.parent) {
    const splitterChildren = found.parent.children
    if (splitterChildren.length <= 2) {
      // 不允许删除，因为分割面板至少需要2个panel才有意义
      // 但可以删除整个 splitter 容器
      console.warn('[低代码设计器] 分割面板至少需要 2 个面板才有意义，无法删除。')
      return
    }
  }
  pushHistory()
  found.list.splice(found.index, 1)
  if (designerStore.selectedId === id) designerStore.selectedId = null
  designerStore.selectedIds = designerStore.selectedIds.filter((x) => x !== id)
}

/** 批量删除节点（含容器维度：删除容器会连同其所有子节点一起删除） */
export function removeNodes(ids: string[]): void {
  const toRemove = new Set(ids)
  if (!toRemove.size) return
  // 表格模式：禁止删除根级 dx-data-grid（保证始终有一个表格）
  if (isTableMode()) {
    for (const id of ids) {
      const found = findNode(designerStore.schema, id)
      if (found && !found.parent && found.node.type === 'dx-data-grid') {
        console.warn('[低代码设计器] 表格模式下的数据表格不可删除。')
        return
      }
    }
  }
  // 检查是否会导致 splitter 面板少于2个
  for (const id of ids) {
    const found = findNode(designerStore.schema, id)
    if (found && found.node.type === 'el-splitter-panel' && found.parent) {
      // 检查删除后的子节点数量
      const wouldRemoveCount = found.parent.children.filter(
        (c) => toRemove.has(c.id) || c.id === id,
      ).length
      if (found.parent.children.length - wouldRemoveCount < 2) {
        // 不允许删除，因为分割面板至少需要2个panel才有意义
        console.warn('[低代码设计器] 分割面板至少需要 2 个面板才有意义，无法删除。')
        return
      }
    }
  }
  pushHistory()
  let removed = false
  const removeInList = (list: ComponentSchema[]) => {
    for (let i = list.length - 1; i >= 0; i--) {
      if (toRemove.has(list[i].id)) {
        list.splice(i, 1)
        removed = true
      } else {
        removeInList(list[i].children)
      }
    }
  }
  removeInList(designerStore.schema)
  if (!removed) {
    // 没有任何节点被删除，回滚空快照
    history.undoStack.pop()
    return
  }
  designerStore.selectedIds = designerStore.selectedIds.filter((id) => !toRemove.has(id))
  if (designerStore.selectedId && toRemove.has(designerStore.selectedId)) {
    designerStore.selectedId = designerStore.selectedIds[0] ?? null
  }
}

/** 复制节点（粘贴到根列表末尾） */
export function copyNode(id: string): void {
  const found = findNode(designerStore.schema, id)
  if (!found) return
  pushHistory()
  const copy = cloneNode(found.node)
  regenerateFieldNames(copy)
  if (typeof copy.x === 'number') copy.x += 24
  if (typeof copy.y === 'number') copy.y += 24
  designerStore.schema.push(copy)
  designerStore.selectedId = copy.id
  designerStore.selectedIds = [copy.id]
}

/** 为节点及其所有子节点重新生成不重复的字段名 */
function regenerateFieldNames(root: ComponentSchema): void {
  for (const n of [root, ...collectAllNodes(root.children)]) {
    n.fieldName = nextFieldName(n.type)
  }
}

/** 复制单个节点到剪贴板 */
export function copyNodeToClipboard(id: string): void {
  copyNodesToClipboard([id])
}

/** 原地复制节点（Ctrl+D，支持多选，副本偏移 24px） */
export function duplicateNodes(ids: string[]): void {
  if (!ids.length) return
  pushHistory()
  const newIds: string[] = []
  for (const id of ids) {
    const found = findNode(designerStore.schema, id)
    if (!found) continue
    const copy = cloneNode(found.node)
    regenerateFieldNames(copy)
    if (typeof copy.x === 'number') copy.x += 24
    if (typeof copy.y === 'number') copy.y += 24
    designerStore.schema.push(copy)
    newIds.push(copy.id)
  }
  if (newIds.length) {
    designerStore.selectedIds = newIds
    designerStore.selectedId = newIds[0] ?? null
  } else {
    // 没有实际复制任何节点，回滚空快照
    history.undoStack.pop()
  }
}

/** 批量复制节点到内部剪贴板（容器维度：容器连同子树一起复制） */
export function copyNodesToClipboard(ids: string[]): void {
  const nodes: ComponentSchema[] = []
  for (const id of ids) {
    const found = findNode(designerStore.schema, id)
    if (found) nodes.push(cloneNode(found.node, false))
  }
  designerStore.clipboardNodes = nodes
}

/** 剪切单个节点到剪贴板 */
export function cutNodeToClipboard(id: string): void {
  cutNodesToClipboard([id])
}

/** 批量剪切节点到内部剪贴板（容器维度） */
export function cutNodesToClipboard(ids: string[]): void {
  copyNodesToClipboard(ids)
  removeNodes(ids)
}

/** 粘贴剪贴板中的节点到画布根列表末尾（支持多选粘贴） */
export function pasteClipboard(): void {
  const sources = designerStore.clipboardNodes
  if (!sources.length) return
  pushHistory()
  const pastedIds: string[] = []
  for (const source of sources) {
    const copy = cloneNode(source)
    regenerateFieldNames(copy)
    if (typeof copy.x === 'number') copy.x += 24
    if (typeof copy.y === 'number') copy.y += 24
    designerStore.schema.push(copy)
    pastedIds.push(copy.id)
  }
  designerStore.selectedIds = pastedIds
  designerStore.selectedId = pastedIds[0] ?? null
}

// ---------- 选中集合管理 ----------

/** 获取当前操作对象集合：优先多选集合，其次单选 */
export function getSelectionIds(): string[] {
  if (designerStore.selectedIds.length) return [...designerStore.selectedIds]
  if (designerStore.selectedId) return [designerStore.selectedId]
  return []
}

/** 单选某个节点 */
export function selectSingleNode(id: string): void {
  designerStore.selectedId = id
  designerStore.selectedIds = [id]
}

/** 切换某个节点的多选状态（Shift+点击 / 框选） */
export function toggleSelectNode(id: string): void {
  const idx = designerStore.selectedIds.indexOf(id)
  if (idx >= 0) {
    designerStore.selectedIds.splice(idx, 1)
    if (designerStore.selectedId === id) {
      designerStore.selectedId = designerStore.selectedIds[0] ?? null
    }
  } else {
    designerStore.selectedIds.push(id)
    designerStore.selectedId = id
  }
}

/** 设置多选集合（框选 / 搜索选中） */
export function setSelection(ids: string[]): void {
  designerStore.selectedIds = [...ids]
  designerStore.selectedId = ids[0] ?? null
}

/** 清空所有选中 */
export function clearSelection(): void {
  designerStore.selectedIds = []
  designerStore.selectedId = null
}

/** 判断节点是否在选中集合中 */
export function isNodeSelected(id: string): boolean {
  return designerStore.selectedIds.includes(id) || designerStore.selectedId === id
}

// ---------- CSS 样式 ----------

/**
 * 判断 CSS 尺寸值是否为「撑满父容器」的 100%（容忍 "100 %" / 前后空格等写法）。
 * 只有 100% 才需要吸附到原点；99%、101% 等仍按普通百分比处理。
 */
export function isFullPercent(value: string | undefined): boolean {
  if (!value) return false
  return value.replace(/\s+/g, '') === '100%'
}

/** 更新控件级 CSS 样式（传空字符串表示清除该项） */
export function updateNodeCss(id: string, key: keyof NodeCss, value: string): void {
  const found = findNode(designerStore.schema, id)
  if (!found) return
  pushHistory()
  const css: NodeCss = { ...(found.node.css ?? {}) }
  const trimmed = value.trim()
  if (!trimmed) {
    delete css[key]
  } else {
    css[key] = trimmed
    // 设置布局容器的高度 CSS 时，标记为非自适应（设置宽度不影响高度自适应）
    if (key === 'height' && LAYOUT_CONTAINER_TYPES.has(found.node.type)) {
      found.node.heightAuto = false
    }
  }
  found.node.css = Object.keys(css).length ? css : undefined
  // 宽度/高度为 100% 时把控件吸附到父容器原点：
  // 否则 x/y 有任何偏移，控件就会超出父容器画布（右/下溢出）。
  // 仅对自由定位节点生效——只有它们才有 x/y 偏移。
  if (found.node.x !== undefined || found.node.y !== undefined) {
    if (isFullPercent(css.width)) found.node.x = 0
    if (isFullPercent(css.height)) found.node.y = 0
  }
  designerStore.schemaRevision += 1
}

/** 将节点 css 转换为可绑定到元素 style 的对象 */
export function resolveNodeStyle(node: ComponentSchema): Record<string, string> {
  const css = node.css
  if (!css) return {}
  const style: Record<string, string> = {}
  if (css.width) style.width = css.width
  if (css.height) style.height = css.height
  if (css.padding) style.padding = css.padding
  if (css.margin) style.margin = css.margin
  if (css.background) style.background = css.background
  if (css.borderRadius) style.borderRadius = css.borderRadius
  if (css.border) style.border = css.border
  if (css.overflow) style.overflow = css.overflow
  if (css.opacity) style.opacity = css.opacity
  if (css.display) style.display = css.display
  return style
}

/** 更新节点属性 */
export function updateProps(id: string, prop: string, value: unknown): void {
  const found = findNode(designerStore.schema, id)
  if (!found) return
  pushHistory()
  setPropValue(found.node.props, prop, value)
  designerStore.schemaRevision += 1
}

/** 移除节点上的某个属性（恢复为组件默认值） */
export function removeProp(id: string, prop: string): void {
  const found = findNode(designerStore.schema, id)
  if (!found) return
  pushHistory()
  deletePropValue(found.node.props, prop)
  designerStore.schemaRevision += 1
}

/** 更新节点事件处理代码 */
export function updateEvent(id: string, eventName: string, code: string): void {
  const found = findNode(designerStore.schema, id)
  if (!found) return
  pushHistory()
  if (!found.node.events) found.node.events = {}
  if (code.trim()) {
    found.node.events[eventName] = code
  } else {
    delete found.node.events[eventName]
    if (!Object.keys(found.node.events).length) delete found.node.events
  }
  designerStore.schemaRevision += 1
}

/** 移除节点上的某个事件 */
export function removeEvent(id: string, eventName: string): void {
  const found = findNode(designerStore.schema, id)
  if (!found || !found.node.events) return
  pushHistory()
  delete found.node.events[eventName]
  if (!Object.keys(found.node.events).length) delete found.node.events
  designerStore.schemaRevision += 1
}

/**
 * 更新自由画布中节点位置
 * @param clampToZero 是否把坐标钳制到 >= 0。默认 true。
 *   拖拽过程中传 false，可让节点跟随光标移出自由定位容器（负坐标），
 *   落点由 CanvasPanel.onDragEnd 重新校正为合法值。
 */
export function updateNodePosition(
  id: string,
  x: number,
  y: number,
  clampToZero = true,
): void {
  const found = findNode(designerStore.schema, id)
  if (!found) return
  found.node.x = clampToZero ? Math.max(0, x) : x
  found.node.y = clampToZero ? Math.max(0, y) : y
  designerStore.schemaRevision += 1
}

/** 更新控件实际渲染尺寸。heightChanged=false 时仅调整宽度，不修改 heightAuto 标记 */
export function resizeNode(id: string, width: number, height: number, heightChanged = true): void {
  const found = findNode(designerStore.schema, id)
  if (!found) return
  found.node.width = Math.max(20, width)
  found.node.height = Math.max(20, height)
  // 布局容器拖拽修改高度后，标记为非自适应
  // （即使是旧数据没有 heightAuto 字段，也在此处初始化）
  if (heightChanged && LAYOUT_CONTAINER_TYPES.has(found.node.type)) {
    found.node.heightAuto = false
  }
  designerStore.schemaRevision += 1
}

/** 更新控件字段名称（store 层做唯一性防御：重名时自动追加序号） */
export function updateFieldName(id: string, fieldName: string): boolean {
  const found = findNode(designerStore.schema, id)
  if (!found) return false
  const trimmed = fieldName.trim()
  if (!trimmed) return false
  // store 层唯一性防御：如果重名，自动追加 _2/_3... 直到唯一
  const allNodes = collectAllNodes(designerStore.schema)
  const isDuplicate = allNodes.some((n) => n.id !== id && n.fieldName === trimmed)
  const finalName = isDuplicate ? resolveUniqueFieldName(trimmed, id) : trimmed
  pushHistory()
  found.node.fieldName = finalName
  designerStore.schemaRevision += 1
  return finalName === trimmed
}

/** 给已有 fieldName 追加序号直到不重名 */
function resolveUniqueFieldName(base: string, excludeId: string): string {
  const allNodes = collectAllNodes(designerStore.schema)
  const used = new Set(allNodes.filter((n) => n.id !== excludeId).map((n) => n.fieldName).filter(Boolean) as string[])
  if (!used.has(base)) return base
  // 尝试 base_2, base_3...
  const m = base.match(/^(.+?)(_(\d+))?$/)
  const stem = m?.[1] ?? base
  let idx = m?.[3] ? Number(m[3]) + 1 : 2
  let name = `${stem}_${idx}`
  while (used.has(name)) {
    idx += 1
    name = `${stem}_${idx}`
  }
  return name
}

/** 标识符校验正则：字母开头，只含字母/数字/下划线，1~64字符 */
const IDENTIFIER_RE = /^[a-zA-Z][a-zA-Z0-9_]{0,63}$/

/** 校验标识符（表名/字段名/控件名）：字母开头，只含字母/数字/下划线 */
export function isValidIdentifier(name: string): boolean {
  return IDENTIFIER_RE.test(name)
}

/** 更新控件的数据库字段名（dbField），带唯一性校验。返回 true 表示校验通过 */
export function updateDbField(id: string, dbField: string): { ok: boolean; error?: string } {
  const found = findNode(designerStore.schema, id)
  if (!found) return { ok: false, error: '节点不存在' }
  const trimmed = dbField.trim()
  if (!trimmed) {
    pushHistory()
    found.node.dbField = undefined
    designerStore.schemaRevision += 1
    return { ok: true }
  }
  // 特殊字符校验：只允许字母开头+字母/数字/下划线
  if (!IDENTIFIER_RE.test(trimmed)) {
    return { ok: false, error: '只能以字母开头，仅含字母、数字、下划线' }
  }
  // 唯一性校验：在同一表单 schema 内不能重复
  const allNodes = collectAllNodes(designerStore.schema)
  const isDuplicate = allNodes.some((n) => n.id !== id && n.dbField === trimmed)
  if (isDuplicate) {
    return { ok: false, error: '数据库字段名已存在，不能重复' }
  }
  pushHistory()
  found.node.dbField = trimmed
  designerStore.schemaRevision += 1
  return { ok: true }
}

/** 表单模式保存前校验：检查表名和所有表单控件的 dbField */
export function validateFormBeforeSave(): { ok: boolean; errors: string[] } {
  const errors: string[] = []
  // 仅业务形态的表单模式需要校验
  if (designerStore.mode !== 'biz' || designerStore.bizMode !== 'form') {
    return { ok: true, errors: [] }
  }
  // 检查表名
  if (!designerStore.tableName || !designerStore.tableName.trim()) {
    errors.push('请先填写数据表名（tableName）')
  } else if (!IDENTIFIER_RE.test(designerStore.tableName.trim())) {
    errors.push('数据表名只能以字母开头，仅含字母、数字、下划线')
  }
  // 检查所有表单控件（category='表单'）的 dbField
  const formSchema = designerStore.bizSchemas.form
  const formNodes = collectAllNodes(formSchema)
  const dbFields = new Set<string>()
  for (const node of formNodes) {
    const cfg = getConfig(node.type)
    if (!cfg || cfg.category !== '表单') continue
    if (!node.dbField || !node.dbField.trim()) {
      errors.push(`控件 ${node.fieldName ?? node.type} 未填写数据库字段名`)
    } else if (!IDENTIFIER_RE.test(node.dbField.trim())) {
      errors.push(`控件 ${node.fieldName ?? node.type} 的数据库字段名 "${node.dbField}" 不合法（只能以字母开头，仅含字母、数字、下划线）`)
    } else if (dbFields.has(node.dbField)) {
      errors.push(`数据库字段名 "${node.dbField}" 重复`)
    } else {
      dbFields.add(node.dbField)
    }
  }
  return { ok: errors.length === 0, errors }
}
export function updatePageCss(patch: Partial<PageCss>): void {
  pushHistory()
  Object.assign(designerStore.pageCss, patch)
  designerStore.schemaRevision += 1
}

/** 按点号路径读取对象属性，路径不存在或中间值不是对象时返回 undefined */
export function getPropValue(target: Record<string, unknown>, path: string): unknown {
  const keys = path.split('.')
  let current: unknown = target
  for (const key of keys) {
    if (current === null || typeof current !== 'object' || Array.isArray(current)) return undefined
    // 先通过 proxy get 触发响应式依赖收集（即使属性不存在也要 track）
    const val = (current as Record<string, unknown>)[key]
    if (!Object.prototype.hasOwnProperty.call(current, key)) return undefined
    current = val
  }
  return current
}

/** 按点号路径判断属性是否已设置 */
export function hasPropValue(target: Record<string, unknown>, path: string): boolean {
  const keys = path.split('.')
  let current: unknown = target
  for (const key of keys) {
    if (current === null || typeof current !== 'object' || Array.isArray(current)) return false
    if (!Object.prototype.hasOwnProperty.call(current, key)) return false
    current = (current as Record<string, unknown>)[key]
  }
  return true
}

/** 按点号路径写入属性，路径中间的普通值会被替换为对象 */
export function setPropValue(target: Record<string, unknown>, path: string, value: unknown): void {
  const keys = path.split('.')
  let current: Record<string, unknown> = target
  for (let i = 0; i < keys.length - 1; i += 1) {
    const key = keys[i]
    const next = current[key]
    const child: Record<string, unknown> =
      next && typeof next === 'object' && !Array.isArray(next)
        ? { ...(next as Record<string, unknown>) }
        : {}
    current[key] = child
    current = child
  }
  const lastKey = keys[keys.length - 1]
  if (value === undefined) {
    delete current[lastKey]
  } else {
    current[lastKey] = value
  }
}

/** 按点号路径删除属性，并清理因此变空的父对象 */
export function deletePropValue(target: Record<string, unknown>, path: string): void {
  const keys = path.split('.')
  const chain: { parent: Record<string, unknown>; key: string }[] = []
  let current: Record<string, unknown> = target
  for (let i = 0; i < keys.length - 1; i += 1) {
    const key = keys[i]
    const next = current[key]
    if (next === null || typeof next !== 'object' || Array.isArray(next)) return
    chain.push({ parent: current, key })
    current = next as Record<string, unknown>
  }
  const leafKey = keys[keys.length - 1]
  delete current[leafKey]
  for (let i = chain.length - 1; i >= 0; i -= 1) {
    const { parent, key } = chain[i]
    const child = parent[key] as Record<string, unknown> | undefined
    if (child && typeof child === 'object' && !Array.isArray(child) && Object.keys(child).length === 0) {
      delete parent[key]
    } else {
      break
    }
  }
}

/** 获取当前选中节点 */
export function getSelectedNode(): ComponentSchema | null {
  if (!designerStore.selectedId) return null
  return findNode(designerStore.schema, designerStore.selectedId)?.node ?? null
}

/** 设置设计器形态：default=自定义页面，biz=业务页面（单据/基础资料） */
export function setDesignerMode(mode: 'default' | 'biz'): void {
  designerStore.mode = mode
}

/** 补全 schema 节点的默认属性、fieldName、位置、尺寸（向下兼容） */
function migrateSchemaNodes(nodes: ComponentSchema[]): void {
  const allNodes = collectAllNodes(nodes)
  const usedFieldNames = new Set<string>()
  allNodes.forEach((node, index) => {
    migrateNodeProps(node)
    const size = defaultNodeSize(node.type)
    if (!node.fieldName || usedFieldNames.has(node.fieldName)) {
      node.fieldName = nextFieldName(node.type)
    }
    usedFieldNames.add(node.fieldName as string)
    if (typeof node.x !== 'number') node.x = 24 + (index % 6) * 24
    if (typeof node.y !== 'number') node.y = 24 + (index % 6) * 24
    if (typeof node.width !== 'number') node.width = size.width
    if (typeof node.height !== 'number') node.height = size.height
  })
}

/** 设置业务子模式：切换时把当前 schema 存回旧模式槽位，再加载新模式槽位 */
export function setBizMode(bizMode: 'form' | 'table' | 'mobile'): void {
  if (designerStore.mode !== 'biz') return
  const oldMode = designerStore.bizMode
  if (oldMode === bizMode) return
  // 保存当前 schema 到旧模式槽位
  designerStore.bizSchemas[oldMode] = designerStore.schema
  // 切换到新模式槽位
  designerStore.bizMode = bizMode
  designerStore.schema = designerStore.bizSchemas[bizMode]
  // 补全新 schema 节点属性（向下兼容）
  migrateSchemaNodes(designerStore.schema)
  designerStore.selectedId = null
  designerStore.selectedIds = []
}

/** 从表单模式克隆控件到当前画布（移动端/表格模式引用表单字段）。
 *  保留原控件的 props/fieldName，重新生成 id，fieldName 冲突时追加序号。 */
export function addNodeFromForm(sourceNode: ComponentSchema): void {
  pushHistory()
  const copy = cloneNode(sourceNode)
  // fieldName 唯一性：如果当前画布已有同名字段，追加序号
  const allNodes = collectAllNodes(designerStore.schema)
  const used = new Set(allNodes.map((n) => n.fieldName).filter(Boolean) as string[])
  if (copy.fieldName && used.has(copy.fieldName)) {
    const base = copy.fieldName
    let idx = 2
    let name = `${base}_${idx}`
    while (used.has(name)) {
      idx += 1
      name = `${base}_${idx}`
    }
    copy.fieldName = name
  }
  // 设置画布位置
  const pos = nextRootPosition(designerStore.schema)
  copy.x = pos.x
  copy.y = pos.y
  designerStore.schema.push(copy)
  designerStore.selectedId = copy.id
  designerStore.selectedIds = [copy.id]
}

/** 导出页面 Schema */
export function exportSchema(): PageSchema {
  const result: PageSchema = {
    version: '1.0.0',
    pageId: designerStore.pageId,
    pageName: designerStore.pageName,
    tableName: designerStore.tableName,
    primaryKeyField: designerStore.primaryKeyField,
    pageType: designerStore.pageType,
    pageCss: { ...designerStore.pageCss },
    themeId: designerStore.themeId,
    root: designerStore.schema,
  }
  // 业务形态：同步当前 schema 到槽位，并输出三份独立内容
  if (designerStore.mode === 'biz') {
    designerStore.bizSchemas[designerStore.bizMode] = designerStore.schema
    result.bizSchemas = {
      form: designerStore.bizSchemas.form,
      table: designerStore.bizSchemas.table,
      mobile: designerStore.bizSchemas.mobile,
    }
    result.lastBizMode = designerStore.bizMode
  }
  return result
}

/**
 * 深度合并默认属性到目标对象
 * 只补全缺失的属性，不覆盖已有值
 */
function deepMergeDefaults(target: Record<string, unknown>, defaults: Record<string, unknown>): void {
  for (const key of Object.keys(defaults)) {
    const defVal = defaults[key]
    if (target[key] === undefined || target[key] === null) {
      // 数组和对象深拷贝，避免引用共享
      if (Array.isArray(defVal)) {
        target[key] = JSON.parse(JSON.stringify(defVal))
      } else if (defVal !== null && typeof defVal === 'object') {
        target[key] = JSON.parse(JSON.stringify(defVal))
      } else {
        target[key] = defVal
      }
    } else if (
      defVal !== null &&
      typeof defVal === 'object' &&
      !Array.isArray(defVal) &&
      target[key] !== null &&
      typeof target[key] === 'object' &&
      !Array.isArray(target[key])
    ) {
      // 嵌套对象：递归合并
      deepMergeDefaults(target[key] as Record<string, unknown>, defVal as Record<string, unknown>)
    }
    // 数组类型如果用户已有值，不做合并（保持用户配置）
  }
}

/**
 * 迁移/补全节点属性（向下兼容）
 * 当设计器升级后，旧页面元数据缺少新属性时，自动补全默认值
 */
function migrateNodeProps(node: ComponentSchema): void {
  const cfg = getConfig(node.type)
  if (!cfg?.defaultProps) return
  deepMergeDefaults(node.props as Record<string, unknown>, cfg.defaultProps as Record<string, unknown>)
}

/** 导入页面 Schema（整体替换） */
export function importSchema(page: PageSchema): boolean {
  if (!page || !Array.isArray(page.root)) return false
  pushHistory()
  designerStore.pageId = page.pageId || `page_${Date.now().toString(36)}`
  designerStore.pageName = page.pageName || '未命名页面'
  designerStore.tableName = page.tableName || ''
  designerStore.primaryKeyField = page.primaryKeyField || ''
  designerStore.pageType = page.pageType || designerStore.pageType
  designerStore.pageCss = page.pageCss ? { ...page.pageCss } : { background: '#ffffff' }
  designerStore.themeId = page.themeId
  // 业务形态：加载三份独立内容，当前 schema 指向当前模式槽位
  if (designerStore.mode === 'biz') {
    // 恢复上次使用的子模式（持久化）
    if (page.lastBizMode && page.lastBizMode !== designerStore.bizMode) {
      designerStore.bizMode = page.lastBizMode
    }
    if (page.bizSchemas) {
      designerStore.bizSchemas = {
        form: page.bizSchemas.form || [],
        table: page.bizSchemas.table || [],
        mobile: page.bizSchemas.mobile || [],
      }
    } else {
      // 旧数据无 bizSchemas：把 root 放入当前模式槽位，其余置空
      designerStore.bizSchemas = {
        form: [] as ComponentSchema[],
        table: [] as ComponentSchema[],
        mobile: [] as ComponentSchema[],
      }
      designerStore.bizSchemas[designerStore.bizMode] = page.root
    }
    designerStore.schema = designerStore.bizSchemas[designerStore.bizMode]
  } else {
    designerStore.schema = page.root
  }
  // 补全当前 schema 节点属性（向下兼容）
  migrateSchemaNodes(designerStore.schema)
  designerStore.selectedId = null
  designerStore.selectedIds = []
  return true
}

/** 清空画布 */
export function clearCanvas(): void {
  if (!designerStore.schema.length) return
  pushHistory()
  designerStore.schema = []
  designerStore.selectedId = null
  designerStore.selectedIds = []
}

/** 渲染用 store 接口（designerStore 和子页面 store 都满足此接口） */
export interface RenderStore {
  schema: ComponentSchema[]
  schemaRevision: number
}

/** designer store 的 injection key，用于 provide/inject 隔离子页面渲染 */
export const DESIGNER_STORE_KEY: InjectionKey<RenderStore> = Symbol('designerStore')

/** 创建独立渲染 store（用于子页面弹窗隔离渲染上下文） */
export function createRenderStore(): RenderStore {
  return reactive({
    schema: [] as ComponentSchema[],
    schemaRevision: 0,
  })
}
