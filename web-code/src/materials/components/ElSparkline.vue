<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent } from 'echarts/components'
use([CanvasRenderer, LineChart, GridComponent])
const props = defineProps<{ dataSource?: any[]; valueField?: string; argumentField?: string; type?: string; width?: number | string; height?: number | string }>()
const vf = computed(() => props.valueField || 'val')
const af = computed(() => props.argumentField || 'arg')
const option = computed(() => ({
  grid: { left: 0, right: 0, top: 0, bottom: 0 },
  xAxis: { type: 'category', show: false, data: (props.dataSource || []).map((i: any) => i[af.value]) },
  yAxis: { type: 'value', show: false },
  series: [{ type: props.type === 'winloss' ? 'bar' : 'line', data: (props.dataSource || []).map((i: any) => i[vf.value]), symbol: 'none', lineStyle: { width: 1.5 } }],
}))
const chartHeight = computed(() => props.height ? (typeof props.height === 'number' ? `${props.height}px` : props.height) : '30px')
</script>
<template><VChart :option="option" autoresize :style="{ width: width ? (typeof width === 'number' ? `${width}px` : width) : '100%', height: chartHeight }" /></template>