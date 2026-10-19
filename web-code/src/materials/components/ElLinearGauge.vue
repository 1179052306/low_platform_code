<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { GaugeChart } from 'echarts/charts'
import { TooltipComponent } from 'echarts/components'
import { dxGaugeToEcharts } from '../dx-chart-to-echarts'

use([CanvasRenderer, GaugeChart, TooltipComponent])

const props = defineProps<{ value?: number; min?: number; max?: number; width?: number | string; height?: number | string }>()
const option = computed(() => dxGaugeToEcharts(props))
const chartHeight = computed(() => props.height ? (typeof props.height === 'number' ? `${props.height}px` : props.height) : '300px')
</script>

<template>
  <VChart :option="option" autoresize :style="{ width: width ? (typeof width === 'number' ? `${width}px` : width) : '100%', height: chartHeight }" />
</template>