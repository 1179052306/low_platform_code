import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import type { Warehouse, Park, ViewMode, LocationStatusUpdate } from '@/types/warehouse-3d'
import { LayoutBuilder } from './LayoutBuilder'
import { ObstacleBuilder } from './ObstacleBuilder'
import { ChannelRenderer } from './ChannelRenderer'
import { SelectionManager } from './SelectionManager'
import { ParkBuilder } from './ParkBuilder'

export interface RendererCallbacks {
  onLocationClick?: (locationId: string, code: string) => void
  onWarehouseClick?: (warehouseId: string) => void
  onFpsUpdate?: (fps: number) => void
}

export class WarehouseRenderer {
  scene: THREE.Scene
  camera: THREE.PerspectiveCamera
  renderer!: THREE.WebGLRenderer
  controls!: OrbitControls

  layoutBuilder: LayoutBuilder
  obstacleBuilder: ObstacleBuilder
  channelRenderer: ChannelRenderer
  selectionManager: SelectionManager
  parkBuilder: ParkBuilder

  private warehouse: Warehouse | null = null
  private park: Park | null = null
  private rafId = 0
  private lastFrameTime = 0
  private fpsAccum = 0
  private fpsCount = 0
  private fpsLastReport = 0
  private callbacks: RendererCallbacks
  private disposed = false
  private parkClickHandler: ((e: MouseEvent) => void) | null = null
  private parkRaycaster = new THREE.Raycaster()
  private parkMouse = new THREE.Vector2()

  constructor(callbacks: RendererCallbacks = {}) {
    this.callbacks = callbacks
    this.scene = new THREE.Scene()
    this.scene.background = new THREE.Color(0x1a1d24)
    this.scene.fog = new THREE.Fog(0x1a1d24, 200, 600)

    this.camera = new THREE.PerspectiveCamera(50, 1, 0.1, 2000)
    this.camera.position.set(80, 80, 80)
    this.camera.lookAt(50, 0, 30)


    this.layoutBuilder = new LayoutBuilder()
    this.obstacleBuilder = new ObstacleBuilder()
    this.channelRenderer = new ChannelRenderer()
    this.selectionManager = new SelectionManager()
    this.parkBuilder = new ParkBuilder()

    this.setupLights()
  }

  private setupLights(): void {
    const hemi = new THREE.HemisphereLight(0xe8edf2, 0x3a3d44, 0.6)
    this.scene.add(hemi)

    const ambient = new THREE.AmbientLight(0xffffff, 0.25)
    this.scene.add(ambient)

    const dir = new THREE.DirectionalLight(0xffffff, 0.8)
    dir.position.set(60, 100, 40)
    this.scene.add(dir)

    const dir2 = new THREE.DirectionalLight(0xa0b4c8, 0.35)
    dir2.position.set(-40, 60, -40)
    this.scene.add(dir2)

    const dir3 = new THREE.DirectionalLight(0xffffff, 0.2)
    dir3.position.set(0, 80, 80)
    this.scene.add(dir3)
  }

  init(canvas: HTMLCanvasElement): void {
    this.renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: false })
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    this.renderer.shadowMap.enabled = false
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping
    this.renderer.toneMappingExposure = 1.0

    this.controls = new OrbitControls(this.camera, canvas)
    this.controls.enableDamping = true
    this.controls.dampingFactor = 0.08
    this.controls.maxPolarAngle = Math.PI / 2 - 0.01

    const parent = canvas.parentElement
    if (parent) {
      const w = parent.clientWidth
      const h = parent.clientHeight
      this.camera.aspect = w / h
      this.camera.updateProjectionMatrix()
      this.renderer.setSize(w, h, false)
    }
    this.startRenderLoop()
  }

  onResize(): void {
    const canvas = this.renderer.domElement
    const parent = canvas.parentElement
    if (!parent) return
    const w = parent.clientWidth
    const h = parent.clientHeight
    if (w === 0 || h === 0) return
    this.camera.aspect = w / h
    this.camera.updateProjectionMatrix()
    this.renderer.setSize(w, h, false)
  }

  loadLayout(warehouse: Warehouse, floorOffsetY = 0): void {
    this.warehouse = warehouse
    this.clearScene()

    this.layoutBuilder.build(warehouse, floorOffsetY)
    this.obstacleBuilder.build(warehouse.obstacles)
    this.channelRenderer.build(warehouse.channels)

    this.scene.add(this.layoutBuilder.group)
    this.scene.add(this.obstacleBuilder.group)
    this.scene.add(this.channelRenderer.group)

    this.selectionManager.attach(
      this.layoutBuilder.locationMesh,
      this.layoutBuilder.pickMap,
      this.camera,
      this.renderer.domElement,
      this.callbacks.onLocationClick,
    )

    this.fitToView()
  }

  /** 加载园区布局 */
  loadPark(park: Park): void {
    this.park = park
    this.warehouse = null
    this.clearScene()
    this.clearParkScene()

    this.parkBuilder.build(park)
    this.scene.add(this.parkBuilder.group)

    this.parkClickHandler = (e: MouseEvent) => this.handleParkClick(e)
    this.renderer.domElement.addEventListener('click', this.parkClickHandler)

    this.fitToParkView()
  }

  private handleParkClick(e: MouseEvent): void {
    if (!this.park || this.parkBuilder.buildingMeshes.length === 0) return
    const canvas = this.renderer.domElement
    const rect = canvas.getBoundingClientRect()
    this.parkMouse.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
    this.parkMouse.y = -((e.clientY - rect.top) / rect.height) * 2 + 1

    this.parkRaycaster.setFromCamera(this.parkMouse, this.camera)
    const hits = this.parkRaycaster.intersectObjects(this.parkBuilder.buildingMeshes, false)
    if (hits.length > 0) {
      const mesh = hits[0].object as THREE.Mesh
      const wh = this.parkBuilder.getWarehouseByMesh(mesh)
      if (wh) {
        this.callbacks.onWarehouseClick?.(wh.id)
      }
    }
  }

  private fitToParkView(): void {
    if (!this.park) return
    const box = this.parkBuilder.getBoundingBox(this.park)
    const center = box.getCenter(new THREE.Vector3())
    const size = box.getSize(new THREE.Vector3())
    const maxDim = Math.max(size.x, size.y, size.z)
    const distance = maxDim / (2 * Math.tan((this.camera.fov * Math.PI) / 360)) * 1.4

    this.camera.position.set(
      center.x + distance * 0.6,
      center.y + distance * 0.7,
      center.z + distance * 0.6,
    )
    this.camera.lookAt(center)
    this.controls.target.copy(center)
    this.controls.update()
  }

  private clearParkScene(): void {
    if (this.parkClickHandler) {
      this.renderer.domElement.removeEventListener('click', this.parkClickHandler)
      this.parkClickHandler = null
    }
    this.scene.remove(this.parkBuilder.group)
    this.parkBuilder.dispose()
  }

  /** 更新库位状态（WebSocket 推送） */
  updateLocationStatus(updates: LocationStatusUpdate[]): void {
    for (const u of updates) {
      this.layoutBuilder.updateLocationStatus(u.locationId, u.status, u.utilization)
    }
  }

  setViewMode(mode: ViewMode): void {
    if (!this.warehouse) return
    const wh = this.warehouse
    const cx = wh.length / 2
    const cz = wh.width / 2

    switch (mode) {
      case 'perspective':
        this.camera.position.set(cx + 60, 70, cz + 60)
        this.camera.lookAt(cx, 0, cz)
        break
      case 'top':
        this.camera.position.set(cx, 120, cz + 0.01)
        this.camera.lookAt(cx, 0, cz)
        break
      case 'side':
        this.camera.position.set(cx, 20, cz + 120)
        this.camera.lookAt(cx, 10, cz)
        break
      case 'front':
        this.camera.position.set(cx + 120, 20, cz)
        this.camera.lookAt(cx, 10, cz)
        break
    }
    this.controls.update()
  }

  fitToView(): void {
    if (!this.warehouse) return
    const box = this.layoutBuilder.getBoundingBox(this.warehouse)
    const center = box.getCenter(new THREE.Vector3())
    const size = box.getSize(new THREE.Vector3())
    const maxDim = Math.max(size.x, size.y, size.z)
    const distance = maxDim / (2 * Math.tan((this.camera.fov * Math.PI) / 360)) * 1.6

    this.camera.position.set(
      center.x + distance * 0.7,
      center.y + distance * 0.6,
      center.z + distance * 0.7,
    )
    this.camera.lookAt(center)
    this.controls.target.copy(center)
    this.controls.update()
  }

  setEditMode(enabled: boolean): void {
    this.controls.enableRotate = !enabled
    this.selectionManager.setEditMode(enabled)
  }

  toggleAreaBoundaries(visible: boolean): void {
    this.layoutBuilder.areaGroup.visible = visible
  }

  toggleChannels(visible: boolean): void {
    this.channelRenderer.group.visible = visible
  }

  toggleObstacles(visible: boolean): void {
    this.obstacleBuilder.group.visible = visible
  }

  toggleFloorGrid(visible: boolean): void {
    if (this.layoutBuilder.gridHelper) {
      this.layoutBuilder.gridHelper.visible = visible
    }
  }

  toggleLabels(visible: boolean): void {
    this.layoutBuilder.toggleLabels(visible)
  }

  private startRenderLoop(): void {
    const loop = (time: number) => {
      if (this.disposed) return
      this.rafId = requestAnimationFrame(loop)

      const delta = time - this.lastFrameTime
      this.lastFrameTime = time
      if (delta > 0) {
        this.fpsAccum += 1000 / delta
        this.fpsCount++
      }
      if (time - this.fpsLastReport > 1000 && this.fpsCount > 0) {
        const fps = Math.round(this.fpsAccum / this.fpsCount)
        this.callbacks.onFpsUpdate?.(fps)
        this.fpsAccum = 0
        this.fpsCount = 0
        this.fpsLastReport = time
      }

      this.controls.update()
      this.renderer.render(this.scene, this.camera)
    }
    this.rafId = requestAnimationFrame(loop)
  }

  private clearScene(): void {
    this.selectionManager.detach()
    this.scene.remove(this.layoutBuilder.group)
    this.scene.remove(this.obstacleBuilder.group)
    this.scene.remove(this.channelRenderer.group)
    this.layoutBuilder.dispose()
    this.obstacleBuilder.dispose()
    this.channelRenderer.dispose()
  }

  dispose(): void {
    this.disposed = true
    cancelAnimationFrame(this.rafId)
    this.selectionManager.detach()
    this.clearParkScene()
    this.layoutBuilder.dispose()
    this.obstacleBuilder.dispose()
    this.channelRenderer.dispose()
    this.controls.dispose()
    this.renderer.dispose()
    this.scene.traverse((obj) => {
      if (obj instanceof THREE.Mesh) {
        obj.geometry?.dispose()
        const mat = obj.material
        if (Array.isArray(mat)) {
          mat.forEach((m) => m.dispose())
        } else {
          mat?.dispose()
        }
      }
    })
  }
}