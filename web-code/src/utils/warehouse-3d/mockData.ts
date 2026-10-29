import type {
  Warehouse,
  WarehouseArea,
  Rack,
  Location,
  Obstacle,
  Channel,
  LocationStatus,
  WsMessage,
  LocationStatusUpdate,
  Park,
} from '@/types/warehouse-3d'

const STATUS_POOL: LocationStatus[] = [
  'empty', 'empty', 'empty', 'empty',
  'partial', 'partial', 'partial',
  'full', 'full',
  'locked',
  'reserved',
  'disabled',
]

function randomStatus(): LocationStatus {
  return STATUS_POOL[Math.floor(Math.random() * STATUS_POOL.length)]
}

function genId(prefix: string, n: number): string {
  return `${prefix}-${String(n).padStart(4, '0')}`
}

/** 生成 Mock 仓库布局 */
export function generateMockWarehouse(whId = 'mock-wh-001', whCode = 'WH-001', whName = '主仓库', whX = 0, whZ = 0): Warehouse {
  const warehouseId = whId
  const areas: WarehouseArea[] = []
  const obstacles: Obstacle[] = []
  const channels: Channel[] = []

  const floorCount = 2
  const floorHeight = 6
  const areaCount = 4
  const rackPerArea = 5
  const levels = 3
  const baysPerLevel = 10

  const areaLength = 20
  const areaWidth = 14
  const gap = 4

  for (let f = 1; f <= floorCount; f++) {
    for (let a = 0; a < areaCount; a++) {
      const areaId = genId('area', f * 100 + a + 1)
      const areaX = 2 + a * (areaLength + gap)
      const areaZ = 2
      const racks: Rack[] = []

      for (let r = 0; r < rackPerArea; r++) {
        const rackId = genId('rack', f * 1000 + a * rackPerArea + r + 1)
        const rackX = areaX + 1
        const rackZ = areaZ + r * (2.5 + 0.5)
        const locations: Location[] = []

        for (let lv = 1; lv <= levels; lv++) {
          for (let bay = 1; bay <= baysPerLevel; bay++) {
            const locId = genId('loc', f * 100000 + a * 5000 + r * 200 + lv * 20 + bay)
            const status = randomStatus()
            const utilization = status === 'empty' ? 0 : status === 'full' ? 100 : Math.floor(Math.random() * 80) + 10
            locations.push({
              id: locId,
              code: `F${f}-${String.fromCharCode(65 + a)}-${String(r + 1).padStart(2, '0')}-${String(lv).padStart(2, '0')}-${String(bay).padStart(2, '0')}`,
              rackId,
              level: lv,
              bay,
              x: rackX + (bay - 1) * 1.8,
              y: (lv - 1) * 1.2,
              z: rackZ,
              sizeX: 1.6,
              sizeY: 1.0,
              sizeZ: 1.0,
              status,
              utilization,
              productCode: status === 'empty' ? undefined : `SKU-${Math.floor(Math.random() * 9000) + 1000}`,
            })
          }
        }

        racks.push({
          id: rackId,
          code: `F${f}-R-${String.fromCharCode(65 + a)}${String(r + 1).padStart(2, '0')}`,
          x: rackX,
          z: rackZ,
          length: baysPerLevel * 1.8,
          width: 1.0,
          levels,
          baysPerLevel,
          orientation: 'x',
          locations,
        })
      }

      areas.push({
        id: areaId,
        code: `F${f}-${String.fromCharCode(65 + a)}`,
        name: `${f}F-${String.fromCharCode(65 + a)}区`,
        areaType: a === 0 ? 'receiving' : a === areaCount - 1 ? 'shipping' : 'storage',
        x: areaX,
        z: areaZ,
        length: areaLength,
        width: areaWidth,
        floor: f,
        racks,
      })
    }
  }

  obstacles.push(
    { id: genId('obs', 1), obstacleType: 'pillar', x: 50, z: 30, length: 1, width: 1, height: 8, rotation: 0 },
    { id: genId('obs', 2), obstacleType: 'pillar', x: 30, z: 30, length: 1, width: 1, height: 8, rotation: 0 },
    { id: genId('obs', 3), obstacleType: 'wall', x: 0, z: 0, length: 2, width: 60, height: 3, rotation: 0 },
  )

  channels.push(
    { id: genId('ch', 1), channelType: 'main', path: [{ x: 0, z: 0 }, { x: 100, z: 0 }], width: 3, bidirectional: true },
    { id: genId('ch', 2), channelType: 'main', path: [{ x: 0, z: 30 }, { x: 100, z: 30 }], width: 3, bidirectional: true },
  )

  return {
    id: warehouseId,
    code: whCode,
    name: whName,
    length: 100,
    width: 60,
    height: floorHeight,
    floorCount,
    floorHeight,
    x: whX,
    z: whZ,
    areas,
    obstacles,
    channels,
  }
}

/** 生成 Mock 园区（含多个仓库） */
export function generateMockPark(): Park {
  const wh1 = generateMockWarehouse('mock-wh-001', 'WH-001', '1号仓库', 10, 10)
  const wh2 = generateMockWarehouse('mock-wh-002', 'WH-002', '2号仓库', 10, 90)
  const wh3 = generateMockWarehouse('mock-wh-003', 'WH-003', '3号仓库', 130, 10)

  return {
    id: 'mock-park-001',
    code: 'PK-001',
    name: '物流园区',
    length: 240,
    width: 180,
    warehouses: [wh1, wh2, wh3],
  }
}

/** 按楼层过滤仓库布局 */
export function filterByFloor(wh: Warehouse, floor: number): Warehouse {
  return {
    ...wh,
    areas: wh.areas.filter((a) => a.floor === floor),
  }
}

/** 统计 Mock 库位总数 */
export function countLocations(wh: Warehouse): number {
  let n = 0
  for (const area of wh.areas) {
    for (const rack of area.racks) {
      n += rack.locations.length
    }
  }
  return n
}

/** Mock WebSocket：定时随机更新库位状态 */
export function startMockWebSocket(
  warehouse: Warehouse,
  onUpdate: (msg: WsMessage) => void,
): () => void {
  const allLocs: Location[] = []
  for (const area of warehouse.areas) {
    for (const rack of area.racks) {
      allLocs.push(...rack.locations)
    }
  }

  const timer = setInterval(() => {
    const count = 3 + Math.floor(Math.random() * 5)
    const updates: LocationStatusUpdate[] = []
    for (let i = 0; i < count; i++) {
      const loc = allLocs[Math.floor(Math.random() * allLocs.length)]
      const status = randomStatus()
      loc.status = status
      loc.utilization = status === 'empty' ? 0 : status === 'full' ? 100 : Math.floor(Math.random() * 80) + 10
      updates.push({
        locationId: loc.id,
        status,
        utilization: loc.utilization,
        productCode: loc.productCode,
        updateTime: new Date().toISOString(),
      })
    }
    onUpdate({
      type: 'locationStatus',
      warehouseId: warehouse.id,
      payload: updates,
    })
  }, 5000)

  return () => clearInterval(timer)
}