<script setup lang="ts">
import { computed } from 'vue'
import { ElSlider } from 'element-plus'

const props = defineProps<{
  value?: [number, number]
  modelValue?: [number, number]
  start?: number
  end?: number
  readOnly?: boolean
  disabled?: boolean
  min?: number
  max?: number
  step?: number
}>()

const emit = defineEmits<{
  'update:value': [val: [number, number]]
  'update:modelValue': [val: [number, number]]
  valueChanged: [e: { value: [number, number]; start: number; end: number }]
}>()

const inner = computed({
  get: () => props.modelValue ?? props.value ?? [props.start ?? props.min ?? 0, props.end ?? props.max ?? 100],
  set: (v) => {
    emit('update:value', v)
    emit('update:modelValue', v)
    emit('valueChanged', { value: v, start: v?.[0], end: v?.[1] })
  },
})
</script>

<template>
  <ElSlider
    v-model="inner"
    range
    :disabled="readOnly || disabled"
    :min="min ?? 0"
    :max="max ?? 100"
    :step="step ?? 1"
  />
</template>