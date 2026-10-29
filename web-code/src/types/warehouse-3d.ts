/** 库位状态 */
export type LocationStatus =
  | 'empty'
  | 'partial'
  | 'full'
  | 'locked'
  | 'reserved'
  | 'disabled'
  | 'error'

/** 视角模式 */
export type ViewMode = 'perspective' | 'top' | 'side' | 'front'

/** 货架朝向 */
export type RackOrientation = 'x' | 'z'

/** 库区类型 */
export type AreaType = 'storage' | 'picking' | 'staging' | 'receiving' | 'shipping'

/** 障碍物类型 */
export type ObstacleType = 'pillar' | 'wall' | 'door' | 'column'

/** 通道类型 */
export type ChannelType = 'main' | 'sub' | 'fire'

/** 路径算法 */
export type PathAlgorithm = 'nearest' | 's-shape' | 'optimal'

/** 库位推荐策略 */
export type RecommendCriteria = 'distance' | 'utilization' | 'category'

/** 2D 坐标点（XZ 平面） */
export interface Point2D {
  x: number
  z: number
}

/** 3D 坐标点 */
export interface Point3D {
  x: number
  y: number
  z: number
}

/** 库位 */
export interface Location {
  id: string
  code: string
  rackId: string
  level: number
  bay: number
  x: number
  y: number
  z: number
  sizeX: number
  sizeY: number
  sizeZ: number
  status: LocationStatus
  utilization: number
  productCode?: string
  updateTime?: string
}

/** 货架 */
export interface Rack {
  id: string
  code: string
  x: number
  z: number
  length: number
  width: number
  levels: number
  baysPerLevel: number
  orientation: RackOrientation
  locations: Location[]
}

/** 库区 */
export interface WarehouseArea {
  id: string
  code: string
  name: string
  areaType: AreaType
  x: number
  z: number
  length: number
  width: number
  floor: number
  racks: Rack[]
}

/** 静态障碍物 */
export interface Obstacle {
  id: string
  obstacleType: ObstacleType
  x: number
  z: number
  length: number
  width: number
  height: number
  rotation: number
}

/** 通道 */
export interface Channel {
  id: string
  channelType: ChannelType
  path: Point2D[]
  width: number
  bidirectional: boolean
}

/** 仓库 */
export interface Warehouse {
  id: string
  code: string
  name: string
  length: number
  width: number
  height: number
  floorCount: number
  floorHeight: number
  x: number
  z: number
  areas: WarehouseArea[]
  obstacles: Obstacle[]
  channels: Channel[]
}

/** 园区 */
export interface Park {
  id: string
  code: string
  name: string
  length: number
  width: number
  warehouses: Warehouse[]
}

/** 拣货路径 */
export interface PickingPath {
  id: string
  startPoint: Point2D
  endPoint: Point2D
  waypoints: (Point2D & { locationId?: string })[]
  totalDistance: number
  algorithm: PathAlgorithm
}

/** 版本快照 */
export interface LayoutSnapshot {
  id: string
  version: number
  warehouseId: string
  snapshotTime: string
  operator: string
  description: string
  data: Warehouse
  changeLog?: string
}

/** 编辑锁 */
export interface EditLock {
  warehouseId: string
  lockedBy: string
  lockedByName: string
  lockedAt: string
  expiresAt: string
}

/** WebSocket 消息 */
export interface WsMessage {
  type: 'locationStatus' | 'lockChanged' | 'layoutUpdated' | 'snapshotCreated'
  warehouseId: string
  payload: unknown
}

/** 库位状态更新推送 */
export interface LocationStatusUpdate {
  locationId: string
  status: LocationStatus
  utilization: number
  productCode?: string
  updateTime: string
}

/** 库位推荐结果 */
export interface LocationRecommend {
  locationId: string
  code: string
  score: number
  reason: string
}