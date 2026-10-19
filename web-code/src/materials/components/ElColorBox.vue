<script setup lang="ts">
import { computed } from 'vue'
import { ElColorPicker } from 'element-plus'
import { designerStore } from '@/store/designer'

const props = defineProps<{
  value?: string
  modelValue?: string
  readOnly?: boolean
  disabled?: boolean
  placeholder?: string
}>()

const emit = defineEmits<{
  'update:value': [val: string]
  'update:modelValue': [val: string]
  valueChanged: [e: { value: string }]
}>()

const inner = computed({
  get: () => props.modelValue ?? props.value ?? '',
  set: (v) => {
    emit('update:value', v)
    emit('update:modelValue', v)
    emit('valueChanged', { value: v })
  },
})

const isDesign = computed(() => !designerStore.previewMode)
</script>

<template>
  <div v-if="isDesign" class="el-color-box-placeholder">
    <span
      class="el-color-box-swatch"
      :style="{ background: inner || 'var(--el-fill-color-light)' }"
    />
    <span class="el-color-box-text">{{ inner || '请选择颜色' }}</span>
  </div>
  <ElColorPicker
    v-else
    v-model="inner"
    :disabled="readOnly || disabled"
  />
</template>

<style scoped>
.el-color-box-placeholder {
  display: flex;
  align-items: center;
  gap: 8px;
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
.el-color-box-swatch {
  display: inline-block;
  width: 16px;
  height: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 2px;
  flex-shrink: 0;
}
.el-color-box-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>