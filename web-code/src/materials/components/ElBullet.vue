<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
use([CanvasRenderer, BarChart, GridComponent, TooltipComponent])
const props = defineProps<{ value?: number; target?: number; min?: number; max?: number; width?: number | string; height?: number | string }>()
const option = computed(() => ({
  tooltip: { trigger: 'axis' },
  xAxis: { type: 'category', data: [''] },
  yAxis: { type: 'value', min: props.min ?? 0, max: props.max ?? 100 },
  series: [
    { type: 'bar', data: [props.value ?? 0], itemStyle: { color: '#5470c6' } },
    { type: 'line', data: [props.target ?? 0], markLine: { data: [{ yAxis: props.target ?? 0 }] } },
  ],
}))
const chartHeight = computed(() => props.height ? (typeof props.height === 'number' ? `${props.height}px` : props.height) : '80px')
</script>
<template><VChart :option="option" autoresize :style="{ width: width ? (typeof width === 'number' ? `${width}px` : width) : '100%', height: chartHeight }" /></template>