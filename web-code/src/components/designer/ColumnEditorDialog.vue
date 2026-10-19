<script setup lang="ts">
import { ref, watch, computed, onMounted } from 'vue'
import JsonTableEditorDialog from './JsonTableEditorDialog.vue'
import ApiSelectDialog from './ApiSelectDialog.vue'
import PageSelectDialog from './PageSelectDialog.vue'
import { useDialogDragAndMax } from './useDialogDragAndMax'
import DialogMaxButton from './DialogMaxButton.vue'
import FieldSelectDialog from './FieldSelectDialog.vue'
import { t } from '@/store/lang'
import { listBaseDataPages, loadPage, type SavedPageMeta } from '@/store/persistence'
import type { ComponentSchema } from '@/types/schema'

interface ColumnConfig {
  dataField: string
  caption: string
  dataType: string
  formFieldName?: string
  width?: number
  visible?: boolean
  isSystemField?: boolean
  allowEditing?: boolean
  required?: boolean
  allowSorting?: boolean
  allowFiltering?: boolean
  allowGrouping?: boolean
  permCode?: string
  alignment?: string
  minWidth?: number
  fixed?: boolean
  format?: string
  editorType: string
  lookup?: {
    dataSource: unknown[]
    valueExpr: string
    displayExpr: string
    dataSourceType?: 'static' | 'remote'
    serviceId?: string
    remoteUrl?: string
    method?: string
  }
  dropDownGrid?: {
    inheritParent: boolean
    serviceId?: string
    remoteUrl: string
    method: string
    valueExpr: string
    displayExpr: string
    popupWidth?: number
    filterRow?: boolean
    columns: {
      dataField: string
      caption: string
      width?: number
      visible?: boolean
      writeBack?: boolean
    }[]
  }
  baseDataRef?: {
    refPageId: string
    valueField: string
    sourceField: string
    displayField?: string
    writeBack?: { sourceField: string; targetField: string }[]
  }
}

const props = defineProps<{
  modelValue: string
  visible: boolean
  title?: string
  editingMode?: string
  formFields?: { fieldName: string; dbField?: string; title: string; children?: { fieldName: string; dbField: string; title: string }[]; refPageId?: string; valueField?: string; displayField?: string; controlType?: string; selectItems?: { value: string; text: string }[] }[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:visible': [value: boolean]
}>()

const columns = ref<ColumnConfig[]>([])
const rawText = ref('')
const mode = ref<'table' | 'json'>('table')
const parseError = ref('')
const expandedIdx = ref<number | null>(null)
const searchKeyword = ref('')
const dragIdx = ref<number | null>(null)
const dragOverIdx = ref<number | null>(null)

/** API 服务选择弹窗状态 */
const apiSelectVisible = ref(false)
const apiSelectTarget = ref<'lookup' | 'dropDownGrid'>('lookup')

function openLookupApiSelect() {
  apiSelectTarget.value = 'lookup'
  apiSelectVisible.value = true
}
function openDdgApiSelect() {
  apiSelectTarget.value = 'dropDownGrid'
  apiSelectVisible.value = true
}
function onApiSelect(serviceId: string) {
  const col = columns.value[expandedIdx.value ?? -1]
  if (!col) return
  if (apiSelectTarget.value === 'lookup' && col.lookup) {
    col.lookup.serviceId = serviceId
  } else if (apiSelectTarget.value === 'dropDownGrid' && col.dropDownGrid) {
    col.dropDownGrid.serviceId = serviceId
  }
}
function clearLookupApiSelect() {
  const col = columns.value[expandedIdx.value ?? -1]
  if (col?.lookup) col.lookup.serviceId = ''
}
function clearDdgApiSelect() {
  const col = columns.value[expandedIdx.value ?? -1]
  if (col?.dropDownGrid) col.dropDownGrid.serviceId = ''
}

const isEditingEnabled = computed(() => {
  const mode = props.editingMode || 'none'
  return mode !== 'none'
})

const DATA_TYPES = [
  { label: t('文本 (string)'), value: 'string' },
  { label: t('数字 (number)'), value: 'number' },
  { label: t('日期 (date)'), value: 'date' },
  { label: t('日期时间 (datetime)'), value: 'datetime' },
  { label: t('布尔 (boolean)'), value: 'boolean' },
]

const EDITOR_TYPES = [
  { label: t('默认编辑器'), value: 'default' },
  { label: t('下拉框 (SelectBox)'), value: 'selectBox' },
  { label: t('下拉表格 (DropDownGrid)'), value: 'dropDownGrid' },
  { label: t('基础资料 (BaseData)'), value: 'baseData' },
]

const baseDataPages = ref<SavedPageMeta[]>([])
async function loadBaseDataPages() {
  try { baseDataPages.value = await listBaseDataPages() } catch { baseDataPages.value = [] }
}
onMounted(loadBaseDataPages)
watch(() => props.visible, (v) => { if (v) loadBaseDataPages() })
watch(expandedIdx, (idx) => {
  if (idx !== null && idx >= 0) {
    const col = columns.value[idx]
    if (col?.baseDataRef?.refPageId) loadRefPageFields(col.baseDataRef.refPageId)
  }
})

/** 基础资料页面选择弹窗 */
const refPageSelectVisible = ref(false)
const refPageSelectId = ref('')
/** 显示字段选择弹窗 */
const displayFieldSelectVisible = ref(false)
const displayFieldSelectId = ref('')

/** 关联页面字段列表 */
const refPageFields = ref<{ dbField: string; fieldName: string; displayText: string }[]>([])
const refPagePrimaryKey = ref('')
/** 当前正在编辑基础资料配置的列索引 */
const baseDataEditingIdx = ref<number>(-1)

/** 加载关联基础资料页面的字段列表 */
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
    refPagePrimaryKey.value = (page.primaryKeyField as string) || (page as any).primary_key_field || 'id'
    const fields: { dbField: string; fieldName: string; displayText: string }[] = []
    const tableSchema = page.bizSchemas?.table?.[0]
    const tableProps = (tableSchema?.props ?? {}) as Record<string, unknown>
    if (Array.isArray(tableProps.columns)) {
      const cols = tableProps.columns as Record<string, unknown>[]
      for (const c of cols) {
        const dataField = String(c.dataField || '')
        if (!dataField || dataField.includes('.')) continue
        const caption = String(c.caption || dataField)
        fields.push({ dbField: dataField, fieldName: caption, displayText: `${caption} (${dataField})` })
      }
    }
    const collect = (nodes: ComponentSchema[]) => {
      for (const node of nodes) {
        if (node.dbField && !node.dbField.includes('.')) {
          if (!fields.some((f) => f.dbField === node.dbField)) {
            const label = (node.props?.label as string) || ''
            const fieldName = node.fieldName || node.dbField
            fields.push({ dbField: node.dbField, fieldName, displayText: label ? `${label} (${fieldName})` : fieldName })
          }
        }
        if (node.children?.length) collect(node.children)
      }
    }
    collect(page.root || [])
    if (page.bizSchemas?.form?.length) collect(page.bizSchemas.form)
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

/** 打开关联基础资料页面选择弹窗 */
function openRefPageSelectDialog() {
  const idx = expandedIdx.value ?? -1
  baseDataEditingIdx.value = idx
  const col = columns.value[idx]
  refPageSelectId.value = col?.baseDataRef?.refPageId || ''
  refPageSelectVisible.value = true
}

/** 关联基础资料页面选择确认 */
function onRefPageSelectConfirm(pageId: string) {
  const idx = baseDataEditingIdx.value
  const col = columns.value[idx]
  if (!col?.baseDataRef) return
  col.baseDataRef.refPageId = pageId
  loadRefPageFields(pageId).then(() => {
    const pk = refPagePrimaryKey.value
    if (pk) col.baseDataRef!.valueField = pk
  })
}

/** 已选关联页面显示名称 */
const refPageDisplayName = computed(() => {
  const idx = expandedIdx.value ?? -1
  const col = columns.value[idx]
  const id = col?.baseDataRef?.refPageId || ''
  if (!id) return ''
  return baseDataPages.value.find((p) => p.pageId === id)?.pageName || id
})

/** 打开显示字段选择弹窗 */
function openDisplayFieldSelectDialog() {
  const idx = expandedIdx.value ?? -1
  baseDataEditingIdx.value = idx
  const col = columns.value[idx]
  displayFieldSelectId.value = col?.baseDataRef?.displayField || ''
  displayFieldSelectVisible.value = true
}

/** 显示字段选择确认 */
function onDisplayFieldSelectConfirm(dbField: string) {
  const idx = baseDataEditingIdx.value
  const col = columns.value[idx]
  if (!col?.baseDataRef) return
  col.baseDataRef.displayField = dbField
}

/** 已选显示字段文本 */
const displayFieldText = computed(() => {
  const idx = expandedIdx.value ?? -1
  const col = columns.value[idx]
  const field = col?.baseDataRef?.displayField || ''
  if (!field) return ''
  const parts = field.split(',').map((f) => f.trim()).filter(Boolean)
  const texts = parts.map((f) => refPageFields.value.find((p) => p.dbField === f)?.displayText || f)
  return texts.join(', ')
})


const filteredColumns = computed(() => {
  if (!searchKeyword.value.trim()) return columns.value
  const kw = searchKeyword.value.toLowerCase()
  return columns.value.filter(
    (c) =>
      c.dataField?.toLowerCase().includes(kw) ||
      c.caption?.toLowerCase().includes(kw) ||
      c.dataType?.toLowerCase().includes(kw)
  )
})

const selectedColIdx = ref<Set<number>>(new Set())
const allColsSelected = computed(() =>
  columns.value.length > 0 && selectedColIdx.value.size === columns.value.length
)

function toggleColSelect(realIdx: number) {
  const s = new Set(selectedColIdx.value)
  if (s.has(realIdx)) s.delete(realIdx)
  else s.add(realIdx)
  selectedColIdx.value = s
}

function toggleSelectAllCols() {
  if (allColsSelected.value) {
    selectedColIdx.value = new Set()
  } else {
    selectedColIdx.value = new Set(columns.value.map((_, i) => i))
  }
}

function batchDeleteColumns() {
  if (selectedColIdx.value.size === 0) return
  const indices = Array.from(selectedColIdx.value).sort((a, b) => b - a)
  for (const idx of indices) {
    columns.value.splice(idx, 1)
  }
  if (expandedIdx.value !== null && selectedColIdx.value.has(expandedIdx.value)) {
    expandedIdx.value = null
  } else if (expandedIdx.value !== null) {
    let shift = 0
    for (const idx of indices) {
      if (idx < expandedIdx.value) shift++
    }
    expandedIdx.value -= shift
  }
  selectedColIdx.value = new Set()
}

function getRealIndex(filteredIdx: number): number {
  const filtered = filteredColumns.value
  if (filtered === columns.value) return filteredIdx
  const target = filtered[filteredIdx]
  return columns.value.indexOf(target)
}

function parseColumns(text: string): ColumnConfig[] | null {
  try {
    const data = JSON.parse(text)
    if (!Array.isArray(data)) return null
    return data.map((item: Record<string, unknown>) => ({
      dataField: String(item.dataField ?? item.name ?? ''),
      caption: String(item.caption ?? item.dataField ?? ''),
      dataType: String(item.dataType ?? 'string'),
      width: item.width !== undefined ? Number(item.width) : undefined,
      visible: item.visible !== undefined ? Boolean(item.visible) : true,
      allowEditing:
        item.allowEditing !== undefined ? Boolean(item.allowEditing) : false,
      required:
        item.required !== undefined ? Boolean(item.required) : false,
      allowSorting:
        item.allowSorting !== undefined ? Boolean(item.allowSorting) : true,
      allowFiltering:
        item.allowFiltering !== undefined ? Boolean(item.allowFiltering) : true,
      allowGrouping:
        item.allowGrouping !== undefined
          ? Boolean(item.allowGrouping)
          : undefined,
      permCode:
        item.permCode !== undefined && item.permCode !== ''
          ? String(item.permCode)
          : String(item.dataField ?? item.name ?? ''),
      alignment:
        item.alignment !== undefined ? String(item.alignment) : undefined,
      minWidth: item.minWidth !== undefined ? Number(item.minWidth) : undefined,
      fixed: item.fixed !== undefined ? Boolean(item.fixed) : undefined,
      format:
        item.format !== undefined
          ? String(item.format)
          : defaultFormat(String(item.dataType ?? 'string')),
      editorType: String(item.editorType ?? 'default'),
      formFieldName: item.formFieldName as string | undefined,
      lookup: item.lookup as ColumnConfig['lookup'],
      dropDownGrid: item.dropDownGrid as ColumnConfig['dropDownGrid'],
      baseDataRef: item.baseDataRef as ColumnConfig['baseDataRef'],
    }))
  } catch {
    return null
  }
}

watch(
  () => props.modelValue,
  (val) => {
    rawText.value = val || ''
    parseError.value = ''
    if (mode.value === 'table') {
      const result = parseColumns(val || '')
      if (result) {
        columns.value = result
      } else {
        columns.value = []
        if (val && val.trim()) {
          parseError.value = t('JSON 格式错误，请切换到 JSON 模式编辑')
        }
      }
    }
  },
  { immediate: true }
)

function serializeColumns(): string {
  return JSON.stringify(columns.value, null, 2)
}

function addColumn() {
  const dataField = `FIELD_${columns.value.length + 1}`
  columns.value.push({
    dataField,
    caption: t(`列${columns.value.length + 1}`),
    dataType: 'string',
    editorType: 'default',
    allowEditing: false,
    visible: true,
    permCode: dataField,
  })
  expandedIdx.value = columns.value.length - 1
  searchKeyword.value = ''
}

/** 从表单导入字段弹窗状态 */
const formImportVisible = ref(false)
const formImportSelected = ref<Set<string>>(new Set())
const formImportSearch = ref('')
const formImportExpanded = ref<Set<string>>(new Set())
const { isMaximized: formImportMax, isDragging: formImportDragging, dragStyle: formImportDragStyle, onHeaderMouseDown: onFormImportHeaderDown, toggleMaximize: toggleFormImportMax, resetDialog: resetFormImportDrag } = useDialogDragAndMax()
watch(() => formImportVisible.value, (v) => { if (!v) resetFormImportDrag() })

interface ImportTreeNode {
  key: string
  label: string
  title: string
  level: number
  hasCheckbox: boolean
  children?: ImportTreeNode[]
}
interface ImportFlatItem extends ImportTreeNode {
  isExpanded: boolean
}

function buildChildTree(
  children: { fieldName: string; title: string }[],
  parentKey: string,
  level: number,
): ImportTreeNode[] {
  const groups = new Map<string, { fieldName: string; title: string }[]>()
  const direct: ImportTreeNode[] = []
  for (const c of children) {
    const dotIdx = c.fieldName.indexOf('.')
    if (dotIdx > 0) {
      const prefix = c.fieldName.substring(0, dotIdx)
      if (!groups.has(prefix)) groups.set(prefix, [])
      groups.get(prefix)!.push({
        fieldName: c.fieldName.substring(dotIdx + 1),
        title: c.title,
      })
    } else {
      direct.push({
        key: `${parentKey}.${c.fieldName}`,
        label: c.fieldName,
        title: c.title,
        level,
        hasCheckbox: true,
      })
    }
  }
  for (const [prefix, subChildren] of groups) {
    const groupKey = `${parentKey}.${prefix}`
    const childNodes = buildChildTree(subChildren, groupKey, level + 1)
    direct.push({
      key: groupKey,
      label: prefix,
      title: '',
      level,
      hasCheckbox: false,
      children: childNodes,
    })
  }
  return direct
}

function buildTree(fields: { fieldName: string; title: string; children?: { fieldName: string; title: string }[] }[]): ImportTreeNode[] {
  return fields.map((f) => {
    const node: ImportTreeNode = {
      key: f.fieldName,
      label: f.fieldName,
      title: f.title,
      level: 0,
      hasCheckbox: true,
    }
    if (f.children?.length) {
      node.children = buildChildTree(f.children, f.fieldName, 1)
    }
    return node
  })
}

function flattenTree(nodes: ImportTreeNode[], expanded: Set<string>): ImportFlatItem[] {
  const result: ImportFlatItem[] = []
  for (const n of nodes) {
    const isExpanded = expanded.has(n.key)
    result.push({ ...n, isExpanded })
    if (n.children?.length && isExpanded) {
      result.push(...flattenTree(n.children, expanded))
    }
  }
  return result
}

function nodeMatchesSearch(node: ImportTreeNode, kw: string): boolean {
  if (!kw) return true
  if (node.label.toLowerCase().includes(kw) || node.title.toLowerCase().includes(kw)) return true
  return (node.children ?? []).some((c) => nodeMatchesSearch(c, kw))
}

function filterTree(nodes: ImportTreeNode[], kw: string): ImportTreeNode[] {
  if (!kw) return nodes
  const result: ImportTreeNode[] = []
  for (const n of nodes) {
    if (nodeMatchesSearch(n, kw)) {
      result.push(n)
    }
  }
  return result
}

function collectExpandKeys(nodes: ImportTreeNode[]): string[] {
  const keys: string[] = []
  for (const n of nodes) {
    if (n.children?.length) {
      keys.push(n.key)
      keys.push(...collectExpandKeys(n.children))
    }
  }
  return keys
}

const formImportFlatList = computed<ImportFlatItem[]>(() => {
  const tree = buildTree(props.formFields ?? [])
  const kw = formImportSearch.value.trim().toLowerCase()
  const filtered = filterTree(tree, kw)
  let expanded = formImportExpanded.value
  if (kw) {
    expanded = new Set([...expanded, ...collectExpandKeys(filtered)])
  }
  return flattenTree(filtered, expanded)
})

const formImportSelectedCount = computed(() => formImportSelected.value.size)

function toggleFormImportExpand(key: string) {
  const s = new Set(formImportExpanded.value)
  if (s.has(key)) s.delete(key)
  else s.add(key)
  formImportExpanded.value = s
}

function openFormImport() {
  formImportSearch.value = ''
  formImportExpanded.value = new Set()
  const existingDataFields = new Set(columns.value.map((c) => c.dataField))
  const selected = new Set<string>()
  for (const f of props.formFields ?? []) {
    const fDbField = f.dbField || f.fieldName
    if (existingDataFields.has(fDbField)) {
      selected.add(f.fieldName)
    }
    for (const child of f.children ?? []) {
      const childDbField = child.dbField || child.fieldName
      const compositeKey = `${fDbField}.${childDbField}`
      if (existingDataFields.has(compositeKey)) {
        selected.add(`${f.fieldName}.${child.fieldName}`)
      }
    }
  }
  formImportSelected.value = selected
  formImportVisible.value = true
}

function formImportSelectAll() {
  const selected = new Set<string>()
  for (const f of props.formFields ?? []) {
    selected.add(f.fieldName)
    for (const child of f.children ?? []) {
      selected.add(`${f.fieldName}.${child.fieldName}`)
    }
  }
  formImportSelected.value = selected
}

function formImportDeselectAll() {
  formImportSelected.value = new Set()
}

function toggleFormImportField(fieldName: string) {
  const s = new Set(formImportSelected.value)
  if (s.has(fieldName)) s.delete(fieldName)
  else s.add(fieldName)
  formImportSelected.value = s
}

/** 根据表单控件类型推断列的 dataType */
function inferDataType(controlType?: string): string {
  if (!controlType) return 'string'
  if (controlType === 'date-box' || controlType === 'date-range-box') return 'date'
  if (controlType === 'number-box') return 'number'
  if (controlType === 'check-box' || controlType === 'switch') return 'boolean'
  return 'string'
}

/** 确认导入：把选中的表单字段添加为列，dataField 用 dbField（已存在的跳过） */
function confirmFormImport() {
  const existing = new Set(columns.value.map((c) => c.dataField))
  for (const key of formImportSelected.value) {
    let dataField: string
    let caption: string
    let baseDataRef: { refPageId: string; valueField: string; sourceField: string; displayField?: string } | undefined
    if (key.includes('.')) {
      const dotIdx = key.indexOf('.')
      const parentFieldName = key.substring(0, dotIdx)
      const childPath = key.substring(dotIdx + 1)
      const parentMeta = props.formFields?.find((f) => f.fieldName === parentFieldName)
      const childMeta = parentMeta?.children?.find((c) => c.fieldName === childPath)
      if (!childMeta) continue
      const parentDbField = parentMeta?.dbField || parentFieldName
      const childDbField = childMeta.dbField || childPath
      dataField = `${parentDbField}.${childDbField}`
      const parentTitle = parentMeta?.title || parentFieldName
      const childTitle = childMeta.title || childPath
      caption = `${parentTitle}.${childTitle}`
      if (parentMeta?.refPageId) {
        baseDataRef = {
          refPageId: parentMeta.refPageId,
          valueField: parentMeta.valueField || 'id',
          sourceField: parentMeta.dbField || '',
        }
      }
    } else {
      const fieldMeta = props.formFields?.find((f) => f.fieldName === key)
      dataField = fieldMeta?.dbField || key
      caption = fieldMeta?.title ?? key
      // 基础资料父字段：设置 baseDataRef + editorType，让单元格显示显示字段值而非主键
      if (fieldMeta?.refPageId) {
        const displayField = fieldMeta.displayField || fieldMeta.children?.[0]?.dbField || ''
        baseDataRef = {
          refPageId: fieldMeta.refPageId,
          valueField: fieldMeta.valueField || 'id',
          sourceField: fieldMeta.dbField || '',
          displayField,
        }
      }
    }
    if (existing.has(dataField)) continue
    const fieldMeta = key.includes('.')
      ? undefined
      : props.formFields?.find((f) => f.fieldName === key)
    const isSelectBox = fieldMeta?.controlType === 'select-box' && fieldMeta.selectItems?.length
    const isBaseData = !!baseDataRef && !key.includes('.')
    const inferredDataType = inferDataType(fieldMeta?.controlType)
    columns.value.push({
      dataField,
      caption,
      dataType: inferredDataType,
      format: defaultFormat(inferredDataType),
      editorType: isSelectBox ? 'selectBox' : (isBaseData ? 'baseData' : 'default'),
      formFieldName: key,
      ...(isSelectBox
        ? {
            lookup: {
              dataSource: fieldMeta!.selectItems!,
              valueExpr: 'value',
              displayExpr: 'text',
              dataSourceType: 'static' as const,
            },
          }
        : {}),
      allowEditing: false,

      visible: true,
      permCode: dataField,
      baseDataRef,
    })
  }
  formImportVisible.value = false
  searchKeyword.value = ''
}

function removeColumn(realIdx: number) {
  columns.value.splice(realIdx, 1)
  if (expandedIdx.value === realIdx) {
    expandedIdx.value = null
  } else if (expandedIdx.value !== null && expandedIdx.value > realIdx) {
    expandedIdx.value--
  }
}

function toggleExpand(realIdx: number) {
  expandedIdx.value = expandedIdx.value === realIdx ? null : realIdx
}

function onDragStart(realIdx: number) { dragIdx.value = realIdx }
function onDragOver(realIdx: number) {
  if (dragIdx.value === null || dragIdx.value === realIdx) return
  dragOverIdx.value = realIdx
}
function onDrop(realIdx: number) {
  if (dragIdx.value === null || dragIdx.value === realIdx) {
    dragIdx.value = null; dragOverIdx.value = null; return
  }
  const from = dragIdx.value
  const moved = columns.value.splice(from, 1)[0]
  columns.value.splice(realIdx, 0, moved)
  if (expandedIdx.value === from) expandedIdx.value = realIdx
  dragIdx.value = null; dragOverIdx.value = null
}
function onDragEnd() { dragIdx.value = null; dragOverIdx.value = null }

/** 根据数据类型返回默认格式化 */
function defaultFormat(dataType: string): string | undefined {
  if (dataType === 'datetime') return 'yyyy-MM-dd HH:mm:ss'
  if (dataType === 'date') return 'yyyy-MM-dd'
  return undefined
}

/** 数据类型变化时设置默认格式化 */
function onDataTypeChange(col: ColumnConfig) {
  col.format = defaultFormat(col.dataType)
}

function onEditorTypeChange(realIdx: number, type: string) {
  const col = columns.value[realIdx]
  col.editorType = type
  if (type === 'selectBox' && !col.lookup) {
    col.lookup = {
      dataSource: [],
      valueExpr: 'VALUE',
      displayExpr: 'TEXT',
      dataSourceType: 'static',
    }
  }
  if (type !== 'selectBox') {
    delete col.lookup
  }
  if (type === 'baseData' && !col.baseDataRef) {
    col.baseDataRef = { refPageId: '', valueField: 'id', sourceField: col.dataField, displayField: '' }
  }
  if (type === 'baseData' && col.baseDataRef?.refPageId) {
    loadRefPageFields(col.baseDataRef.refPageId)
  }
  if (type !== 'baseData') {
    delete col.baseDataRef
  }
  if (type === 'dropDownGrid' && !col.dropDownGrid) {
    col.dropDownGrid = {
      inheritParent: true,
      remoteUrl: '',
      method: 'POST',
      valueExpr: 'ID',
      displayExpr: 'NAME',
      popupWidth: 600,
      filterRow: true,
      columns: [
        { dataField: 'ID', caption: 'ID', visible: true, writeBack: false },
        {
          dataField: 'NAME',
          caption: t('名称'),
          visible: true,
          writeBack: false,
        },
      ],
    }
  }
}

function addDropDownGridColumn(col: ColumnConfig) {
  if (!col.dropDownGrid) return
  col.dropDownGrid.columns.push({
    dataField: '',
    caption: '',
    width: undefined,
    visible: true,
    writeBack: false,
  })
}

function removeDropDownGridColumn(col: ColumnConfig, idx: number) {
  if (!col.dropDownGrid) return
  col.dropDownGrid.columns.splice(idx, 1)
}

function switchToJson() {
  if (mode.value === 'table') {
    rawText.value = serializeColumns()
  } else {
    const result = parseColumns(rawText.value)
    if (result) {
      columns.value = result
      parseError.value = ''
    } else {
      parseError.value = t('JSON 格式错误')
      return
    }
  }
  mode.value = mode.value === 'table' ? 'json' : 'table'
}

/** 下拉框数据源编辑弹窗 */
const lookupDialogVisible = ref(false)
const lookupDialogColIdx = ref(-1)
const lookupDialogValue = ref('')

/** 下拉框数据源弹窗的列（基于值字段和显示字段） */
const lookupDialogColumns = computed(() => {
  const col = columns.value[lookupDialogColIdx.value]
  if (!col?.lookup) return undefined
  const cols = [col.lookup.valueExpr, col.lookup.displayExpr].filter(Boolean)
  return Array.from(new Set(cols)) as string[]
})

function openLookupDataSourceDialog(realIdx: number) {
  const col = columns.value[realIdx]
  if (!col.lookup) return
  lookupDialogColIdx.value = realIdx
  try {
    lookupDialogValue.value = JSON.stringify(
      col.lookup.dataSource ?? [],
      null,
      2
    )
  } catch {
    lookupDialogValue.value = '[]'
  }
  lookupDialogVisible.value = true
}

function onLookupDialogConfirm(value: string) {
  const col = columns.value[lookupDialogColIdx.value]
  if (col?.lookup) {
    try {
      col.lookup.dataSource = JSON.parse(value)
    } catch {
      /* 保持原值 */
    }
  }
  lookupDialogVisible.value = false
}

function onConfirm() {
  if (mode.value === 'table') {
    emit('update:modelValue', serializeColumns())
  } else {
    try {
      JSON.parse(rawText.value)
      emit('update:modelValue', rawText.value)
    } catch {
      parseError.value = t('JSON 格式错误，请检查')
      return
    }
  }
  emit('update:visible', false)
}

function onCancel() {
  emit('update:visible', false)
}

const { isMaximized, isDragging, dragStyle, onHeaderMouseDown, toggleMaximize, resetDialog } = useDialogDragAndMax()
watch(() => props.visible, (v) => { if (!v) resetDialog() })

const isEmpty = computed(() => {
  if (mode.value === 'table') return columns.value.length === 0
  return !rawText.value.trim()
})

function handleAutoInit() {
  if (isEmpty.value && mode.value === 'table') {
    columns.value = [
      {
        dataField: 'NAME',
        caption: t('名称'),
        dataType: 'string',
        editorType: 'default',
        allowEditing: false,
      },
      {
        dataField: 'CREATED_AT',
        caption: t('创建时间'),
        dataType: 'datetime',
        editorType: 'default',
        allowEditing: false,
      },
      {
        dataField: 'UPDATED_AT',
        caption: t('更新时间'),
        dataType: 'datetime',
        editorType: 'default',
        allowEditing: false,
      },
      {
        dataField: 'CREATED_BY',
        caption: t('创建人'),
        dataType: 'string',
        editorType: 'default',
        allowEditing: false,
      },
      {
        dataField: 'UPDATED_BY',
        caption: t('更新人'),
        dataType: 'string',
        editorType: 'default',
        allowEditing: false,
      },
    ]
  }
}

watch(mode, () => {
  handleAutoInit()
})
</script>

<template>
  <div v-if="visible" class="dialog-overlay" @click.self="onCancel">
    <div class="dialog-container dialog-xlarge" :class="{ 'is-maximized': isMaximized, 'is-dragging': isDragging }" :style="dragStyle">
      <div class="dialog-header" :class="{ 'is-draggable': !isMaximized }" @mousedown="onHeaderMouseDown">
        <span class="dialog-title">{{ title || t('列配置编辑') }}</span>
        <div class="dialog-header-actions">
          <button class="mode-toggle" @click="switchToJson">
            {{ mode === 'table' ? t('切换到 JSON 模式') : t('切换到表格模式') }}
          </button>
          <DialogMaxButton :maximized="isMaximized" @toggle="toggleMaximize" />
          <button class="dialog-close" @click="onCancel">x</button>
        </div>
      </div>
      <div class="dialog-body">
        <!-- 表格模式 -->
        <div v-if="mode === 'table'" class="column-editor">
          <div v-if="parseError" class="parse-error">{{ parseError }}</div>

          <!-- 搜索栏 + 添加按钮 -->
          <div class="toolbar">
            <input
              v-if="columns.length > 5"
              class="search-input"
              v-model="searchKeyword"
              :placeholder="t('搜索列名/标题/类型...')"
            />
            <button class="action-btn-add" @click="addColumn">
              {{ t('+ 添加列') }}
            </button>
            <button
              v-if="props.formFields && props.formFields.length"
              class="action-btn-add"
              @click="openFormImport"
            >
              {{ t('从表单导入') }}
            </button>
            <label v-if="columns.length > 0" class="batch-select-all-label">
              <input
                type="checkbox"
                :checked="allColsSelected"
                @change="toggleSelectAllCols"
              />
              {{ t('全选') }}
            </label>
            <button
              v-if="selectedColIdx.size > 0"
              class="action-btn-batch-delete"
              @click="batchDeleteColumns"
            >
              {{ t('批量删除') }} ({{ selectedColIdx.size }})
            </button>
          </div>

          <div v-if="isEmpty && !parseError" class="table-empty-tip">
            <span>{{ t('暂无列配置，点击上方按钮添加') }}</span>
          </div>

          <!-- 列列表（左右分栏） -->
          <div class="column-split-layout">
            <div class="column-list-left">
            <template
              v-for="(col, fidx) in filteredColumns"
              :key="getRealIndex(fidx)"
            >
              <div
                class="column-card"
                :class="{
                  expanded: expandedIdx === getRealIndex(fidx),
                  hidden: col.visible === false,
                  'drag-over': dragOverIdx === getRealIndex(fidx),
                  dragging: dragIdx === getRealIndex(fidx),
                }"
                draggable="true"
                @dragstart="onDragStart(getRealIndex(fidx))"
                @dragover.prevent="onDragOver(getRealIndex(fidx))"
                @drop="onDrop(getRealIndex(fidx))"
                @dragend="onDragEnd"
              >
                <div
                  class="column-card-header"
                  @click="toggleExpand(getRealIndex(fidx))"
                >
                  <input
                    type="checkbox"
                    class="col-select-checkbox"
                    :checked="selectedColIdx.has(getRealIndex(fidx))"
                    @click.stop="toggleColSelect(getRealIndex(fidx))"
                  />
                  <div class="column-card-summary">
                    <span class="col-badge" :class="`badge-${col.dataType}`">{{
                      col.dataType
                    }}</span>
                    <span class="col-field">{{ col.dataField }}</span>
                    <span
                      class="col-caption"
                      v-if="col.caption !== col.dataField"
                      >({{ col.caption }})</span
                    >
                    <span
                      class="col-editor-tag"
                      v-if="col.editorType !== 'default'"
                      >{{ col.editorType }}</span
                    >
                    <span class="col-tag-hide" v-if="col.visible === false">{{
                      t('隐藏')
                    }}</span>
                    <span
                      class="col-tag-lock"
                      v-if="col.allowEditing === false"
                      >{{ t('锁') }}</span
                    >
                  </div>
                  <div class="column-card-actions">
                    <span class="expand-icon">{{
                      expandedIdx === getRealIndex(fidx) ? '▼' : '▶'
                    }}</span>
                    <button
                      class="row-delete"
                      @click.stop="removeColumn(getRealIndex(fidx))"
                    >
                      {{ t('删除') }}
                    </button>
                  </div>
                </div>
              </div>
            </template>
            </div>

            <div class="column-list-right">
              <div v-if="expandedIdx === null" class="col-detail-empty">
                {{ t('点击左侧列查看详情') }}
              </div>
              <template v-for="col in (expandedIdx !== null ? [columns[expandedIdx]] : [])" :key="expandedIdx">
                <div class="column-card-body">
                  <div class="col-detail-title">{{ col.dataField }} ({{ col.caption }})</div>
                  <!-- 第一行：核心字段 -->
                  <div class="section-header">{{ t('基本属性') }}</div>
                  <div class="form-grid-2col">
                    <div class="form-row">
                      <label class="form-label">{{ t('数据字段') }}</label>
                      <input
                        class="form-input"
                        v-model="col.dataField"
                        placeholder="dataField"
                      />
                    </div>
                    <div class="form-row">
                      <label class="form-label">{{ t('显示标题') }}</label>
                      <input
                        class="form-input"
                        v-model="col.caption"
                        placeholder="caption"
                      />
                    </div>
                    <div class="form-row">
                      <label class="form-label">{{ t('数据类型') }}</label>
                      <select
                        class="form-select"
                        v-model="col.dataType"
                        @change="onDataTypeChange(col)"
                      >
                        <option
                          v-for="opt in DATA_TYPES"
                          :key="opt.value"
                          :value="opt.value"
                        >
                          {{ opt.label }}
                        </option>
                      </select>
                    </div>
                    <div
                      class="form-row"
                      v-if="
                        col.dataType === 'date' || col.dataType === 'datetime'
                      "
                    >
                      <label class="form-label">{{ t('格式化') }}</label>
                      <input
                        class="form-input"
                        v-model="col.format"
                        :placeholder="t('如 yyyy-MM-dd HH:mm:ss')"
                      />
                    </div>
                    <div class="form-row">
                      <label class="form-label">{{ t('对齐方式') }}</label>
                      <select class="form-select" v-model="col.alignment">
                        <option :value="undefined">{{ t('默认') }}</option>
                        <option value="left">{{ t('左对齐') }}</option>
                        <option value="center">{{ t('居中') }}</option>
                        <option value="right">{{ t('右对齐') }}</option>
                      </select>
                    </div>
                    <div class="form-row">
                      <label class="form-label">{{ t('列宽 (px)') }}</label>
                      <input
                        class="form-input"
                        type="number"
                        v-model.number="col.width"
                        :placeholder="t('自动')"
                      />
                    </div>
                    <div class="form-row">
                      <label class="form-label">{{ t('最小宽度') }}</label>
                      <input
                        class="form-input"
                        type="number"
                        v-model.number="col.minWidth"
                        :placeholder="t('无')"
                      />
                    </div>
                  </div>

                  <!-- 第二行：开关组（紧凑布局） -->
                  <div class="section-header">{{ t('显示设置') }}</div>
                  <div class="switch-group">
                    <label class="switch-item">
                      <input
                        type="checkbox"
                        v-model="col.visible"
                        :true-value="true"
                        :false-value="false"
                      />
                      <span>{{ t('显示') }}</span>
                    </label>
                    <label class="switch-item">
                      <input
                        type="checkbox"
                        v-model="col.allowSorting"
                        :true-value="true"
                        :false-value="false"
                      />
                      <span>{{ t('排序') }}</span>
                    </label>
                    <label class="switch-item">
                      <input
                        type="checkbox"
                        v-model="col.allowFiltering"
                        :true-value="true"
                        :false-value="false"
                      />
                      <span>{{ t('筛选') }}</span>
                    </label>
                    <label class="switch-item">
                      <input
                        type="checkbox"
                        v-model="col.allowGrouping"
                        :true-value="true"
                        :false-value="false"
                      />
                      <span>{{ t('分组') }}</span>
                    </label>
                    <label class="switch-item">
                      <input
                        type="checkbox"
                        v-model="col.fixed"
                        :true-value="true"
                        :false-value="false"
                      />
                      <span>{{ t('固定列') }}</span>
                    </label>
                    <label v-if="isEditingEnabled" class="switch-item">
                      <input
                        type="checkbox"
                        v-model="col.allowEditing"
                        :true-value="true"
                        :false-value="false"
                      />
                      <span>{{ t('可编辑') }}</span>
                    </label>

                  </div>

                  <!-- 权限码 -->
                  <div class="perm-code-row">
                    <div class="form-row">
                      <label class="form-label">{{ t('权限码') }}</label>
                      <input
                        class="form-input"
                        v-model="col.permCode"
                        :placeholder="t('留空则不鉴权')"
                      />
                    </div>
                  </div>

                  <!-- 第三行：编辑器配置（仅允许编辑时显示） -->
                  <div
                    v-if="isEditingEnabled && col.allowEditing !== false"
                    class="editor-section"
                  >
                    <div class="section-header">{{ t('编辑器配置') }}</div>
                    <div class="form-row">
                      <label class="form-label">{{ t('必填') }}</label>
                      <label class="switch-item">
                        <input
                          type="checkbox"
                          v-model="col.required"
                          :true-value="true"
                          :false-value="false"
                        />
                        <span>{{ col.required ? t('是') : t('否') }}</span>
                      </label>
                    </div>
                    <div class="form-row">
                      <label class="form-label">{{ t('编辑器类型') }}</label>
                      <select
                        class="form-select"
                        :value="col.editorType"
                        @change="(e) => onEditorTypeChange(expandedIdx ?? -1, (e.target as HTMLSelectElement).value)"
                      >
                        <option
                          v-for="opt in EDITOR_TYPES"
                          :key="opt.value"
                          :value="opt.value"
                        >
                          {{ opt.label }}
                        </option>
                      </select>
                    </div>

                    <!-- 下拉框配置 -->
                    <div
                      v-if="col.editorType === 'selectBox' && col.lookup"
                      class="sub-config"
                    >
                      <div class="sub-config-title">{{ t('下拉框配置') }}</div>
                      <div class="form-grid-2col">
                        <div class="form-row">
                          <label class="form-label">{{ t('值字段') }}</label>
                          <input
                            class="form-input"
                            v-model="col.lookup.valueExpr"
                            placeholder="valueExpr"
                          />
                        </div>
                        <div class="form-row">
                          <label class="form-label">{{ t('显示字段') }}</label>
                          <input
                            class="form-input"
                            v-model="col.lookup.displayExpr"
                            placeholder="displayExpr"
                          />
                        </div>
                      </div>
                      <div class="form-row" v-if="!col.lookup.serviceId">
                        <label class="form-label">{{ t('数据源类型') }}</label>
                        <select
                          class="form-select"
                          v-model="col.lookup.dataSourceType"
                        >
                          <option value="static">{{ t('静态数据') }}</option>
                          <option value="remote">{{ t('远程加载') }}</option>
                        </select>
                      </div>
                      <!-- 静态数据源 -->
                      <template v-if="col.lookup.dataSourceType !== 'remote'">
                        <div class="form-row">
                          <label class="form-label">{{
                            t('数据源 (JSON)')
                          }}</label>
                          <textarea
                            class="form-textarea"
                            :value="
                              JSON.stringify(col.lookup.dataSource, null, 2)
                            "
                            spellcheck="false"
                            @input="(e) => { try { col.lookup!.dataSource = JSON.parse((e.target as HTMLTextAreaElement).value) } catch {} }"
                          />
                          <button
                            class="action-btn-add lookup-edit-btn"
                            @click="
                              openLookupDataSourceDialog(expandedIdx ?? -1)
                            "
                          >
                            {{ t('▦ 数据源编辑') }}
                          </button>
                        </div>
                      </template>
                      <!-- 远程数据源 -->
                      <template v-else>
                        <div class="form-row">
                          <label class="form-label">{{ t('API 服务') }}</label>
                          <div class="api-select-row">
                            <button
                              class="api-select-btn"
                              type="button"
                              @click="openLookupApiSelect"
                            >
                              {{
                                col.lookup.serviceId
                                  ? t('已选：') + col.lookup.serviceId
                                  : t('选择 API 服务')
                              }}
                            </button>
                            <button
                              v-if="col.lookup.serviceId"
                              class="api-clear-btn"
                              type="button"
                              @click="clearLookupApiSelect"
                            >
                              {{ t('清除') }}
                            </button>
                          </div>
                        </div>
                      </template>
                    </div>

                    <!-- 基础资料配置 -->
                    <div
                      v-if="col.editorType === 'baseData' && col.baseDataRef"
                      class="sub-config"
                    >
                      <div class="sub-config-title">{{ t('基础资料配置') }}</div>
                      <div class="form-row">
                        <label class="form-label">{{ t('关联页面') }}</label>
                        <div class="api-select-row">
                          <button
                            class="api-select-btn"
                            type="button"
                            @click="openRefPageSelectDialog"
                          >
                            {{ refPageDisplayName || t('选择基础资料页面') }}
                          </button>
                          <button
                            v-if="col.baseDataRef.refPageId"
                            class="api-clear-btn"
                            type="button"
                            @click="col.baseDataRef.refPageId = ''; col.baseDataRef.valueField = 'id'; col.baseDataRef.displayField = ''"
                          >
                            {{ t('清除') }}
                          </button>
                        </div>
                      </div>
                      <div class="form-row">
                        <label class="form-label">{{ t('显示字段') }}</label>
                        <div class="api-select-row">
                          <button
                            class="api-select-btn"
                            type="button"
                            :disabled="!col.baseDataRef.refPageId"
                            @click="openDisplayFieldSelectDialog"
                          >
                            {{ displayFieldText || t('选择显示字段') }}
                          </button>
                          <button
                            v-if="col.baseDataRef.displayField"
                            class="api-clear-btn"
                            type="button"
                            @click="col.baseDataRef.displayField = ''"
                          >
                            {{ t('清除') }}
                          </button>
                        </div>
                      </div>
                      <div class="form-grid-2col">
                        <div class="form-row">
                          <label class="form-label">{{ t('值字段') }}</label>
                          <input
                            class="form-input"
                            :value="col.baseDataRef.valueField"
                            disabled
                          />
                        </div>
                        <div class="form-row">
                          <label class="form-label">{{ t('源字段') }}</label>
                          <input
                            class="form-input"
                            v-model="col.baseDataRef.sourceField"
                            :placeholder="col.dataField"
                          />
                        </div>
                      </div>
                      <!-- 反写列表配置 -->
                      <div class="form-row" v-if="col.baseDataRef.refPageId">
                        <label class="form-label">{{ t('反写规则') }}</label>
                        <div class="writeback-list">
                          <div
                            v-for="(wb, wbIdx) in (col.baseDataRef.writeBack || (col.baseDataRef.writeBack = []))"
                            :key="wbIdx"
                            class="writeback-row"
                          >
                            <select
                              class="form-input writeback-select"
                              v-model="wb.sourceField"
                            >
                              <option value="">{{ t('源字段') }}</option>
                              <option
                                v-for="f in refPageFields"
                                :key="f.dbField"
                                :value="f.dbField"
                              >{{ f.displayText }}</option>
                            </select>
                            <span class="writeback-arrow">→</span>
                            <select
                              class="form-input writeback-select"
                              v-model="wb.targetField"
                            >
                              <option value="">{{ t('目标字段') }}</option>
                              <option
                                v-for="c in columns"
                                :key="c.dataField"
                                :value="c.dataField"
                              >{{ c.caption || c.dataField }} ({{ c.dataField }})</option>
                            </select>
                            <button
                              class="writeback-del-btn"
                              type="button"
                              @click="col.baseDataRef!.writeBack!.splice(wbIdx, 1)"
                            >✕</button>
                          </div>
                          <button
                            class="writeback-add-btn"
                            type="button"
                            @click="(col.baseDataRef!.writeBack ||= []).push({ sourceField: '', targetField: '' })"
                          >+ {{ t('添加反写') }}</button>
                        </div>
                      </div>
                    </div>

                    <!-- 下拉表格配置 -->
                    <div
                      v-if="
                        col.editorType === 'dropDownGrid' && col.dropDownGrid
                      "
                      class="sub-config"
                    >
                      <div class="sub-config-title">
                        {{ t('下拉表格配置') }}
                        <span class="sub-config-hint"
                          >({{ t('不可编辑、不可嵌套') }})</span
                        >
                      </div>
                      <div class="switch-group">
                        <label class="switch-item">
                          <input
                            type="checkbox"
                            v-model="col.dropDownGrid.inheritParent"
                          />
                          <span>{{ t('继承父表格数据源') }}</span>
                        </label>
                        <label class="switch-item">
                          <input
                            type="checkbox"
                            v-model="col.dropDownGrid.filterRow"
                          />
                          <span>{{ t('行筛选') }}</span>
                        </label>
                      </div>
                      <div
                        class="form-row"
                        v-if="!col.dropDownGrid.inheritParent"
                      >
                        <label class="form-label">{{ t('API 服务') }}</label>
                        <div class="api-select-row">
                          <button
                            class="api-select-btn"
                            type="button"
                            @click="openDdgApiSelect"
                          >
                            {{
                              col.dropDownGrid.serviceId
                                ? t('已选：') + col.dropDownGrid.serviceId
                                : t('选择 API 服务')
                            }}
                          </button>
                          <button
                            v-if="col.dropDownGrid.serviceId"
                            class="api-clear-btn"
                            type="button"
                            @click="clearDdgApiSelect"
                          >
                            {{ t('清除') }}
                          </button>
                        </div>
                      </div>
                      <div class="form-grid-2col">
                        <div class="form-row">
                          <label class="form-label">{{ t('值字段') }}</label>
                          <input
                            class="form-input"
                            v-model="col.dropDownGrid.valueExpr"
                            placeholder="valueExpr"
                          />
                        </div>
                        <div class="form-row">
                          <label class="form-label">{{ t('显示字段') }}</label>
                          <input
                            class="form-input"
                            v-model="col.dropDownGrid.displayExpr"
                            placeholder="displayExpr"
                          />
                        </div>
                        <div class="form-row">
                          <label class="form-label">{{ t('弹窗宽度') }}</label>
                          <input
                            class="form-input"
                            type="number"
                            v-model.number="col.dropDownGrid.popupWidth"
                            placeholder="600"
                          />
                        </div>
                      </div>
                      <!-- 嵌套表格列配置 -->
                      <div class="nested-columns">
                        <div class="nested-col-header">
                          <span class="ncol-label flex1">dataField</span>
                          <span class="ncol-label flex1">caption</span>
                          <span class="ncol-label w60">width</span>
                          <span class="ncol-label w40">{{ t('显示') }}</span>
                          <span class="ncol-label w40">{{ t('反写') }}</span>
                          <span class="ncol-label w24"></span>
                        </div>
                        <div
                          v-for="(ncol, nidx) in col.dropDownGrid.columns"
                          :key="nidx"
                          class="nested-col-row"
                        >
                          <input
                            class="form-input small flex1"
                            v-model="ncol.dataField"
                            placeholder="dataField"
                          />
                          <input
                            class="form-input small flex1"
                            v-model="ncol.caption"
                            placeholder="caption"
                          />
                          <input
                            class="form-input small w60"
                            type="number"
                            v-model.number="ncol.width"
                            placeholder="auto"
                          />
                          <label class="ncol-check w40"
                            ><input
                              type="checkbox"
                              v-model="ncol.visible"
                              :true-value="true"
                              :false-value="false"
                          /></label>
                          <label class="ncol-check w40"
                            ><input
                              type="checkbox"
                              v-model="ncol.writeBack"
                              :true-value="true"
                              :false-value="false"
                          /></label>
                          <button
                            class="row-delete small w24"
                            @click="removeDropDownGridColumn(col, nidx)"
                          >
                            x
                          </button>
                        </div>
                        <button
                          class="action-btn-add small"
                          @click="addDropDownGridColumn(col)"
                        >
                          {{ t('+ 添加列') }}
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>
        <!-- JSON 模式 -->
        <div v-else class="json-editor">
          <div v-if="parseError" class="parse-error">{{ parseError }}</div>
          <textarea
            v-model="rawText"
            class="json-textarea"
            :placeholder="t('请输入 JSON 数组数据...')"
            spellcheck="false"
          />
        </div>
      </div>
      <div class="dialog-footer">
        <button class="dialog-btn dialog-btn-cancel" @click="onCancel">
          {{ t('取消') }}
        </button>
        <button class="dialog-btn dialog-btn-confirm" @click="onConfirm">
          {{ t('确定') }}
        </button>
      </div>
    </div>
    <JsonTableEditorDialog
      v-model:model-value="lookupDialogValue"
      v-model:visible="lookupDialogVisible"
      :title="t('下拉框数据源编辑')"
      :columns="lookupDialogColumns"
      @update:model-value="onLookupDialogConfirm"
    />
    <ApiSelectDialog
      v-model:visible="apiSelectVisible"
      :model-value="
        apiSelectTarget === 'lookup'
          ? columns[expandedIdx ?? -1]?.lookup?.serviceId || ''
          : columns[expandedIdx ?? -1]?.dropDownGrid?.serviceId || ''
      "
      @select="(id: string) => onApiSelect(id)"
    />
    <!-- 基础资料页面选择弹窗 -->
    <PageSelectDialog
      v-model:visible="refPageSelectVisible"
      v-model:model-value="refPageSelectId"
      page-type="basedata"
      @select="(id: string) => onRefPageSelectConfirm(id)"
    />
    <!-- 显示字段选择弹窗（多选） -->
    <FieldSelectDialog
      v-model:visible="displayFieldSelectVisible"
      v-model:model-value="displayFieldSelectId"
      :fields="refPageFields"
      :multiple="true"
      :title="t('选择显示字段')"
      @select="(field: string) => onDisplayFieldSelectConfirm(field)"
    />

    <Teleport to="body">
      <div
        v-if="formImportVisible"
        class="dialog-overlay"
        @click.self="formImportVisible = false"
      >
        <div class="form-import-dialog" :class="{ 'is-maximized': formImportMax, 'is-dragging': formImportDragging }" :style="formImportDragStyle">
          <div class="form-import-title" :class="{ 'is-draggable': !formImportMax }" @mousedown="onFormImportHeaderDown">
            <span>{{ t('从表单导入字段') }}</span>
            <div class="dialog-header-actions">
              <DialogMaxButton :maximized="formImportMax" @toggle="toggleFormImportMax" />
              <button class="dialog-close" @click="formImportVisible = false">✕</button>
            </div>
          </div>
          <div class="form-import-toolbar">
            <input
              v-model="formImportSearch"
              class="form-import-search"
              :placeholder="t('搜索字段名或名称...')"
            />
            <button class="dialog-btn dialog-btn-confirm" @click="formImportSelectAll">
              {{ t('全选') }}
            </button>
            <button class="dialog-btn dialog-btn-cancel" @click="formImportDeselectAll">
              {{ t('取消全选') }}
            </button>
            <span class="form-import-count">{{ t('已选') }} {{ formImportSelectedCount }} {{ t('项') }}</span>
          </div>
          <div class="form-import-list">
            <template v-for="item in formImportFlatList" :key="item.key">
              <div
                class="form-import-row"
                :class="{ 'form-import-group': !item.hasCheckbox }"
                :style="{ paddingLeft: 16 + item.level * 20 + 'px' }"
              >
                <span
                  v-if="item.children?.length"
                  class="form-import-toggle"
                  @click="toggleFormImportExpand(item.key)"
                >{{ item.isExpanded ? '▼' : '▶' }}</span>
                <span v-else class="form-import-toggle-placeholder" />
                <label v-if="item.hasCheckbox" class="form-import-item">
                  <input
                    type="checkbox"
                    :checked="formImportSelected.has(item.key)"
                    @change="toggleFormImportField(item.key)"
                  />
                  <span class="form-import-field">{{ item.label }}</span>
                  <span v-if="item.title" class="form-import-title-label">{{ item.title }}</span>
                </label>
                <span v-else class="form-import-group-label">
                  <span class="form-import-field">{{ item.label }}</span>
                </span>
              </div>
            </template>
            <div
              v-if="!props.formFields || !props.formFields.length"
              class="form-import-empty"
            >
              {{ t('表单页面暂无控件字段，请先在表单页面添加控件') }}
            </div>
            <div
              v-else-if="!formImportFlatList.length"
              class="form-import-empty"
            >
              {{ t('未找到匹配的字段') }}
            </div>
          </div>
          <div class="dialog-footer">
            <button
              class="dialog-btn dialog-btn-cancel"
              @click="formImportVisible = false"
            >
              {{ t('取消') }}
            </button>
            <button
              class="dialog-btn dialog-btn-confirm"
              @click="confirmFormImport"
            >
              {{ t('确定') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.api-select-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.api-select-btn {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  background: #fff;
  cursor: pointer;
  text-align: left;
}
.api-select-btn:hover {
  background: #f5f7fa;
}
.api-clear-btn {
  border: none;
  background: none;
  color: #f56c6c;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
}
/* ===================== 反写列表 ===================== */
.writeback-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}
.writeback-row {
  display: flex;
  align-items: center;
  gap: 4px;
}
.writeback-select {
  flex: 1;
  min-width: 0;
}
.writeback-arrow {
  color: #909399;
  font-size: 12px;
  flex-shrink: 0;
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
/* ===================== 弹窗容器 ===================== */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
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
.dialog-xlarge {
  width: 1300px;
  max-width: 95vw;
}

/* ===================== 头部 ===================== */
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
.dialog-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.mode-toggle {
  border: 1px solid #dcdfe6;
  background: #fff;
  border-radius: 4px;
  padding: 4px 10px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
}
.mode-toggle:hover {
  border-color: #1976d2;
  color: #1976d2;
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

/* ===================== 主体 ===================== */
.dialog-body {
  padding: 12px 16px;
  flex: 1;
  overflow: auto;
}
.column-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.parse-error {
  color: #f56c6c;
  font-size: 12px;
  padding: 8px 12px;
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 4px;
}
.table-empty-tip {
  text-align: center;
  color: #909399;
  font-size: 13px;
  padding: 24px;
  background: #f5f7fa;
  border-radius: 6px;
}

/* ===================== 工具栏 ===================== */
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.search-input {
  flex: 1;
  max-width: 280px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 5px 10px;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
}
.search-input:focus {
  border-color: #1976d2;
}

/* ===================== 左右分栏布局 ===================== */
.column-split-layout {
  display: flex;
  gap: 0;
  align-items: stretch;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  overflow: hidden;
}
.column-list-left {
  flex: 0 0 300px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 600px;
  min-height: 400px;
  overflow-y: auto;
  padding: 10px 10px 10px 10px;
  border-right: 2px solid #dcdfe6;
  background: #fafafa;
}
.column-list-right {
  flex: 1;
  min-width: 0;
  max-height: 600px;
  min-height: 400px;
  overflow-y: auto;
  padding: 10px 14px;
  background: #fff;
}
.col-detail-empty {
  padding: 48px 20px;
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  background: #f5f7fa;
  border-radius: 6px;
}
.col-detail-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  padding: 4px 0 10px;
  border-bottom: 2px solid #1976d2;
  margin-bottom: 8px;
}

/* 分区标题 */
.section-header {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  padding-left: 8px;
  border-left: 3px solid #1976d2;
  margin-top: 2px;
}

/* ===================== 左侧列卡片 ===================== */
.column-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.column-card {
  border: 1px solid #dcdfe6;
  border-radius: 0;
  overflow: hidden;
  transition: border-color 0.15s;
  background: #fff;
  flex-shrink: 0;
}
.column-card.dragging {
  opacity: 0.5;
}
.column-card.drag-over {
  border-color: #1976d2;
  border-style: dashed;
}
.column-card.expanded {
  border-color: #1976d2;
}
.column-card.hidden {
  opacity: 0.55;
  border-style: dashed;
}
.column-card.hidden .col-field {
  text-decoration: line-through;
  color: #c0c4cc;
}
.column-card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 12px;
  background: #f5f7fa;
  cursor: pointer;
  min-height: 40px;
  box-sizing: border-box;
}
.column-card.expanded .column-card-header {
  background: #ecf5ff;
}
.column-card-header:hover {
  background: #f0f7ff;
}
.column-card-summary {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
  font-size: 13px;
  flex-wrap: wrap;
}
.col-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  color: #fff;
  background: #909399;
}
.badge-string { background: #409eff; }
.badge-number { background: #67c23a; }
.badge-date { background: #e6a23c; }
.badge-datetime { background: #e6a23c; }
.badge-boolean { background: #f56c6c; }
.col-field {
  font-weight: 500;
  color: #303133;
}
.col-caption {
  color: #909399;
  font-size: 12px;
}
.col-editor-tag {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  background: #ecf5ff;
  color: #1976d2;
  border: 1px solid #b3d8ff;
}
.col-tag-hide {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  background: #f0f0f0;
  color: #909399;
}
.col-tag-lock {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  background: #fdf6ec;
  color: #e6a23c;
}
.column-card-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.expand-icon {
  color: #909399;
  font-size: 12px;
}
.column-card.expanded .expand-icon {
  color: #1976d2;
}
.row-delete {
  border: none;
  background: transparent;
  color: #f56c6c;
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 12px;
  cursor: pointer;
}
.row-delete:hover {
  background: #fef0f0;
}
.row-delete.small {
  padding: 2px 6px;
  font-size: 11px;
}

/* ===================== 右侧详情面板 ===================== */
.column-card-body {
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 权限码行 */
.perm-code-row {
  background: #f9fafb;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px 12px;
}

/* ===================== 表单字段 ===================== */
.form-grid-2col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
  background: #f9fafb;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px 14px;
}
.form-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.form-label {
  width: 72px;
  font-size: 12px;
  font-weight: 500;
  color: #606266;
  flex-shrink: 0;
  text-align: right;
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
.form-input.small {
  width: 120px;
  flex: none;
}
.form-select {
  flex: 1;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 5px 8px;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
  background: #fff;
  min-width: 0;
  color: #303133;
  cursor: pointer;
}
.form-select:focus {
  border-color: #1976d2;
}
.form-textarea {
  flex: 1;
  min-height: 60px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 5px 8px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  outline: none;
  box-sizing: border-box;
  resize: vertical;
  background: #fff;
  color: #303133;
}
.form-textarea:focus {
  border-color: #1976d2;
}

/* ===================== 开关组 ===================== */
.switch-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 20px;
  padding: 10px 14px;
  background: #f9fafb;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}
.switch-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  white-space: nowrap;
}
.switch-item input {
  width: 15px;
  height: 15px;
  cursor: pointer;
  accent-color: #1976d2;
}

/* ===================== 编辑器区域 ===================== */
.editor-section {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 12px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #fff;
}

/* ===================== 子配置卡片 ===================== */
.sub-config {
  margin-top: 4px;
  padding: 12px 14px;
  background: #f9fafb;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sub-config-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 2px;
}
.sub-config-hint {
  font-size: 11px;
  color: #c0c4cc;
  font-weight: normal;
}

/* ===================== 嵌套列 ===================== */
.nested-columns {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.nested-col-header {
  display: flex;
  gap: 4px;
  align-items: center;
}
.ncol-label {
  font-size: 11px;
  color: #909399;
  text-align: center;
}
.ncol-label.flex1 { flex: 1; }
.ncol-label.w60 { width: 60px; }
.ncol-label.w40 { width: 40px; }
.ncol-label.w24 { width: 24px; }
.nested-col-row {
  display: flex;
  gap: 4px;
  align-items: center;
}
.form-input.small.flex1 {
  flex: 1;
  width: auto;
}
.form-input.small.w60 {
  width: 60px;
  flex: none;
}
.ncol-check {
  display: flex;
  justify-content: center;
  align-items: center;
}
.ncol-check.w40 {
  width: 40px;
  flex: none;
}
.ncol-check input {
  width: 14px;
  height: 14px;
  cursor: pointer;
  accent-color: #1976d2;
}
.row-delete.small.w24 {
  width: 24px;
  flex: none;
  padding: 2px 0;
  text-align: center;
}

/* ===================== 操作按钮 ===================== */
.action-btn-add {
  border: 1px dashed #1976d2;
  background: #ecf5ff;
  color: #1976d2;
  padding: 6px 14px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}
.action-btn-add:hover {
  background: #1976d2;
  color: #fff;
}
.batch-select-all-label {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  white-space: nowrap;
}
.batch-select-all-label input {
  accent-color: #1976d2;
}
.action-btn-batch-delete {
  border: 1px solid #f56c6c;
  background: #fef0f0;
  color: #f56c6c;
  padding: 6px 14px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}
.action-btn-batch-delete:hover {
  background: #f56c6c;
  color: #fff;
}
.col-select-checkbox {
  margin-right: 2px;
  cursor: pointer;
  flex-shrink: 0;
  accent-color: #1976d2;
}
.action-btn-add.small {
  padding: 3px 10px;
  font-size: 11px;
  align-self: flex-start;
}
.lookup-edit-btn {
  margin-top: 6px;
  align-self: flex-start;
}

/* ===================== JSON 模式 ===================== */
.json-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.json-textarea {
  width: 100%;
  min-height: 300px;
  padding: 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'Consolas', 'Monaco', monospace;
  line-height: 1.5;
  resize: vertical;
  box-sizing: border-box;
  outline: none;
}
.json-textarea:focus {
  border-color: #1976d2;
}

/* ===================== 底部 ===================== */
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
}

/* ===================== 表单导入弹窗 ===================== */
.form-import-dialog {
  background: #fff;
  border-radius: 8px;
  width: 720px;
  max-width: 90vw;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}
.form-import-title {
  font-size: 15px;
  font-weight: 600;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
  color: #303133;
}
.form-import-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-bottom: 1px solid #e4e7ed;
  flex-wrap: wrap;
}
.form-import-search {
  flex: 1;
  min-width: 180px;
  padding: 5px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
}
.form-import-search:focus {
  border-color: #1976d2;
}
.form-import-count {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
.form-import-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}
.form-import-row {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 16px;
  cursor: default;
}
.form-import-row:hover {
  background: #f5f7fa;
}
.form-import-group {
  font-weight: 600;
  color: #303133;
  background: #fafafa;
}
.form-import-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  cursor: pointer;
  font-size: 10px;
  color: #909399;
  flex-shrink: 0;
  user-select: none;
}
.form-import-toggle:hover {
  color: #1976d2;
}
.form-import-toggle-placeholder {
  display: inline-block;
  width: 16px;
  flex-shrink: 0;
}
.form-import-item {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 13px;
  flex: 1;
  min-width: 0;
}
.form-import-field {
  font-weight: 500;
  color: #303133;
  white-space: nowrap;
}
.form-import-title-label {
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.form-import-group-label {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}
.form-import-group-label .form-import-field {
  font-weight: 600;
}
.form-import-empty {
  text-align: center;
  color: #c0c4cc;
  padding: 24px 0;
  font-size: 13px;
}

/* ===================== 底部按钮 ===================== */
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
</style>
