import * as THREE from 'three'
import type { Channel } from '@/types/warehouse-3d'

export class ChannelRenderer {
  group: THREE.Group

  constructor() {
    this.group = new THREE.Group()
    this.group.name = 'Channels'
  }

  build(channels: Channel[]): void {
    this.dispose()
    for (const ch of channels) {
      const line = this.createChannelLine(ch)
      this.group.add(line)
    }
  }

  private createChannelLine(ch: Channel): THREE.Line {
    const points: THREE.Vector3[] = ch.path.map((p) => new THREE.Vector3(p.x, 0.05, p.z))
    const geo = new THREE.BufferGeometry().setFromPoints(points)

    let color = 0x2196F3
    switch (ch.channelType) {
      case 'main':
        color = 0x2196F3
        break
      case 'sub':
        color = 0x03A9F4
        break
      case 'fire':
        color = 0xF44336
        break
    }

    const mat = new THREE.LineBasicMaterial({ color, linewidth: 2, transparent: true, opacity: 0.7 })
    const line = new THREE.Line(geo, mat)
    line.name = `Channel-${ch.id}`
    line.userData.channel = ch
    return line
  }

  dispose(): void {
    this.group.traverse((obj) => {
      if (obj instanceof THREE.Line) {
        obj.geometry.dispose()
        ;(obj.material as THREE.Material).dispose()
      }
    })
    this.group.clear()
  }
}