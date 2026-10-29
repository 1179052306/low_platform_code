import { ref } from 'vue'
import type { Warehouse, Park, ViewMode, LocationStatusUpdate, WarehouseArea, Rack, Location, AreaType } from '@/types/warehouse-3d'
import { generateMockPark, generateMockWarehouse, countLocations, startMockWebSocket, filterByFloor } from '@/utils/warehouse-3d/mockData'

export type ViewLevel = 'park' | 'warehouse'

export const park = ref<Park | null>(null)
export const warehouse = ref<Warehouse | null>(null)
export const viewLevel = ref<ViewLevel>('park')
export const viewMode = ref<ViewMode>('perspective')
export const editMode = ref(false)
export const showAreas = ref(true)
export const showChannels = ref(true)
export const showObstacles = ref(true)
export const showGrid = ref(true)
export const showLabels = ref(true)
export const fps = ref(0)
export const locationCount = ref(0)
export const selectedInfo = ref<{ id: string; code: string } | null>(null)

export const currentFloor = ref(1)
export const floorCount = ref(1)

let mockWsStop: (() => void) | null = null
let fullWarehouse: Warehouse | null = null

function genId(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`
}

function refreshFloorView(): void {
  if (!fullWarehouse) return
  const filtered = filterByFloor(fullWarehouse, currentFloor.value)
  warehouse.value = filtered
  locationCount.value = countLocations(filtered)
}

export function loadMockData(): void {
  const p = generateMockPark()
  park.value = p
  viewLevel.value = 'park'
  fullWarehouse = null
  warehouse.value = null
}

export function enterWarehouse(whId: string): void {
  if (!park.value) return
  const wh = park.value.warehouses.find((w) => w.id === whId)
  if (!wh) return

  const whCopy: Warehouse = JSON.parse(JSON.stringify(wh))
  fullWarehouse = whCopy
  floorCount.value = whCopy.floorCount
  currentFloor.value = 1
  viewLevel.value = 'warehouse'
  editMode.value = false
  refreshFloorView()

  if (mockWsStop) mockWsStop()
  mockWsStop = startMockWebSocket(whCopy, (msg) => {
    if (msg.type === 'locationStatus') {
      wsLocationUpdates.value = msg.payload as LocationStatusUpdate[]
    }
  })
}

export function exitToPark(): void {
  if (mockWsStop) {
    mockWsStop()
    mockWsStop = null
  }
  viewLevel.value = 'park'
  editMode.value = false
  warehouse.value = null
  fullWarehouse = null
}

export const wsLocationUpdates = ref<LocationStatusUpdate[]>([])

export function setViewMode(mode: ViewMode): void {
  viewMode.value = mode
}

export function toggleEditMode(): void {
  editMode.value = !editMode.value
}

export function setSelectedLocation(id: string, code: string): void {
  selectedInfo.value = { id, code }
}

export function setFloor(floor: number): void {
  if (floor < 1 || floor > floorCount.value) return
  currentFloor.value = floor
  refreshFloorView()
}

export function stopMockWs(): void {
  if (mockWsStop) {
    mockWsStop()
    mockWsStop = null
  }
}

/** 新增库区 */
export function addArea(params: {
  code: string
  name: string
  areaType: AreaType
  x: number
  z: number
  length: number
  width: number
}): WarehouseArea | null {
  if (!fullWarehouse) return null
  const area: WarehouseArea = {
    id: genId('area'),
    code: params.code,
    name: params.name,
    areaType: params.areaType,
    x: params.x,
    z: params.z,
    length: params.length,
    width: params.width,
    floor: currentFloor.value,
    racks: [],
  }
  fullWarehouse.areas.push(area)
  refreshFloorView()
  return area
}

/** 新增货架 */
export function addRack(params: {
  areaId: string
  code: string
  x: number
  z: number
  length: number
  width: number
  levels: number
  baysPerLevel: number
}): Rack | null {
  if (!fullWarehouse) return null
  const area = fullWarehouse.areas.find((a) => a.id === params.areaId)
  if (!area) return null
  const rack: Rack = {
    id: genId('rack'),
    code: params.code,
    x: params.x,
    z: params.z,
    length: params.length,
    width: params.width,
    levels: params.levels,
    baysPerLevel: params.baysPerLevel,
    orientation: 'x',
    locations: [],
  }
  area.racks.push(rack)
  refreshFloorView()
  return rack
}

/** 批量生成库位 */
export function generateLocations(rackId: string): number {
  if (!fullWarehouse) return 0
  let rack: Rack | null = null
  for (const area of fullWarehouse.areas) {
    const r = area.racks.find((r) => r.id === rackId)
    if (r) { rack = r; break }
  }
  if (!rack) return 0

  rack.locations = []
  for (let lv = 1; lv <= rack.levels; lv++) {
    for (let bay = 1; bay <= rack.baysPerLevel; bay++) {
      rack.locations.push({
        id: genId('loc'),
        code: `${rack.code}-${String(lv).padStart(2, '0')}-${String(bay).padStart(2, '0')}`,
        rackId: rack.id,
        level: lv,
        bay,
        x: rack.x + (bay - 1) * 1.8,
        y: (lv - 1) * 1.2,
        z: rack.z,
        sizeX: 1.6,
        sizeY: 1.0,
        sizeZ: 1.0,
        status: 'empty',
        utilization: 0,
      })
    }
  }
  refreshFloorView()
  return rack.locations.length
}

/** 获取当前楼层的库区列表 */
export function getCurrentFloorAreas(): WarehouseArea[] {
  if (!fullWarehouse) return []
  return fullWarehouse.areas.filter((a) => a.floor === currentFloor.value)
}

/** 获取当前仓库信息 */
export function getCurrentWarehouse(): Warehouse | null {
  return fullWarehouse
}