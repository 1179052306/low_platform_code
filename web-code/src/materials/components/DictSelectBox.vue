<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { listDictItems } from '@/store/api-store'
import type { DataDictItem } from '@/types/dict'
import { t } from '@/store/lang'

const props = defineProps<{
  dictCode?: string
  value?: any
  modelValue?: any
  disabled?: boolean
  readOnly?: boolean
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:value': [val: any]
  'update:modelValue': [val: any]
  valueChanged: [e: { value: any; selectedItem: any }]
  focusOut: []
}>()

const inner = computed(() => props.modelValue ?? props.value)

const dictItems = ref<DataDictItem[]>([])
const loading = ref(false)
const showDialog = ref(false)
const searchText = ref('')

async function loadItems() {
  if (!props.dictCode) {
    dictItems.value = []
    return
  }
  loading.value = true
  try {
    const items = await listDictItems(props.dictCode)
    dictItems.value = items.filter(i => i.enabled).sort((a, b) => a.sortNo - b.sortNo)
  } catch {
    dictItems.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.dictCode, loadItems, { immediate: true })

const currentLabel = computed(() => {
  if (inner.value == null || inner.value === '') return ''
  const item = dictItems.value.find(i => i.value === String(inner.value))
  return item ? item.label : String(inner.value)
})

const filteredItems = computed(() => {
  const kw = searchText.value.trim().toLowerCase()
  if (!kw) return dictItems.value
  return dictItems.value.filter(i =>
    i.value.toLowerCase().includes(kw) || i.label.toLowerCase().includes(kw)
  )
})

function pickItem(item: DataDictItem) {
  emit('update:value', item.value)
  emit('update:modelValue', item.value)
  emit('valueChanged', { value: item.value, selectedItem: item })
  showDialog.value = false
}

function clearValue() {
  emit('update:value', '')
  emit('update:modelValue', '')
  emit('valueChanged', { value: '', selectedItem: null })
}

function onBlur() {
  emit('focusOut')
}
</script>

<template>
  <div class="dict-select-box">
    <input
      class="dict-select-input"
      type="text"
      readonly
      :value="currentLabel"
      :placeholder="placeholder || t('请选择')"
      :disabled="disabled || readOnly"
      @click="!(disabled || readOnly) && (showDialog = true)"
      @blur="onBlur"
    />
    <button
      v-if="inner != null && inner !== '' && !disabled && !readOnly"
      class="dict-select-clear"
      @click="clearValue"
    >×</button>
    <Teleport to="body" v-if="showDialog">
      <div class="dict-select-overlay" @click="showDialog = false">
        <div class="dict-select-dialog" @click.stop>
          <div class="dict-select-header">
            <span>{{ t('选择字典项') }}</span>
            <button class="dict-select-close" @click="showDialog = false">×</button>
          </div>
          <div class="dict-select-body">
            <input
              class="dict-select-search"
              type="text"
              v-model="searchText"
              :placeholder="t('搜索值或标签')"
            />
            <table class="dict-select-table">
              <thead>
                <tr>
                  <th>{{ t('标签') }}</th>
                  <th>{{ t('值') }}</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="loading">
                  <td colspan="3" class="dict-select-empty">{{ t('加载中...') }}</td>
                </tr>
                <tr v-else-if="filteredItems.length === 0">
                  <td colspan="3" class="dict-select-empty">{{ t('暂无数据') }}</td>
                </tr>
                <tr
                  v-for="item in filteredItems"
                  :key="item.id"
                  :class="{ 'is-selected': inner === item.value }"
                >
                  <td>{{ item.label }}</td>
                  <td>{{ item.value }}</td>
                  <td class="dict-select-action">
                    <button class="dict-select-pick" @click="pickItem(item)">
                      {{ inner === item.value ? t('已选') : t('选择') }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.dict-select-box {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
}
.dict-select-input {
  flex: 1;
  padding: 4px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  background: #fff;
  color: #1f2937;
  min-width: 0;
}
.dict-select-input:disabled {
  background: #f5f7fa;
  cursor: not-allowed;
  color: #c0c4cc;
}
.dict-select-input:hover:not(:disabled) {
  border-color: #c0c4cc;
}
.dict-select-clear {
  border: none;
  background: #c0c4cc;
  color: #fff;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
  padding: 0;
  flex-shrink: 0;
}
.dict-select-clear:hover {
  background: #909399;
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
  width: 480px;
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
  width: 100%;
  padding: 6px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  margin-bottom: 10px;
  box-sizing: border-box;
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
</style>