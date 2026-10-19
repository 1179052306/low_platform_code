<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { FunnelChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import { dxFunnelToEcharts } from '../dx-chart-to-echarts'
use([CanvasRenderer, FunnelChart, TooltipComponent, LegendComponent, TitleComponent])
const props = defineProps<{ dataSource?: any[]; argumentField?: string; valueField?: string; title?: any; width?: number | string; height?: number | string }>()
const option = computed(() => dxFunnelToEcharts(props))
const chartHeight = computed(() => props.height ? (typeof props.height === 'number' ? `${props.height}px` : props.height) : '300px')
</script>
<template><VChart :option="option" autoresize :style="{ width: width ? (typeof width === 'number' ? `${width}px` : width) : '100%', height: chartHeight }" /></template>