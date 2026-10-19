<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, ScatterChart, PieChart, RadarChart, FunnelChart, SankeyChart, TreemapChart, GaugeChart, HeatmapChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent, DataZoomComponent, VisualMapComponent } from 'echarts/components'
import { dxChartToEcharts } from '../dx-chart-to-echarts'

use([CanvasRenderer, BarChart, LineChart, ScatterChart, PieChart, RadarChart, FunnelChart, SankeyChart, TreemapChart, GaugeChart, HeatmapChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, DataZoomComponent, VisualMapComponent])

const props = defineProps<{
  dataSource?: any[]
  series?: any[]
  type?: string
  title?: any
  legend?: any
  tooltip?: any
  argumentField?: string
  valueField?: string
  argumentAxis?: any
  valueAxis?: any
  width?: number | string
  height?: number | string
}>()

const option = computed(() => dxChartToEcharts(props))
const chartHeight = computed(() => props.height ? (typeof props.height === 'number' ? `${props.height}px` : props.height) : '300px')
</script>

<template>
  <VChart :option="option" autoresize :style="{ width: width ? (typeof width === 'number' ? `${width}px` : width) : '100%', height: chartHeight }" />
</template>