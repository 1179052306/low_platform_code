<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { GridComponent, DataZoomComponent, TooltipComponent } from 'echarts/components'
use([CanvasRenderer, LineChart, GridComponent, DataZoomComponent, TooltipComponent])
const props = defineProps<{ dataSource?: any[]; valueField?: string; argumentField?: string; width?: number | string; height?: number | string }>()
const vf = computed(() => props.valueField || 'val')
const af = computed(() => props.argumentField || 'arg')
const option = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 20, top: 20, bottom: 60 },
  xAxis: { type: 'category', data: (props.dataSource || []).map((i: any) => i[af.value]) },
  yAxis: { type: 'value' },
  dataZoom: [{ type: 'slider', bottom: 10 }],
  series: [{ type: 'line', data: (props.dataSource || []).map((i: any) => i[vf.value]) }],
}))
const chartHeight = computed(() => props.height ? (typeof props.height === 'number' ? `${props.height}px` : props.height) : '120px')
const emit = defineEmits<{ 'update:value': [val: any]; valueChanged: [e: { value: any }] }>()
</script>
<template>
  <VChart :option="option" autoresize :style="{ width: width ? (typeof width === 'number' ? `${width}px` : width) : '100%', height: chartHeight }" @datazoom="(e: any) => { emit('update:value', e.batch?.[0]?.startValue ?? e.startValue); emit('valueChanged', { value: e.batch?.[0]?.startValue ?? e.startValue }) }" />
</template>