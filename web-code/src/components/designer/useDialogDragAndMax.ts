import { ref, computed, onBeforeUnmount } from 'vue'

/**
 * 弹框拖拽移动 + 最大化/还原 通用逻辑。
 *
 * 用于自定义 div 弹框（.dialog-overlay / .dialog-container 结构）：
 *   - 拖拽 header 移动弹框（transform 偏移基于 flex 居中位置）
 *   - 最大化时铺满视口，还原时恢复原尺寸与位置
 *
 * 用于 ElDialog：只需 isMaximized + toggleMaximize 即可配合 draggable + :fullscreen。
 */
export function useDialogDragAndMax() {
  const isMaximized = ref(false)
  const isDragging = ref(false)
  const dragDelta = ref({ x: 0, y: 0 })

  let startMouseX = 0
  let startMouseY = 0
  let startDeltaX = 0
  let startDeltaY = 0
  let maximizedDelta = { x: 0, y: 0 }

  function onHeaderMouseDown(e: MouseEvent) {
    if (isMaximized.value) return
    if (e.button !== 0) return
    const target = e.target as HTMLElement
    if (target.closest('button') || target.closest('a') || target.closest('input')) return

    isDragging.value = true
    startMouseX = e.clientX
    startMouseY = e.clientY
    startDeltaX = dragDelta.value.x
    startDeltaY = dragDelta.value.y
    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', onMouseUp)
    e.preventDefault()
  }

  function onMouseMove(e: MouseEvent) {
    if (!isDragging.value) return
    let nx = startDeltaX + (e.clientX - startMouseX)
    let ny = startDeltaY + (e.clientY - startMouseY)
    const maxX = window.innerWidth / 2 - 80
    const maxY = window.innerHeight / 2 - 40
    nx = Math.max(-maxX, Math.min(nx, maxX))
    ny = Math.max(-maxY, Math.min(ny, maxY))
    dragDelta.value = { x: nx, y: ny }
  }

  function onMouseUp() {
    isDragging.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }

  function toggleMaximize() {
    if (isMaximized.value) {
      isMaximized.value = false
      dragDelta.value = { ...maximizedDelta }
    } else {
      maximizedDelta = { ...dragDelta.value }
      isMaximized.value = true
    }
  }

  function resetDialog() {
    isMaximized.value = false
    dragDelta.value = { x: 0, y: 0 }
    isDragging.value = false
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }

  const dragStyle = computed(() => {
    if (isMaximized.value) return {}
    if (dragDelta.value.x === 0 && dragDelta.value.y === 0) return {}
    return {
      transform: `translate(${dragDelta.value.x}px, ${dragDelta.value.y}px)`,
    }
  })

  onBeforeUnmount(() => {
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  })

  return {
    isMaximized,
    isDragging,
    dragDelta,
    dragStyle,
    onHeaderMouseDown,
    toggleMaximize,
    resetDialog,
  }
}