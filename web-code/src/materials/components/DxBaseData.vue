<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, inject, nextTick } from 'vue'
import DxDataGrid from 'devextreme-vue/data-grid'
import DxButton from 'devextreme-vue/button'
import DxTextBox from 'devextreme-vue/text-box'
import CustomStore from 'devextreme/data/custom_store'
import { gatewayCall } from '@/api'
import { pageModal } from '@/components/renderer/pageModal'
import { loadPage } from '@/store/persistence'
import { t } from '@/store/lang'
import { DESIGNER_STORE_KEY, findNodeByFieldNameIn, type RenderStore } from '@/store/designer'
import type { DependFilterCondition } from '@/components/designer/DependFilterDialog.vue'

const props = withDefaults(
  defineProps<{
    refPageId?: string
    displayField?: string
    valueField?: string
    placeholder?: string
    value?: any
    disabled?: boolean
    readOnly?: boolean
    visible?: boolean
    dependField?: string
    dependFilters?: DependFilterCondition[]
  }>(),
  {
    refPageId: '',
    displayField: '',
    valueField: 'id',
    placeholder: '请选择',
    value: null,
    disabled: false,
    readOnly: false,
    visible: true,
    dependField: '',
    dependFilters: () => [],
  },
)

const emit = defineEmits<{
  (e: 'valueChanged', value: unknown): void
  (e: 'selectionChanged', data: Record<string, unknown> | null): void
}>()

const isOpen = ref(false)
const displayValue = ref('')
const searchText = ref('')
const selectedRowData = ref<Record<string, unknown> | null>(null)
const gridColumns = ref<Record<string, unknown>[]>([])
const tableName = ref('')
const gridRef = ref<any>(null)
const wrapRef = ref<HTMLElement | null>(null)

const isReadOnly = computed(() => props.readOnly || props.disabled)
const textBoxRef = ref<any>(null)

let searchKeyword = ''
let baseDataRefList: Record<string, unknown>[] = []

const renderStore = inject(DESIGNER_STORE_KEY, null) as RenderStore | null

let isEchoQuery = false
let dependWatchInitialized = false

const dependValue = computed(() => {
  if (!props.dependField || !renderStore) return undefined
  const dependNode = findNodeByFieldNameIn(renderStore.schema, props.dependField)
  return dependNode?.props.value
})

const valueExpr = computed(() => (props.valueField || 'id').toUpperCase())
const displayExprs = computed(() => {
  const raw = props.displayField || 'name'
  return raw.split(',').map((f) => f.trim().toUpperCase()).filter(Boolean)
})
const displayExpr = computed(() => displayExprs.value[0] || 'NAME')

const dependVersion = ref(0)
const searchVersion = ref(0)

async function loadPageMeta() {
  if (!props.refPageId) {
    gridColumns.value = []
    tableName.value = ''
    return
  }
  try {
    const page = await loadPage(props.refPageId)
    if (!page) return
    tableName.value = page.tableName || ''
    const schema = page.bizSchemas?.table?.[0]
    let columns: Record<string, unknown>[] = []
    const bdRefs: Record<string, unknown>[] = []
    if (schema && Array.isArray((schema as any).props?.columns)) {
      const rawCols = (schema as any).props.columns as Record<string, unknown>[]
      columns = rawCols
        .filter((c: any) => c.visible !== false)
        .map((c: any) => {
          const df = String(c.dataField || '').toUpperCase()
          const col: Record<string, unknown> = {
            dataField: df,
            caption: c.caption || c.dataField || '',
          }
          if (df.includes('.')) {
            const flatKey = df
            col.dataField = df.replace(/\./g, '__')
            col.calculateDisplayValue = (rowData: any) => rowData?.[flatKey]
            col.allowFiltering = false
            col.allowSorting = false
          }
          return col
        })
      for (const c of rawCols) {
        if (c.baseDataRef) {
          bdRefs.push({
            dataField: String(c.dataField || ''),
            ...(c.baseDataRef as Record<string, unknown>),
          })
        }
      }
    }
    if (!columns.length) {
      const formNodes = page.root || []
      for (const node of formNodes) {
        if (node.dbField) {
          columns.push({ dataField: node.dbField.toUpperCase(), caption: node.fieldName || node.dbField })
        }
      }
    }
    gridColumns.value = columns
    baseDataRefList = bdRefs
  } catch {
    gridColumns.value = []
  }
}

watch(() => props.refPageId, loadPageMeta, { immediate: false })
onMounted(async () => {
  await loadPageMeta()
  if (props.value != null && props.value !== '' && !displayValue.value) {
    const newVal = props.value
    if (!dataSource.value) {
      displayValue.value = String(newVal)
      searchText.value = String(newVal)
      return
    }
    try {
      isEchoQuery = true
      const data = await dataSource.value.load({
        filter: [valueExpr.value, '=', newVal],
        skip: 0,
        take: 1,
      })
      const row = (data as any).data?.[0]
      if (row) {
        displayValue.value = getDisplayText(row)
        selectedRowData.value = row
      } else {
        displayValue.value = String(newVal)
      }
      searchText.value = displayValue.value
      searchKeyword = ''
    } catch {
      displayValue.value = String(newVal)
      searchText.value = String(newVal)
    }
  }
})

const dataSource = computed(() => {
  if (!props.refPageId || !tableName.value) return null
  void dependVersion.value
  void searchVersion.value
  const kw = searchKeyword
  return new CustomStore({
    key: valueExpr.value,
    load: async (loadOptions: any) => {
      try {
        const echoMode = isEchoQuery
        isEchoQuery = false
        const skip = loadOptions.skip ?? 0
        const take = loadOptions.take ?? 15
        const page = Math.floor(skip / take) + 1
        const pageSize = take
        const gatewayPayload: Record<string, unknown> = { page, pageSize }
        const allFilters: unknown[] = []
        if (!echoMode && kw) {
          for (let i = 0; i < displayExprs.value.length; i++) {
            allFilters.push({ Symbol: i === 0 ? 'and' : 'or' })
            allFilters.push({ Id: displayExprs.value[i], Symbol: 'like', Val: `%${kw}%`, TableAlias: 'T' })
          }
        }
        if (loadOptions.filter) {
          allFilters.push(...convertFilter(loadOptions.filter))
        }
        if (!echoMode && props.dependField && props.dependFilters && props.dependFilters.length > 0) {
          const dVal = dependValue.value
          const hasDependCond = props.dependFilters.some((c) => c.valueType === 'depend')
          if (hasDependCond && (dVal == null || dVal === '')) {
            return { data: [], totalCount: 0 }
          }
          for (const c of props.dependFilters) {
            const val = c.valueType === 'depend' ? dVal : c.fixedValue
            if (val == null || val === '') continue
            allFilters.push({ Symbol: 'and' })
            allFilters.push({ Id: c.field, Symbol: c.operator, Val: val, TableAlias: 'T' })
          }
        }
        if (allFilters.length > 0) {
          gatewayPayload.filterData = JSON.stringify(allFilters)
        }

        const res = await gatewayCall(tableName.value, 'query', gatewayPayload)
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        const rawRows = (res.payload.data || []) as Record<string, unknown>[]
        const upperRows = rawRows.map((row) => {
          const newRow: Record<string, unknown> = {}
          for (const key in row) {
            newRow[key.toUpperCase()] = row[key]
          }
          return newRow
        })
        return { data: upperRows, totalCount: res.payload.totalCount }
      } catch (err) {
        console.error('[基础资料数据加载失败]', err)
        throw err
      }
    },
  })
})

function mapOp(op: string): string {
  switch (op) {
    case 'contains': return 'like'
    case 'notcontains': return 'not like'
    case 'startswith': return 'like'
    case 'endswith': return 'like'
    default: return op
  }
}

function wrapLikeVal(op: string, val: unknown): unknown {
  if (typeof val !== 'string') return val
  if (op === 'contains') return `%${val}%`
  if (op === 'startswith') return `${val}%`
  if (op === 'endswith') return `%${val}`
  return val
}

function convertFilter(dxFilter: unknown): unknown[] {
  const result: unknown[] = []
  if (!Array.isArray(dxFilter)) return result
  if (dxFilter.length >= 3 && typeof dxFilter[0] === 'string') {
    const op = String(dxFilter[1])
    result.push({ Symbol: 'and' })
    result.push({ Id: dxFilter[0], Symbol: mapOp(op), Val: wrapLikeVal(op, dxFilter[2]), TableAlias: 'T' })
  } else {
    for (let i = 0; i < dxFilter.length; i++) {
      const item = dxFilter[i]
      if (Array.isArray(item)) {
        const inner = convertFilter(item)
        if (inner.length > 0) {
          let logic = 'and'
          if (i > 0 && (dxFilter[i - 1] === 'and' || dxFilter[i - 1] === 'or')) {
            logic = dxFilter[i - 1] as string
          }
          result.push({ Symbol: logic })
          for (let j = 1; j < inner.length; j++) result.push(inner[j])
        }
      }
    }
  }
  return result
}

let suppressSelection = false

function getDisplayText(row: Record<string, unknown>): string {
  return displayExprs.value.map((expr) => String(row[expr] ?? '').trim()).filter(Boolean).join(' - ')
}

function onContentReady(e: any) {
  if (!selectedRowData.value) return
  const key = selectedRowData.value[valueExpr.value]
  if (key == null) return
  try {
    const rows = e.component?.getVisibleRows?.() || []
    if (rows.some((r: any) => r.key === key)) {
      suppressSelection = true
      e.component.selectRow(key, true)
    }
  } catch {
    // 高亮失败可忽略
  } finally {
    suppressSelection = false
  }
}

function onSelectionChanged(e: { selectedRowsData: Record<string, unknown>[] }) {
  if (suppressSelection) return
  if (e.selectedRowsData.length > 0) {
    const row = e.selectedRowsData[0]
    selectedRowData.value = row
    const val = row[valueExpr.value]
    displayValue.value = getDisplayText(row)
    searchText.value = displayValue.value
    searchKeyword = ''
    emit('valueChanged', val)
    emit('selectionChanged', row)
    isOpen.value = false
  }
}

watch(
  () => props.value,
  async (newVal) => {
    if (newVal == null || newVal === '') {
      displayValue.value = ''
      selectedRowData.value = null
      searchText.value = ''
      searchKeyword = ''
      return
    }
    if (selectedRowData.value && selectedRowData.value[valueExpr.value] === newVal) return
    if (!dataSource.value) return
    try {
      isEchoQuery = true
      const data = await dataSource.value.load({
        filter: [valueExpr.value, '=', newVal],
        skip: 0,
        take: 1,
      })
      const row = (data as any).data?.[0]
      if (row) {
        displayValue.value = getDisplayText(row)
        selectedRowData.value = row
      } else {
        displayValue.value = String(newVal)
      }
      searchText.value = displayValue.value
      searchKeyword = ''
    } catch {
      displayValue.value = String(newVal)
    }
  },
  { immediate: true },
)

watch(dependValue, (newVal, oldVal) => {
  if (!props.dependField) return
  if (oldVal === undefined) {
    // 表单初始化：依赖值首次设置，只刷新数据源，不清空当前值
    dependVersion.value++
    return
  }
  // 用户手动变更：清空当前值 + 刷新数据源
  displayValue.value = ''
  selectedRowData.value = null
  searchText.value = ''
  searchKeyword = ''
  emit('valueChanged', null)
  emit('selectionChanged', null)
  dependVersion.value++
})

async function openPopupPage() {
  if (!props.refPageId || props.disabled) return
  try {
    const result = await pageModal.openPage(props.refPageId, {}, { mode: 'popup', width: 900, height: 600, bizMode: 'table', selectMode: true })
    if (result && typeof result === 'object') {
      const rawRow = result as Record<string, unknown>
      const row: Record<string, unknown> = {}
      for (const key in rawRow) {
        row[key.toUpperCase()] = rawRow[key]
      }
      selectedRowData.value = row
      const val = row[valueExpr.value]
      displayValue.value = getDisplayText(row)
      searchText.value = displayValue.value
      searchKeyword = ''
      emit('valueChanged', val)
      emit('selectionChanged', row)
    }
  } catch (err) {
    console.error('[打开基础资料页面失败]', err)
  }
}

function onSearchInput(e: any) {
  const text = e.value ?? e.event?.target?.value ?? ''
  searchText.value = text
  searchKeyword = text
  if (text && !isOpen.value) {
    isOpen.value = true
  }
  searchVersion.value++
  if (!text) {
    displayValue.value = ''
    selectedRowData.value = null
    emit('valueChanged', null)
    emit('selectionChanged', null)
  }
}

function onTextBoxFocusIn() {
  if (isReadOnly.value) return
  isOpen.value = true
  if (!searchKeyword) {
    searchVersion.value++
  }
}

function clearValue() {
  displayValue.value = ''
  selectedRowData.value = null
  searchText.value = ''
  searchKeyword = ''

  emit('valueChanged', null)
  emit('selectionChanged', null)
  isOpen.value = false
  nextTick(() => {
    textBoxRef.value?.instance?.focus()
  })
}

function onDocumentClick(e: MouseEvent) {
  if (!isOpen.value) return
  const target = e.target as Node
  if (wrapRef.value && !wrapRef.value.contains(target)) {
    isOpen.value = false
    if (searchText.value !== displayValue.value) {
      searchText.value = displayValue.value
      searchKeyword = ''
    }
  }
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick, true)
})
onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick, true)
})
</script>

<template>
  <div v-show="visible" class="dx-base-data-control">
    <div class="base-data-wrap" ref="wrapRef">
      <DxTextBox
        ref="textBoxRef"
        :value="searchText"
        :placeholder="placeholder"
        :disabled="disabled"
        :read-only="readOnly"
        @focus-in="onTextBoxFocusIn"
        @input="onSearchInput"
      />
      <button
        v-if="!isReadOnly && (selectedRowData || searchText)"
        class="base-data-clear-btn"
        :disabled="disabled"
        :title="t('清空')"
        @click="clearValue"
      >×</button>
      <div v-show="isOpen" class="base-data-dropdown">
        <DxDataGrid
          v-if="dataSource"
          ref="gridRef"
          :data-source="dataSource"
          :columns="gridColumns"
          :selection="{ mode: 'single', showCheckBoxesMode: 'none' }"
          :hover-state-enabled="true"
          height="250"
          :filter-row="{ visible: true }"
          :paging="{ enabled: true, pageSize: 15 }"
          :pager="{ visible: true, showInfo: true, showNavigationButtons: true }"
          :remote-operations="{ paging: true, filtering: true }"
          :no-data-text="t('未找到匹配的基础资料')"
          @content-ready="onContentReady"
          @selection-changed="onSelectionChanged"
        />
        <div v-else class="no-data-tip">{{ t('请先配置关联基础资料页面') }}</div>
      </div>
    </div>
    <DxButton
      icon="search"
      styling-mode="text"
      :width="28"
      :height="28"
      :disabled="isReadOnly"
      :hint="t('打开选择')"
      @click="openPopupPage"
    />
  </div>
</template>

<style scoped>
.dx-base-data-control {
  display: flex;
  align-items: center;
  gap: 2px;
  width: 100%;
  box-sizing: border-box;
}
.base-data-wrap {
  position: relative;
  flex: 1;
  min-width: 0;
}
.base-data-clear-btn {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  border: none;
  background: none;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: #999;
  padding: 2px 4px;
  border-radius: 3px;
  z-index: 1;
}
.base-data-clear-btn:hover {
  color: #666;
  background: #f0f0f0;
}
.base-data-clear-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.base-data-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  z-index: 9999;
  width: 500px;
  max-width: 600px;
  background: #fff;
  border: 1px solid #ddd;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  border-radius: 2px;
  overflow: hidden;
}
.dx-base-data-control > :deep(.dx-button) {
  flex-shrink: 0;
}
.no-data-tip {
  padding: 20px;
  text-align: center;
  color: #999;
  font-size: 14px;
}
</style>
