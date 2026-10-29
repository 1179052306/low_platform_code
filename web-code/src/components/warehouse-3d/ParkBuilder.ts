import * as THREE from 'three'
import type { Park, Warehouse } from '@/types/warehouse-3d'

function makeLabel(text: string, color = '#ffffff', sub = ''): THREE.Sprite {
  const canvas = document.createElement('canvas')
  canvas.width = 256
  canvas.height = 80
  const ctx = canvas.getContext('2d')!
  ctx.fillStyle = 'rgba(20,23,30,0.9)'
  ctx.fillRect(0, 0, 256, 80)
  ctx.strokeStyle = 'rgba(100,116,139,0.5)'
  ctx.lineWidth = 2
  ctx.strokeRect(1, 1, 254, 78)
  ctx.fillStyle = color
  ctx.font = 'bold 28px sans-serif'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'middle'
  ctx.fillText(text, 128, sub ? 28 : 40)
  if (sub) {
    ctx.fillStyle = '#9099a8'
    ctx.font = '18px sans-serif'
    ctx.fillText(sub, 128, 56)
  }
  const tex = new THREE.CanvasTexture(canvas)
  const mat = new THREE.SpriteMaterial({ map: tex, transparent: true })
  const sprite = new THREE.Sprite(mat)
  sprite.scale.set(6, 1.875, 1)
  return sprite
}

export class ParkBuilder {
  group: THREE.Group
  buildingGroup: THREE.Group
  labelGroup: THREE.Group
  roadGroup: THREE.Group

  floorMesh: THREE.Mesh | null = null
  gridHelper: THREE.GridHelper | null = null

  buildingMeshes: THREE.Mesh[] = []
  warehouseMap: Map<number, Warehouse> = new Map()

  constructor() {
    this.group = new THREE.Group()
    this.group.name = 'ParkLayout'
    this.buildingGroup = new THREE.Group()
    this.buildingGroup.name = 'Buildings'
    this.labelGroup = new THREE.Group()
    this.labelGroup.name = 'Labels'
    this.roadGroup = new THREE.Group()
    this.roadGroup.name = 'Roads'
  }

  build(park: Park): void {
    this.dispose()
    this.buildingMeshes = []
    this.warehouseMap.clear()

    this.buildFloor(park)
    this.buildRoads(park)
    this.buildBuildings(park)

    this.group.add(this.floorMesh!)
    if (this.gridHelper) this.group.add(this.gridHelper)
    this.group.add(this.roadGroup)
    this.group.add(this.buildingGroup)
    this.group.add(this.labelGroup)
  }

  private buildFloor(park: Park): void {
    const geo = new THREE.PlaneGeometry(park.length, park.width)
    const mat = new THREE.MeshStandardMaterial({
      color: 0x1e2228,
      roughness: 0.9,
      metalness: 0.05,
    })
    this.floorMesh = new THREE.Mesh(geo, mat)
    this.floorMesh.rotation.x = -Math.PI / 2
    this.floorMesh.position.set(park.length / 2, 0, park.width / 2)
    this.floorMesh.name = 'ParkFloor'

    const gridSize = Math.max(park.length, park.width)
    this.gridHelper = new THREE.GridHelper(gridSize, gridSize / 2, 0x3a4050, 0x2a2d34)
    this.gridHelper.position.set(park.length / 2, 0.02, park.width / 2)
    this.gridHelper.name = 'ParkGrid'
  }

  private buildRoads(park: Park): void {
    const roadMat = new THREE.MeshStandardMaterial({
      color: 0x353840,
      roughness: 0.95,
      metalness: 0.0,
    })

    const hRoadGeo = new THREE.PlaneGeometry(park.length, 8)
    const hRoad = new THREE.Mesh(hRoadGeo, roadMat)
    hRoad.rotation.x = -Math.PI / 2
    hRoad.position.set(park.length / 2, 0.03, park.width / 2)
    this.roadGroup.add(hRoad)

    const vRoadGeo = new THREE.PlaneGeometry(8, park.width)
    const vRoad = new THREE.Mesh(vRoadGeo, roadMat)
    vRoad.rotation.x = -Math.PI / 2
    vRoad.position.set(park.length / 2, 0.03, park.width / 2)
    this.roadGroup.add(vRoad)
  }

  private buildBuildings(park: Park): void {
    const buildingMat = new THREE.MeshStandardMaterial({
      color: 0x4a5a6a,
      roughness: 0.7,
      metalness: 0.2,
      transparent: true,
      opacity: 0.85,
    })
    const roofMat = new THREE.MeshStandardMaterial({
      color: 0x3a4555,
      roughness: 0.6,
      metalness: 0.3,
    })
    const edgeMat = new THREE.LineBasicMaterial({ color: 0x6a80a0, transparent: true, opacity: 0.6 })

    for (let i = 0; i < park.warehouses.length; i++) {
      const wh = park.warehouses[i]
      const cx = wh.x + wh.length / 2
      const cz = wh.z + wh.width / 2
      const h = wh.floorCount * wh.floorHeight

      const bodyGeo = new THREE.BoxGeometry(wh.length, h, wh.width)
      const body = new THREE.Mesh(bodyGeo, buildingMat)
      body.position.set(cx, h / 2, cz)
      body.name = `WH-${wh.code}`
      body.userData.warehouseId = wh.id
      body.userData.whIndex = i
      this.buildingGroup.add(body)
      this.buildingMeshes.push(body)
      this.warehouseMap.set(i, wh)

      const edges = new THREE.EdgesGeometry(bodyGeo)
      const edge = new THREE.LineSegments(edges, edgeMat)
      edge.position.copy(body.position)
      this.buildingGroup.add(edge)

      const roofGeo = new THREE.BoxGeometry(wh.length + 1, 0.3, wh.width + 1)
      const roof = new THREE.Mesh(roofGeo, roofMat)
      roof.position.set(cx, h + 0.15, cz)
      this.buildingGroup.add(roof)

      const locCount = this.countLocations(wh)
      const label = makeLabel(wh.name, '#e8edf2', `${wh.code} | ${wh.floorCount}F | ${locCount}库位`)
      label.position.set(cx, h + 2.5, cz)
      this.labelGroup.add(label)
    }
  }

  private countLocations(wh: Warehouse): number {
    let n = 0
    for (const area of wh.areas) {
      for (const rack of area.racks) {
        n += rack.locations.length
      }
    }
    return n
  }

  getWarehouseByMesh(mesh: THREE.Mesh): Warehouse | null {
    const idx = mesh.userData.whIndex as number
    if (idx === undefined) return null
    return this.warehouseMap.get(idx) || null
  }

  getBoundingBox(park: Park): THREE.Box3 {
    const box = new THREE.Box3()
    box.setFromCenterAndSize(
      new THREE.Vector3(park.length / 2, 5, park.width / 2),
      new THREE.Vector3(park.length, 10, park.width),
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
    disposeGroup(this.buildingGroup)
    disposeGroup(this.labelGroup)
    disposeGroup(this.roadGroup)
    this.buildingMeshes = []
    this.warehouseMap.clear()
  }
}