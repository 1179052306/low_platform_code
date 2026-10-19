<script setup lang="ts">
/**
 * ElGanttBox — 替换 DxGantt
 * 用 dhtmlx-gantt 实现。onMounted 初始化，onBeforeUnmount 清理。
 */
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { gantt } from 'dhtmlx-gantt'
import 'dhtmlx-gantt/codebase/dhtmlxgantt.css'

interface GanttTask {
  id: string | number
  text?: string
  start_date?: string | Date
  end_date?: string | Date
  duration?: number
  progress?: number
  parent?: string | number
  [key: string]: any
}
interface GanttLink {
  id?: string | number
  source: string | number
  target: string | number
  type?: string
}
interface GanttColumn {
  field?: string
  caption?: string
  width?: number
  type?: string
}

const props = defineProps<{
  tasks?: GanttTask[]
  links?: GanttLink[]
  columns?: GanttColumn[]
  readOnly?: boolean
  height?: number | string
  width?: number | string
  scale?: 'day' | 'week' | 'month'
}>()

const emit = defineEmits<{
  taskClick: [e: { id: string | number; task: GanttTask }]
  taskDblClick: [e: { id: string | number; task: GanttTask }]
  valueChanged: [e: { value: any }]
}>()

const container = ref<HTMLDivElement | null>(null)
let clickEventId: string | null = null
let dblClickEventId: string | null = null

function buildColumns() {
  if (!props.columns?.length) {
    return [
      { name: 'text', label: '任务名称', tree: true, width: 200 },
      { name: 'start_date', label: '开始时间', width: 120 },
      { name: 'duration', label: '工期', width: 60 },
    ]
  }
  return props.columns.map((c, i) => ({
    name: c.field ?? `col${i}`,
    label: c.caption ?? c.field ?? '',
    width: c.width ?? 120,
    type: c.type,
    tree: i === 0,
  }))
}

function applyConfig() {
  gantt.config.columns = buildColumns() as any
  gantt.config.readonly = !!props.readOnly
  gantt.config.row_height = 36
  gantt.config.date_format = '%Y-%m-%d'
  if (props.scale === 'week') {
    gantt.config.scales = [{ unit: 'week', step: 1, format: '%W 周' }, { unit: 'day', step: 1, format: '%j' }]
  } else if (props.scale === 'month') {
    gantt.config.scales = [{ unit: 'month', step: 1, format: '%Y-%m' }, { unit: 'week', step: 1, format: '%W' }]
  } else {
    gantt.config.scales = [{ unit: 'day', step: 1, format: '%Y-%m-%d' }]
  }
}

function parseData() {
  gantt.clearAll()
  gantt.parse({ data: (props.tasks ?? []) as any[], links: (props.links ?? []) as any[] })
}

onMounted(() => {
  if (!container.value) return
  applyConfig()
  gantt.init(container.value)
  parseData()
  clickEventId = gantt.attachEvent('onTaskClick', (id: string | number) => {
    const task = gantt.getTask(id)
    emit('taskClick', { id, task })
    return true
  })
  dblClickEventId = gantt.attachEvent('onTaskDblClick', (id: string | number) => {
    const task = gantt.getTask(id)
    emit('taskDblClick', { id, task })
    return true
  })
})

onBeforeUnmount(() => {
  if (clickEventId) gantt.detachEvent(clickEventId)
  if (dblClickEventId) gantt.detachEvent(dblClickEventId)
  gantt.clearAll()
})

watch(() => [props.tasks, props.links], () => {
  if (container.value) parseData()
})
watch(() => [props.columns, props.readOnly, props.scale], () => {
  if (container.value) {
    applyConfig()
    gantt.render()
  }
})

const heightStyle = ref('')
const widthStyle = ref('')
function updateStyle() {
  const h = props.height
  heightStyle.value = h == null ? '500px' : (typeof h === 'number' ? `${h}px` : h)
  const w = props.width
  widthStyle.value = w == null ? '100%' : (typeof w === 'number' ? `${w}px` : w)
}
updateStyle()
watch(() => [props.height, props.width], updateStyle)
</script>

<template>
  <div
    ref="container"
    class="el-gantt-box"
    :style="{ height: heightStyle, width: widthStyle }"
  />
</template>

<style scoped>
.el-gantt-box {
  box-sizing: border-box;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  overflow: hidden;
}
</style>