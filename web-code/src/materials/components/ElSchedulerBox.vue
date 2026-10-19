<script setup lang="ts">
/**
 * ElSchedulerBox — 替换 DxScheduler
 * 用 FullCalendar 实现。dataSource 映射为 FullCalendar events。
 */
import { computed, ref, watch } from 'vue'
import FullCalendar from '@fullcalendar/vue3'
import type { EventClickArg, EventDropArg } from '@fullcalendar/core'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin from '@fullcalendar/interaction'
import { resolveDataSource } from '../dx-to-el'

const props = defineProps<{
  dataSource?: any
  items?: any[]
  startDateExpr?: string
  endDateExpr?: string
  textExpr?: string
  idExpr?: string
  currentView?: string
  currentDate?: string | Date
  readOnly?: boolean
  height?: number | string
  headerToolbar?: any
}>()

const emit = defineEmits<{
  appointmentChanged: [e: { event: any; item: any; revert: () => void }]
  appointmentClick: [e: { item: any; event: any }]
  valueChanged: [e: { value: any }]
}>()

const calRef = ref<InstanceType<typeof FullCalendar> | null>(null)
const events = ref<any[]>([])

function getStart(it: any): any { return it?.[props.startDateExpr ?? 'startDate'] ?? it?.start ?? it?.Start }
function getEnd(it: any): any { return it?.[props.endDateExpr ?? 'endDate'] ?? it?.end ?? it?.End }
function getTitle(it: any): any { return it?.[props.textExpr ?? 'text'] ?? it?.title ?? it?.Text ?? '' }
function getId(it: any): any { return it?.[props.idExpr ?? 'id'] ?? it?.ID ?? it?.id }

async function loadEvents() {
  const ds = props.dataSource ?? props.items
  if (!ds) { events.value = []; return }
  const data = await resolveDataSource(ds)
  events.value = data.map((it) => ({
    id: getId(it),
    start: getStart(it),
    end: getEnd(it),
    title: getTitle(it),
    extendedProps: { _raw: it },
  }))
}
watch(() => [props.dataSource, props.items], loadEvents, { immediate: true })

function viewMap(v?: string): string {
  switch (v) {
    case 'month': return 'dayGridMonth'
    case 'week': return 'timeGridWeek'
    case 'day': return 'timeGridDay'
    case 'agenda': return 'timeGridDay'
    case 'timelineDay': return 'timeGridDay'
    case 'timelineWeek': return 'timeGridWeek'
    default: return v || 'dayGridMonth'
  }
}

const heightOpt = computed(() => {
  const h = props.height
  if (h == null) return 600
  return typeof h === 'number' ? h : h
})

const calendarOptions = computed<any>(() => ({
  plugins: [dayGridPlugin, timeGridPlugin, interactionPlugin],
  initialView: viewMap(props.currentView),
  initialDate: props.currentDate as any,
  headerToolbar: props.headerToolbar ?? {
    left: 'prev,next today',
    center: 'title',
    right: 'dayGridMonth,timeGridWeek,timeGridDay',
  },
  height: heightOpt.value,
  editable: !props.readOnly,
  selectable: !props.readOnly,
  eventStartEditable: !props.readOnly,
  eventDurationEditable: !props.readOnly,
  events: events.value,
  eventClick: onEventClick,
  eventDrop: onEventDrop,
  eventResize: onEventResize,
}))

function onEventClick(arg: EventClickArg) {
  emit('appointmentClick', { item: (arg.event.extendedProps as any)?._raw, event: arg.event.toPlainObject() })
}
function onEventDrop(arg: EventDropArg) {
  emit('appointmentChanged', {
    event: arg.event.toPlainObject(),
    item: (arg.event.extendedProps as any)?._raw,
    revert: arg.revert,
  })
}
function onEventResize(arg: any) {
  emit('appointmentChanged', {
    event: arg.event.toPlainObject(),
    item: (arg.event.extendedProps as any)?._raw,
    revert: arg.revert,
  })
}
</script>

<template>
  <div class="el-scheduler-box">
    <FullCalendar ref="calRef" :options="calendarOptions" />
  </div>
</template>

<style scoped>
.el-scheduler-box {
  width: 100%;
  box-sizing: border-box;
}
</style>