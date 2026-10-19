<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { TreemapChart } from 'echarts/charts'
import { TooltipComponent, TitleComponent } from 'echarts/components'
import { dxTreeMapToEcharts } from '../dx-chart-to-echarts'
use([CanvasRenderer, TreemapChart, TooltipComponent, TitleComponent])
const props = defineProps<{ dataSource?: any[]; title?: any; width?: number | string; height?: number | string }>()
const option = computed(() => dxTreeMapToEcharts(props))
const chartHeight = computed(() => props.height ? (typeof props.height === 'number' ? `${props.height}px` : props.height) : '300px')
</script>
<template><VChart :option="option" autoresize :style="{ width: width ? (typeof width === 'number' ? `${width}px` : width) : '100%', height: chartHeight }" /></template>