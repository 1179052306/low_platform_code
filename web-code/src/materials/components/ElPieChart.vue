<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import { dxPieToEcharts } from '../dx-chart-to-echarts'

use([CanvasRenderer, PieChart, TooltipComponent, LegendComponent, TitleComponent])

const props = defineProps<{
  dataSource?: any[]
  type?: string
  title?: any
  legend?: any
  argumentField?: string
  valueField?: string
  width?: number | string
  height?: number | string
}>()

const option = computed(() => dxPieToEcharts(props))
const chartHeight = computed(() => props.height ? (typeof props.height === 'number' ? `${props.height}px` : props.height) : '300px')
</script>

<template>
  <VChart :option="option" autoresize :style="{ width: width ? (typeof width === 'number' ? `${width}px` : width) : '100%', height: chartHeight }" />
</template>