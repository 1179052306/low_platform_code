import * as THREE from 'three'
import type { Warehouse, WarehouseArea, Rack, Location } from '@/types/warehouse-3d'
import { STATUS_COLORS } from '@/utils/warehouse-3d/colorPalette'

/** 库位 instanceId → Location 映射（raycaster 拾取用） */
export interface LocationPickMap {
  instanceId: number
  location: Location
  rackId: string
  areaId: string
}

/** 文字标签 Sprite 生成 */
function makeLabel(text: string, color = '#ffffff'): THREE.Sprite {
  const canvas = document.createElement('canvas')
  canvas.width = 256
  canvas.height = 64
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = 'rgba(20,23,30,0.85)'
  ctx.fillRect(0, 0, 256, 64)
  ctx.strokeStyle = 'rgba(100,116,139,0.6)'
  ctx.lineWidth = 2
  ctx.strokeRect(1, 1, 254, 62)
  ctx.fillStyle = color
  ctx.font = 'bold 26px sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(text, 128, 32)
  const tex = new THREE.CanvasTexture(canvas)
  const mat = new THREE.SpriteMaterial({ map: tex, transparent: true })
  const sprite = new THREE.Sprite(mat)
  sprite.scale.set(4, 1, 1)
  return sprite
}

export class LayoutBuilder {
  group: THREE.Group

  locationMesh: THREE.InstancedMesh | null = null
  pickMap: LocationPickMap[] = []

  rackGroup: THREE.Group
  areaGroup: THREE.Group
  wallGroup: THREE.Group
  labelGroup: THREE.Group

  floorMesh: THREE.Mesh | null = null
  gridHelper: THREE.GridHelper | null = null

  private dummy = new THREE.Object3D()
  private color = new THREE.Color()

  constructor() {
    this.group = new THREE.Group()
    this.group.name = 'WarehouseLayout'
    this.rackGroup = new THREE.Group()
    this.rackGroup.name = 'Racks'
    this.areaGroup = new THREE.Group()
    this.areaGroup.name = 'Areas'
    this.wallGroup = new THREE.Group()
    this.wallGroup.name = 'Walls'
    this.labelGroup = new THREE.Group()
    this.labelGroup.name = 'Labels'
  }

  build(warehouse: Warehouse, floorOffsetY = 0): void {
    this.dispose()
    this.pickMap = []

    this.buildFloor(warehouse, floorOffsetY)
    this.buildWalls(warehouse, floorOffsetY)
    this.buildAreas(warehouse, floorOffsetY)
    this.buildRacks(warehouse, floorOffsetY)
    this.buildLocations(warehouse, floorOffsetY)

    this.group.add(this.floorMesh!)
    if (this.gridHelper) this.group.add(this.gridHelper)
    this.group.add(this.wallGroup)
    this.group.add(this.areaGroup)
    this.group.add(this.rackGroup)
    this.group.add(this.labelGroup)
    if (this.locationMesh) this.group.add(this.locationMesh)
  }

  private buildFloor(warehouse: Warehouse, offsetY: number): void {
    const geo = new THREE.PlaneGeometry(warehouse.length, warehouse.width)
    const mat = new THREE.MeshStandardMaterial({
      color: 0x2a2d34,
      roughness: 0.85,
      metalness: 0.1,
    })
    this.floorMesh = new THREE.Mesh(geo, mat)
    this.floorMesh.rotation.x = -Math.PI / 2
    this.floorMesh.position.set(warehouse.length / 2, offsetY, warehouse.width / 2)
    this.floorMesh.name = 'Floor'
    this.floorMesh.receiveShadow = true

    const gridSize = Math.max(warehouse.length, warehouse.width)
    this.gridHelper = new THREE.GridHelper(gridSize, gridSize, 0x4a5060, 0x3a3d44)
    this.gridHelper.position.set(warehouse.length / 2, offsetY + 0.02, warehouse.width / 2)
    this.gridHelper.name = 'Grid'
  }

  /** 仓库外墙（线框 + 半透明面） */
  private buildWalls(warehouse: Warehouse, offsetY: number): void {
    const h = warehouse.height
    const l = warehouse.length
    const w = warehouse.width

    const wallMat = new THREE.MeshStandardMaterial({
      color: 0x3a4a5a,
      transparent: true,
      opacity: 0.12,
      roughness: 0.8,
      side: THREE.DoubleSide,
    })
    const edgeMat = new THREE.LineBasicMaterial({ color: 0x607d8b, transparent: true, opacity: 0.6 })

    const walls: [number, number, number, number][] = [
      [0, 0, l, 0],
      [l, 0, l, w],
      [l, w, 0, w],
      [0, w, 0, 0],
    ]
    for (const [x1, z1, x2, z2] of walls) {
      const pts = [
        new THREE.Vector3(x1, offsetY, z1),
        new THREE.Vector3(x2, offsetY, z2),
        new THREE.Vector3(x2, offsetY + h, z2),
        new THREE.Vector3(x1, offsetY + h, z1),
      ]
      const geo = new THREE.BufferGeometry().setFromPoints(pts)
      const indices = [0, 1, 2, 0, 2, 3]
      geo.setIndex(indices)
      geo.computeVertexNormals()
      const mesh = new THREE.Mesh(geo, wallMat)
      this.wallGroup.add(mesh)

      const edgeGeo = new THREE.EdgesGeometry(geo)
      const edge = new THREE.LineSegments(edgeGeo, edgeMat)
      this.wallGroup.add(edge)
    }
  }

  /** 库区边界 + 填充面 + 标签 */
  private buildAreas(warehouse: Warehouse, offsetY: number): void {
    const areaColors: Record<string, number> = {
      storage: 0x4a90d9,
      picking: 0x9c27b0,
      staging: 0x00bcd4,
      receiving: 0xff9800,
      shipping: 0x4caf50,
    }

    for (const area of warehouse.areas) {
      const color = areaColors[area.areaType] || 0x4a90d9

      const fillGeo = new THREE.PlaneGeometry(area.length, area.width)
      const fillMat = new THREE.MeshStandardMaterial({
        color,
        transparent: true,
        opacity: 0.06,
        roughness: 0.8,
      })
      const fill = new THREE.Mesh(fillGeo, fillMat)
      fill.rotation.x = -Math.PI / 2
      fill.position.set(area.x + area.length / 2, offsetY + 0.03, area.z + area.width / 2)
      fill.name = `Area-${area.code}`
      fill.userData.area = area
      this.areaGroup.add(fill)

      const box = new THREE.BoxGeometry(area.length, 0.02, area.width)
      const edges = new THREE.EdgesGeometry(box)
      const line = new THREE.LineSegments(
        edges,
        new THREE.LineBasicMaterial({ color, transparent: true, opacity: 0.9 }),
      )
      line.position.set(area.x + area.length / 2, offsetY + 0.04, area.z + area.width / 2)
      this.areaGroup.add(line)

      const label = makeLabel(`${area.code} ${area.name}`, '#e8edf2')
      label.position.set(area.x + area.length / 2, offsetY + 0.5, area.z + area.width / 2)
      this.labelGroup.add(label)
    }
  }

  /** 货架框架（立柱 + 横梁 + 层板） */
  private buildRacks(warehouse: Warehouse, offsetY: number): void {
    for (const area of warehouse.areas) {
      for (const rack of area.racks) {
        this.buildRackFrame(rack, offsetY)
      }
    }
  }

  private buildRackFrame(rack: Rack, offsetY: number): void {
    const rackH = rack.levels * 1.2
    const postMat = new THREE.MeshStandardMaterial({ color: 0x4a5060, roughness: 0.6, metalness: 0.3 })
    const beamMat = new THREE.MeshStandardMaterial({ color: 0x5a6270, roughness: 0.6, metalness: 0.3 })
    const boardMat = new THREE.MeshStandardMaterial({ color: 0x3a3d44, roughness: 0.8, metalness: 0.1 })

    // 4 根立柱
    const postGeo = new THREE.BoxGeometry(0.08, rackH, 0.08)
    const posts: [number, number][] = [
      [rack.x, rack.z],
      [rack.x + rack.length, rack.z],
      [rack.x, rack.z + rack.width],
      [rack.x + rack.length, rack.z + rack.width],
    ]
    for (const [px, pz] of posts) {
      const post = new THREE.Mesh(postGeo, postMat)
      post.position.set(px, offsetY + rackH / 2, pz)
      this.rackGroup.add(post)
    }

    // 每层横梁 + 层板
    for (let lv = 0; lv <= rack.levels; lv++) {
      const y = offsetY + lv * 1.2
      // 横梁（长边）
      const beamGeo = new THREE.BoxGeometry(rack.length, 0.05, 0.05)
      for (const pz of [rack.z, rack.z + rack.width]) {
        const beam = new THREE.Mesh(beamGeo, beamMat)
        beam.position.set(rack.x + rack.length / 2, y, pz)
        this.rackGroup.add(beam)
      }
      // 横梁（短边）
      const beamGeo2 = new THREE.BoxGeometry(0.05, 0.05, rack.width)
      for (const px of [rack.x, rack.x + rack.length]) {
        const beam = new THREE.Mesh(beamGeo2, beamMat)
        beam.position.set(px, y, rack.z + rack.width / 2)
        this.rackGroup.add(beam)
      }
    }

    // 层板
    for (let lv = 1; lv <= rack.levels; lv++) {
      const y = offsetY + (lv - 1) * 1.2 + 0.02
      const boardGeo = new THREE.BoxGeometry(rack.length - 0.1, 0.02, rack.width - 0.1)
      const board = new THREE.Mesh(boardGeo, boardMat)
      board.position.set(rack.x + rack.length / 2, y, rack.z + rack.width / 2)
      this.rackGroup.add(board)
    }

    const label = makeLabel(rack.code, '#a0b4c8')
    label.position.set(rack.x + rack.length / 2, offsetY + rackH + 0.3, rack.z + rack.width / 2)
    label.scale.set(2, 0.5, 1)
    this.labelGroup.add(label)
  }

  /** InstancedMesh 渲染所有库位 */
  private buildLocations(warehouse: Warehouse, offsetY: number): void {
    const allLocs: { loc: Location; areaId: string }[] = []
    for (const area of warehouse.areas) {
      for (const rack of area.racks) {
        for (const loc of rack.locations) {
          allLocs.push({ loc, areaId: area.id })
        }
      }
    }
    if (allLocs.length === 0) return

    const geo = new THREE.BoxGeometry(1.4, 0.85, 0.85)
    const mat = new THREE.MeshStandardMaterial({
      roughness: 0.5,
      metalness: 0.2,

    })

    this.locationMesh = new THREE.InstancedMesh(geo, mat, allLocs.length)
    this.locationMesh.name = 'Locations'
    this.locationMesh.instanceMatrix.setUsage(THREE.StaticDrawUsage)

    for (let i = 0; i < allLocs.length; i++) {
      const { loc, areaId } = allLocs[i]
      this.dummy.position.set(loc.x, offsetY + loc.y + 0.45, loc.z)
      this.dummy.rotation.set(0, 0, 0)
      this.dummy.scale.set(1, 1, 1)
      this.dummy.updateMatrix()
      this.locationMesh.setMatrixAt(i, this.dummy.matrix)

      const c = STATUS_COLORS[loc.status]
      this.color.setHex(c.hex)
      this.locationMesh.setColorAt(i, this.color)

      this.pickMap.push({ instanceId: i, location: loc, rackId: loc.rackId, areaId })
    }

    this.locationMesh.instanceMatrix.needsUpdate = true
    if (this.locationMesh.instanceColor) this.locationMesh.instanceColor.needsUpdate = true
  }

  updateLocationStatus(locationId: string, status: Location['status'], utilization: number): void {
    const entry = this.pickMap.find((p) => p.location.id === locationId)
    if (!entry || !this.locationMesh) return
    const c = STATUS_COLORS[status]
    this.color.setHex(c.hex)
    this.locationMesh.setColorAt(entry.instanceId, this.color)
    if (this.locationMesh.instanceColor) this.locationMesh.instanceColor.needsUpdate = true
    entry.location.status = status
    entry.location.utilization = utilization
  }

  highlightLocation(instanceId: number, highlighted: boolean): void {
    if (!this.locationMesh) return
    const entry = this.pickMap[instanceId]
    if (!entry) return
    if (highlighted) {
      this.color.setHex(0xFFD700)
    } else {
      const c = STATUS_COLORS[entry.location.status]
      this.color.setHex(c.hex)
    }
    this.locationMesh.setColorAt(instanceId, this.color)
    if (this.locationMesh.instanceColor) this.locationMesh.instanceColor.needsUpdate = true
  }

  getBoundingBox(warehouse: Warehouse): THREE.Box3 {
    const box = new THREE.Box3()
    box.setFromCenterAndSize(
      new THREE.Vector3(warehouse.length / 2, warehouse.height / 2, warehouse.width / 2),
      new THREE.Vector3(warehouse.length, warehouse.height, warehouse.width),
    )
    return box
  }

  toggleLabels(visible: boolean): void {
    this.labelGroup.visible = visible
  }

  dispose(): void {
    if (this.floorMesh) {
      this.floorMesh.geometry.dispose()
      ;(this.floorMesh.material as THREE.Material).dispose()
      this.floorMesh = null
    }
    if (this.gridHelper) {
      this.gridHelper.geometry.dispose()
      ;(this.gridHelper.material as THREE.Material).dispose()
      this.gridHelper = null
    }
    const disposeGroup = (g: THREE.Group) => {
      g.traverse((obj) => {
        if (obj instanceof THREE.Mesh) {
          obj.geometry?.dispose()
          const m = obj.material
          if (Array.isArray(m)) m.forEach((x) => x.dispose())
          else m?.dispose()
        } else if (obj instanceof THREE.LineSegments) {
          obj.geometry?.dispose()
          ;(obj.material as THREE.Material)?.dispose()
        } else if (obj instanceof THREE.Sprite) {
          const sm = obj.material as THREE.SpriteMaterial
          sm.map?.dispose()
          sm.dispose()
        }
      })
      g.clear()
    }
    disposeGroup(this.areaGroup)
    disposeGroup(this.rackGroup)
    disposeGroup(this.wallGroup)
    disposeGroup(this.labelGroup)
    if (this.locationMesh) {
      this.locationMesh.geometry.dispose()
      ;(this.locationMesh.material as THREE.Material).dispose()
      this.locationMesh = null
    }
    this.pickMap = []
  }
}