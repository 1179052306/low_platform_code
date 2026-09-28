<script setup lang="ts">
import { provide, computed, watch } from 'vue'
import RendererNode from './RendererNode.vue'
import { createRenderStore, DESIGNER_STORE_KEY } from '@/store/designer'
import { pageModal, type PageModalInstance } from './pageModal'

const props = defineProps<{
  modal: PageModalInstance
  stackIndex?: number
}>()

/** 为此弹窗创建独立渲染 store，隔离 schema 和 schemaRevision */
const store = createRenderStore()
store.schema = props.modal.schema
provide(DESIGNER_STORE_KEY, store)

const isDrawer = computed(() => props.modal.options.mode === 'drawer')

/** 根据栈深度计算 z-index，确保多层弹窗叠加时后弹的在上方 */
const overlayZIndex = computed(() => 10000 + (props.stackIndex ?? 0) * 10)

function close() {
  pageModal.closePage()
}

function onOverlayClick() {
  close()
}

/** 弹窗内容样式 */
const contentStyle = computed(() => {
  const opts = props.modal.options
  const style: Record<string, string> = {}
  if (isDrawer.value) {
    style.width =
      typeof opts.width === 'number' ? `${opts.width}px` : String(opts.width)
    style.height = '100%'
  } else {
    style.width =
      typeof opts.width === 'number' ? `${opts.width}px` : String(opts.width)
    style.height =
      typeof opts.height === 'number' ? `${opts.height}px` : String(opts.height)
  }
  return style
})
</script>

<template>
  <Teleport to="body">
    <div
      class="page-modal-overlay"
      :class="{ 'is-drawer': isDrawer }"
      :style="{ zIndex: overlayZIndex }"
      @click.self="onOverlayClick"
    >
      <div
        class="page-modal-content"
        :class="{ 'is-drawer': isDrawer }"
        :style="contentStyle"
      >
        <div class="page-modal-header">
          <span class="page-modal-title">{{ modal.options.title }}</span>
          <button class="page-modal-close" @click="close">✕</button>
        </div>
        <div class="page-modal-body">
          <RendererNode
            v-for="node in store.schema"
            :key="node.id"
            :node="node"
          />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.page-modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);

  display: flex;
  align-items: center;
  justify-content: center;
}
.page-modal-overlay.is-drawer {
  justify-content: flex-end;
}
.page-modal-content {
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  max-width: 95vw;
  max-height: 95vh;
}
.page-modal-content.is-drawer {
  border-radius: 8px 0 0 8px;
  height: 100%;
  max-height: 100%;
  max-width: 95vw;
}
.page-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
  flex-shrink: 0;
}
.page-modal-title {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}
.page-modal-close {
  border: none;
  background: none;
  font-size: 16px;
  color: #999;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
}
.page-modal-close:hover {
  background: #f0f0f0;
  color: #333;
}
.page-modal-body {
  flex: 1;
  overflow: auto;
  padding: 12px;
}
</style>