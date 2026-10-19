<script setup lang="ts">
import { computed } from 'vue'
import { ElDatePicker } from 'element-plus'
import { designerStore } from '@/store/designer'

const props = defineProps<{
  value?: any
  modelValue?: any
  readOnly?: boolean
  disabled?: boolean
  min?: any
  max?: any
  firstDayOfWeek?: number
  selectionMode?: string
}>()

const emit = defineEmits<{
  'update:value': [val: any]
  'update:modelValue': [val: any]
  valueChanged: [e: { value: any }]
}>()

const inner = computed({
  get: () => props.modelValue ?? props.value,
  set: (v) => {
    emit('update:value', v)
    emit('update:modelValue', v)
    emit('valueChanged', { value: v })
  },
})

const isDesign = computed(() => !designerStore.previewMode)

const displayText = computed(() => {
  const v = inner.value
  if (v == null || v === '') return '请选择日期'
  return String(v)
})
</script>

<template>
  <div class="el-calendar-box-wrapper">
    <div v-if="isDesign" class="el-calendar-box-placeholder">
      <span class="el-calendar-box-icon">🗓</span>
      <span class="el-calendar-box-text">{{ displayText }}</span>
    </div>
    <ElDatePicker
      v-else
      v-model="inner"
      type="date"
      :readonly="readOnly"
      :disabled="disabled"
      :min-date="min"
      :max-date="max"
      value-format="YYYY-MM-DD"
      style="width: 100%"
    />
  </div>
</template>

<style scoped>
.el-calendar-box-wrapper {
  width: 100%;
}
.el-calendar-box-placeholder {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 11px;
  height: 32px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base, 4px);
  background: var(--el-fill-color-blank);
  color: var(--el-text-color-regular);
  font-size: 13px;
  width: 100%;
  box-sizing: border-box;
  cursor: default;
}
.el-calendar-box-icon {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.el-calendar-box-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>