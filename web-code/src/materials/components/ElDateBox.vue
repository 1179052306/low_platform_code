<script setup lang="ts">
import { computed } from 'vue'
import { ElDatePicker } from 'element-plus'
import { designerStore } from '@/store/designer'
import { dxDateTypeToEl } from '../dx-to-el'

const props = defineProps<{
  value?: any
  modelValue?: any
  readOnly?: boolean
  disabled?: boolean
  placeholder?: string
  type?: string
  displayFormat?: string
  min?: any
  max?: any
  showClearButton?: boolean
  interval?: number
}>()

const emit = defineEmits<{
  'update:value': [val: any]
  'update:modelValue': [val: any]
  valueChanged: [e: { value: any }]
  focusIn: []
  focusOut: []
}>()

const inner = computed({
  get: () => props.modelValue ?? props.value,
  set: (v) => {
    emit('update:value', v)
    emit('update:modelValue', v)
    emit('valueChanged', { value: v })
  },
})

const elType = computed(() => dxDateTypeToEl(props.type))
const isDesign = computed(() => !designerStore.previewMode)

const displayText = computed(() => {
  const v = inner.value
  if (v == null || v === '') return props.placeholder ?? '请选择日期'
  return String(v)
})
</script>

<template>
  <div class="el-date-box-wrapper">
    <div v-if="isDesign" class="el-date-box-placeholder">
      <span class="el-date-box-icon">📅</span>
      <span class="el-date-box-text">{{ displayText }}</span>
    </div>
    <ElDatePicker
      v-else
      v-model="inner"
      :type="elType"
      :readonly="readOnly"
      :disabled="disabled"
      :placeholder="placeholder ?? '请选择日期'"
      :clearable="showClearButton"
      :format="displayFormat"
      :value-format="elType === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD'"
      style="width: 100%"
      @focus="() => emit('focusIn')"
      @blur="() => emit('focusOut')"
    />
  </div>
</template>

<style scoped>
.el-date-box-wrapper {
  width: 100%;
}
.el-date-box-placeholder {
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
.el-date-box-icon {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}
.el-date-box-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>