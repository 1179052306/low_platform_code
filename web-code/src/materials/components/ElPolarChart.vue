<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { RadarChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'

use([CanvasRenderer, RadarChart, TooltipComponent, LegendComponent, TitleComponent])

const props = defineProps<{
  dataSource?: any[]
  series?: any[]
  title?: any
  width?: number | string
  height?: number | string
}>()

const option = computed(() => ({
  title: props.title ? { text: typeof props.title === 'string' ? props.title : props.title.text } : undefined,
  tooltip: {},
  legend: {},
  radar: { indicator: (props.dataSource?.[0] ? Object.keys(props.dataSource[0]).filter((k) => k !== 'name').map((k) => ({ name: k, max: 100 })) : []) },
  series: [{
    type: 'radar',
    data: (props.dataSource || []).map((item: any) => ({ name: item.name, value: Object.keys(item).filter((k) => k !== 'name').map((k) => item[k]) })),
  }],
}))
const chartHeight = computed(() => props.height ? (typeof props.height === 'number' ? `${props.height}px` : props.height) : '300px')
</script>

<template>
  <VChart :option="option" autoresize :style="{ width: width ? (typeof width === 'number' ? `${width}px` : width) : '100%', height: chartHeight }" />
</template>