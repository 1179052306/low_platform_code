<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import DxTextBox from 'devextreme-vue/text-box'
import DxNumberBox from 'devextreme-vue/number-box'
import DxSwitch from 'devextreme-vue/switch'
import DxSelectBox from 'devextreme-vue/select-box'
import DxColorBox from 'devextreme-vue/color-box'
import DxButton from 'devextreme-vue/button'
import TextEditorDialog from './TextEditorDialog.vue'
import EventCodeDialog from './EventCodeDialog.vue'
import JsonTableEditorDialog from './JsonTableEditorDialog.vue'
import ColumnEditorDialog from './ColumnEditorDialog.vue'
import ToolbarItemsEditorDialog from './ToolbarItemsEditorDialog.vue'
import SummaryEditorDialog from './SummaryEditorDialog.vue'
import PageSelectDialog from './PageSelectDialog.vue'
import ApiSelectDialog from './ApiSelectDialog.vue'
import ThemeSelectDialog from './ThemeSelectDialog.vue'

import { getConfig, getDxAllPropSetters } from '@/materials'
import {
  VALIDATABLE_TYPES,
  VALIDATION_RULES,
  getRuleMeta,
  type ValidationRuleConfig,
  type ValidationType,
} from '@/materials/dx-validation'
import { getEvents } from '@/materials/dx-events'
import {
  designerStore,
  getSelectedNode,
  updateProps,
  removeProp,
  getPropValue,
  hasPropValue,
  setPropValue,
  deletePropValue,
  updateEvent,
  removeEvent,
  updateFieldName,
  updateDbField,
  updatePageCss,
  updateNodeCss,
  selectSingleNode,
  collectAllNodes,
  addNode,
  removeNode,
  createNode,
  pushHistory,
} from '@/store/designer'
import type { PropSetterConfig, NodeCss, Theme } from '@/types/schema'
import { dxPropMeta, type DxPropMeta } from '@/materials/dx-prop-meta'
import { translatePropName } from '@/materials/prop-labels'

import { listApiServices } from '@/store/api-store'
import type { ApiService } from '@/api/registry'
import { t, loadModule } from '@/store/lang'
import { loadThemes } from '@/composables/useTheme'
import { listBaseDataPages, loadPage, type SavedPageMeta } from '@/store/persistence'
import type { ComponentSchema } from '@/types/schema'

const themeDialogVisible = ref(false)
const themeList = ref<Theme[]>([])
const currentThemeName = ref('')

async function refreshThemeName() {
  if (!designerStore.themeId) {
    currentThemeName.value = ''
    return
  }
  if (!themeList.value.length) {
    try {
      themeList.value = await loadThemes()
    } catch {
      // ignore
    }
  }
  const th = themeList.value.find((tm) => tm.id === designerStore.themeId)
  currentThemeName.value = th?.name || ''
}

watch(() => designerStore.themeId, refreshThemeName, { immediate: true })

function onThemeSelect(themeId: string) {
  designerStore.themeId = themeId
  refreshThemeName()
}

/** 统一的属性项接口，兼容 PropSetterConfig 和 DxPropMeta */
interface FullPropItem {
  /** 属性名（prop 或 name） */
  name: string
  /** 类型（type 或 kind） */
  kind: string
  /** 选项（select 类型专用） */
  options?: { label: string; value: string | number }[]
  /** 来源：dx=DevExtreme 元数据, config=物料配置 */
  source: 'dx' | 'config'
}

const selectedNode = computed(() => getSelectedNode())

/** 当前控件是否为表单输入类控件（category='表单'），需要填写 dbField */
const isFormField = computed(() => {
  const node = selectedNode.value
  if (!node) return false
  const c = getConfig(node.type)
  return c?.category === '表单'
})

/** 当前控件是否为 dx-base-data 基础资料控件 */
const isBaseDataControl = computed(() => selectedNode.value?.type === 'dx-base-data')

/** 基础资料页面列表（pageType='basedata'） */
const baseDataPages = ref<SavedPageMeta[]>([])
const refPageFields = ref<{ dbField: string; fieldName: string; displayText: string }[]>([])
const refPagePrimaryKey = ref('')

/** 加载基础资料页面列表 */
async function loadBaseDataPages() {
  try {
    baseDataPages.value = await listBaseDataPages()
  } catch {
    baseDataPages.value = []
  }
}

/** 加载指定基础资料页面的字段列表 */
async function loadRefPageFields(refPageId: string) {
  if (!refPageId) {
    refPageFields.value = []
    refPagePrimaryKey.value = ''
    return
  }
  try {
    const page = await loadPage(refPageId)
    if (!page) {
      refPageFields.value = []
      refPagePrimaryKey.value = ''
      return
    }
    refPagePrimaryKey.value = page.primaryKeyField || 'id'
    const fields: { dbField: string; fieldName: string; displayText: string }[] = []
    const collect = (nodes: ComponentSchema[]) => {
      for (const node of nodes) {
        if (node.dbField) {
          const label = (node.props?.label as string) || ''
          const fieldName = node.fieldName || node.dbField
          fields.push({
            dbField: node.dbField,
            fieldName,
            displayText: label ? `${label} (${fieldName})` : fieldName,
          })
        }
        if (node.children?.length) collect(node.children)
      }
    }
    collect(page.root || [])
    refPageFields.value = fields
  } catch {
    refPageFields.value = []
    refPagePrimaryKey.value = ''
  }
}

/** dx-base-data：选择关联页面 */
function onRefPageSelect(e: { value?: unknown }) {
  const node = getSelectedNode()
  if (!node) return
  const pageId = String(e.value ?? '')
  setEditable('refPageId', pageId)
  updateProps(node.id, 'refPageId', pageId)
  node.refPageId = pageId
  loadRefPageFields(pageId).then(() => {
    const pk = refPagePrimaryKey.value
    if (pk) {
      setEditable('valueField', pk)
      updateProps(node.id, 'valueField', pk)
    }
  })
}

/** dx-base-data：选择显示字段 */
function onDisplayFieldSelect(e: { value?: unknown }) {
  const node = getSelectedNode()
  if (!node) return
  const val = String(e.value ?? '')
  setEditable('displayField', val)
  updateProps(node.id, 'displayField', val)
}

/** dx-base-data：选择值字段 */
function onValueFieldSelect(e: { value?: unknown }) {
  const node = getSelectedNode()
  if (!node) return
  const val = String(e.value ?? '')
  setEditable('valueField', val)
  updateProps(node.id, 'valueField', val)
}

/** 当前选中是否为 el-row 栅格布局 */
const isRowLayout = computed(() => selectedNode.value?.type === 'el-row')

/** 栅格快捷布局：列数和行数 */
const gridCols = ref(2)
const gridRows = ref(1)

/** 快捷生成栅格：清空现有子节点，按列数×行数生成 el-col */
function generateGrid() {
  const node = getSelectedNode()
  if (!node || node.type !== 'el-row') return
  const cols = Math.max(1, Math.min(24, gridCols.value))
  const rows = Math.max(1, Math.min(10, gridRows.value))
  const span = Math.floor(24 / cols)
  pushHistory()
  node.children = []
  for (let i = 0; i < rows * cols; i++) {
    const col = createNode('el-col')
    if (col) {
      Object.assign(col.props, { span, offset: 0, padding: '0', background: '#ffffff' })
      col.css = { padding: '0', margin: '0' }
      node.children.push(col)
    }
  }
  designerStore.schemaRevision += 1
}

/** 监听选中节点变化，加载基础资料页面和字段 */
watch(
  () => selectedNode.value?.id,
  () => {
    if (isBaseDataControl.value) {
      loadBaseDataPages()
      const refPageId = String(selectedNode.value?.props.refPageId ?? '')
      if (refPageId) loadRefPageFields(refPageId)
    }
  },
  { immediate: true },
)

/** dbField 本地输入值和错误提示 */
const dbFieldInput = ref('')
const dbFieldError = ref('')
watch(
  () => selectedNode.value?.id,
  () => {
    dbFieldInput.value = selectedNode.value?.dbField ?? ''
    dbFieldError.value = ''
  },
  { immediate: true }
)

function onDbFieldInput(e: Event): void {
  const node = getSelectedNode()
  if (!node) return
  const value = (e.target as HTMLInputElement).value
  dbFieldInput.value = value
  const result = updateDbField(node.id, value)
  if (!result.ok) {
    dbFieldError.value = t(result.error ?? '')
  } else {
    dbFieldError.value = ''
  }
}

function onDbFieldBlur(): void {
  const node = getSelectedNode()
  if (dbFieldError.value && node) {
    dbFieldInput.value = node.dbField ?? ''
    dbFieldError.value = ''
  }
}

const cfg = computed(() =>
  selectedNode.value ? getConfig(selectedNode.value.type) : undefined
)
const gridEditingMode = computed(() => {
  const node = selectedNode.value
  if (!node || node.type !== 'dx-data-grid') return 'none'
  const editing = node.props?.editing as Record<string, unknown> | undefined
  return String(editing?.mode ?? 'none')
})

/** 当前活动标签页 */
const activeTab = ref<'basic' | 'css' | 'layout' | 'events' | 'validation'>(
  'basic'
)

/** 控件搜索（已由 DxSelectBox 内置搜索处理） */

/** 更多属性区域的搜索关键字 */
const searchKeyword = ref('')
const fieldNameError = ref('')
/** 字段名称本地输入值（v-model 双向绑定，避免 :value 单向绑定时校验失败导致输入字符消失） */
const fieldNameInput = ref('')
/** 监听选中节点变化，同步 fieldName 到本地输入 */
watch(
  () => selectedNode.value?.id,
  () => {
    fieldNameInput.value = selectedNode.value?.fieldName ?? ''
    fieldNameError.value = ''
  },
  { immediate: true }
)

/** 本地编辑副本 */
const editingProps = ref<Record<string, unknown>>({})

/** 弹窗编辑器状态 */
const textDialogVisible = ref(false)
const textDialogProp = ref('')
const textDialogValue = ref('')
const jsonDialogVisible = ref(false)
const jsonDialogProp = ref('')
const jsonDialogValue = ref('')
const jsonDialogColumns = ref<string[] | undefined>(undefined)

const columnDialogVisible = ref(false)
const columnDialogProp = ref('')
const columnDialogValue = ref('')

const toolbarItemsDialogVisible = ref(false)
const toolbarItemsDialogProp = ref('')
const toolbarItemsDialogValue = ref('')

const summaryDialogVisible = ref(false)
const summaryDialogValue = ref('')

function openTextDialog(prop: string, currentValue: unknown) {
  textDialogProp.value = prop
  textDialogValue.value = String(currentValue ?? '')
  textDialogVisible.value = true
}

function onTextDialogConfirm(value: string) {
  if (!selectedNode.value) return
  setEditable(textDialogProp.value, value)
  updateProps(selectedNode.value.id, textDialogProp.value, value)
  textDialogVisible.value = false
}

function openJsonDialog(prop: string, currentValue: unknown) {
  jsonDialogProp.value = prop
  // 当前值可能是对象或数组，序列化为 JSON 字符串
  if (typeof currentValue === 'string') {
    jsonDialogValue.value = currentValue
  } else {
    try {
      jsonDialogValue.value = JSON.stringify(currentValue ?? [], null, 2)
    } catch {
      jsonDialogValue.value = String(currentValue ?? '')
    }
  }
  // dx-data-grid 的静态数据源：传入列配置以锁定列
  const node = selectedNode.value
  if (node && node.type === 'dx-data-grid' && prop === 'dataSource') {
    const cols = node.props.columns as Record<string, unknown>[] | undefined
    jsonDialogColumns.value = Array.isArray(cols)
      ? cols.map((c) => String(c.dataField ?? '')).filter(Boolean)
      : []
  } else {
    jsonDialogColumns.value = undefined
  }
  jsonDialogVisible.value = true
}

function onJsonDialogConfirm(value: string) {
  if (!selectedNode.value) return
  // 验证并解析 JSON
  try {
    const parsed = JSON.parse(value)
    setEditable(jsonDialogProp.value, parsed)
    updateProps(selectedNode.value.id, jsonDialogProp.value, parsed)
  } catch {
    // 如果不是有效 JSON，可能是用户输入的普通字符串
    setEditable(jsonDialogProp.value, value)
    updateProps(selectedNode.value.id, jsonDialogProp.value, value)
  }
  jsonDialogVisible.value = false
}

function openColumnDialog(prop: string, currentValue: unknown) {
  columnDialogProp.value = prop
  if (typeof currentValue === 'string') {
    columnDialogValue.value = currentValue
  } else {
    try {
      columnDialogValue.value = JSON.stringify(currentValue ?? [], null, 2)
    } catch {
      columnDialogValue.value = String(currentValue ?? '')
    }
  }
  columnDialogVisible.value = true
}

function onColumnDialogConfirm(value: string) {
  if (!selectedNode.value) return
  try {
    const parsed = JSON.parse(value)
    setEditable(columnDialogProp.value, parsed)
    updateProps(selectedNode.value.id, columnDialogProp.value, parsed)
  } catch {
    setEditable(columnDialogProp.value, value)
    updateProps(selectedNode.value.id, columnDialogProp.value, value)
  }
  columnDialogVisible.value = false
}

function openToolbarItemsDialog(prop: string, currentValue: unknown) {
  toolbarItemsDialogProp.value = prop
  if (typeof currentValue === 'string') {
    toolbarItemsDialogValue.value = currentValue
  } else {
    try {
      toolbarItemsDialogValue.value = JSON.stringify(
        currentValue ?? '[]',
        null,
        2
      )
    } catch {
      toolbarItemsDialogValue.value = '[]'
    }
  }
  toolbarItemsDialogVisible.value = true
}

function onToolbarItemsDialogConfirm(value: string) {
  if (!selectedNode.value) return
  try {
    const parsed = JSON.parse(value)
    setEditable(toolbarItemsDialogProp.value, parsed)
    updateProps(selectedNode.value.id, toolbarItemsDialogProp.value, parsed)
  } catch {
    setEditable(toolbarItemsDialogProp.value, value)
    updateProps(selectedNode.value.id, toolbarItemsDialogProp.value, value)
  }
  toolbarItemsDialogVisible.value = false
}

function openSummaryDialog(prop: string, currentValue: unknown) {
  if (typeof currentValue === 'string') {
    summaryDialogValue.value = currentValue
  } else {
    try {
      summaryDialogValue.value = JSON.stringify(
        currentValue ?? { totalItems: [] },
        null,
        2
      )
    } catch {
      summaryDialogValue.value = ''
    }
  }
  summaryDialogVisible.value = true
}

function onSummaryDialogConfirm(value: string) {
  if (!selectedNode.value) return
  try {
    const parsed = JSON.parse(value)
    setEditable('summary', parsed)
    updateProps(selectedNode.value.id, 'summary', parsed)
  } catch {
    setEditable('summary', value)
    updateProps(selectedNode.value.id, 'summary', value)
  }
  summaryDialogVisible.value = false
}

function getEditable(prop: string): unknown {
  return getPropValue(editingProps.value, prop)
}
function setEditable(prop: string, value: unknown): void {
  setPropValue(editingProps.value, prop, value)
}
function deleteEditable(prop: string): void {
  deletePropValue(editingProps.value, prop)
}

/** pageOpen setter：获取配置对象 */
function getPageOpenConfig(prop: string): {
  pageId: string
  params: string
  mode: string
  title: string
} {
  const val = getEditable(prop) as Record<string, unknown> | undefined
  return {
    pageId: (val?.pageId as string) || '',
    params: val?.params ? JSON.stringify(val.params) : '',
    mode: (val?.mode as string) || 'popup',
    title: (val?.title as string) || '',
  }
}

/** pageOpen setter：设置单个字段 */
function setPageOpenField(prop: string, field: string, value: unknown): void {
  const val = (getEditable(prop) as Record<string, unknown> | undefined) || {}
  if (field === 'params') {
    try {
      val.params = value ? JSON.parse(value as string) : {}
    } catch {
      return
    }
  } else {
    val[field] = value
  }
  setEditable(prop, val)
  if (selectedNode.value) {
    updateProps(selectedNode.value.id, prop, val)
  }
}

/** 页面选择弹窗状态 */
const pageSelectDialogVisible = ref(false)
const pageSelectDialogProp = ref('')
const pageSelectDialogPageId = ref('')

function openPageSelectDialog(prop: string) {
  pageSelectDialogProp.value = prop
  pageSelectDialogPageId.value = getPageOpenConfig(prop).pageId
  pageSelectDialogVisible.value = true
}

function onPageSelect(pageId: string) {
  setPageOpenField(pageSelectDialogProp.value, 'pageId', pageId)
}

/** API 服务选择弹窗状态 */
const apiSelectDialogVisible = ref(false)
const apiSelectDialogProp = ref('')

function openApiSelectDialog(prop: string) {
  apiSelectDialogProp.value = prop
  apiSelectDialogVisible.value = true
}

function onApiSelect(serviceId: string) {
  if (selectedNode.value) {
    setEditable(apiSelectDialogProp.value, serviceId)
    updateProps(selectedNode.value.id, apiSelectDialogProp.value, serviceId)
  }
}

function clearApiSelect(prop: string) {
  if (selectedNode.value) {
    setEditable(prop, '')
    updateProps(selectedNode.value.id, prop, '')
  }
}

/** API 服务详情弹窗 */
const apiDetailVisible = ref(false)
const apiDetailService = ref<ApiService | null>(null)

async function openApiDetail(prop: string) {
  const serviceId = String(getEditable(prop) || '')
  if (!serviceId) return
  try {
    const list = await listApiServices()
    const svc = list.find((s) => s.id === serviceId)
    if (svc) {
      apiDetailService.value = svc
      apiDetailVisible.value = true
    }
  } catch {
    /* ignore */
  }
}

/** JSON 设置器的文本缓存 */
const jsonTextCache = ref<Record<string, string>>({})
const jsonError = ref<Record<string, string>>({})

/** 事件编辑代码缓存 */
const eventCodeCache = ref<Record<string, string>>({})

function jsonKey(nodeId: string, prop: string): string {
  return `${nodeId}::${prop}`
}
function getJsonText(nodeId: string, prop: string, value: unknown): string {
  const key = jsonKey(nodeId, prop)
  if (jsonTextCache.value[key] !== undefined) return jsonTextCache.value[key]
  if (value === undefined || value === null) return ''
  return JSON.stringify(value, null, 2)
}
function onJsonInput(prop: string, raw: string): void {
  const node = getSelectedNode()
  if (!node) return
  const key = jsonKey(node.id, prop)
  jsonTextCache.value[key] = raw
  if (!raw.trim()) {
    delete jsonError.value[key]
    deleteEditable(prop)
    removeProp(node.id, prop)
    return
  }
  try {
    const parsed = JSON.parse(raw)
    delete jsonError.value[key]
    setEditable(prop, parsed)
    updateProps(node.id, prop, parsed)
  } catch (err) {
    jsonError.value[key] = err instanceof Error ? err.message : String(err)
  }
}

function onTextareaInput(prop: string, e: Event): void {
  const node = getSelectedNode()
  if (!node) return
  const value = (e.target as HTMLTextAreaElement).value
  setEditable(prop, value)
  updateProps(node.id, prop, value)
}

watch(
  () => designerStore.selectedId,
  () => {
    const node = getSelectedNode()
    editingProps.value = node ? { ...node.props } : {}
    jsonTextCache.value = {}
    jsonError.value = {}
    eventCodeCache.value = {}
    searchKeyword.value = ''
    fieldNameError.value = ''
    activeTab.value = 'basic'
  },
  { immediate: true }
)

/** 控件字段名称 — v-model 管理本地值，校验通过时才写入 store */
function onFieldNameInput(e: Event): void {
  const node = getSelectedNode()
  if (!node) return
  const value = (e.target as HTMLInputElement).value.trim()
  if (!value) {
    fieldNameError.value = t('字段名称不能为空')
    return
  }
  const allNodes = collectAllNodes(designerStore.schema)
  if (allNodes.some((n) => n.id !== node.id && n.fieldName === value)) {
    fieldNameError.value = t('字段名称已存在，不能重复')
    return
  }
  fieldNameError.value = ''
  updateFieldName(node.id, value)
}
function onFieldNameBlur(): void {
  const node = getSelectedNode()
  // blur 时如果校验有错，恢复为 store 中的原值
  if (fieldNameError.value && node) {
    fieldNameInput.value = node.fieldName ?? ''
    fieldNameError.value = ''
  }
}

function onPageBackgroundChange(e: { value?: unknown }): void {
  updatePageCss({ background: String(e.value ?? '#ffffff') })
}

function makeHandler(setter: PropSetterConfig) {
  return (e: { value?: unknown }) => {
    setEditable(setter.prop, e.value)
    const node = getSelectedNode()
    if (node) updateProps(node.id, setter.prop, e.value)
  }
}

/** 将 PropSetterConfig 转换为 FullPropItem */
function setterToItem(s: PropSetterConfig): FullPropItem {
  return { name: s.prop, kind: s.type, options: s.options, source: 'config' }
}

/** 将 DxPropMeta 转换为 FullPropItem */
function metaToItem(p: DxPropMeta): FullPropItem {
  return { name: p.name, kind: p.kind, options: p.options, source: 'dx' }
}

const fullProps = computed<FullPropItem[]>(() => {
  const node = selectedNode.value
  if (!node) return []
  // 对 DevExtreme 控件，使用完整属性列表
  if (node.type.startsWith('dx-')) {
    const allSetters = getDxAllPropSetters(node.type)
    const seen = new Set<string>()
    return allSetters
      .filter((s) => {
        if (seen.has(s.prop)) return false
        seen.add(s.prop)
        return true
      })
      .map(setterToItem)
  }
  // 非 DX 控件：使用 meta prop（不受 COMPLEX_PROPS 限制）
  const seen = new Set<string>()
  return (dxPropMeta[node.type] ?? [])
    .filter((p) => {
      if (seen.has(p.name)) return false
      seen.add(p.name)
      return true
    })
    .map(metaToItem)
})

const coveredProps = computed(() => {
  const set = new Set<string>()
  for (const setter of cfg.value?.propSetters ?? []) set.add(setter.prop)
  return set
})

/** 只读/输出状态属性：不是可配置项，配了也不会产生可预期的外观变化 */
const NON_CONFIGURABLE_PROPS = new Set([
  // 只读状态属性
  'isDirty',
  'isValid',
  'validationError',
  'validationErrors',
  'validationStatus',
  'opened',
  // DataGrid 内部状态
  'selectedItemKeys',
  'selectedItems',
  'selectedRowKeys',
  'focusedRowIndex',
  'focusedRowKey',
  'focusedColumnIndex',
  // Chat 组件
  'typingUsers',
  'alerts',
])

/** 全部属性列表（排除基础标签页已覆盖的 + 不可配置的） */
const moreProps = computed<FullPropItem[]>(() => {
  const node = selectedNode.value
  if (!node) return []
  return fullProps.value.filter(
    (p) =>
      !coveredProps.value.has(p.name) && !NON_CONFIGURABLE_PROPS.has(p.name)
  )
})

const normalizedKeyword = computed(() =>
  searchKeyword.value.trim().toLowerCase()
)

/** 表单页面的控件字段列表（供表格列配置选择导入，仅表单输入类控件） */
const formFieldOptions = computed(() => {
  const formSchema = designerStore.bizSchemas.form
  const nodes = collectAllNodes(formSchema)
  return nodes
    .filter((n) => {
      if (!n.fieldName) return false
      const c = getConfig(n.type)
      return c?.category === '表单'
    })
    .map((n) => {
      const cfg = getConfig(n.type)
      return {
        fieldName: n.fieldName as string,
        dbField: n.dbField ?? '',
        title: cfg?.title ?? n.type,
      }
    })
})

const filteredCommonSetters = computed(() => {
  const setters = cfg.value?.propSetters ?? []
  // 引用 editingProps 确保响应式
  void editingProps.value
  // 过滤掉不满足 visibleIf 条件的属性
  const visibleSetters = setters.filter((setter) => {
    if (!setter.visibleIf) return true
    const condProp = setter.visibleIf.prop
    const condVal = getEditable(condProp)
    const equals = setter.visibleIf.equals
    if (typeof equals === 'boolean') return Boolean(condVal) === equals
    return String(condVal ?? '') === String(equals)
  })
  const hiddenFiltered = visibleSetters.filter((setter) => {
    if (!setter.hiddenIf) return true
    const hiddenVal = getEditable(setter.hiddenIf.prop)
    const notEmpty = setter.hiddenIf.notEmpty !== false
    if (notEmpty) return !hiddenVal
    return Boolean(hiddenVal)
  })
  // 表格模式下数据源类型固定为远程加载，不允许修改，隐藏该 setter
  const bizFiltered = hiddenFiltered.filter((setter) => {
    if (
      designerStore.mode === 'biz' &&
      designerStore.bizMode === 'table' &&
      setter.prop === 'dataSourceType'
    ) {
      return false
    }
    return true
  })
  const kw = normalizedKeyword.value
  if (!kw) return bizFiltered
  return bizFiltered.filter(
    (setter) =>
      setter.prop.toLowerCase().includes(kw) ||
      setter.label.toLowerCase().includes(kw)
  )
})

function isSet(prop: string): boolean {
  const node = selectedNode.value
  if (!node) return false
  return hasPropValue(node.props, prop)
}

function clearProp(prop: string): void {
  const node = getSelectedNode()
  if (!node) return
  deleteEditable(prop)
  delete jsonTextCache.value[jsonKey(node.id, prop)]
  delete jsonError.value[jsonKey(node.id, prop)]
  removeProp(node.id, prop)
}

function makeFullHandler(prop: string) {
  return (e: { value?: unknown }) => {
    setEditable(prop, e.value)
    const node = getSelectedNode()
    if (node) updateProps(node.id, prop, e.value)
  }
}

function displayName(name: string): string {
  return t(translatePropName(name))
}

function formatSetterLabel(setter: PropSetterConfig): string {
  const suffix = `（${setter.prop}）`
  const label = t(setter.label)
  if (label.endsWith(suffix)) return label
  return `${label}${suffix}`
}

function translatedOptions(
  options: { label: string; value: string | number }[] | undefined
) {
  if (!options) return []
  return options.map((opt) => ({ ...opt, label: t(opt.label) }))
}

function fullPropMatches(p: FullPropItem, kw: string): boolean {
  return (
    p.name.toLowerCase().includes(kw) ||
    displayName(p.name).toLowerCase().includes(kw)
  )
}

const MAX_RESULTS = 300
const filteredMoreProps = computed<FullPropItem[]>(() => {
  const kw = normalizedKeyword.value
  if (kw)
    return moreProps.value
      .filter((p) => fullPropMatches(p, kw))
      .slice(0, MAX_RESULTS)
  return moreProps.value
})
const resultTruncated = computed(() => {
  const kw = normalizedKeyword.value
  if (!kw) return false
  return (
    moreProps.value.filter((p) => fullPropMatches(p, kw)).length > MAX_RESULTS
  )
})

function numberValue(v: unknown): number | undefined {
  if (v === undefined || v === null || v === '') return undefined
  const n = Number(v)
  return Number.isFinite(n) ? n : undefined
}

// ---------- CSS 样式编辑 ----------

const CSS_FIELDS: {
  key: keyof NodeCss
  label: string
  placeholder: string
  unitSelect?: boolean
  unitOptions?: string[]
}[] = [
  {
    key: 'width',
    label: t('宽度'),
    placeholder: t('自动'),
    unitSelect: true,
    unitOptions: ['px', '%', 'auto'],
  },
  {
    key: 'height',
    label: t('高度'),
    placeholder: t('自动'),
    unitSelect: true,
    unitOptions: ['px', '%', 'auto'],
  },
  {
    key: 'padding',
    label: t('内边距'),
    placeholder: t('如 10px 或 10px 20px'),
  },
  { key: 'margin', label: t('外边距'), placeholder: t('如 8px 或 8px auto') },
  { key: 'background', label: t('背景色'), placeholder: t('如 #f5f5f5') },
  { key: 'borderRadius', label: t('圆角'), placeholder: t('如 4px 或 50%') },
  { key: 'border', label: t('边框'), placeholder: t('如 1px solid #ddd') },
  {
    key: 'overflow',
    label: t('溢出'),
    placeholder: 'visible/hidden/auto/scroll',
  },
  { key: 'opacity', label: t('透明度'), placeholder: '0 ~ 1' },
]

/** 解析带单位的 CSS 值为 { value, unit } */
function parseCssValue(str: string): { value: string; unit: string } {
  if (!str) return { value: '', unit: 'px' }
  const trimmed = str.trim()
  if (trimmed === 'auto') return { value: '', unit: 'auto' }
  const match = trimmed.match(/^(-?\d+\.?\d*)\s*(px|%|rem|em|vh|vw)?$/)
  if (match) {
    return { value: match[1], unit: match[2] || 'px' }
  }
  return { value: trimmed, unit: '' }
}

/** 组合数值和单位为 CSS 值字符串 */
function composeCssValue(value: string, unit: string): string {
  if (!value) return unit === 'auto' ? 'auto' : ''
  return value + unit
}

/** 获取当前节点 CSS 某字段的解析值（schemaRevision 参与计算以确保响应式更新） */
function getCssParsed(key: keyof NodeCss): { value: string; unit: string } {
  void designerStore.schemaRevision
  return parseCssValue(getCssValue(key))
}

function getCssValue(key: keyof NodeCss): string {
  void designerStore.schemaRevision
  return selectedNode.value?.css?.[key] ?? ''
}

function onCssInput(key: keyof NodeCss, e: Event): void {
  const node = getSelectedNode()
  if (!node) return
  const input = e.target as HTMLInputElement
  updateNodeCss(node.id, key, input.value)
}

function onCssUnitInput(key: keyof NodeCss, unit: string): void {
  const node = getSelectedNode()
  if (!node) return
  const parsed = parseCssValue(getCssValue(key))
  let newValue: string
  if (unit === 'auto') {
    newValue = 'auto'
  } else if (!parsed.value) {
    // 数值为空时，根据单位类型设置合理默认值
    newValue = unit === '%' ? '100%' : ''
  } else {
    newValue = parsed.value + unit
  }
  updateNodeCss(node.id, key, newValue)
}

function onCssColorChange(key: keyof NodeCss) {
  return (e: { value?: unknown }) => {
    const node = getSelectedNode()
    if (!node) return
    updateNodeCss(node.id, key, String(e.value ?? ''))
  }
}

// ---------- 控件搜索 ----------

const allPageNodes = computed(() => collectAllNodes(designerStore.schema))

/** 下拉搜索选项列表 */
const nodeSelectItems = computed(() =>
  allPageNodes.value.map((node) => {
    const c = getConfig(node.type)
    return {
      id: node.id,
      label: `${c?.icon ?? ''} ${t(c?.title ?? node.type)}${
        node.fieldName ? ' (' + node.fieldName + ')' : ''
      }`,
      type: node.type,
    }
  })
)

function onNodeSelectChanged(e: { value?: unknown }): void {
  const id = e.value as string | null
  if (id) {
    selectSingleNode(id)
    scrollNodeIntoView(id)
  }
}

function onNodeSearchSelect(id: string): void {
  selectSingleNode(id)
  scrollNodeIntoView(id)
}

/** 滚动画布使选中节点可见 */
function scrollNodeIntoView(id: string): void {
  setTimeout(() => {
    const el = document.getElementById(`canvas-node-${id}`)
    if (el)
      el.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
        inline: 'center',
      })
  }, 50)
}

function nodeDisplayLabel(node: { type: string; fieldName?: string }): string {
  const c = getConfig(node.type)
  const name = t(c?.title ?? node.type)
  const field = node.fieldName ? ` (${node.fieldName})` : ''
  return `${name}${field}`
}

// ---------- 布局控件子项管理 (Row / Splitter / Container) ----------

const LAYOUT_TYPES = new Set(['el-row', 'el-splitter', 'el-tabs'])

const isLayoutContainer = computed(() =>
  selectedNode.value ? LAYOUT_TYPES.has(selectedNode.value.type) : false
)

/** 是否为 DevExtreme 控件（类型以 dx- 开头） */
const isDxComponent = computed(() =>
  selectedNode.value ? selectedNode.value.type.startsWith('dx-') : false
)

/** 表格模式下的 dx-data-grid：禁止设置尺寸（宽高固定 100% 撑满画布） */
const isTableGrid = computed(
  () =>
    designerStore.mode === 'biz' &&
    designerStore.bizMode === 'table' &&
    selectedNode.value?.type === 'dx-data-grid',
)

/** 是否显示尺寸区块（DX 控件且非表格模式的 data-grid） */
const showSizeSection = computed(() => isDxComponent.value && !isTableGrid.value)

const layoutChildren = computed(() => selectedNode.value?.children ?? [])

function addLayoutChild(): void {
  const node = getSelectedNode()
  if (!node || !LAYOUT_TYPES.has(node.type)) return
  // 根据容器类型确定子节点类型
  let childType = 'el-col'
  let defaultProps: Record<string, unknown> = {}
  if (node.type === 'el-splitter') {
    childType = 'el-splitter-panel'
    defaultProps = {
      size: 0,
      sizeUnit: 'px',
      min: 100,
      max: 0,
      collapsible: false,
      padding: '0',
      background: '#ffffff',
    }
  } else if (node.type === 'el-tabs') {
    childType = 'el-tab-pane'
    const idx = node.children.length + 1
    defaultProps = {
      label: t('标签') + idx,
      name: `tab${idx}`,
      padding: '0',
      background: '#ffffff',
    }
  }
  // 保持当前选中的布局容器，不跳转到新子节点
  addNode(childType, node.id, node.children.length, false, defaultProps)
}

function removeLayoutChild(index: number): void {
  const node = getSelectedNode()
  if (!node || index < 0 || index >= node.children.length) return
  removeNode(node.children[index].id)
}

function onChildPropNumber(childId: string, prop: string, e: Event): void {
  const input = e.target as HTMLInputElement
  const v = input.value.trim()
  updateProps(childId, prop, v ? Number(v) : 0)
}

function onChildPropSwitch(
  childId: string,
  prop: string,
  e: { value?: unknown }
): void {
  updateProps(childId, prop, !!e.value)
}

function onChildPropSelect(childId: string, prop: string, e: Event): void {
  const select = e.target as HTMLSelectElement
  updateProps(childId, prop, select.value)
}

function onChildPropText(childId: string, prop: string, e: Event): void {
  const input = e.target as HTMLInputElement
  updateProps(childId, prop, input.value)
}

// ---- 栅格布局列编辑 ----

const isElRow = computed(() => selectedNode.value?.type === 'el-row')

// ---- 分割面板编辑 ----

const isElSplitter = computed(() => selectedNode.value?.type === 'el-splitter')

// ---- 标签页编辑 ----

const isElTabs = computed(() => selectedNode.value?.type === 'el-tabs')

// ---- 事件配置 ----

/** 当前控件可用事件列表 */
const availableEvents = computed(() => {
  const node = selectedNode.value
  if (!node) return []
  return getEvents(node.type)
})

/** 事件编辑弹窗状态 */
const eventDialogVisible = ref(false)
const eventDialogName = ref('')
const eventDialogValue = ref('')

function getEventCode(eventName: string): string {
  void designerStore.schemaRevision
  const node = selectedNode.value
  if (!node || !node.events) return eventCodeCache.value[eventName] ?? ''
  return eventCodeCache.value[eventName] ?? node.events[eventName] ?? ''
}

function onEventCodeInput(eventName: string, e: Event): void {
  const node = selectedNode.value
  if (!node) return
  const value = (e.target as HTMLTextAreaElement).value
  eventCodeCache.value[eventName] = value
  updateEvent(node.id, eventName, value)
}

function isEventSet(eventName: string): boolean {
  const node = selectedNode.value
  return !!(node?.events && node.events[eventName])
}

function clearEvent(eventName: string): void {
  const node = selectedNode.value
  if (!node) return
  delete eventCodeCache.value[eventName]
  removeEvent(node.id, eventName)
}

function openEventDialog(eventName: string): void {
  eventDialogName.value = eventName
  eventDialogValue.value = getEventCode(eventName)
  eventDialogVisible.value = true
}

function onEventDialogConfirm(value: string): void {
  const node = selectedNode.value
  if (!node) return
  eventCodeCache.value[eventDialogName.value] = value
  updateEvent(node.id, eventDialogName.value, value)
  eventDialogVisible.value = false
}

// ---- 验证规则 ----
const isValidatable = computed(() => {
  const node = selectedNode.value
  if (!node) return false
  return VALIDATABLE_TYPES.has(node.type)
})

const isRequired = computed(() => {
  const node = selectedNode.value
  if (!node) return false
  const rules = node.props.validationRules
  if (!Array.isArray(rules)) return false
  return rules.some((r: ValidationRuleConfig) => r.type === 'required')
})

function onRequiredChange(e: { value?: unknown }) {
  const node = selectedNode.value
  if (!node) return
  const required = !!e.value
  const rules = [...nodeValidationRules.value]
  const idx = rules.findIndex((r) => r.type === 'required')
  if (required && idx === -1) {
    rules.unshift({ type: 'required', message: t('此字段为必填项') })
  } else if (!required && idx >= 0) {
    rules.splice(idx, 1)
  }
  updateProps(node.id, 'validationRules', rules.length ? rules : undefined)
}

const nodeValidationRules = computed<ValidationRuleConfig[]>(() => {
  const node = selectedNode.value
  if (!node) return []
  const rules = node.props.validationRules
  return Array.isArray(rules) ? (rules as ValidationRuleConfig[]) : []
})

function addValidationRule(type: ValidationType) {
  const node = selectedNode.value
  if (!node) return
  const rules = [...nodeValidationRules.value]
  const meta = getRuleMeta(type)
  const newRule: ValidationRuleConfig = {
    type,
    message: `${t(meta?.label || '')}${t('验证失败')}`,
  }
  if (type === 'range') {
    newRule.min = 0
    newRule.max = 100
  }
  if (type === 'stringLength') {
    newRule.min = 0
    newRule.max = 50
  }
  if (type === 'compare') {
    newRule.comparisonType = '=='
  }
  rules.push(newRule)
  updateProps(node.id, 'validationRules', rules)
}

function removeValidationRule(idx: number) {
  const node = selectedNode.value
  if (!node) return
  const rules = [...nodeValidationRules.value]
  rules.splice(idx, 1)
  updateProps(node.id, 'validationRules', rules.length ? rules : undefined)
}

function updateValidationRule(idx: number, prop: string, value: unknown) {
  const node = selectedNode.value
  if (!node) return
  const rules = [...nodeValidationRules.value]
  rules[idx] = { ...rules[idx], [prop]: value }
  updateProps(node.id, 'validationRules', rules)
}

function onAddRuleSelect(e: Event) {
  const select = e.target as HTMLSelectElement
  const type = select.value as ValidationType
  if (type) {
    addValidationRule(type)
    select.value = ''
  }
}
</script>

<template>
  <aside class="prop-panel">
    <div class="panel-title">{{ t('属性配置') }}</div>

    <!-- 无选中节点：页面设置 + 控件搜索 -->
    <div v-if="!selectedNode" class="prop-section page-settings">
      <div class="prop-section-title">{{ t('页面设置') }}</div>
      <div class="prop-item">
        <div class="prop-label">{{ t('页面名称') }}</div>
        <input
          v-model="designerStore.pageName"
          class="page-input"
          :placeholder="t('请输入页面名称')"
        />
      </div>
      <div class="prop-item">
        <div class="prop-label">{{ t('数据表名') }}</div>
        <input
          v-model="designerStore.tableName"
          class="page-input"
          :placeholder="t('请输入数据表名')"
        />
      </div>
      <div class="prop-item">
        <div class="prop-label">{{ t('主键字段') }}</div>
        <input
          v-model="designerStore.primaryKeyField"
          class="page-input"
          :placeholder="t('请输入主键字段名，如 id')"
        />
      </div>
      <div class="prop-item">
        <div class="prop-label">{{ t('页面唯一标识') }}</div>
        <input :value="designerStore.pageId" class="page-input" readonly />
      </div>
      <div class="prop-item">
        <div class="prop-label">{{ t('页面背景') }}</div>
        <DxColorBox
          :value="designerStore.pageCss.background"
          @value-changed="onPageBackgroundChange"
        />
      </div>
      <div class="prop-item">
        <div class="prop-label">{{ t('页面主题') }}</div>
        <button
          class="page-input"
          style="
            text-align: left;
            cursor: pointer;
            display: block;
            width: 100%;
            box-sizing: border-box;
          "
          @click="themeDialogVisible = true"
        >
          {{ currentThemeName || t('未选择') }}
        </button>
      </div>
      <div class="page-tip">{{ t('点击画布空白处即可设置页面信息') }}</div>
      <ThemeSelectDialog
        v-model:visible="themeDialogVisible"
        :current-theme-id="designerStore.themeId"
        @select="onThemeSelect"
      />

      <!-- 控件搜索（仅空白页时显示，支持下拉搜索选择） -->
      <div
        v-if="allPageNodes.length"
        class="prop-section"
        style="margin-top: 18px"
      >
        <div class="prop-section-title">{{ t('页面控件搜索') }}</div>
        <DxSelectBox
          :items="nodeSelectItems"
          display-expr="label"
          value-expr="id"
          :value="null"
          :placeholder="t('搜索并选择控件')"
          :show-clear-button="true"
          :search-enabled="true"
          :search-expr="['label', 'type']"
          search-mode="contains"
          @value-changed="onNodeSelectChanged"
        />
      </div>
    </div>

    <!-- 有选中节点但无配置 -->
    <div v-else-if="!cfg" class="panel-placeholder">
      {{ t('点击画布中的组件进行配置') }}
    </div>

    <!-- 有选中节点：标签页 -->
    <template v-else>
      <div class="prop-node-type">
        {{ cfg.icon }} {{ t(cfg.title)
        }}<span v-if="cfg.enName" class="prop-en-name">
          ({{ cfg.enName }})</span
        >
      </div>

      <!-- 标签页导航 -->
      <div class="prop-tabs">
        <button
          :class="['prop-tab', activeTab === 'basic' ? 'active' : '']"
          @click="activeTab = 'basic'"
        >
          {{ t('基础属性') }}
        </button>
        <button
          v-if="isLayoutContainer"
          :class="['prop-tab', activeTab === 'layout' ? 'active' : '']"
          @click="activeTab = 'layout'"
        >
          {{ t('布局配置') }}
        </button>
        <button
          v-if="!isDxComponent"
          :class="['prop-tab', activeTab === 'css' ? 'active' : '']"
          @click="activeTab = 'css'"
        >
          {{ t('CSS 样式') }}
        </button>
        <button
          v-if="availableEvents.length"
          :class="['prop-tab', activeTab === 'events' ? 'active' : '']"
          @click="activeTab = 'events'"
        >
          {{ t('事件') }}
        </button>
        <button
          v-if="isValidatable"
          :class="['prop-tab', activeTab === 'validation' ? 'active' : '']"
          @click="activeTab = 'validation'"
        >
          {{ t('验证') }}
        </button>
      </div>

      <!-- ====== 基础属性标签页 ====== -->
      <div v-if="activeTab === 'basic'" class="prop-tab-content">
        <div class="prop-section">
          <div class="prop-section-title">{{ t('控件标识') }}</div>
          <div class="prop-item">
            <div class="prop-label">{{ t('字段名称') }}</div>
            <input
              class="page-input"
              v-model="fieldNameInput"
              @input="onFieldNameInput"
              @blur="onFieldNameBlur"
            />
            <div v-if="fieldNameError" class="field-name-error">
              {{ fieldNameError }}
            </div>
          </div>
          <!-- 数据库字段名（仅表单输入类控件显示） -->
          <div v-if="isFormField" class="prop-item">
            <div class="prop-label">{{ t('数据库字段') }}</div>
            <input
              class="page-input"
              v-model="dbFieldInput"
              :placeholder="t('输入数据库字段名，不能重复')"
              @input="onDbFieldInput"
              @blur="onDbFieldBlur"
            />
            <div v-if="dbFieldError" class="field-name-error">
              {{ dbFieldError }}
            </div>
          </div>
        </div>

        <!-- 栅格快捷布局（仅 el-row 显示） -->
        <div v-if="isRowLayout" class="prop-section">
          <div class="prop-section-title">{{ t('快捷布局') }}</div>
          <div class="prop-item" style="display: flex; gap: 8px; align-items: flex-end;">
            <div style="flex: 1;">
              <div class="prop-label">{{ t('列数') }}</div>
              <DxNumberBox
                :value="gridCols"
                :min="1"
                :max="24"
                :show-spin-buttons="false"
                value-change-event="input"
                @value-changed="gridCols = $event.value"
              />
            </div>
            <div style="flex: 1;">
              <div class="prop-label">{{ t('行数') }}</div>
              <DxNumberBox
                :value="gridRows"
                :min="1"
                :max="10"
                :show-spin-buttons="false"
                value-change-event="input"
                @value-changed="gridRows = $event.value"
              />
            </div>
            <button
              class="grid-gen-btn"
              @click="generateGrid"
            >{{ t('生成') }}</button>
          </div>
        </div>

        <!-- 基础资料控件配置（仅 dx-base-data 显示） -->
        <div v-if="isBaseDataControl" class="prop-section">
          <div class="prop-section-title">{{ t('基础资料配置') }}</div>
          <div class="prop-item">
            <div class="prop-label">{{ t('关联基础资料页面') }}</div>
            <DxSelectBox
              :items="baseDataPages"
              display-expr="pageName"
              value-expr="pageId"
              :value="getEditable('refPageId')"
              :placeholder="t('选择基础资料页面')"
              :search-enabled="true"
              @value-changed="onRefPageSelect"
            />
          </div>
          <div class="prop-item">
            <div class="prop-label">{{ t('显示字段') }}</div>
            <DxSelectBox
              :items="refPageFields"
              display-expr="displayText"
              value-expr="dbField"
              :value="getEditable('displayField')"
              :placeholder="t('选择显示字段')"
              @value-changed="onDisplayFieldSelect"
            />
          </div>
          <div class="prop-item">
            <div class="prop-label">{{ t('值字段（主键）') }}</div>
            <input
              class="page-input"
              :value="getEditable('valueField') || refPagePrimaryKey"
              readonly
              :placeholder="t('自动取关联页面主键字段')"
            />
          </div>
        </div>

        <!-- 必填验证开关（仅可验证控件显示） -->
        <div v-if="isValidatable" class="prop-section">
          <div class="prop-section-title">{{ t('验证') }}</div>
          <div class="prop-item">
            <div class="prop-label">{{ t('必填') }}</div>
            <DxSwitch :value="isRequired" @value-changed="onRequiredChange" />
          </div>
        </div>

        <!-- DevExtreme 控件的宽度/高度（带单位下拉框） -->
        <div v-if="showSizeSection" class="prop-section">
          <div class="prop-section-title">{{ t('尺寸') }}</div>
          <div class="prop-item">
            <div class="prop-label">{{ t('宽度') }}</div>
            <div class="css-unit-row">
              <input
                v-if="getCssParsed('width').unit !== 'auto'"
                class="page-input css-unit-input"
                :value="getCssParsed('width').value"
                :placeholder="t('数值')"
                @input="(e: Event) => { const p = getCssParsed('width'); updateNodeCss(selectedNode!.id, 'width', composeCssValue((e.target as HTMLInputElement).value, p.unit)) }"
              />
              <span v-else class="page-input css-unit-auto">auto</span>
              <select
                class="css-unit-select"
                :value="getCssParsed('width').unit"
                @change="(e: Event) => onCssUnitInput('width', (e.target as HTMLSelectElement).value)"
                @click.stop
                @mousedown.stop
              >
                <option
                  v-for="opt in ['px', '%', 'auto']"
                  :key="opt"
                  :value="opt"
                >
                  {{ opt }}
                </option>
              </select>
            </div>
          </div>
          <div class="prop-item">
            <div class="prop-label">{{ t('高度') }}</div>
            <div class="css-unit-row">
              <input
                v-if="getCssParsed('height').unit !== 'auto'"
                class="page-input css-unit-input"
                :value="getCssParsed('height').value"
                :placeholder="t('数值')"
                @input="(e: Event) => { const p = getCssParsed('height'); updateNodeCss(selectedNode!.id, 'height', composeCssValue((e.target as HTMLInputElement).value, p.unit)) }"
              />
              <span v-else class="page-input css-unit-auto">auto</span>
              <select
                class="css-unit-select"
                :value="getCssParsed('height').unit"
                @change="(e: Event) => onCssUnitInput('height', (e.target as HTMLSelectElement).value)"
                @click.stop
                @mousedown.stop
              >
                <option
                  v-for="opt in ['px', '%', 'auto']"
                  :key="opt"
                  :value="opt"
                >
                  {{ opt }}
                </option>
              </select>
            </div>
          </div>
        </div>

        <DxTextBox
          v-model:value="searchKeyword"
          :placeholder="t('搜索属性名或中文标签')"
          :show-clear-button="true"
          class="prop-search"
        />

        <div v-if="filteredCommonSetters.length" class="prop-section">
          <div class="prop-section-title">{{ t('可配置属性') }}</div>
          <div
            v-for="setter in filteredCommonSetters"
            :key="setter.prop"
            class="prop-item"
          >
            <div class="prop-label">
              {{ formatSetterLabel(setter) }}
              <button
                v-if="setter.type === 'textarea'"
                class="editor-btn"
                :title="t('弹窗编辑大文本')"
                @click="openTextDialog(setter.prop, getEditable(setter.prop))"
              >
                📝
              </button>
              <button
                v-if="setter.type === 'json'"
                class="editor-btn"
                :title="t('表格方式编辑 JSON')"
                @click="openJsonDialog(setter.prop, getEditable(setter.prop))"
              >
                ▦
              </button>
            </div>
            <DxTextBox
              v-if="setter.type === 'input'"
              :value="String(getEditable(setter.prop) ?? '')"
              @value-changed="makeHandler(setter)($event)"
            />
            <div
              v-else-if="setter.type === 'textarea'"
              class="textarea-wrapper"
            >
              <textarea
                class="json-editor"
                :value="String(getEditable(setter.prop) ?? '')"
                spellcheck="false"
                @input="onTextareaInput(setter.prop, $event)"
              />
            </div>
            <DxNumberBox
              v-else-if="setter.type === 'number'"
              :value="Number(getEditable(setter.prop) ?? 0)"
              :show-spin-buttons="true"
              @value-changed="makeHandler(setter)($event)"
            />
            <DxSwitch
              v-else-if="setter.type === 'switch'"
              :value="Boolean(getEditable(setter.prop))"
              @value-changed="makeHandler(setter)($event)"
            />
            <DxSelectBox
              v-else-if="setter.type === 'select'"
              :items="translatedOptions(setter.options)"
              display-expr="label"
              value-expr="value"
              :value="getEditable(setter.prop)"
              @value-changed="makeHandler(setter)($event)"
            />
            <div
              v-else-if="setter.type === 'apiSelect'"
              class="api-select-setter"
            >
              <div class="api-select-row">
                <button
                  class="api-select-btn"
                  @click="openApiSelectDialog(setter.prop)"
                >
                  {{
                    getEditable(setter.prop)
                      ? t('已选：') + getEditable(setter.prop)
                      : t('选择 API 服务')
                  }}
                </button>
                <button
                  v-if="getEditable(setter.prop)"
                  class="api-select-view-btn"
                  :title="t('查看 API 服务详情')"
                  @click="openApiDetail(setter.prop)"
                >
                  👁
                </button>
              </div>
              <button
                v-if="getEditable(setter.prop)"
                class="api-select-clear-btn"
                @click="clearApiSelect(setter.prop)"
              >
                {{ t('清除') }}
              </button>
            </div>
            <DxColorBox
              v-else-if="setter.type === 'color'"
              :value="String(getEditable(setter.prop) ?? '#333333')"
              @value-changed="makeHandler(setter)($event)"
            />
            <template v-else-if="setter.type === 'json' && selectedNode">
              <div class="json-wrapper">
                <textarea
                  class="json-editor"
                  :value="
                    getJsonText(
                      selectedNode.id,
                      setter.prop,
                      getEditable(setter.prop)
                    )
                  "
                  spellcheck="false"
                  @input="onJsonInput(setter.prop, ($event.target as HTMLTextAreaElement).value)"
                />
                <button
                  class="json-table-btn"
                  @click="openJsonDialog(setter.prop, getEditable(setter.prop))"
                >
                  {{ t('▦ 表格编辑') }}
                </button>
              </div>
              <div
                v-if="jsonError[jsonKey(selectedNode.id, setter.prop)]"
                class="json-error"
              >
                {{ t('JSON 格式错误：')
                }}{{ jsonError[jsonKey(selectedNode.id, setter.prop)] }}
              </div>
            </template>
            <!-- 列配置编辑器 -->
            <div v-else-if="setter.type === 'columns'" class="columns-setter">
              <button
                class="column-edit-btn"
                @click="openColumnDialog(setter.prop, getEditable(setter.prop))"
              >
                {{ t('▦ 编辑列配置') }}
              </button>
            </div>
            <!-- 工具栏按钮编辑器 -->
            <div
              v-else-if="setter.type === 'toolbarItems'"
              class="columns-setter"
            >
              <button
                class="column-edit-btn"
                @click="
                  openToolbarItemsDialog(setter.prop, getEditable(setter.prop))
                "
              >
                {{ t('▦ 编辑工具栏按钮') }}
              </button>
            </div>
            <!-- 汇总配置编辑器（读取当前列字段勾选，仅数字列可汇总） -->
            <div v-else-if="setter.type === 'summary'" class="columns-setter">
              <button
                class="column-edit-btn"
                @click="
                  openSummaryDialog(setter.prop, getEditable(setter.prop))
                "
              >
                {{ t('▦ 编辑汇总配置') }}
              </button>
            </div>
            <!-- 打开页面配置：弹窗选择页面 + 传参 + 弹窗形式 -->
            <div
              v-else-if="setter.type === 'pageOpen' && selectedNode"
              class="page-open-setter"
            >
              <button
                class="page-open-select-btn"
                @click="openPageSelectDialog(setter.prop)"
              >
                {{
                  getPageOpenConfig(setter.prop).pageId
                    ? t('已选：') + getPageOpenConfig(setter.prop).pageId
                    : t('选择页面')
                }}
              </button>
              <button
                v-if="getPageOpenConfig(setter.prop).pageId"
                class="page-open-clear-btn"
                @click="setPageOpenField(setter.prop, 'pageId', '')"
              >
                {{ t('清除') }}
              </button>
              <select
                class="page-open-mode"
                :value="getPageOpenConfig(setter.prop).mode"
                @change="
                  setPageOpenField(
                    setter.prop,
                    'mode',
                    ($event.target as HTMLSelectElement).value
                  )
                "
              >
                <option value="popup">{{ t('弹窗') }}</option>
                <option value="drawer">{{ t('抽屉') }}</option>
              </select>
              <input
                class="page-open-title"
                type="text"
                :value="getPageOpenConfig(setter.prop).title"
                :placeholder="t('弹窗标题（留空用页面名）')"
                @input="
                  setPageOpenField(
                    setter.prop,
                    'title',
                    ($event.target as HTMLInputElement).value
                  )
                "
              />
              <textarea
                class="page-open-params"
                :value="getPageOpenConfig(setter.prop).params"
                :placeholder="
                  t(
                    '传参 JSON，如 {&quot;id&quot;: 1, &quot;mode&quot;: &quot;view&quot;}'
                  )
                "
                rows="2"
                @input=" setPageOpenField( setter.prop, 'params',
              ($event.target as HTMLTextAreaElement).value ) "
              />
            </div>
          </div>
        </div>
      </div>

      <!-- ====== CSS 样式标签页（DevExtreme 控件不显示） ====== -->
      <div
        v-if="activeTab === 'css' && !isDxComponent"
        class="prop-tab-content"
      >
        <div class="prop-section">
          <div class="prop-section-title">{{ t('控件 CSS 样式') }}</div>
          <div class="css-tip">
            {{
              t('宽度/高度可选择 px/%/auto 单位，子控件默认 100% 填充父容器')
            }}
          </div>
          <div v-for="field in CSS_FIELDS" :key="field.key" class="prop-item">
            <div class="prop-label">{{ field.label }}</div>
            <template v-if="field.key === 'background'">
              <DxColorBox
                :value="getCssValue(field.key) || '#ffffff'"
                @value-changed="onCssColorChange(field.key)($event)"
              />
            </template>
            <template v-else-if="field.unitSelect && field.unitOptions">
              <div class="css-unit-row">
                <input
                  v-if="getCssParsed(field.key).unit !== 'auto'"
                  class="page-input css-unit-input"
                  :value="getCssParsed(field.key).value"
                  :placeholder="t('数值')"
                  @input="(e: Event) => { const p = getCssParsed(field.key); updateNodeCss(selectedNode!.id, field.key, composeCssValue((e.target as HTMLInputElement).value, p.unit)) }"
                />
                <span v-else class="page-input css-unit-auto">auto</span>
                <select
                  class="css-unit-select"
                  :value="getCssParsed(field.key).unit"
                  @change="(e: Event) => onCssUnitInput(field.key, (e.target as HTMLSelectElement).value)"
                  @click.stop
                  @mousedown.stop
                >
                  <option
                    v-for="opt in field.unitOptions"
                    :key="opt"
                    :value="opt"
                  >
                    {{ opt }}
                  </option>
                </select>
              </div>
            </template>
            <template v-else>
              <input
                class="page-input"
                :value="getCssValue(field.key)"
                :placeholder="field.placeholder"
                @input="onCssInput(field.key, $event)"
              />
            </template>
          </div>
        </div>
      </div>

      <!-- 页面控件搜索已移到空白页状态 -->

      <!-- ====== 布局配置标签页 ====== -->
      <div
        v-if="activeTab === 'layout' && isLayoutContainer"
        class="prop-tab-content"
      >
        <!-- 栅格布局：列管理 -->
        <div v-if="isElRow" class="prop-section">
          <div class="prop-section-title">
            {{ t('列配置') }} ({{ layoutChildren.length }} {{ t('列') }})
          </div>
          <div class="items-tip">
            {{ t('24 栅格系统，每列占 span/24 宽度，总和不宜超过 24。') }}
          </div>
          <div
            v-for="(col, i) in layoutChildren"
            :key="col.id"
            class="item-row"
          >
            <div class="item-header">
              <button
                class="item-select-btn"
                :class="{ active: designerStore.selectedId === col.id }"
                @click="selectSingleNode(col.id)"
              >
                <span class="item-index">#{{ i + 1 }}</span>
                {{ t('列') }} (span={{ col.props.span ?? 12 }})
              </button>
              <button
                class="item-remove-btn"
                :title="t('移除')"
                @click="removeLayoutChild(i)"
              >
                ✕
              </button>
            </div>
            <div class="item-props">
              <div class="item-prop-row">
                <span class="item-prop-label">span (1-24)</span>
                <input
                  class="item-prop-input"
                  type="number"
                  min="1"
                  max="24"
                  :value="col.props.span ?? 12"
                  @input="onChildPropNumber(col.id, 'span', $event)"
                />
              </div>
              <div class="item-prop-row">
                <span class="item-prop-label">offset</span>
                <input
                  class="item-prop-input"
                  type="number"
                  min="0"
                  max="23"
                  :value="col.props.offset ?? 0"
                  @input="onChildPropNumber(col.id, 'offset', $event)"
                />
              </div>
            </div>
          </div>
          <DxButton
            :text="t('＋ 添加列')"
            icon="add"
            styling-mode="outlined"
            :width="120"
            @click="addLayoutChild"
          />
        </div>

        <!-- 分割面板：面板管理 -->
        <div v-if="isElSplitter" class="prop-section">
          <div class="prop-section-title">
            {{ t('面板配置') }} ({{ layoutChildren.length }} {{ t('个') }})
          </div>
          <div class="items-tip">
            {{
              t(
                '每个面板可配置尺寸 (size)、最小值 (min)、最大值 (max)、可折叠。size=0 表示均分剩余空间。'
              )
            }}
          </div>
          <div
            v-for="(panel, i) in layoutChildren"
            :key="panel.id"
            class="item-row"
          >
            <div class="item-header">
              <button
                class="item-select-btn"
                :class="{ active: designerStore.selectedId === panel.id }"
                @click="selectSingleNode(panel.id)"
              >
                <span class="item-index">#{{ i + 1 }}</span> {{ t('面板') }}
              </button>
              <button
                class="item-remove-btn"
                :title="t('移除')"
                @click="removeLayoutChild(i)"
              >
                ✕
              </button>
            </div>
            <div class="item-props">
              <div class="item-prop-row">
                <span class="item-prop-label">{{ t('尺寸 (0=均分)') }}</span>
                <div class="css-unit-row">
                  <input
                    class="item-prop-input css-unit-input"
                    type="number"
                    min="0"
                    step="10"
                    :value="panel.props.size ?? 0"
                    @input="onChildPropNumber(panel.id, 'size', $event)"
                  />
                  <select
                    class="css-unit-select"
                    :value="panel.props.sizeUnit ?? 'px'"
                    @change="(e: Event) => onChildPropSelect(panel.id, 'sizeUnit', e)"
                    @click.stop
                    @mousedown.stop
                  >
                    <option value="px">px</option>
                    <option value="%">%</option>
                  </select>
                </div>
              </div>
              <div class="item-prop-row">
                <span class="item-prop-label">min (px)</span>
                <input
                  class="item-prop-input"
                  type="number"
                  min="0"
                  step="50"
                  :value="panel.props.min ?? 0"
                  @input="onChildPropNumber(panel.id, 'min', $event)"
                />
              </div>
              <div class="item-prop-row">
                <span class="item-prop-label">{{ t('max (px/0=不限)') }}</span>
                <input
                  class="item-prop-input"
                  type="number"
                  min="0"
                  step="50"
                  :value="panel.props.max ?? 0"
                  @input="onChildPropNumber(panel.id, 'max', $event)"
                />
              </div>
              <div class="item-prop-row">
                <span class="item-prop-label">{{ t('可折叠') }}</span>
                <DxSwitch
                  :value="!!panel.props.collapsible"
                  @value-changed="
                    onChildPropSwitch(panel.id, 'collapsible', $event)
                  "
                />
              </div>
            </div>
          </div>
          <DxButton
            :text="t('＋ 添加面板')"
            icon="add"
            styling-mode="outlined"
            :width="130"
            @click="addLayoutChild"
          />
        </div>

        <!-- 标签页：面板管理 -->
        <div v-if="isElTabs" class="prop-section">
          <div class="prop-section-title">
            {{ t('标签配置') }} ({{ layoutChildren.length }} {{ t('个') }})
          </div>
          <div class="items-tip">
            {{
              t(
                '每个标签页可配置名称和标识，点击标签可切换编辑不同面板的内容。'
              )
            }}
          </div>
          <div
            v-for="(pane, i) in layoutChildren"
            :key="pane.id"
            class="item-row"
          >
            <div class="item-header">
              <button
                class="item-select-btn"
                :class="{ active: designerStore.selectedId === pane.id }"
                @click="selectSingleNode(pane.id)"
              >
                <span class="item-index">#{{ i + 1 }}</span>
                {{ pane.props.label || t('标签') }}
              </button>
              <button
                class="item-remove-btn"
                :title="t('移除')"
                @click="removeLayoutChild(i)"
              >
                ✕
              </button>
            </div>
            <div class="item-props">
              <div class="item-prop-row">
                <span class="item-prop-label">{{ t('标签名称') }}</span>
                <input
                  class="item-prop-input"
                  type="text"
                  :value="pane.props.label ?? ''"
                  @input="onChildPropText(pane.id, 'label', $event)"
                />
              </div>
              <div class="item-prop-row">
                <span class="item-prop-label">{{ t('唯一标识') }}</span>
                <input
                  class="item-prop-input"
                  type="text"
                  :value="pane.props.name ?? ''"
                  @input="onChildPropText(pane.id, 'name', $event)"
                />
              </div>
            </div>
          </div>
          <DxButton
            :text="t('＋ 添加标签')"
            icon="add"
            styling-mode="outlined"
            :width="130"
            @click="addLayoutChild"
          />
        </div>
      </div>

      <!-- ====== 事件标签页 ====== -->
      <div
        v-if="activeTab === 'events' && availableEvents.length"
        class="prop-tab-content"
      >
        <div class="prop-section">
          <div class="prop-section-title">{{ t('事件配置') }}</div>
          <div class="events-tip">
            {{ t('为控件事件编写 JavaScript 处理代码。参数') }} <code>e</code>
            {{ t('为事件对象。') }}
          </div>
          <div
            v-for="evt in availableEvents"
            :key="evt.name"
            class="prop-item event-item"
          >
            <div class="prop-label event-label">
              <span class="event-name" :title="t(evt.description ?? '')">{{
                t(evt.label)
              }}</span>
              <code class="event-code-tag">{{ evt.name }}</code>
              <button
                class="editor-btn"
                :title="t('弹窗编辑代码')"
                @click="openEventDialog(evt.name)"
              >
                📝
              </button>
              <DxButton
                v-if="isEventSet(evt.name)"
                icon="close"
                styling-mode="text"
                :width="22"
                :height="22"
                :hint="t('清除事件处理代码')"
                @click="clearEvent(evt.name)"
              />
            </div>
            <textarea
              class="json-editor event-code-editor"
              :value="getEventCode(evt.name)"
              :placeholder="t('// JavaScript 代码，参数 e 为事件对象')"
              spellcheck="false"
              @input="onEventCodeInput(evt.name, $event)"
            />
          </div>
        </div>
      </div>

      <!-- ====== 验证标签页 ====== -->
      <div
        v-if="activeTab === 'validation' && isValidatable"
        class="prop-tab-content"
      >
        <div class="prop-section">
          <div class="prop-section-title">{{ t('验证规则') }}</div>
          <div class="events-tip">
            {{
              t('配置表单验证规则，支持多种验证类型。验证在预览模式下生效。')
            }}
          </div>

          <div
            v-for="(rule, idx) in nodeValidationRules"
            :key="idx"
            class="validation-rule-item"
          >
            <div class="validation-rule-header">
              <span class="validation-rule-type">{{
                getRuleMeta(rule.type)?.label || rule.type
              }}</span>
              <button
                class="validation-remove-btn"
                :title="t('删除此规则')"
                @click="removeValidationRule(idx)"
              >
                ✕
              </button>
            </div>
            <div class="prop-item">
              <div class="prop-label">{{ t('提示信息') }}</div>
              <input
                class="page-input"
                :value="rule.message || ''"
                @input="(e: Event) => updateValidationRule(idx, 'message', (e.target as HTMLInputElement).value)"
              />
            </div>
            <template
              v-for="field in getRuleMeta(rule.type)?.fields || []"
              :key="field.prop"
            >
              <div class="prop-item">
                <div class="prop-label">{{ t(field.label) }}</div>
                <input
                  v-if="field.inputType === 'text'"
                  class="page-input"
                  :value="(rule as unknown as Record<string, unknown>)[field.prop] as string || ''"
                  @input="(e: Event) => updateValidationRule(idx, field.prop, (e.target as HTMLInputElement).value)"
                />
                <input
                  v-else-if="field.inputType === 'number'"
                  class="page-input"
                  type="number"
                  :value="(rule as unknown as Record<string, unknown>)[field.prop] as number ?? ''"
                  @input="(e: Event) => updateValidationRule(idx, field.prop, Number((e.target as HTMLInputElement).value))"
                />
                <select
                  v-else-if="field.inputType === 'select'"
                  class="page-input"
                  :value="(rule as unknown as Record<string, unknown>)[field.prop] as string || ''"
                  @change="(e: Event) => updateValidationRule(idx, field.prop, (e.target as HTMLSelectElement).value)"
                >
                  <option
                    v-for="opt in field.options"
                    :key="opt.value"
                    :value="opt.value"
                  >
                    {{ t(opt.label) }}
                  </option>
                </select>
              </div>
            </template>
          </div>

          <div class="prop-item">
            <select class="page-input" @change="onAddRuleSelect">
              <option value="">{{ t('+ 添加验证规则') }}</option>
              <option
                v-for="r in VALIDATION_RULES"
                :key="r.type"
                :value="r.type"
              >
                {{ t(r.label) }} — {{ t(r.description) }}
              </option>
            </select>
          </div>
        </div>
      </div>
    </template>

    <!-- 弹窗编辑器 -->
    <TextEditorDialog
      v-model:model-value="textDialogValue"
      v-model:visible="textDialogVisible"
      :title="t('文本编辑')"
      @update:model-value="onTextDialogConfirm"
    />
    <JsonTableEditorDialog
      v-model:model-value="jsonDialogValue"
      v-model:visible="jsonDialogVisible"
      :title="t('数据源编辑')"
      :columns="jsonDialogColumns"
      @update:model-value="onJsonDialogConfirm"
    />
    <ColumnEditorDialog
      v-model:model-value="columnDialogValue"
      v-model:visible="columnDialogVisible"
      :title="t('列配置编辑')"
      :editing-mode="gridEditingMode"
      :form-fields="formFieldOptions"
      @update:model-value="onColumnDialogConfirm"
    />
    <ToolbarItemsEditorDialog
      v-model:model-value="toolbarItemsDialogValue"
      v-model:visible="toolbarItemsDialogVisible"
      :title="t('工具栏按钮配置')"
      @update:model-value="onToolbarItemsDialogConfirm"
    />
    <SummaryEditorDialog
      v-model:model-value="summaryDialogValue"
      v-model:visible="summaryDialogVisible"
      :title="t('汇总高级配置')"
      :columns="selectedNode?.type === 'dx-data-grid' ? (selectedNode.props.columns as unknown[] | undefined) : undefined"
      @update:model-value="onSummaryDialogConfirm"
    />
    <EventCodeDialog
      v-model:model-value="eventDialogValue"
      v-model:visible="eventDialogVisible"
      :title="t('事件代码编辑 - ') + eventDialogName"
      @update:model-value="onEventDialogConfirm"
    />
    <PageSelectDialog
      v-model:visible="pageSelectDialogVisible"
      v-model:model-value="pageSelectDialogPageId"
      :exclude-page-id="designerStore.pageId"
      @select="onPageSelect"
    />
    <ApiSelectDialog
      v-model:visible="apiSelectDialogVisible"
      :model-value="String(getEditable(apiSelectDialogProp) || '')"
      @select="(id: string) => onApiSelect(id)"
    />
    <!-- API 服务详情弹窗 -->
    <Teleport to="body" v-if="apiDetailVisible && apiDetailService">
      <div class="api-detail-overlay" @click="apiDetailVisible = false">
        <div class="api-detail-dialog" @click.stop>
          <div class="api-detail-header">
            <span>{{ t('API 服务详情') }}</span>
            <button class="api-detail-close" @click="apiDetailVisible = false">
              ×
            </button>
          </div>
          <div class="api-detail-body">
            <div class="api-detail-row">
              <label>{{ t('服务 ID') }}</label>
              <span>{{ apiDetailService.id }}</span>
            </div>
            <div class="api-detail-row">
              <label>URL</label>
              <span>{{ apiDetailService.url }}</span>
            </div>
            <div class="api-detail-row">
              <label>{{ t('请求方法') }}</label>
              <span>{{ apiDetailService.method || 'GET' }}</span>
            </div>
            <div class="api-detail-row">
              <label>{{ t('权限码') }}</label>
              <span>{{ apiDetailService.permCode || '-' }}</span>
            </div>
            <div class="api-detail-row">
              <label>{{ t('描述') }}</label>
              <span>{{ apiDetailService.description || '-' }}</span>
            </div>
            <div class="api-detail-row">
              <label>{{ t('分组') }}</label>
              <span>{{ apiDetailService.group || '-' }}</span>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </aside>
</template>

<style scoped>
.prop-panel {
  width: 300px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 6px;
  overflow-y: auto;
  padding: 12px;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
}
.panel-placeholder {
  color: #c0c4cc;
  font-size: 13px;
  text-align: center;
  padding: 40px 0;
}
.panel-placeholder.small {
  padding: 16px 0;
  font-size: 12px;
}
.prop-node-type {
  font-size: 13px;
  font-weight: 600;
  padding: 8px 10px;
  background: #f0f7ff;
  border-radius: 6px;
  margin-bottom: 10px;
}
.prop-en-name {
  font-weight: 400;
  color: #909399;
  font-size: 12px;
}

/* 标签页 */
.prop-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 12px;
  border-bottom: 1px solid #e4e7ed;
}
.prop-tab {
  flex: 1;
  padding: 8px 4px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 12px;
  color: #606266;
  border-bottom: 2px solid transparent;
  transition: all 0.15s;
}
.prop-tab:hover {
  color: #1976d2;
}
.prop-tab.active {
  color: #1976d2;
  border-bottom-color: #1976d2;
  font-weight: 600;
}
.prop-tab-content {
  min-height: 100px;
}

.page-settings {
  margin-top: 4px;
}
.page-input {
  width: 100%;
  box-sizing: border-box;
  height: 32px;
  padding: 0 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
}
.page-input:focus {
  border-color: #1976d2;
}
.page-tip {
  margin-top: 8px;
  font-size: 11px;
  color: #909399;
  line-height: 1.5;
}
.field-name-error {
  margin-top: 4px;
  font-size: 11px;
  color: #e53935;
}
.prop-section {
  margin-bottom: 18px;
}
.prop-section-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  padding-left: 8px;
  border-left: 3px solid #1976d2;
}
.grid-gen-btn {
  padding: 6px 16px;
  font-size: 13px;
  color: #fff;
  background: #1976d2;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
}
.grid-gen-btn:hover {
  background: #1565c0;
}
.prop-search {
  margin-bottom: 8px;
}
.all-tip {
  font-size: 11px;
  color: #909399;
  line-height: 1.5;
  margin-bottom: 10px;
}
.all-tip.warn {
  color: #e6a23c;
}
.prop-item {
  margin-bottom: 14px;
}
.prop-label {
  font-size: 12px;
  color: #606266;
  margin-bottom: 6px;
}
.full-label {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.prop-name {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  color: #303133;
  word-break: break-all;
}

/* CSS 样式标签页 */
.css-tip {
  font-size: 11px;
  color: #909399;
  margin-bottom: 12px;
  line-height: 1.4;
}
.css-unit-row {
  display: flex;
  gap: 6px;
  align-items: center;
}
.css-unit-input {
  flex: 1;
  min-width: 0;
}
.css-unit-select {
  width: 70px;
  height: 32px;
  box-sizing: border-box;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
}
.css-unit-select:focus {
  border-color: #1976d2;
  outline: none;
}
.css-unit-auto {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
  font-size: 13px;
  background: #f5f7fa;
}

.json-editor {
  width: 100%;
  min-height: 96px;
  box-sizing: border-box;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 6px 8px;
  font-family: Consolas, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
  color: #303133;
  resize: vertical;
  outline: none;
  transition: border-color 0.15s;
}
.json-editor:focus {
  border-color: #1976d2;
}
.json-error {
  margin-top: 4px;
  font-size: 11px;
  color: #e53935;
  word-break: break-all;
}

/* 编辑器按钮 */
.editor-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  padding: 2px 4px;
  margin-left: 4px;
  opacity: 0.6;
  transition: opacity 0.15s;
}
.editor-btn:hover {
  opacity: 1;
  color: #1976d2;
}

/* JSON 包装器（文本框 + 表格按钮） */
.json-wrapper {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.json-table-btn {
  border: 1px dashed #1976d2;
  background: #ecf5ff;
  color: #1976d2;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  align-self: flex-start;
}
.json-table-btn:hover {
  background: #1976d2;
  color: #fff;
  border-style: solid;
}

/* 列配置编辑器按钮 */
.columns-setter {
  display: flex;
}
.column-edit-btn {
  width: 100%;
  border: 1px dashed #1976d2;
  background: #ecf5ff;
  color: #1976d2;
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}
.column-edit-btn:hover {
  background: #1976d2;
  color: #fff;
  border-style: solid;
}

/* 文本域包装器 */
.textarea-wrapper {
  display: flex;
  flex-direction: column;
}

/* 属性标签中的可编辑属性名 */
.prop-name {
  font-size: 12px;
  color: #606266;
}

/* 子项管理标签页 */
.items-tip {
  font-size: 11px;
  color: #909399;
  margin-bottom: 12px;
  line-height: 1.4;
}
.item-row {
  margin-bottom: 12px;
  padding: 10px;
  background: #f9fafb;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}
.item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.item-select-btn {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 12px;
  color: #303133;
  text-align: left;
}
.item-select-btn:hover {
  border-color: #1976d2;
  color: #1976d2;
}
.item-select-btn.active {
  border-color: #1976d2;
  background: #ecf5ff;
  color: #1976d2;
}
.item-index {
  font-weight: 600;
  color: #909399;
  min-width: 24px;
}
.item-remove-btn {
  border: none;
  background: none;
  color: #c0c4cc;
  cursor: pointer;
  font-size: 14px;
  padding: 2px 6px;
  margin-left: 4px;
  border-radius: 3px;
}
.item-remove-btn:hover {
  color: #e53935;
  background: #fef0f0;
}
.item-props {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.item-prop-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.item-prop-label {
  font-size: 11px;
  color: #606266;
  white-space: nowrap;
  flex-shrink: 0;
}
.item-prop-input {
  width: 80px;
  height: 26px;
  padding: 0 6px;
  border: 1px solid #dcdfe6;
  border-radius: 3px;
  font-size: 12px;
  outline: none;
  text-align: right;
}
.item-prop-input:focus {
  border-color: #1976d2;
}

/* 布局区域卡片 */
.section-card {
  margin-bottom: 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
}
.section-card-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f5f7fa;
  font-size: 12px;
  font-weight: 600;
}
.section-card-title {
  color: #303133;
  font-family: Consolas, 'Courier New', monospace;
}
.section-always-on {
  font-size: 10px;
  color: #67c23a;
  font-weight: 400;
  margin-left: auto;
}
.section-card-body {
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 事件配置标签页 */
.events-tip {
  font-size: 11px;
  color: #909399;
  margin-bottom: 12px;
  line-height: 1.5;
}
.events-tip code {
  background: #f0f7ff;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 11px;
  color: #1976d2;
}
.event-item {
  margin-bottom: 16px;
}
.event-label {
  display: flex;
  align-items: center;
  gap: 6px;
}
.event-name {
  font-size: 12px;
  color: #303133;
  font-weight: 500;
}
.event-code-tag {
  font-family: Consolas, 'Courier New', monospace;
  font-size: 11px;
  color: #909399;
  background: #f5f7fa;
  padding: 1px 6px;
  border-radius: 3px;
}
.event-code-editor {
  min-height: 60px;
  font-size: 12px;
}

/* 验证规则标签页 */
.validation-rule-item {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 8px;
  background: #fafbff;
}
.validation-rule-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
  padding-bottom: 4px;
  border-bottom: 1px solid #f0f0f0;
}
.validation-rule-type {
  font-size: 12px;
  font-weight: 600;
  color: #1976d2;
  background: #ecf5ff;
  padding: 2px 8px;
  border-radius: 3px;
}
.validation-remove-btn {
  border: none;
  background: none;
  cursor: pointer;
  color: #f56c6c;
  font-size: 14px;
  padding: 0 4px;
}
.validation-remove-btn:hover {
  color: #e53935;
}
.page-open-setter {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}
.page-open-select-btn {
  width: 100%;
  box-sizing: border-box;
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
  text-align: left;
}
.page-open-select-btn:hover {
  background: #f3f4f6;
}
.page-open-clear-btn {
  border: none;
  background: none;
  color: #ef4444;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
  align-self: flex-start;
}
.page-open-select,
.page-open-mode,
.page-open-title,
.page-open-params {
  width: 100%;
  box-sizing: border-box;
  padding: 4px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
}
.page-open-params {
  resize: vertical;
  font-family: monospace;
}
.api-select-setter {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.api-select-row {
  display: flex;
  gap: 4px;
  align-items: stretch;
}
.api-select-btn {
  flex: 1;
  min-width: 0;
  box-sizing: border-box;
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
  text-align: left;
}
.api-select-btn:hover {
  background: #f3f4f6;
}
.api-select-view-btn {
  border: 1px solid #d1d5db;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  padding: 4px 8px;
  font-size: 14px;
  flex-shrink: 0;
  line-height: 1;
}
.api-select-view-btn:hover {
  background: #e0f2fe;
  border-color: #0284c7;
}
.api-select-clear-btn {
  border: none;
  background: none;
  color: #ef4444;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
  align-self: flex-start;
}
.api-detail-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 21000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.api-detail-dialog {
  background: #fff;
  border-radius: 8px;
  width: 420px;
  max-width: 90vw;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}
.api-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  font-size: 15px;
  font-weight: 600;
}
.api-detail-close {
  border: none;
  background: none;
  font-size: 20px;
  cursor: pointer;
  color: #6b7280;
  line-height: 1;
}
.api-detail-close:hover {
  color: #1f2937;
}
.api-detail-body {
  padding: 16px;
}
.api-detail-row {
  display: flex;
  padding: 8px 0;
  border-bottom: 1px solid #f3f4f6;
}
.api-detail-row:last-child {
  border-bottom: none;
}
.api-detail-row label {
  width: 80px;
  flex-shrink: 0;
  color: #6b7280;
  font-size: 13px;
}
.api-detail-row span {
  flex: 1;
  color: #1f2937;
  font-size: 13px;
  word-break: break-all;
}
</style>
