<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import JsonTableEditorDialog from './JsonTableEditorDialog.vue'
import ApiSelectDialog from './ApiSelectDialog.vue'
import { t } from '@/store/lang'

interface ColumnConfig {
  dataField: string
  caption: string
  dataType: string
  width?: number
  visible?: boolean
  allowEditing?: boolean
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
}

const props = defineProps<{
  modelValue: string
  visible: boolean
  title?: string
  editingMode?: string
  formFields?: { fieldName: string; dbField?: string; title: string }[]
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
]

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
      lookup: item.lookup as ColumnConfig['lookup'],
      dropDownGrid: item.dropDownGrid as ColumnConfig['dropDownGrid'],
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

function openFormImport() {
  formImportSelected.value = new Set()
  formImportVisible.value = true
}

function toggleFormImportField(fieldName: string) {
  const s = new Set(formImportSelected.value)
  if (s.has(fieldName)) s.delete(fieldName)
  else s.add(fieldName)
  formImportSelected.value = s
}

/** 确认导入：把选中的表单字段添加为列，dataField 用 dbField（已存在的跳过） */
function confirmFormImport() {
  const existing = new Set(columns.value.map((c) => c.dataField))
  for (const fieldName of formImportSelected.value) {
    const fieldMeta = props.formFields?.find((f) => f.fieldName === fieldName)
    const dataField = fieldMeta?.dbField || fieldName
    if (existing.has(dataField)) continue
    columns.value.push({
      dataField,
      caption: fieldMeta?.title ?? fieldName,
      dataType: 'string',
      editorType: 'default',
      allowEditing: false,
      visible: true,
      permCode: dataField,
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
    ]
  }
}

watch(mode, () => {
  handleAutoInit()
})
</script>

<template>
  <div v-if="visible" class="dialog-overlay" @click.self="onCancel">
    <div class="dialog-container dialog-xlarge">
      <div class="dialog-header">
        <span class="dialog-title">{{ title || t('列配置编辑') }}</span>
        <div class="dialog-header-actions">
          <button class="mode-toggle" @click="switchToJson">
            {{ mode === 'table' ? t('切换到 JSON 模式') : t('切换到表格模式') }}
          </button>
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
          </div>

          <div v-if="isEmpty && !parseError" class="table-empty-tip">
            <span>{{ t('暂无列配置，点击上方按钮添加') }}</span>
          </div>

          <!-- 列列表 -->
          <div class="column-list">
            <template
              v-for="(col, fidx) in filteredColumns"
              :key="getRealIndex(fidx)"
            >
              <div
                class="column-card"
                :class="{
                  expanded: expandedIdx === getRealIndex(fidx),
                  hidden: col.visible === false,
                }"
              >
                <div
                  class="column-card-header"
                  @click="toggleExpand(getRealIndex(fidx))"
                >
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
                <div
                  v-if="expandedIdx === getRealIndex(fidx)"
                  class="column-card-body"
                >
                  <!-- 第一行：核心字段 -->
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
                  <div class="form-row" style="margin-top: 8px">
                    <label class="form-label">{{ t('权限码') }}</label>
                    <input
                      class="form-input"
                      v-model="col.permCode"
                      :placeholder="t('留空则不鉴权')"
                    />
                  </div>

                  <!-- 第三行：编辑器配置（仅允许编辑时显示） -->
                  <div
                    v-if="isEditingEnabled && col.allowEditing !== false"
                    class="editor-section"
                  >
                    <div class="form-row">
                      <label class="form-label">{{ t('编辑器类型') }}</label>
                      <select
                        class="form-select"
                        :value="col.editorType"
                        @change="(e) => onEditorTypeChange(getRealIndex(fidx), (e.target as HTMLSelectElement).value)"
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
                              openLookupDataSourceDialog(getRealIndex(fidx))
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
              </div>
            </template>
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
    <Teleport to="body">
      <div
        v-if="formImportVisible"
        class="dialog-overlay"
        @click.self="formImportVisible = false"
      >
        <div class="form-import-dialog">
          <div class="form-import-title">{{ t('从表单导入字段') }}</div>
          <div class="form-import-list">
            <label
              v-for="f in props.formFields"
              :key="f.fieldName"
              class="form-import-item"
            >
              <input
                type="checkbox"
                :checked="formImportSelected.has(f.fieldName)"
                @change="toggleFormImportField(f.fieldName)"
              />
              <span class="form-import-field">{{ f.fieldName }}</span>
              <span class="form-import-title-label">{{ f.title }}</span>
            </label>
            <div
              v-if="!props.formFields || !props.formFields.length"
              class="form-import-empty"
            >
              {{ t('表单页面暂无控件字段，请先在表单页面添加控件') }}
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
.api-clear-btn {
  border: none;
  background: none;
  color: #ef4444;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 6px;
}
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}
.dialog-container {
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  max-width: 95vw;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.dialog-xlarge {
  width: 1000px;
}
.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #e4e7ed;
}
.dialog-title {
  font-size: 14px;
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
.dialog-body {
  padding: 12px 16px;
  flex: 1;
  overflow: auto;
}
.column-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
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
  padding: 20px;
}

/* 工具栏 */
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: space-between;
}
.search-input {
  flex: 1;
  max-width: 300px;
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

/* 列卡片 */
.column-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.column-card {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
  transition: border-color 0.15s;
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
  justify-content: space-between;
  padding: 7px 12px;
  background: #f5f7fa;
  cursor: pointer;
}
.column-card-header:hover {
  background: #ecf5ff;
}
.column-card-summary {
  display: flex;
  align-items: center;
  gap: 8px;
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
.badge-string {
  background: #409eff;
}
.badge-number {
  background: #67c23a;
}
.badge-date {
  background: #e6a23c;
}
.badge-datetime {
  background: #e6a23c;
}
.badge-boolean {
  background: #f56c6c;
}
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
  background: #e8f4ff;
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
  gap: 8px;
}
.expand-icon {
  color: #909399;
  font-size: 12px;
}
.row-delete {
  border: none;
  background: #fef0f0;
  color: #f56c6c;
  padding: 3px 8px;
  border-radius: 3px;
  font-size: 12px;
  cursor: pointer;
}
.row-delete:hover {
  background: #fde2e2;
}
.row-delete.small {
  padding: 2px 6px;
  font-size: 11px;
}

/* 展开内容 */
.column-card-body {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

/* 双列网格布局 */
.form-grid-2col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 16px;
}
.form-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.form-label {
  width: 80px;
  font-size: 12px;
  color: #606266;
  padding-top: 6px;
  flex-shrink: 0;
  text-align: right;
}
.form-input {
  flex: 1;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
  min-width: 0;
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
  padding: 4px 8px;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
  background: #fff;
  min-width: 0;
}
.form-textarea {
  flex: 1;
  min-height: 60px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 12px;
  font-family: 'Consolas', 'Monaco', monospace;
  outline: none;
  box-sizing: border-box;
  resize: vertical;
}

/* 开关组 - 紧凑横排 */
.switch-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 20px;
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 4px;
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
}

/* 编辑器区域 */
.editor-section {
  border-top: 1px dashed #e4e7ed;
  padding-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 子配置 */
.sub-config {
  margin-top: 4px;
  padding: 10px 12px;
  background: #f9fafc;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.sub-config-title {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  margin-bottom: 2px;
}
.sub-config-hint {
  font-size: 11px;
  color: #c0c4cc;
  font-weight: normal;
}

/* 嵌套列 */
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
.ncol-label.flex1 {
  flex: 1;
}
.ncol-label.w60 {
  width: 60px;
}
.ncol-label.w40 {
  width: 40px;
}
.ncol-label.w24 {
  width: 24px;
}
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
}
.row-delete.small.w24 {
  width: 24px;
  flex: none;
  padding: 2px 0;
  text-align: center;
}

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
.action-btn-add.small {
  padding: 3px 10px;
  font-size: 11px;
  align-self: flex-start;
}
.lookup-edit-btn {
  margin-top: 6px;
  align-self: flex-start;
}

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
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid #e4e7ed;
}
.form-import-dialog {
  background: #fff;
  border-radius: 8px;
  width: 420px;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}
.form-import-title {
  font-size: 15px;
  font-weight: 600;
  padding: 14px 16px;
  border-bottom: 1px solid #e4e7ed;
}
.form-import-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 16px;
}
.form-import-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  cursor: pointer;
  font-size: 13px;
}
.form-import-item:hover {
  background: #f5f7fa;
}
.form-import-field {
  font-weight: 500;
  color: #303133;
  min-width: 120px;
}
.form-import-title-label {
  color: #909399;
}
.form-import-empty {
  text-align: center;
  color: #c0c4cc;
  padding: 24px 0;
  font-size: 13px;
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
</style>
