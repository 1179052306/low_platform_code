import * as THREE from 'three'
import type { Warehouse, Obstacle, ViewMode } from '@/types/warehouse-3d'

export class ObstacleBuilder {
  group: THREE.Group

  constructor() {
    this.group = new THREE.Group()
    this.group.name = 'Obstacles'
  }

  build(obstacles: Obstacle[]): void {
    this.dispose()
    for (const obs of obstacles) {
      const mesh = this.createObstacleMesh(obs)
      this.group.add(mesh)
    }
  }

  private createObstacleMesh(obs: Obstacle): THREE.Mesh {
    let color = 0x9E9E9E
    let opacity = 0.8
    switch (obs.obstacleType) {
      case 'pillar':
        color = 0xBDBDBD
        opacity = 0.9
        break
      case 'wall':
        color = 0x757575
        opacity = 0.7
        break
      case 'door':
        color = 0x8D6E63
        opacity = 0.6
        break
      case 'column':
        color = 0xA1887F
        opacity = 0.9
        break
    }

    const geo = new THREE.BoxGeometry(obs.length, obs.height, obs.width)
    const mat = new THREE.MeshStandardMaterial({
      color,
      transparent: opacity < 1,
      opacity,
      roughness: 0.8,
    })
    const mesh = new THREE.Mesh(geo, mat)
    mesh.position.set(obs.x + obs.length / 2, obs.height / 2, obs.z + obs.width / 2)
    mesh.rotation.y = obs.rotation
    mesh.name = `Obstacle-${obs.id}`
    mesh.userData.obstacle = obs
    return mesh
  }

  dispose(): void {
    this.group.traverse((obj) => {
      if (obj instanceof THREE.Mesh) {
        obj.geometry.dispose()
        ;(obj.material as THREE.Material).dispose()
      }
    })
    this.group.clear()
  }
}