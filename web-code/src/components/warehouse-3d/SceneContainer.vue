<template>
  <div class="warehouse-3d-container" ref="containerRef">
    <canvas ref="canvasRef" class="warehouse-3d-canvas" />
    <div class="warehouse-3d-overlay">
      <slot name="toolbar" />
      <slot name="legend" />
      <slot name="panel" />
    </div>
    <div v-if="!webglOk" class="warehouse-3d-fallback">
      <span>当前环境不支持 WebGL，无法显示 3D 视图</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { WarehouseRenderer } from './WarehouseRenderer'
import type { Warehouse, Park, ViewMode, LocationStatusUpdate } from '@/types/warehouse-3d'

const props = defineProps<{
  warehouse: Warehouse | null
  park: Park | null
  viewMode?: ViewMode
  editMode?: boolean
  showAreas?: boolean
  showChannels?: boolean
  showObstacles?: boolean
  showGrid?: boolean
  showLabels?: boolean
  floorOffsetY?: number
}>()

const emit = defineEmits<{
  locationClick: [locationId: string, code: string]
  warehouseClick: [warehouseId: string]
  fpsUpdate: [fps: number]
}>()

const containerRef = ref<HTMLDivElement>()
const canvasRef = ref<HTMLCanvasElement>()
const webglOk = ref(true)

let renderer: WarehouseRenderer | null = null
let resizeObserver: ResizeObserver | null = null

onMounted(() => {
  const canvas = canvasRef.value!
  try {
    const testCtx = canvas.getContext('webgl') || canvas.getContext('experimental-webgl')
    if (!testCtx) {
      webglOk.value = false
      return
    }
  } catch {
    webglOk.value = false
    return
  }

  renderer = new WarehouseRenderer({
    onLocationClick: (id, code) => emit('locationClick', id, code),
    onWarehouseClick: (id) => emit('warehouseClick', id),
    onFpsUpdate: (fps) => emit('fpsUpdate', fps),
  })
  renderer.init(canvas)

  if (props.park) {
    renderer.loadPark(props.park)
  } else if (props.warehouse) {
    renderer.loadLayout(props.warehouse, props.floorOffsetY || 0)
  }

  resizeObserver = new ResizeObserver(() => {
    renderer?.onResize()
  })
  resizeObserver.observe(containerRef.value!)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  renderer?.dispose()
  renderer = null
})

watch(() => props.park, (pk) => {
  if (pk && renderer) {
    renderer.loadPark(pk)
  }
})

watch(() => props.warehouse, (wh) => {
  if (wh && renderer) {
    renderer.loadLayout(wh, props.floorOffsetY || 0)
  }
})

watch(() => props.viewMode, (mode) => {
  if (mode && renderer) renderer.setViewMode(mode)
})

watch(() => props.editMode, (val) => {
  if (renderer) renderer.setEditMode(val || false)
})

watch(() => props.showAreas, (val) => {
  if (renderer) renderer.toggleAreaBoundaries(val !== false)
})

watch(() => props.showChannels, (val) => {
  if (renderer) renderer.toggleChannels(val !== false)
})

watch(() => props.showObstacles, (val) => {
  if (renderer) renderer.toggleObstacles(val !== false)
})

watch(() => props.showGrid, (val) => {
  if (renderer) renderer.toggleFloorGrid(val !== false)
})

watch(() => props.showLabels, (val) => {
  if (renderer) renderer.toggleLabels(val !== false)
})

defineExpose({
  updateLocationStatus(updates: LocationStatusUpdate[]) {
    renderer?.updateLocationStatus(updates)
  },
  fitToView() {
    renderer?.fitToView()
  },
})
</script>

<style scoped>
.warehouse-3d-container {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #1a1d24;
}
.warehouse-3d-canvas {
  display: block;
  width: 100%;
  height: 100%;
}
.warehouse-3d-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
}
.warehouse-3d-overlay :deep(*) {
  pointer-events: auto;
}
.warehouse-3d-fallback {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  color: #666;
  font-size: 14px;
}
</style>