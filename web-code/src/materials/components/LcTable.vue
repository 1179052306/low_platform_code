<script setup lang="ts">
import { ref, computed, watch, watchEffect, onMounted, onBeforeUnmount, useSlots, nextTick, h, Teleport, defineAsyncComponent } from 'vue'
import { VxeGrid } from 'vxe-table'
import VxeUI from 'vxe-pc-ui'
import { ElMessage, ElMessageBox, ElButton, ElInput, ElSelect, ElOption, ElDatePicker, ElDialog, ElForm, ElFormItem, ElPagination, ElPopover, ElDropdown, ElDropdownMenu, ElDropdownItem, ElCheckbox } from 'element-plus'
import * as XLSX from 'xlsx'
import { saveAs } from 'file-saver'
import { getSession } from '@/store/auth'
import { loadColumnConfig, saveColumnConfig, type ColumnConfigData } from '@/store/persistence'

const DxBaseDataAsync = defineAsyncComponent(() => import('./DxBaseData.vue'))

const TEXT_FILTER_OPS = [
  { label: '包含', value: 'contains' },
  { label: '不包含', value: 'notcontains' },
  { label: '等于', value: 'equal' },
  { label: '不等于', value: 'notequal' },
  { label: '开始于', value: 'startswith' },
  { label: '结束于', value: 'endswith' },
]
const NUM_FILTER_OPS = [
  { label: '等于', value: 'equal' },
  { label: '不等于', value: 'notequal' },
  { label: '大于', value: 'greaterthan' },
  { label: '小于', value: 'lessthan' },
  { label: '大于等于', value: 'greaterthanorequal' },
  { label: '小于等于', value: 'lessthanorequal' },
  { label: '之间', value: 'between' },
]
const DATE_FILTER_OPS = [
  { label: '等于', value: 'equal' },
  { label: '不等于', value: 'notequal' },
  { label: '大于', value: 'greaterthan' },
  { label: '小于', value: 'lessthan' },
  { label: '大于等于', value: 'greaterthanorequal' },
  { label: '小于等于', value: 'lessthanorequal' },
  { label: '之间', value: 'between' },
]
const LOOKUP_FILTER_OPS = [
  { label: '等于', value: 'equal' },
  { label: '不等于', value: 'notequal' },
]

const _regFilter = (name: string, config: any) => {
  if (!VxeUI.renderer.get(name)) VxeUI.renderer.add(name, config)
}

_regFilter('FilterTextWithOp', {
  renderTableFloatingFilter(renderOpts: any, params: any) {
    const { $table, column, option } = params
    const ops = renderOpts.props?.ops || TEXT_FILTER_OPS
    if (typeof option.data !== 'object' || option.data === null) {
      option.data = { op: ops[0].value, value: '' }
    }
    const op = option.data.op || ops[0].value
    const val = option.data.value || ''
    const setChecked = () => {
      const hasVal = !!(option.data && option.data.value)
      option._checked = hasVal
      option.checked = hasVal
    }
    const triggerFilter = (evnt: any) => {
      setChecked()
      $table.saveFilterByEvent(evnt, column)
    }
    return [
      h('div', { style: 'position:relative;width:100%;height:100%;overflow:hidden' }, [
        h(ElSelect, {
          modelValue: op,
          'onUpdate:modelValue': (v: any) => { option.data = { op: v, value: val } },
          onChange: () => triggerFilter(new Event('change')),
          size: 'small',
          style: 'position:absolute;left:0;top:0;width:70px;z-index:10',
        }, { default: () => ops.map((o: any) => h(ElOption, { value: o.value, label: o.label })) }),
        h(ElInput, {
          modelValue: val,
          'onUpdate:modelValue': (v: any) => { option.data = { op, value: v || '' }; setChecked() },
          onKeydown: (e: any) => { if (e.key === 'Enter') triggerFilter(e) },
          onBlur: () => triggerFilter(new Event('blur')),
          size: 'small',
          clearable: true,
          onClear: () => { option.data = { op, value: '' }; triggerFilter(new Event('clear')) },
          style: 'width:100%;padding-left:74px',
        }),
      ]),
    ]
  },
})

_regFilter('FilterDateWithOp', {
  renderTableFloatingFilter(renderOpts: any, params: any) {
    const { $table, column, option } = params
    const ops = renderOpts.props?.ops || DATE_FILTER_OPS
    if (typeof option.data !== 'object' || option.data === null) {
      option.data = { op: ops[0].value, value: '', value2: '' }
    }
    const op = option.data.op || ops[0].value
    const val = option.data.value || ''
    const val2 = option.data.value2 || ''
    const isBetween = op === 'between'
    const setChecked = () => {
      const hasVal = !!(option.data && option.data.value)
      if (isBetween) {
        option._checked = !!(option.data && option.data.value && option.data.value2)
        option.checked = option._checked
      } else {
        option._checked = hasVal
        option.checked = hasVal
      }
    }
    const triggerFilter = (evnt: any) => {
      setChecked()
      $table.saveFilterByEvent(evnt, column)
    }
    const opStyle = 'position:absolute;left:0;top:0;width:70px;z-index:10'
    const valStyle = 'width:100%;padding-left:74px'
    const opSelect = h(ElSelect, {
      modelValue: op,
      'onUpdate:modelValue': (v: any) => { option.data = { op: v, value: val, value2: val2 } },
      onChange: () => triggerFilter(new Event('change')),
      size: 'small',
      style: opStyle,
    }, { default: () => ops.map((o: any) => h(ElOption, { value: o.value, label: o.label })) })
    const dp1 = h(ElDatePicker, {
      modelValue: val,
      'onUpdate:modelValue': (v: any) => { option.data = { op, value: v || '', value2: val2, ...(isBetween ? { _editing: true } : {}) }; setChecked() },
      onChange: () => triggerFilter(new Event('change')),
      type: 'date',
      size: 'small',
      clearable: true,
      valueFormat: 'YYYY-MM-DD',
      placeholder: '开始',
      style: valStyle,
    })
    if (!isBetween) {
      return [
        h('div', { style: 'position:relative;width:100%;height:100%;overflow:hidden' }, [opSelect, dp1]),
      ]
    }
    const editing = option.data._editing === true
    if (!editing && val && val2) {
      return [
        h('div', { style: 'position:relative;width:100%;height:100%;overflow:hidden' }, [
          opSelect,
          h('div', {
            style: 'width:100%;padding-left:74px;height:100%;display:flex;align-items:center;cursor:pointer;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px;',
            onClick: () => { option.data = { op, value: val, value2: val2, _editing: true } },
          }, `${val} ~ ${val2}`),
        ]),
      ]
    }
    const dp2 = h(ElDatePicker, {
      modelValue: val2,
      'onUpdate:modelValue': (v: any) => { option.data = { op, value: v || '', value2: val2, _editing: true }; setChecked() },
      onChange: () => { const d = option.data; option.data = { op: d.op, value: d.value, value2: d.value2, _editing: false }; triggerFilter(new Event('change')) },
      onBlur: () => { const d = option.data; if (d.value && d.value2) { option.data = { op: d.op, value: d.value, value2: d.value2, _editing: false } } },
      type: 'date',
      size: 'small',
      clearable: true,
      valueFormat: 'YYYY-MM-DD',
      placeholder: '结束',
      style: 'flex:1;min-width:0',
    })
    return [
      h('div', { style: 'position:relative;width:100%;height:100%;overflow:hidden' }, [
        opSelect,
        h('div', { style: 'display:flex;align-items:center;gap:2px;width:100%;padding-left:74px' }, [
          h(ElDatePicker, {
            modelValue: val,
            'onUpdate:modelValue': (v: any) => { option.data = { op, value: v || '', value2: val2, _editing: true }; setChecked() },
            onChange: () => triggerFilter(new Event('change')),
            type: 'date',
            size: 'small',
            clearable: true,
            valueFormat: 'YYYY-MM-DD',
            placeholder: '开始',
            style: 'flex:1;min-width:0',
          }),
          h('span', { style: 'flex-shrink:0;font-size:12px;color:#999' }, '~'),
          dp2,
        ]),
      ]),
    ]
  },
})


_regFilter('FilterSelectWithOp', {
  renderTableFloatingFilter(renderOpts: any, params: any) {
    const { $table, column, option } = params
    const ops = renderOpts.props?.ops || LOOKUP_FILTER_OPS
    const options = renderOpts.props?.options || []
    if (typeof option.data !== 'object' || option.data === null) {
      option.data = { op: ops[0].value, value: '' }
    }
    const op = option.data.op || ops[0].value
    const val = option.data.value ?? ''
    const setChecked = () => {
      const hasVal = !!(option.data && option.data.value !== '' && option.data.value !== null && option.data.value !== undefined)
      option._checked = hasVal
      option.checked = hasVal
    }
    const triggerFilter = (evnt: any) => {
      setChecked()
      $table.saveFilterByEvent(evnt, column)
    }
    return [
      h('div', { style: 'position:relative;width:100%;height:100%;overflow:hidden' }, [
        h(ElSelect, {
          modelValue: op,
          'onUpdate:modelValue': (v: any) => { option.data = { op: v, value: val } },
          onChange: () => triggerFilter(new Event('change')),
          size: 'small',
          style: 'position:absolute;left:0;top:0;width:70px;z-index:10',
        }, { default: () => ops.map((o: any) => h(ElOption, { value: o.value, label: o.label })) }),
        h(ElSelect, {
          modelValue: val,
          'onUpdate:modelValue': (v: any) => { option.data = { op, value: v ?? '' }; setChecked() },
          onChange: () => triggerFilter(new Event('change')),
          size: 'small',
          clearable: true,
          onClear: () => { option.data = { op, value: '' }; triggerFilter(new Event('clear')) },
          style: 'width:100%;padding-left:74px',
        }, { default: () => [
          h(ElOption, { value: '', label: '全部' }),
          ...options.map((o: any) => h(ElOption, { value: o.value, label: o.label })),
        ] }),
      ]),
    ]
  },
})

_regFilter('FilterBaseDataWithOp', {
  renderTableFloatingFilter(renderOpts: any, params: any) {
    const { $table, column, option } = params
    const ops = renderOpts.props?.ops || LOOKUP_FILTER_OPS
    const baseDataRef = renderOpts.props?.baseDataRef || {}
    if (typeof option.data !== 'object' || option.data === null) {
      option.data = { op: ops[0].value, value: '', displayValue: '' }
    }
    const op = option.data.op || ops[0].value
    const val = option.data.value ?? ''
    const dispVal = option.data.displayValue ?? ''
    const setChecked = () => {
      const hasVal = !!(option.data && option.data.value !== '' && option.data.value !== null && option.data.value !== undefined)
      option._checked = hasVal
      option.checked = hasVal
    }
    const triggerFilter = (evnt: any) => {
      setChecked()
      $table.saveFilterByEvent(evnt, column)
    }
    return [
      h('div', { style: 'position:relative;width:100%;height:100%;overflow:hidden' }, [
        h(ElSelect, {
          modelValue: op,
          'onUpdate:modelValue': (v: any) => { option.data = { op: v, value: val, displayValue: dispVal } },
          onChange: () => triggerFilter(new Event('change')),
          size: 'small',
          style: 'position:absolute;left:0;top:0;width:70px;z-index:10',
        }, { default: () => ops.map((o: any) => h(ElOption, { value: o.value, label: o.label })) }),
        h(DxBaseDataAsync, {
          refPageId: baseDataRef.refPageId || '',
          displayField: baseDataRef.displayField || '',
          valueField: baseDataRef.valueField || 'id',
          value: val || null,
          initialDisplayValue: dispVal,
          placeholder: '请选择',
          dropdownWidthOffset: 300,
          style: 'width:100%;padding-left:74px',
          onValueChanged: (v: any, display?: string) => {
            option.data = { op, value: v ?? '', displayValue: display ?? '' }
            setChecked()
            triggerFilter(new Event('change'))
          },
        }),
      ]),
    ]
  },
})

_regFilter('FilterNumberWithOp', {
  renderTableFloatingFilter(renderOpts: any, params: any) {
    const { $table, column, option } = params
    const ops = renderOpts.props?.ops || NUM_FILTER_OPS
    if (typeof option.data !== 'object' || option.data === null) {
      option.data = { op: ops[0].value, value: '', value2: '' }
    }
    const op = option.data.op || ops[0].value
    const val = option.data.value ?? ''
    const val2 = option.data.value2 ?? ''
    const isBetween = op === 'between'
    const setChecked = () => {
      if (isBetween) {
        option._checked = !!(option.data && option.data.value !== '' && option.data.value2 !== '')
        option.checked = option._checked
      } else {
        option._checked = !!(option.data && option.data.value !== '')
        option.checked = option._checked
      }
    }
    const triggerFilter = (evnt: any) => {
      setChecked()
      $table.saveFilterByEvent(evnt, column)
    }
    const opStyle = 'position:absolute;left:0;top:0;width:70px;z-index:10'
    const valStyle = 'width:100%;padding-left:74px'
    const opSelect = h(ElSelect, {
      modelValue: op,
      'onUpdate:modelValue': (v: any) => { option.data = { op: v, value: val, value2: val2 } },
      onChange: () => triggerFilter(new Event('change')),
      size: 'small',
      style: opStyle,
    }, { default: () => ops.map((o: any) => h(ElOption, { value: o.value, label: o.label })) })
    const inp1 = h(ElInput, {
      modelValue: val,
      'onUpdate:modelValue': (v: any) => { option.data = { op, value: v ?? '', value2: val2, ...(isBetween ? { _editing: true } : {}) }; setChecked() },
      onKeydown: (e: any) => { if (e.key === 'Enter') triggerFilter(e) },
      onBlur: () => triggerFilter(new Event('blur')),
      size: 'small',
      clearable: true,
      onClear: () => { option.data = { op, value: '', value2: val2 }; triggerFilter(new Event('clear')) },
      type: 'number',
      style: valStyle,
    })
    if (!isBetween) {
      return [
        h('div', { style: 'position:relative;width:100%;height:100%;overflow:hidden' }, [opSelect, inp1]),
      ]
    }
    const editing = option.data._editing === true
    if (!editing && val !== '' && val2 !== '') {
      return [
        h('div', { style: 'position:relative;width:100%;height:100%;overflow:hidden' }, [
          opSelect,
          h('div', {
            style: 'width:100%;padding-left:74px;height:100%;display:flex;align-items:center;cursor:pointer;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px;',
            onClick: () => { option.data = { op, value: val, value2: val2, _editing: true } },
          }, `${val} ~ ${val2}`),
        ]),
      ]
    }
    const inp2 = h(ElInput, {
      modelValue: val2,
      'onUpdate:modelValue': (v: any) => { option.data = { op, value: val, value2: v ?? '', _editing: true }; setChecked() },
      onKeydown: (e: any) => { if (e.key === 'Enter') { const d = option.data; option.data = { op: d.op, value: d.value, value2: d.value2, _editing: false }; triggerFilter(e) } },
      onBlur: () => { const d = option.data; if (d.value !== '' && d.value2 !== '') { option.data = { op: d.op, value: d.value, value2: d.value2, _editing: false } }; triggerFilter(new Event('blur')) },
      size: 'small',
      clearable: true,
      onClear: () => { option.data = { op, value: val, value2: '', _editing: true }; triggerFilter(new Event('clear')) },
      type: 'number',
      placeholder: '结束',
      style: 'flex:1;min-width:0',
    })
    return [
      h('div', { style: 'position:relative;width:100%;height:100%;overflow:hidden' }, [
        opSelect,
        h('div', { style: 'display:flex;align-items:center;gap:2px;width:100%;padding-left:74px' }, [
          h(ElInput, {
            modelValue: val,
            'onUpdate:modelValue': (v: any) => { option.data = { op, value: v ?? '', value2: val2, _editing: true }; setChecked() },
            onKeydown: (e: any) => { if (e.key === 'Enter') triggerFilter(e) },
            onBlur: () => triggerFilter(new Event('blur')),
            size: 'small',
            clearable: true,
            onClear: () => { option.data = { op, value: '', value2: val2 }; triggerFilter(new Event('clear')) },
            type: 'number',
            placeholder: '开始',
            style: 'flex:1;min-width:0',
          }),
          h('span', { style: 'flex-shrink:0;font-size:12px;color:#999' }, '~'),
          inp2,
        ]),
      ]),
    ]
  },
})

const slots = useSlots()

export interface LcColumn {
  dataField?: string
  caption?: string
  dataType?: string
  width?: number | string
  minWidth?: number
  alignment?: 'left' | 'center' | 'right'
  visible?: boolean
  format?: string | ((value: any) => string)
  allowFiltering?: boolean
  allowSorting?: boolean
  allowEditing?: boolean
  required?: boolean
  allowFixing?: boolean
  allowSummary?: boolean
  lookup?: { dataSource: any[]; valueExpr: string; displayExpr: string }
  filterRowEditorOptions?: { dataSource?: any[]; valueExpr?: string; displayExpr?: string; showClearButton?: boolean }
  editorType?: string
  calculateDisplayValue?: (rowData: any) => any
  fixed?: boolean | string
  cellTemplate?: string
  editCellTemplate?: string
  [k: string]: unknown
}

export interface LcDataSource {
  load?: (loadOptions: any) => Promise<{ data: any[]; totalCount?: number }>
  update?: (key: any, values: Record<string, unknown>) => Promise<any>
  insert?: (values: Record<string, unknown>) => Promise<any>
  remove?: (key: any) => Promise<any>
}

const props = defineProps<{
  dataSource?: any[] | LcDataSource
  columns?: LcColumn[]
  keyExpr?: string
  height?: number | string
  showBorders?: boolean
  showRowLines?: boolean
  showColumnLines?: boolean
  showColumnHeaders?: boolean
  columnAutoWidth?: boolean
  wordWrapEnabled?: boolean
  showOverflow?: boolean
  showHeaderOverflow?: boolean
  autoResize?: boolean
  allowColumnReordering?: boolean
  allowColumnResizing?: boolean
  filterRow?: { visible?: boolean; operation?: string }
  headerFilter?: { visible?: boolean }
  sorting?: { mode?: 'single' | 'multiple' | 'none' }
  selection?: { mode?: 'none' | 'single' | 'multiple'; showCheckBoxesMode?: 'onClick' | 'onLongTap' | 'always' | 'none' }
  editing?: { mode?: 'row' | 'batch' | 'cell' | 'form' | 'popup'; allowAdding?: boolean; allowUpdating?: boolean; allowDeleting?: boolean }
  paging?: { enabled?: boolean; pageSize?: number; pageIndex?: number }
  pager?: { showPageSizeSelector?: boolean; allowedPageSizes?: number[]; showInfo?: boolean; showNavigationButtons?: boolean }
  remoteOperations?: boolean | Record<string, boolean>
  selectedRowKeys?: any[]
  toolbar?: { visible?: boolean; items?: any[] }
  summary?: any
  grouping?: any
  noDataText?: string
  loadPanelEnabled?: boolean
  size?: 'mini' | 'small' | 'medium' | 'large' | 'xlarge'
  stripe?: boolean
  onSelectionChanged?: (e: any) => void
  onRowDblClick?: (e: any) => void
  onContextMenuPreparing?: (e: any) => void
  onContentReady?: (e: any) => void
  onInitialized?: (e: any) => void
  onEditorPreparing?: (e: any) => void
  masterDetail?: { enabled?: boolean; template?: string }
  exportConfig?: { fileName?: string }
  advFilterConditions?: any[]
  minHeight?: number | string
  maxHeight?: number | string
  round?: boolean
  align?: 'left' | 'center' | 'right'
  headerAlign?: 'left' | 'center' | 'right'
  footerAlign?: 'left' | 'center' | 'right'
  showFooter?: boolean
  footerMethod?: any
  footerData?: any[]
  showFooterOverflow?: boolean
  editRules?: any
  editTrigger?: 'click' | 'manual' | 'dblclick'
  editShowStatus?: boolean
  editAutoClear?: boolean
  keepSource?: boolean
  virtualScroll?: boolean
  virtualScrollThreshold?: number
  columnPersistence?: boolean
  pageId?: string
  treeConfig?: any
  expandConfig?: any
  rowDragConfig?: any
  virtualYConfig?: any
  virtualXConfig?: any
  customConfig?: any
  scrollbarConfig?: any
  tooltipConfig?: any
  headerTooltipConfig?: any
  radioConfig?: any
  menuConfig?: any
  keyboardConfig?: any
  clipConfig?: any
  fnrConfig?: any
  zoomConfig?: any
  seqConfig?: any
  spanMethod?: any
  mergeCells?: any
  mergeHeaderCells?: any
  importConfig?: any
  printConfig?: any
  nativeExportConfig?: any
  undoRedoHistoryConfig?: any
  aggregateConfig?: any
  emptyRender?: any
  showSeq?: boolean
  seqWidth?: number
}>()

const emit = defineEmits<{
  'selection-changed': [e: any]
  'row-dbl-click': [e: any]
  'content-ready': [e: any]
  'initialized': [e: any]
  'editor-preparing': [e: any]
  'toolbar-item-click': [e: any]
}>()

const gridRef = ref<any>(null)
const tableData = ref<any[]>([])
const totalCount = ref(0)
const loading = ref(false)
const currentPage = ref((props.paging?.pageIndex ?? 0) + 1)
const pageSize = ref(props.paging?.pageSize ?? 15)
const selectedRowsData = ref<any[]>([])
const selectedKeys = ref<any[]>(props.selectedRowKeys ? [...props.selectedRowKeys] : [])
const searchValue = ref('')
const filterValues = ref<Record<string, { op: string; val: any; val2?: any }>>({})
const editingRowKey = ref<any>(null)
const editingRowData = ref<Record<string, any>>({})
const insertMode = ref(false)
const contextMenu = ref<{ visible: boolean; x: number; y: number; row: any; col: any }>({ visible: false, x: 0, y: 0, row: null, col: null })
const ddgVisible = ref(false)
const ddgCol = ref<any>(null)
const ddgSearchText = ref('')
const ddgData = ref<any[]>([])
const ddgLoading = ref(false)
const ddgSelectedRow = ref<any>(null)
const ddgAllData = ref<any[]>([])
const columnChooserVisible = ref(false)
const columnVisibleState = ref<Record<string, boolean>>({})
const columnOrder = ref<string[]>([])
const columnWidths = ref<Record<string, number>>({})
const columnFixedOverride = ref<Record<string, 'left' | 'right' | undefined>>({})
const advSearchConditions = ref<any[]>([])
const lookupCache = ref<Record<string, any[]>>({})

// 列持久化：防抖保存定时器
let saveColumnConfigTimer: number | null = null

const headerContextMenu = ref<{ visible: boolean; x: number; y: number; field: string }>({ visible: false, x: 0, y: 0, field: '' })

const headerCtxCol = computed(() => visibleColumns.value.find(c => c.dataField === headerContextMenu.value.field))
const headerCtxCanSort = computed(() => headerCtxCol.value?.allowSorting === true && sortMode.value !== 'none')
const headerCtxCanFix = computed(() => headerCtxCol.value?.allowFixing === true)
const headerCtxIsSorted = computed(() => sortState.value.some(s => s.field === headerContextMenu.value.field))
const headerCtxIsFixed = computed(() => {
  const f = headerContextMenu.value.field
  const override = columnFixedOverride.value[f]
  if (override !== undefined) return !!override
  const col = headerCtxCol.value
  return col?.fixed === true || col?.fixed === 'left' || col?.fixed === 'right'
})

const keyField = computed(() => props.keyExpr || 'id')
const isRemote = computed(() => {
  if (typeof props.remoteOperations === 'boolean') return props.remoteOperations
  if (props.remoteOperations && typeof props.remoteOperations === 'object') {
    return Object.values(props.remoteOperations).some(v => v === true)
  }
  return !!props.dataSource && typeof (props.dataSource as any).load === 'function'
})
const filterRowVisible = computed(() => props.filterRow?.visible !== false)
const sortMode = computed(() => props.sorting?.mode || 'single')
const selectionMode = computed(() => props.selection?.mode || 'none')
const showCheckBoxes = computed(() => {
  const mode = props.selection?.showCheckBoxesMode
  return mode !== 'none' && mode !== 'onClick'
})
const editingMode = computed(() => props.editing?.mode || 'cell')
const allowAdding = computed(() => props.editing?.allowAdding || false)
const allowUpdating = computed(() => props.editing?.allowUpdating || false)
const allowDeleting = computed(() => props.editing?.allowDeleting || false)
const hasAnyEditableColumn = computed(() => visibleColumns.value.some(c => c.allowEditing === true))
const pagingEnabled = computed(() => props.paging?.enabled !== false)

const hasSelectionCol = computed(() => showCheckBoxes.value || selectionMode.value === 'multiple')

const visibleColumns = computed(() => {
  const cols = props.columns || []
  const ordered = columnOrder.value.length ? columnOrder.value.map(f => cols.find(c => c.dataField === f)).filter(Boolean) as LcColumn[] : cols
  return ordered.filter(c => {
    if (c.visible === false) return false
    const v = columnVisibleState.value[c.dataField as string]
    return v !== false
  })
})

watchEffect(() => {
  for (const col of visibleColumns.value) {
    const lk = col.lookup
    if (lk && lk.dataSource && typeof (lk.dataSource as any).load === 'function') {
      const field = col.dataField as string
      if (!lookupCache.value[field]) {
        ;(lk.dataSource as any).load().then((data: any[]) => {
          lookupCache.value = { ...lookupCache.value, [field]: data || [] }
        }).catch(() => {
          lookupCache.value = { ...lookupCache.value, [field]: [] }
        })
      }
    }
  }
})

function getLookupDataSource(col: LcColumn): any[] {
  const lk = col.lookup
  if (!lk) return []
  if (Array.isArray(lk.dataSource)) return lk.dataSource
  const field = col.dataField as string
  return lookupCache.value[field] || []
}

function getCellValue(row: any, col: LcColumn): any {
  if (typeof col.calculateDisplayValue === 'function') return col.calculateDisplayValue(row)
  const val = row[col.dataField as string]
  if (col.dataType === 'date' && val) {
    const d = new Date(val)
    if (!isNaN(d.getTime())) {
      return d.toISOString().slice(0, 10)
    }
  }
  return val
}

function getLookupDisplay(value: any, col: LcColumn): string {
  if (!col.lookup) return String(value ?? '')
  const ds = getLookupDataSource(col)
  const item = ds.find((d: any) => String(d[col.lookup!.valueExpr]) === String(value))
  return item ? item[col.lookup.displayExpr] : String(value ?? '')
}

function defaultOp(field: string): string {
  const col = visibleColumns.value.find(c => c.dataField === field)
  if (col?.dataType === 'number') return '='
  return 'contains'
}

function applyFilterValue(field: string, op: string, val: any, val2?: any) {
  if (val === '' || val === null || val === undefined) {
    delete filterValues.value[field]
  } else {
    filterValues.value[field] = { op, val, val2 }
  }
  if (isRemote.value) {
    loadData()
  }
}

function clearFilterValue(field: string) {
  delete filterValues.value[field]
  if (isRemote.value) loadData()
}

function convertFilterToDev(filterVal: any): any[] {
  if (!filterVal) return []
  const result: any[] = []
  for (const [field, cond] of Object.entries(filterVal)) {
    const c = cond as { op: string; val: any }
    const col = visibleColumns.value.find(col => col.dataField === field)
    const tableAlias = (col as any)?.tableAlias || ''
    result.push({ Symbol: 'and', Id: field, Val: c.val, TableAlias: tableAlias })
  }
  return result
}

let _firstLoad = true

async function loadData() {

  if (isRemote.value && props.dataSource && typeof (props.dataSource as any).load === 'function') {
    if (_firstLoad) loading.value = true
    try {
      const loadOptions: any = {
        skip: (currentPage.value - 1) * pageSize.value,
        take: pageSize.value,
        sort: sortState.value.length ? [{ selector: sortState.value[0].field, desc: sortState.value[0].order === 'desc' }] : undefined,
        filter: Object.keys(filterValues.value).length ? Object.entries(filterValues.value).map(([f, c]) => (c as any).op === 'between' ? [f, 'between', (c as any).val, (c as any).val2] : [f, (c as any).op, (c as any).val]) : undefined,
        searchValue: searchValue.value || undefined,
      }
      const result = await (props.dataSource as any).load(loadOptions)
      tableData.value = result.data || []
      totalCount.value = result.totalCount ?? result.data?.length ?? 0
    } catch (e) {
      console.error('[LcTable] load error:', e)
      tableData.value = []
      totalCount.value = 0
    } finally {
      _firstLoad = false
      loading.value = false
    }
  } else if (Array.isArray(props.dataSource)) {
    tableData.value = props.dataSource
    totalCount.value = props.dataSource.length
    _firstLoad = false
  }
}

const displayData = computed(() => {
  if (isRemote.value) return tableData.value
  if (pagingEnabled.value === false) return tableData.value
  const start = (currentPage.value - 1) * pageSize.value
  return tableData.value.slice(start, start + pageSize.value)
})

const autoFooterData = computed(() => {
  const summaryCols = (props.columns || []).filter(c => c.allowSummary && c.dataField)
  if (!summaryCols.length) return null
  const data = isRemote.value ? tableData.value : displayData.value
  const footerRow: Record<string, string> = {}
  for (const col of summaryCols) {
    const field = col.dataField!
    const vals = data.map(r => Number(getCellValue(r, col) || 0)).filter(v => !isNaN(v))
    footerRow[field] = String(vals.reduce((a, b) => a + b, 0))
  }
  return [footerRow]
})

const autoShowFooter = computed(() => props.showFooter || autoFooterData.value !== null)


const totalForPager = computed(() => isRemote.value ? totalCount.value : tableData.value.length)

const sortState = ref<{ field: string; order: 'asc' | 'desc' }[]>([])


function handleSelectionChange(e: any) {
  const records = e.$table ? e.$table.getCheckboxRecords() : (e.records || [])
  selectedRowsData.value = records
  selectedKeys.value = records.map((r: any) => r[keyField.value])
  const event = { selectedRowsData: records, selectedRowKeys: selectedKeys.value }
  props.onSelectionChanged?.(event)
  emit('selection-changed', event)
}

function handleRowDblClick(e: any) {
  const row = e.row
  const event = { row, data: row, rowIndex: e.rowIndex }
  props.onRowDblClick?.(event)
  emit('row-dbl-click', event)
}

function handleRowClick(e: any) {
  if (selectionMode.value === 'single' && !showCheckBoxes.value) {
    const row = e.row
    if (!row) return
    selectedRowsData.value = [row]
    selectedKeys.value = [row[keyField.value]]
    const event = { selectedRowsData: [row], selectedRowKeys: selectedKeys.value }
    props.onSelectionChanged?.(event)
    emit('selection-changed', event)
    const $table = gridRef.value?.getTableRef?.() || (gridRef.value as any)
    $table?.setCurrentRow?.(row)
  }
}

function handlePageChange(e: any) {
  currentPage.value = e.currentPage
  pageSize.value = e.pageSize
  if (isRemote.value) loadData()
}

function handleSortChange({ field, order }: { field: string; order: string | null }) {
  if (!field || !order) {
    sortState.value = []
  } else {
    const o = order === 'asc' ? 'asc' : 'desc'
    if (sortMode.value === 'single') {
      sortState.value = [{ field, order: o }]
    } else {
      sortState.value = [...sortState.value.filter(s => s.field !== field), { field, order: o }]
    }
  }
  if (isRemote.value) loadData()
}

function handleSearch() {
  currentPage.value = 1
  if (isRemote.value) loadData()
}

function handleFilterChange(e: any) {
  if (!isRemote.value) return
  const grid = gridRef.value
  if (!grid) return
  const allFilters = grid.getCheckedFilters?.() || []
  filterValues.value = {}
  for (const f of allFilters) {
    const field = f.column?.field || f.property
    if (field && f.datas?.length) {
      const d = f.datas[0]
      filterValues.value[field] = { op: d.op || 'equal', val: d.value ?? '', val2: d.value2 }
    }
  }
  currentPage.value = 1
  loadData()
}

function refresh() { loadData() }
function getSelectedRowsData() { return selectedRowsData.value }
function getSelectedRowKeys() { return selectedKeys.value }

function startEditRow(row: any) {
  editingRowKey.value = row[keyField.value]
  editingRowData.value = { ...row }
  if (typeof props.onEditorPreparing === 'function') {
    try { props.onEditorPreparing({ row, data: row, component: instance }) } catch (e) { console.error('[LcTable] onEditorPreparing error:', e) }
  }
}
function isEditing(row: any) { return editingRowKey.value === row[keyField.value] }

async function saveEdit() {
  if (isRemote.value && (props.dataSource as any)?.update) {
    try {
      await (props.dataSource as LcDataSource).update?.(editingRowKey.value, editingRowData.value)
      ElMessage.success('保存成功')
    } catch (e) { ElMessage.error('保存失败'); return }
  } else {
    const idx = tableData.value.findIndex(r => r[keyField.value] === editingRowKey.value)
    if (idx >= 0) Object.assign(tableData.value[idx], editingRowData.value)
  }
  editingRowKey.value = null
  editingRowData.value = {}
  loadData()
}

function cancelEdit() {
  editingRowKey.value = null
  editingRowData.value = {}
  insertMode.value = false
}

async function deleteRow(row: any) {
  try {
    await ElMessageBox.confirm('确认删除该行？', '提示', { type: 'warning' })
  } catch { return }
  if (isRemote.value && (props.dataSource as any)?.remove) {
    try {
      await (props.dataSource as LcDataSource).remove?.(row[keyField.value])
      ElMessage.success('删除成功')
    } catch (e) { ElMessage.error('删除失败'); return }
  } else {
    const idx = tableData.value.findIndex(r => r[keyField.value] === row[keyField.value])
    if (idx >= 0) tableData.value.splice(idx, 1)
  }
  loadData()
}

function addRow() {
  if (editingMode.value === 'popup' || editingMode.value === 'form') {
    insertMode.value = true
    editingRowKey.value = null
    editingRowData.value = {}
  } else {
    const newRow: any = {}
    visibleColumns.value.forEach(c => { newRow[c.dataField as string] = null })
    tableData.value.unshift(newRow)
    startEditRow(newRow)
  }
}

function onContextMenu(e: MouseEvent, row: any, col: any) {
  e.preventDefault()
  contextMenu.value = { visible: true, x: e.clientX, y: e.clientY, row, col }
}

function onCellContextMenu(e: MouseEvent) {
  const target = e.target as HTMLElement
  const cell = target.closest('.vxe-body--column')
  if (!cell) return
  const $table = gridRef.value?.getTableRef?.() || (gridRef.value as any)
  if (!$table) return
  const colId = cell.getAttribute('colid')
  const rowId = cell.parentElement?.getAttribute('rowid')
  if (!colId || !rowId) return
  const col = $table.getColumnById?.(colId)
  const row = $table.getRowById?.(rowId)
  if (!col || !row) return
  const colConfig = visibleColumns.value.find(c => c.dataField === col.field)
  if (!colConfig) return
  e.preventDefault()
  contextMenu.value = { visible: true, x: e.clientX, y: e.clientY, row, col: colConfig }
}

function onGridContextMenu(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (target.closest('.vxe-header--column')) {
    onHeaderContextMenu(e)
  } else if (target.closest('.vxe-body--column')) {
    onCellContextMenu(e)
  }
}

function closeContextMenu() { contextMenu.value.visible = false }

function copyCellValue() {
  if (contextMenu.value.row && contextMenu.value.col) {
    const text = String(getCellValue(contextMenu.value.row, contextMenu.value.col))
    navigator.clipboard?.writeText(text)
    ElMessage.success(`已复制: ${text}`)
  }
  closeContextMenu()
}

function copyRowData() {
  if (contextMenu.value.row) {
    const row = contextMenu.value.row
    const text = visibleColumns.value
      .map(c => {
        const v = getCellValue(row, c)
        if (c.lookup) return getLookupDisplay(v, c)
        return v ?? ''
      })
      .join('\t')
    navigator.clipboard?.writeText(text)
    ElMessage.success(`已复制: ${text}`)
  }
  closeContextMenu()
}

function copyRowJsonData() {
  if (contextMenu.value.row) {
    const text = JSON.stringify(contextMenu.value.row)
    navigator.clipboard?.writeText(text)
    ElMessage.success(`已复制: ${text}`)
  }
  closeContextMenu()
}

function onHeaderContextMenu(e: MouseEvent) {
  const target = e.target as HTMLElement
  const th = target.closest('.vxe-header--column')
  if (!th) return
  const colId = th.getAttribute('colid')
  const $table = gridRef.value?.getTableRef?.() || (gridRef.value as any)
  const col = $table?.getColumnById?.(colId)
  if (!col?.field) return
  const colConfig = visibleColumns.value.find(c => c.dataField === col.field)
  if (!colConfig) return
  const canSort = colConfig.allowSorting === true && sortMode.value !== 'none'
  const canFix = colConfig.allowFixing === true
  if (!canSort && !canFix) return
  e.preventDefault()
  headerContextMenu.value = { visible: true, x: e.clientX, y: e.clientY, field: col.field }
}

function closeHeaderContextMenu() { headerContextMenu.value.visible = false }

function closeEditOnOutsideMousedown(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (!target) return
  if (target.closest('.el-popper') || target.closest('.el-overlay') || target.closest('.el-select-dropdown') || target.closest('.el-date-editor') || target.closest('.base-data-dropdown')) return
  if (target.closest('input, select, textarea, button, .el-input, .el-select, .el-select-v2, .dx-base-data-control, .vxe-cell--edit-wrapper, .vxe-cell--edit')) return
  const $table = gridRef.value?.getTableRef?.() || gridRef.value
  if ($table?.clearEdit && $table?.getEditCell) {
    if ($table.getEditCell()) {
      $table.clearEdit()
    }
  }
}

function headerSortAsc() {
  const field = headerContextMenu.value.field
  gridRef.value?.sort?.(field, 'asc')
  closeHeaderContextMenu()
}
function headerSortDesc() {
  const field = headerContextMenu.value.field
  gridRef.value?.sort?.(field, 'desc')
  closeHeaderContextMenu()
}
function headerClearSort() {
  gridRef.value?.clearSort?.()
  closeHeaderContextMenu()
}
function headerFixLeft() {
  columnFixedOverride.value[headerContextMenu.value.field] = 'left'
  closeHeaderContextMenu()
}
function headerFixRight() {
  columnFixedOverride.value[headerContextMenu.value.field] = 'right'
  closeHeaderContextMenu()
}
function headerUnfix() {
  columnFixedOverride.value[headerContextMenu.value.field] = undefined
  closeHeaderContextMenu()
}

function exportToExcel(allData?: boolean) {
  const data = allData ? tableData.value : (selectedRowsData.value.length ? selectedRowsData.value : tableData.value)
  doExport(data)
}
function exportPageData() {
  doExport(displayData.value)
}
function exportSelected() {
  if (!selectedRowsData.value.length) {
    ElMessage.warning('请先选择要导出的行')
    return
  }
  doExport(selectedRowsData.value)
}
function exportQuery() { doExport(tableData.value) }

function openColumnConfig() {
  const cols = props.columns || []
  if (!columnOrder.value.length || columnOrder.value.length !== cols.length) {
    columnOrder.value = cols.map(c => c.dataField as string)
  }
  cols.forEach(c => {
    const f = c.dataField as string
    if (columnVisibleState.value[f] === undefined) {
      columnVisibleState.value[f] = c.visible !== false
    }
  })
  columnChooserVisible.value = true
}

function toggleColumnVisible(field: string) {
  columnVisibleState.value[field] = !columnVisibleState.value[field]
  scheduleSaveColumnConfig()
}

const colDragIdx = ref(-1)
const colDragOverIdx = ref(-1)

function onColDragStart(idx: number) {
  colDragIdx.value = idx
}
function onColDragOver(idx: number) {
  if (idx === colDragIdx.value) return
  colDragOverIdx.value = idx
}
function onColDrop(idx: number) {
  const from = colDragIdx.value
  const to = idx
  if (from < 0 || from === to) { onColDragEnd(); return }
  const arr = [...columnOrder.value]
  const [moved] = arr.splice(from, 1)
  arr.splice(to, 0, moved)
  columnOrder.value = arr
  onColDragEnd()
  scheduleSaveColumnConfig()
}
function onColDragEnd() {
  colDragIdx.value = -1
  colDragOverIdx.value = -1
}

function resetColumnConfig() {
  const cols = props.columns || []
  columnOrder.value = cols.map(c => c.dataField as string)
  columnVisibleState.value = {}
  columnWidths.value = {}
  columnChooserVisible.value = false
  scheduleSaveColumnConfig()
}

// ================ 列配置持久化（后端存储） ================

/** 收集当前列配置为可保存的结构 */
function collectColumnConfig(): ColumnConfigData {
  return {
    columnOrder: [...columnOrder.value],
    columnVisibleState: { ...columnVisibleState.value },
    columnWidths: { ...columnWidths.value },
    updatedAt: new Date().toISOString(),
  }
}

/** 防抖保存列配置到后端（500ms 内多次变更只保存一次） */
function scheduleSaveColumnConfig() {
  if (!props.columnPersistence || !props.pageId) return
  const session = getSession()
  if (!session?.username) return
  if (saveColumnConfigTimer) window.clearTimeout(saveColumnConfigTimer)
  saveColumnConfigTimer = window.setTimeout(() => {
    saveColumnConfigTimer = null
    saveColumnConfig(session.username, props.pageId!, collectColumnConfig())
  }, 500)
}

/** vxe-grid 列宽调整事件：记录列宽并触发持久化保存 */
function handleColumnResize({ column, resizeWidth }: any) {
  const field = column?.field
  if (!field) return
  columnWidths.value[field] = resizeWidth
  scheduleSaveColumnConfig()
}

/** 从后端加载列配置并应用到当前表格 */
async function applyPersistedColumnConfig() {
  if (!props.columnPersistence || !props.pageId) return
  const session = getSession()
  if (!session?.username) return
  const cfg = await loadColumnConfig(session.username, props.pageId)
  if (!cfg) return
  const cols = props.columns || []
  const validFields = new Set(cols.map(c => c.dataField as string))
  // 应用列顺序：只保留当前 schema 中仍存在的字段，再补上新增字段
  if (cfg.columnOrder?.length) {
    const persisted = cfg.columnOrder.filter(f => validFields.has(f))
    const remaining = cols.map(c => c.dataField as string).filter(f => !persisted.includes(f))
    columnOrder.value = [...persisted, ...remaining]
  }
  // 应用列可见性
  if (cfg.columnVisibleState) {
    const vis: Record<string, boolean> = {}
    for (const f of validFields) {
      if (cfg.columnVisibleState[f] !== undefined) vis[f] = cfg.columnVisibleState[f]
    }
    columnVisibleState.value = vis
  }
  // 应用列宽
  if (cfg.columnWidths) {
    const w: Record<string, number> = {}
    for (const f of validFields) {
      if (cfg.columnWidths[f] && cfg.columnWidths[f] > 0) w[f] = cfg.columnWidths[f]
    }
    columnWidths.value = w
  }
}

function handleExportCommand(cmd: string) {
  if (cmd === 'export_page') exportPageData()
  else if (cmd === 'export_selected') exportSelected()
  else if (cmd === 'export_query') exportQuery()
}
function doExport(data: any[]) {
  const cols = visibleColumns.value
  const headerRow = cols.map(c => c.caption || c.dataField || '')
  const dataRows = data.map((r: any) => cols.map(c => getCellValue(r, c)))
  const ws = XLSX.utils.aoa_to_sheet([headerRow, ...dataRows])
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, 'Sheet1')
  saveAs(new Blob([XLSX.write(wb, { type: 'array', bookType: 'xlsx' })]), `${props.exportConfig?.fileName || 'export'}.xlsx`)
}

function openDdgEditor(col: any, row: any) {
  ddgCol.value = col
  ddgVisible.value = true
  ddgSearchText.value = ''
  ddgSelectedRow.value = null
  const ddg = col.dropDownGrid
  if (ddg?.dataSource?.load) {
    ddgLoading.value = true
    ddg.dataSource.load({ skip: 0, take: 15, searchValue: '' }).then((res: any) => {
      ddgData.value = res.data || []
      ddgAllData.value = res.data || []
      ddgLoading.value = false
    }).catch(() => { ddgLoading.value = false })
  }
}
function confirmDdg() {
  if (ddgSelectedRow.value && ddgCol.value) {
    editingRowData.value[ddgCol.value.dataField] = ddgSelectedRow.value[ddgCol.value.dropDownGrid.valueExpr || 'id']
  }
  ddgVisible.value = false
}

const summaryComputed = computed(() => {
  if (!props.summary) return null
  const totalItems = props.summary?.totalItems || props.summary?.groupItems || []
  if (!totalItems.length) return null
  const result: Record<string, number> = {}
  const data = displayData.value
  for (const item of totalItems) {
    const col = item.column || item.name
    const type = item.summaryType || item.type
    const vals = data.map(r => Number(getCellValue(r, { dataField: col }) || 0))
    if (type === 'sum') result[col] = vals.reduce((a, b) => a + b, 0)
    else if (type === 'count') result[col] = data.length
    else if (type === 'avg') result[col] = vals.length ? vals.reduce((a, b) => a + b, 0) / vals.length : 0
    else if (type === 'min') result[col] = vals.length ? Math.min(...vals) : 0
    else if (type === 'max') result[col] = vals.length ? Math.max(...vals) : 0
  }
  return result
})

function getSummaryText() { return '合计' }

function computeGridColumns(): any[] {
  const result: any[] = []
  if (hasSelectionCol.value) {
    result.push({ colId: 'lc-checkbox-col', type: 'checkbox', width: 50, align: 'center', resizable: false })
  }
  if (props.showSeq) {
    result.push({ colId: 'lc-seq-col', type: 'seq', width: props.seqWidth || 60, align: 'center', resizable: false })
  }
  if (props.masterDetail?.enabled) {
    result.push({ colId: 'lc-expand-col', type: 'expand', width: 50, fixed: 'left', slots: { content: 'expandContent' } })
  }
  const baseDataRefBySource: Record<string, any> = {}
  for (const col of visibleColumns.value) {
    const bdRef = col.baseDataRef as any
    if (bdRef?.sourceField) {
      baseDataRefBySource[bdRef.sourceField as string] = bdRef
    }
  }
  for (const col of visibleColumns.value) {
    const persistedWidth = columnWidths.value[col.dataField as string]
    const vxeCol: any = {
      colId: `lc-col-${col.dataField}`,
      field: col.dataField,
      title: col.caption || col.dataField,
      width: persistedWidth && persistedWidth > 0 ? persistedWidth : (typeof col.width === 'number' ? col.width : undefined),
      minWidth: col.minWidth || 80,
      align: col.alignment || 'left',
      fixed: columnFixedOverride.value[col.dataField as string] !== undefined
        ? columnFixedOverride.value[col.dataField as string]
        : (col.fixed === true ? 'left' : (col.fixed as string) || undefined),
      sortable: col.allowSorting !== false && sortMode.value !== 'none',
      resizable: props.allowColumnResizing !== false,
      showOverflow: props.showOverflow !== undefined ? props.showOverflow : !props.wordWrapEnabled,
    }
    if (col.required) {
      if (!vxeCol.slots) vxeCol.slots = {}
      vxeCol.slots.header = () => [
        h('span', {}, vxeCol.title),
        h('span', { style: 'color: #f56c6c; margin-left: 2px; font-weight: bold' }, '*'),
      ]
    }
    if (col.lookup || typeof (col as any).calculateDisplayValue === 'function') {
      if (!vxeCol.slots) vxeCol.slots = {}
      vxeCol.slots.default = `cell_${col.dataField}`
    }
    if (filterRowVisible.value && col.allowFiltering !== false) {
      const bdRef = col.baseDataRef || baseDataRefBySource[col.dataField as string]
      if (bdRef) {
        vxeCol.filters = [{ data: { op: 'equal', value: '' } }]
        vxeCol.filterRender = { name: 'FilterBaseDataWithOp', props: { ops: LOOKUP_FILTER_OPS, baseDataRef: bdRef } }
      } else if (col.lookup || col.filterRowEditorOptions?.dataSource) {
        const rawDs = col.filterRowEditorOptions?.dataSource || col.lookup?.dataSource || []
        const ds: any[] = Array.isArray(rawDs) ? rawDs : (lookupCache.value[col.dataField as string] || [])
        const selectOptions = ds.map((d: any) => ({ label: d[col.filterRowEditorOptions?.displayExpr || col.lookup?.displayExpr || 'text'], value: d[col.filterRowEditorOptions?.valueExpr || col.lookup?.valueExpr || 'value'] }))
        vxeCol.filters = [{ data: { op: 'equal', value: '' } }]
        vxeCol.filterRender = { name: 'FilterSelectWithOp', props: { ops: LOOKUP_FILTER_OPS, options: selectOptions } }
      } else if (col.dataType === 'date' || col.dataType === 'datetime') {
        vxeCol.filters = [{ data: { op: 'equal', value: '' } }]
        vxeCol.filterRender = { name: 'FilterDateWithOp', props: { ops: DATE_FILTER_OPS } }
      } else if (col.dataType === 'number') {
        vxeCol.filters = [{ data: { op: 'equal', value: '' } }]
        vxeCol.filterRender = { name: 'FilterNumberWithOp', props: { ops: NUM_FILTER_OPS } }
      } else {
        vxeCol.filters = [{ data: { op: 'contains', value: '' } }]
        vxeCol.filterRender = { name: 'FilterTextWithOp', props: { ops: TEXT_FILTER_OPS } }
      }
      vxeCol.filterMethod = ({ row, column, option }: any) => {
        const d = option.data
        if (!d || d.value === '' || d.value === null || d.value === undefined) return true
        const cellVal = row[column.field]
        const filterVal = d.value
        const fop = d.op || 'equal'
        if (fop === 'equal') return cellVal == filterVal
        if (fop === 'notequal') return cellVal != filterVal
        if (fop === 'contains') return String(cellVal ?? '').includes(String(filterVal))
        if (fop === 'notcontains') return !String(cellVal ?? '').includes(String(filterVal))
        if (fop === 'startswith') return String(cellVal ?? '').startsWith(String(filterVal))
        if (fop === 'endswith') return String(cellVal ?? '').endsWith(String(filterVal))
        if (fop === 'greaterthan') return Number(cellVal) > Number(filterVal)
        if (fop === 'lessthan') return Number(cellVal) < Number(filterVal)
        if (fop === 'greaterthanorequal') return Number(cellVal) >= Number(filterVal)
        if (fop === 'lessthanorequal') return Number(cellVal) <= Number(filterVal)
        if (fop === 'between') return Number(cellVal) >= Number(filterVal) && Number(cellVal) <= Number(d.value2)
        return true
      }
    }
    if (col.allowEditing === true || (allowUpdating.value && col.allowEditing !== false)) {
      vxeCol.editRender = {}
      if (!vxeCol.slots) vxeCol.slots = {}
      const editCol = col
      vxeCol.slots.edit = (params: any) => {
        const { row, $table } = params
        const field = editCol.dataField as string
        const finishEdit = () => { if ($table?.clearEdit) $table.clearEdit() }
        const focusInput = (vnode: any) => {
          nextTick(() => {
            const el = vnode?.el as HTMLElement
            if (!el) return
            const input = el.querySelector('input, textarea') as HTMLElement | null
            if (input) input.focus()
          })
        }
        if (editCol.editorType === 'baseData' && editCol.baseDataRef) {
          const bdRef = editCol.baseDataRef as any

          return h(DxBaseDataAsync, {
            refPageId: bdRef.refPageId || '',
            displayField: bdRef.displayField || '',
            valueField: bdRef.valueField || 'id',
            value: row[field] ?? null,
            size: 'small',
            autoFocus: true,
            style: 'width: 100%',
            onSelectionChanged: (selectedRow: any) => {
              if (selectedRow) {
                const vf = (bdRef.valueField || 'id').toUpperCase()
                row[field] = selectedRow[vf]
                if (bdRef.writeBack && Array.isArray(bdRef.writeBack)) {
                  for (const wb of bdRef.writeBack) {
                    if (wb.sourceField && wb.targetField) {
                      row[wb.targetField] = selectedRow[wb.sourceField.toUpperCase()]
                    }
                  }
                }
              }
              finishEdit()
            },
            onValueChanged: () => { finishEdit() },
            onDropdownClosed: () => { finishEdit() },
          })
        }
        if (editCol.lookup) {
          const ds = getLookupDataSource(editCol)
          return h(ElSelect, {
            modelValue: row[field],
            'onUpdate:modelValue': (val: any) => { row[field] = val },
            onChange: () => finishEdit(),
            size: 'small',
            style: 'width: 100%',
            onVnodeMounted: focusInput,
          }, () => ds.map(opt => h(ElOption, {
            key: opt[editCol.lookup!.valueExpr],
            label: opt[editCol.lookup!.displayExpr],
            value: opt[editCol.lookup!.valueExpr],
          })))
        }
        if (editCol.dataType === 'date' || editCol.dataType === 'datetime') {
          return h(ElDatePicker, {
            modelValue: row[field],
            'onUpdate:modelValue': (val: any) => { row[field] = val },
            onChange: () => finishEdit(),
            size: 'small',
            type: editCol.dataType === 'datetime' ? 'datetime' : 'date',
            style: 'width: 100%',
            onVnodeMounted: focusInput,
          })
        }
        return h(ElInput, {
          modelValue: row[field],
          'onUpdate:modelValue': (val: any) => { row[field] = val },
          size: 'small',
          type: editCol.dataType === 'number' ? 'number' : 'text',
          style: 'width: 100%',
          onVnodeMounted: focusInput,
          onKeydown: (e: any) => { if (e.key === 'Enter') finishEdit() },
          onBlur: () => finishEdit(),
        })
      }
    }
    if (col.cellTemplate && slots[col.cellTemplate]) {
      if (!vxeCol.slots) vxeCol.slots = {}
      vxeCol.slots.default = `cell_${col.dataField}`
    }
    result.push(vxeCol)
  }
  if (allowUpdating.value || allowDeleting.value) {
    result.push({ colId: 'lc-operation-col', title: '操作', width: 80, fixed: 'right', slots: { default: 'operationCell' } })
  }
  return result
}

const gridColumns = ref<any[]>([])
let _prevColsKey = ''
watchEffect(() => {
  const cols = computeGridColumns()
  const key = JSON.stringify(cols, (_k, v: any) => {
    if (typeof v === 'function') return '__fn__'
    return v
  })
  if (key !== _prevColsKey) {
    _prevColsKey = key
    gridColumns.value = cols
  }
})

const proxyConfig = computed(() => {
  if (!isRemote.value) return undefined
  return {
    enabled: true,
    seq: true,
    filter: true,
    sort: true,
    response: {
      result: 'result',
      total: 'total',
    },
    ajax: {
      query: async ({ page, sort, filters }: any) => {
        const loadOptions: any = {
          skip: page ? (page.currentPage - 1) * page.pageSize : 0,
          take: page ? page.pageSize : pageSize.value,
        }
        if (sort && sort.field) {
          loadOptions.sort = [{ selector: sort.field, desc: sort.order === 'desc' }]
        }
        if (filters && Array.isArray(filters) && filters.length) {
          const filterArr: any[] = []
          for (const f of filters) {
            const datas = (f.datas || []).filter((v: any) => v !== '' && v != null)
            if (datas.length) {
              const d = datas[0]
              if (typeof d === 'object' && d !== null && d.op) {
                if (d.value !== '' && d.value != null) {
                  filterArr.push([f.field, d.op, d.value])
                }
              } else {
                filterArr.push([f.field, 'contains', d])
              }
            }
          }
          if (filterArr.length) loadOptions.filter = filterArr
        }
        if (searchValue.value) loadOptions.searchValue = searchValue.value
        const result = await (props.dataSource as LcDataSource).load?.(loadOptions)
        if (!result) return { result: [], total: 0 }

        return { result: result.data, total: result.totalCount || result.data.length }
      },
      insert: async ({ body }: any) => {
        await (props.dataSource as LcDataSource).insert?.(body.insertRecords[0])
      },
      update: async ({ body }: any) => {
        const row = body.updateRecords[0]
        await (props.dataSource as LcDataSource).update?.(row[keyField.value], row)
      },
      delete: async ({ body }: any) => {
        const row = body.removeRecords[0]
        await (props.dataSource as LcDataSource).remove?.(row[keyField.value])
      },
    },
  }
})

const OP_LABELS: Record<string, string> = {
  equal: '等于', notEqual: '不等于', '=': '等于', '<>': '不等于',
  contains: '包含', notContains: '不包含',
  startsWith: '开始于', endsWith: '结束于',
  greaterThan: '大于', greaterThanOrEqual: '大于等于',
  lessThan: '小于', lessThanOrEqual: '小于等于',
  between: '介于', notBetween: '不介于',
  isNull: '为空', isNotNull: '不为空',

}

const activeFilterTags = computed(() => {
  const tags: { key: string; label: string }[] = []
  const cols = props.columns || []
  const colMap = new Map(cols.map(c => [c.dataField as string, c]))
  for (const [field, cond] of Object.entries(filterValues.value)) {
    if (cond.val === '' || cond.val === null || cond.val === undefined) continue
    const col = colMap.get(field)
    const fieldName = col?.caption || field
    const opLabel = OP_LABELS[cond.op] || cond.op
    let valText = String(cond.val)
    if (col?.lookup) {
      const ds = getLookupDataSource(col)
      const item = ds?.find((d: any) => d[col.lookup!.valueExpr] === cond.val)
      if (item) valText = String(item[col.lookup!.displayExpr])
    }
    if (cond.op === 'between' && cond.val2 !== undefined) valText += ` ~ ${cond.val2}`
    tags.push({ key: `filter:${field}`, label: `${fieldName} ${opLabel} ${valText}` })
  }
  for (const cond of (props.advFilterConditions || advSearchConditions.value)) {
    if (!cond.field || cond.value === '' || cond.value === null || cond.value === undefined) continue
    const col = colMap.get(cond.field)
    const fieldName = col?.caption || cond.field
    const isMulti = String(cond.value).includes('\n')
    let opLabel = OP_LABELS[cond.operator] || cond.operator
    if (isMulti) opLabel = cond.operator === '<>' ? '不包含于' : '包含于'
    let valText = String(cond.value)
    if (isMulti) valText = valText.split('\n').filter(v => v.trim()).join(', ')
    if (col?.lookup) {
      const ds = getLookupDataSource(col)
      const item = ds?.find((d: any) => d[col.lookup!.valueExpr] === cond.value)
      if (item) valText = String(item[col.lookup!.displayExpr])
    }
    if (cond.operator === 'between' && cond.value2 !== undefined) valText += ` ~ ${cond.value2}`
    tags.push({ key: `adv:${cond.field}`, label: `${fieldName} ${opLabel} ${valText}` })
  }
  if (searchValue.value) {
    tags.push({ key: 'search', label: `搜索: ${searchValue.value}` })
  }
  return tags
})

const showFilterDetail = ref(false)

function clearAllFilters() {
  filterValues.value = {}
  advSearchConditions.value = []
  searchValue.value = ''
  sortState.value = []
  const grid = gridRef.value
  grid?.clearFilter?.()
  grid?.clearSort?.()
  currentPage.value = 1
  loadData()
}

const filterConfig = computed(() => ({
  remote: isRemote.value,
  showIcon: false,
}))

const floatingFilterConfig = computed(() => ({
  enabled: filterRowVisible.value,
}))

const editConfig = computed(() => {
  if (editingMode.value === 'form' || editingMode.value === 'popup') return undefined
  if (!allowUpdating.value && !allowAdding.value && !hasAnyEditableColumn.value) return undefined
  const cfg: any = {
    trigger: (props.editTrigger || 'dblclick') as 'click' | 'manual' | 'dblclick',
    mode: (editingMode.value === 'row' ? 'row' : 'cell') as 'cell' | 'row',
    autoFocus: true,
    showStatus: props.editShowStatus !== false,
    beforeEditMethod: ({ row, column }: any) => {
      const col = visibleColumns.value.find(c => c.dataField === column.field)
      if (!col) return true
      if (col.allowEditing === true) return true
      if (col.allowEditing === false) return false
      return allowUpdating.value
    },
  }
  if (props.editAutoClear !== undefined) cfg.autoClear = props.editAutoClear
  else cfg.autoClear = true
  return cfg
})

const sortConfig = computed(() => ({
  remote: isRemote.value,
  multiple: sortMode.value === 'multiple',
  trigger: 'cell' as const,
}))

const pagerConfig = computed(() => undefined)

const checkboxConfig = computed(() => ({
  range: selectionMode.value === 'multiple',
  highlight: true,
  trigger: 'row' as const,
}))

const rowConfig = computed(() => ({
  keyField: keyField.value,
  isHover: true,
  isCurrent: true,
}))

const columnConfig = computed(() => ({
  resizable: props.allowColumnResizing !== false,
  isHover: true,
  drag: props.allowColumnReordering !== false,
}))

const columnDragConfig = computed(() => ({
  trigger: 'cell' as const,
  showIcon: false,
  dragStartMethod({ column }: { column: any }) {
    const t = column.type
    if (t === 'seq' || t === 'checkbox' || t === 'radio' || t === 'expand') return false
    const cid = column.colId || column.field || ''
    if (cid === 'lc-checkbox-col' || cid === 'lc-seq-col' || cid === 'lc-expand-col') return false
    return true
  },
}))

const virtualYConfigComputed = computed(() => {
  if (props.virtualYConfig) return props.virtualYConfig
  if (props.virtualScroll) return { gt: props.virtualScrollThreshold ?? 60 }
  return undefined
})

const customConfigComputed = computed(() => {
  if (props.customConfig) return props.customConfig
  // 列持久化由本组件手动管理（后端存储），禁用 vxe-table 内置 localStorage
  if (props.columnPersistence) return { storage: false }
  return undefined
})

const toolbarConfig = computed(() => {
  if (props.toolbar?.visible === false) return undefined
  const buttons: any[] = []
  if (allowAdding.value) buttons.push({ code: 'add', name: '新增' })
  if (allowUpdating.value) buttons.push({ code: 'edit', name: '修改' })
  if (allowDeleting.value) buttons.push({ code: 'delete', name: '删除' })
  if (props.toolbar?.items) {
    for (const item of props.toolbar.items) {
      if (item.visible !== false && !['add', 'edit', 'delete', 'export', 'refresh', 'columnConfig', 'columnChooser'].includes(item.name)) {
        buttons.push({ code: item.name, name: item.caption || item.name })
      }
    }
  }
  return { buttons }
})

function handleToolbarClick({ code }: any) {
  if (code === 'add') addRow()
  else if (code === 'edit') {
    const row = selectedRowsData.value[0]
    if (row) startEditRow(row)
  }
  else if (code === 'delete') {
    selectedRowsData.value.forEach(row => deleteRow(row))
  }
  else if (code === 'columnConfig') {
    openColumnConfig()
  }
  else if (code === 'refresh') {
    loadData()
  }
  else {
    const item = props.toolbar?.items?.find((i: any) => i.name === code)
    if (item?.onClick) {
      try {
        const fn = new Function('e', 'api', 'grid', item.onClick)
        fn({}, {}, instance)
      } catch (e) { console.error('[LcTable] toolbar onClick error:', e) }
    }
    emit('toolbar-item-click', { name: code })
  }
}

const showToolbar = computed(() => props.toolbar?.visible !== false && (allowAdding.value || allowDeleting.value || allowUpdating.value || (props.toolbar?.items?.length || 0) > 0))

const instance = {
  refresh, loadData, getSelectedRowsData, getSelectedRowKeys,
  exportToExcel, exportPageData, exportSelected, exportQuery,
  openColumnConfig,
  addRow, deleteRow, startEditRow, saveEdit, cancelEdit,
  get option() { return (name: string, value?: any) => { if (value !== undefined) (this as any)[name] = value; return (this as any)[name] } },
  cellValue: (rowIndex: number, field: string, value?: any) => {
    const row = displayData.value[rowIndex]
    if (!row) return
    if (value !== undefined) row[field] = value
    return row[field]
  },
  getRowIndexByKey: (key: any) => displayData.value.findIndex(r => r[keyField.value] === key),
  editRow: (idx: number) => { const row = displayData.value[idx]; if (row) startEditRow(row) },
  saveEditData: saveEdit,
  cancelEditData: cancelEdit,
}


let styleInterval: number | null = null


const forceHeaderStyle = () => {
  const el = gridRef.value?.$el as HTMLElement
  if (!el) return

  el.querySelectorAll('.is--floating-filters .vxe-header--column, .is--floating-filters .vxe-cell, .is--floating-filters .vxe-cell--wrapper').forEach((e: Element) => {
    (e as HTMLElement).style.setProperty('overflow', 'visible', 'important')
  })
}

let dragScrollState: {
  el: HTMLElement
  startX: number
  startY: number
  scrollLeft: number
  scrollTop: number
  dragging: boolean
} | null = null

function onDragMouseMove(e: MouseEvent) {
  if (!dragScrollState) return
  const dx = e.clientX - dragScrollState.startX
  const dy = e.clientY - dragScrollState.startY
  if (!dragScrollState.dragging && Math.abs(dx) < 5 && Math.abs(dy) < 5) return
  if (!dragScrollState.dragging) {
    dragScrollState.dragging = true
    dragScrollState.el.style.cursor = 'grabbing'
    dragScrollState.el.style.userSelect = 'none'
  }
  dragScrollState.el.scrollLeft = dragScrollState.scrollLeft - dx
  dragScrollState.el.scrollTop = dragScrollState.scrollTop - dy
}

function onDragMouseUp() {
  if (!dragScrollState) return
  if (dragScrollState.dragging) {
    const el = dragScrollState.el
    const blockClick = (ev: Event) => {
      ev.stopPropagation()
      ev.preventDefault()
      el.removeEventListener('click', blockClick, true)
    }
    el.addEventListener('click', blockClick, true)
  }
  dragScrollState.el.style.cursor = 'grab'
  dragScrollState.el.style.userSelect = ''
  dragScrollState = null
}

function setupDragScroll() {
  const gridEl = gridRef.value?.$el as HTMLElement
  if (!gridEl) return
  const scrollEl = gridEl.querySelector<HTMLElement>('.vxe-table--body-inner-wrapper')
  if (!scrollEl || scrollEl.dataset.dragScrollInit) return
  scrollEl.dataset.dragScrollInit = '1'
  scrollEl.style.cursor = 'grab'
  scrollEl.addEventListener('mousedown', (e: MouseEvent) => {
    const target = e.target as HTMLElement
    if (target.closest('input,select,textarea,button,a,.vxe-checkbox,.vxe-radio,[contenteditable],.vxe-cell--edit-wrapper')) return
    dragScrollState = {
      el: scrollEl,
      startX: e.clientX,
      startY: e.clientY,
      scrollLeft: scrollEl.scrollLeft,
      scrollTop: scrollEl.scrollTop,
      dragging: false,
    }
  })
  document.addEventListener('mousemove', onDragMouseMove)
  document.addEventListener('mouseup', onDragMouseUp)
}

onMounted(() => {
  if (!columnOrder.value.length && props.columns) {
    columnOrder.value = props.columns.map(c => c.dataField as string)
  }
  // 列持久化：从后端加载已保存的列配置
  if (props.columnPersistence && props.pageId) {
    applyPersistedColumnConfig()
  }
  loadData()
  props.onInitialized?.({ component: instance })
  emit('initialized', { component: instance })
  document.addEventListener('click', closeContextMenu)
  document.addEventListener('click', closeHeaderContextMenu)
  document.addEventListener('mousedown', closeEditOnOutsideMousedown)
  styleInterval = window.setInterval(() => { setupDragScroll() }, 500)
  nextTick(() => setupDragScroll())

})

onBeforeUnmount(() => {
  document.removeEventListener('click', closeContextMenu)
  document.removeEventListener('click', closeHeaderContextMenu)
  document.removeEventListener('mousedown', closeEditOnOutsideMousedown)
  if (styleInterval) window.clearInterval(styleInterval)
  document.removeEventListener('mousemove', onDragMouseMove)
  document.removeEventListener('mouseup', onDragMouseUp)
  // 保存未落盘的列配置
  if (saveColumnConfigTimer) {
    window.clearTimeout(saveColumnConfigTimer)
    saveColumnConfigTimer = null
  }

})

watch(() => props.dataSource, () => loadData(), { deep: true })
watch(() => props.columns, (cols) => {
  if (!cols) return
  const newOrder = cols.map(c => c.dataField as string)
  const same = newOrder.length === columnOrder.value.length &&
    newOrder.every((f, i) => columnOrder.value[i] === f)
  if (!same) columnOrder.value = newOrder
})
watch(() => props.selectedRowKeys, (v) => { if (v) selectedKeys.value = [...v] })
watch(() => props.paging?.pageSize, (v) => { if (v) pageSize.value = v })


defineExpose(instance)
</script>

<template>
  <div class="lc-table-wrapper">
    <div v-if="showToolbar" class="lc-table-toolbar">
      <el-button v-if="allowAdding" @click="addRow">新增</el-button>
      <el-button v-if="allowUpdating" @click="() => { const r = selectedRowsData[0]; if (r) startEditRow(r) }">修改</el-button>
      <el-button v-if="allowDeleting" @click="() => selectedRowsData.forEach(r => deleteRow(r))">删除</el-button>
      <el-button v-for="item in (props.toolbar?.items || []).filter((i: any) => i.visible !== false && !['add','edit','delete','export','refresh','columnConfig','columnChooser'].includes(i.name))" :key="item.name" :type="(item.type as any) || 'default'" @click="handleToolbarClick({ code: item.name })">{{ item.caption || item.name }}</el-button>
      <el-dropdown v-if="(props.toolbar?.items || []).some((i: any) => i.name === 'export' && i.visible !== false)" @command="(cmd: string) => handleExportCommand(cmd)">
        <el-button type="default">导出 ▾</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="export_page">页面数据导出</el-dropdown-item>
            <el-dropdown-item command="export_selected">选中导出</el-dropdown-item>
            <el-dropdown-item command="export_query">查询导出</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-button v-if="(props.toolbar?.items || []).some((i: any) => i.name === 'refresh' && i.visible !== false)" @click="loadData">刷新</el-button>
      <el-button v-if="(props.toolbar?.items || []).some((i: any) => i.name === 'columnConfig' && i.visible !== false)" @click="openColumnConfig">列配置</el-button>
    </div>


    <div class="lc-grid-container">
    <vxe-grid
      ref="gridRef"
      class="lc-vxe-scrollbar"
      style="height: 100%; width: 100%;"
      :columns="gridColumns"
      :data="displayData"
      :proxy-config="undefined"
      :filter-config="filterConfig"
      :floating-filter-config="floatingFilterConfig"
      :edit-config="editConfig"
      :sort-config="sortConfig"
      :pager-config="pagerConfig"
      :checkbox-config="checkboxConfig"
      :row-config="rowConfig"
      :column-config="columnConfig"
      :column-drag-config="columnDragConfig"
      :toolbar-config="undefined"
      :height="height || '100%'"
      :auto-resize="autoResize !== false"
      :show-header-overflow="showHeaderOverflow"
      :border="showBorders || showRowLines || showColumnLines"
      :show-header="showColumnHeaders !== false"
      :loading="loading"
      :empty-text="noDataText || '暂无数据'"
      :size="size"
      :stripe="stripe"
      :row-class-name="() => 'lc-table-row'"
      :min-height="minHeight"
      :max-height="maxHeight"
      :round="round"
      :align="align"
      :header-align="headerAlign"
      :footer-align="footerAlign"
      :show-footer="autoShowFooter"
      :footer-method="footerMethod"
      :footer-data="autoFooterData || footerData"
      :show-footer-overflow="showFooterOverflow"
      :edit-rules="editRules"
      :keep-source="keepSource"
      :tree-config="treeConfig"
      :expand-config="expandConfig"
      :row-drag-config="rowDragConfig"
      :virtual-y-config="virtualYConfigComputed"
      :virtual-x-config="virtualXConfig"
      :custom-config="customConfigComputed"
      :scrollbar-config="scrollbarConfig"
      :tooltip-config="tooltipConfig"
      :header-tooltip-config="headerTooltipConfig"
      :radio-config="radioConfig"
      :menu-config="menuConfig"
      :keyboard-config="keyboardConfig"
      :clip-config="clipConfig"
      :fnr-config="fnrConfig"
      :zoom-config="zoomConfig"
      :seq-config="seqConfig"
      :span-method="spanMethod"
      :merge-cells="mergeCells"
      :merge-header-cells="mergeHeaderCells"
      :import-config="importConfig"
      :print-config="printConfig"
      :export-config="nativeExportConfig"
      :undo-redo-history-config="undoRedoHistoryConfig"
      :aggregate-config="aggregateConfig"
      :empty-render="emptyRender"
      @checkbox-change="handleSelectionChange"
      @page-change="handlePageChange"
      @sort-change="handleSortChange"
      @filter-change="handleFilterChange"
      @cell-dblclick="handleRowDblClick"
      @cell-click="handleRowClick"
      @contextmenu="onGridContextMenu"
      @column-resize="handleColumnResize"

    >
      <template #expandContent="{ row }">
        <slot name="detail" :row="row" />
      </template>

      <template #operationCell="{ row }">
        <template v-if="isEditing(row)">
          <el-button link type="primary" @click="saveEdit">✓</el-button>
          <el-button link type="danger" @click="cancelEdit">✕</el-button>
        </template>
        <template v-else>
          <el-button v-if="allowUpdating" link type="primary" @click="startEditRow(row)">编</el-button>
          <el-button v-if="allowDeleting" link type="danger" @click="deleteRow(row)">删</el-button>
        </template>
      </template>

      <template v-for="col in visibleColumns" #[`cell_${col.dataField}`]="{ row, rowIndex }" :key="col.dataField">
        <template v-if="isEditing(row) && col.allowEditing !== false">
          <template v-if="col.lookup">
            <el-select v-model="editingRowData[col.dataField!]" size="small" style="width: 100%">
              <el-option v-for="opt in getLookupDataSource(col)" :key="opt[col.lookup.valueExpr]" :label="opt[col.lookup.displayExpr]" :value="opt[col.lookup.valueExpr]" />
            </el-select>
          </template>
          <template v-else-if="col.dataType === 'date'">
            <el-date-picker v-model="editingRowData[col.dataField!]" size="small" type="date" style="width: 100%" />
          </template>
          <template v-else>
            <el-input v-model="editingRowData[col.dataField!]" size="small" style="width: 100%" />
          </template>
        </template>
        <template v-else>
          <slot
            v-if="col.cellTemplate && slots[col.cellTemplate]"
            :name="col.cellTemplate"
            :data="row"
            :value="getCellValue(row, col)"
            :row-index="rowIndex"
          />
          <span
            v-else
            @contextmenu="onContextMenu($event, row, col)"
          >{{ getLookupDisplay(getCellValue(row, col), col) }}</span>
        </template>
      </template>


    </vxe-grid>
    </div>

    <div v-if="activeFilterTags.length" class="lc-filter-summary">
      <div class="lc-filter-summary-tags" @click="showFilterDetail = true" style="cursor: pointer" title="点击查看全部筛选条件">
        <span v-for="tag in activeFilterTags.slice(0, 10)" :key="tag.key" class="lc-filter-tag">{{ tag.label }}</span>
        <span v-if="activeFilterTags.length > 10" class="lc-filter-tag lc-filter-tag-more">+{{ activeFilterTags.length - 10 }} 更多</span>
      </div>
      <el-button link type="primary" size="small" @click="showFilterDetail = true">查看详情</el-button>
      <el-button link type="primary" size="small" @click="clearAllFilters">清空筛选</el-button>
    </div>

    <el-dialog v-model="showFilterDetail" title="筛选条件详情" width="500" append-to-body>
      <div class="lc-filter-detail-list">
        <div v-for="tag in activeFilterTags" :key="tag.key" class="lc-filter-detail-item">
          <span class="lc-filter-detail-label">{{ tag.label }}</span>
        </div>
        <div v-if="!activeFilterTags.length" class="lc-filter-detail-empty">暂无筛选条件</div>
      </div>
      <template #footer>
        <el-button @click="showFilterDetail = false">关闭</el-button>
        <el-button type="primary" @click="clearAllFilters(); showFilterDetail = false">清空全部</el-button>
      </template>
    </el-dialog>

    <div v-if="pagingEnabled" class="lc-table-pager">
      <el-pagination
        :current-page="currentPage"
        :page-size="pageSize"
        :page-sizes="props.pager?.allowedPageSizes || [15, 30, 50, 100]"
        :total="totalForPager"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="(v: number) => { currentPage = v; loadData() }"
        @size-change="(v: number) => { pageSize = v; currentPage = 1; loadData() }"
      />
    </div>

    <el-dialog
      v-if="editingMode === 'popup' || editingMode === 'form'"
      :model-value="editingRowKey !== null || insertMode"
      :title="insertMode ? '新增' : '编辑'"
      width="600"
      append-to-body
      @close="cancelEdit"
    >
      <el-form label-width="100px">
        <el-form-item v-for="col in visibleColumns" :key="col.dataField" :label="col.caption || col.dataField">
          <template v-if="col.lookup">
            <el-select v-model="editingRowData[col.dataField!]" style="width: 100%">
              <el-option v-for="opt in getLookupDataSource(col)" :key="opt[col.lookup.valueExpr]" :label="opt[col.lookup.displayExpr]" :value="opt[col.lookup.valueExpr]" />
            </el-select>
          </template>
          <template v-else-if="col.dataType === 'number'">
            <el-input v-model.number="editingRowData[col.dataField!]" type="number" style="width: 100%" />
          </template>
          <template v-else-if="col.dataType === 'date'">
            <el-date-picker v-model="editingRowData[col.dataField!]" type="date" style="width: 100%" />
          </template>
          <template v-else-if="col.dataType === 'datetime'">
            <el-date-picker v-model="editingRowData[col.dataField!]" type="datetime" style="width: 100%" />
          </template>
          <template v-else>
            <el-input v-model="editingRowData[col.dataField!]" />
          </template>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelEdit">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="columnChooserVisible" title="列配置" width="420" append-to-body>
      <div class="lc-column-config-list">
        <div
          v-for="(field, idx) in columnOrder"
          :key="field"
          class="lc-column-config-item"
          :class="{ 'lc-col-drag-over': colDragOverIdx === idx, 'lc-col-dragging': colDragIdx === idx }"
          draggable="true"
          @dragstart="onColDragStart(idx)"
          @dragover.prevent="onColDragOver(idx)"
          @drop="onColDrop(idx)"
          @dragend="onColDragEnd"
        >
          <span class="lc-col-drag-handle">⠿</span>
          <el-checkbox
            :model-value="columnVisibleState[field] !== false"
            @change="toggleColumnVisible(field)"
          >{{ (props.columns || []).find(c => c.dataField === field)?.caption || field }}</el-checkbox>
        </div>
      </div>
      <template #footer>
        <el-button @click="resetColumnConfig">重置</el-button>
        <el-button type="primary" @click="columnChooserVisible = false">确认</el-button>
      </template>
    </el-dialog>

    <div v-if="contextMenu.visible" class="lc-context-menu" :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }" @click.stop>
      <div class="lc-context-menu-item" @click="copyCellValue">复制单元格</div>
      <div class="lc-context-menu-item" @click="copyRowData">复制行数据</div>
      <div class="lc-context-menu-item" @click="copyRowJsonData">复制行原数据</div>
      <div class="lc-context-menu-divider"></div>
      <div class="lc-context-menu-item" @click="closeContextMenu">取消</div>
    </div>

    <div v-if="headerContextMenu.visible" class="lc-context-menu" :style="{ left: headerContextMenu.x + 'px', top: headerContextMenu.y + 'px' }" @click.stop>
      <template v-if="headerCtxCanSort">
        <div class="lc-context-menu-item" @click="headerSortAsc">升序</div>
        <div class="lc-context-menu-item" @click="headerSortDesc">降序</div>
        <div v-if="headerCtxIsSorted" class="lc-context-menu-item" @click="headerClearSort">清除排序</div>
      </template>
      <template v-if="headerCtxCanSort && headerCtxCanFix">
        <div class="lc-context-menu-divider"></div>
      </template>
      <template v-if="headerCtxCanFix">
        <div class="lc-context-menu-item" @click="headerFixLeft">固定到左侧</div>
        <div class="lc-context-menu-item" @click="headerFixRight">固定到右侧</div>
        <div v-if="headerCtxIsFixed" class="lc-context-menu-item" @click="headerUnfix">取消固定</div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.lc-table-wrapper { width: 100%; flex: 1; min-height: 0; position: relative; display: flex; flex-direction: column; overflow: hidden; }
.lc-table-wrapper :deep(.vxe-table) { transition: none !important; }
.lc-table-wrapper :deep(.vxe-table--body-wrapper) { transition: none !important; }
.lc-table-wrapper :deep(.vxe-loading) { transition: none !important; }
.lc-table-wrapper :deep(.is--floating-filters) { height: 28px !important; }
.lc-table-wrapper :deep(.vxe-table--header-wrapper) { z-index: 100 !important; position: relative !important; }
.lc-table-wrapper :deep(.vxe-table--header-area-wrapper) { z-index: 100 !important; position: relative !important; }


.lc-table-wrapper :deep(.is--floating-filters .vxe-header--column) { overflow: visible !important; }
.lc-table-wrapper :deep(.is--floating-filters .vxe-cell) { overflow: visible !important; }
.lc-table-wrapper :deep(.is--floating-filters .vxe-cell--wrapper) { overflow: visible !important; margin: 0 !important; height: 100% !important; }
.lc-table-wrapper :deep(.is--floating-filters .vxe-header-cell--wrapper) { margin: 0 !important; height: 100% !important; }
.lc-table-wrapper :deep(.is--floating-filters .el-select__wrapper) { border-radius: 0 !important; min-height: 36px !important; }
.lc-table-wrapper :deep(.is--floating-filters .el-input__wrapper) { border-radius: 0 !important; min-height: 36px !important; }
.lc-table-wrapper :deep(.is--floating-filters .el-input) { padding-right: 5px !important; }
.lc-table-wrapper :deep(.is--floating-filters .el-date-editor .el-input__wrapper) { border-radius: 0 !important; min-height: 36px !important; }
.lc-table-toolbar { display: flex; gap: 8px; align-items: center; padding: 8px 0; flex-wrap: wrap; flex-shrink: 0; }
.lc-grid-container { flex: 1; min-height: 0; overflow: hidden; position: relative; }
.lc-table-search { padding: 0 0 8px 0; flex-shrink: 0; }
.lc-table-empty { text-align: center; padding: 32px 0; color: #909399; font-size: 14px; }
.lc-context-menu {
  position: fixed; z-index: 100000; background: #fff; border: 1px solid #e7ed;
  border-radius: 4px; box-shadow: 0 2px 12px rgba(0,0,0,.15); padding: 4px 0; min-width: 120px;
}
.lc-context-menu-item { padding: 6px 16px; cursor: pointer; font-size: 13px; }
.lc-context-menu-item:hover { background: #f5f7fa; color: #409eff; }
.lc-context-menu-divider { height: 1px; background: #e4e7ed; margin: 4px 0; }
.lc-filter-summary {
  display: flex; align-items: center; gap: 8px; padding: 4px 8px;
  background: #f5f7fa; border-top: 1px solid #e4e7ed; flex-shrink: 0;
  overflow-x: auto;
}
.lc-filter-summary-tags { display: flex; gap: 6px; flex-wrap: nowrap; overflow: hidden; }
.lc-filter-tag {
  display: inline-flex; align-items: center; padding: 2px 8px;
  background: #ecf5ff; color: #409eff; border-radius: 3px;
  font-size: 12px; white-space: nowrap;
}
.lc-filter-tag-more { background: #f0f0f0; color: #909399; }
.lc-filter-detail-list { display: flex; flex-direction: column; gap: 8px; max-height: 400px; overflow-y: auto; }
.lc-filter-detail-item { display: flex; align-items: center; padding: 6px 12px; background: #f5f7fa; border-radius: 4px; }
.lc-filter-detail-label { font-size: 13px; color: #303133; }
.lc-filter-detail-empty { text-align: center; color: #909399; padding: 20px; }
.lc-table-pager {
  display: flex; justify-content: flex-end; align-items: center;
  padding: 4px 8px; flex-shrink: 0; background: #fff;
}
.lc-column-config-list { max-height: 400px; overflow-y: auto; }
.lc-column-config-item {
  display: flex; align-items: center; gap: 8px;
  padding: 8px 4px; border-bottom: 1px solid #f0f0f0;
  cursor: grab; transition: background 0.15s;
}
.lc-column-config-item:hover { background: #f5f7fa; }
.lc-column-config-item.lc-col-drag-over { background: #ecf5ff; border-top: 2px solid #409eff; }
.lc-column-config-item.lc-col-dragging { opacity: 0.5; cursor: grabbing; }
.lc-col-drag-handle { color: #c0c4cc; font-size: 16px; cursor: grab; user-select: none; }

</style>
<style>

.lc-table-wrapper .is--floating-filters .vxe-header--column { overflow: visible !important; }
.lc-table-wrapper .is--floating-filters .vxe-cell { overflow: visible !important; }
.lc-table-wrapper .is--floating-filters .vxe-cell--wrapper { overflow: visible !important; }

.lc-vxe-scrollbar ::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
.lc-vxe-scrollbar ::-webkit-scrollbar-thumb {
  background: #c9ced4;
  border-radius: 3px;
}
.lc-vxe-scrollbar ::-webkit-scrollbar-thumb:hover {
  background: #909399;
}
.lc-vxe-scrollbar ::-webkit-scrollbar-track {
  background: transparent;
}

.lc-table-wrapper .vxe-table--footer-wrapper { background-image: none !important; }
.lc-table-wrapper .vxe-table--footer { background-image: none !important; }
.lc-table-wrapper .vxe-footer--row { background-image: none !important; }
.lc-table-wrapper .vxe-footer--column { background-image: none !important; background-size: 0 !important; border: none !important; }
.lc-table-wrapper .vxe-footer--column .vxe-cell { color: rgb(255, 0, 0) !important; }
.lc-table-wrapper .vxe-footer--column .vxe-footer-cell--wrapper { color: rgb(255, 0, 0) !important; }
.lc-table-wrapper .vxe-footer--column .vxe-cell--wrapper { color: rgb(255, 0, 0) !important; }
</style>