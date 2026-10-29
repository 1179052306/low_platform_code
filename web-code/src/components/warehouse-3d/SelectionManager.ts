import * as THREE from 'three'
import type { Location, LocationStatusUpdate } from '@/types/warehouse-3d'
import type { LocationPickMap } from './LayoutBuilder'
import { STATUS_COLORS } from '@/utils/warehouse-3d/colorPalette'

export class SelectionManager {
  private mesh: THREE.InstancedMesh | null = null
  private pickMap: LocationPickMap[] = []
  private camera: THREE.PerspectiveCamera | null = null
  private canvas: HTMLCanvasElement | null = null
  private raycaster = new THREE.Raycaster()
  private mouse = new THREE.Vector2()
  private editMode = false
  private onLocationClick: ((locationId: string, code: string) => void) | null = null

  private selectedInstanceIds: Set<number> = new Set()
  private clickHandler: ((e: MouseEvent) => void) | null = null

  attach(
    mesh: THREE.InstancedMesh | null,
    pickMap: LocationPickMap[],
    camera: THREE.PerspectiveCamera,
    canvas: HTMLCanvasElement,
    onLocationClick?: (locationId: string, code: string) => void,
  ): void {
    this.detach()
    this.mesh = mesh
    this.pickMap = pickMap
    this.camera = camera
    this.canvas = canvas
    this.onLocationClick = onLocationClick || null

    this.clickHandler = (e: MouseEvent) => this.handleClick(e)
    canvas.addEventListener('click', this.clickHandler)
  }

  detach(): void {
    if (this.clickHandler && this.canvas) {
      this.canvas.removeEventListener('click', this.clickHandler)
    }
    this.clickHandler = null
    this.canvas = null
    this.mesh = null
    this.pickMap = []
    this.camera = null
    this.selectedInstanceIds.clear()
  }

  setEditMode(enabled: boolean): void {
    this.editMode = enabled
  }

  private handleClick(e: MouseEvent): void {
    if (!this.mesh || !this.camera || !this.canvas) return
    const rect = this.canvas.getBoundingClientRect()
    this.mouse.x = ((e.clientX - rect.left) / rect.width) * 2 - 1
    this.mouse.y = -((e.clientY - rect.top) / rect.height) * 2 + 1

    this.raycaster.setFromCamera(this.mouse, this.camera)
    const intersection = this.raycaster.intersectObject(this.mesh, false)

    if (intersection.length > 0) {
      const instId = intersection[0].instanceId
      if (instId === undefined || instId === null) return
      const entry = this.pickMap[instId]
      if (!entry) return

      if (e.shiftKey) {
        this.toggleSelect(instId)
      } else {
        this.clearSelection()
        this.toggleSelect(instId)
      }

      this.onLocationClick?.(entry.location.id, entry.location.code)
    } else if (!e.shiftKey) {
      this.clearSelection()
    }
  }

  private toggleSelect(instanceId: number): void {
    if (this.selectedInstanceIds.has(instanceId)) {
      this.selectedInstanceIds.delete(instanceId)
      this.highlight(instanceId, false)
    } else {
      this.selectedInstanceIds.add(instanceId)
      this.highlight(instanceId, true)
    }
  }

  private highlight(instanceId: number, highlighted: boolean): void {
    if (!this.mesh) return
    const color = new THREE.Color()
    if (highlighted) {
      color.setHex(0xFFD700)
    } else {
      const entry = this.pickMap[instanceId]
      if (!entry) return
      const c = STATUS_COLORS[entry.location.status]
      color.setHex(c.hex)
    }
    this.mesh.setColorAt(instanceId, color)
    if (this.mesh.instanceColor) {
      this.mesh.instanceColor.needsUpdate = true
    }
  }

  clearSelection(): void {
    for (const id of this.selectedInstanceIds) {
      this.highlight(id, false)
    }
    this.selectedInstanceIds.clear()
  }

  getSelectedLocations(): Location[] {
    return Array.from(this.selectedInstanceIds).map((id) => this.pickMap[id].location)
  }

  getSelectedCount(): number {
    return this.selectedInstanceIds.size
  }
}