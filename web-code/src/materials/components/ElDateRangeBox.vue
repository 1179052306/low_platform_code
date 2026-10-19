<script setup lang="ts">
import { computed } from 'vue'
import { ElDatePicker } from 'element-plus'
import { designerStore } from '@/store/designer'

const props = defineProps<{
  value?: any
  modelValue?: any
  readOnly?: boolean
  disabled?: boolean
  placeholder?: string
  startDate?: any
  endDate?: any
  displayFormat?: string
}>()

const emit = defineEmits<{
  'update:value': [val: any]
  'update:modelValue': [val: any]
  valueChanged: [e: { value: any; startDate: any; endDate: any }]
}>()

const inner = computed({
  get: () => props.modelValue ?? props.value ?? [],
  set: (v) => {
    emit('update:value', v)
    emit('update:modelValue', v)
    emit('valueChanged', { value: v, startDate: v?.[0], endDate: v?.[1] })
  },
})

const isDesign = computed(() => !designerStore.previewMode)

const displayText = computed(() => {
  const v = inner.value
  if (Array.isArray(v) && v.length === 2 && v[0] && v[1]) {
    return `${v[0]} 至 ${v[1]}`
  }
  return `${props.placeholder ?? '开始日期'} 至 结束日期`
})
</script>

<template>
  <div class="el-date-range-wrapper">
    <div v-if="isDesign" class="el-date-range-placeholder">
      <span class="el-date-range-icon">📅</span>
      <span class="el-date-range-text">{{ displayText }}</span>
    </div>
    <ElDatePicker
      v-else
      v-model="inner"
      type="daterange"
      :readonly="readOnly"
      :disabled="disabled"
      :start-placeholder="placeholder ?? '开始日期'"
      end-placeholder="结束日期"
      :format="displayFormat"
      value-format="YYYY-MM-DD"
      range-separator="至"
      style="width: 100%"
    />
  </div>
</template>

<style scoped>
.el-date-range-wrapper {
  width: 100%;
}
.el-date-range-placeholder {
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
.el-date-range-icon {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.el-date-range-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>