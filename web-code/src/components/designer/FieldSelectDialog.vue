<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { t } from '@/store/lang'

export interface FieldOption {
  dbField: string
  fieldName: string
  displayText: string
}

const props = defineProps<{
  visible: boolean
  /** 当前选中的字段 dbField */
  modelValue?: string
  /** 可选字段列表 */
  fields: FieldOption[]
  /** 弹窗标题 */
  title?: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'update:modelValue': [value: string]
  select: [dbField: string, displayText: string]
}>()

const selectedField = ref('')
const searchKey = ref('')

const filteredFields = computed(() => {
  const key = searchKey.value.trim().toLowerCase()
  if (!key) return props.fields
  return props.fields.filter(
    (f) =>
      f.dbField.toLowerCase().includes(key) ||
      f.fieldName.toLowerCase().includes(key) ||
      f.displayText.toLowerCase().includes(key),
  )
})

function confirmSelect() {
  if (!selectedField.value) return
  const f = props.fields.find((x) => x.dbField === selectedField.value)
  emit('select', selectedField.value, f?.displayText || '')
  emit('update:modelValue', selectedField.value)
  emit('update:visible', false)
}

function closeDialog() {
  emit('update:visible', false)
}

watch(
  () => props.visible,
  (val) => {
    if (val) {
      selectedField.value = props.modelValue || ''
      searchKey.value = ''
    }
  },
)
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="fsd-overlay" @click.self="closeDialog">
      <div class="fsd-dialog">
        <div class="fsd-header">
          <span>{{ title || t('选择字段') }}</span>
          <button class="fsd-close" @click="closeDialog">✕</button>
        </div>
        <div class="fsd-body">
          <div class="fsd-search">
            <input
              v-model="searchKey"
              class="fsd-search-input"
              :placeholder="t('搜索字段...')"
            />
          </div>
          <div class="fsd-list">
            <div
              v-for="f in filteredFields"
              :key="f.dbField"
              class="fsd-item"
              :class="{ 'fsd-item-active': f.dbField === selectedField }"
              @click="selectedField = f.dbField"
            >
              <span class="fsd-item-text">{{ f.displayText }}</span>
              <span class="fsd-item-field">{{ f.dbField }}</span>
            </div>
            <div v-if="!filteredFields.length" class="fsd-empty">
              {{ t('暂无可选字段') }}
            </div>
          </div>
        </div>
        <div class="fsd-footer">
          <span class="fsd-selected-info" v-if="selectedField">
            {{ t('已选：') }}{{ selectedField }}
          </span>
          <div class="fsd-footer-actions">
            <button class="fsd-btn" @click="closeDialog">
              {{ t('取消') }}
            </button>
            <button
              class="fsd-btn fsd-btn-primary"
              :disabled="!selectedField"
              @click="confirmSelect"
            >
              {{ t('确定') }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.fsd-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 20000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.fsd-dialog {
  background: #fff;
  border-radius: 8px;
  width: 520px;
  max-width: 95vw;
  height: 480px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}
.fsd-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
  font-size: 15px;
  font-weight: 600;
}
.fsd-close {
  border: none;
  background: none;
  font-size: 16px;
  color: #999;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
}
.fsd-close:hover {
  background: #f0f0f0;
}
.fsd-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.fsd-search {
  padding: 8px 12px;
  border-bottom: 1px solid #eee;
  flex-shrink: 0;
}
.fsd-search-input {
  width: 100%;
  box-sizing: border-box;
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
}
.fsd-search-input:focus {
  border-color: #2563eb;
}
.fsd-list {
  flex: 1;
  overflow-y: auto;
  padding: 4px 0;
}
.fsd-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  cursor: pointer;
  transition: background 0.15s;
}
.fsd-item:hover {
  background: #f5f5f5;
}
.fsd-item-active {
  background: #eff6ff;
}
.fsd-item-active:hover {
  background: #dbeafe;
}
.fsd-item-text {
  font-size: 13px;
  color: #333;
}
.fsd-item-field {
  font-size: 12px;
  color: #999;
  font-family: monospace;
}
.fsd-empty {
  padding: 32px;
  text-align: center;
  color: #999;
  font-size: 13px;
}
.fsd-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-top: 1px solid #eee;
}
.fsd-selected-info {
  font-size: 13px;
  color: #2563eb;
}
.fsd-footer-actions {
  display: flex;
  gap: 8px;
}
.fsd-btn {
  border: 1px solid #d1d5db;
  background: #fff;
  color: #374151;
  padding: 6px 16px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}
.fsd-btn:hover {
  background: #f3f4f6;
}
.fsd-btn-primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}
.fsd-btn-primary:hover {
  background: #4338ca;
}
.fsd-btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>