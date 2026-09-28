<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { t } from '@/store/lang'

const props = defineProps<{
  modelValue: string
  visible: boolean
  title?: string
  columns?: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:visible': [value: boolean]
}>()

/** 解析后的表格数据 */
interface TableRow {
  [key: string]: string | number | boolean | null
}

const columns = ref<string[]>([])
const rows = ref<TableRow[]>([])
const rawText = ref('')
const mode = ref<'table' | 'json'>('table')
const parseError = ref('')

/** 是否由外部传入列配置（锁定列，不允许添加/删除/重命名列） */
const isColumnsLocked = computed(() => Array.isArray(props.columns))
/** 外部列配置是否为空（需提示先配置列） */
const isColumnsEmpty = computed(
  () => isColumnsLocked.value && props.columns!.length === 0
)

// 解析 JSON 数据
function parseJson(
  text: string
): { columns: string[]; rows: TableRow[] } | null {
  try {
    const data = JSON.parse(text)
    if (Array.isArray(data)) {
      if (data.length === 0) {
        return { columns: [], rows: [] }
      }
      // 收集所有列名（取第一个元素的所有 key）
      const cols = new Set<string>()
      for (const item of data) {
        if (typeof item === 'object' && item !== null) {
          Object.keys(item).forEach((k) => cols.add(k))
        }
      }
      const colArr = Array.from(cols)
      const rowArr = data.map((item: Record<string, unknown>) => {
        const row: TableRow = {}
        if (typeof item === 'object' && item !== null) {
          colArr.forEach((c) => {
            const val = item[c]
            row[c] =
              val === undefined ? '' : (val as string | number | boolean | null)
          })
        }
        return row
      })
      return { columns: colArr, rows: rowArr }
    } else if (typeof data === 'object' && data !== null) {
      // 如果是对象而非数组，包裹成数组
      return parseJson(JSON.stringify([data]))
    }
    return null
  } catch {
    return null
  }
}

// 初始化
watch(
  () => props.modelValue,
  (val) => {
    rawText.value = val || ''
    parseError.value = ''
    if (mode.value === 'table') {
      const result = parseJson(val || '')
      if (result) {
        // 锁定列模式：使用外部列配置作为表头
        if (isColumnsLocked.value && props.columns!.length > 0) {
          columns.value = [...props.columns!]
          rows.value = result.rows.map((row) => {
            const newRow: TableRow = {}
            columns.value.forEach((c) => {
              newRow[c] = row[c] ?? ''
            })
            return newRow
          })
        } else {
          columns.value = result.columns
          rows.value = result.rows
        }
      } else {
        columns.value = isColumnsLocked.value ? [...(props.columns || [])] : []
        rows.value = []
        if (val && val.trim()) {
          parseError.value = t('JSON 格式错误，请切换到 JSON 模式编辑')
        }
      }
    }
  },
  { immediate: true }
)

// 外部列配置变化时同步表头
watch(
  () => props.columns,
  (cols) => {
    if (!Array.isArray(cols)) return
    columns.value = [...cols]
    // 保留已有行数据，补齐新列
    rows.value = rows.value.map((row) => {
      const newRow: TableRow = {}
      columns.value.forEach((c) => {
        newRow[c] = row[c] ?? ''
      })
      return newRow
    })
  }
)

// 序列化表格数据
function serializeTable(): string {
  const result = rows.value.map((row) => {
    const obj: Record<string, unknown> = {}
    columns.value.forEach((c) => {
      const val = row[c]
      // 尝试转换为数字或布尔
      if (val === '' || val === null || val === undefined) {
        obj[c] = ''
      } else if (typeof val === 'string') {
        // 尝试解析数字
        if (!isNaN(Number(val)) && val !== '') {
          obj[c] = Number(val)
        } else if (val === 'true') {
          obj[c] = true
        } else if (val === 'false') {
          obj[c] = false
        } else {
          obj[c] = val
        }
      } else {
        obj[c] = val
      }
    })
    return obj
  })
  return JSON.stringify(result, null, 2)
}

// 添加行
function addRow() {
  const newRow: TableRow = {}
  columns.value.forEach((c) => {
    newRow[c] = ''
  })
  rows.value.push(newRow)
}

// 删除行
function removeRow(index: number) {
  rows.value.splice(index, 1)
}

// 添加列
function addColumn() {
  const name = prompt(t('请输入列名'))
  if (!name) return
  if (columns.value.includes(name)) return
  columns.value.push(name)
  rows.value.forEach((row) => {
    row[name] = ''
  })
}

// 删除列
function removeColumn(col: string) {
  if (!confirm(t('确定删除列 "') + col + t('" 吗？'))) return
  columns.value = columns.value.filter((c) => c !== col)
  rows.value.forEach((row) => {
    delete row[col]
  })
}

// 重命名列
function renameColumn(oldName: string) {
  const newName = prompt(t('请输入新列名'), oldName)
  if (!newName || newName === oldName) return
  if (columns.value.includes(newName)) {
    alert(t('列名已存在'))
    return
  }
  columns.value = columns.value.map((c) => (c === oldName ? newName : c))
  rows.value.forEach((row) => {
    row[newName] = row[oldName]
    delete row[oldName]
  })
}

// 切换到 JSON 模式
function switchToJson() {
  if (mode.value === 'table') {
    // 从表格数据生成 JSON
    rawText.value = serializeTable()
  } else {
    // 从 JSON 解析到表格
    const result = parseJson(rawText.value)
    if (result) {
      columns.value = result.columns
      rows.value = result.rows
      parseError.value = ''
    } else {
      parseError.value = t('JSON 格式错误')
      return
    }
  }
  mode.value = mode.value === 'table' ? 'json' : 'table'
}

// 确认
function onConfirm() {
  if (mode.value === 'table') {
    emit('update:modelValue', serializeTable())
  } else {
    // 验证 JSON 格式
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

// 新增默认空数据
function initEmptyData() {
  const c1 = t('列1')
  const c2 = t('列2')
  columns.value = [c1, c2]
  rows.value = [{ [c1]: '', [c2]: '' }]
  rawText.value = serializeTable()
}

// 自动检测：若当前为空则初始化为空表格
const isEmpty = computed(() => {
  if (mode.value === 'table') {
    return columns.value.length === 0 && rows.value.length === 0
  }
  return !rawText.value.trim()
})

function handleAutoInit() {
  if (isEmpty.value && mode.value === 'table') {
    initEmptyData()
  }
}

watch(mode, () => {
  handleAutoInit()
})
</script>

<template>
  <div v-if="visible" class="dialog-overlay" @click.self="onCancel">
    <div class="dialog-container dialog-large">
      <div class="dialog-header">
        <span class="dialog-title">{{ title || t('数据源编辑') }}</span>
        <div class="dialog-header-actions">
          <button class="mode-toggle" @click="switchToJson">
            {{ mode === 'table' ? t('切换到 JSON 模式') : t('切换到表格模式') }}
          </button>
          <button class="dialog-close" @click="onCancel">✕</button>
        </div>
      </div>
      <div class="dialog-body">
        <!-- 表格模式 -->
        <div v-if="mode === 'table'" class="table-editor">
          <div v-if="parseError" class="parse-error">{{ parseError }}</div>
          <div v-if="isColumnsEmpty" class="table-empty-tip">
            <span>{{ t('请先在列配置中设置列字段') }}</span>
          </div>
          <div v-else-if="isEmpty && !parseError" class="table-empty-tip">
            <span>{{ t('暂无数据，点击下方按钮添加') }}</span>
          </div>
          <div class="table-wrapper" v-if="!isColumnsEmpty">
            <table v-if="columns.length" class="data-table">
              <thead>
                <tr>
                  <th v-for="col in columns" :key="col" class="table-header">
                    <div class="th-content">
                      <template v-if="isColumnsLocked">
                        <span class="th-locked-label">{{ col }}</span>
                      </template>
                      <template v-else>
                        <input
                          class="th-input"
                          :value="col"
                          @change="renameColumn(col)"
                        />
                        <span
                          class="th-delete"
                          @click="removeColumn(col)"
                          :title="t('删除列')"
                          >✕</span
                        >
                      </template>
                    </div>
                  </th>
                  <th class="table-action-header">{{ t('操作') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, rowIdx) in rows" :key="rowIdx">
                  <td v-for="col in columns" :key="col" class="table-cell">
                    <input
                      class="cell-input"
                      :value="String(row[col] ?? '')"
                      @input="(e: Event) => { rows[rowIdx][col] = (e.target as HTMLInputElement).value }"
                    />
                  </td>
                  <td class="table-action-cell">
                    <button
                      class="row-delete"
                      @click="removeRow(rowIdx)"
                      :title="t('删除行')"
                    >
                      {{ t('删除') }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="table-actions" v-if="!isColumnsEmpty">
            <button class="action-btn-add" @click="addRow">
              {{ t('+ 添加行') }}
            </button>
            <button
              v-if="!isColumnsLocked"
              class="action-btn-add"
              @click="addColumn"
            >
              {{ t('+ 添加列') }}
            </button>
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
  </div>
</template>

<style scoped>
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
  width: 720px;
  max-width: 95vw;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.dialog-large {
  width: 780px;
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
  padding: 16px;
  flex: 1;
  overflow: auto;
}
.table-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
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
.table-wrapper {
  overflow-x: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.table-header {
  background: #f5f7fa;
  padding: 8px;
  text-align: left;
  font-weight: 500;
  border-bottom: 1px solid #e4e7ed;
  min-width: 120px;
}
.th-content {
  display: flex;
  align-items: center;
  gap: 4px;
}
.th-input {
  flex: 1;
  border: 1px solid transparent;
  background: transparent;
  padding: 2px 4px;
  font-size: 13px;
  border-radius: 3px;
  outline: none;
  min-width: 60px;
}
.th-input:hover {
  border-color: #dcdfe6;
  background: #fff;
}
.th-input:focus {
  border-color: #1976d2;
  background: #fff;
}
.th-delete {
  color: #c0c4cc;
  cursor: pointer;
  font-size: 12px;
  padding: 2px;
}
.th-delete:hover {
  color: #f56c6c;
}
.th-locked-label {
  flex: 1;
  padding: 2px 4px;
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}
.table-row {
  border-bottom: 1px solid #ebeef5;
}
.table-cell {
  padding: 4px 8px;
  border-bottom: 1px solid #ebeef5;
}
.cell-input {
  width: 100%;
  border: 1px solid transparent;
  background: transparent;
  padding: 4px 6px;
  font-size: 13px;
  border-radius: 3px;
  outline: none;
}
.cell-input:hover {
  border-color: #dcdfe6;
  background: #fafbfc;
}
.cell-input:focus {
  border-color: #1976d2;
  background: #fff;
}
.table-action-header,
.table-action-cell {
  width: 60px;
  text-align: center;
  padding: 8px 4px;
  border-bottom: 1px solid #ebeef5;
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
.table-actions {
  display: flex;
  gap: 8px;
  padding-top: 8px;
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