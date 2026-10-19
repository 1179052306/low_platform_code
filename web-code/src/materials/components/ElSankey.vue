<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { SankeyChart } from 'echarts/charts'
import { TooltipComponent, TitleComponent } from 'echarts/components'
import { dxSankeyToEcharts } from '../dx-chart-to-echarts'
use([CanvasRenderer, SankeyChart, TooltipComponent, TitleComponent])
const props = defineProps<{ nodes?: any[]; links?: any[]; title?: any; width?: number | string; height?: number | string }>()
const option = computed(() => dxSankeyToEcharts(props))
const chartHeight = computed(() => props.height ? (typeof props.height === 'number' ? `${props.height}px` : props.height) : '300px')
</script>
<template><VChart :option="option" autoresize :style="{ width: width ? (typeof width === 'number' ? `${width}px` : width) : '100%', height: chartHeight }" /></template>