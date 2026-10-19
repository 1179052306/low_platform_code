<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import type { ComponentSchema } from '@/types/schema'
import { materialComponents, getConfig, resolveNodeProps } from '@/materials'
import { MATERIAL_TYPES } from '@/materials/dx-materials'
import { t } from '@/store/lang'
import {
  designerStore,
  addNode,
  moveNode,
  removeNode,
  removeNodes,
  copyNode,
  getSelectionIds,
  resolveNodeStyle,
  isProtectedChild,
  findNode,
  updateNodeCss,
  isNodeSelected,
} from '@/store/designer'
import {
  STRUCTURAL_CHILD_TYPES,
  LAYOUT_CONTAINER_TYPES,
  FREE_POSITION_CONTAINERS,
  GRID_SECTION_TYPES,
  SUB_CONTAINER_TYPES,
} from '@/constants/container-types'
import { containerDragGuides } from '@/composables/useCanvasDragState'
import { ElButton, ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus'
import AdvancedSearchPanel from '@/components/renderer/AdvancedSearchPanel.vue'
import ToolbarIcon from '@/components/renderer/ToolbarIcon.vue'
import { useToolbarOverflow } from '@/composables/useToolbarOverflow'

const props = defineProps<{
  node: ComponentSchema
  parentId: string | null
  index: number
  positioned?: boolean
  selected?: boolean
}>()

const FORM_CONTROL_TYPES = new Set([
  'text-box',
  'text-area',
  'number-box',
  'check-box',
  'switch',
  'slider',
  'range-slider',
  'calendar',
  'date-box',
  'date-range-box',
  'color-box',
  'select-box',
  'lookup',
  'tag-box',
  'autocomplete',
  'radio-group',
  'drop-down-button',
  'drop-down-box',
  'file-uploader',
  'html-editor',
  'base-data',
])

const isFormField = computed(() => FORM_CONTROL_TYPES.has(props.node.type))
const labelConfig = computed(() => {
  const p = props.node.props
  return {
    label: (p.label as string) ?? getConfig(props.node.type)?.title ?? '',
    labelVisible: p.labelVisible !== false,
    labelPosition: (p.labelPosition as string) || 'top',
    required: p.required === true || (Array.isArray(p.validationRules) && p.validationRules.some((r: any) => r.type === 'required')),
    requiredPosition: (p.requiredPosition as string) || 'after',
    tooltipEnabled: p.tooltipEnabled === true,
    tooltipText: (p.tooltipText as string) || '',
  }
})
const showLabel = computed(
  () =>
    isFormField.value &&
    labelConfig.value.labelVisible &&
    !!labelConfig.value.label
)

const emit = defineEmits<{
  (
    e: 'drag-start',
    payload: {
      event: MouseEvent
      nodeId: string
      x: number
      y: number
      width: number
      height: number
      parentId: string | null
    }
  ): void
  (
    e: 'resize-start',
    payload: {
      event: MouseEvent
      nodeId: string
      dir: string
      x: number
      y: number
      width: number
      height: number
    }
  ): void
  (e: 'select-single', payload: { id: string; additive: boolean }): void
}>()

const cfg = computed(() => getConfig(props.node.type))
// 注意：Vue 3 对未绑定的 Boolean prop 做布尔转换（absent → false 而非 undefined），
// 因此 ?? 无法区分「未绑定」和「绑定为 false」，嵌套渲染必须显式传递 :selected
const isSelected = computed(
  () => props.selected ?? designerStore.selectedId === props.node.id
)
const isPositioned = computed(() => props.positioned === true)
const isContainer = computed(() => !!cfg.value?.isContainer)
const OVERLAY_TYPES = new Set(['popup', 'toast', 'load-panel'])

/** DataGrid 工具栏是否显示（设计器画布用） */
const showGridToolbar = computed(() => {
  if (props.node.type !== 'data-grid') return false
  const toolbar = props.node.props.toolbar as
    | Record<string, unknown>
    | undefined
  return toolbar?.visible === true
})

/** 高级查询是否显示（设计器画布用） */
const showAdvancedSearch = computed(() => {
  if (props.node.type !== 'data-grid') return false
  return props.node.props.advancedSearch === true
})

/** DataGrid 列配置（用于高级查询面板） */
const gridColumns = computed(() => {
  const cols = props.node.props.columns as Record<string, unknown>[] | undefined
  if (!Array.isArray(cols)) return []
  return cols.map((c) => ({
    dataField: c.dataField as string,
    caption: (c.caption as string) || (c.dataField as string),
    dataType: (c.dataType as string) || 'string',
    visible: c.visible !== false,
  }))
})

/** DataGrid 高度偏移量（toolbar + 高级查询占用的高度） */
const gridHeightOffset = ref(0)
const wrapperRef = ref<HTMLElement | null>(null)

/** DataGrid wrapper 实际像素高度（用于将 css.height='100%' 等百分比值转换为具体像素值，
 *  避免 toolbar 与 DevExtreme DataGrid 同时以 100% 抢占空间导致表格超出容器）
 */
const measuredWrapperHeight = ref(0)
let wrapperResizeObserver: ResizeObserver | null = null

function refreshMeasuredWrapperHeight(): void {
  if (!wrapperRef.value) return
  const h = wrapperRef.value.clientHeight
  if (h > 0 && h !== measuredWrapperHeight.value) {
    measuredWrapperHeight.value = h
  }
}

function disposeWrapperObserver(): void {
  if (wrapperResizeObserver) {
    wrapperResizeObserver.disconnect()
    wrapperResizeObserver = null
  }
}

/** 计算 DataGrid 上方元素（高级查询 + toolbar）的总高度 */
function updateGridHeightOffset() {
  if (!wrapperRef.value) return
  // 先确保 wrapper 实际高度已被测量（百分比高度场景下需要）
  refreshMeasuredWrapperHeight()
  let offset = 0
  const children = wrapperRef.value.children
  for (let i = 0; i < children.length; i++) {
    const el = children[i] as HTMLElement
    if (
      el.classList.contains('lc-table-wrapper') ||
      el.querySelector('.lc-table-wrapper') ||
      el.classList.contains('dx-datagrid') ||
      el.querySelector('.dx-datagrid')
    )
      break
    offset += el.offsetHeight
  }
  gridHeightOffset.value = offset
}

/** DataGrid 专用 props：根据上方元素高度动态调整 DataGrid 高度 */
const gridBoundProps = computed(() => {
  const base: Record<string, unknown> = { ...boundProps.value }
  const hasExtra = showGridToolbar.value || showAdvancedSearch.value
  if (hasExtra) {
    const h = base.height
    if (typeof h === 'number') {
      // 数值高度：直接减去 toolbar/高级查询偏移
      base.height = Math.max(100, h - gridHeightOffset.value)
    } else if (h === '100%' && measuredWrapperHeight.value > 0) {
      // 100% 高度：实测 wrapper 像素高度后减去 toolbar/高级查询偏移
      base.height = Math.max(
        100,
        measuredWrapperHeight.value - gridHeightOffset.value
      )
    }
  }
  return base
})

/** DataGrid 独立工具栏按钮配置（设计器画布用，仅显示无事件） */
const gridToolbarItems = computed(() => {
  if (props.node.type !== 'data-grid') return []
  const toolbar = props.node.props.toolbar as
    | Record<string, unknown>
    | undefined
  const items = toolbar?.items as
    | {
        name: string
        caption: string
        icon: string
        visible: boolean
        location?: string
        type?: 'default' | 'primary' | 'success' | 'warning' | 'danger' | 'info' | 'normal'
        stylingMode?: 'contained' | 'outlined' | 'text'
        showIcon?: boolean
        bgColor?: string
        textColor?: string
        hoverColor?: string
        isCustom?: boolean
        itemType?: string
        children?: { name: string; caption: string; icon: string; visible: boolean; showIcon?: boolean }[]
      }[]
    | undefined
  if (!Array.isArray(items)) return []
  return items
    .filter((item) => item.visible !== false)
    .map((item): any => {
      if (item.itemType === 'dropdown') {
        const childItems = (item.children || [])
          .filter((c) => c.visible !== false)
          .map((c) => ({
            name: c.name,
            text: c.caption || c.name,
            icon: c.showIcon === false ? undefined : (c.icon || c.name),
          }))
        return {
          widget: 'dxDropDownButton',
          location: item.location || 'after',
          options: {
            text: item.caption || item.name,
            icon: item.showIcon === false ? undefined : (item.icon || item.name),
            type: (item.type === 'normal' || !item.type) ? 'default' : item.type,
            stylingMode: (!item.stylingMode || (item.stylingMode === 'text' && !item.isCustom)) ? 'contained' : item.stylingMode,
            items: childItems,
            ...((item.bgColor || item.textColor || item.hoverColor)
              ? {
                  elementAttr: {
                    class: [
                      item.bgColor ? 'lc-btn-has-bg' : '',
                      item.textColor ? 'lc-btn-has-text' : '',
                      item.hoverColor ? 'lc-btn-has-hover' : '',
                    ]
                      .filter(Boolean)
                      .join(' '),
                    style: `--lc-btn-bg:${item.bgColor || ''};--lc-btn-text:${item.textColor || ''};--lc-btn-hover:${item.hoverColor || ''};`,
                  },
                }
              : {}),
          },
        }
      }
      return {
        widget: 'dxButton',
        location: item.location || 'before',
        options: {
          icon: item.showIcon === false ? undefined : (item.icon || item.name),
          text: item.caption || item.name,
          type: (item.type === 'normal' || !item.type) ? 'default' : item.type,
          stylingMode: (!item.stylingMode || (item.stylingMode === 'text' && !item.isCustom)) ? 'contained' : item.stylingMode,
          ...((item.bgColor || item.textColor || item.hoverColor)
            ? {
                elementAttr: {
                  class: [
                    item.bgColor ? 'lc-btn-has-bg' : '',
                    item.textColor ? 'lc-btn-has-text' : '',
                    item.hoverColor ? 'lc-btn-has-hover' : '',
                  ]
                    .filter(Boolean)
                    .join(' '),
                  style: `--lc-btn-bg:${item.bgColor || ''};--lc-btn-text:${item.textColor || ''};--lc-btn-hover:${item.hoverColor || ''};`,
                },
              }
            : {}),
        },
      }
    })
})

const toolbarRef = ref<HTMLElement>()
const { overflowIndices } = useToolbarOverflow(toolbarRef)
const beforeItems = computed(() => gridToolbarItems.value.map((item, idx) => ({ item, idx })).filter(({ item }) => (item.location || 'before') === 'before'))
const centerItems = computed(() => gridToolbarItems.value.map((item, idx) => ({ item, idx })).filter(({ item }) => item.location === 'center'))
const afterItems = computed(() => gridToolbarItems.value.map((item, idx) => ({ item, idx })).filter(({ item }) => (item.location || 'after') === 'after'))
const overflowIndicesArray = computed(() => [...overflowIndices.value].sort((a, b) => a - b))
const moreMenuVisible = ref(false)
const moreMenuStyle = ref<Record<string, string>>({})
const expandedMoreIdx = ref<number | null>(null)
function toggleMoreMenu(event: MouseEvent) {
  if (moreMenuVisible.value) {
    moreMenuVisible.value = false
    return
  }
  const btn = (event.currentTarget as HTMLElement).getBoundingClientRect()
  moreMenuStyle.value = {
    position: 'fixed',
    top: `${btn.bottom}px`,
    right: `${window.innerWidth - btn.right}px`,
    zIndex: '10000',
  }
  moreMenuVisible.value = true
}
function triggerOverflowItem(idx: number) {
  const item = gridToolbarItems.value[idx]
  if (item?.widget === 'dxDropDownButton') {
    expandedMoreIdx.value = expandedMoreIdx.value === idx ? null : idx
    return
  }
  moreMenuVisible.value = false
  if (item?.options) (item.options as any)?.onClick?.({})
}

const isLayoutContainer = computed(() =>
  LAYOUT_CONTAINER_TYPES.has(props.node.type)
)
const childrenPositioned = computed(() =>
  FREE_POSITION_CONTAINERS.has(props.node.type)
)

/** 当前容器是否正在拖拽子节点（用于显示容器内对齐辅助线） */
const isActiveDragContainer = computed(
  () =>
    childrenPositioned.value &&
    containerDragGuides.value?.containerId === props.node.id
)
const dragGuides = computed(() =>
  isActiveDragContainer.value ? containerDragGuides.value!.guides : []
)
const overlayProps = computed(() =>
  OVERLAY_TYPES.has(props.node.type)
    ? { container: `#canvas-node-${props.node.id}` }
    : {}
)
const boundProps = computed(() => {
  // 读取 revision，确保任意嵌套 prop 变化时组件重新绑定最新 props
  void designerStore.schemaRevision
  // 始终传入节点信息（含 css），以便 CSS 宽高设置能传递给组件 props
  // 仅当节点是自由定位时，resolveNodeProps 才会使用节点的 width/height 属性
  const base: Record<string, unknown> = {
    ...resolveNodeProps(props.node.type, props.node.props, props.node),
  }

  // 剥离自定义 label 属性，避免传给 DevExtreme 控件触发其自带 label
  if (FORM_CONTROL_TYPES.has(props.node.type)) {
    // 有 label 时控件占满 form-field-control 宽度，用自然高度，避免 node.width/height 覆盖
    if (
      props.node.props.labelVisible !== false &&
      labelConfig.value.label
    ) {
      base.width = '100%'
      delete base.height
    }
    delete base.label
    delete base.labelVisible
    delete base.labelPosition
    delete base.required
    delete base.requiredPosition
    delete base.tooltipEnabled
    delete base.tooltipText
  }

  // 设计器中强制 visible=true，避免用户设置 visible=false 后控件消失无法选中编辑
  if (MATERIAL_TYPES.has(props.node.type)) {
    base.visible = true
  }

  // 为 el-splitter-panel 添加拖拽手柄所需的 props
  const extraProps: Record<string, unknown> = {}
  if (props.node.type === 'el-splitter-panel' && props.parentId) {
    const parentResult = findNode(designerStore.schema, props.parentId)
    if (parentResult && parentResult.node.type === 'el-splitter') {
      extraProps.showHandle = true
      extraProps.layout = parentResult.node.props.layout
      const totalPanels = parentResult.node.children.length
      extraProps.isLastPanel = props.index >= totalPanels - 1
      // 传递面板索引和父节点ID，便于 ElSplitterPanel 自行处理拖拽和键盘操作
      extraProps.panelIndex = props.index
      extraProps.parentNodeId = props.parentId
    }
  }

  // dx-data-grid：删除 toolbar 属性（使用独立 DxToolbar 组件渲染，不依赖表格自带工具栏）
  if (props.node.type === 'data-grid') {
    if (base.toolbar) delete base.toolbar
  }

  // dx-data-grid 列处理：清理自定义属性，补全必要属性，保证 filterRow 等功能正常
  if (props.node.type === 'data-grid' && Array.isArray(base.columns)) {
    base.columns = (base.columns as Record<string, unknown>[]).map((col) => {
      // 清理自定义属性，不传给 DevExtreme（避免未知属性导致内部异常）
      const {
        editorType: _et,
        dropDownGrid: _ddg,
        writeBack: _wb,
        ...cleanCol
      } = col
      void _et
      void _ddg
      void _wb
      // 补全必要属性
      if (!cleanCol.dataType) cleanCol.dataType = 'string'
      if (cleanCol.allowFiltering === undefined) cleanCol.allowFiltering = true
      return cleanCol
    })
  }

  // 为容器类型组件添加 nodeId，便于内部操作（如 splitter 拖拽调整）
  // 为 el-tabs 传递 children 数据，用于渲染 tab 标签头
  return {
    ...base,
    ...extraProps,
    ...overlayProps.value,
    ...(isContainer.value ? { nodeId: props.node.id } : {}),
    ...(props.node.type === 'el-tabs' ? { children: props.node.children } : {}),
  }
})

/** 外层包装样式：负责画布定位、占位尺寸，以及容器级 CSS 样式 */
const wrapperStyle = computed(() => {
  const flexItem: Record<string, string> = {}
  // 栅格列：span>0 用 span/24 栅格制，否则 flexEqual 用 flex:1 等宽
  const span = props.node.props.span
  if (typeof span === 'number' && span > 0) {
    const pct = ((span / 24) * 100).toFixed(2)
    const colCount = 24 / span
    flexItem.flex = `0 0 calc(${pct}% - var(--col-gap, 0px) * ${(colCount - 1) / colCount})`
    flexItem.maxWidth = `calc(${pct}% - var(--col-gap, 0px) * ${(colCount - 1) / colCount})`
  } else if (props.node.props.flexEqual === true) {
    const flexCols = props.node.props.flexCols
    if (typeof flexCols === 'number' && flexCols > 0) {
      flexItem.flex = `0 0 calc(100% / ${flexCols} - var(--col-gap, 0px) * ${(flexCols - 1) / flexCols})`
      flexItem.maxWidth = `calc(100% / ${flexCols} - var(--col-gap, 0px) * ${(flexCols - 1) / flexCols})`
    } else {
      flexItem.flex = '1 1 0'
    }
  }
  const offset = props.node.props.offset
  if (typeof offset === 'number' && offset > 0) {
    flexItem.marginLeft = `${((offset / 24) * 100).toFixed(2)}%`
  }
  // 分割面板尺寸 (size/min/max)
  const size = props.node.props.size
  const sizeUnit = (props.node.props.sizeUnit as string) ?? 'px'
  const min = props.node.props.min
  const max = props.node.props.max
  // 判断父 el-splitter 布局方向：垂直时 flex-basis 控制高度，宽度应撑满容器
  const splitterParent = props.parentId ? findNode(designerStore.schema, props.parentId) : null
  const isVerticalSplitter =
    splitterParent?.node.type === 'el-splitter' &&
    splitterParent.node.props.layout === 'vertical'
  if (typeof size === 'number' && size > 0) {
    if (sizeUnit === '%') {
      flexItem.flex = `0 0 ${size}%`
      if (isVerticalSplitter) {
        flexItem.maxHeight = `${size}%`
        flexItem.width = '100%'
      } else {
        flexItem.maxWidth = `${size}%`
      }
    } else {
      flexItem.flexBasis = `${size}px`
      flexItem.flexGrow = '0'
      flexItem.flexShrink = '0'
    }
  } else if (typeof size === 'number' && size === 0) {
    flexItem.flex = '1'
  }
  if (typeof min === 'number' && min > 0) {
    if (isVerticalSplitter) {
      flexItem.minHeight = `${min}px`
    } else {
      flexItem.minWidth = `${min}px`
    }
  }
  if (typeof max === 'number' && max > 0) {
    if (isVerticalSplitter) {
      flexItem.maxHeight = `${max}px`
    } else {
      flexItem.maxWidth = `${max}px`
    }
  }
  // 栅格 section 类型（宽高由 flex/span/sectionKey 控制，不应用 CSS width/height）
  const nodeType = props.node.type

  if (!isPositioned.value) {
    // 非定位节点（容器内的子节点）：应用 CSS 样式到外层
    if (isContainer.value) {
      if (GRID_SECTION_TYPES.has(nodeType)) {
        // 仅应用非尺寸类 CSS（padding/margin/bg 等）
        const { width: _w, height: _h, ...restStyle } = bodyStyle.value
        void _w
        void _h
        return { ...restStyle, ...flexItem }
      }
      // el-tab-pane: 填满 tabs-content 区域
      if (nodeType === 'el-tab-pane') {
        return { ...bodyStyle.value, width: '100%', height: '100%' }
      }
      return { ...bodyStyle.value, ...flexItem }
    }
    // 非定位、非容器节点：合并 bodyStyle 中的宽高到外层（让选中框反映配置尺寸）
    if (GRID_SECTION_TYPES.has(nodeType)) {
      const { width: _w, height: _h, ...restStyle } = bodyStyle.value
      void _w
      void _h
      return { ...restStyle, ...flexItem }
    }
    // 普通控件：将 CSS 宽高应用到外层 wrapper，使选中框能反映尺寸变化
    {
      const result = { ...bodyStyle.value, ...flexItem }
      if (showLabel.value && labelConfig.value.labelPosition === 'top') {
        result.height = 'auto'
      }
      return result
    }
  }
  // 定位节点：合并画布位置尺寸 + CSS 样式
  const isLayoutRoot =
    isContainer.value && LAYOUT_CONTAINER_TYPES.has(props.node.type)
  const style: Record<string, string> = isLayoutRoot
    ? {
        position: 'absolute',
        left: `${props.node.x ?? 0}px`,
        top: `${props.node.y ?? 0}px`,
        width: `${props.node.width ?? 300}px`,
      }
    : {
        position: 'absolute',
        left: `${props.node.x ?? 0}px`,
        top: `${props.node.y ?? 0}px`,
        width: `${props.node.width ?? 240}px`,
        height: `${props.node.height ?? 120}px`,
      }
  // 先合并 bodyStyle，让用户配置的 CSS 宽高覆盖默认画布尺寸
  if (isContainer.value) {
    Object.assign(style, bodyStyle.value)
    // 布局容器（el-row / el-splitter / el-tabs）默认高度自适应子节点
    if (LAYOUT_CONTAINER_TYPES.has(props.node.type)) {
      // CSS 中已设置 height 时优先使用，否则用 heightAuto 逻辑
      if (bodyStyle.value.height) {
        // CSS height 已通过 Object.assign 应用，保持不变
      } else if (props.node.heightAuto !== false) {
        // 纵向分割面板和标签页需要确定高度才能让子面板/pane 的 flex:1 生效
        if (
          props.node.type === 'el-splitter' &&
          props.node.props.layout === 'vertical'
        ) {
          // 容器高度至少要能容纳所有子面板的最小高度之和，否则子面板溢出、选中框无法覆盖全部面板
          const children = props.node.children ?? []
          const minSum = children.reduce((sum, child) => {
            const min = (child as any).props?.min
            return sum + (typeof min === 'number' && min > 0 ? min : 40)
          }, 0)
          style.height = `${Math.max(props.node.height ?? 200, minSum)}px`
        } else if (props.node.type === 'el-tabs') {
          style.height = `${props.node.height ?? 200}px`
        } else {
          style.height = 'auto'
          // 横向分割面板：容器宽度至少要能容纳所有子面板 min-width 之和，否则子面板溢出、选中框只覆盖部分面板
          if (props.node.type === 'el-splitter') {
            const children = props.node.children ?? []
            const minSum = children.reduce((sum, child) => {
              const min = (child as any).props?.min
              return sum + (typeof min === 'number' && min > 0 ? min : 40)
            }, 0)
            const currentWidth = parseInt(style.width) || 300
            style.width = `${Math.max(currentWidth, minSum)}px`
          }
        }
      } else {
        style.height = `${props.node.height ?? 120}px`
      }
      style.overflow = 'visible'
    }
  } else {
    // 定位、非容器节点：也需要合并 bodyStyle，让 CSS 宽高生效
    Object.assign(style, bodyStyle.value)
  }

  // 根级定位节点：限制最大宽度不超过画布右边界（防止 width:100% + left偏移 导致溢出）
  // 容器内的自由定位子节点由父容器尺寸约束，不应用此限制
  if (props.parentId === null) {
    const nodeX = props.node.x ?? 0
    if (nodeX > 0) {
      style.maxWidth = `calc(100% - ${nodeX}px)`
    }
  }
  if (showLabel.value && labelConfig.value.labelPosition === 'top') {
    style.height = 'auto'
  }
  return style
})

/** 组件体样式：控件级 CSS 样式（宽高百分比/px、内外边距等）应用在组件包装层 */
const bodyStyle = computed(() => resolveNodeStyle(props.node))

/** node-body 样式：排除 width/height，避免与外层 wrapper 双重应用导致百分比缩减 */
const nodeBodyStyle = computed(() => {
  const { width: _w, height: _h, ...rest } = bodyStyle.value
  void _w
  void _h
  return rest
})

/** 容器内容区 inline 样式：根据容器类型+sectionKey 覆盖 gap/flex-direction，穿透 scoped CSS */
const containerContentStyle = computed(() => {
  const style: Record<string, string> = {}
  const nodeType = props.node.type
  const sectionKey = props.node.sectionKey

  // el-container 已移除
  void sectionKey
  if (nodeType === 'el-row') {
    const gutter = props.node.props.gutter ?? 0
    style.display = 'flex'
    style.flexDirection = 'row'
    style.flexWrap = 'wrap'
    style.columnGap = `${gutter}px`
    style.rowGap = `${props.node.props.rowGap ?? 0}px`
    style.justifyContent = (props.node.props.justify as string) || 'start'
    style.alignItems = (props.node.props.align as string) || 'stretch'
    style.width = '100%'
    style.height = 'auto'
    style.minHeight = '40px'
    style.boxSizing = 'border-box'
    style['--col-gap'] = `${gutter}px`
  } else if (nodeType === 'el-splitter') {
    const dir = props.node.props.layout === 'vertical' ? 'column' : 'row'
    style.display = 'flex'
    style.flexDirection = dir
    style.gap = '0px'
    style.width = '100%'
    style.height = '100%'
    style.boxSizing = 'border-box'
    style.overflow = 'visible'
  } else if (nodeType === 'el-tabs') {
    // el-tabs：内容区填满 tabs-content，使用 block 布局（非 flex）让非激活 pane 不占空间
    style.position = 'relative'
    style.width = '100%'
    style.height = '100%'
    style.boxSizing = 'border-box'
    style.overflow = 'hidden'
    style.display = 'block'
  } else if (FREE_POSITION_CONTAINERS.has(nodeType)) {
    // 自由定位容器：子节点使用 absolute 定位，容器需要 position:relative
    style.position = 'relative'
    style.width = '100%'
    style.height = '100%'
    style.minHeight = '40px'
    style.boxSizing = 'border-box'
    style.overflow = 'visible'
    // 网格辅助线（与主画布一致，20px 网格）
    style.backgroundImage =
      'linear-gradient(to right, #ececec 1px, transparent 1px), linear-gradient(to bottom, #ececec 1px, transparent 1px)'
    style.backgroundSize = '20px 20px'
  } else {
    // 默认容器：纵向排列
    style.display = 'flex'
    style.flexDirection = 'column'
    style.gap = '8px'
    style.width = '100%'
    style.minHeight = '40px'
    style.boxSizing = 'border-box'
  }
  return style
})

/** 拖拽悬停位置：top / bottom / inside（容器） */
const dropPos = ref<'' | 'top' | 'bottom' | 'inside'>('')

function selectNode(e: MouseEvent): void {
  e.stopPropagation()
  designerStore.selectedId = props.node.id
  emit('select-single', {
    id: props.node.id,
    additive: e.shiftKey || e.ctrlKey || e.metaKey,
  })
}

/** click 事件处理：Alt+点击时跳过（由 mousedown 处理父容器选中） */
function onNodeClick(e: MouseEvent): void {
  if (e.altKey) return
  // section 子节点不拦截 click，让事件冒泡到父布局容器
  if (STRUCTURAL_CHILD_TYPES.has(props.node.type)) return
  selectNode(e)
}

function onNodeMouseDown(e: MouseEvent): void {
  if (e.button !== 0) return

  // section 子节点（el-col/el-splitter-panel/el-header 等）：
  // - 普通点击：不拦截，让事件冒泡到父布局容器选中父容器
  // - Alt+点击：选中 section 子节点自身（配置 span 等属性）
  if (STRUCTURAL_CHILD_TYPES.has(props.node.type)) {
    if (e.altKey) {
      selectNode(e)
    }
    return
  }

  // Alt+点击：选中父容器（而非当前节点）
  if (e.altKey) {
    const closestCanvasNode = (e.target as HTMLElement).closest('.canvas-node')
    const isDirectClick = closestCanvasNode === e.currentTarget
    if (isDirectClick) {
      // 直接点击本节点：不选中自身，让事件冒泡到父容器
      return
    } else {
      // 事件来自子节点冒泡：选中自身（作为子节点的父容器）
      selectNode(e)
    }
    return
  }

  // 判断点击是否直接在本节点上（而非更深层的子 canvas-node）
  const closestCanvasNode = (e.target as HTMLElement).closest('.canvas-node')
  const isDirectClick = closestCanvasNode === e.currentTarget

  // 非定位布局容器子节点：点击其 section 子节点时重定向选中到自身
  if (!isDirectClick && !isPositioned.value && isLayoutContainer.value) {
    selectNode(e)
    return
  }

  // 定位布局容器：点击子节点（section 子节点如 el-col）时也启动拖拽
  if (!isDirectClick && isPositioned.value && isLayoutContainer.value) {
    selectNode(e)
    e.preventDefault()
    emit('drag-start', {
      event: e,
      nodeId: props.node.id,
      x: props.node.x ?? 0,
      y: props.node.y ?? 0,
      width: props.node.width ?? 240,
      height: props.node.height ?? 120,
      parentId: props.parentId,
    })
    return
  }

  // 点击在子 canvas-node 上：子节点已自行 stopPropagation，不应到达此处；若到达则不处理
  if (!isDirectClick) return

  // 多选时：如果当前节点已在选中集合中且未按修饰键，保持多选状态直接启动拖拽
  const isInMultiSelection =
    designerStore.selectedIds.length > 1 &&
    designerStore.selectedIds.includes(props.node.id)
  if (!isInMultiSelection) {
    selectNode(e)
  } else {
    e.stopPropagation()
  }

  // 定位节点：直接启动拖拽（✥ 手柄仍可使用，但直接点击也能拖动）
  if (isPositioned.value) {
    e.preventDefault()
    emit('drag-start', {
      event: e,
      nodeId: props.node.id,
      x: props.node.x ?? 0,
      y: props.node.y ?? 0,
      width: props.node.width ?? 240,
      height: props.node.height ?? 120,
      parentId: props.parentId,
    })
  }
}

/** 拖拽手柄 mousedown：启动定位节点的自定义拖拽移动 */
function onHandleMouseDown(e: MouseEvent): void {
  if (e.button !== 0) return
  e.stopPropagation()
  e.preventDefault()
  designerStore.selectedId = props.node.id
  emit('drag-start', {
    event: e,
    nodeId: props.node.id,
    x: props.node.x ?? 0,
    y: props.node.y ?? 0,
    width: props.node.width ?? 240,
    height: props.node.height ?? 120,
    parentId: props.parentId,
  })
}

function onResizeStart(e: MouseEvent, dir: string): void {
  if (e.button !== 0) return
  e.stopPropagation()
  e.preventDefault()
  designerStore.selectedId = props.node.id

  // 获取实际尺寸：优先从 DOM 读取（布局容器 heightAuto 时 schema height 不反映实际高度）
  let width = props.node.width ?? 240
  let height = props.node.height ?? 120
  const el = (e.currentTarget as HTMLElement).closest(
    '.canvas-node'
  ) as HTMLElement | null
  if (el) {
    if (!isPositioned.value || isLayoutContainer.value) {
      width = el.offsetWidth
      height = el.offsetHeight
    }
  }

  emit('resize-start', {
    event: e,
    nodeId: props.node.id,
    dir,
    x: props.node.x ?? 0,
    y: props.node.y ?? 0,
    width,
    height,
  })
}

/** 提取到画布根级：将当前节点从容器中移出到画布根级 */
function onExtract(e: MouseEvent): void {
  e.stopPropagation()
  const moved = moveNode(props.node.id, null, designerStore.schema.length)
  if (moved) {
    // 设置默认位置和尺寸（变为定位节点）
    const node = findNode(designerStore.schema, props.node.id)
    if (node && node.node) {
      if (node.node.x == null) node.node.x = 50
      if (node.node.y == null) node.node.y = 50
      if (node.node.width == null) node.node.width = 240
      if (node.node.height == null) node.node.height = 120
    }
  }
}

function onNodeDragStart(e: DragEvent): void {
  if (isPositioned.value) return
  e.stopPropagation()
  if (!e.dataTransfer) return
  // 结构子节点（el-col / el-splitter-panel / el-header 等）拖拽时重定向到父布局容器，
  // 这样用户在子节点上发起拖拽时，实际拖动的是整个布局容器
  let dragNodeId = props.node.id
  if (STRUCTURAL_CHILD_TYPES.has(props.node.type) && props.parentId) {
    const parentFound = findNode(designerStore.schema, props.parentId)
    if (parentFound && LAYOUT_CONTAINER_TYPES.has(parentFound.node.type)) {
      dragNodeId = parentFound.node.id
      designerStore.selectedId = dragNodeId
      designerStore.selectedIds = [dragNodeId]
    }
  }
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('application/x-node-id', dragNodeId)
}

function onDragOver(e: DragEvent): void {
  if (isPositioned.value) return
  // 自由定位容器：让 dragover 冒泡到 CanvasPanel 处理（放置位置由 CanvasPanel 计算）
  if (isContainer.value && FREE_POSITION_CONTAINERS.has(props.node.type)) return
  e.preventDefault()
  e.stopPropagation()
  // 布局 section 子节点（el-col、el-splitter-panel 等）整个区域都是内部放置
  if (STRUCTURAL_CHILD_TYPES.has(props.node.type)) {
    dropPos.value = 'inside'
    return
  }
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
  const ratio = (e.clientY - rect.top) / rect.height
  if (isContainer.value && ratio > 0.25 && ratio < 0.75) {
    dropPos.value = 'inside'
  } else {
    dropPos.value = ratio < 0.5 ? 'top' : 'bottom'
  }
}

function onDragLeave(e: DragEvent): void {
  if (isPositioned.value) return
  // 自由定位容器：跳过
  if (isContainer.value && FREE_POSITION_CONTAINERS.has(props.node.type)) return
  e.stopPropagation()
  dropPos.value = ''
}

function onDrop(e: DragEvent): void {
  if (isPositioned.value) return
  // 自由定位容器：让 drop 事件冒泡到 CanvasPanel 处理（CanvasPanel 会计算精确放置位置）
  if (isContainer.value && FREE_POSITION_CONTAINERS.has(props.node.type)) return
  e.preventDefault()
  e.stopPropagation()
  const pos = dropPos.value
  dropPos.value = ''
  const materialType = e.dataTransfer?.getData('application/x-material')
  const nodeId = e.dataTransfer?.getData('application/x-node-id')
  if (!materialType && !nodeId) return

  if (isContainer.value && pos === 'inside') {
    const insertIndex = props.node.children.length
    if (materialType) addNode(materialType, props.node.id, insertIndex)
    else if (nodeId) moveNode(nodeId, props.node.id, insertIndex)
  } else {
    const insertIndex = pos === 'top' ? props.index : props.index + 1
    if (materialType) addNode(materialType, props.parentId, insertIndex)
    else if (nodeId) moveNode(nodeId, props.parentId, insertIndex)
  }
}

function onDelete(e: MouseEvent): void {
  e.stopPropagation()
  // 受保护的结构子节点禁止删除
  if (isProtected.value) {
    console.warn(
      '[低代码设计器] 受保护的结构子节点不能删除，请直接操作父容器。'
    )
    return
  }
  // 批量删除：当前节点在多选集合中时，删除所有选中节点
  const ids = getSelectionIds()
  if (ids.length > 1 && ids.includes(props.node.id)) {
    removeNodes(ids)
  } else {
    removeNode(props.node.id)
  }
}

function onCopy(e: MouseEvent): void {
  e.stopPropagation()
  copyNode(props.node.id)
}

/** 容器类别：用于区分选中视觉（布局容器用橙色虚线，子容器用蓝色虚线，普通控件用实线） */
const containerKind = computed<'layout' | 'sub' | 'plain'>(() => {
  if (!isContainer.value) return 'plain'
  if (LAYOUT_CONTAINER_TYPES.has(props.node.type)) return 'layout'
  if (SUB_CONTAINER_TYPES.has(props.node.type)) return 'sub'
  return 'plain'
})

/** 是否为受保护的布局容器子节点（不能被拖拽分离或删除） */
const isProtected = computed(() => isProtectedChild(props.node.id))

/** resize 方向：el-row 高度自适应子节点，只允许宽度调整 */
const resizeHandleDirs = computed<string[]>(() => {
  if (isLayoutContainer.value && props.node.type === 'el-row') {
    return ['w', 'e']
  }
  return ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w']
})

/** el-row 高度自适应：监听 DOM 高度变化同步到 schema */
const nodeRef = ref<HTMLElement>()
let heightObserver: ResizeObserver | null = null

function syncLayoutHeight(): void {
  if (!isPositioned.value || !isLayoutContainer.value) return
  if (props.node.heightAuto === false) return
  // 纵向分割面板和标签页使用固定高度（让子面板/pane flex:1 生效），不自动调整
  if (
    props.node.type === 'el-splitter' &&
    props.node.props.layout === 'vertical'
  )
    return
  if (props.node.type === 'el-tabs') return
  const el = nodeRef.value
  if (!el) return
  // 使用 scrollHeight 而非 offsetHeight，避免 selection-border 和 node-actions 等覆盖层影响测量
  const actualHeight = el.scrollHeight
  if (
    actualHeight > 0 &&
    Math.abs(actualHeight - (props.node.height ?? 0)) > 1
  ) {
    props.node.height = actualHeight
  }
}

onMounted(() => {
  if (
    isPositioned.value &&
    isLayoutContainer.value &&
    props.node.heightAuto !== false
  ) {
    syncLayoutHeight()
    // 纵向分割面板和标签页不需要 ResizeObserver（使用固定高度）
    const isVerticalSplitter =
      props.node.type === 'el-splitter' &&
      props.node.props.layout === 'vertical'
    const isTabs = props.node.type === 'el-tabs'
    if (!isVerticalSplitter && !isTabs) {
      heightObserver = new ResizeObserver(() => syncLayoutHeight())
      if (nodeRef.value) heightObserver.observe(nodeRef.value)
    }
  }
  // DataGrid：计算上方元素（toolbar + 高级查询）高度，动态调整表格高度
  if (
    props.node.type === 'data-grid' &&
    (showGridToolbar.value || showAdvancedSearch.value)
  ) {
    nextTick(() => updateGridHeightOffset())
  }
  // DataGrid：监听 wrapper 自身尺寸变化，用于 100% 高度等百分比值转换为像素
  if (props.node.type === 'data-grid' && wrapperRef.value) {
    refreshMeasuredWrapperHeight()
    if (!wrapperResizeObserver) {
      wrapperResizeObserver = new ResizeObserver(() =>
        refreshMeasuredWrapperHeight()
      )
      wrapperResizeObserver.observe(wrapperRef.value)
    }
  }
})

// DataGrid：监听工具栏/高级查询显示状态变化
watch([showGridToolbar, showAdvancedSearch], () => {
  if (props.node.type === 'data-grid') {
    nextTick(() => updateGridHeightOffset())
  }
})

watch(
  () => props.node.children.length,
  () => {
    if (
      isPositioned.value &&
      isLayoutContainer.value &&
      props.node.heightAuto !== false
    ) {
      setTimeout(syncLayoutHeight, 50)
    }
  }
)

// 分割面板方向切换时重新建立/移除 ResizeObserver
watch(
  () => props.node.props.layout,
  (newLayout) => {
    if (
      !isPositioned.value ||
      !isLayoutContainer.value ||
      props.node.heightAuto === false
    )
      return
    if (props.node.type !== 'el-splitter') return
    const isVertical = newLayout === 'vertical'
    if (isVertical) {
      // 切到纵向：移除 observer，使用固定高度
      if (heightObserver) {
        heightObserver.disconnect()
        heightObserver = null
      }
      // 确保有合理的最小高度（横向时 heightAuto 可能把高度设得很小）
      if (!props.node.height || props.node.height < 200) {
        props.node.height = 200
      }
    } else {
      // 切到横向：建立 observer，使用 auto 高度
      if (!heightObserver && nodeRef.value) {
        syncLayoutHeight()
        heightObserver = new ResizeObserver(() => syncLayoutHeight())
        heightObserver.observe(nodeRef.value)
      }
    }
  }
)

onBeforeUnmount(() => {
  if (heightObserver) {
    heightObserver.disconnect()
    heightObserver = null
  }
  disposeWrapperObserver()
})

const wrapperClass = computed(() => {
  const cls = ['canvas-node']
  if (isPositioned.value) cls.push('canvas-node-positioned')
  if (isSelected.value) {
    cls.push('canvas-node-selected')
    cls.push(`canvas-node-selected-${containerKind.value}`)
  }
  if (isContainer.value) cls.push('canvas-node-container')
  if (isProtected.value) cls.push('canvas-node-protected')
  if (dropPos.value === 'top') cls.push('drag-over-top')
  if (dropPos.value === 'bottom') cls.push('drag-over-bottom')
  if (dropPos.value === 'inside') cls.push('drag-over-container')
  return cls.join(' ')
})
</script>

<template>
  <div
    :id="`canvas-node-${node.id}`"
    ref="nodeRef"
    :class="wrapperClass"
    :style="wrapperStyle"
    :draggable="!isPositioned"
    :data-section="node.sectionKey || undefined"
    @click="onNodeClick"
    @mousedown="onNodeMouseDown"
    @dragstart="onNodeDragStart"
    @dragover="onDragOver"
    @dragleave="onDragLeave"
    @drop="onDrop"
  >
    <div v-if="isSelected" class="node-actions" @click.stop @mousedown.stop>
      <span class="node-label">
        {{ cfg?.title }}
        <span
          v-if="isProtected"
          class="node-protected-icon"
          :title="t('受保护的结构子节点')"
          >🔒</span
        >
      </span>
      <span
        v-if="isPositioned"
        class="action-btn drag-handle"
        :title="t('按住拖动移动控件')"
        @mousedown="onHandleMouseDown"
        >✥</span
      >
      <button
        v-if="!isPositioned && !isProtected"
        class="action-btn"
        :title="t('提取到画布根级（脱离当前容器）')"
        @click="onExtract"
      >
        ⇪
      </button>
      <button class="action-btn" :title="t('复制')" @click="onCopy">⧉</button>
      <button
        class="action-btn action-btn-danger"
        :class="{ 'action-btn-disabled': isProtected }"
        :title="
          isProtected
            ? t('受保护的结构子节点，不能删除，请直接操作父容器')
            : t('删除')
        "
        @click="onDelete"
      >
        ✕
      </button>
    </div>

    <!-- 普通控件：外包一层承载控件级 CSS 样式（排除 width/height，已由外层 wrapper 控制） -->
    <div v-if="!isContainer" class="node-body" :style="nodeBodyStyle">
      <!-- dx-data-grid：wrapper 包裹工具栏、高级查询和表格 -->
      <template v-if="node.type === 'data-grid'">
        <div class="data-grid-wrapper" ref="wrapperRef">
          <AdvancedSearchPanel
            v-if="showAdvancedSearch"
            :columns="gridColumns"
            :grid-id="node.id"
            @height-change="updateGridHeightOffset"
          />
          <div v-if="showGridToolbar" class="grid-toolbar" ref="toolbarRef">
            <div class="toolbar-group toolbar-before">
              <template v-for="({ item, idx }, ti) in beforeItems" :key="'b'+ti">
                <el-dropdown v-if="item.widget === 'dxDropDownButton'" v-show="!overflowIndices.has(idx)" :data-toolbar-btn="idx">
                  <el-button :type="(item.options?.type as any) || 'default'" :text="item.options?.stylingMode === 'text'" :bg="item.options?.stylingMode === 'text'" :plain="item.options?.stylingMode === 'outlined'" :class="item.options?.elementAttr?.class" :style="item.options?.elementAttr?.style">
                    <ToolbarIcon v-if="item.options?.icon" :name="item.options.icon" />
                    <span>{{ item.options?.text }}</span>
                    <span style="margin-left: 2px">▾</span>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-for="c in ((item.options as any)?.items || [])" :key="c.name" :command="c.name">
                        <ToolbarIcon v-if="c.icon" :name="c.icon" />
                        <span>{{ c.text }}</span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-button
                  v-else
                  v-show="!overflowIndices.has(idx)"
                  :data-toolbar-btn="idx"
                  :type="(item.options?.type as any) || 'default'"
                  :text="item.options?.stylingMode === 'text'"
                  :bg="item.options?.stylingMode === 'text'" :plain="item.options?.stylingMode === 'outlined'"
                  :class="item.options?.elementAttr?.class"
                  :style="item.options?.elementAttr?.style"
                >
                  <ToolbarIcon v-if="item.options?.icon" :name="item.options.icon" />
                  <span>{{ item.options?.text }}</span>
                </el-button>
              </template>
            </div>
            <div class="toolbar-group toolbar-center">
              <template v-for="({ item, idx }, ti) in centerItems" :key="'c'+ti">
                <el-dropdown v-if="item.widget === 'dxDropDownButton'" v-show="!overflowIndices.has(idx)" :data-toolbar-btn="idx">
                  <el-button :type="(item.options?.type as any) || 'default'" :text="item.options?.stylingMode === 'text'" :bg="item.options?.stylingMode === 'text'" :plain="item.options?.stylingMode === 'outlined'" :class="item.options?.elementAttr?.class" :style="item.options?.elementAttr?.style">
                    <ToolbarIcon v-if="item.options?.icon" :name="item.options.icon" />
                    <span>{{ item.options?.text }}</span>
                    <span style="margin-left: 2px">▾</span>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-for="c in ((item.options as any)?.items || [])" :key="c.name" :command="c.name">
                        <ToolbarIcon v-if="c.icon" :name="c.icon" />
                        <span>{{ c.text }}</span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-button
                  v-else
                  v-show="!overflowIndices.has(idx)"
                  :data-toolbar-btn="idx"
                  :type="(item.options?.type as any) || 'default'"
                  :text="item.options?.stylingMode === 'text'"
                  :bg="item.options?.stylingMode === 'text'" :plain="item.options?.stylingMode === 'outlined'"
                  :class="item.options?.elementAttr?.class"
                  :style="item.options?.elementAttr?.style"
                >
                  <ToolbarIcon v-if="item.options?.icon" :name="item.options.icon" />
                  <span>{{ item.options?.text }}</span>
                </el-button>
              </template>
            </div>
            <div class="toolbar-group toolbar-after">
              <template v-for="({ item, idx }, ti) in afterItems" :key="'a'+ti">
                <el-dropdown v-if="item.widget === 'dxDropDownButton'" v-show="!overflowIndices.has(idx)" :data-toolbar-btn="idx">
                  <el-button :type="(item.options?.type as any) || 'default'" :text="item.options?.stylingMode === 'text'" :bg="item.options?.stylingMode === 'text'" :plain="item.options?.stylingMode === 'outlined'" :class="item.options?.elementAttr?.class" :style="item.options?.elementAttr?.style">
                    <ToolbarIcon v-if="item.options?.icon" :name="item.options.icon" />
                    <span>{{ item.options?.text }}</span>
                    <span style="margin-left: 2px">▾</span>
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item v-for="c in ((item.options as any)?.items || [])" :key="c.name" :command="c.name">
                        <ToolbarIcon v-if="c.icon" :name="c.icon" />
                        <span>{{ c.text }}</span>
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <el-button
                  v-else
                  v-show="!overflowIndices.has(idx)"
                  :data-toolbar-btn="idx"
                  :type="(item.options?.type as any) || 'default'"
                  :text="item.options?.stylingMode === 'text'"
                  :bg="item.options?.stylingMode === 'text'" :plain="item.options?.stylingMode === 'outlined'"
                  :class="item.options?.elementAttr?.class"
                  :style="item.options?.elementAttr?.style"
                >
                  <ToolbarIcon v-if="item.options?.icon" :name="item.options.icon" />
                  <span>{{ item.options?.text }}</span>
                </el-button>
              </template>
            </div>
            <div v-if="overflowIndicesArray.length" class="toolbar-more-wrap" data-more-btn>
              <button class="more-btn-native" @click="toggleMoreMenu">更多 ▾</button>
              <Teleport to="body">
                <div v-if="moreMenuVisible" class="more-dropdown-overlay-fs" @click="moreMenuVisible = false"></div>
                <div v-if="moreMenuVisible" class="more-dropdown-menu-fs" :style="moreMenuStyle">
                  <template v-for="idx in overflowIndicesArray" :key="'more'+idx">
                    <div class="more-dropdown-item-fs" @click="triggerOverflowItem(idx)">
                      <ToolbarIcon v-if="gridToolbarItems[idx]?.options?.icon" :name="(gridToolbarItems[idx].options as any).icon" />
                      <span>{{ gridToolbarItems[idx]?.options?.text }}</span>
                      <span v-if="gridToolbarItems[idx]?.widget === 'dxDropDownButton'" class="more-arrow">{{ expandedMoreIdx === idx ? '▸' : '▾' }}</span>
                    </div>
                    <template v-if="expandedMoreIdx === idx">
                      <div
                        v-for="child in ((gridToolbarItems[idx] as any)?.options?.items || [])"
                        :key="'more-child-'+child.name"
                        class="more-dropdown-child-fs"
                      >
                        <ToolbarIcon v-if="child.icon" :name="child.icon" />
                        <span>{{ child.text }}</span>
                      </div>
                    </template>
                  </template>
                </div>
              </Teleport>
            </div>
          </div>
          <component
            :is="materialComponents[node.type]"
            v-bind="gridBoundProps"
          />
        </div>
      </template>
      <template v-else>
        <div
          v-if="showLabel"
          class="form-field-wrapper"
          :class="`label-${labelConfig.labelPosition}`"
        >
          <label class="form-field-label">
            <span
              v-if="
                labelConfig.required &&
                labelConfig.requiredPosition === 'before'
              "
              class="form-field-required"
              >*</span
            >
            {{ labelConfig.label }}:
            <span
              v-if="
                labelConfig.required && labelConfig.requiredPosition === 'after'
              "
              class="form-field-required"
              >*</span
            >
            <span v-if="labelConfig.tooltipEnabled" class="form-field-info"
              >ⓘ<span class="form-field-tooltip">{{
                labelConfig.tooltipText
              }}</span></span
            >
          </label>
          <div class="form-field-control">
            <component
              :is="materialComponents[node.type]"
              v-bind="boundProps"
            />
          </div>
        </div>
        <component
          v-else
          :is="materialComponents[node.type]"
          v-bind="boundProps"
        />
      </template>
      <!-- 透明覆盖层：拦截 iframe 等元素的事件捕获，确保鼠标事件冒泡到 canvas-node -->
      <div class="node-body-overlay"></div>
    </div>

    <!-- 容器控件：子节点渲染在内容区，控件级 CSS 样式已应用在外层 wrapper（定位时）或单独内容区（非定位时） -->
    <component :is="materialComponents[node.type]" v-else v-bind="boundProps">
      <div class="container-content" :style="containerContentStyle">
        <div v-if="!node.children.length" class="container-empty">
          {{ t('拖入组件到此容器') }}
        </div>
        <!-- 容器内拖拽对齐辅助线 -->
        <template v-if="dragGuides.length">
          <div
            v-for="guide in dragGuides"
            :key="`guide-${guide.type}-${guide.pos}`"
            class="container-guide-line"
            :class="guide.type"
            :style="
              guide.type === 'v'
                ? { left: guide.pos + 'px' }
                : { top: guide.pos + 'px' }
            "
          />
        </template>
        <CanvasNode
          v-for="(child, i) in node.children"
          :key="child.id"
          :node="child"
          :parent-id="node.id"
          :index="i"
          :positioned="childrenPositioned"
          :selected="isNodeSelected(child.id)"
          @drag-start="emit('drag-start', $event)"
          @resize-start="emit('resize-start', $event)"
          @select-single="emit('select-single', $event)"
        />
      </div>
    </component>

    <div v-if="isSelected && !isProtected" class="resize-handles">
      <span
        v-for="dir in resizeHandleDirs"
        :key="dir"
        :class="['resize-handle', dir]"
        @mousedown="onResizeStart($event, dir)"
      />
    </div>

    <!-- 选中指示框：真实 DOM 元素，覆盖在所有子内容之上 -->
    <div
      v-if="isSelected"
      class="selection-border"
      :class="`selection-border-${containerKind}`"
    ></div>
  </div>
</template>

<style scoped>
.canvas-node {
  position: relative;
  cursor: move;
}
.canvas-node-positioned {
  box-sizing: border-box;
  overflow: visible;
  /* 确保定位节点的层级关系正确（后渲染的在上层） */
  z-index: 1;
}
.canvas-node-selected {
  z-index: 5;
}
/* 选中指示框：真实 DOM 元素，绝对定位覆盖在所有子内容之上 */
.selection-border {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  z-index: 9999;
  box-sizing: border-box;
}
/* 普通控件选中：1px 蓝色实线 */
.selection-border-plain {
  border: 1px solid #1976d2;
  box-shadow: 0 0 0 2px rgba(25, 118, 210, 0.12);
}
/* 布局容器选中：2px 橙色虚线 */
.selection-border-layout {
  border: 2px dashed #ff7043;
}
.canvas-node-selected-layout {
  background: rgba(255, 112, 67, 0.06);
}
/* 子容器选中：2px 绿色实线 */
.selection-border-sub {
  border: 2px solid #2e7d32;
}
.canvas-node-selected-sub {
  background: rgba(46, 125, 50, 0.12) !important;
}
/* 子容器选中时，内容区也有高亮边框 */
.canvas-node-selected-sub > .container-content,
.canvas-node-selected-sub > .node-body {
  border: 1px solid rgba(46, 125, 50, 0.5);
  border-radius: 2px;
}
/* 容器未选中时也给出轻微暗示（浅灰虚线），帮助用户区分容器节点与普通控件 */
.canvas-node-container:not(.canvas-node-selected):not(.canvas-node-positioned) {
  outline: 1px dashed #e0e0e0;
  outline-offset: 1px;
}
/* 容器节点的操作条（选中时）：不同类别不同底色 */
.canvas-node-selected-layout > .node-actions {
  background: #ff7043;
}
.canvas-node-selected-sub > .node-actions {
  background: #43a047;
}
.node-actions {
  position: absolute;
  top: -24px;
  right: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  gap: 4px;
  background: #1976d2;
  color: #fff;
  border-radius: 3px;
  padding: 2px 6px;
  font-size: 12px;
  line-height: 18px;
}
.node-label {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: flex;
  align-items: center;
  gap: 3px;
}
.node-protected-icon {
  font-size: 10px;
  cursor: help;
}
.action-btn {
  border: none;
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
  border-radius: 3px;
  cursor: pointer;
  font-size: 12px;
  padding: 0 5px;
  line-height: 16px;
}
.action-btn:hover {
  background: rgba(255, 255, 255, 0.35);
}
.action-btn-danger:hover {
  background: #e53935;
}
.action-btn-disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.action-btn-disabled:hover {
  background: rgba(255, 255, 255, 0.2) !important;
}
/* 拖拽手柄：按住拖动移动定位节点 */
.drag-handle {
  cursor: move;
  display: inline-block;
}
.drag-handle:hover {
  background: rgba(255, 255, 255, 0.35);
}
/* 受保护节点的特殊样式 */
.canvas-node-protected {
  position: relative;
}
.canvas-node-protected::after {
  content: '';
  position: absolute;
  top: 2px;
  right: 2px;
  width: 16px;
  height: 16px;
  background: rgba(255, 152, 0, 0.85);
  border-radius: 3px;
  z-index: 5;
  pointer-events: none;
}
.adv-search-preview {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fafafa;
  margin-bottom: 4px;
  padding: 6px 8px;
}
.adv-search-mock {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.adv-mock-row {
  display: flex;
  gap: 4px;
  align-items: center;
}
.adv-mock-field,
.adv-mock-op {
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  padding: 2px 8px;
  font-size: 11px;
  color: #909399;
  background: #fff;
}
.adv-mock-field {
  min-width: 80px;
}
.adv-mock-op {
  min-width: 60px;
}
.adv-mock-val {
  flex: 1;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  padding: 2px 8px;
  font-size: 11px;
  color: #c0c4cc;
}
.adv-mock-toolbar {
  display: flex;
  gap: 6px;
  font-size: 11px;
  color: #909399;
}
.adv-mock-toolbar .adv-mock-primary {
  color: #1976d2;
  font-weight: 500;
}
.container-content {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 40px;
  width: 100%;
  height: 100%;
  box-sizing: border-box;
}
.container-guide-line {
  position: absolute;
  z-index: 30;
  pointer-events: none;
}
.container-guide-line.v {
  top: 0;
  bottom: 0;
  border-left: 1px dashed #1976d2;
}
.container-guide-line.h {
  left: 0;
  right: 0;
  border-top: 1px dashed #1976d2;
}
.node-body {
  position: relative;
  width: 100%;
  height: 100%;
  box-sizing: border-box;
}
/* DevExtreme 组件填充 node-body 容器 */
.node-body > :deep(.dx-widget) {
  width: 100%;
  height: 100%;
}
/* DataGrid 独立工具栏 + 表格 布局 */
/* DataGrid wrapper 布局：flex column 使工具栏/高级查询/表格纵向排列
 * overflow: hidden 兜底：万一 100% 高度转换链路失效时不让表格撑出容器 */
.data-grid-wrapper {
  width: 100%;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.grid-toolbar {
  display: flex;
  align-items: center;
  gap: 0;
  min-height: 40px;
  padding: 4px 12px;
  border-bottom: 1px solid #e4e7ed;
  background-color: #ffffff;
  box-sizing: border-box;
  flex-shrink: 0;

}
.toolbar-group {
  display: flex;
  align-items: center;
  gap: 0;
  flex-shrink: 0;
  min-height: 32px;
}
.toolbar-before {
}
.toolbar-center {
  margin-left: auto;
  margin-right: auto;
}
.toolbar-after {
  margin-left: auto;
}
.grid-toolbar .el-button {
  height: 32px;
  min-height: 32px;
  padding: 0 16px;
  font-size: 13px;
  margin-left: 0;
}
.grid-toolbar .el-button > * + * {
  margin-left: 4px;
}
.toolbar-more-wrap {
  position: relative;
  flex-shrink: 0;
  margin-left: auto;
}
.more-btn-native {
  height: 32px;
  padding: 0 16px;
  font-size: 13px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  white-space: nowrap;
}
.more-btn-native:hover {
  border-color: #c0c4cc;
  color: #606266;
}
.more-dropdown-overlay-fs {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 9999;
}
.more-dropdown-menu-fs {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0,0,0,.15);
  padding: 4px 0;
  min-width: 120px;
}
.more-dropdown-item-fs {
  padding: 6px 16px;
  cursor: pointer;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}
.more-dropdown-item-fs:hover {
  background: #f5f7fa;
  color: #409eff;
}
.more-arrow { margin-left: auto; font-size: 10px; color: #909399; }
.more-dropdown-child-fs {
  padding: 6px 16px 6px 32px;
  cursor: default;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  color: #606266;
}
.node-body-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1;
  cursor: move;
}
.container-empty {
  color: #c0c4cc;
  font-size: 12px;
  text-align: center;
  padding: 12px 0;
}

.resize-handles {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.resize-handle {
  position: absolute;
  width: 8px;
  height: 8px;
  background: #fff;
  border: 1px solid #1976d2;
  border-radius: 1px;
  pointer-events: auto;
  z-index: 5;
}
.resize-handle.n {
  top: -4px;
  left: 50%;
  transform: translateX(-50%);
  cursor: n-resize;
}
.resize-handle.s {
  bottom: -4px;
  left: 50%;
  transform: translateX(-50%);
  cursor: s-resize;
}
.resize-handle.e {
  right: -4px;
  top: 50%;
  transform: translateY(-50%);
  cursor: e-resize;
}
.resize-handle.w {
  left: -4px;
  top: 50%;
  transform: translateY(-50%);
  cursor: w-resize;
}
.resize-handle.nw {
  top: -4px;
  left: -4px;
  cursor: nwse-resize;
}
.resize-handle.ne {
  top: -4px;
  right: -4px;
  cursor: nesw-resize;
}
.resize-handle.sw {
  bottom: -4px;
  left: -4px;
  cursor: nesw-resize;
}
.resize-handle.se {
  bottom: -4px;
  right: -4px;
  cursor: nwse-resize;
}

/* 表单控件 label 包裹层 */
.form-field-wrapper {
  display: flex;
  align-items: flex-start;
  width: 100%;
  height: 100%;
  box-sizing: border-box;
}
.form-field-wrapper.label-left {
  flex-direction: row;
  align-items: center;
  gap: 8px;
}
.form-field-wrapper.label-top {
  flex-direction: column;
  align-items: stretch;
  gap: 4px;
  height: auto;
}
.form-field-wrapper.label-top .form-field-control {
  width: 100%;
  flex: none;
}
.form-field-wrapper.label-top .form-field-control > :deep(.dx-widget) {
  width: 100%;
}
.form-field-label {
  font-size: 14px;
  color: #374151;
  white-space: nowrap;
  flex-shrink: 0;
  cursor: default;
  user-select: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.form-field-wrapper.label-top .form-field-label {
  margin-bottom: 2px;
}
.form-field-required {
  color: #ef4444;
  font-weight: bold;
  margin: 0 2px;
}
.form-field-info {
  position: relative;
  cursor: help;
  color: #6b7280;
  font-size: 14px;
  display: inline-flex;
  align-items: center;
}
.form-field-tooltip {
  display: none;
  position: absolute;
  left: 100%;
  top: 0;
  background: #fff;
  color: #374151;
  border: 1px solid #d1d5db;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  padding: 8px 12px;
  border-radius: 4px;
  font-size: 13px;
  white-space: pre-wrap;
  width: 500px;
  z-index: 9999;
  margin-left: 8px;
  line-height: 1.5;
}
.form-field-info:hover .form-field-tooltip {
  display: block;
}
.form-field-control {
  flex: 1;
  min-width: 0;
}
.form-field-control > :deep(.dx-widget) {
  width: 100%;
}
</style>
