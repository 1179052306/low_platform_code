<script setup lang="ts">
import { computed, onMounted, provide, ref } from 'vue'
import { t, loadAllTranslations } from '@/store/lang'

import RendererNode from '@/components/renderer/RendererNode.vue'
import PageModalHost from '@/components/renderer/PageModalHost.vue'
import {
  designerStore,
  importSchema,
  DESIGNER_STORE_KEY,
} from '@/store/designer'
import { loadLatestPage } from '@/store/persistence'
import { applyTheme, getThemeDetail } from '@/composables/useTheme'
import type { PageSchema } from '@/types/schema'

/** 提供主 store 给渲染层（子页面弹窗会注入独立 store 覆盖） */
provide(DESIGNER_STORE_KEY, designerStore)

const previewRootRef = ref<HTMLElement>()

/** 新标签页打开时 store 为空，需从 sessionStorage（设计器实时数据）或 localStorage（已保存数据）恢复 */
onMounted(async () => {
  // 优先从 sessionStorage 读取设计器当前数据（无需保存即可预览）
  const previewDataStr = sessionStorage.getItem('lowcode-preview-data')
  if (previewDataStr) {
    try {
      const previewData = JSON.parse(previewDataStr)
      // 构造 PageSchema 并导入
      const pageSchema: PageSchema = {
        version: '1.0',
        pageId: previewData.pageId || 'preview',
        pageName: previewData.pageName || t('预览页面'),
        tableName: previewData.tableName || '',
        pageCss: previewData.pageCss || { background: '#ffffff' },
        themeId: previewData.themeId,
        root: previewData.schema,
      }
      importSchema(pageSchema)
      sessionStorage.removeItem('lowcode-preview-data')
    } catch {
      // 忽略格式错误，回退到 localStorage
    }
  }

  // 如果仍无 schema，尝试从后端恢复最近保存的页面
  if (!designerStore.schema.length) {
    const page = await loadLatestPage()
    if (page) importSchema(page)
  }
  designerStore.previewMode = true
  await loadAllTranslations()
  if (previewRootRef.value && designerStore.themeId) {
    try {
      const theme = await getThemeDetail(designerStore.themeId)
      applyTheme(previewRootRef.value, theme)
    } catch {
      // 主题加载失败忽略，使用默认样式
    }
  }
})

const pageStyle = computed(() => ({
  background: designerStore.pageCss.background || 'var(--lc-page-bg, #ffffff)',
  minHeight: '100vh',
}))
</script>

<template>
  <div
    class="preview"
    :class="{ 'lc-theme-page': !!designerStore.themeId }"
    ref="previewRootRef"
    :style="pageStyle"
  >
    <div v-if="!designerStore.schema.length" class="preview-empty">
      {{ t('暂无页面配置，请返回设计器配置后保存并重新预览') }}
    </div>
    <div v-else class="preview-canvas">
      <RendererNode
        v-for="node in designerStore.schema"
        :key="node.id"
        :node="node"
        :positioned="true"
      />
    </div>
    <PageModalHost />
  </div>
</template>

<style scoped>
.preview {
  width: 100%;
  min-height: 100vh;
  box-sizing: border-box;
  position: relative;
}
.preview-canvas {
  position: relative;
  width: 100%;
  min-height: 100vh;
}
.preview-empty {
  color: #c0c4cc;
  text-align: center;
  padding: 120px 0;
  font-size: 14px;
}
</style>
