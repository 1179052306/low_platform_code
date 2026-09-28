<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { t } from '@/store/lang'

/**
 * 汇总（summary）高级配置编辑器。
 * 不再手动编写 JSON：从当前表格列字段读取，仅数字类型列可参与汇总，
 * 通过勾选 + 选择汇总方式生成 DevExtreme summary 配置。
 */

interface SummaryItem {
  column: string
  caption: string
  summaryType: string
  enabled: boolean
}

const props = defineProps<{
  modelValue: string
  visible: boolean
  /** 当前表格列配置（dx-data-grid 的 columns） */
  columns?: unknown[]
  title?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:visible': [value: boolean]
}>()

/** 支持汇总的汇总方式 */
const SUMMARY_TYPES = [
  { label: '求和 (sum)', value: 'sum' },
  { label: '平均值 (avg)', value: 'avg' },
  { label: '最小值 (min)', value: 'min' },
  { label: '最大值 (max)', value: 'max' },
  { label: '计数 (count)', value: 'count' },
]

/** 判断列是否为数字类型（dataType=number，或配置了数字格式 format） */
function isNumericColumn(col: Record<string, unknown>): boolean {
  const dt = String(col.dataType ?? '')
  if (dt === 'number') return true
  // 未声明 dataType 时，若 format 为数字格式也视为数字列
  const fmt = col.format
  if (fmt && typeof fmt === 'object') {
    const type = String((fmt as Record<string, unknown>).type ?? '')
    if (
      [
        'fixedPoint',
        'currency',
        'percent',
        'millions',
        'billions',
        'largeNumber',
        'exponential',
      ].includes(type)
    )
      return true
  } else if (typeof fmt === 'string') {
    if (
      [
        'fixedPoint',
        'currency',
        'percent',
        'millions',
        'billions',
        'largeNumber',
      ].includes(fmt)
    )
      return true
  }
  return false
}

/** 当前表格中的数字列（仅这些列可参与汇总） */
const numericColumns = computed(() => {
  if (!Array.isArray(props.columns)) return []
  return props.columns
    .filter(
      (c) =>
        c &&
        typeof c === 'object' &&
        isNumericColumn(c as Record<string, unknown>)
    )
    .map((c) => {
      const col = c as Record<string, unknown>
      return {
        dataField: String(col.dataField ?? col.name ?? ''),
        caption: String(col.caption ?? col.dataField ?? col.name ?? ''),
      }
    })
    .filter((c) => c.dataField)
})

/** 当前已配置的汇总项（来自 summary JSON） */
const summaryItems = ref<SummaryItem[]>([])

/** 解析已有 summary 配置（兼容 { totalItems: [...] } / 数组 / groupItems 三种形态） */
function parseSummary(text: string): SummaryItem[] {
  if (!text || !text.trim()) return []
  try {
    const data = JSON.parse(text)
    let raw: unknown[] = []
    if (Array.isArray(data)) {
      raw = data
    } else if (data && typeof data === 'object') {
      if (Array.isArray(data.totalItems)) raw = data.totalItems
      else if (Array.isArray(data.groupItems)) raw = data.groupItems
    }
    return raw
      .map((item) => {
        const it = (item ?? {}) as Record<string, unknown>
        return {
          column: String(it.column ?? it.selector ?? ''),
          caption: String(it.caption ?? ''),
          summaryType: String(it.summaryType ?? 'sum'),
          enabled: true,
        }
      })
      .filter((it) => it.column)
  } catch {
    return []
  }
}

watch(
  () => [props.modelValue, props.visible, props.columns] as const,
  () => {
    if (!props.visible) return
    // 已配置的汇总项回显：仅保留与当前数字列匹配的列
    const configured = parseSummary(props.modelValue)
    const cfgMap = new Map(configured.map((it) => [it.column, it]))
    summaryItems.value = numericColumns.value.map((col) => {
      const existed = cfgMap.get(col.dataField)
      return {
        column: col.dataField,
        caption: col.caption,
        summaryType: existed?.summaryType ?? 'sum',
        enabled: existed?.enabled ?? false,
      }
    })
  },
  { immediate: true }
)

/** 序列化为 DevExtreme summary 配置 */
function serialize(): string {
  const totalItems = summaryItems.value
    .filter((it) => it.enabled)
    .map((it) => ({ column: it.column, summaryType: it.summaryType }))
  return JSON.stringify({ totalItems }, null, 2)
}

function onConfirm(): void {
  emit('update:modelValue', serialize())
  emit('update:visible', false)
}

function onCancel(): void {
  emit('update:visible', false)
}

/** 勾选状态汇总 */
const enabledCount = computed(
  () => summaryItems.value.filter((it) => it.enabled).length
)

function toggleAll(checked: boolean): void {
  summaryItems.value.forEach((it) => {
    it.enabled = checked
  })
}
</script>

<template>
  <div v-if="visible" class="dialog-overlay" @click.self="onCancel">
    <div class="dialog-container">
      <div class="dialog-header">
        <span class="dialog-title">{{ title || t('汇总高级配置') }}</span>
        <button class="dialog-close" @click="onCancel">x</button>
      </div>
      <div class="dialog-body">
        <div class="summary-tip">
          {{ t('汇总字段从当前表格列中读取，') }}<b>{{ t('仅数字类型') }}</b
          >{{ t('的字段可参与汇总（自动过滤）。') }}
        </div>

        <div v-if="!numericColumns.length" class="empty-tip">
          {{ t('当前表格没有数字类型字段，无法配置汇总。') }}<br />
          {{ t('请先在「列配置」中为需要汇总的列设置数据类型为「数字」。') }}
        </div>

        <template v-else>
          <!-- 顶部工具栏 -->
          <div class="toolbar">
            <span class="toolbar-count"
              >{{ t('数字字段：') }}{{ numericColumns.length
              }}{{ t(' 个，已勾选') }} {{ enabledCount }}{{ t(' 个') }}</span
            >
            <div class="toolbar-actions">
              <button class="link-btn" @click="toggleAll(true)">
                {{ t('全选') }}
              </button>
              <button class="link-btn" @click="toggleAll(false)">
                {{ t('全不选') }}
              </button>
            </div>
          </div>

          <!-- 汇总字段列表 -->
          <div class="summary-list">
            <div
              v-for="item in summaryItems"
              :key="item.column"
              class="summary-row"
              :class="{ checked: item.enabled }"
            >
              <label class="row-check">
                <input type="checkbox" v-model="item.enabled" />
                <span class="field-name">{{ item.column }}</span>
                <span class="field-caption" v-if="item.caption !== item.column"
                  >({{ item.caption }})</span
                >
              </label>
              <select
                v-if="item.enabled"
                class="summary-type-select"
                v-model="item.summaryType"
              >
                <option
                  v-for="opt in SUMMARY_TYPES"
                  :key="opt.value"
                  :value="opt.value"
                >
                  {{ t(opt.label) }}
                </option>
              </select>
              <span v-else class="type-placeholder">{{ t('未参与汇总') }}</span>
            </div>
          </div>
        </template>
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
  width: 520px;
  max-width: 90vw;
  max-height: 80vh;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
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
  font-size: 14px;
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
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.summary-tip {
  font-size: 12px;
  color: #606266;
  background: #f0f7ff;
  border: 1px solid #b3d8ff;
  border-radius: 4px;
  padding: 8px 10px;
  line-height: 1.6;
}
.summary-tip b {
  color: #1976d2;
}

.empty-tip {
  text-align: center;
  color: #909399;
  font-size: 13px;
  padding: 24px 0;
  line-height: 1.8;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0;
}
.toolbar-count {
  font-size: 12px;
  color: #909399;
}
.toolbar-actions {
  display: flex;
  gap: 8px;
}
.link-btn {
  border: none;
  background: none;
  color: #1976d2;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 4px;
}
.link-btn:hover {
  text-decoration: underline;
}

.summary-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 8px 12px;
  transition: border-color 0.15s, background 0.15s;
}
.summary-row.checked {
  border-color: #1976d2;
  background: #f0f7ff;
}
.row-check {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  min-width: 0;
  flex: 1;
}
.row-check input {
  width: 15px;
  height: 15px;
  cursor: pointer;
  flex-shrink: 0;
}
.field-name {
  font-weight: 500;
  color: #303133;
  font-size: 13px;
}
.field-caption {
  color: #909399;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.summary-type-select {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 4px 8px;
  font-size: 12px;
  outline: none;
  background: #fff;
  cursor: pointer;
  width: 140px;
  flex-shrink: 0;
}
.summary-type-select:focus {
  border-color: #1976d2;
}
.type-placeholder {
  color: #c0c4cc;
  font-size: 12px;
  width: 140px;
  text-align: right;
  flex-shrink: 0;
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
