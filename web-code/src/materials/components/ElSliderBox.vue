<script setup lang="ts">
import { computed } from 'vue'
import { ElSlider } from 'element-plus'

const props = defineProps<{
  value?: number
  modelValue?: number
  readOnly?: boolean
  disabled?: boolean
  min?: number
  max?: number
  step?: number
  showRange?: boolean
  tooltip?: { enabled?: boolean }
}>()

const emit = defineEmits<{
  'update:value': [val: number]
  'update:modelValue': [val: number]
  valueChanged: [e: { value: number }]
}>()

const inner = computed({
  get: () => props.modelValue ?? props.value ?? 0,
  set: (v) => {
    emit('update:value', v)
    emit('update:modelValue', v)
    emit('valueChanged', { value: v })
  },
})
</script>

<template>
  <ElSlider
    v-model="inner"
    :disabled="readOnly || disabled"
    :min="min ?? 0"
    :max="max ?? 100"
    :step="step ?? 1"
    :show-tooltip="tooltip?.enabled !== false"
  />
</template>