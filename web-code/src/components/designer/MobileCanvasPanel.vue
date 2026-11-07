<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { designerStore } from '@/store/designer'

const emit = defineEmits<{
  (e: 'save'): void
}>()

const iframeRef = ref<HTMLIFrameElement | null>(null)
const iframeReady = ref(false)
/** 拖拽悬停在 iframe 画布上时高亮 */
const iframeDropActive = ref(false)

/** iframe 文档级 dragover：preventDefault 允许 drop（否则浏览器显示禁止符） */
function onIframeDragOver(e: DragEvent): void {
  if (!e.dataTransfer?.types?.includes('application/x-material')) return
  e.preventDefault()
  e.dataTransfer.dropEffect = 'copy'
  iframeDropActive.value = true
}

/** iframe 文档级 drop：读取物料类型，转发给 Flutter 设计器按落点坐标添加节点 */
function onIframeDragDrop(e: DragEvent): void {
  const materialType = e.dataTransfer?.getData('application/x-material')
  iframeDropActive.value = false
  if (!materialType) return
  e.preventDefault()
  // clientX/Y 为 iframe 视口坐标，与 Flutter Web 全局坐标一致
  iframeRef.value?.contentWindow?.postMessage(
    JSON.stringify({ type: 'dropMaterial', materialType, x: e.clientX, y: e.clientY }),
    '*',
  )
}

/** 向 iframe 文档注册拖放桥接（Flutter 渲染层不处理 HTML5 DnD，由父页面代收）。
 *  先移除再添加保证幂等：designerReady 到达时文档可能被 Service Worker 替换过，需重挂 */
function setupIframeDropBridge(): void {
  const doc = iframeRef.value?.contentDocument
  if (!doc) return
  teardownIframeDropBridge()
  doc.addEventListener('dragover', onIframeDragOver as EventListener)
  doc.addEventListener('drop', onIframeDragDrop as EventListener)
  doc.addEventListener('dragleave', onIframeDragLeave as EventListener)
}

function onIframeDragLeave(): void {
  iframeDropActive.value = false
}

function teardownIframeDropBridge(): void {
  const doc = iframeRef.value?.contentDocument
  if (!doc) return
  doc.removeEventListener('dragover', onIframeDragOver as EventListener)
  doc.removeEventListener('drop', onIframeDragDrop as EventListener)
  doc.removeEventListener('dragleave', onIframeDragLeave as EventListener)
}

function buildMobileSchema(): Record<string, unknown> {
  const nodes = designerStore.schema
  const mobileGap = designerStore.pageCss.mobileGap ?? 8
  const mobilePadding = designerStore.pageCss.mobilePadding ?? 0
  const mobileLayout = designerStore.pageCss.mobileLayout || 'vertical'
  const root = nodes.length === 1 && (nodes[0].type === 'm-scaffold' || nodes[0].type === 'm-page-root')
    ? nodes[0]
    : { id: 'root', type: 'm-page-root', props: { layout: mobileLayout, gap: mobileGap, padding: mobilePadding }, children: nodes }
  return {
    pageType: 'mobile',
    pageCode: 'designer',
    pageName: '设计器',
    root,
  }
}

function sendSchemaToFlutter() {
  const iframe = iframeRef.value
  if (!iframe?.contentWindow) return
  try {
    const schema = JSON.parse(JSON.stringify(buildMobileSchema()))
    iframe.contentWindow.postMessage(JSON.stringify({ type: 'loadSchema', root: schema.root }), '*')
  } catch (e) {
    console.error('[mobile-designer] sendSchema error', e)
  }
}

function onWindowMessage(e: MessageEvent) {
  if (typeof e.data !== 'string') return
  try {
    const msg = JSON.parse(e.data)
    if (msg.type === 'designerReady') {
      iframeReady.value = true
      setupIframeDropBridge()
      sendSchemaToFlutter()
    } else if (msg.type === 'saveSchema') {
      const schema = msg.schema
      if (schema?.root) {
        const root = schema.root
        designerStore.schema = Array.isArray(root.children) ? root.children : []
        if (root.type === 'm-scaffold') {
          designerStore.schema = [root]
        }
        emit('save')
      }
    }
  } catch (_) {}
}

function onIframeLoad() {
  setupIframeDropBridge()
  setTimeout(() => {
    if (!iframeReady.value) {
      iframeReady.value = true
      sendSchemaToFlutter()
    }
  }, 2000)
}

watch(
  () => designerStore.schemaRevision,
  () => {
    if (iframeRef.value?.contentWindow) sendSchemaToFlutter()
  },
)

onMounted(() => {
  window.addEventListener('message', onWindowMessage)
})
onBeforeUnmount(() => {
  window.removeEventListener('message', onWindowMessage)
  teardownIframeDropBridge()
})
</script>

<template>
  <div class="mobile-canvas-wrapper" :class="{ 'drop-active': iframeDropActive }">
    <iframe
      ref="iframeRef"
      src="/mobile-preview/index.html?mode=designer"
      class="mobile-designer-iframe"
      @load="onIframeLoad"
    />
  </div>
</template>

<style scoped>
.mobile-canvas-wrapper {
  flex: 1;
  display: flex;
  overflow: hidden;
  background: #f0f2f5;
  transition: box-shadow 0.15s;
}
.mobile-canvas-wrapper.drop-active {
  box-shadow: inset 0 0 0 2px #1976d2;
}
.mobile-designer-iframe {
  width: 100%;
  height: 100%;
  border: none;
  display: block;
}
</style>
