<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { httpRequest, extractList } from '@/utils/request'
import { ElMessage } from 'element-plus'

export interface GridColumn {
  dataField: string
  caption: string
  dataType: string
  visible?: boolean
  editorType?: string
  lookup?: {
    dataSource: unknown[]
    valueExpr: string
    displayExpr: string
    dataSourceType?: 'static' | 'remote'
    remoteUrl?: string
    method?: string
  }
}

export interface SearchCondition {
  field: string
  operator: string
  value: string
  value2: string // between 操作符的第二个值
}

interface SearchScheme {
  name: string
  conditions: SearchCondition[]
  isDefault: boolean
}

const props = defineProps<{
  columns: GridColumn[]
  gridId?: string
}>()

const emit = defineEmits<{
  search: [conditions: SearchCondition[]]
  reset: []
  heightChange: []
}>()

const conditions = ref<SearchCondition[]>([
  { field: '', operator: '', value: '', value2: '' },
])
const expanded = ref(true)
const schemes = ref<SearchScheme[]>([])
const schemeName = ref('')
const showSchemeDialog = ref(false)

const visibleColumns = computed(() =>
  (props.columns || []).filter((c) => c.visible !== false)
)

const activeConditions = computed(() =>
  conditions.value.filter((c) => {
    if (!c.field || !c.operator) return false
    if (c.operator === 'between') return c.value !== '' && c.value2 !== ''
    return c.value !== ''
  })
)

/** 是否有条件使用了"之间"操作符（影响布局列数） */
const hasBetweenCondition = computed(() =>
  conditions.value.some((c) => c.operator === 'between')
)

/** 容器宽度，用于自适应列数 */
const conditionsGridRef = ref<HTMLElement | null>(null)
const containerWidth = ref(0)
let resizeObserver: ResizeObserver | null = null

/** 每个条件单元格最小宽度（含内部 gap），between 条件因多一个输入框需要更宽 */
const MIN_CELL_WIDTH = 260
const BETWEEN_MIN_CELL_WIDTH = 350
const GRID_GAP = 8

/** 条件网格列数：根据容器宽度自适应，有"之间"条件时最多2列，否则最多3列 */
const gridColumns = computed(() => {
  const minCellWidth = hasBetweenCondition.value
    ? BETWEEN_MIN_CELL_WIDTH
    : MIN_CELL_WIDTH
  const maxCols = hasBetweenCondition.value ? 2 : 3
  const width = containerWidth.value
  if (width <= 0) return maxCols
  const fitCols = Math.floor((width + GRID_GAP) / (minCellWidth + GRID_GAP))
  return Math.max(1, Math.min(maxCols, fitCols))
})

function ensureDefaultField(cond: SearchCondition) {
  if (!cond.field && visibleColumns.value.length > 0) {
    cond.field = visibleColumns.value[0].dataField
    const ops = getOperators(visibleColumns.value[0].dataType)
    if (ops.length > 0) cond.operator = ops[0].value
  }
}

function getOperators(dataType: string) {
  switch (dataType) {
    case 'number':
      return [
        { label: '等于', value: '=' },
        { label: '不等于', value: '<>' },
        { label: '大于', value: '>' },
        { label: '大于等于', value: '>=' },
        { label: '小于', value: '<' },
        { label: '小于等于', value: '<=' },
        { label: '之间', value: 'between' },
      ]
    case 'date':
    case 'datetime':
      return [
        { label: '等于', value: '=' },
        { label: '不等于', value: '<>' },
        { label: '大于', value: '>' },
        { label: '大于等于', value: '>=' },
        { label: '小于', value: '<' },
        { label: '小于等于', value: '<=' },
        { label: '之间', value: 'between' },
      ]
    case 'boolean':
      return [
        { label: '等于', value: '=' },
        { label: '不等于', value: '<>' },
      ]
    default:
      return [
        { label: '包含', value: 'contains' },
        { label: '不包含', value: 'notcontains' },
        { label: '等于', value: '=' },
        { label: '不等于', value: '<>' },
        { label: '以...开头', value: 'startswith' },
        { label: '以...结尾', value: 'endswith' },
      ]
  }
}

function onFieldChange(idx: number) {
  const col = visibleColumns.value.find(
    (c) => c.dataField === conditions.value[idx].field
  )
  if (col) {
    const ops = getOperators(col.dataType)
    if (
      ops.length > 0 &&
      !ops.find((o) => o.value === conditions.value[idx].operator)
    ) {
      conditions.value[idx].operator = ops[0].value
    }
    conditions.value[idx].value = ''
    conditions.value[idx].value2 = ''
  }
}

function getColType(dataField: string): string {
  const col = visibleColumns.value.find((c) => c.dataField === dataField)
  return col?.dataType || 'string'
}

/** 获取列的 lookup 配置（selectBox 下拉框数据源） */
function getColLookup(dataField: string): GridColumn['lookup'] | undefined {
  const col = visibleColumns.value.find((c) => c.dataField === dataField)
  if (col?.editorType === 'selectBox') return col.lookup
  return undefined
}

/** 远程 lookup 数据缓存：dataField → 数据数组 */
const remoteLookupCache = ref<Record<string, Record<string, unknown>[]>>({})

/** 加载远程 lookup 数据（已缓存则跳过） */
async function loadRemoteLookup(dataField: string) {
  const lookup = getColLookup(dataField)
  if (!lookup || lookup.dataSourceType !== 'remote' || !lookup.remoteUrl) return
  if (remoteLookupCache.value[dataField]) return
  try {
    const result = await httpRequest(lookup.remoteUrl, {
      method: lookup.method || 'GET',
    })
    const arr = extractList(result.data) as Record<string, unknown>[]
    remoteLookupCache.value = {
      ...remoteLookupCache.value,
      [dataField]: arr,
    }
  } catch (err) {
    console.error(`[高级查询远程数据源加载失败] ${dataField}`, err)
  }
}

/** 获取下拉框选项数组（类型化） */
function getLookupOptions(dataField: string): Record<string, unknown>[] {
  const lookup = getColLookup(dataField)
  if (!lookup) return []
  // 远程数据源：使用已加载的缓存数据
  if (lookup.dataSourceType === 'remote') {
    return remoteLookupCache.value[dataField] || []
  }
  return (lookup.dataSource || []) as Record<string, unknown>[]
}

/** 获取下拉框的值/显示字段名 */
function getLookupExprs(dataField: string): {
  valueExpr: string
  displayExpr: string
} {
  const lookup = getColLookup(dataField)
  return {
    valueExpr: lookup?.valueExpr ?? '',
    displayExpr: lookup?.displayExpr ?? '',
  }
}

/** 切换操作符时清空值 */
function onOperatorChange(idx: number) {
  conditions.value[idx].value = ''
  conditions.value[idx].value2 = ''
}

function addCondition() {
  const newCond = { field: '', operator: '', value: '', value2: '' }
  ensureDefaultField(newCond)
  conditions.value.push(newCond)
  nextTick(() => emit('heightChange'))
}

function removeCondition(idx: number) {
  if (conditions.value.length > 1) {
    conditions.value.splice(idx, 1)
    nextTick(() => emit('heightChange'))
  }
}

function clearConditions() {
  conditions.value = [{ field: '', operator: '', value: '', value2: '' }]
  ensureDefaultField(conditions.value[0])
}

function onSearch() {
  emit('search', activeConditions.value)
}

function onReset() {
  clearConditions()
  emit('reset')
}

function toggleExpand() {
  expanded.value = !expanded.value
  nextTick(() => emit('heightChange'))
}

async function loadSchemes() {
  try {
    const res = await httpRequest(
      `/api/lowcode/search-scheme/list?gridId=${encodeURIComponent(
        props.gridId || 'default'
      )}`,
      { method: 'POST' }
    )
    if (res.ok) {
      const list = (res.data as { data?: SearchScheme[] })?.data
      if (Array.isArray(list)) {
        schemes.value = list
      }
    }
  } catch (e) {
    console.error('[advSearch] load failed:', e)
  }
}

async function saveSchemes() {
  try {
    const res = await httpRequest('/api/lowcode/search-scheme', {
      method: 'POST',
      body: JSON.stringify({
        gridId: props.gridId || 'default',
        schemes: schemes.value,
      }),
    })
    if (!res.ok) {
      ElMessage.error('查询方案保存失败：后端连接异常')
      return
    }
    const body = res.data as { res?: boolean; msg?: string }
    if (body && body.res === false) {
      ElMessage.error(body.msg || '查询方案保存失败')
    }
  } catch (e) {
    console.error('[advSearch] save failed:', e)
    ElMessage.error('查询方案保存失败：后端连接异常')
  }
}

async function saveScheme() {
  const name = schemeName.value.trim()
  if (!name) return
  const validConds = activeConditions.value.map((c) => ({ ...c }))
  const existing = schemes.value.findIndex((s) => s.name === name)
  if (existing >= 0) {
    schemes.value[existing].conditions = validConds
  } else {
    schemes.value.push({
      name,
      conditions: validConds,
      isDefault: schemes.value.length === 0,
    })
  }
  await saveSchemes()
  schemeName.value = ''
}

function applyScheme(scheme: SearchScheme) {
  conditions.value = JSON.parse(JSON.stringify(scheme.conditions))
  // 兼容旧数据：确保每个条件都包含 value2 字段
  conditions.value.forEach((c) => {
    if (c.value2 === undefined) c.value2 = ''
  })
  if (conditions.value.length === 0) {
    conditions.value = [{ field: '', operator: '', value: '', value2: '' }]
  }
  conditions.value.forEach(ensureDefaultField)
  onSearch()
  showSchemeDialog.value = false
}

async function deleteScheme(idx: number) {
  const wasDefault = schemes.value[idx].isDefault
  schemes.value.splice(idx, 1)
  if (wasDefault && schemes.value.length > 0) {
    schemes.value[0].isDefault = true
  }
  await saveSchemes()
}

async function setDefault(idx: number) {
  schemes.value.forEach((s, i) => (s.isDefault = i === idx))
  await saveSchemes()
}

// 监听列变化，自动加载所有远程 lookup 数据源
watch(
  () => visibleColumns.value,
  (cols) => {
    for (const col of cols) {
      if (
        col.editorType === 'selectBox' &&
        col.lookup?.dataSourceType === 'remote' &&
        col.lookup?.remoteUrl
      ) {
        loadRemoteLookup(col.dataField)
      }
    }
  },
  { immediate: true }
)

onMounted(async () => {
  await loadSchemes()
  const def = schemes.value.find((s) => s.isDefault)
  if (def && def.conditions.length > 0) {
    conditions.value = JSON.parse(JSON.stringify(def.conditions))
    // 兼容旧数据：确保每个条件都包含 value2 字段
    conditions.value.forEach((c) => {
      if (c.value2 === undefined) c.value2 = ''
    })
  } else {
    ensureDefaultField(conditions.value[0])
  }
  // 监听条件网格容器宽度变化，自适应列数
  if (conditionsGridRef.value && typeof ResizeObserver !== 'undefined') {
    containerWidth.value = conditionsGridRef.value.clientWidth
    resizeObserver = new ResizeObserver((entries) => {
      for (const entry of entries) {
        containerWidth.value = entry.contentRect.width
      }
    })
    resizeObserver.observe(conditionsGridRef.value)
  }
})

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
})
</script>

<template>
  <div class="adv-search-panel">
    <!-- 条件 + 按钮 混合布局：第一行右侧放查询/重置/收起 -->
    <div class="adv-search-main">
      <div
        ref="conditionsGridRef"
        class="conditions-grid"
        :class="{ collapsed: !expanded }"
        :style="{ gridTemplateColumns: `repeat(${gridColumns}, 1fr)` }"
      >
        <div
          v-for="(cond, idx) in conditions"
          :key="idx"
          class="condition-cell"
        >
          <select
            class="field-select"
            v-model="cond.field"
            @change="onFieldChange(idx)"
          >
            <option value="">选择字段</option>
            <option
              v-for="col in visibleColumns"
              :key="col.dataField"
              :value="col.dataField"
            >
              {{ col.caption }}
            </option>
          </select>

          <select
            class="op-select"
            v-model="cond.operator"
            @change="onOperatorChange(idx)"
          >
            <option
              v-for="op in getOperators(getColType(cond.field))"
              :key="op.value"
              :value="op.value"
            >
              {{ op.label }}
            </option>
          </select>

          <select
            v-if="getColLookup(cond.field)"
            class="value-input"
            v-model="cond.value"
          >
            <option value="">请选择</option>
            <option
              v-for="item in getLookupOptions(cond.field)"
              :key="String(item[getLookupExprs(cond.field).valueExpr])"
              :value="item[getLookupExprs(cond.field).valueExpr]"
            >
              {{ item[getLookupExprs(cond.field).displayExpr] }}
            </option>
          </select>
          <input
            v-else-if="getColType(cond.field) === 'date'"
            class="value-input"
            type="date"
            v-model="cond.value"
            :placeholder="cond.operator === 'between' ? '开始' : '输入值'"
          />
          <input
            v-else-if="getColType(cond.field) === 'datetime'"
            class="value-input"
            type="datetime-local"
            v-model="cond.value"
            :placeholder="cond.operator === 'between' ? '开始' : '输入值'"
          />
          <input
            v-else-if="getColType(cond.field) === 'number'"
            class="value-input"
            type="number"
            v-model="cond.value"
            :placeholder="cond.operator === 'between' ? '开始' : '输入值'"
          />
          <input
            v-else
            class="value-input"
            type="text"
            v-model="cond.value"
            :placeholder="cond.operator === 'between' ? '开始' : '输入值'"
          />

          <!-- between 操作符的第二个值输入框（结束值） -->
          <template v-if="cond.operator === 'between'">
            <span class="between-sep">~</span>
            <select
              v-if="getColLookup(cond.field)"
              class="value-input"
              v-model="cond.value2"
            >
              <option value="">请选择</option>
              <option
                v-for="item in getLookupOptions(cond.field)"
                :key="String(item[getLookupExprs(cond.field).valueExpr])"
                :value="item[getLookupExprs(cond.field).valueExpr]"
              >
                {{ item[getLookupExprs(cond.field).displayExpr] }}
              </option>
            </select>
            <input
              v-else-if="getColType(cond.field) === 'date'"
              class="value-input"
              type="date"
              v-model="cond.value2"
              placeholder="结束"
            />
            <input
              v-else-if="getColType(cond.field) === 'datetime'"
              class="value-input"
              type="datetime-local"
              v-model="cond.value2"
              placeholder="结束"
            />
            <input
              v-else-if="getColType(cond.field) === 'number'"
              class="value-input"
              type="number"
              v-model="cond.value2"
              placeholder="结束"
            />
            <input
              v-else
              class="value-input"
              type="text"
              v-model="cond.value2"
              placeholder="结束"
            />
          </template>

          <button
            v-if="conditions.length > 1"
            class="cond-remove"
            @click="removeCondition(idx)"
            title="删除"
          >
            -
          </button>
          <span v-else class="cond-placeholder"></span>
        </div>
      </div>

      <!-- 右侧操作按钮组 -->
      <div class="side-actions">
        <button class="adv-btn adv-btn-primary" @click="onSearch">查询</button>
        <button class="adv-btn" @click="onReset">重置</button>
        <button
          class="adv-btn adv-btn-icon"
          @click="toggleExpand"
          :title="expanded ? '收起' : '展开'"
        >
          {{ expanded ? '⤒' : '⤓' }}
        </button>
      </div>
    </div>

    <!-- 展开时显示底部操作栏 -->
    <div v-show="expanded" class="bottom-bar">
      <div class="bottom-bar-left">
        <button class="adv-btn" @click="addCondition">+ 添加条件</button>
        <button class="adv-btn" @click="clearConditions">清除</button>
      </div>
      <div class="bottom-bar-right">
        <input
          class="scheme-name-input"
          v-model="schemeName"
          placeholder="方案名称"
          @keyup.enter="saveScheme"
        />
        <button class="adv-btn" @click="saveScheme">方案保存</button>
        <button class="adv-btn" @click="showSchemeDialog = !showSchemeDialog">
          方案配置
        </button>
      </div>
    </div>

    <!-- 方案配置弹窗 -->
    <Teleport to="body">
      <div
        v-if="showSchemeDialog"
        class="scheme-dialog-overlay"
        @click.self="showSchemeDialog = false"
      >
        <div class="scheme-dialog">
          <div class="scheme-dialog-header">
            <span>方案配置</span>
            <button class="dialog-close" @click="showSchemeDialog = false">
              x
            </button>
          </div>
          <div class="scheme-dialog-body">
            <div v-if="schemes.length === 0" class="scheme-empty">
              暂无保存的方案
            </div>
            <div
              v-for="(scheme, idx) in schemes"
              :key="idx"
              class="scheme-item"
            >
              <label class="scheme-radio">
                <input
                  type="radio"
                  :checked="scheme.isDefault"
                  @change="setDefault(idx)"
                />
                <span>默认</span>
              </label>
              <span class="scheme-name">{{ scheme.name }}</span>
              <span class="scheme-cond-count"
                >{{ scheme.conditions.length }} 个条件</span
              >
              <div class="scheme-actions">
                <button class="scheme-btn apply" @click="applyScheme(scheme)">
                  应用
                </button>
                <button class="scheme-btn delete" @click="deleteScheme(idx)">
                  删除
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.adv-search-panel {
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fafafa;
  margin-bottom: 4px;
  position: relative;
}
.adv-search-main {
  display: flex;
  gap: 8px;
  padding: 8px;
  align-items: flex-start;
}
.conditions-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  /* 最多3行高度，超出出现滚动条 */
  max-height: calc((32px + 8px) * 3);
  overflow-y: auto;
  overflow-x: hidden;
  padding-right: 4px;
}
/* 收起时只显示1行 */
.conditions-grid.collapsed {
  max-height: 32px;
  overflow: hidden;
}
.condition-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 32px;
}
.field-select,
.op-select {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 3px 6px;
  font-size: 12px;
  outline: none;
  background: #fff;
  height: 28px;
  box-sizing: border-box;
}
.field-select {
  width: 90px;
  flex-shrink: 0;
}
.op-select {
  min-width: 65px;
  flex-shrink: 0;
}
.value-input {
  flex: 1;
  min-width: 70px;
  height: 28px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 3px 8px;
  font-size: 13px;
  outline: none;
  box-sizing: border-box;
}
.value-input:focus {
  border-color: #1976d2;
}
.between-sep {
  font-size: 12px;
  color: #909399;
  flex-shrink: 0;
}
.cond-remove {
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 4px;
  background: #fef0f0;
  color: #f56c6c;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.cond-remove:hover {
  background: #fde2e2;
}
.cond-placeholder {
  width: 22px;
  flex-shrink: 0;
}

.side-actions {
  display: flex;
  flex-direction: row;
  gap: 6px;
  flex-shrink: 0;
  align-self: flex-start;
  padding-top: 2px;
}
.side-actions .adv-btn {
  width: auto;
}

.bottom-bar {
  display: flex;
  gap: 6px;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  padding: 6px 8px;
  border-top: 1px solid #ebeef5;
}
.bottom-bar-left,
.bottom-bar-right {
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
}
.adv-btn {
  border: 1px solid #dcdfe6;
  background: #fff;
  border-radius: 4px;
  padding: 4px 12px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  white-space: nowrap;
}
.adv-btn:hover {
  border-color: #1976d2;
  color: #1976d2;
}
.adv-btn-primary {
  background: #1976d2;
  color: #fff;
  border-color: #1976d2;
}
.adv-btn-primary:hover {
  background: #1565c0;
  color: #fff;
}
.adv-btn-icon {
  padding: 4px 8px;
  font-size: 14px;
}
.scheme-name-input {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 12px;
  width: 100px;
  outline: none;
  box-sizing: border-box;
}
.scheme-name-input:focus {
  border-color: #1976d2;
}

/* 滚动条样式 */
.conditions-grid::-webkit-scrollbar {
  width: 6px;
}
.conditions-grid::-webkit-scrollbar-track {
  background: #f0f0f0;
  border-radius: 3px;
}
.conditions-grid::-webkit-scrollbar-thumb {
  background: #c0c4cc;
  border-radius: 3px;
}
.conditions-grid::-webkit-scrollbar-thumb:hover {
  background: #909399;
}

.scheme-dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 20001;
}
.scheme-dialog {
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
  width: 500px;
  max-height: 400px;
  display: flex;
  flex-direction: column;
}
.scheme-dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 16px;
  border-bottom: 1px solid #e4e7ed;
  font-size: 14px;
  font-weight: 600;
}
.dialog-close {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 16px;
  color: #909399;
}
.dialog-close:hover {
  color: #303133;
}
.scheme-dialog-body {
  padding: 12px 16px;
  overflow: auto;
  flex: 1;
}
.scheme-empty {
  text-align: center;
  color: #909399;
  padding: 20px;
}
.scheme-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}
.scheme-radio {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  flex-shrink: 0;
}
.scheme-radio input {
  cursor: pointer;
}
.scheme-name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}
.scheme-cond-count {
  font-size: 12px;
  color: #909399;
}
.scheme-actions {
  margin-left: auto;
  display: flex;
  gap: 12px;
}
.scheme-btn {
  border: 1px solid #dcdfe6;
  background: #fff;
  border-radius: 4px;
  padding: 3px 10px;
  font-size: 12px;
  cursor: pointer;
}
.scheme-btn.apply {
  color: #1976d2;
  border-color: #1976d2;
}
.scheme-btn.apply:hover {
  background: #1976d2;
  color: #fff;
}
.scheme-btn.delete {
  color: #f56c6c;
  border-color: #fde2e2;
}
.scheme-btn.delete:hover {
  background: #f56c6c;
  color: #fff;
}
</style>
