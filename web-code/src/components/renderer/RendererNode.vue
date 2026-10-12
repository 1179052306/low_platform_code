<script setup lang="ts">
import {
  computed,
  ref,
  onMounted,
  onBeforeUnmount,
  createApp,
  h,
  nextTick,
  watch,
  inject,
  provide,
} from 'vue'
import type { ComponentSchema } from '@/types/schema'
import type { DetailTableConfig } from '@/types/schema'
import { materialComponents, getConfig, resolveNodeProps } from '@/materials'
import {
  designerStore,
  resolveNodeStyle,
  findNode,
  findNodeByFieldNameIn,
  collectAllNodes,
  DESIGNER_STORE_KEY,
  type RenderStore,
} from '@/store/designer'
import { DxValidator } from 'devextreme-vue/validator'
import DxDropDownBox from 'devextreme-vue/drop-down-box'
import DxDataGrid from 'devextreme-vue/data-grid'
import { DxMasterDetail } from 'devextreme-vue/data-grid'
import DxToolbar from 'devextreme-vue/toolbar'
import {
  VALIDATABLE_TYPES,
  type ValidationRuleConfig,
  registerValidator,
  unregisterValidator,
  getValidator,
} from '@/materials/dx-validation'
import CustomStore from 'devextreme/data/custom_store'
import AdvancedSearchPanel from './AdvancedSearchPanel.vue'
import type { SearchCondition } from './AdvancedSearchPanel.vue'
import { pageModal } from './pageModal'
import { httpRequest, extractPayload, extractList } from '@/utils/request'
import { apiCall, resolveApi, type ApiCallResult } from '@/api'
import { isFieldHidden, isFieldReadonly, isFrozen, isActionFrozen } from '@/utils/permission'
import { evaluateExpr, pickFields } from '@/utils/compute-expr'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createSandboxedFunction } from '@/utils/sandbox'
import { gatewayCall } from '@/api'
import { t } from '@/store/lang'
import BatchActionDialog from './BatchActionDialog.vue'

const props = defineProps<{
  node: ComponentSchema
  positioned?: boolean
  parentId?: string | null
  index?: number
}>()

const cfg = getConfig(props.node.type)

/** 字段权限：当前节点是否因权限隐藏（G3 权限驱动渲染，permissions 未加载时放行） */
const nodeFieldHidden = computed(() => {
  if (!props.node.fieldName) return false
  return isFieldHidden(designerStore.pageName, props.node.fieldName)
})

/** 字段权限：当前节点是否只读（permissions 未加载时放行） */
const nodeFieldReadonly = computed(() => {
  if (!props.node.fieldName) return false
  if (isFieldReadonly(designerStore.pageName, props.node.fieldName)) return true
  // 数据冻结：冻结状态下 edit 操作被禁用时，所有字段只读
  if (isActionFrozen(designerStore.pageName, 'edit')) return true
  return false
})

/** 表单控件类型集合（需要渲染独立 label） */
const FORM_CONTROL_TYPES = new Set([
  'dx-text-box',
  'dx-text-area',
  'dx-number-box',
  'dx-check-box',
  'dx-switch',
  'dx-slider',
  'dx-range-slider',
  'dx-calendar',
  'dx-date-box',
  'dx-date-range-box',
  'dx-color-box',
  'dx-select-box',
  'dx-lookup',
  'dx-tag-box',
  'dx-autocomplete',
  'dx-radio-group',
  'dx-drop-down-button',
  'dx-drop-down-box',
  'dx-file-uploader',
  'dx-html-editor',
  'dx-base-data',
])

/** 计算规则运行时深度计数器（模块级，防止规则链无限循环） */
let computeRuleDepth = 0
const COMPUTE_RULE_MAX_DEPTH = 10

/** 当前渲染上下文的 store（通过 inject 获取，子页面弹窗使用独立实例） */
const store = inject(DESIGNER_STORE_KEY, designerStore) as RenderStore
// 向下提供，确保子 RendererNode 继承同一 store
provide(DESIGNER_STORE_KEY, store)

/** 存储 dx-data-grid 的 DevExtreme 实例，供 editCellTemplate 使用 */
const gridInstanceRef = ref<any>(null)

/** 获取真实的 DataGrid 实例（兼容 Vue 包装器） */
function getGridInstance(): any {
  const inst = gridInstanceRef.value
  if (!inst) return null
  if (typeof inst.instance === 'function') {
    try {
      return inst.instance()
    } catch {
      return inst
    }
  }
  return inst
}

/** 复制文本到系统剪贴板，返回是否成功 */
async function copyTextToClipboard(text: string): Promise<boolean> {
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    // 回退到 execCommand
  }
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.top = '-9999px'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.focus()
    ta.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    return ok
  } catch {
    return false
  }
}

/** 取单元格显示文本（优先 DOM 文本，回退到行数据字段值） */
function getCellDisplayText(grid: any, row: any, column: any): string {
  try {
    if (grid && row && column) {
      const el = grid.getCellElement?.(row.rowIndex, column.index)
      if (el) {
        const text = (el as HTMLElement).textContent ?? ''
        if (text.trim()) return text.trim()
      }
    }
  } catch {
    // 回退
  }
  if (row?.data && column?.dataField) {
    const v = (row.data as Record<string, unknown>)[column.dataField]
    return v == null ? '' : String(v)
  }
  return ''
}

/** 取一行数据按可见列顺序的 TSV 文本（方便粘贴到 Excel） */
function getRowTsv(grid: any, row: any): string {
  let cols: any[] = []
  try {
    cols = grid?.getVisibleColumns?.() ?? []
  } catch {
    cols = []
  }
  const data = (row?.data ?? {}) as Record<string, unknown>
  const cells: string[] = []
  for (const col of cols) {
    if (!col.dataField) continue
    let text = ''
    try {
      const el = grid?.getCellElement?.(row.rowIndex, col.index)
      if (el) {
        const t = (el as HTMLElement).textContent ?? ''
        if (t.trim()) text = t.trim()
      }
    } catch {
      // 回退
    }
    if (!text) {
      const v = data[col.dataField]
      text = v == null ? '' : String(v)
    }
    cells.push(text.replace(/\t/g, ' ').replace(/\r?\n/g, ' '))
  }
  return cells.join('\t')
}

/** 为 dx-data-grid 构造内置右键复制菜单项 */
function buildCopyContextMenuItems(e: any): any[] {
  const items: any[] = []
  const grid = getGridInstance()
  const target = e?.target
  const row = e?.row
  const column = e?.column
  if (target === 'content' && row && column && column.dataField) {
    items.push({
      text: '复制单元格内容',
      onItemClick: async () => {
        const text = getCellDisplayText(grid, row, column)
        const ok = await copyTextToClipboard(text)
        if (ok) ElMessage.success('已复制单元格内容')
        else ElMessage.error('复制失败')
      },
    })
  }
  if (row) {
    items.push({
      text: '复制行数据',
      onItemClick: async () => {
        const tsv = getRowTsv(grid, row)
        const ok = await copyTextToClipboard(tsv)
        if (ok) ElMessage.success('已复制行数据')
        else ElMessage.error('复制失败')
      },
    })
  }
  return items
}

/** DataGrid 工具栏是否显示 */
const showGridToolbar = computed(() => {
  if (props.node.type !== 'dx-data-grid') return false
  if (store.selectMode) return false
  const toolbar = props.node.props.toolbar as
    | Record<string, unknown>
    | undefined
  return toolbar?.visible === true
})

/** 高级查询是否显示 */
const showAdvancedSearch = computed(() => {
  if (props.node.type !== 'dx-data-grid') return false

  return props.node.props.advancedSearch === true
})

/** 表单 schema 中下拉框字段的选项映射（dbField 大写 → items），用于表格列自动注入下拉筛选 */
const formSelectBoxMap = computed(() => {
  const map = new Map<string, { value: string; text: string }[]>()
  try {
    const formSchema = (store as any).bizSchemas?.form
    if (!Array.isArray(formSchema)) return map
    for (const n of collectAllNodes(formSchema)) {
      if (n.type === 'dx-select-box' && n.dbField) {
        const items = n.props?.items as { value: string; text: string }[] | undefined
        if (Array.isArray(items) && items.length) {
          map.set((n.dbField as string).toUpperCase(), items)
        }
      }
    }
  } catch { /* ignore */ }
  return map
})

/** 表单 schema 中基础资料控件映射（dbField 大写 → { refPageId, displayField, valueField }），用于高级查询渲染基础资料控件 */
const formBaseDataMap = computed(() => {
  const map = new Map<string, { refPageId: string; displayField: string; valueField: string }>()
  try {
    const formSchema = (store as any).bizSchemas?.form
    if (!Array.isArray(formSchema)) return map
    for (const n of collectAllNodes(formSchema)) {
      if (n.type === 'dx-base-data' && n.dbField) {
        const p = n.props as any
        if (p?.refPageId && p?.displayField) {
          map.set((n.dbField as string).toUpperCase(), {
            refPageId: p.refPageId,
            displayField: p.displayField,
            valueField: p.valueField || 'id',
          })
        }
      }
    }
  } catch { /* ignore */ }
  return map
})

/** DataGrid 列配置（用于高级查询面板） */
const gridColumns = computed(() => {
  const cols = props.node.props.columns as Record<string, unknown>[] | undefined
  if (!Array.isArray(cols)) return []
  const selectBoxMap = formSelectBoxMap.value
  const baseDataMap = formBaseDataMap.value
  return cols
    .filter((c) => {
      const df = c.dataField
      return typeof df === 'string' && df && !df.includes('.')
    })
    .map((c) => {
      const dataField = c.dataField as string
      const editorType = c.editorType as string | undefined
      const colLookup = c.lookup as
        | {
            dataSource: unknown[]
            valueExpr: string
            displayExpr: string
            dataSourceType?: 'static' | 'remote'
            remoteUrl?: string
            method?: string
          }
        | undefined
      if (editorType !== 'selectBox' && !colLookup) {
        const formItems = selectBoxMap.get(dataField.toUpperCase())
        if (formItems) {
          return {
            dataField,
            caption: (c.caption as string) || dataField,
            dataType: (c.dataType as string) || 'string',
            visible: c.visible !== false,
            editorType: 'selectBox' as string,
            isSystemField: c.isSystemField as boolean | undefined,
            lookup: {
              dataSource: JSON.parse(JSON.stringify(formItems)),
              valueExpr: 'value',
              displayExpr: 'text',
              dataSourceType: 'static' as const,
            },
          }
        }
        const baseDataInfo = baseDataMap.get(dataField.toUpperCase())
        if (baseDataInfo) {
          return {
            dataField,
            caption: (c.caption as string) || dataField,
            dataType: (c.dataType as string) || 'string',
            visible: c.visible !== false,
            editorType,
            isSystemField: c.isSystemField as boolean | undefined,
            lookup: colLookup,
            baseDataRef: baseDataInfo,
          }
        }
      }
      return {
        dataField,
        caption: (c.caption as string) || dataField,
        dataType: (c.dataType as string) || 'string',
        visible: c.visible !== false,
        editorType,
        isSystemField: c.isSystemField as boolean | undefined,
        lookup: colLookup,
      }
    })
})

/** 高级查询触发搜索 */
function onAdvSearch(conditions: SearchCondition[]) {
  advSearchConditions.value = conditions
  // 通过 schemaRevision 触发 boundProps 重新计算，DataGrid 自动重新加载
  store.schemaRevision += 1
}

/** 高级查询重置 */
function onAdvSearchReset() {
  advSearchConditions.value = []
  store.schemaRevision += 1
}

/** DataGrid 高度偏移量（toolbar + 高级查询占用的高度） */
const gridHeightOffset = ref(0)
const wrapperRef = ref<HTMLElement | null>(null)

/**
 * 计算 DataGrid 上方元素（toolbar + 高级查询）的总高度，
 * 用于动态调整 DataGrid 高度，避免溢出
 */
function updateGridHeightOffset() {
  if (!wrapperRef.value) return
  let offset = 0
  const children = wrapperRef.value.children
  for (let i = 0; i < children.length; i++) {
    const el = children[i] as HTMLElement
    // 只计算 DataGrid 之前的元素（toolbar、高级查询面板等）
    if (
      el.classList.contains('dx-datagrid') ||
      el.querySelector('.dx-datagrid')
    )
      break
    offset += el.offsetHeight
    // offsetHeight 不含 margin，需补上外边距，避免溢出产生滚动条
    const style = getComputedStyle(el)
    offset += parseFloat(style.marginTop) || 0
    offset += parseFloat(style.marginBottom) || 0
  }
  gridHeightOffset.value = offset
}

/** DataGrid 专用 props：根据上方元素高度动态调整 DataGrid 高度 */
const gridBoundProps = computed<Record<string, unknown>>(() => {
  const base: Record<string, unknown> = { ...boundProps.value }
  if (showGridToolbar.value || showAdvancedSearch.value) {
    const h = base.height
    const offset = gridHeightOffset.value
    if (offset > 0) {
      if (typeof h === 'number') {
        base.height = Math.max(100, h - offset)
      } else if (typeof h === 'string') {
        // 百分比高度：如 "100%" → "calc(100% - 80px)"
        const pctMatch = h.match(/^(\d+(?:\.\d+)?)%$/)
        if (pctMatch) {
          base.height = `calc(${pctMatch[1]}% - ${offset}px)`
        } else {
          // 像素字符串：如 "500px" → 420
          const pxMatch = h.match(/^(\d+(?:\.\d+)?)px$/)
          if (pxMatch) {
            base.height = Math.max(100, Number(pxMatch[1]) - offset)
          }
        }
      }
    }
  }
  // FE-S10: 表格选中行追踪（供批量操作用）
  base.onSelectionChanged = onGridSelectionChanged

  // 选择模式：强制单选 + 行双击回填
  if (store.selectMode) {
    base.selection = { mode: 'single', showCheckBoxesMode: 'none' }
    base.onRowDblClick = onGridRowDblClick
  }

  // 内置右键复制功能：复制单元格内容 / 复制行数据
  if (props.node.type === 'dx-data-grid') {
    const userContextMenu = base.onContextMenuPreparing as
      | ((e: any) => void)
      | undefined
    base.onContextMenuPreparing = (e: any) => {
      if (typeof userContextMenu === 'function') {
        try {
          userContextMenu(e)
        } catch (err) {
          console.error('[内置右键菜单] 用户 contextMenuPreparing 错误:', err)
        }
      }
      const builtItems = buildCopyContextMenuItems(e)
      if (builtItems.length) {
        if (!Array.isArray(e.items)) e.items = []
        e.items.push(...builtItems)
      }
    }

  }

  return base
})

/** DataGrid 独立工具栏按钮配置 */
const gridToolbarItems = computed(() => {
  if (props.node.type !== 'dx-data-grid') return []
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
        isCustom?: boolean
        onClick?: string
        type?: 'default' | 'normal' | 'success' | 'danger' | 'warning'
        stylingMode?: 'contained' | 'outlined' | 'text'
        showIcon?: boolean
        bgColor?: string
        textColor?: string
        hoverColor?: string
        itemType?: 'button' | 'dropdown'
        children?: {
          name: string
          caption: string
          icon: string
          visible: boolean
          onClick?: string
          showIcon?: boolean
        }[]
        openPage?: {
          pageId?: string
          params?: Record<string, unknown>
          mode?: string
          title?: string
          bizMode?: 'form' | 'table' | 'mobile'
          passSelectedRow?: boolean
        }
      }[]
    | undefined
  if (!Array.isArray(items)) return []

  return items
    .filter((item) => item.visible !== false)
    .map((item) => {
      // 构建点击事件
      let onClick: (e: unknown) => void
      if (item.onClick && item.onClick.trim()) {
        // 用户自定义事件代码
        try {
          const userFn = createSandboxedFunction(
            ['e', 'api', 'grid'],
            `return (async () => { ${item.onClick} })()`
          )
          onClick = (e: unknown) => {
            const api = createRuntimeApi()
            try {
              const result = userFn(e, api, getGridInstance())
              if (result instanceof Promise) {
                result.catch((err) => {
                  console.error(`[工具栏按钮事件] ${item.name} 异步错误:`, err)
                })
              }
            } catch (err) {
              console.error(`[工具栏按钮事件] ${item.name} 运行时错误:`, err)
            }
          }
        } catch (err) {
          console.error(`[工具栏按钮事件] ${item.name} 代码语法错误:`, err)
          onClick = () => {}
        }
      } else {
        // 预设按钮行为
        onClick = () => {
          const grid = getGridInstance()
          if (!grid) return
          switch (item.name) {
            case 'add': {
              const openCfg = item.openPage
              const mc = props.node.props.modalConfig as { width?: number; height?: number; maximizable?: boolean; draggable?: boolean } | undefined
              if (openCfg?.pageId && designerStore.previewMode) {
                const targetPageId = openCfg.pageId === '__current__' ? designerStore.pageId : openCfg.pageId
                if (targetPageId) {
                  pageModal.openPage(
                    targetPageId,
                    { tableName: designerStore.tableName, ...(openCfg.params || {}) },
                    {
                      bizMode: openCfg.bizMode || 'form',
                      title: openCfg.title || t('新增'),
                      mode: (openCfg.mode as 'popup' | 'drawer') || 'popup',
                      width: mc?.width ?? 900, height: mc?.height ?? 600,
                      maximizable: mc?.maximizable ?? true, draggable: mc?.draggable ?? true,
                    },
                  ).then(() => { if (typeof grid.refresh === 'function') grid.refresh() }).catch(() => {})
                }
              } else if (designerStore.previewMode && designerStore.pageId) {
                pageModal.openPage(
                  designerStore.pageId,
                  { tableName: designerStore.tableName },
                  { bizMode: 'form', title: t('新增'), width: mc?.width ?? 900, height: mc?.height ?? 600, maximizable: mc?.maximizable ?? true, draggable: mc?.draggable ?? true },
                ).then(() => { if (typeof grid.refresh === 'function') grid.refresh() }).catch(() => {})
              } else if (typeof grid.addRow === 'function') {
                grid.addRow()
              }
              break
            }
            case 'delete': {
              if (typeof grid.getSelectedRowsData !== 'function') break
              const sel = grid.getSelectedRowsData()
              if (!sel || !sel.length) break
              const keyExpr = store.primaryKeyField || (props.node.props.keyExpr as string) || 'id'
              const key = sel[0][keyExpr]
              if (key === undefined || key === null) break
              const bizCode = designerStore.tableName
              if (!bizCode) break
              void (async () => {
                try {
                  await ElMessageBox.confirm(t('确认删除选中的记录？'), t('提示'), {
                    type: 'warning',
                  })
                } catch {
                  return
                }
                try {
                  await gatewayCall(bizCode, 'delete', { [keyExpr]: key })
                  ElMessage.success(t('删除成功'))
                  if (typeof grid.refresh === 'function') grid.refresh()
                } catch (err) {
                  ElMessage.error(err instanceof Error ? err.message : String(err))
                }
              })()
              break
            }
            case 'edit': {
              if (typeof grid.getSelectedRowsData !== 'function') break
              const sel = grid.getSelectedRowsData()
              if (!sel || !sel.length) break
              const keyExpr = store.primaryKeyField || (props.node.props.keyExpr as string) || 'id'
              const key = keyExpr ? sel[0][keyExpr] : sel[0]
              const openCfg = item.openPage
              const mc = props.node.props.modalConfig as { width?: number; height?: number; maximizable?: boolean; draggable?: boolean } | undefined
              const passRow = openCfg?.passSelectedRow !== false
              if (openCfg?.pageId && designerStore.previewMode) {
                const targetPageId = openCfg.pageId === '__current__' ? designerStore.pageId : openCfg.pageId
                if (targetPageId) {
                  const editParams: Record<string, unknown> = { tableName: designerStore.tableName, ...(openCfg.params || {}) }
                  if (passRow) {
                    editParams.rowData = sel[0]
                    if (sel.length > 1) editParams.rowDataList = sel
                  }
                  pageModal.openPage(
                    targetPageId,
                    editParams,
                    {
                      bizMode: openCfg.bizMode || 'form',
                      title: openCfg.title || t('修改'),
                      mode: (openCfg.mode as 'popup' | 'drawer') || 'popup',
                      width: mc?.width ?? 900, height: mc?.height ?? 600,
                      maximizable: mc?.maximizable ?? true, draggable: mc?.draggable ?? true,
                    },
                  ).then(() => { if (typeof grid.refresh === 'function') grid.refresh() }).catch(() => {})
                }
              } else if (designerStore.previewMode && designerStore.pageId) {
                pageModal.openPage(
                  designerStore.pageId,
                  { rowData: sel[0], tableName: designerStore.tableName, rowDataList: sel.length > 1 ? sel : undefined },
                  { bizMode: 'form', title: t('修改'), width: mc?.width ?? 900, height: mc?.height ?? 600, maximizable: mc?.maximizable ?? true, draggable: mc?.draggable ?? true },
                ).then(() => { if (typeof grid.refresh === 'function') grid.refresh() }).catch(() => {})
              } else if (typeof grid.getRowIndexByKey === 'function') {
                const idx = grid.getRowIndexByKey(key)
                if (
                  idx !== undefined &&
                  idx >= 0 &&
                  typeof grid.editRow === 'function'
                ) {
                  grid.editRow(idx)
                }
              }
              break
            }
            case 'export':
              if (typeof grid.exportToExcel === 'function') grid.exportToExcel()
              break
            case 'refresh':
              if (typeof grid.refresh === 'function') grid.refresh()
              break
            case 'save':
              if (typeof grid.saveEditData === 'function') grid.saveEditData()
              break
            case 'cancel':
              if (typeof grid.cancelEditData === 'function')
                grid.cancelEditData()
              break
          }
        }
      }
      // 如果配置了打开页面，包装点击事件（add/edit 预设按钮已在 switch 中处理 openPage，跳过避免重复打开）
      const openPageCfg = item.openPage
      const skipWrapper = (item.name === 'add' || item.name === 'edit') && !item.isCustom
      if (openPageCfg?.pageId && !skipWrapper) {
        const baseOnClick = onClick
        onClick = async (e: unknown) => {
          if (typeof baseOnClick === 'function') baseOnClick(e)
          try {
            const api = createRuntimeApi()
            const targetPageId = openPageCfg.pageId === '__current__' ? designerStore.pageId : openPageCfg.pageId
            if (!targetPageId) return
            await api.openPage(targetPageId, openPageCfg.params || {}, {
              mode: (openPageCfg.mode as 'popup' | 'drawer') || 'popup',
              title: openPageCfg.title,
              bizMode: openPageCfg.bizMode,
            })
          } catch (err) {
            console.error(`[工具栏按钮打开页面失败] ${openPageCfg.pageId}`, err)
          }
        }
      }
      // 下拉按钮类型：渲染为 dxDropDownButton
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
            type: item.type || 'normal',
            stylingMode: item.stylingMode || 'text',
            items: childItems,
            onItemClick: (e: { itemData?: { name?: string } }) => {
              const grid = getGridInstance()
              if (!grid) return
              const childName = e.itemData?.name || ''
              handleExportAction(childName, grid)
            },
            ...((item.bgColor || item.textColor || item.hoverColor)
              ? {
                  elementAttr: {
                    class: [
                      item.bgColor ? 'lc-btn-has-bg' : '',
                      item.textColor ? 'lc-btn-has-text' : '',
                      item.hoverColor ? 'lc-btn-has-hover' : '',
                    ].filter(Boolean).join(' '),
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
          onClick,
          type: item.type || 'normal',
          stylingMode: item.stylingMode || (item.type && item.type !== 'normal' ? 'contained' : 'text'),
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

/** 处理导出子按钮动作 */
function handleExportAction(actionName: string, grid: any): void {
  switch (actionName) {
    case 'export_selected': {
      const sel = typeof grid.getSelectedRowsData === 'function' ? grid.getSelectedRowsData() : []
      if (!sel || !sel.length) {
        ElMessage.warning(t('请先选择要导出的行'))
        return
      }
      try {
        const oldExport = grid.option('export')
        grid.option('export', { ...(oldExport || {}), selectedRows: true, enabled: true })
        grid.exportToExcel()
        grid.option('export', oldExport)
      } catch {
        if (typeof grid.exportToExcel === 'function') grid.exportToExcel()
      }
      break
    }
    case 'export_query':
      if (typeof grid.exportToExcel === 'function') grid.exportToExcel()
      break
    case 'export_template_selected': {
      const sel = typeof grid.getSelectedRowsData === 'function' ? grid.getSelectedRowsData() : []
      if (!sel || !sel.length) {
        ElMessage.warning(t('请先选择要导出的行'))
        return
      }
      try {
        const oldExport = grid.option('export')
        grid.option('export', { ...(oldExport || {}), selectedRows: true, enabled: true })
        grid.exportToExcel()
        grid.option('export', oldExport)
      } catch {
        if (typeof grid.exportToExcel === 'function') grid.exportToExcel()
      }
      break
    }
    case 'export_template_query':
      if (typeof grid.exportToExcel === 'function') grid.exportToExcel()
      break
    default:
      break
  }
}

/** 创建运行时 API 对象，供事件和工具栏按钮使用 */
function createRuntimeApi() {
  const api: any = {
    // ---- 值操作 ----
    getValue: (fieldName: string) => {
      const node = findNodeByFieldNameIn(store.schema, fieldName)
      if (!node) return undefined
      return node.props.value ?? node.props.text ?? node.props.selectedItem
    },
    setValue: (fieldName: string, value: unknown) => {
      const node = findNodeByFieldNameIn(store.schema, fieldName)
      if (node) {
        node.props.value = value
        store.schemaRevision += 1
      }
    },

    // ---- 属性操作 ----
    getProp: (fieldName: string, prop: string) => {
      const node = findNodeByFieldNameIn(store.schema, fieldName)
      if (!node) return undefined
      const keys = prop.split('.')
      let current: unknown = node.props
      for (const key of keys) {
        if (
          current === null ||
          typeof current !== 'object' ||
          Array.isArray(current)
        )
          return undefined
        current = (current as Record<string, unknown>)[key]
      }
      return current
    },
    setProp: (fieldName: string, prop: string, value: unknown) => {
      const node = findNodeByFieldNameIn(store.schema, fieldName)
      if (!node) return
      const keys = prop.split('.')
      let current: Record<string, unknown> = node.props
      for (let i = 0; i < keys.length - 1; i++) {
        if (typeof current[keys[i]] !== 'object' || current[keys[i]] === null) {
          current[keys[i]] = {}
        }
        current = current[keys[i]] as Record<string, unknown>
      }
      current[keys[keys.length - 1]] = value
      store.schemaRevision += 1
    },

    // ---- 显示/隐藏 ----
    getVisible: (fieldName: string) => {
      const node = findNodeByFieldNameIn(store.schema, fieldName)
      if (!node) return true
      return node.css?.display !== 'none'
    },
    setVisible: (fieldName: string, visible: boolean) => {
      const node = findNodeByFieldNameIn(store.schema, fieldName)
      if (!node) return
      if (!node.css) node.css = {}
      if (visible) {
        delete node.css.display
        if (!Object.keys(node.css).length) node.css = undefined
      } else {
        node.css.display = 'none'
      }
      store.schemaRevision += 1
    },

    // ---- 必填校验 ----
    getRequired: (fieldName: string) => {
      const node = findNodeByFieldNameIn(store.schema, fieldName)
      if (!node) return false
      const rules = node.props.validationRules
      if (!Array.isArray(rules)) return false
      return rules.some((r: Record<string, unknown>) => r.type === 'required')
    },
    setRequired: (fieldName: string, required: boolean) => {
      const node = findNodeByFieldNameIn(store.schema, fieldName)
      if (!node) return
      const rules = Array.isArray(node.props.validationRules)
        ? [...(node.props.validationRules as Record<string, unknown>[])]
        : []
      const idx = rules.findIndex((r) => r.type === 'required')
      if (required && idx === -1) {
        rules.push({ type: 'required', message: '此字段为必填项' })
      } else if (!required && idx >= 0) {
        rules.splice(idx, 1)
      }
      node.props.validationRules = rules.length ? rules : undefined
      store.schemaRevision += 1
    },

    // ---- 数据源 ----
    getDataSource: (fieldName: string) => {
      const node = findNodeByFieldNameIn(store.schema, fieldName)
      if (!node) return undefined
      return node.props.dataSource
    },
    setDataSource: (fieldName: string, data: unknown[]) => {
      const node = findNodeByFieldNameIn(store.schema, fieldName)
      if (!node) return
      node.props.dataSource = data
      store.schemaRevision += 1
    },

    // ---- CSS 样式 ----
    getStyle: (fieldName: string, cssKey: string) => {
      const node = findNodeByFieldNameIn(store.schema, fieldName)
      if (!node?.css) return undefined
      return (node.css as Record<string, string>)[cssKey]
    },
    setStyle: (fieldName: string, cssKey: string, value: string) => {
      const node = findNodeByFieldNameIn(store.schema, fieldName)
      if (!node) return
      if (!node.css) node.css = {}
      ;(node.css as Record<string, string>)[cssKey] = value
      store.schemaRevision += 1
    },

    // ---- 控件操作 ----
    getControlNode: (fieldName: string) => {
      return findNodeByFieldNameIn(store.schema, fieldName)
    },
    getAllControls: () => {
      return collectAllNodes(store.schema)
    },
    getAllFields: () => {
      return collectAllNodes(store.schema)
        .filter((n) => n.fieldName)
        .map((n) => n.fieldName as string)
    },

    // ---- 后台 API 请求（内部实现） ----
    _request: async (
      url: string,
      options: {
        method?: string
        headers?: Record<string, string>
        body?: string
        onSuccess?: (data: unknown) => void
        onError?: (err: {
          status: number
          message: string
          data: unknown
        }) => void
        fieldName?: string
      } = {}
    ) => {
      try {
        const result = await httpRequest(url, {
          method: options.method || 'GET',
          headers: options.headers,
          body: options.body,
        })
        if (!result.ok) {
          const errMsg =
            typeof result.data === 'string'
              ? result.data
              : JSON.stringify(result.data)
          const err = {
            status: result.status,
            message: `HTTP ${result.status}: ${errMsg}`,
            data: result.data,
          }
          if (options.onError) options.onError(err)
          else console.error(`[API 请求失败] ${url}`, err)
          return null
        }
        if (options.onSuccess) options.onSuccess(result.data)
        if (options.fieldName) api.setValue(options.fieldName, result.data)
        return result.data
      } catch (networkErr) {
        const err = {
          status: 0,
          message:
            networkErr instanceof Error
              ? networkErr.message
              : String(networkErr),
          data: null,
        }
        if (options.onError) options.onError(err)
        else console.error(`[API 网络错误] ${url}`, err)
        return null
      }
    },
    /** GET 请求 */
    get: (
      url: string,
      paramsOrOptions?:
        | Record<string, string>
        | {
            onSuccess?: (data: unknown) => void
            onError?: (err: {
              status: number
              message: string
              data: unknown
            }) => void
            fieldName?: string
            params?: Record<string, string>
          }
    ) => {
      let params: Record<string, string> | undefined
      let opts: Record<string, unknown> = {}
      if (paramsOrOptions) {
        if (
          typeof paramsOrOptions === 'object' &&
          ('onSuccess' in paramsOrOptions ||
            'onError' in paramsOrOptions ||
            'fieldName' in paramsOrOptions ||
            'params' in paramsOrOptions)
        ) {
          opts = paramsOrOptions
          params = (paramsOrOptions as { params?: Record<string, string> })
            .params
        } else {
          params = paramsOrOptions as Record<string, string>
        }
      }
      const qs = params
        ? '?' +
          Object.entries(params)
            .map(
              ([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`
            )
            .join('&')
        : ''
      return api._request(url + qs, { method: 'GET', ...opts })
    },
    /** POST 请求 */
    post: (
      url: string,
      data?: unknown,
      opts?: {
        onSuccess?: (data: unknown) => void
        onError?: (err: {
          status: number
          message: string
          data: unknown
        }) => void
        fieldName?: string
      }
    ) => {
      return api._request(url, {
        method: 'POST',
        body: data !== undefined ? JSON.stringify(data) : undefined,
        ...opts,
      })
    },
    /** 按 serviceId 调用 API（推荐方式，替代 api.get(url)/api.post(url)，URL 从注册表解析） */
    call: async (
      serviceId: string,
      opts?: {
        params?: Record<string, unknown>
        data?: unknown
        formData?: boolean
        headers?: Record<string, string>
        onSuccess?: (result: ApiCallResult) => void
        onError?: (err: {
          status: number
          message: string
          data: unknown
        }) => void
        fieldName?: string
      }
    ): Promise<ApiCallResult> => {
      try {
        const result = await apiCall(serviceId, {
          params: opts?.params,
          data: opts?.data,
          formData: opts?.formData,
          headers: opts?.headers,
        })
        if (!result.ok) {
          const errMsg =
            typeof result.data === 'string'
              ? result.data
              : JSON.stringify(result.data)
          const err = {
            status: result.status,
            message: `HTTP ${result.status}: ${errMsg}`,
            data: result.data,
          }
          if (opts?.onError) opts.onError(err)
          else console.error(`[API 治理] ${serviceId} 调用失败`, err)
          return result
        }
        if (opts?.onSuccess) opts.onSuccess(result)
        if (opts?.fieldName) api.setValue(opts.fieldName, result.data)
        return result
      } catch (err) {
        const e = err instanceof Error ? err : new Error(String(err))
        if (opts?.onError)
          opts.onError({ status: 0, message: e.message, data: null })
        else console.error(`[API 治理] ${serviceId} 异常`, err)
        return {
          ok: false,
          status: 0,
          data: null,
          list: [],
          payload: { data: [], totalCount: 0 },
        }
      }
    },

    // ---- 消息 ----
    showMessage: (text: string) => {
      alert(t(text))
    },
    confirm: (text: string) => {
      return confirm(t(text))
    },

    // ---- 翻译 ----
    t,

    // ---- 表单验证 ----
    validate: (fieldName: string) => {
      const node = findNodeByFieldNameIn(store.schema, fieldName)
      if (!node) return { isValid: false, errors: ['字段不存在'] }
      const rules = node.props.validationRules
      if (!Array.isArray(rules) || !rules.length)
        return { isValid: true, errors: [] }
      const value =
        node.props.value ?? node.props.text ?? node.props.selectedItem ?? ''
      const errors: string[] = []
      for (const rule of rules as ValidationRuleConfig[]) {
        let valid = true
        switch (rule.type) {
          case 'required':
            valid = !!value || value === 0 || value === false
            break
          case 'email':
            valid =
              !value || /^[\d\w._-]+@[\d\w._-]+\.[\w]+$/i.test(String(value))
            break
          case 'numeric':
            valid = !value || !isNaN(Number(value))
            break
          case 'pattern':
            valid =
              !value ||
              !rule.pattern ||
              new RegExp(rule.pattern).test(String(value))
            break
          case 'range': {
            if (!value) break
            const num = Number(value)
            if (rule.min !== undefined && num < Number(rule.min)) valid = false
            if (rule.max !== undefined && num > Number(rule.max)) valid = false
            break
          }
          case 'stringLength': {
            const len = String(value || '').length
            if (rule.min !== undefined && len < Number(rule.min)) valid = false
            if (rule.max !== undefined && len > Number(rule.max)) valid = false
            break
          }
          case 'compare': {
            if (!rule.comparisonTarget) break
            const targetNode = findNodeByFieldNameIn(
              store.schema,
              rule.comparisonTarget
            )
            if (!targetNode) break
            const tv = targetNode.props.value
            const ct = rule.comparisonType || '=='
            if (ct === '==') valid = value === tv
            else if (ct === '!=') valid = value !== tv
            else if (ct === '>') valid = Number(value) > Number(tv)
            else if (ct === '>=') valid = Number(value) >= Number(tv)
            else if (ct === '<') valid = Number(value) < Number(tv)
            else if (ct === '<=') valid = Number(value) <= Number(tv)
            break
          }
          case 'custom': {
            if (!rule.validationCallback) break
            try {
              const fn = createSandboxedFunction(
                ['value'],
                rule.validationCallback
              )
              valid = !!fn(value)
            } catch {
              valid = true
            }
            break
          }
        }
        if (!valid) errors.push(rule.message || `${rule.type} 验证失败`)
      }
      const reg = getValidator(fieldName)
      if (reg) {
        try {
          reg.validate()
        } catch {
          /* ignore */
        }
      }
      return { isValid: errors.length === 0, errors }
    },
    validateAll: () => {
      const nodes = collectAllNodes(store.schema).filter((n) => n.fieldName)
      let allValid = true
      const errors: Record<string, string[]> = {}
      for (const node of nodes) {
        const result = api.validate(node.fieldName!)
        if (!result.isValid) {
          allValid = false
          errors[node.fieldName!] = result.errors
        }
      }
      return { isValid: allValid, errors }
    },

    // ---- 页面弹窗 ----
    /**
     * 打开另一个平台配置的页面作为弹窗，返回 Promise。
     * 子页面 closePage 时 resolve，父页面 await 拿到返回值。
     * @param pageId 要打开的页面 ID
     * @param params 传给子页面的参数
     * @param options 弹窗选项 { mode, title, width, height }
     */
    openPage: (
      pageId: string,
      params?: Record<string, unknown>,
      options?: {
        mode?: 'popup' | 'drawer'
        title?: string
        width?: number | string
        height?: number | string
        bizMode?: 'form' | 'table' | 'mobile'
        maximizable?: boolean
        draggable?: boolean
      }
    ) => {
      return pageModal.openPage(pageId, params || {}, options || {})
    },
    /** 关闭当前弹窗并返回结果给父页面 */
    closePage: (result?: unknown) => {
      pageModal.closePage(result)
    },
    /** 获取当前弹窗传入的指定参数 */
    getParam: (key: string) => {
      return pageModal.getParam(key)
    },
    /** 获取当前弹窗传入的全部参数 */
    get params(): Record<string, unknown> {
      return pageModal.getParams()
    },
  }
  return api
}

/** 高级查询条件 */
const advSearchConditions = ref<SearchCondition[]>([])
const isContainer = !!cfg?.isContainer

/** 刷新 DataGrid 数据源 */
function reloadGrid() {
  const inst = gridInstanceRef.value
  if (!inst) return
  // 获取真实实例
  let realInst: any = inst
  if (typeof realInst.instance === 'function') {
    try {
      realInst = realInst.instance()
    } catch {
      /* keep original */
    }
  } else if (realInst?.$refs?.dataGrid) {
    realInst = realInst.$refs.dataGrid
  }
  // 调用刷新方法
  if (typeof realInst.refresh === 'function') {
    realInst.refresh()
  } else if (typeof realInst.reload === 'function') {
    realInst.reload()
  } else if (typeof realInst.reloadData === 'function') {
    realInst.reloadData()
  }
}
const OVERLAY_TYPES = new Set(['dx-popup', 'dx-toast', 'dx-load-panel'])
const overlayProps = computed(() =>
  OVERLAY_TYPES.has(props.node.type)
    ? { container: `#renderer-node-${props.node.id}` }
    : {}
)
const boundProps = computed<Record<string, unknown>>(() => {
  void store.schemaRevision
  const base: Record<string, unknown> = {
    ...resolveNodeProps(props.node.type, props.node.props, props.node),
    ...overlayProps.value,
  }
  // G3 权限驱动：字段只读时设 disabled（permissions 未加载时 nodeFieldReadonly=false，不影响现有）
  if (nodeFieldReadonly.value) {
    base.disabled = true
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
  // 为 el-splitter-panel 添加拖拽手柄所需的 props
  if (props.node.type === 'el-splitter-panel' && props.parentId) {
    const parentResult = findNode(store.schema, props.parentId)
    if (parentResult && parentResult.node.type === 'el-splitter') {
      base.showHandle = true
      base.layout = parentResult.node.props.layout
      const totalPanels = parentResult.node.children.length
      base.isLastPanel = (props.index ?? 0) >= totalPanels - 1
      base.panelIndex = props.index ?? 0
      base.parentNodeId = props.parentId
    }
  }
  // 可验证控件：值变化时同步回 node.props.value，确保 validate API 读取到最新值
  if (VALIDATABLE_TYPES.has(props.node.type)) {
    const userHandler = base.onValueChanged
    base.onValueChanged = (e: unknown) => {
      const val = (e != null && typeof e === 'object' && 'value' in (e as object)) ? (e as any).value : e
      props.node.props.value = val
      store.schemaRevision += 1
      if (typeof userHandler === 'function') userHandler(e)
    }
  }

  // 绑定事件处理函数：将事件名转为 Vue 的 on* 格式，注入运行时 API
  if (props.node.events) {
    for (const [eventName, code] of Object.entries(props.node.events)) {
      const handlerName =
        'on' + eventName.charAt(0).toUpperCase() + eventName.slice(1)
      try {
        const userFn = createSandboxedFunction(
          ['e', 'api'],
          `return (async () => { ${code} })()`
        )
        base[handlerName] = (e: unknown) => {
          const api = createRuntimeApi()
          try {
            const result = userFn(e, api)
            if (result instanceof Promise) {
              result.catch((err) => {
                console.error(`[事件执行] ${eventName} 异步错误:`, err)
              })
            }
          } catch (err) {
            console.error(`[事件执行] ${eventName} 运行时错误:`, err)
          }
        }
      } catch (err) {
        console.error(`[事件绑定] ${eventName} 代码语法错误:`, err)
      }
    }
  }
  // dx-button：如果配置了 openPage，自动绑定点击事件打开子页面
  if (props.node.type === 'dx-button') {
    const openPageCfg = base.openPage as
      | {
          pageId?: string
          params?: Record<string, unknown>
          mode?: string
          title?: string
          bizMode?: 'form' | 'table' | 'mobile'
        }
      | undefined
    // 清理自定义属性，不传给 DevExtreme 组件
    delete base.openPage
    // showIcon 控制
    const showIcon = base.showIcon
    delete base.showIcon
    if (showIcon === false) delete base.icon
    // 按钮配色
    const bgColor = base.bgColor as string | undefined
    const textColor = base.textColor as string | undefined
    const hoverColor = base.hoverColor as string | undefined
    delete base.bgColor
    delete base.textColor
    delete base.hoverColor
    if (bgColor || textColor || hoverColor) {
      base.elementAttr = {
        ...(base.elementAttr || {}),
        class: [
          (base.elementAttr as any)?.class || '',
          bgColor ? 'lc-btn-has-bg' : '',
          textColor ? 'lc-btn-has-text' : '',
          hoverColor ? 'lc-btn-has-hover' : '',
        ].filter(Boolean).join(' '),
        style: `--lc-btn-bg:${bgColor || ''};--lc-btn-text:${textColor || ''};--lc-btn-hover:${hoverColor || ''};`,
      }
    }
    if (openPageCfg?.pageId) {
      const userClick = base.onClick
      base.onClick = async (e: unknown) => {
        // 先执行用户自定义点击代码
        if (typeof userClick === 'function') userClick(e)
        try {
          const api = createRuntimeApi()
          const targetPageId = openPageCfg.pageId === '__current__' ? designerStore.pageId : openPageCfg.pageId
          if (!targetPageId) return
          await api.openPage(targetPageId, openPageCfg.params || {}, {
            mode: (openPageCfg.mode as 'popup' | 'drawer') || 'popup',
            title: openPageCfg.title,
            bizMode: openPageCfg.bizMode,
          })
        } catch (err) {
          console.error(`[打开页面失败] ${openPageCfg.pageId}`, err)
        }
      }
    }
  }
  // el-tabs 需要传递 children 数据用于渲染标签头
  if (props.node.type === 'el-tabs') {
    base.children = props.node.children
  }
  // dx-data-grid：根据数据源类型决定使用静态数据还是远程加载
  if (props.node.type === 'dx-data-grid') {
    // 主键字段始终跟随页面 primaryKeyField 配置
    base.keyExpr = store.primaryKeyField || 'id'
    const dataSourceType = (base.dataSourceType as string) || 'static'

    // 清理自定义配置属性，不传给 DevExtreme 组件
    delete base.dataSourceType
    delete base.firstLoad
    delete base.remoteUrl
    delete base.remoteMethod
    delete base.remoteBody

    if (dataSourceType === 'remote') {
      const serviceId = props.node.props.serviceId as string | undefined
      const resolved = serviceId ? resolveApi(serviceId) : null
      const url = resolved?.url || String(props.node.props.remoteUrl || '')
      const method =
        resolved?.method || (props.node.props.remoteMethod as string) || 'GET'
      const remoteBody = props.node.props.remoteBody as string | undefined
      const firstLoad = props.node.props.firstLoad === true

      // url 为空时走网关
      const gwTableName = store.tableName || designerStore.tableName
      const useGateway = !url && !!gwTableName
      const gatewayBizCode = gwTableName

      // 首次加载标记：firstLoad=true 时第一次 load 返回空数据
      let _firstLoadDone = false

      // 从事件配置中获取 storeUpdate/storeInsert/storeRemove 用户自定义函数
      const events = props.node.events || {}
      const buildStoreFn = (eventName: string, argNames: string[]) => {
        const code = events[eventName]
        if (!code) return undefined
        try {
          // 用 Function 构造器创建函数，参数为事件参数名 + api
          const fn = createSandboxedFunction(
            [...argNames, 'api'],
            `return (async () => { ${code} })()`
          )
          return (...args: unknown[]) => {
            // 构造运行时 API（和事件处理器的 api 一致）
            const api = {
              getValue: (fieldName: string) => {
                const node = findNodeByFieldNameIn(store.schema, fieldName)
                if (!node) return undefined
                return (
                  node.props.value ?? node.props.text ?? node.props.selectedItem
                )
              },
              setValue: (fieldName: string, value: unknown) => {
                const node = findNodeByFieldNameIn(store.schema, fieldName)
                if (node) {
                  node.props.value = value
                  store.schemaRevision += 1
                }
              },
              getProp: (fieldName: string, prop: string) => {
                const node = findNodeByFieldNameIn(store.schema, fieldName)
                if (!node) return undefined
                const keys = prop.split('.')
                let current: unknown = node.props
                for (const key of keys) {
                  if (
                    current === null ||
                    typeof current !== 'object' ||
                    Array.isArray(current)
                  )
                    return undefined
                  current = (current as Record<string, unknown>)[key]
                }
                return current
              },
              setProp: (fieldName: string, prop: string, value: unknown) => {
                const node = findNodeByFieldNameIn(store.schema, fieldName)
                if (!node) return
                const keys = prop.split('.')
                let current: Record<string, unknown> = node.props
                for (let i = 0; i < keys.length - 1; i++) {
                  if (
                    typeof current[keys[i]] !== 'object' ||
                    current[keys[i]] === null
                  ) {
                    current[keys[i]] = {}
                  }
                  current = current[keys[i]] as Record<string, unknown>
                }
                current[keys[keys.length - 1]] = value
                store.schemaRevision += 1
              },
            }
            return fn(...args, api)
          }
        } catch {
          return undefined
        }
      }
      const storeUpdateFn = buildStoreFn('storeUpdate', ['key', 'values'])
      const storeInsertFn = buildStoreFn('storeInsert', ['values'])
      const storeRemoveFn = buildStoreFn('storeRemove', ['key'])

      /** DevExtreme 操作符转后端识别的符号 */
      function mapOperator(op: string): string {
        switch (op) {
          case 'contains':
            return 'like'
          case 'notcontains':
            return 'not like'
          case 'startswith':
            return 'like'
          case 'endswith':
            return 'like'
          case '=':
            return '='
          case '<>':
            return '<>'
          case '>':
            return '>'
          case '>=':
            return '>='
          case '<':
            return '<'
          case '<=':
            return '<='
          default:
            return op
        }
      }

      /** 获取列的表别名，默认 T */
      function getColumnTableAlias(_column: string): string {
        return 'T'
      }

      /** 将 DevExtreme filter 数组转换为 filterData 格式
       *  格式：[{ Symbol: "and" }, { Id, Symbol, Val, TableAlias }, { Symbol: "and" }, ...]
       */
      function convertFilter(
        dxFilter: unknown
      ): { Symbol: string; Id?: string; Val?: unknown; TableAlias?: string }[] {
        const result: {
          Symbol: string
          Id?: string
          Val?: unknown
          TableAlias?: string
        }[] = []
        if (!Array.isArray(dxFilter)) return result

        // 判断是否为多条件组合（包含逻辑运算符，如 ["cond1", "and", "cond2"]）
        const hasLogic = dxFilter.some(
          (item) =>
            typeof item === 'string' &&
            (item === 'and' || item === 'or' || item === '!')
        )

        if (
          !hasLogic &&
          dxFilter.length >= 2 &&
          typeof dxFilter[0] === 'string'
        ) {
          // 单条件：["field", "op", value]
          const field = dxFilter[0]
          const op = dxFilter.length >= 3 ? String(dxFilter[1]) : '='
          const val = dxFilter.length >= 3 ? dxFilter[2] : dxFilter[1]
          if (val !== '' && val !== null && val !== undefined) {
            // 格式：先连接符，再条件项
            result.push({ Symbol: 'and' })
            result.push({
              Id: field,
              Symbol: mapOperator(op),
              Val: val,
              TableAlias: getColumnTableAlias(field),
            })
          }
        } else {
          // 多条件组合：遍历每个条件，前面加逻辑运算符
          for (let i = 0; i < dxFilter.length; i++) {
            const item = dxFilter[i]
            if (Array.isArray(item)) {
              const inner = convertFilter(item)
              if (inner.length > 0) {
                // 找当前条件前的逻辑运算符
                let logic = 'and'
                if (
                  i > 0 &&
                  typeof dxFilter[i - 1] === 'string' &&
                  (dxFilter[i - 1] === 'and' || dxFilter[i - 1] === 'or')
                ) {
                  logic = dxFilter[i - 1]
                }
                // 第一个条件组前也加连接符（与后端格式一致）
                if (result.length === 0) {
                  result.push({ Symbol: logic })
                } else {
                  result.push({ Symbol: logic })
                }
                // 添加条件项（去掉 inner 开头的连接符，因为外层已经加了）
                for (let j = 1; j < inner.length; j++) {
                  result.push(inner[j])
                }
              }
            }
          }
        }
        return result
      }

      /** 将 DevExtreme loadOptions 转换为后端请求参数 */
      function buildLoadParams(loadOptions: {
        skip?: number
        take?: number
        sort?: unknown
        filter?: unknown
        searchValue?: unknown
        searchExpr?: unknown
        searchOperation?: unknown
        totalSummary?: unknown
        groupSummary?: unknown
      }): Record<string, unknown> {
        const params: Record<string, unknown> = {}
        // 分页
        if (loadOptions.skip !== undefined) params.skip = loadOptions.skip
        if (loadOptions.take !== undefined) params.take = loadOptions.take
        // 排序：JSON.stringify 第一个排序项，添加 TableAlias
        if (
          loadOptions.sort &&
          Array.isArray(loadOptions.sort) &&
          loadOptions.sort.length > 0
        ) {
          const sortItem = {
            ...(loadOptions.sort[0] as Record<string, unknown>),
          }
          if (sortItem.selector) {
            sortItem.TableAlias = getColumnTableAlias(String(sortItem.selector))
          }
          params.sort = JSON.stringify(sortItem)
        }
        // 筛选：转换为 filterData 格式后 JSON.stringify
        let allFilterData: {
          Symbol: string
          Id?: string
          Val?: unknown
          TableAlias?: string
        }[] = []
        if (loadOptions.filter) {
          allFilterData = convertFilter(loadOptions.filter)
        }
        // 合并高级查询条件（所有条件之间默认并且，第一个条件前也加 Symbol:and）
        if (advSearchConditions.value.length > 0) {
          for (const cond of advSearchConditions.value) {
            if (cond.field && cond.operator && cond.value !== '') {
              if (cond.operator === 'between' && cond.value2 !== '') {
                // between 拆分为两个条件：>= value1 AND <= value2
                allFilterData.push({ Symbol: 'and' })
                allFilterData.push({
                  Id: cond.field,
                  Symbol: '>=',
                  Val: cond.value,
                  TableAlias: getColumnTableAlias(cond.field),
                })
                allFilterData.push({ Symbol: 'and' })
                allFilterData.push({
                  Id: cond.field,
                  Symbol: '<=',
                  Val: cond.value2,
                  TableAlias: getColumnTableAlias(cond.field),
                })
              } else {
                // 每个条件前都加 Symbol:and（包括第一个条件，无论前面是否已有行筛选条件）
                allFilterData.push({ Symbol: 'and' })
                allFilterData.push({
                  Id: cond.field,
                  Symbol: mapOperator(cond.operator),
                  Val: cond.value,
                  TableAlias: getColumnTableAlias(cond.field),
                })
              }
            }
          }
        }
        if (allFilterData.length > 0) {
          params.filterData = JSON.stringify(allFilterData)
        }
        // 搜索
        if (
          loadOptions.searchValue !== undefined &&
          loadOptions.searchValue !== ''
        )
          params.searchValue = loadOptions.searchValue
        // 汇总：只保留 selector 字段
        if (
          loadOptions.totalSummary &&
          Array.isArray(loadOptions.totalSummary) &&
          loadOptions.totalSummary.length > 0
        ) {
          const summary = (
            loadOptions.totalSummary as Record<string, unknown>[]
          ).map((item) => ({
            selector: item.selector,
          }))
          params.totalSummary = JSON.stringify(summary)
        }
        return params
      }

      base.dataSource = new CustomStore({
        key: store.primaryKeyField || (props.node.props.keyExpr as string) || 'id',
        load: async (loadOptions: {
          skip?: number
          take?: number
          sort?: unknown
          filter?: unknown
          searchValue?: unknown
          searchExpr?: unknown
          searchOperation?: unknown
          totalSummary?: unknown
          groupSummary?: unknown
        }) => {
          // 首次不加载逻辑：firstLoad=true 时第一次 load 返回空数据
          if (firstLoad && !_firstLoadDone) {
            _firstLoadDone = true
            return { data: [], totalCount: 0 }
          }

          try {
            const dxParams = buildLoadParams(loadOptions)
            let result

            if (useGateway) {
              const gwPayload: Record<string, unknown> = {}
              if (loadOptions.skip != null && loadOptions.take != null) {
                gwPayload.page = Math.floor(loadOptions.skip / loadOptions.take) + 1
                gwPayload.pageSize = loadOptions.take
              }
              // 复用 buildLoadParams：行筛选 + 高级查询条件统一转成后端 filterData 格式
              if (dxParams.filterData) {
                gwPayload.filterData = dxParams.filterData
              }

              result = await httpRequest('/api/lowcode/gateway', {
                method: 'POST',
                body: JSON.stringify({ bizCode: gatewayBizCode, action: 'query', payload: gwPayload }),
              })
            } else if (method === 'POST') {
              // 使用 multipart/form-data：通过 FormData 发送，浏览器自动设置 Content-Type 含 boundary
              const formData = new FormData()
              // 合并用户自定义 body 参数
              let bodyObj: Record<string, unknown> = {}
              if (remoteBody) {
                try {
                  bodyObj = JSON.parse(remoteBody)
                } catch {
                  /* ignore */
                }
              }
              const allParams = { ...bodyObj, ...dxParams }
              for (const [k, v] of Object.entries(allParams)) {
                if (v !== undefined && v !== null) {
                  formData.append(
                    k,
                    typeof v === 'object' ? JSON.stringify(v) : String(v)
                  )
                }
              }
              result = await httpRequest(url, {
                method: 'POST',
                body: formData,
                jsonContentType: false,
              })
            } else {
              // GET：参数拼到 URL
              const qs = Object.entries(dxParams)
                .filter(([, v]) => v !== undefined && v !== null)
                .map(
                  ([k, v]) =>
                    `${encodeURIComponent(k)}=${encodeURIComponent(
                      typeof v === 'object' ? JSON.stringify(v) : String(v)
                    )}`
                )
                .join('&')
              const fullUrl =
                url + (qs ? (url.includes('?') ? '&' : '?') + qs : '')
              result = await httpRequest(fullUrl, {
                method: 'GET',
                jsonContentType: false,
              })
            }

            if (!result.ok) throw new Error(`HTTP ${result.status}`)
            const payload = extractPayload(result.data)
            // 主键字段大小写兼容：后端 mapColumnNames 返回的列名格式可能与 keyExpr 不一致
            const _keyExpr = store.primaryKeyField || (props.node.props.keyExpr as string) || 'id'
            const _rows = (payload.data || []) as Record<string, unknown>[]
            if (
              _keyExpr !== 'id' &&
              _rows.length > 0 &&
              !(_keyExpr in _rows[0])
            ) {
              const variants = [
                _keyExpr.toLowerCase(),
                _keyExpr.toUpperCase(),
                _keyExpr.replace(/[A-Z]/g, (m) => '_' + m.toLowerCase()),
                _keyExpr.replace(/_([a-z])/g, (_, c) => c.toUpperCase()),
              ]
              const actualKey = variants.find(
                (v) => v !== _keyExpr && v in _rows[0]
              )
              if (actualKey) {
                for (const row of _rows) {
                  row[_keyExpr] = row[actualKey]
                }
              }
            }
            return { data: payload.data, totalCount: payload.totalCount }
          } catch (err) {
            console.error(`[远程数据源加载失败] ${url}`, err)
            throw err
          }
        },
        // update/insert/remove：如果用户配置了事件则调用，否则空实现避免 E4011
        update: async (key: unknown, values: Record<string, unknown>) => {
          if (storeUpdateFn) return await storeUpdateFn(key, values)
        },
        insert: async (values: Record<string, unknown>) => {
          if (storeInsertFn) return await storeInsertFn(values)
        },
        remove: async (key: unknown) => {
          if (storeRemoveFn) return await storeRemoveFn(key)
        },
      })
      // 开启远程操作：筛选、排序、分页都走后端
      if (base.remoteOperations === undefined) {
        base.remoteOperations = true
      }
      if (!base.pager) {
        base.pager = {
          showPageSizeSelector: true,
          allowedPageSizes: [10, 20, 50, 100],
          showInfo: true,
          showNavigationButtons: true,
        }
      }
      if (base.paging === undefined) {
        base.paging = { pageSize: 10 }
      }
    }
  }
  // dx-data-grid: 捕获实例，供 editCellTemplate 中 cellValue 调用
  if (props.node.type === 'dx-data-grid') {
    base.onInitialized = (e: { component?: unknown }) => {
      gridInstanceRef.value = e.component
    }
  }
  // dx-data-grid：删除 toolbar 属性（使用独立 DxToolbar 组件渲染，不依赖表格自带工具栏）
  if (props.node.type === 'dx-data-grid') {
    if (base.toolbar) delete base.toolbar
  }
  // dx-data-grid 列处理：为 selectBox 列添加 lookup，为 dropDownGrid 列添加 editCellTemplate
  if (props.node.type === 'dx-data-grid' && Array.isArray(base.columns)) {
    // 预处理：表单中下拉框字段自动注入 selectBox + lookup，使行筛选/高级查询为下拉框
    const selectBoxMap = formSelectBoxMap.value
    if (selectBoxMap.size) {
      base.columns = (base.columns as Record<string, unknown>[]).map((col) => {
        const et = col.editorType as string
        const df = col.dataField as string
        if (et !== 'selectBox' && typeof df === 'string' && !df.includes('.') && !col.lookup) {
          const formItems = selectBoxMap.get(df.toUpperCase())
          if (formItems) {
            return {
              ...col,
              editorType: 'selectBox',
              lookup: {
                dataSource: JSON.parse(JSON.stringify(formItems)),
                valueExpr: 'value',
                displayExpr: 'text',
                dataSourceType: 'static',
              },
            }
          }
        }
        return col
      })
    }
    base.columns = (base.columns as Record<string, unknown>[]).map((col) => {
      const editorType = col.editorType as string
      // 清理自定义属性，不传给 DevExtreme
      const {
        editorType: _et,
        dropDownGrid: _ddg,
        visible: _vis,
        writeBack: _wb,
        allowEditing: _ae,
        baseDataRef: _bdr,
        ...cleanCol
      } = col
      void _et
      void _ddg
      void _vis
      void _wb
      void _bdr
      // allowEditing: 只传 false（禁用编辑），true/undefined 不传（继承 editing.allowUpdating）
      if (_ae === false) {
        cleanCol.allowEditing = false
      }
      // visible=false 的列设置 visible 属性
      if (col.visible === false) {
        cleanCol.visible = false
      }
      // 确保列有 dataType，否则 filterRow、lookup 等功能可能异常
      if (!cleanCol.dataType) cleanCol.dataType = 'string'
      // 默认日期格式：datetime → yyyy-MM-dd HH:mm:ss，date → yyyy-MM-dd
      if (!cleanCol.format) {
        if (cleanCol.dataType === 'datetime') {
          cleanCol.format = 'yyyy-MM-dd HH:mm:ss'
        } else if (cleanCol.dataType === 'date') {
          cleanCol.format = 'yyyy-MM-dd'
        }
      }
      // 确保列允许筛选（行筛选需要）
      if (cleanCol.allowFiltering === undefined) cleanCol.allowFiltering = true

      // 带点号的 dataField（如 ORG_KEY.ORG_NAME）：后端用扁平 key 回填，
      // DevExtreme 会把含点号的 dataField 当嵌套路径解析导致取不到值。
      // 方案：将 dataField 点号替换为 __ 避免嵌套解析，用 calculateDisplayValue 从扁平 key 取值
      if (typeof cleanCol.dataField === 'string' && cleanCol.dataField.includes('.')) {
        const _flatKey = cleanCol.dataField
        cleanCol.dataField = cleanCol.dataField.replace(/\./g, '__')
        cleanCol.calculateDisplayValue = (rowData: any) => rowData?.[_flatKey]
        // 关联显示列后端无此字段，禁用筛选/排序/编辑避免发送无效参数
        cleanCol.allowFiltering = false
        cleanCol.allowSorting = false
        cleanCol.allowEditing = false
      }


      if (editorType === 'selectBox' && col.lookup) {
        const lookup = col.lookup as {
          dataSource: unknown[]
          valueExpr: string
          displayExpr: string
          dataSourceType?: string
          serviceId?: string
          remoteUrl?: string
          method?: string
        }
        if (
          lookup.dataSourceType === 'remote' &&
          (lookup.remoteUrl || lookup.serviceId)
        ) {
          // 远程加载下拉数据（优先 serviceId）
          const lookupResolved = lookup.serviceId
            ? resolveApi(lookup.serviceId)
            : null
          const remoteUrl = lookupResolved?.url || lookup.remoteUrl || ''
          const remoteMethod = lookupResolved?.method || lookup.method || 'GET'
          const remoteStore = new CustomStore({
            key: lookup.valueExpr,
            load: async () => {
              try {
                const result = await httpRequest(remoteUrl, {
                  method: remoteMethod,
                })
                return extractList(result.data)
              } catch {
                return []
              }
            },
          })
          cleanCol.lookup = {
            valueExpr: lookup.valueExpr,
            displayExpr: lookup.displayExpr,
            dataSource: remoteStore,
          }
          cleanCol.filterRowEditorOptions = {
            dataSource: remoteStore,
            valueExpr: lookup.valueExpr,
            displayExpr: lookup.displayExpr,
            showClearButton: true,
          }
        } else {
          // 转为普通数组，避免 Vue 响应式 Proxy 导致 DevExtreme 无法识别
          const plainDataSource = Array.isArray(lookup.dataSource)
            ? JSON.parse(JSON.stringify(lookup.dataSource))
            : []
          cleanCol.lookup = {
            valueExpr: lookup.valueExpr,
            displayExpr: lookup.displayExpr,
            dataSource: plainDataSource,
          }
          cleanCol.filterRowEditorOptions = {
            dataSource: plainDataSource,
            valueExpr: lookup.valueExpr,
            displayExpr: lookup.displayExpr,
            showClearButton: true,
          }
        }
      }
      // dropDownGrid: 必须删除 lookup，否则 DevExtreme 会用内置 lookup 编辑器覆盖 editCellTemplate
      if (editorType === 'dropDownGrid') {
        delete cleanCol.lookup
      }

      if (editorType === 'dropDownGrid' && col.dropDownGrid) {
        const ddg = col.dropDownGrid as Record<string, unknown>
        const valueExpr = String(ddg.valueExpr || 'id')
        const displayExpr = String(ddg.displayExpr || 'name')
        const ddgAllColumns = ddg.columns as
          | {
              dataField: string
              caption: string
              width?: number
              visible?: boolean
              writeBack?: boolean
            }[]
          | undefined
        // 显示用：过滤掉 visible=false 的列
        const ddgColumns = ddgAllColumns?.filter((c) => c.visible !== false)
        const inheritParent = ddg.inheritParent === true
        const ddgServiceId = String(ddg.serviceId || '')
        const ddgResolved = ddgServiceId ? resolveApi(ddgServiceId) : null
        const remoteUrl = ddgResolved?.url || String(ddg.remoteUrl || '')
        const method = ddgResolved?.method || String(ddg.method || 'POST')
        const popupWidth = Number(ddg.popupWidth) || 600

        // 创建远程数据源（只创建一次，避免每次 render 都新建）
        const gridDataSource = new CustomStore({
          key: valueExpr,
          // 提供空的 update/insert/remove 避免 E4011 错误
          update: async () => {},
          insert: async () => {},
          remove: async () => {},
          load: async (loadOptions: any) => {
            let finalUrl: string
            let finalMethod: string
            if (inheritParent) {
              const parentServiceId = props.node.props.serviceId as
                | string
                | undefined
              const parentResolved = parentServiceId
                ? resolveApi(parentServiceId)
                : null
              finalUrl =
                parentResolved?.url || String(props.node.props.remoteUrl || '')
              finalMethod =
                parentResolved?.method ||
                String(props.node.props.remoteMethod || 'POST')
            } else {
              finalUrl = remoteUrl
              finalMethod = method
            }
            const params: Record<string, unknown> = {}
            if (loadOptions.skip !== undefined) params.skip = loadOptions.skip
            if (loadOptions.take !== undefined) params.take = loadOptions.take
            if (loadOptions.skip != null && loadOptions.take != null) {
              params.page = Math.floor(loadOptions.skip / loadOptions.take) + 1
              params.pageSize = loadOptions.take
            }
            let result
            if (finalMethod === 'POST') {
              const formData = new FormData()
              for (const [k, v] of Object.entries(params)) {
                if (v !== undefined && v !== null) formData.append(k, String(v))
              }
              result = await httpRequest(finalUrl, {
                method: 'POST',
                body: formData,
                jsonContentType: false,
              })
            } else {
              const qs = Object.entries(params)
                .map(
                  ([k, v]) =>
                    `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`
                )
                .join('&')
              result = await httpRequest(finalUrl + (qs ? '?' + qs : ''), {
                method: 'GET',
                jsonContentType: false,
              })
            }
            const data = result.data
            // 处理 { res, data: [...], totalCount: N } 格式
            let arr: unknown[] = []
            let tc = 0
            if (Array.isArray(data)) {
              arr = data
              tc = data.length
            } else if (data && typeof data === 'object') {
              const obj = data as Record<string, unknown>
              if (obj.res === true || obj.res === false) {
                // 后端标准格式 { res, data, totalCount }
                if (Array.isArray(obj.data)) {
                  arr = obj.data
                  tc = Number(obj.totalCount ?? obj.data.length)
                } else if (obj.data && typeof obj.data === 'object') {
                  const inner = obj.data as Record<string, unknown>
                  if (Array.isArray(inner.data)) {
                    arr = inner.data
                    tc = Number(inner.totalCount ?? inner.data.length)
                  } else if (Array.isArray(inner.rows)) {
                    arr = inner.rows
                    tc = Number(inner.totalCount ?? inner.rows.length)
                  } else if (Array.isArray(inner.list)) {
                    arr = inner.list
                    tc = Number(inner.totalCount ?? inner.list.length)
                  } else if (Array.isArray(inner.items)) {
                    arr = inner.items
                    tc = Number(inner.totalCount ?? inner.items.length)
                  }
                }
              } else {
                if (Array.isArray(obj.data)) {
                  arr = obj.data
                  tc = Number(obj.totalCount ?? obj.data.length)
                } else if (Array.isArray(obj.items)) {
                  arr = obj.items
                  tc = Number(obj.totalCount ?? obj.items.length)
                } else if (Array.isArray(obj.rows)) {
                  arr = obj.rows
                  tc = Number(obj.totalCount ?? obj.rows.length)
                } else if (Array.isArray(obj.list)) {
                  arr = obj.list
                  tc = Number(obj.totalCount ?? obj.list.length)
                }
              }
            }
            return { data: arr, totalCount: tc }
          },
        })

        // 编辑单元格模板：用 Vue createApp 渲染 DropDownBox + DataGrid
        // DevExtreme Vue 模板签名: (cellElement, cellInfo)
        // DataGrid 实例通过 onInitialized 捕获到 gridInstanceRef
        const colDataField = String(col.dataField || '')
        cleanCol.editCellTemplate = (
          cellElement: HTMLElement,
          cellInfo: any
        ) => {
          // 获取 DataGrid 实例：cellInfo.component 可能是实例或 Vue 包装器
          const getGridInstance = () => {
            const comp = cellInfo.component
            if (comp) {
              if (typeof comp.cellValue === 'function') return comp
              if (
                comp.instance &&
                typeof comp.instance.cellValue === 'function'
              )
                return comp.instance
            }
            return gridInstanceRef.value
          }
          const app = createApp({
            data() {
              return { currentValue: cellInfo.value, _isSelecting: false }
            },
            mounted() {
              ;(this as any)._ddbRef = (this as any).$refs.ddb
            },
            methods: {
              async handleSelection(selected: Record<string, unknown>) {
                if ((this as any)._isSelecting) return
                ;(this as any)._isSelecting = true

                const val = selected[valueExpr]
                const displayVal = selected[displayExpr]
                this.currentValue = val
                const gridInstance = getGridInstance()

                // 1. 设置当前单元格值
                cellInfo.setValue(val, displayVal)

                // 更新 data 对象
                if (cellInfo.data) {
                  cellInfo.data[colDataField] = val
                }

                // 2. 反写所有勾选了 writeBack 的列
                if (
                  ddgAllColumns &&
                  cellInfo.rowIndex !== undefined &&
                  gridInstance
                ) {
                  for (const nc of ddgAllColumns) {
                    if (nc.dataField && nc.writeBack) {
                      const writeVal = selected[nc.dataField]
                      cellInfo.data[nc.dataField] = writeVal
                      gridInstance.cellValue(
                        cellInfo.rowIndex,
                        nc.dataField,
                        writeVal
                      )
                    }
                  }
                }

                // 3. 关闭下拉框
                const ddb = (this as any)._ddbRef
                if (ddb && ddb.instance) {
                  ddb.instance.close()
                }

                // 4. 保存编辑数据
                if (gridInstance) {
                  try {
                    await gridInstance.saveEditData()
                    gridInstance.cancelEditData()
                  } catch (e) {
                    console.error('[4] save/cancel error:', e)
                  }
                }

                setTimeout(() => {
                  ;(this as any)._isSelecting = false
                }, 200)
              },
            },
            render() {
              const self = this as any
              return h(
                DxDropDownBox,
                {
                  ref: 'ddb',
                  value: this.currentValue,
                  displayExpr,
                  valueExpr,
                  contentTemplate: 'gridContent',
                  dropDownOptions: { width: popupWidth },
                  onValueChanged: (e: { value?: unknown }) => {
                    // 仅在非选择过程中同步值，不重复调 setValue
                    if (!(this as any)._isSelecting) {
                      this.currentValue = e.value
                    }
                  },
                },
                {
                  gridContent: () =>
                    h(DxDataGrid, {
                      dataSource: gridDataSource,
                      columns: ddgColumns || [
                        { dataField: valueExpr, caption: valueExpr },
                        { dataField: displayExpr, caption: displayExpr },
                      ],
                      selection: { mode: 'single', showCheckBoxesMode: 'none' },
                      hoverStateEnabled: true,
                      height: 250,
                      remoteOperations: true,
                      paging: { enabled: true, pageSize: 15 },
                      pager: {
                        visible: true,
                        showPageSizeSelector: true,
                        allowedPageSizes: [15, 30, 50, 100],
                        showInfo: true,
                        showNavigationButtons: true,
                      },
                      filterRow:
                        ddg.filterRow === true ? { visible: true } : undefined,
                      selectedRowKeys:
                        this.currentValue !== undefined &&
                        this.currentValue !== null
                          ? [this.currentValue]
                          : [],
                      onSelectionChanged: (e: {
                        selectedRowsData: Record<string, unknown>[]
                      }) => {
                        if (e.selectedRowsData.length > 0) {
                          self.handleSelection(e.selectedRowsData[0])
                        }
                      },
                    }),
                }
              )
            },
          })
          app.mount(cellElement)
          return () => {
            app.unmount()
          }
        }
      }

      return cleanCol
    })
  }

  // 多语言：翻译用户配置的文本属性（caption、text、placeholder 等）
  const TEXT_PROPS = [
    'text',
    'placeholder',
    'title',
    'hint',
    'label',
    'summaryText',
    'emptyPanelText',
  ]
  for (const key of TEXT_PROPS) {
    if (typeof base[key] === 'string' && base[key]) {
      base[key] = t(base[key] as string)
    }
  }
  // DataGrid 列标题翻译
  if (Array.isArray(base.columns)) {
    base.columns = (base.columns as Record<string, unknown>[]).map((col) => {
      if (typeof col.caption === 'string' && col.caption) {
        return { ...col, caption: t(col.caption) }
      }
      return col
    })
  }

  return base
})

/** 是否为可验证控件 */
const isValidatable = computed(() => VALIDATABLE_TYPES.has(props.node.type))

/** 是否为表单控件（需要渲染 label） */
const isFormField = computed(() => FORM_CONTROL_TYPES.has(props.node.type))

/** label 配置 */
const labelConfig = computed(() => {
  const p = props.node.props
  return {
    label: (p.label as string) ?? cfg?.title ?? '',
    labelVisible: p.labelVisible !== false,
    labelPosition: (p.labelPosition as string) || 'top',
    required: p.required === true,
    requiredPosition: (p.requiredPosition as string) || 'after',
    tooltipEnabled: p.tooltipEnabled === true,
    tooltipText: (p.tooltipText as string) || '',
  }
})

/** 是否需要渲染 label 包裹层 */
const showLabel = computed(
  () =>
    isFormField.value &&
    labelConfig.value.labelVisible &&
    !!labelConfig.value.label
)

/** DxValidator 组件引用 */
const validatorRef = ref<InstanceType<typeof DxValidator> | null>(null)

/** 注册/注销验证器到全局注册表 */
onMounted(() => {
  if (isValidatable.value && props.node.fieldName && dxValidationRules.value) {
    const dxv = validatorRef.value as any
    if (dxv?.instance) {
      registerValidator(props.node.fieldName, dxv.instance, () =>
        dxv.instance.validate()
      )
    }
  }
  // 计算 DataGrid 上方元素高度
  if (
    props.node.type === 'dx-data-grid' &&
    (showGridToolbar.value || showAdvancedSearch.value)
  ) {
    nextTick(() => updateGridHeightOffset())
  }
})

// 监听工具栏/高级查询显示状态变化，重新计算高度
watch([showGridToolbar, showAdvancedSearch], () => {
  if (props.node.type === 'dx-data-grid') {
    nextTick(() => updateGridHeightOffset())
  }
})

// ================ FE-S10: 页面级计算规则 watch ================
// 当 store.computeRules 中有规则的目标字段包含当前节点 fieldName 时，
// watch 该规则的触发字段，变化时自动计算并回填当前字段值
if (props.node.fieldName && store.computeRules) {
  for (const rule of store.computeRules) {
    if (rule.enabled === false) continue
    const target = rule.targets.find((t) => t.fieldName === props.node.fieldName)
    if (!target) continue
    const ruleTriggerFields = rule.triggerFields
    const targetExpr = target.expr
    watch(
      () => {
        const root = store.schema
        const ctx: Record<string, unknown> = {}
        for (const fn of ruleTriggerFields) {
          const depNode = findNodeByFieldNameIn(root, fn)
          ctx[fn] = depNode?.props.value ?? null
        }
        return ctx
      },
      (ctx) => {
        if (computeRuleDepth >= COMPUTE_RULE_MAX_DEPTH) {
          console.warn(`[计算规则] 超过最大深度 ${COMPUTE_RULE_MAX_DEPTH}，可能存在循环依赖，中断执行`)
          return
        }
        computeRuleDepth++
        try {
          const result = evaluateExpr(targetExpr, ctx)
          if (result !== null && result !== undefined && result !== props.node.props.value) {
            props.node.props.value = result
          }
        } finally {
          computeRuleDepth--
        }
      },
      { deep: true },
    )
  }
}

// ================ FE-S10 P1: 批量操作 ================
const batchSelectedRows = ref<Record<string, unknown>[]>([])
const showBatchDialog = ref(false)
const batchActionType = ref<string>('')

const batchActions = [
  { name: 'submit', caption: t('批量下达') },
  { name: 'approve', caption: t('批量审批') },
  { name: 'freeze', caption: t('批量冻结') },
  { name: 'delete', caption: t('批量删除') },
]

function onGridSelectionChanged(e: { selectedRowsData: Record<string, unknown>[] }) {
  batchSelectedRows.value = e.selectedRowsData ?? []
  if (store.selectMode) {
    store.selectedRowData = (e.selectedRowsData && e.selectedRowsData.length > 0) ? e.selectedRowsData[0] : null
  }
}

function onGridRowDblClick(e: { data: Record<string, unknown> }) {
  if (store.selectMode) {
    pageModal.closePage(e.data)
  }
}


function startBatchAction(action: string) {
  if (!batchSelectedRows.value.length) return
  batchActionType.value = action
  showBatchDialog.value = true
}

async function onConfirmBatchAction(action: string) {
  try {
    const keyExpr = store.primaryKeyField || (props.node.props.keyExpr as string) || 'id'
    const ids = batchSelectedRows.value.map((r) => r[keyExpr] ?? r[keyExpr.toLowerCase()] ?? r[keyExpr.toUpperCase()])
    await gatewayCall(designerStore.pageName, `batch${action}`, { ids })
    store.schemaRevision += 1
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : String(err))
  }
}

// ================ G2: 主从表渲染 ================
/** 当前表格是否配置了主从表 */
const hasMasterDetail = computed(() => {
  const md = designerStore.masterDetail
  if (!md) return false
  const table = (props.node.props.tableName as string) || designerStore.pageName
  return md.masterTable === table
})

/** 主从表配置 */
const masterDetailConfig = computed(() => {
  if (!hasMasterDetail.value) return null
  return designerStore.masterDetail || null
})

/**
 * 从表数据源：根据主表行数据 + 从表配置加载从表数据。
 * 后端未就绪时返回空数组。
 */
async function loadDetailData(
  masterRow: Record<string, unknown>,
  detailTableName: string,
  foreignKey: string,
): Promise<Record<string, unknown>[]> {
  try {
    const keyExpr = store.primaryKeyField || (props.node.props.keyExpr as string) || 'id'
    const masterId = masterRow[keyExpr]
    const res = await gatewayCall(detailTableName, 'query', {
      filter: JSON.stringify([{ field: foreignKey, operator: '=', value: masterId }]),
    })
    if (res && typeof res === 'object' && 'data' in res) {
      return (res as { data: Record<string, unknown>[] }).data || []
    }
    return []
  } catch {
    return []
  }
}

/** 构建从表 CustomStore（供模板中 DxDataGrid :data-source 使用） */
function getDetailCustomStore(
  masterRow: Record<string, unknown>,
  detail: DetailTableConfig,
) {
  return new CustomStore({
    key: 'id',
    load: async () => {
      const data = await loadDetailData(
        masterRow,
        detail.tableName,
        detail.foreignKey,
      )
      return { data, totalCount: data.length }
    },
  })
}

onBeforeUnmount(() => {
  if (props.node.fieldName) {
    unregisterValidator(props.node.fieldName)
  }
})

/** 转换为 DevExtreme DxValidator 可用的规则格式 */
const dxValidationRules = computed<unknown[] | null>(() => {
  const rules = props.node.props.validationRules
  if (!Array.isArray(rules) || !rules.length) return null
  return rules.map((rule: Record<string, unknown>) => {
    const r: Record<string, unknown> = {
      type: rule.type,
      message: rule.message,
    }
    if (rule.type === 'pattern' && rule.pattern) {
      try {
        r.pattern = new RegExp(rule.pattern as string)
      } catch {
        /* invalid pattern */
      }
    }
    if (rule.type === 'range' || rule.type === 'stringLength') {
      if (rule.min !== undefined) r.min = rule.min
      if (rule.max !== undefined) r.max = rule.max
    }
    if (rule.type === 'compare' && rule.comparisonTarget) {
      const targetFieldName = rule.comparisonTarget as string
      r.comparisonTarget = () => {
        const targetNode = findNodeByFieldNameIn(store.schema, targetFieldName)
        return targetNode?.props.value
      }
      r.comparisonType = rule.comparisonType || '=='
    }
    if (rule.type === 'custom' && rule.validationCallback) {
      const code = rule.validationCallback as string
      r.validationCallback = (e: { value: unknown }) => {
        try {
          const fn = createSandboxedFunction(['value'], code)
          return fn(e.value)
        } catch {
          return true
        }
      }
    }
    return r
  })
})

/** 支持子节点自由定位的容器类型（子节点可像画布一样自由拖拽） */
const FREE_POSITION_CONTAINERS = new Set(['el-splitter-panel', 'el-tab-pane'])

/** 容器内容区样式：与设计器 CanvasNode.vue 的 containerContentStyle 保持一致 */
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
  } else if (FREE_POSITION_CONTAINERS.has(nodeType)) {
    // 自由定位容器：子节点使用 absolute 定位，容器需要 position:relative
    style.position = 'relative'
    style.width = '100%'
    style.height = '100%'
    style.minHeight = '40px'
    style.boxSizing = 'border-box'
    style.overflow = 'visible'
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

/** 子节点是否使用自由定位（绝对定位） */
const childrenPositioned = computed(() =>
  FREE_POSITION_CONTAINERS.has(props.node.type)
)

const wrapperStyle = computed(() => {
  const style: Record<string, string> = {}
  // 子节点在容器内的 flex/span/size 样式
  const nodeType = props.node.type
  const gridSectionTypes = new Set(['el-col', 'el-splitter-panel'])
  const bodyStyle = resolveNodeStyle(props.node)
  const layoutTypes = new Set(['el-row', 'el-splitter'])

  // 定位节点：自由画布模式
  if (props.positioned) {
    // 布局容器在根节点时，使用 node.width 而非 100% 避免超出画布
    if (layoutTypes.has(nodeType)) {
      Object.assign(style, {
        position: 'absolute' as const,
        left: `${props.node.x ?? 0}px`,
        top: `${props.node.y ?? 0}px`,
        width: `${props.node.width ?? 300}px`,
      })
      if (props.node.heightAuto !== false) {
        style.height = 'auto'
      } else {
        style.height = `${props.node.height ?? 120}px`
      }
      style.overflow = 'visible'
    } else {
      Object.assign(style, {
        position: 'absolute' as const,
        left: `${props.node.x ?? 0}px`,
        top: `${props.node.y ?? 0}px`,
        width: `${props.node.width ?? 240}px`,
        height: `${props.node.height ?? 120}px`,
      })
    }
    // 定位节点：合并 bodyStyle，让用户配置的 CSS 宽高覆盖默认画布尺寸
    Object.assign(style, bodyStyle)
    // 限制最大宽度不超过画布右边界（防止 width:100% + left偏移 导致溢出）
    const nodeX = props.node.x ?? 0
    if (nodeX > 0) {
      style.maxWidth = `calc(100% - ${nodeX}px)`
    }
  } else {
    // 非定位节点：容器内子节点
    if (isContainer) {
      if (gridSectionTypes.has(nodeType)) {
        // 栅格 section 类型：宽高由 flex/span/sectionKey 控制，不应用 CSS width/height
        const { width: _w, height: _h, ...restStyle } = bodyStyle
        void _w
        void _h
        Object.assign(style, restStyle)
      } else {
        // 非栅格 section 的容器子节点：合并 bodyStyle
        Object.assign(style, bodyStyle)
      }
    } else {
      // 非定位、非容器节点（普通控件）：合并 bodyStyle 让宽高生效
      if (gridSectionTypes.has(nodeType)) {
        const { width: _w, height: _h, ...restStyle } = bodyStyle
        void _w
        void _h
        Object.assign(style, restStyle)
      } else {
        Object.assign(style, bodyStyle)
      }
    }
  }

  // 栅格列：span>0 用 span/24 栅格制，否则 flexEqual 用 flex:1 等宽
  const span = props.node.props.span
  if (typeof span === 'number' && span > 0) {
    const pct = ((span / 24) * 100).toFixed(2)
    const colCount = 24 / span
    style.flex = `0 0 calc(${pct}% - var(--col-gap, 0px) * ${(colCount - 1) / colCount})`
    style.maxWidth = `calc(${pct}% - var(--col-gap, 0px) * ${(colCount - 1) / colCount})`
  } else if (props.node.props.flexEqual === true) {
    const flexCols = props.node.props.flexCols
    if (typeof flexCols === 'number' && flexCols > 0) {
      style.flex = `0 0 calc(100% / ${flexCols} - var(--col-gap, 0px) * ${(flexCols - 1) / flexCols})`
      style.maxWidth = `calc(100% / ${flexCols} - var(--col-gap, 0px) * ${(flexCols - 1) / flexCols})`
    } else {
      style.flex = '1 1 0'
    }
  }
  const offset = props.node.props.offset
  if (typeof offset === 'number' && offset > 0) {
    style.marginLeft = `${((offset / 24) * 100).toFixed(2)}%`
  }

  // 分割面板尺寸 (size/min/max)
  const size = props.node.props.size
  const sizeUnit = (props.node.props.sizeUnit as string) ?? 'px'
  const min = props.node.props.min
  const max = props.node.props.max
  // 判断父 el-splitter 布局方向：垂直时 flex-basis 控制高度，宽度应撑满容器
  const splitterParent = props.parentId ? findNode(store.schema, props.parentId) : null
  const isVerticalSplitter =
    splitterParent?.node.type === 'el-splitter' &&
    splitterParent.node.props.layout === 'vertical'
  if (typeof size === 'number' && size > 0) {
    if (sizeUnit === '%') {
      style.flex = `0 0 ${size}%`
      if (isVerticalSplitter) {
        style.maxHeight = `${size}%`
        style.width = '100%'
      } else {
        style.maxWidth = `${size}%`
      }
    } else {
      style.flexBasis = `${size}px`
      style.flexGrow = '0'
      style.flexShrink = '0'
    }
  } else if (typeof size === 'number' && size === 0) {
    style.flex = '1'
  }
  if (typeof min === 'number' && min > 0) {
    if (isVerticalSplitter) {
      style.minHeight = `${min}px`
    } else {
      style.minWidth = `${min}px`
    }
  }
  if (typeof max === 'number' && max > 0) {
    if (isVerticalSplitter) {
      style.maxHeight = `${max}px`
    } else {
      style.maxWidth = `${max}px`
    }
  }

  if (showLabel.value && labelConfig.value.labelPosition === 'top') {
    style.height = 'auto'
  }

  return style
})
</script>

<template>
  <div
    v-if="!nodeFieldHidden"
    :id="`renderer-node-${node.id}`"
    class="renderer-node"
    :style="wrapperStyle"
    :data-section="node.sectionKey || undefined"
  >
    <template v-if="!isContainer">
      <!-- dx-data-grid：wrapper 包裹工具栏、高级查询和表格 -->
      <template v-if="node.type === 'dx-data-grid'">
        <div class="data-grid-wrapper" ref="wrapperRef">
          <AdvancedSearchPanel
            v-if="showAdvancedSearch"
            :columns="gridColumns"
            :grid-id="node.id"
            :query-config="designerStore.queryConfig"
            @search="onAdvSearch"
            @reset="onAdvSearchReset"
            @height-change="updateGridHeightOffset"
          />
          <DxToolbar
            v-if="showGridToolbar"
            class="grid-toolbar"
            :items="gridToolbarItems"
          />
          <!-- FE-S10: 批量操作按钮（表格页选中行后显示） -->
          <div v-if="showGridToolbar && batchSelectedRows.length > 1" class="batch-action-bar">
            <span class="batch-count">{{ t('已选') }} {{ batchSelectedRows.length }} {{ t('条') }}</span>
            <button
              v-for="ba in batchActions"
              :key="ba.name"
              class="batch-btn"
              @click="startBatchAction(ba.name)"
            >{{ ba.caption }}</button>
          </div>
          <component
            :is="materialComponents[node.type]"
            v-bind="gridBoundProps"
          >
            <DxValidator
              v-if="isValidatable && dxValidationRules"
              ref="validatorRef"
              :validation-rules="(dxValidationRules as any)"
            />
            <!-- G2: 主从表渲染 -->
            <DxMasterDetail
              v-if="hasMasterDetail && masterDetailConfig"
              :enabled="true"
            >
              <template #default="{ data }">
                <div class="master-detail-container">
                  <div
                    v-for="detail in masterDetailConfig!.details"
                    :key="detail.tableName"
                    class="detail-table-section"
                  >
                    <div class="detail-table-label">{{ detail.label }}</div>
                    <DxDataGrid
                      :data-source="getDetailCustomStore(data.data, detail)"
                      :show-borders="true"
                      :show-row-lines="true"
                      :paging="{ enabled: false }"
                      :column-auto-width="true"
                      :editing="{
                        allowUpdating: detail.editable,
                        allowAdding: detail.allowAdd || false,
                        allowDeleting: detail.allowDelete || false,
                        mode: 'row',
                      }"
                    />
                  </div>
                </div>
              </template>
            </DxMasterDetail>
          </component>
        </div>
      </template>
      <template v-else>
        <div
          v-if="showLabel"
          v-show="node.props.visible !== false"
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
            <component :is="materialComponents[node.type]" v-bind="boundProps">
              <DxValidator
                v-if="isValidatable && dxValidationRules"
                ref="validatorRef"
                :validation-rules="(dxValidationRules as any)"
              />
            </component>
          </div>
        </div>
        <component
          v-else
          :is="materialComponents[node.type]"
          v-bind="boundProps"
        >
          <DxValidator
            v-if="isValidatable && dxValidationRules"
            ref="validatorRef"
            :validation-rules="(dxValidationRules as any)"
          />
        </component>
      </template>
    </template>

    <template v-else>
      <component :is="materialComponents[node.type]" v-bind="boundProps">
        <div class="container-content" :style="containerContentStyle">
          <RendererNode
            v-for="(child, i) in node.children"
            :key="child.id"
            :node="child"
            :positioned="childrenPositioned"
            :parent-id="node.id"
            :index="i"
          />
        </div>
      </component>
    </template>
    <!-- FE-S10: 批量操作确认弹窗 -->
    <BatchActionDialog
      v-model:visible="showBatchDialog"
      :action="batchActionType"
      :selected-count="batchSelectedRows.length"
      @confirm="onConfirmBatchAction"
    />
  </div>
</template>

<style scoped>
.renderer-node {
  box-sizing: border-box;
}
/* DataGrid wrapper 布局（不用 flex，避免影响 DataGrid 内部行筛选） */
.data-grid-wrapper {
  width: 100%;
  height: 100%;
  overflow: hidden;
}
.grid-toolbar {
  height: 40px;
  border-bottom: 1px solid #e4e7ed;
  background-color: #ffffff;
}
.renderer-node :deep(.lc-container) {
  height: 100%;
}
/* DevExtreme 组件填充 renderer-node 容器 */
.renderer-node > :deep(.dx-widget) {
  width: 100%;
  height: 100%;
}
.renderer-node :deep(.container-content) {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 40px;
  width: 100%;
  height: 100%;
  box-sizing: border-box;
}

/* 默认容器子节点的 CanvasNode 高度 100% */
.renderer-node :deep(.canvas-node) {
  min-height: 32px;
}
/* DataGrid 工具栏背景色修复 */
.renderer-node :deep(.dx-datagrid) {
  background-color: #ffffff;
}
.renderer-node :deep(.dx-datagrid .dx-toolbar),
.renderer-node :deep(.dx-datagrid .dx-datagrid-header-panel .dx-toolbar) {
  background-color: #ffffff !important;
  border-bottom: 1px solid #e4e7ed;
}
.renderer-node :deep(.dx-datagrid .dx-toolbar-before),
.renderer-node :deep(.dx-datagrid .dx-toolbar-center),
.renderer-node :deep(.dx-datagrid .dx-toolbar-after),
.renderer-node :deep(.dx-datagrid .dx-toolbar .dx-toolbar-item) {
  background-color: #ffffff !important;
}
.renderer-node :deep(.dx-datagrid .dx-header-row) {
  background-color: #fafafa;
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
.batch-action-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  background: #f0f7ff;
  border-bottom: 1px solid #d6e8ff;
}
.batch-count {
  font-size: 13px;
  color: #409eff;
  font-weight: 600;
}
.batch-btn {
  padding: 4px 12px;
  background: #409eff;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
}
.batch-btn:hover {
  background: #66b1ff;
}
/* G2: 主从表渲染 */
.master-detail-container {
  padding: 8px 12px;
}
.detail-table-section {
  margin-bottom: 12px;
}
.detail-table-label {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 6px;
}
</style>
