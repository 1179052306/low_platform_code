<script setup lang="ts">
import { computed } from 'vue'
import { ElProgress } from 'element-plus'

const props = defineProps<{
  value?: number
  modelValue?: number
  min?: number
  max?: number
  width?: number | string
  height?: number | string
  showStatus?: boolean
  statusFormat?: string
}>()

const percentage = computed(() => {
  const v = props.modelValue ?? props.value ?? 0
  const min = props.min ?? 0
  const max = props.max ?? 100
  if (max === min) return 0
  return Math.round(((v - min) / (max - min)) * 100)
})
</script>

<template>
  <ElProgress
    :percentage="percentage"
    :show-text="showStatus !== false"
    :stroke-width="height ? (typeof height === 'number' ? height : 8) : 8"
    :style="{ width: width ? (typeof width === 'number' ? `${width}px` : width) : '100%' }"
  />
</template>