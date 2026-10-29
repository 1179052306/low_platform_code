<script setup lang="ts">
import { computed, onActivated, onMounted, provide, ref } from 'vue'
import { useRoute } from 'vue-router'
import { t, loadAllTranslations } from '@/store/lang'

import RendererNode from '@/components/renderer/RendererNode.vue'
import PageModalHost from '@/components/renderer/PageModalHost.vue'
import ImportDialog from '@/components/import/ImportDialog.vue'
import {
  designerStore,
  importSchema,
  loadMergedSchema,
  setBizMode,
  setDesignerMode,
  DESIGNER_STORE_KEY,
} from '@/store/designer'
import { loadLatestPage, loadPage } from '@/store/persistence'
import { applyTheme, getThemeDetail } from '@/composables/useTheme'
import { useVersionHistory } from '@/composables/useVersionHistory'
import { gatewayCall } from '@/api'
import type { PageSchema, ComponentSchema } from '@/types/schema'

const { loadVersionSchema } = useVersionHistory()

/** 递归填充表单控件值：根据 dbField 从行数据中取值 */
function fillFormValues(node: ComponentSchema, data: Record<string, unknown>): void {
  if (node.dbField && data[node.dbField] !== undefined) {
    node.props.value = data[node.dbField]
  }
  if (node.children) {
    for (const child of node.children) {
      fillFormValues(child, data)
    }
  }
}

/** 提供主 store 给渲染层（子页面弹窗会注入独立 store 覆盖） */
provide(DESIGNER_STORE_KEY, designerStore)

const route = useRoute()
const previewRootRef = ref<HTMLElement>()

/** 清空 store 中的页面数据，为重新加载做准备 */
function clearPageData(): void {
  designerStore.schema = []
  designerStore.pageId = ''
  designerStore.pageName = ''
  designerStore.tableName = ''
  designerStore.pageType = 'custom'
  designerStore.themeId = ''
  designerStore.computeRules = []
  designerStore.fieldChangeEvents = []
  designerStore.pageRules = undefined
}

/** 从后端按 pageId 加载页面 schema */
async function loadPageFromBackend(pageId: string): Promise<void> {
  const versionId = route.query.version as string
  if (versionId) {
    try {
      const versionSchema = await loadVersionSchema(pageId, versionId)
      if (versionSchema) {
        const pageSchema: PageSchema = {
          version: '1.0',
          pageId,
          pageName: (versionSchema.pageName as string) || t('预览页面'),
          tableName: (versionSchema.tableName as string) || '',
          pageCss:
            (versionSchema.pageCss as Record<string, string>) || {
              background: '#ffffff',
            },
          root: (versionSchema.root as unknown as ComponentSchema[]) || [],
          computeRules: versionSchema.computeRules as unknown as PageSchema['computeRules'],
          fieldChangeEvents: versionSchema.fieldChangeEvents as unknown as PageSchema['fieldChangeEvents'],
          pageRules: versionSchema.pageRules as unknown as PageSchema['pageRules'],
        }
        importSchema(pageSchema)
      }
    } catch {
      /* 版本加载失败，回退到合并 schema */
    }
  }
  if (!designerStore.schema.length) {
    try {
      const page = await loadPage(pageId)
      if (page) {
        if (page.pageType && page.pageType !== 'custom') {
          setDesignerMode('biz')
        }
        importSchema(page)
      }
    } catch {
      // loadPage 失败，尝试分层继承合并 schema
    }
  }
  if (!designerStore.schema.length) {
    try {
      const merged = await loadMergedSchema(pageId)
      if (merged.pageType && merged.pageType !== 'custom') {
        setDesignerMode('biz')
      }
      importSchema(merged)
    } catch {
      const latest = await loadLatestPage()
      if (latest) {
        if (latest.pageType && latest.pageType !== 'custom') {
          setDesignerMode('biz')
        }
        importSchema(latest)
      }
    }
  }
}

/** 加载页面数据（首次挂载或路由变化时调用） */
async function loadPageData(): Promise<void> {
  // 优先从 sessionStorage 读取设计器当前数据（无需保存即可预览）
  const previewDataStr = sessionStorage.getItem('lowcode-preview-data')
  if (previewDataStr) {
    try {
      const previewData = JSON.parse(previewDataStr)
      if (previewData.pageType && previewData.pageType !== 'custom') {
        setDesignerMode('biz')
      }
      const pageSchema: PageSchema = {
        version: '1.0',
        pageId: previewData.pageId || 'preview',
        pageName: previewData.pageName || t('预览页面'),
        tableName: previewData.tableName || '',
        pageCss: previewData.pageCss || { background: '#ffffff' },
        themeId: previewData.themeId,
        root: previewData.schema,
        computeRules: previewData.computeRules,
        fieldChangeEvents: previewData.fieldChangeEvents,
        pageRules: previewData.pageRules,
        pageType: previewData.pageType,
        bizSchemas: previewData.bizSchemas,
        lastBizMode: previewData.lastBizMode,
      }
      importSchema(pageSchema)
    } catch {
      // 忽略格式错误，回退到 localStorage
    }
  }

  // 如果仍无 schema，尝试从后端恢复
  if (!designerStore.schema.length) {
    const pageId = (route.meta.pageId as string) || (route.query.pageId as string)
    if (pageId) {
      await loadPageFromBackend(pageId)
    } else {
      const page = await loadLatestPage()
      if (page) {
        if (page.pageType && page.pageType !== 'custom') {
          setDesignerMode('biz')
        }
        importSchema(page)
      }
    }
  }
  designerStore.previewMode = true

  // 支持 ?mode=form/table/mobile 参数：切换到对应模式 schema
  const modeParam = route.query.mode as string
  if (modeParam === 'form' || modeParam === 'table' || modeParam === 'mobile') {
    setBizMode(modeParam)
  }

  // 修改模式：?id=xxx 通过 gateway query 加载数据填充表单
  const editId = route.query.id as string
  if (editId && designerStore.tableName) {
    try {
      const result = await gatewayCall(designerStore.tableName, 'query', { filterData: JSON.stringify([{ Symbol: 'and' }, { Id: 'id', Symbol: '=', Val: editId }]) })
      if (result.ok && result.data) {
        const resBody = result.data as { data?: { data?: unknown[] } }
        const rows = resBody.data?.data || []
        if (rows.length > 0) {
          const rowData = rows[0] as Record<string, unknown>
          for (const node of designerStore.schema) {
            fillFormValues(node, rowData)
          }
        }
      }
    } catch {
      // 数据加载失败忽略
    }
  }

  await loadAllTranslations()
  if (previewRootRef.value && designerStore.themeId) {
    try {
      const theme = await getThemeDetail(designerStore.themeId)
      applyTheme(previewRootRef.value, theme)
    } catch {
      // 主题加载失败忽略，使用默认样式
    }
  }
}

/** 首次挂载：加载页面数据 */
onMounted(async () => {
  await loadPageData()
})

/** keep-alive 重新激活：若路由 pageId 变化则清空 store 重新加载 */
onActivated(async () => {
  const currentPageId = (route.meta.pageId as string) || (route.query.pageId as string)
  if (currentPageId && currentPageId !== designerStore.pageId) {
    clearPageData()
    await loadPageData()
  }
})

/** 业务页（单据/基础资料）流式渲染；自定义页保持自由画布定位 */
const isBizPage = computed(() => !!designerStore.pageType && designerStore.pageType !== 'custom')

/** Tab 内渲染（动态路由）时高度填满父容器；独立 /preview 全屏页时用 100vh */
const isStandalone = computed(() => !route.meta?.dynamic)

const pageStyle = computed(() => ({
  background: designerStore.pageCss.background || 'var(--lc-page-bg, #ffffff)',
  minHeight: isStandalone.value ? '100vh' : '100%',
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
        :positioned="!isBizPage"
      />
    </div>
    <PageModalHost />
    <ImportDialog />
  </div>
</template>

<style scoped>
.preview {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  position: relative;
  overflow: hidden;
}
.preview-canvas {
  position: relative;
  width: 100%;
  height: 100%;
}
.preview-empty {
  color: #c0c4cc;
  text-align: center;
  padding: 120px 0;
  font-size: 14px;
}
</style>
