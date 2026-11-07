<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'

import TextEditorDialog from './TextEditorDialog.vue'
import EventCodeDialog from './EventCodeDialog.vue'
import MobileEventDialog from './MobileEventDialog.vue'
import JsonTableEditorDialog from './JsonTableEditorDialog.vue'
import ColumnEditorDialog from './ColumnEditorDialog.vue'
import ToolbarItemsEditorDialog from './ToolbarItemsEditorDialog.vue'
import SummaryEditorDialog from './SummaryEditorDialog.vue'
import PageSelectDialog from './PageSelectDialog.vue'
import FieldSelectDialog from './FieldSelectDialog.vue'
import DependFilterDialog, { type DependFilterCondition } from './DependFilterDialog.vue'
import ApiSelectDialog from './ApiSelectDialog.vue'
import ThemeSelectDialog from './ThemeSelectDialog.vue'
import FieldEventRuleDialog from './FieldEventRuleDialog.vue'
import { useDialogDragAndMax } from './useDialogDragAndMax'
import DialogMaxButton from './DialogMaxButton.vue'


import {
  ElMessage,
  ElSelect,
  ElOption,
  ElInput,
  ElInputNumber,
  ElSwitch,
  ElColorPicker,
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElDatePicker,
} from 'element-plus'
import { listWorkflows } from '@/store/workflow'

import { getConfig, getDxAllPropSetters } from '@/materials'
import { MATERIAL_TYPES } from '@/materials/dx-materials'
import {
  VALIDATABLE_TYPES,
  VALIDATION_RULES,
  getRuleMeta,
  type ValidationRuleConfig,
  type ValidationType,
} from '@/materials/dx-validation'
import { getEvents } from '@/materials/dx-events'

const DX_ICONS: string[] = [
  'plus', 'add', 'minus', 'remove', 'trash', 'edit', 'save', 'revert', 'close', 'check',
  'checkmarkcircle', 'clear', 'refresh', 'export', 'download', 'upload', 'print', 'copy', 'paste', 'cut',
  'undo', 'redo', 'filter', 'find', 'search', 'group', 'column', 'card', 'chart', 'clock',
  'user', 'info', 'help', 'warning', 'favorites', 'menu', 'more', 'message', 'chat', 'comment',
  'link', 'image', 'photo', 'video', 'doc', 'folder', 'file', 'csv', 'attach', 'send',
  'share', 'bookmark', 'map', 'home', 'globe', 'lock', 'unlock', 'key', 'expand', 'collapse',
  'back', 'right', 'box', 'cart', 'bell', 'coffee', 'like', 'pin', 'diagram', 'codeblock',
  'arrowup', 'arrowdown', 'arrowleft', 'arrowright', 'chevrondown', 'chevronup', 'chevronleft', 'chevronright',
  'bold', 'alignleft', 'alignright', 'aligncenter', 'alignjustify', 'bulletlist',
]
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
  updateNodeProperty,
  updatePageCss,
  updateNodeCss,
  selectSingleNode,
  collectAllNodes,
  addNode,
  removeNode,
  createNode,
  pushHistory,
  isPropOverridden,
  restoreProp,
  normalizeIdentifier,
  fetchDbType,
} from '@/store/designer'
import type { PropSetterConfig, NodeCss, Theme } from '@/types/schema'
import { dxPropMeta, type DxPropMeta } from '@/materials/dx-prop-meta'
import { translatePropName } from '@/materials/prop-labels'

import { listApiServices, listDataDicts } from '@/store/api-store'
import type { ApiService } from '@/api/registry'
import type { DataDict } from '@/types/dict'
import NumberRuleDialog from './NumberRuleDialog.vue'
import { t, loadModule } from '@/store/lang'
import { loadThemes } from '@/composables/useTheme'
import { listBaseDataPages, loadPage, type SavedPageMeta } from '@/store/persistence'
import type { ComponentSchema } from '@/types/schema'

const themeDialogVisible = ref(false)
const themeList = ref<Theme[]>([])
const currentThemeName = ref('')

/** 审批流列表（单据页面绑定审批流用） */
const workflowOptions = ref<{ id: string; name: string }[]>([])
function onWorkflowChange(e: { value?: string | null }): void {
  designerStore.workflowId = e.value ?? undefined
}
onMounted(async () => {
  fetchDbType()
  try {
    const wfs = await listWorkflows()
    workflowOptions.value = wfs
      .filter((w) => w.enabled)
      .map((w) => ({ id: w.id, name: w.name }))
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : String(err))
    workflowOptions.value = []
  }
})

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

/** FE-S10: 所有字段名列表（供计算表达式触发字段多选用） */
const allFieldNames = computed(() => {
  return collectAllNodes(designerStore.schema)
    .map((n) => n.fieldName)
    .filter((f): f is string => !!f)
})

/** 日期/时间控件类型集合 */
const DATE_CONTROL_TYPES = new Set(['date-box', 'date-range-box', 'calendar'])

/** FE-S10 P2: 表单控件字段列表（仅 category='表单'，供编号规则编码来源选择） */
const formFieldNames = computed(() => {
  return collectAllNodes(designerStore.schema)
    .filter((n) => {
      if (!n.fieldName) return false
      const c = getConfig(n.type)
      return c?.category === '表单'
    })
    .map((n) => n.fieldName as string)
})

/** FE-S10 P2: 日期/时间类型表单字段列表（供业务对象日期选择） */
const dateFieldNames = computed(() => {
  return collectAllNodes(designerStore.schema)
    .filter((n) => {
      if (!n.fieldName) return false
      return DATE_CONTROL_TYPES.has(n.type)
    })
    .map((n) => n.fieldName as string)
})

/** FE-S10 P2: 非日期/时间类型表单字段列表（供业务对象字段选择） */
const nonDateFieldNames = computed(() => {
  return collectAllNodes(designerStore.schema)
    .filter((n) => {
      if (!n.fieldName) return false
      const c = getConfig(n.type)
      if (c?.category !== '表单') return false
      return !DATE_CONTROL_TYPES.has(n.type)
    })
    .map((n) => n.fieldName as string)
})

/** FE-S10 P2: 数据字典列表（供引用字典选择） */
const dataDictList = ref<DataDict[]>([])
const showNumberRuleDialog = ref(false)
const showDictSelectDialog = ref(false)
const dictSelectSearch = ref('')


/** FE-S10 P2: 当前节点的编号规则表达式 */
const currentNodeNumberExpr = computed(() => {
  return (selectedNode.value?.props.defaultValueExpr as string) || ''
})

/** FE-S10 P2: 编号规则预览文本（解析 JSON 配置生成可读预览） */
const currentNodeNumberPreview = computed(() => {
  const expr = currentNodeNumberExpr.value
  if (!expr) return ''
  try {
    const config = JSON.parse(expr) as { segments: Array<Record<string, unknown>> }
    if (!config.segments || !Array.isArray(config.segments)) return expr
    const parts: string[] = []
    for (const seg of config.segments) {
      let part = ''
      const type = seg.type as string
      switch (type) {
        case 'constant':
          part = (seg.value as string) || ''
          break
        case 'random':
          part = 'X'.repeat((seg.length as number) || 8)
          break
        case 'sysDate':
        case 'bizDate':
          part = (seg.dateFormat as string) || 'yyyyMMdd'
          break
        case 'bizField':
          part = (seg.fieldName as string) || 'field'
          break
        case 'sequence': {
          const len = (seg.seqLength as number) || 4
          const start = (seg.seqStart as number) || 1
          const zeroPad = seg.seqZeroPad !== false
          part = zeroPad ? String(start).padStart(len, '0') : String(start)
          break
        }
      }
      parts.push(part)
      if (seg.separator) parts.push(seg.separator as string)
    }
    return parts.join('')
  } catch {
    return expr
  }
})

/** FE-S10 P2: 当前节点引用的字典编码 */
const currentNodeDictCode = computed(() => {
  return (selectedNode.value?.props.dictCode as string) || ''
})

/** FE-S3: 当前节点被覆盖的属性列表 */
const overriddenProps = computed(() => {
  const node = selectedNode.value
  if (!node?.overrideProps) return []
  return Object.keys(node.overrideProps)
})

/** FE-S3: 恢复单个覆盖属性 */
function onRestoreProp(prop: string) {
  const node = getSelectedNode()
  if (!node) return
  restoreProp(node.id, prop)
}

/** FE-S3: 恢复所有覆盖属性 */
function onRestoreAllProps() {
  const node = getSelectedNode()
  if (!node) return
  for (const prop of overriddenProps.value) {
    restoreProp(node.id, prop)
  }
}

async function loadDataDicts() {
  try {
    dataDictList.value = await listDataDicts()
  } catch {
    dataDictList.value = []
  }
}


/** FE-S10 P2: 从弹框选择字典 */
function onDictRowSelect(dict: DataDict) {
  const node = getSelectedNode()
  if (!node) return
  updateProps(node.id, 'dictCode', dict.code)
  showDictSelectDialog.value = false
}

/** FE-S10 P2: 清除引用字典 */
function onDictClear() {
  const node = getSelectedNode()
  if (!node) return
  updateProps(node.id, 'dictCode', undefined)
}

/** FE-S10 P2: 过滤后的字典列表 */
const filteredDictList = computed(() => {
  const kw = dictSelectSearch.value.trim().toLowerCase()
  if (!kw) return dataDictList.value
  return dataDictList.value.filter(d =>
    d.code.toLowerCase().includes(kw) || (d.name || '').toLowerCase().includes(kw)
  )
})

/** FE-S10 P2: 当前引用字典名称 */
const currentNodeDictName = computed(() => {
  const code = currentNodeDictCode.value
  if (!code) return ''
  const d = dataDictList.value.find(x => x.code === code)
  return d ? `${d.name} (${d.code})` : code
})

/** FE-S10 P2: 编号规则保存 */
function onSaveNumberRule(expr: string) {
  const node = getSelectedNode()
  if (!node) return
  updateProps(node.id, 'defaultValueExpr', expr)
}

// FE-S10 P2: 初始化加载数据字典列表
loadDataDicts()

/** 当前控件是否为 dx-base-data 基础资料控件 */
const isBaseDataControl = computed(() => selectedNode.value?.type === 'base-data')

/** 基础资料页面列表（pageType='basedata'） */
const baseDataPages = ref<SavedPageMeta[]>([])
const refPageFields = ref<{ dbField: string; fieldName: string; displayText: string }[]>([])
const refPagePrimaryKey = ref('')

/** 基础资料页面选择弹窗 & 字段选择弹窗 */
const refPageSelectVisible = ref(false)
const refPageSelectId = ref('')
const fieldSelectVisible = ref(false)
const fieldSelectId = ref('')
const valueFieldSelectVisible = ref(false)
const valueFieldSelectId = ref('')
const codeFieldSelectVisible = ref(false)
const codeFieldSelectId = ref('')
/** 被关联基础资料页面中启用编码规则（编号规则）的字段，用作编码字段默认值 */
const refPageCodeRuleField = ref('')

/** 加载基础资料页面列表 */
async function loadBaseDataPages() {
  try {
    baseDataPages.value = await listBaseDataPages()
  } catch {
    baseDataPages.value = []
  }
}

/** 加载指定基础资料页面的字段列表（优先取表格页面配置的列字段，回退表单节点） */
async function loadRefPageFields(refPageId: string) {
  if (!refPageId) {
    refPageFields.value = []
    refPagePrimaryKey.value = ''
    refPageCodeRuleField.value = ''
    return
  }
  try {
    const page = await loadPage(refPageId)
    if (!page) {
      refPageFields.value = []
      refPagePrimaryKey.value = ''
      refPageCodeRuleField.value = ''
      return
    }
    refPagePrimaryKey.value = page.primaryKeyField || 'id'
    const fields: { dbField: string; fieldName: string; displayText: string }[] = []

    // 优先从表格模式（bizSchemas.table）的 dx-data-grid columns 获取
    // 不过滤 visible=false 的列：过滤条件需要能选所有表字段（包括隐藏的主键/标识字段）
    const tableSchema = page.bizSchemas?.table?.[0]
    const tableProps = (tableSchema?.props ?? {}) as Record<string, unknown>
    if (Array.isArray(tableProps.columns)) {
      const columns = tableProps.columns as Record<string, unknown>[]
      for (const c of columns) {
        const dataField = String(c.dataField || '')
        if (!dataField) continue
        // 排除 baseDataRef 导入的关联字段（带点号），不是表本身字段
        if (dataField.includes('.')) continue
        const caption = String(c.caption || dataField)
        fields.push({
          dbField: dataField,
          fieldName: caption,
          displayText: `${caption} (${dataField})`,
        })
      }
    }

    // 同时从表单模式节点收集字段（基础资料控件绑定的标识字段在表单中定义）
    // 注意：root 可能存的是 lastBizMode 对应的节点（如表格模式），表单控件在 bizSchemas.form 中
    const collect = (nodes: ComponentSchema[]) => {
      for (const node of nodes) {
        if (node.dbField && !node.dbField.includes('.')) {
          if (!fields.some((f) => f.dbField === node.dbField)) {
            const label = (node.props?.label as string) || ''
            const fieldName = node.fieldName || node.dbField
            fields.push({
              dbField: node.dbField,
              fieldName,
              displayText: label ? `${label} (${fieldName})` : fieldName,
            })
          }
        }
        // 检测启用编码规则（编号规则表达式）的字段，作为编码字段默认值
        if (
          node.dbField &&
          !node.dbField.includes('.') &&
          !refPageCodeRuleField.value &&
          String((node.props as Record<string, unknown> | undefined)?.defaultValueExpr ?? '') !== ''
        ) {
          refPageCodeRuleField.value = node.dbField
        }
        if (node.children?.length) collect(node.children)
      }
    }
    refPageCodeRuleField.value = ''
    collect(page.root || [])
    if (page.bizSchemas?.form?.length) collect(page.bizSchemas.form)

    // 确保基础资料标识（值字段/主键）在列表中，依赖过滤条件需要能选它
    const pk = refPagePrimaryKey.value
    if (pk && !fields.some((f) => f.dbField === pk)) {
      fields.unshift({ dbField: pk, fieldName: pk, displayText: pk })
    }

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

/** 打开基础资料页面选择弹窗 */
function openRefPageSelectDialog() {
  refPageSelectId.value = String(getEditable('refPageId') ?? '')
  refPageSelectVisible.value = true
}

/** 基础资料页面选择弹窗确认回调 */
function onRefPageSelectConfirm(pageId: string) {
  const node = getSelectedNode()
  if (!node) return
  setEditable('refPageId', pageId)
  updateProps(node.id, 'refPageId', pageId)
  node.refPageId = pageId
  loadRefPageFields(pageId).then(() => {
    const pk = refPagePrimaryKey.value
    if (pk) {
      setEditable('valueField', pk)
      updateProps(node.id, 'valueField', pk)
    }
    // 被关联页面存在启用编码规则的字段时，编码字段默认选中该字段
    const codeRuleField = refPageCodeRuleField.value
    if (codeRuleField && !String(node.props.codeField ?? '')) {
      setEditable('codeField', codeRuleField)
      updateProps(node.id, 'codeField', codeRuleField)
    }
  })
}

/** 打开显示字段选择弹窗 */
function openFieldSelectDialog() {
  fieldSelectId.value = String(getEditable('displayField') ?? '')
  fieldSelectVisible.value = true
}

/** 显示字段选择弹窗确认回调 */
function onFieldSelectConfirm(dbField: string) {
  const node = getSelectedNode()
  if (!node) return
  setEditable('displayField', dbField)
  updateProps(node.id, 'displayField', dbField)
}

/** 打开值字段选择弹窗 */
function openValueFieldSelectDialog() {
  valueFieldSelectId.value = String(getEditable('valueField') ?? '')
  valueFieldSelectVisible.value = true
}

/** 值字段选择弹窗确认回调 */
function onValueFieldSelectConfirm(dbField: string) {
  const node = getSelectedNode()
  if (!node) return
  setEditable('valueField', dbField)
  updateProps(node.id, 'valueField', dbField)
}

/** dx-base-data：选择编码字段 */
function onCodeFieldSelect(e: { value?: unknown }) {
  const node = getSelectedNode()
  if (!node) return
  const val = String(e.value ?? '')
  setEditable('codeField', val)
  updateProps(node.id, 'codeField', val || undefined)
}

/** 打开编码字段选择弹窗 */
function openCodeFieldSelectDialog() {
  codeFieldSelectId.value = String(getEditable('codeField') ?? '')
  codeFieldSelectVisible.value = true
}

/** 编码字段选择弹窗确认回调（单选） */
function onCodeFieldSelectConfirm(dbField: string) {
  const node = getSelectedNode()
  if (!node) return
  setEditable('codeField', dbField)
  updateProps(node.id, 'codeField', dbField)
}

/** 已选编码字段文本（用于按钮显示） */
const codeFieldText = computed(() => {
  const field = String(getEditable('codeField') ?? '')
  if (!field) return ''
  return refPageFields.value.find((p) => p.dbField === field)?.displayText || field
})

/** 已选基础资料页面名称（用于按钮显示） */
const refPageDisplayName = computed(() => {
  const id = String(getEditable('refPageId') ?? '')
  if (!id) return ''
  return baseDataPages.value.find((p) => p.pageId === id)?.pageName || id
})

/** 已选显示字段文本（用于按钮显示） */
const displayFieldText = computed(() => {
  const field = String(getEditable('displayField') ?? '')
  if (!field) return ''
  const parts = field.split(',').map((f) => f.trim()).filter(Boolean)
  const texts = parts.map((f) => refPageFields.value.find((p) => p.dbField === f)?.displayText || f)
  return texts.join(', ')
})

/** 已选值字段文本（用于按钮显示） */
const valueFieldText = computed(() => {
  const field = String(getEditable('valueField') ?? '')
  if (!field) return ''
  return refPageFields.value.find((f) => f.dbField === field)?.displayText || field
})

/** 级联依赖字段变更 */
function onDependFieldChange(e: Event): void {
  const n = getSelectedNode()
  if (n) updateProps(n.id, 'dependField', (e.target as HTMLSelectElement).value || undefined)
}
function onDependFilterFieldChange(e: Event): void {
  const n = getSelectedNode()
  if (n) updateProps(n.id, 'dependFilterField', (e.target as HTMLSelectElement).value || undefined)
}
function onDependOperatorChange(e: Event): void {
  const n = getSelectedNode()
  if (n) updateProps(n.id, 'dependOperator', (e.target as HTMLSelectElement).value || '=')
}

const dependFilterDialogVisible = ref(false)

function openDependFilterDialog(): void {
  dependFilterDialogVisible.value = true
}

function onDependFilterSave(conditions: DependFilterCondition[]): void {
  const n = getSelectedNode()
  if (n) updateProps(n.id, 'dependFilters', conditions.length ? conditions : undefined)
}

const dependFilterCount = computed(() => {
  const filters = selectedNode.value?.props?.dependFilters as DependFilterCondition[] | undefined
  return filters?.length ?? 0
})

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
  pushHistory()
  node.children = []
  for (let i = 0; i < rows * cols; i++) {
    const col = createNode('el-col')
    if (col) {
      Object.assign(col.props, {
        span: 0,
        flexEqual: true,
        flexCols: cols,
        offset: 0,
        padding: '0',
        background: '#ffffff',
      })
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
  const raw = (e.target as HTMLInputElement).value
  const value = normalizeIdentifier(raw)
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

function onTableNameInput(e: Event): void {
  const raw = (e.target as HTMLInputElement).value
  designerStore.tableName = normalizeIdentifier(raw)
}

const cfg = computed(() =>
  selectedNode.value ? getConfig(selectedNode.value.type) : undefined
)
const gridEditingMode = computed(() => {
  const node = selectedNode.value
  if (!node || node.type !== 'data-grid') return 'none'
  const editing = node.props?.editing as Record<string, unknown> | undefined
  return String(editing?.mode ?? 'none')
})

/** 当前活动标签页 */
const activeTab = ref<'basic' | 'css' | 'layout' | 'events' | 'validation'>(
  'basic'
)

/** 字段变更事件配置弹窗显隐（FE-S1，页面级配置入口） */
const showFieldEventRuleDialog = ref(false)

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

const filterConditionsDialogVisible = ref(false)
const filterConditionsDialogProp = ref('')
const filterConditionsData = ref<any[]>([])
const filterCondMultiValueVisible = ref(false)
const filterCondMultiValueRows = ref<string[]>([])
let _filterCondMultiTarget: any = null

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
  if (node && node.type === 'data-grid' && prop === 'dataSource') {
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

const optionsDialogVisible = ref(false)
const optionsDialogProp = ref('')
const optionsDialogValue = ref('')

/** 选项编辑器：锁定 value/text 两列 */
function openOptionsDialog(prop: string, currentValue: unknown) {
  optionsDialogProp.value = prop
  if (typeof currentValue === 'string') {
    optionsDialogValue.value = currentValue
  } else {
    try {
      optionsDialogValue.value = JSON.stringify(currentValue ?? [], null, 2)
    } catch {
      optionsDialogValue.value = String(currentValue ?? '')
    }
  }
  optionsDialogVisible.value = true
}

function onOptionsDialogConfirm(value: string) {
  if (!selectedNode.value) return
  try {
    const parsed = JSON.parse(value)
    setEditable(optionsDialogProp.value, parsed)
    updateProps(selectedNode.value.id, optionsDialogProp.value, parsed)
  } catch {
    setEditable(optionsDialogProp.value, value)
    updateProps(selectedNode.value.id, optionsDialogProp.value, value)
  }
  optionsDialogVisible.value = false
}

/** itemsSelect 的下拉选项：从当前节点 items 生成（兼容字符串数组和对象数组） */
const selectItemsOptions = computed(() => {
  const node = selectedNode.value
  if (!node) return []
  const items = node.props.items
  if (!Array.isArray(items)) return []
  return items.map((item: any) => {
    if (typeof item === 'object' && item !== null) {
      return { value: item.value, text: String(item.text ?? item.value ?? '') }
    }
    return { value: item, text: String(item) }
  })
})

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

function openFilterConditionsDialog(prop: string, currentValue: unknown) {
  filterConditionsDialogProp.value = prop
  if (Array.isArray(currentValue)) {
    filterConditionsData.value = currentValue.map((c: any) => ({ field: c.field || '', operator: c.operator || '=', value: c.value || '', value2: c.value2 || '' }))
  } else if (typeof currentValue === 'string') {
    try { filterConditionsData.value = JSON.parse(currentValue).map((c: any) => ({ field: c.field || '', operator: c.operator || '=', value: c.value || '', value2: c.value2 || '' })) } catch { filterConditionsData.value = [] }
  } else {
    filterConditionsData.value = []
  }
  filterConditionsDialogVisible.value = true
}

function onFilterConditionsDialogConfirm() {
  if (!selectedNode.value) return
  const val = filterConditionsData.value.filter((c: any) => {
    if (!c.field) return false
    if (c.value === '' || c.value == null) return false
    if (c.operator === 'between' && (c.value2 === '' || c.value2 == null)) return false
    return true
  })
  setEditable(filterConditionsDialogProp.value, val)
  updateProps(selectedNode.value.id, filterConditionsDialogProp.value, val)
  filterConditionsDialogVisible.value = false
}

function openFilterCondMultiValue(cond: any) {
  _filterCondMultiTarget = cond
  const existing = cond.value ? String(cond.value).split('\n').map((v: string) => v.trim()).filter((v: string) => v !== '') : []
  const rows = [...existing]
  while (rows.length < 100) rows.push('')
  filterCondMultiValueRows.value = rows
  filterCondMultiValueVisible.value = true
}

function confirmFilterCondMultiValue() {
  const values = filterCondMultiValueRows.value.map((v: string) => v.trim()).filter((v: string) => v !== '')
  if (_filterCondMultiTarget) {
    _filterCondMultiTarget.value = values.join('\n')
  }
  filterCondMultiValueVisible.value = false
}

const FC_OP_LABELS: Record<string, string> = {
  '=': '等于', '<>': '不等于', '>': '大于', '>=': '大于等于', '<': '小于', '<=': '小于等于',
  'between': '之间', 'contains': '包含', 'notcontains': '不包含', 'startswith': '开头', 'endswith': '结尾',
}

function getFcColumns(): any[] {
  return (selectedNode.value?.props?.columns as any[] | undefined) || []
}

function getFcColType(field: string): string {
  const col = getFcColumns().find(c => c.dataField === field)
  return col?.dataType || 'string'
}

function getFcColLookup(field: string): any | null {
  const col = getFcColumns().find(c => c.dataField === field)
  return col?.lookup || null
}

function getFcOperatorsForField(field: string): Array<{ label: string; value: string }> {
  const lookup = getFcColLookup(field)
  if (lookup) {
    return [{ label: '等于', value: '=' }, { label: '不等于', value: '<>' }]
  }
  const dt = getFcColType(field)
  switch (dt) {
    case 'number':
      return ['=', '<>', '>', '>=', '<', '<=', 'between'].map(v => ({ label: FC_OP_LABELS[v], value: v }))
    case 'date':
    case 'datetime':
      return ['=', '<>', '>', '>=', '<', '<=', 'between'].map(v => ({ label: FC_OP_LABELS[v], value: v }))
    case 'boolean':
      return [{ label: '等于', value: '=' }, { label: '不等于', value: '<>' }]
    default:
      return ['contains', 'notcontains', '=', '<>', 'startswith', 'endswith'].map(v => ({ label: FC_OP_LABELS[v], value: v }))
  }
}

function isFcMultiValueField(cond: any): boolean {
  if (cond.operator !== '=' && cond.operator !== '<>') return false
  if (getFcColLookup(cond.field)) return false
  const dt = getFcColType(cond.field)
  if (dt === 'date' || dt === 'datetime' || dt === 'boolean') return false
  return true
}

function isFcMultiValueReadonly(cond: any): boolean {
  return cond.value != null && String(cond.value).includes('\n')
}

function onFcFieldChange(cond: any) {
  const ops = getFcOperatorsForField(cond.field)
  if (!ops.some(o => o.value === cond.operator)) {
    cond.operator = ops[0]?.value || '='
  }
  cond.value = ''
  cond.value2 = ''
}

function onFcOperatorChange(cond: any) {
  cond.value = ''
  cond.value2 = ''
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
  bizMode: string
  width: number | string | undefined
  height: number | string | undefined
} {
  const val = getEditable(prop) as Record<string, unknown> | undefined
  return {
    pageId: (val?.pageId as string) || '',
    params: val?.params ? JSON.stringify(val.params) : '',
    mode: (val?.mode as string) || 'popup',
    title: (val?.title as string) || '',
    bizMode: (val?.bizMode as string) || 'form',
    width: typeof val?.width === 'number' || typeof val?.width === 'string' ? val.width as number | string : undefined,
    height: typeof val?.height === 'number' || typeof val?.height === 'string' ? val.height as number | string : undefined,
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

/** 解析尺寸单位（px 或 %） */
function parseSizeUnit(val: unknown): 'px' | '%' {
  if (val == null || val === '') return '%'
  return String(val).endsWith('%') ? '%' : 'px'
}

/** 解析尺寸数值 */
function parseSizeValue(val: unknown): number | '' {
  if (val == null) return ''
  const s = String(val)
  const stripped = s.endsWith('%') ? s.slice(0, -1) : (s.endsWith('px') ? s.slice(0, -2) : s)
  const n = Number(stripped)
  return Number.isFinite(n) ? n : ''
}

/** 设置 openPage 宽/高数值（保留当前单位） */
function setPageOpenSize(prop: string, field: 'width' | 'height', val: string): void {
  const cur = getPageOpenConfig(prop)[field]
  const unit = parseSizeUnit(cur)
  const n = Number(val)
  setPageOpenField(prop, field, Number.isFinite(n) && n > 0 ? (unit === '%' ? `${n}%` : `${n}px`) : undefined)
}

/** 设置 openPage 宽/高单位（保留当前数值） */
function setPageOpenSizeUnit(prop: string, field: 'width' | 'height', unit: 'px' | '%'): void {
  const cur = getPageOpenConfig(prop)[field]
  const v = parseSizeValue(cur)
  if (v === '') return
  setPageOpenField(prop, field, unit === '%' ? `${v}%` : `${v}px`)
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

/** 明细表上级来源选择 */
const parentPageSelectVisible = ref(false)
const parentPageSelectProp = ref('')
const parentPageSelectValue = ref('')

function openParentPageSelect(prop: string) {
  parentPageSelectProp.value = prop
  parentPageSelectValue.value = String(getEditable(prop) ?? '')
  parentPageSelectVisible.value = true
}

function onParentPageSelectConfirm(pageId: string) {
  setEditable(parentPageSelectProp.value, pageId)
  updateProps(selectedNode.value!.id, parentPageSelectProp.value, pageId)
  parentPageSelectVisible.value = false
}

/** 当前 schema 中其他 data-grid 的选项（用于 parentType=table 的下拉选择） */
const otherDetailTableOptions = computed(() => {
  if (!selectedNode.value) return []
  return collectAllNodes(designerStore.schema)
    .filter((n: any) => n.type === 'data-grid' && n.id !== selectedNode.value!.id)
    .map((n: any) => {
      const tn = (n.props?.tableName as string) || ''
      const comment = (n.props?.tableComment as string) || ''
      const fieldName = (n.fieldName as string) || ''
      const label = comment || fieldName || tn || n.id
      const suffix = tn ? ` (${tn})` : ' (未配表名)'
      return { label: label + suffix, value: n.id, hasTableName: !!tn }
    })
})

/** 显式追踪所有 data-grid 的 keyExpr，确保修改任一表格主键时触发重算 */
const allGridKeyExprs = computed(() => {
  void designerStore.schemaRevision
  return collectAllNodes(designerStore.schema)
    .filter((n: any) => n.type === 'data-grid')
    .map((n: any) => ({ id: n.id as string, keyExpr: (n.props?.keyExpr as string) || '' }))
})

/** 解析上级主键字段（外键字段） */
const resolvedParentPkFieldSync = computed(() => {
  const pt = getEditable('parentType') as string
  const pr = (getEditable('parentRef') as string) || ''
  if (pt === 'table' && pr.trim()) {
    const item = allGridKeyExprs.value.find((it) => it.id === pr.trim())
    return item?.keyExpr || ''
  }
  return designerStore.primaryKeyField || ''
})

/** 最终展示的上级主键字段（外键） */
const displayParentPkField = computed(() => resolvedParentPkFieldSync.value)

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
const { isMaximized: apiDetailMax, isDragging: apiDetailDragging, dragStyle: apiDetailDragStyle, onHeaderMouseDown: onApiDetailHeaderDown, toggleMaximize: toggleApiDetailMax, resetDialog: resetApiDetailDrag } = useDialogDragAndMax()
watch(() => apiDetailVisible.value, (v) => { if (!v) resetApiDetailDrag() })

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
  const raw = (e.target as HTMLInputElement).value.trim()
  const value = normalizeIdentifier(raw)
  fieldNameInput.value = value
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
    if (node) {
      updateProps(node.id, setter.prop, e.value)
      if (setter.prop === 'required') {
        const required = !!e.value
        const rules = Array.isArray(node.props.validationRules) ? [...(node.props.validationRules as any[])] : []
        const idx = rules.findIndex((r: any) => r.type === 'required')
        if (required && idx === -1) {
          rules.unshift({ type: 'required', message: t('此字段为必填项') })
        } else if (!required && idx >= 0) {
          rules.splice(idx, 1)
        }
        updateProps(node.id, 'validationRules', rules.length ? rules : undefined)
      }
    }
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
  // 对物料控件，使用完整属性列表
  if (MATERIAL_TYPES.has(node.type)) {
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
interface FormFieldOption {
  fieldName: string
  dbField?: string
  title: string
  children?: { fieldName: string; dbField: string; title: string }[]
  refPageId?: string
  valueField?: string
  displayField?: string
  controlType?: string
  selectItems?: { value: string; text: string }[]
}
const formFieldOptions = ref<FormFieldOption[]>([])

async function collectBaseDataChildren(
  pageId: string,
  visited: Set<string>,
): Promise<{ fieldName: string; dbField: string; title: string }[]> {
  if (visited.has(pageId)) return []
  visited.add(pageId)
  const page = await loadPage(pageId)
  if (!page) return []
  const children: { fieldName: string; dbField: string; title: string }[] = []
  const tableSchema = page.bizSchemas?.table?.[0]
  const tableProps = (tableSchema?.props ?? {}) as Record<string, unknown>
  if (Array.isArray(tableProps.columns)) {
    for (const col of tableProps.columns as Record<string, unknown>[]) {
      if (col.visible === false) continue
      const dataField = String(col.dataField || '')
      if (!dataField) continue
      children.push({
        fieldName: dataField,
        dbField: dataField,
        title: String(col.caption || dataField),
      })
    }
  }
  const formNodes = collectAllNodes(page.bizSchemas?.form || [])
  for (const fn of formNodes) {
    if (fn.type === 'base-data' && fn.refPageId && fn.fieldName) {
      const nested = await collectBaseDataChildren(fn.refPageId, visited)
      for (const nc of nested) {
        const prefixed = `${fn.fieldName}.${nc.fieldName}`
        if (!children.some((c) => c.fieldName === prefixed)) {
          children.push({ fieldName: prefixed, dbField: prefixed, title: nc.title })
        }
      }
    }
  }
  if (!children.length) {
    const collect = (nodes: ComponentSchema[]) => {
      for (const node of nodes) {
        if (node.dbField) {
          children.push({
            fieldName: node.dbField,
            dbField: node.dbField,
            title: (node.props?.label as string) || node.fieldName || node.dbField,
          })
        }
        if (node.children?.length) collect(node.children)
      }
    }
    collect(page.root || [])
  }
  return children
}

watch(
  () => designerStore.bizSchemas.form,
  async () => {
    const formSchema = designerStore.bizSchemas.form
    const nodes = collectAllNodes(formSchema)
    const result: FormFieldOption[] = []
    for (const n of nodes) {
      if (!n.fieldName) continue
      const c = getConfig(n.type)
      if (c?.category !== '表单') continue
      const cfg = getConfig(n.type)
      const item: FormFieldOption = {
        fieldName: n.fieldName as string,
        dbField: n.dbField ?? '',
        title: (n.props.label as string) || cfg?.title || n.type,
      }
      if (n.type === 'select-box') {
        item.controlType = 'select-box'
        const items = n.props.items as { value: string; text: string }[] | undefined
        if (Array.isArray(items)) {
          item.selectItems = items
        }
      }
      if (n.type === 'date-box' || n.type === 'date-range-box' || n.type === 'number-box' || n.type === 'check-box' || n.type === 'switch') {
        item.controlType = n.type
      }
      if (n.type === 'base-data' && n.refPageId) {
        item.refPageId = n.refPageId
        item.valueField = (n.props.valueField as string) || 'id'
        item.displayField = (n.props.displayField as string) || ''
        try {
          const children = await collectBaseDataChildren(n.refPageId, new Set())
          if (children.length) item.children = children
        } catch { /* ignore */ }
      }
      result.push(item)
    }
    formFieldOptions.value = result
  },
  { immediate: true, deep: true },
)

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
    if (equals !== undefined) {
      if (typeof equals === 'boolean') return Boolean(condVal) === equals
      return String(condVal ?? '') === String(equals)
    }
    const notEquals = setter.visibleIf.notEquals
    if (notEquals !== undefined) {
      if (typeof notEquals === 'boolean') return Boolean(condVal) !== notEquals
      return String(condVal ?? '') !== String(notEquals)
    }
    return true
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
      designerStore.bizMode === 'table'
    ) {
      if (setter.prop === 'dataSourceType') return false
      if (setter.group === '明细表') return false
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

/** 当前组件是否有分组属性 */
const hasGroups = computed(() =>
  filteredCommonSetters.value.some((s) => s.group),
)

/** 按分组整理 setters：有 group 的按 group 聚合，无 group 的归入空名组 */
const setterGroups = computed(() => {
  const setters = filteredCommonSetters.value
  if (!hasGroups.value) {
    return [{ name: '', setters }]
  }
  const map = new Map<string, typeof setters>()
  for (const setter of setters) {
    const g = setter.group || '其他'
    if (!map.has(g)) map.set(g, [])
    map.get(g)!.push(setter)
  }
  return Array.from(map.entries()).map(([name, sts]) => ({ name, setters: sts }))
})

/** 默认展开的分组（前三个） */
const DEFAULT_EXPANDED_GROUPS = new Set<string>()
const expandedGroups = ref<Set<string>>(new Set())
/** 搜索时自动展开所有分组 */
watch(
  () => [normalizedKeyword.value, hasGroups.value, selectedNode.value?.id] as [string, boolean, string | undefined],
  ([kw, hg, nodeId], old) => {
    if (!hg) return
    if (old && old[2] !== nodeId) {
      const groups = setterGroups.value.map((g) => g.name)
      expandedGroups.value = new Set(groups.slice(0, 3))
    }
    if (kw) {
      expandedGroups.value = new Set(setterGroups.value.map((g) => g.name))
    } else if (!expandedGroups.value.size) {
      expandedGroups.value = new Set(setterGroups.value.slice(0, 3).map((g) => g.name))
    }
  },
  { immediate: true },
)

function toggleGroup(name: string): void {
  const next = new Set(expandedGroups.value)
  if (next.has(name)) next.delete(name)
  else next.add(name)
  expandedGroups.value = next
}

function isGroupExpanded(name: string): boolean {
  return expandedGroups.value.has(name)
}

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
  boxEdit?: boolean
  borderEdit?: boolean
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
    placeholder: '',
    boxEdit: true,
  },
  {
    key: 'margin',
    label: t('外边距'),
    placeholder: '',
    boxEdit: true,
  },
  { key: 'background', label: t('背景色'), placeholder: t('如 #f5f5f5') },
  {
    key: 'borderRadius',
    label: t('圆角'),
    placeholder: t('数值'),
    unitSelect: true,
    unitOptions: ['px', '%'],
  },
  {
    key: 'border',
    label: t('边框'),
    placeholder: '',
    borderEdit: true,
  },
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

/** 解析 CSS box 值（padding/margin）为四个方向 { value, unit } */
function parseBoxValue(str: string): { value: string; unit: string }[] {
  const empty = { value: '', unit: 'px' }
  if (!str || !str.trim()) return [{ ...empty }, { ...empty }, { ...empty }, { ...empty }]
  const parts = str.trim().split(/\s+/)
  const parseOne = (s: string): { value: string; unit: string } => {
    const m = s.match(/^(-?\d+\.?\d*)\s*(px|%|rem|em|vh|vw|auto)?$/)
    if (m) return { value: m[1], unit: m[2] || 'px' }
    return { value: s, unit: '' }
  }
  if (parts.length === 1) {
    const p = parseOne(parts[0])
    return [p, { ...p }, { ...p }, { ...p }]
  }
  if (parts.length === 2) {
    const v = parseOne(parts[0]), h = parseOne(parts[1])
    return [v, h, { ...v }, { ...h }]
  }
  if (parts.length === 3) {
    const top = parseOne(parts[0]), right = parseOne(parts[1]), bottom = parseOne(parts[2])
    return [top, right, bottom, { ...right }]
  }
  return [parseOne(parts[0]), parseOne(parts[1]), parseOne(parts[2]), parseOne(parts[3])]
}

/** 组合四个方向为 CSS box 值字符串（自动简化） */
function composeBoxValue(sides: { value: string; unit: string }[]): string {
  const strs = sides.map(s => (s.value ? s.value + s.unit : ''))
  if (strs.every(s => !s)) return ''
  if (strs[0] === strs[1] && strs[1] === strs[2] && strs[2] === strs[3]) return strs[0]
  if (strs[0] === strs[2] && strs[1] === strs[3]) return `${strs[0]} ${strs[1]}`.trim()
  if (strs[1] === strs[3]) return `${strs[0]} ${strs[1]} ${strs[2]}`.trim()
  return strs.join(' ')
}

function getBoxParsed(key: keyof NodeCss): { value: string; unit: string }[] {
  void designerStore.schemaRevision
  return parseBoxValue(getCssValue(key))
}

function onBoxInput(key: keyof NodeCss, side: number, e: Event): void {
  const node = getSelectedNode()
  if (!node) return
  const sides = parseBoxValue(getCssValue(key))
  sides[side].value = (e.target as HTMLInputElement).value
  updateNodeCss(node.id, key, composeBoxValue(sides))
}

function onBoxUnitInput(key: keyof NodeCss, side: number, e: Event): void {
  const node = getSelectedNode()
  if (!node) return
  const sides = parseBoxValue(getCssValue(key))
  sides[side].unit = (e.target as HTMLSelectElement).value
  updateNodeCss(node.id, key, composeBoxValue(sides))
}

/** 解析边框值为 { width, style, color } */
function parseBorderValue(str: string): { width: string; style: string; color: string } {
  if (!str || !str.trim()) return { width: '', style: 'solid', color: '#dcdfe6' }
  const m = str.trim().match(/^(\d+)\s*(px)?\s+(none|solid|dashed|dotted)\s+(.+)$/)
  if (m) return { width: m[1], style: m[3], color: m[4] }
  return { width: '', style: 'solid', color: '' }
}

function getBorderParsed(): { width: string; style: string; color: string } {
  void designerStore.schemaRevision
  return parseBorderValue(getCssValue('border'))
}

function onBorderWidthInput(e: Event): void {
  const node = getSelectedNode()
  if (!node) return
  const b = parseBorderValue(getCssValue('border'))
  b.width = (e.target as HTMLInputElement).value
  updateNodeCss(node.id, 'border', composeBorderValue(b))
}

function onBorderStyleChange(e: Event): void {
  const node = getSelectedNode()
  if (!node) return
  const b = parseBorderValue(getCssValue('border'))
  b.style = (e.target as HTMLSelectElement).value
  updateNodeCss(node.id, 'border', composeBorderValue(b))
}

function onBorderColorChange(val: string | null): void {
  const node = getSelectedNode()
  if (!node) return
  const b = parseBorderValue(getCssValue('border'))
  b.color = val ?? ''
  updateNodeCss(node.id, 'border', composeBorderValue(b))
}

function composeBorderValue(b: { width: string; style: string; color: string }): string {
  if (!b.width && !b.color) return ''
  return `${b.width || '0'}px ${b.style} ${b.color || '#dcdfe6'}`.trim()
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

const LAYOUT_TYPES = new Set(['el-row', 'el-splitter', 'el-tabs', 'el-collapse'])

const isLayoutContainer = computed(() =>
  selectedNode.value ? LAYOUT_TYPES.has(selectedNode.value.type) : false
)

/** 是否为物料控件 */
const isDxComponent = computed(() =>
  selectedNode.value ? MATERIAL_TYPES.has(selectedNode.value.type) : false
)

/** 表格模式下的 dx-data-grid：禁止设置尺寸（宽高固定 100% 撑满画布） */
const isTableGrid = computed(
  () =>
    designerStore.mode === 'biz' &&
    designerStore.bizMode === 'table' &&
    selectedNode.value?.type === 'data-grid',
)

/** 移动端模式：页面设置只显示布局相关属性，与表单/表格页面彻底区分 */
const isMobileMode = computed(
  () => designerStore.mode === 'biz' && designerStore.bizMode === 'mobile',
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
  } else if (node.type === 'el-collapse') {
    childType = 'el-collapse-item'
    const idx = node.children.length + 1
    defaultProps = {
      title: t('面板') + idx,
      name: `panel${idx}`,
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

// ---- 折叠面板编辑 ----

const isElCollapse = computed(() => selectedNode.value?.type === 'el-collapse')

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

/** 切换设计器模式时关闭所有弹框 */
watch(() => designerStore.bizMode, () => {
  themeDialogVisible.value = false
  refPageSelectVisible.value = false
  fieldSelectVisible.value = false
  valueFieldSelectVisible.value = false
  dependFilterDialogVisible.value = false
  textDialogVisible.value = false
  jsonDialogVisible.value = false
  columnDialogVisible.value = false
  toolbarItemsDialogVisible.value = false
  summaryDialogVisible.value = false
  optionsDialogVisible.value = false
  pageSelectDialogVisible.value = false
  apiSelectDialogVisible.value = false
  apiDetailVisible.value = false
  eventDialogVisible.value = false
})

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
  if (node.props.required === true) return true
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
  updateProps(node.id, 'required', required)
}

const nodeValidationRules = computed<ValidationRuleConfig[]>(() => {
  const node = selectedNode.value
  if (!node) return []
  const rules = node.props.validationRules
  return Array.isArray(rules) ? (rules as ValidationRuleConfig[]) : []
})

/** 唯一性规则的联合字段候选：表单内其他表单类控件字段 */
const otherFormFieldNames = computed(() => {
  const node = selectedNode.value
  if (!node) return []
  const formNodes = collectAllNodes(designerStore.bizSchemas.form)
  return formNodes
    .filter(
      (n) =>
        n.fieldName &&
        n.fieldName !== node.fieldName &&
        getConfig(n.type)?.category === '表单'
    )
    .map((n) => ({
      fieldName: n.fieldName as string,
      label: (n.props.label as string) || '',
    }))
})

function isCombineFieldSelected(rule: ValidationRuleConfig, fieldName: string): boolean {
  return (rule.combineFields || []).includes(fieldName)
}

function toggleCombineField(idx: number, fieldName: string) {
  const rule = nodeValidationRules.value[idx]
  if (!rule) return
  const current = rule.combineFields || []
  const next = current.includes(fieldName)
    ? current.filter((f) => f !== fieldName)
    : [...current, fieldName]
  updateValidationRule(idx, 'combineFields', next.length ? next : undefined)
}

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
  if (type === 'unique') {
    newRule.message = t('该值已存在')
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
      <div v-if="!isMobileMode" class="prop-item">
        <div class="prop-label">{{ t('数据表名') }}</div>
        <input
          v-model="designerStore.tableName"
          class="page-input"
          :placeholder="t('请输入数据表名')"
          @input="onTableNameInput"
        />
      </div>
      <div v-if="!isMobileMode" class="prop-item">
        <div class="prop-label">{{ t('主键字段') }}</div>
        <input
          v-model="designerStore.primaryKeyField"
          class="page-input"
          :placeholder="t('请输入主键字段名，如 id')"
        />
      </div>
      <div v-if="!isMobileMode" class="prop-item">
        <div class="prop-label">{{ t('主键策略') }}</div>
        <select v-model="designerStore.keyStrategy" class="page-input">
          <option value="UUID">UUID 字符串</option>
          <option value="SNOWFLAKE">Snowflake 数字</option>
        </select>
      </div>
      <div v-if="!isMobileMode" class="prop-item">
        <div class="prop-label">{{ t('页面唯一标识') }}</div>
        <input :value="designerStore.pageId" class="page-input" readonly />
      </div>
      <div v-if="!isMobileMode" class="prop-item">
        <div class="prop-label">{{ t('页面背景') }}</div>
        <ElColorPicker
          :model-value="designerStore.pageCss.background"
          @update:model-value="(v) => onPageBackgroundChange({ value: v })"
        />
      </div>
      <template v-if="isMobileMode">
        <div class="prop-item">
          <div class="prop-label">{{ t('布局方式') }}</div>
          <select
            class="page-input"
            :value="designerStore.pageCss.mobileLayout || 'vertical'"
            @change="(e) => updatePageCss({ mobileLayout: (e.target as HTMLSelectElement).value as any })"
          >
            <option value="vertical">纵向排列</option>
            <option value="horizontal">横向排列</option>
            <option value="free">自由定位</option>
          </select>
        </div>
        <div class="prop-item">
          <div class="prop-label">{{ t('子控件间距') }}</div>
          <input
            type="number"
            class="page-input"
            :value="designerStore.pageCss.mobileGap ?? 8"
            @input="(e) => updatePageCss({ mobileGap: Number((e.target as HTMLInputElement).value) || 0 })"
          />
        </div>
        <div class="prop-item">
          <div class="prop-label">{{ t('内边距') }}</div>
          <input
            type="number"
            class="page-input"
            :value="designerStore.pageCss.mobilePadding ?? 0"
            @input="(e) => updatePageCss({ mobilePadding: Number((e.target as HTMLInputElement).value) || 0 })"
          />
        </div>
      </template>
      <div v-if="!isMobileMode" class="prop-item">
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
      <!-- 审批流绑定（仅单据页面显示，移动端不显示） -->
      <div v-if="designerStore.pageType === 'document' && !isMobileMode" class="prop-item">
        <div class="prop-label">{{ t('绑定审批流') }}</div>
        <ElSelect
          :model-value="designerStore.workflowId"
          :placeholder="t('选择审批流')"
          clearable
          @update:model-value="(v) => onWorkflowChange({ value: v })"
        >
          <ElOption
            v-for="w in workflowOptions"
            :key="w.id"
            :label="w.name"
            :value="w.id"
          />
        </ElSelect>
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
        <ElSelect
          :model-value="null"
          :placeholder="t('搜索并选择控件')"
          clearable
          filterable
          @update:model-value="(v) => onNodeSelectChanged({ value: v })"
        >
          <ElOption
            v-for="n in nodeSelectItems"
            :key="n.id"
            :label="n.label"
            :value="n.id"
          />
        </ElSelect>
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
          <div class="prop-section-title">
            {{ t('控件标识') }}
            <span v-if="overriddenProps.length" class="override-badge">
              {{ t('覆盖') }} {{ overriddenProps.length }}
              <button class="override-restore" @click="onRestoreAllProps">{{ t('恢复全部') }}</button>
            </span>
          </div>
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


        <!-- FE-S10 P2: 引用字典 + 编号规则（仅表单字段控件显示） -->
        <div v-if="isFormField" class="prop-section">
          <div class="prop-section-title">{{ t('数据字典与编号') }}</div>
          <div v-if="selectedNode?.type === 'select-box'" class="prop-item">
            <div class="prop-label">{{ t('引用字典') }}</div>
            <div class="prop-dict-select">
              <input
                class="page-input"
                type="text"
                readonly
                :value="currentNodeDictName"
                :placeholder="t('不引用')"
                @click="showDictSelectDialog = true"
              />
              <button
                v-if="currentNodeDictCode"
                class="prop-clear-btn"
                @click="onDictClear"
              >{{ t('清除') }}</button>
            </div>
          </div>
          <div v-if="selectedNode?.type === 'text-box'" class="prop-item">
            <div class="prop-label">{{ t('编号规则') }}</div>
            <div class="prop-number-rule">
              <input
                class="page-input"
                type="text"
                readonly
                :value="currentNodeNumberPreview"
                :placeholder="t('点击配置编号规则')"
                @click="showNumberRuleDialog = true"
              />
              <button
                v-if="currentNodeNumberExpr"
                class="prop-clear-btn"
                @click="() => { const n = getSelectedNode(); if (n) updateProps(n.id, 'defaultValueExpr', undefined) }"
              >{{ t('清除') }}</button>
            </div>
          </div>
        </div>

        <!-- 栅格快捷布局（仅 el-row 显示） -->
        <div v-if="isRowLayout" class="prop-section">
          <div class="prop-section-title">{{ t('快捷布局') }}</div>
          <div class="prop-item" style="display: flex; gap: 8px; align-items: flex-end; flex-wrap: wrap;">
            <div style="flex: 1; min-width: 80px;">
              <div class="prop-label">{{ t('列数') }}</div>
              <ElInputNumber
                :model-value="gridCols"
                :min="1"
                :max="24"
                :controls="false"
                style="width: 100%"
                @update:model-value="(v) => gridCols = v ?? 1"
              />
            </div>
            <div style="flex: 1; min-width: 80px;">
              <div class="prop-label">{{ t('行数') }}</div>
              <ElInputNumber
                :model-value="gridRows"
                :min="1"
                :max="10"
                :controls="false"
                style="width: 100%"
                @update:model-value="(v) => gridRows = v ?? 1"
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
            <div class="page-open-setter">
              <button
                class="page-open-select-btn"
                @click="openRefPageSelectDialog"
              >
                {{ refPageDisplayName || t('选择基础资料页面') }}
              </button>
              <button
                v-if="getEditable('refPageId')"
                class="page-open-clear-btn"
                @click="onRefPageSelect({ value: '' })"
              >
                {{ t('清除') }}
              </button>
            </div>
          </div>
          <div class="prop-item">
            <div class="prop-label">{{ t('显示字段') }}</div>
            <div class="page-open-setter">
              <button
                class="page-open-select-btn"
                :disabled="!getEditable('refPageId')"
                @click="openFieldSelectDialog"
              >
                {{ displayFieldText || t('选择显示字段') }}
              </button>
              <button
                v-if="getEditable('displayField')"
                class="page-open-clear-btn"
                @click="onDisplayFieldSelect({ value: '' })"
              >
                {{ t('清除') }}
              </button>
            </div>
          </div>
          <div class="prop-item">
            <div class="prop-label">{{ t('编码字段') }}</div>
            <div class="page-open-setter">
              <button
                class="page-open-select-btn"
                :disabled="!getEditable('refPageId')"
                @click="openCodeFieldSelectDialog"
              >
                {{ codeFieldText || t('选择编码字段') }}
              </button>
              <button
                v-if="getEditable('codeField')"
                class="page-open-clear-btn"
                @click="onCodeFieldSelect({ value: '' })"
              >
                {{ t('清除') }}
              </button>
            </div>
          </div>
          <div class="prop-item">
            <div class="prop-label">{{ t('值字段') }}</div>
            <div class="page-open-setter">
              <button
                class="page-open-select-btn"
                disabled
              >
                {{ valueFieldText || refPagePrimaryKey || t('值字段') }}
              </button>
            </div>
          </div>
          <div class="prop-item">
            <div class="prop-label">{{ t('依赖字段（级联父控件）') }}</div>
            <select
              class="page-input"
              :value="selectedNode?.props?.dependField ?? ''"
              @change="onDependFieldChange"
            >
              <option value="">{{ t('无（独立选择）') }}</option>
              <option
                v-for="fn in formFieldNames"
                :key="fn"
                :value="fn"
              >{{ fn }}</option>
            </select>
          </div>
          <div v-if="selectedNode?.props?.dependField" class="prop-item">
            <div class="prop-label">{{ t('过滤条件') }}</div>
            <button
              class="page-open-select-btn"
              @click="openDependFilterDialog"
            >
              {{ dependFilterCount > 0 ? t('已配置') + dependFilterCount + t('个条件') : t('配置过滤条件') }}
            </button>
          </div>
        </div>

        <DependFilterDialog
          v-model:visible="dependFilterDialogVisible"
          :conditions="selectedNode?.props?.dependFilters as DependFilterCondition[] | undefined"
          :fields="refPageFields"
          @save="onDependFilterSave"
        />

        <!-- 必填验证开关（仅可验证控件显示；启用编码规则时必填不生效，隐藏开关） -->
        <div v-if="isValidatable && !currentNodeNumberExpr" class="prop-section">
          <div class="prop-section-title">{{ t('验证') }}</div>
          <div class="prop-item">
            <div class="prop-label">{{ t('必填') }}</div>
            <ElSwitch :model-value="isRequired" @update:model-value="(v) => onRequiredChange({ value: v })" />
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
                type="number"
                min="0"
                step="any"
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
                type="number"
                min="0"
                step="any"
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

        <ElInput
          v-model="searchKeyword"
          :placeholder="t('搜索属性名或中文标签')"
          clearable
          class="prop-search"
        />

        <div v-if="filteredCommonSetters.length" class="prop-section">
          <div class="prop-section-title">{{ t('可配置属性') }}</div>
          <div
            v-for="g in setterGroups"
            :key="g.name || '__default__'"
            class="prop-group"
          >
            <div
              v-if="hasGroups"
              class="prop-group-header"
              @click="toggleGroup(g.name)"
            >
              <span
                class="prop-group-arrow"
                :class="{ expanded: isGroupExpanded(g.name) }"
              >▶</span>
              <span class="prop-group-name">{{ t(g.name) }}</span>
              <span class="prop-group-count">{{ g.setters.length }}</span>
            </div>
            <div
              v-show="!hasGroups || isGroupExpanded(g.name)"
              class="prop-group-body"
            >
            <div
              v-for="setter in g.setters"
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
            <ElInput
              v-if="setter.type === 'input'"
              :model-value="String(getEditable(setter.prop) ?? '')"
              @update:model-value="(v) => makeHandler(setter)({ value: v })"
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
            <ElInputNumber
              v-else-if="setter.type === 'number'"
              :model-value="getEditable(setter.prop) == null || getEditable(setter.prop) === '' ? undefined : Number(getEditable(setter.prop))"
              @update:model-value="(v) => makeHandler(setter)({ value: v })"
              controls-position="right"
              style="width: 100%"
            />
            <ElSwitch
              v-else-if="setter.type === 'switch'"
              :model-value="Boolean(getEditable(setter.prop))"
              @update:model-value="(v) => makeHandler(setter)({ value: v })"
            />
            <ElSelect
              v-else-if="setter.type === 'select'"
              :model-value="(getEditable(setter.prop) as string | number | boolean | undefined)"
              @update:model-value="(v) => makeHandler(setter)({ value: v })"
            >
              <ElOption
                v-for="o in translatedOptions(setter.options)"
                :key="o.value"
                :label="o.label"
                :value="o.value"
              />
            </ElSelect>
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
            <ElSelect
              v-else-if="setter.type === 'iconSelect'"
              :model-value="String(getEditable(setter.prop) || '')"
              filterable
              clearable
              :placeholder="t('搜索选择图标')"
              popper-class="lc-icon-select-popper"
              style="width: 100%"
              @update:model-value="(val: string) => makeHandler(setter)({ value: val })"
            >
              <ElOption
                v-for="ic in DX_ICONS"
                :key="ic"
                :label="ic"
                :value="ic"
              >
                <i :class="`dx-icon-${ic}`" style="margin-right: 8px; vertical-align: middle" />
                <span>{{ ic }}</span>
              </ElOption>
            </ElSelect>
            <ElColorPicker
              v-else-if="setter.type === 'color'"
              :model-value="String(getEditable(setter.prop) ?? '#333333')"
              @update:model-value="(v) => makeHandler(setter)({ value: v })"
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
            <!-- 选项编辑器：值+显示两列 -->
            <div v-else-if="setter.type === 'optionsEditor'" class="columns-setter">
              <button
                class="column-edit-btn"
                @click="openOptionsDialog(setter.prop, getEditable(setter.prop))"
              >
                {{ t('▦ 编辑选项') }}
              </button>
            </div>
            <!-- 从 items 中选择默认值 -->
            <ElSelect
              v-else-if="setter.type === 'itemsSelect' && selectedNode"
              :model-value="(getEditable(setter.prop) as string | number | boolean | undefined)"
              @update:model-value="(v) => makeHandler(setter)({ value: v })"
            >
              <ElOption
                v-for="o in selectItemsOptions"
                :key="o.value"
                :label="o.text"
                :value="o.value"
              />
            </ElSelect>
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
            <!-- 明细表上级来源选择器 -->
            <div
              v-else-if="setter.type === 'parentTablePicker' && selectedNode"
              class="parent-table-picker"
            >
              <template v-if="getEditable('parentType') === 'table'">
                <ElSelect
                  :model-value="(getEditable(setter.prop) as string) || ''"
                  :placeholder="t('请选择表格控件')"
                  style="width: 100%"
                  @update:model-value="(v) => { setEditable(setter.prop, v); updateProps(selectedNode!.id, setter.prop, v) }"
                >
                  <ElOption
                    v-for="opt in otherDetailTableOptions"
                    :key="opt.value"
                    :label="opt.label"
                    :value="opt.value"
                  />
                </ElSelect>
                <span
                  v-if="otherDetailTableOptions.length === 0"
                  class="prop-hint"
                >{{ t('暂无其他表格控件，请先在页面中添加 data-grid') }}</span>
              </template>
              <template v-else>
                <span class="prop-readonly" style="margin-top: 0">{{ t('当前表单页面') }}</span>
              </template>
              <span class="prop-readonly">
                {{ t('外键字段（自动取上级主键）') }}：<strong>{{ displayParentPkField }}</strong>
              </span>
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
              <select
                class="page-open-mode"
                :value="getPageOpenConfig(setter.prop).bizMode || 'form'"
                @change="
                  setPageOpenField(
                    setter.prop,
                    'bizMode',
                    ($event.target as HTMLSelectElement).value
                  )
                "
              >
                <option value="form">{{ t('表单') }}</option>
                <option value="table">{{ t('表格') }}</option>
                <option value="mobile">{{ t('移动端') }}</option>
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
              <div class="page-open-size-row">
                <label class="page-open-size-label">{{ t('宽度') }}</label>
                <input
                  class="page-open-size-input"
                  type="number"
                  min="1"
                  :value="parseSizeValue(getPageOpenConfig(setter.prop).width)"
                  :placeholder="t('默认')"
                  @input="
                    setPageOpenSize(
                      setter.prop,
                      'width',
                      ($event.target as HTMLInputElement).value
                    )
                  "
                />
                <select
                  class="page-open-unit-select"
                  :value="parseSizeUnit(getPageOpenConfig(setter.prop).width)"
                  @change="
                    setPageOpenSizeUnit(
                      setter.prop,
                      'width',
                      ($event.target as HTMLSelectElement).value as 'px' | '%'
                    )
                  "
                >
                  <option value="px">px</option>
                  <option value="%">%</option>
                </select>
                <template v-if="getPageOpenConfig(setter.prop).mode !== 'drawer'">
                  <label class="page-open-size-label">{{ t('高度') }}</label>
                  <input
                    class="page-open-size-input"
                    type="number"
                    min="1"
                    :value="parseSizeValue(getPageOpenConfig(setter.prop).height)"
                    :placeholder="t('默认')"
                    @input="
                      setPageOpenSize(
                        setter.prop,
                        'height',
                        ($event.target as HTMLInputElement).value
                      )
                    "
                  />
                  <select
                    class="page-open-unit-select"
                    :value="parseSizeUnit(getPageOpenConfig(setter.prop).height)"
                    @change="
                      setPageOpenSizeUnit(
                        setter.prop,
                        'height',
                        ($event.target as HTMLSelectElement).value as 'px' | '%'
                      )
                    "
                  >
                    <option value="px">px</option>
                    <option value="%">%</option>
                  </select>
                </template>
              </div>
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
            <!-- 预设过滤条件编辑器 -->
            <div
              v-else-if="setter.type === 'filterConditions' && selectedNode"
              class="columns-setter"
            >
              <button
                class="column-edit-btn"
                @click="openFilterConditionsDialog(setter.prop, getEditable(setter.prop))"
              >
                {{ t('▦ 编辑过滤条件') }}
              </button>
            </div>
          </div>
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
              <ElColorPicker
                :model-value="getCssValue(field.key) || '#ffffff'"
                @update:model-value="(v) => onCssColorChange(field.key)({ value: v })"
              />
            </template>
            <template v-else-if="field.unitSelect && field.unitOptions">
              <div class="css-unit-row">
                <input
                  v-if="getCssParsed(field.key).unit !== 'auto'"
                  type="number"
                  min="0"
                  step="any"
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
            <template v-else-if="field.boxEdit">
              <div class="css-box-editor">
                <div
                  v-for="(sideLabel, si) in [t('上'), t('右'), t('下'), t('左')]"
                  :key="si"
                  class="css-box-row"
                >
                  <span class="css-box-label">{{ sideLabel }}</span>
                  <input
                    type="number"
                    min="0"
                    step="any"
                    class="page-input css-box-input"
                    :value="getBoxParsed(field.key)[si].value"
                    :placeholder="t('数值')"
                    @input="onBoxInput(field.key, si, $event)"
                  />
                  <select
                    class="css-unit-select css-box-unit"
                    :value="getBoxParsed(field.key)[si].unit"
                    @change="onBoxUnitInput(field.key, si, $event)"
                    @click.stop
                    @mousedown.stop
                  >
                    <option value="px">px</option>
                    <option value="%">%</option>
                  </select>
                </div>
              </div>
            </template>
            <template v-else-if="field.borderEdit">
              <div class="css-border-editor">
                <div class="css-border-row">
                  <input
                    type="number"
                    min="0"
                    step="any"
                    class="page-input css-border-width"
                    :value="getBorderParsed().width"
                    :placeholder="t('宽度')"
                    @input="onBorderWidthInput($event)"
                  />
                  <span class="css-border-px">px</span>
                  <select
                    class="css-unit-select css-border-style"
                    :value="getBorderParsed().style"
                    @change="onBorderStyleChange($event)"
                    @click.stop
                    @mousedown.stop
                  >
                    <option value="solid">{{ t('实线') }}</option>
                    <option value="dashed">{{ t('虚线') }}</option>
                    <option value="dotted">{{ t('点线') }}</option>
                    <option value="none">{{ t('无') }}</option>
                  </select>
                </div>
                <ElColorPicker
                  :model-value="getBorderParsed().color || '#dcdfe6'"
                  @update:model-value="onBorderColorChange"
                />
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
          <ElButton
            plain
            style="width: 120px"
            @click="addLayoutChild"
          >{{ t('＋ 添加列') }}</ElButton>
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
                <ElSwitch
                  :model-value="!!panel.props.collapsible"
                  @update:model-value="
                    (v) => onChildPropSwitch(panel.id, 'collapsible', { value: v })
                  "
                />
              </div>
            </div>
          </div>
          <ElButton
            plain
            style="width: 130px"
            @click="addLayoutChild"
          >{{ t('＋ 添加面板') }}</ElButton>
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
          <ElButton
            plain
            style="width: 130px"
            @click="addLayoutChild"
          >{{ t('＋ 添加标签') }}</ElButton>
        </div>

        <!-- 折叠面板：面板管理 -->
        <div v-if="isElCollapse" class="prop-section">
          <div class="prop-section-title">
            {{ t('折叠项配置') }} ({{ layoutChildren.length }} {{ t('个') }})
          </div>
          <div class="items-tip">
            {{
              t(
                '每个折叠项可配置标题和标识，点击标题可展开/折叠内容区域。'
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
                <span class="item-index">#{{ i + 1 }}</span>
                {{ panel.props.title || t('面板') }}
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
                <span class="item-prop-label">{{ t('标题') }}</span>
                <input
                  class="item-prop-input"
                  type="text"
                  :value="panel.props.title ?? ''"
                  @input="onChildPropText(panel.id, 'title', $event)"
                />
              </div>
              <div class="item-prop-row">
                <span class="item-prop-label">{{ t('唯一标识') }}</span>
                <input
                  class="item-prop-input"
                  type="text"
                  :value="panel.props.name ?? ''"
                  @input="onChildPropText(panel.id, 'name', $event)"
                />
              </div>
              <div class="item-prop-row">
                <span class="item-prop-label">{{ t('内容高度') }}</span>
                <div style="display:flex;align-items:center;gap:4px">
                  <input
                    class="item-prop-input"
                    type="number"
                    :value="panel.props.height ?? ''"
                    :placeholder="t('如 300')"
                    @input="onChildPropText(panel.id, 'height', $event)"
                  />
                  <span style="flex-shrink:0;color:#909399">px</span>
                </div>
              </div>
            </div>
          </div>
          <ElButton
            plain
            style="width: 130px"
            @click="addLayoutChild"
          >{{ t('＋ 添加面板') }}</ElButton>
        </div>
      </div>

      <!-- ====== 字段变更事件（页面级，FE-S1） ====== -->
      <div
        v-if="activeTab === 'events'"
        class="prop-tab-content"
      >
        <div class="prop-section">
          <div class="prop-section-title">{{ t('字段变更事件') }}</div>
          <div class="events-tip">
            {{ t('配置字段变更触发的业务事件（后端触发，前端只配置）') }}
          </div>
          <button
            class="editor-btn"
            style="width: auto; padding: 4px 12px"
            @click="showFieldEventRuleDialog = true"
          >
            {{ t('配置字段变更事件') }}
          </button>
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
            <template v-if="isMobileMode">{{ t('为移动端事件选择动作，点击编辑后保存到页面 Schema。') }}</template>
            <template v-else>
              {{ t('为控件事件编写 JavaScript 处理代码。参数') }} <code>e</code>
              {{ t('为事件对象。') }}
            </template>
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
              <ElButton
                v-if="isEventSet(evt.name)"
                text
                :title="t('清除事件处理代码')"
                style="width: 22px; height: 22px; padding: 0"
                @click="clearEvent(evt.name)"
              >✕</ElButton>
            </div>
            <textarea
              v-if="!isMobileMode"
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
            <div v-if="rule.type === 'unique'" class="prop-item">
              <div class="prop-label">{{ t('联合字段（组合唯一）') }}</div>
              <div class="combine-fields-box">
                <label
                  v-for="f in otherFormFieldNames"
                  :key="f.fieldName"
                  class="combine-field-option"
                >
                  <input
                    type="checkbox"
                    :checked="isCombineFieldSelected(rule, f.fieldName)"
                    @change="toggleCombineField(idx, f.fieldName)"
                  />
                  <span>{{ f.fieldName }}<template v-if="f.label">（{{ f.label }}）</template></span>
                </label>
                <div v-if="!otherFormFieldNames.length" class="combine-fields-empty">
                  {{ t('暂无其他表单字段') }}
                </div>
              </div>
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
    <JsonTableEditorDialog
      v-model:model-value="optionsDialogValue"
      v-model:visible="optionsDialogVisible"
      :title="t('选项编辑')"
      :columns="['value', 'text']"
      :column-labels="{ value: t('值'), text: t('显示') }"
      @update:model-value="onOptionsDialogConfirm"
    />
    <ColumnEditorDialog
      v-model:model-value="columnDialogValue"
      v-model:visible="columnDialogVisible"
      :title="t('列配置编辑')"
      :editing-mode="gridEditingMode"
      :remote-grid="selectedNode?.type === 'data-grid' && (selectedNode.props as any)?.dataSourceType === 'remote'"
      :form-fields="formFieldOptions"
      @update:model-value="onColumnDialogConfirm"
    />
    <ToolbarItemsEditorDialog
      v-model:model-value="toolbarItemsDialogValue"
      v-model:visible="toolbarItemsDialogVisible"
      :title="t('工具栏按钮配置')"
      :remote-grid="selectedNode?.type === 'data-grid' && (selectedNode.props as any)?.dataSourceType === 'remote'"
      @update:model-value="onToolbarItemsDialogConfirm"
    />
    <SummaryEditorDialog
      v-model:model-value="summaryDialogValue"
      v-model:visible="summaryDialogVisible"
      :title="t('汇总高级配置')"
      :columns="selectedNode?.type === 'data-grid' ? (selectedNode.props.columns as unknown[] | undefined) : undefined"
      @update:model-value="onSummaryDialogConfirm"
    />

    <div v-if="filterConditionsDialogVisible" class="prop-dialog-overlay" @click.self="filterConditionsDialogVisible = false">
      <div class="dialog-container" style="width: 750px; max-height: 80vh">
        <div class="dialog-header">
          <span class="dialog-title">{{ t('预设过滤条件') }}</span>
          <button class="dialog-close" @click="filterConditionsDialogVisible = false">x</button>
        </div>
        <div class="dialog-body" style="padding: 12px">
          <div style="margin-bottom: 8px; color: #909399; font-size: 12px">{{ t('页面加载时自动应用这些过滤条件') }}</div>
          <div
            v-for="(cond, idx) in filterConditionsData"
            :key="idx"
            style="display: flex; align-items: center; gap: 6px; margin-bottom: 6px; flex-wrap: wrap"
          >
            <ElSelect
              :model-value="cond.field"
              @update:model-value="(v: any) => { cond.field = v; onFcFieldChange(cond) }"
              size="small"
              style="width: 140px; flex-shrink: 0"
              :placeholder="t('选择字段')"
            >
              <ElOption
                v-for="c in getFcColumns()"
                :key="c.dataField"
                :value="c.dataField"
                :label="c.caption || c.dataField"
              />
            </ElSelect>
            <ElSelect
              :model-value="cond.operator"
              @update:model-value="(v: any) => { cond.operator = v; onFcOperatorChange(cond) }"
              size="small"
              style="width: 90px; flex-shrink: 0"
            >
              <ElOption
                v-for="op in getFcOperatorsForField(cond.field)"
                :key="op.value"
                :value="op.value"
                :label="op.label"
              />
            </ElSelect>
            <template v-if="getFcColLookup(cond.field)">
              <ElSelect
                :model-value="cond.value"
                @update:model-value="(v: any) => cond.value = v"
                size="small"
                style="flex: 1; min-width: 120px"
                :placeholder="t('值')"
              >
                <ElOption
                  v-for="opt in (getFcColLookup(cond.field).dataSource as any[])"
                  :key="opt[getFcColLookup(cond.field).valueExpr]"
                  :value="opt[getFcColLookup(cond.field).valueExpr]"
                  :label="opt[getFcColLookup(cond.field).displayExpr]"
                />
              </ElSelect>
            </template>
            <template v-else-if="getFcColType(cond.field) === 'date'">
              <ElDatePicker
                :model-value="cond.value"
                @update:model-value="(v: any) => cond.value = v || ''"
                type="date"
                value-format="YYYY-MM-DD"
                size="small"
                style="flex: 1; min-width: 120px"
                :placeholder="t('值')"
              />
            </template>
            <template v-else-if="getFcColType(cond.field) === 'datetime'">
              <ElDatePicker
                :model-value="cond.value"
                @update:model-value="(v: any) => cond.value = v || ''"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                size="small"
                style="flex: 1; min-width: 120px"
                :placeholder="t('值')"
              />
            </template>
            <template v-else>
              <ElInput
                :model-value="cond.value"
                @update:model-value="(v: any) => cond.value = v"
                size="small"
                :type="getFcColType(cond.field) === 'number' ? 'number' : 'text'"
                :readonly="isFcMultiValueReadonly(cond)"
                :placeholder="isFcMultiValueReadonly(cond) ? String(cond.value).split('\n').filter((v: string) => v.trim()).slice(0, 3).join(', ') + (String(cond.value).split('\n').filter((v: string) => v.trim()).length > 3 ? '...' : '') : (cond.operator === 'between' ? t('开始') : t('值'))"
                style="flex: 1; min-width: 120px"
              />
            </template>
            <ElButton
              v-if="isFcMultiValueField(cond)"
              size="small"
              @click="openFilterCondMultiValue(cond)"
            >多值</ElButton>
            <template v-if="cond.operator === 'between'">
              <span style="flex-shrink: 0; color: #909399">~</span>
              <template v-if="getFcColType(cond.field) === 'date'">
                <ElDatePicker
                  :model-value="cond.value2"
                  @update:model-value="(v: any) => cond.value2 = v || ''"
                  type="date"
                  value-format="YYYY-MM-DD"
                  size="small"
                  style="flex: 1; min-width: 120px"
                  :placeholder="t('结束')"
                />
              </template>
              <template v-else-if="getFcColType(cond.field) === 'datetime'">
                <ElDatePicker
                  :model-value="cond.value2"
                  @update:model-value="(v: any) => cond.value2 = v || ''"
                  type="datetime"
                  value-format="YYYY-MM-DD HH:mm:ss"
                  size="small"
                  style="flex: 1; min-width: 120px"
                  :placeholder="t('结束')"
                />
              </template>
              <template v-else>
                <ElInput
                  :model-value="cond.value2"
                  @update:model-value="(v: any) => cond.value2 = v"
                  size="small"
                  :type="getFcColType(cond.field) === 'number' ? 'number' : 'text'"
                  style="flex: 1; min-width: 120px"
                  :placeholder="t('结束')"
                />
              </template>
            </template>
            <button
              type="button"
              class="writeback-del-btn"
              @click="filterConditionsData.splice(idx, 1)"
            >✕</button>
          </div>
          <button
            class="writeback-add-btn"
            type="button"
            @click="filterConditionsData.push({ field: '', operator: '=', value: '', value2: '' })"
          >+ {{ t('添加条件') }}</button>
        </div>
        <div class="dialog-footer">
          <button class="dialog-btn dialog-btn-cancel" @click="filterConditionsDialogVisible = false">{{ t('取消') }}</button>
          <button class="dialog-btn dialog-btn-confirm" @click="onFilterConditionsDialogConfirm">{{ t('确定') }}</button>
        </div>
      </div>
    </div>

    <div v-if="filterCondMultiValueVisible" class="prop-dialog-overlay" style="z-index: 10001" @click.self="filterCondMultiValueVisible = false">
      <div class="dialog-container" style="width: 420px; max-height: 80vh">
        <div class="dialog-header">
          <span class="dialog-title">{{ t('多值输入') }}</span>
          <button class="dialog-close" @click="filterCondMultiValueVisible = false">x</button>
        </div>
        <div class="dialog-body" style="padding: 8px">
          <div style="max-height: 50vh; overflow-y: auto">
            <div v-for="(_, rowIdx) in filterCondMultiValueRows" :key="rowIdx" style="display: flex; align-items: center; gap: 4px; margin-bottom: 2px">
              <input v-model="filterCondMultiValueRows[rowIdx]" class="form-input" style="flex: 1" :placeholder="t('值')" />
              <button type="button" style="flex-shrink: 0; border: none; background: none; cursor: pointer; color: #f56c6c; font-size: 16px; padding: 0 4px" @click="filterCondMultiValueRows[rowIdx] = ''" title="清空">×</button>
            </div>
          </div>
        </div>
        <div class="dialog-footer">
          <button class="dialog-btn dialog-btn-cancel" @click="filterCondMultiValueRows = filterCondMultiValueRows.map(() => '')">{{ t('清空全部') }}</button>
          <button class="dialog-btn dialog-btn-cancel" @click="filterCondMultiValueVisible = false">{{ t('取消') }}</button>
          <button class="dialog-btn dialog-btn-confirm" @click="confirmFilterCondMultiValue">{{ t('确定') }}</button>
        </div>
      </div>
    </div>
    <EventCodeDialog
      v-if="!isMobileMode"
      v-model:model-value="eventDialogValue"
      v-model:visible="eventDialogVisible"
      :title="t('事件代码编辑 - ') + eventDialogName"
      @update:model-value="onEventDialogConfirm"
    />
    <MobileEventDialog
      v-else
      v-model:model-value="eventDialogValue"
      v-model:visible="eventDialogVisible"
      :event-name="eventDialogName"
      :events="availableEvents"
      @confirm="onEventDialogConfirm"
    />
    <PageSelectDialog
      v-model:visible="pageSelectDialogVisible"
      v-model:model-value="pageSelectDialogPageId"
      :exclude-page-id="designerStore.pageId"
      @select="onPageSelect"
    />
    <!-- 基础资料页面选择弹窗（仅过滤 basedata 类型） -->
    <PageSelectDialog
      v-model:visible="refPageSelectVisible"
      v-model:model-value="refPageSelectId"
      page-type="basedata"
      @select="(id: string) => onRefPageSelectConfirm(id)"
    />
    <!-- 明细表上级页面选择弹窗 -->
    <PageSelectDialog
      v-model:visible="parentPageSelectVisible"
      v-model:model-value="parentPageSelectValue"
      :exclude-page-id="designerStore.pageId"
      @select="(id: string) => onParentPageSelectConfirm(id)"
    />
    <!-- 显示字段选择弹窗 -->
    <FieldSelectDialog
      v-model:visible="fieldSelectVisible"
      v-model:model-value="fieldSelectId"
      :fields="refPageFields"
      :multiple="true"
      :title="t('选择显示字段')"
      @select="(field: string) => onFieldSelectConfirm(field)"
    />
    <!-- 值字段选择弹窗 -->
    <FieldSelectDialog
      v-model:visible="valueFieldSelectVisible"
      v-model:model-value="valueFieldSelectId"
      :fields="refPageFields"
      :title="t('选择值字段')"
      @select="(field: string) => onValueFieldSelectConfirm(field)"
    />
    <!-- 编码字段选择弹窗（单选） -->
    <FieldSelectDialog
      v-model:visible="codeFieldSelectVisible"
      v-model:model-value="codeFieldSelectId"
      :fields="refPageFields"
      :title="t('选择编码字段')"
      @select="(field: string) => onCodeFieldSelectConfirm(field)"
    />
    <ApiSelectDialog
      v-model:visible="apiSelectDialogVisible"
      :model-value="String(getEditable(apiSelectDialogProp) || '')"
      @select="(id: string) => onApiSelect(id)"
    />
    <!-- API 服务详情弹窗 -->
    <Teleport to="body" v-if="apiDetailVisible && apiDetailService">
      <div class="api-detail-overlay" @click="apiDetailVisible = false">
        <div class="api-detail-dialog" :class="{ 'is-maximized': apiDetailMax, 'is-dragging': apiDetailDragging }" :style="apiDetailDragStyle" @click.stop>
          <div class="api-detail-header" :class="{ 'is-draggable': !apiDetailMax }" @mousedown="onApiDetailHeaderDown">
            <span>{{ t('API 服务详情') }}</span>
            <div class="dialog-header-actions">
              <DialogMaxButton :maximized="apiDetailMax" @toggle="toggleApiDetailMax" />
              <button class="api-detail-close" @click="apiDetailVisible = false">
                ×
              </button>
            </div>
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
    <FieldEventRuleDialog v-model:visible="showFieldEventRuleDialog" />
    <!-- FE-S10 P2: 编号规则配置弹窗 -->
    <NumberRuleDialog
      v-model:visible="showNumberRuleDialog"
      :expr="currentNodeNumberExpr"
      :date-field-names="dateFieldNames"
      :non-date-field-names="nonDateFieldNames"
      @save="onSaveNumberRule"
    />
    <!-- 引用字典选择弹窗 -->
    <Teleport to="body" v-if="showDictSelectDialog">
      <div class="dict-select-overlay" @click="showDictSelectDialog = false">
        <div class="dict-select-dialog" @click.stop>
          <div class="dict-select-header">
            <span>{{ t('选择引用字典') }}</span>
            <button class="dict-select-close" @click="showDictSelectDialog = false">×</button>
          </div>
          <div class="dict-select-body">
            <input
              class="page-input dict-select-search"
              type="text"
              v-model="dictSelectSearch"
              :placeholder="t('搜索字典编码或名称')"
            />
            <table class="dict-select-table">
              <thead>
                <tr>
                  <th>{{ t('编码') }}</th>
                  <th>{{ t('名称') }}</th>
                  <th>{{ t('状态') }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="filteredDictList.length === 0">
                  <td colspan="4" class="dict-select-empty">{{ t('暂无数据') }}</td>
                </tr>
                <tr
                  v-for="d in filteredDictList"
                  :key="d.code"
                  :class="{ 'is-selected': currentNodeDictCode === d.code }"
                >
                  <td>{{ d.code }}</td>
                  <td>{{ d.name }}</td>
                  <td>{{ d.enabled ? t('启用') : t('禁用') }}</td>
                  <td class="dict-select-action">
                    <button class="dict-select-pick" @click="onDictRowSelect(d)">
                      {{ currentNodeDictCode === d.code ? t('已选') : t('选择') }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
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
.prop-group {
  margin-bottom: 4px;
}
.prop-group-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 8px;
  cursor: pointer;
  user-select: none;
  background: #f5f7fa;
  border-radius: 4px;
  margin-bottom: 4px;
  transition: background 0.15s;
}
.prop-group-header:hover {
  background: #ecf5ff;
}
.prop-group-arrow {
  font-size: 10px;
  color: #909399;
  transition: transform 0.2s;
  display: inline-block;
}
.prop-group-arrow.expanded {
  transform: rotate(90deg);
}
.prop-group-name {
  font-size: 12px;
  font-weight: 600;
  color: #303133;
  flex: 1;
}
.prop-group-count {
  font-size: 11px;
  color: #909399;
  background: #e4e7ed;
  border-radius: 8px;
  padding: 1px 7px;
}
.prop-group-body {
  padding-left: 4px;
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
.css-box-editor {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  width: 100%;
}
.css-box-row {
  display: flex;
  gap: 4px;
  align-items: center;
}
.css-box-label {
  width: 14px;
  font-size: 12px;
  color: #909399;
  text-align: center;
  flex-shrink: 0;
}
.css-box-input {
  flex: 1;
  min-width: 0;
}
.css-box-unit {
  width: 50px !important;
}
.css-border-editor {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}
.css-border-row {
  display: flex;
  gap: 4px;
  align-items: center;
}
.css-border-width {
  flex: 1;
  min-width: 0;
}
.css-border-px {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}
.css-border-style {
  width: 70px !important;
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
  flex-wrap: wrap;
}
.parent-table-picker {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
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
.prop-hint {
  flex-basis: 100%;
  margin-top: 4px;
  font-size: 11px;
  color: #e6a23c;
  line-height: 1.4;
}
.prop-readonly {
  flex-basis: 100%;
  margin-top: 6px;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.prop-readonly strong {
  color: #1976d2;
  font-size: 14px;
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
.combine-fields-box {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  max-height: 120px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 6px 8px;
  background: #fff;
}
.combine-field-option {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  white-space: nowrap;
}
.combine-fields-empty {
  font-size: 12px;
  color: #c0c4cc;
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
.page-open-select-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
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
.page-open-size-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
}
.page-open-size-label {
  font-size: 12px;
  color: #606266;
  flex-shrink: 0;
}
.page-open-size-input {
  width: 70px;
  box-sizing: border-box;
  padding: 4px 6px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  flex-shrink: 0;
}
.page-open-unit-select {
  width: 56px;
  box-sizing: border-box;
  padding: 4px 6px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  flex-shrink: 0;
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
.prop-number-rule {
  display: flex;
  gap: 6px;
  align-items: center;
}
.prop-clear-btn {
  padding: 2px 8px;
  background: #f56c6c;
  color: #fff;
  border: none;
  border-radius: 3px;
  cursor: pointer;
  font-size: 12px;
  white-space: nowrap;
}
.prop-clear-btn:hover {
  background: #f78989;
}
.override-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
  padding: 2px 6px;
  background: #fdf6ec;
  border: 1px solid #f5dab1;
  border-radius: 3px;
  font-size: 11px;
  color: #e6a23c;
  font-weight: 400;
}
.override-restore {
  padding: 0 4px;
  background: transparent;
  border: none;
  color: #409eff;
  cursor: pointer;
  font-size: 11px;
  text-decoration: underline;
}
.prop-dict-select {
  display: flex;
  gap: 6px;
  align-items: center;
}
.prop-dict-select .page-input {
  cursor: pointer;
  flex: 1;
}
.dict-select-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 21000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.dict-select-dialog {
  background: #fff;
  border-radius: 8px;
  width: 520px;
  max-width: 90vw;
  max-height: 80vh;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
}
.dict-select-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  font-size: 15px;
  font-weight: 600;
}
.dict-select-close {
  border: none;
  background: none;
  font-size: 20px;
  cursor: pointer;
  color: #6b7280;
  line-height: 1;
}
.dict-select-close:hover {
  color: #1f2937;
}
.dict-select-body {
  padding: 12px 16px;
  overflow-y: auto;
  flex: 1;
}
.dict-select-search {
  margin-bottom: 10px;
}
.dict-select-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.dict-select-table th {
  text-align: left;
  padding: 8px 6px;
  border-bottom: 2px solid #e5e7eb;
  color: #6b7280;
  font-weight: 600;
  white-space: nowrap;
}
.dict-select-table td {
  padding: 8px 6px;
  border-bottom: 1px solid #f3f4f6;
  color: #1f2937;
}
.dict-select-table tr.is-selected td {
  background: #ecf5ff;
}
.dict-select-table tr:hover td {
  background: #f5f7fa;
}
.dict-select-empty {
  text-align: center;
  color: #c0c4cc;
  padding: 24px 0;
}
.dict-select-action {
  text-align: right;
  white-space: nowrap;
}
.dict-select-pick {
  padding: 3px 10px;
  background: #409eff;
  color: #fff;
  border: none;
  border-radius: 3px;
  cursor: pointer;
  font-size: 12px;
}
.dict-select-pick:hover {
  background: #66b1ff;
}
.dict-select-table tr.is-selected .dict-select-pick {
  background: #e6a23c;
  cursor: default;
}

/* ===================== 预设过滤条件弹窗 ===================== */
.prop-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10000;
}
.dialog-container {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  max-width: 95vw;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
}
.dialog-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.dialog-close {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 16px;
  color: #909399;
  padding: 0;
  line-height: 1;
}
.dialog-close:hover {
  color: #303133;
}
.dialog-body {
  padding: 12px 16px;
  flex: 1;
  overflow: auto;
}
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
}
.dialog-btn {
  padding: 6px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}
.dialog-btn-cancel {
  background: #fff;
  color: #606266;
}
.dialog-btn-cancel:hover {
  border-color: #1976d2;
  color: #1976d2;
}
.dialog-btn-confirm {
  background: #1976d2;
  color: #fff;
  border-color: #1976d2;
}
.dialog-btn-confirm:hover {
  background: #1565c0;
  border-color: #1565c0;
}
.form-input {
  flex: 1;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 5px 8px;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
  min-width: 0;
  background: #fff;
  color: #303133;
}
.form-input:focus {
  border-color: #1976d2;
}
.writeback-del-btn {
  border: none;
  background: none;
  color: #f56c6c;
  cursor: pointer;
  padding: 2px 4px;
  font-size: 13px;
  flex-shrink: 0;
}
.writeback-add-btn {
  border: 1px dashed #1976d2;
  background: none;
  color: #1976d2;
  font-size: 12px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 2px;
  align-self: flex-start;
}
.writeback-add-btn:hover {
  background: rgba(25, 118, 210, 0.06);
}

</style>
