<script setup lang="ts">
import { computed, onMounted, provide, ref } from 'vue'
import { useRoute } from 'vue-router'
import { t, loadAllTranslations } from '@/store/lang'

import RendererNode from '@/components/renderer/RendererNode.vue'
import PageModalHost from '@/components/renderer/PageModalHost.vue'
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

/** 新标签页打开时 store 为空，需从 sessionStorage（设计器实时数据）或 localStorage（已保存数据）恢复 */
onMounted(async () => {
  // 优先从 sessionStorage 读取设计器当前数据（无需保存即可预览）
  const previewDataStr = sessionStorage.getItem('lowcode-preview-data')
  if (previewDataStr) {
    try {
      const previewData = JSON.parse(previewDataStr)
      // 根据页面类型设置设计器模式：业务页面进 biz 模式以加载 bizSchemas
      if (previewData.pageType && previewData.pageType !== 'custom') {
        setDesignerMode('biz')
      }
      // 构造 PageSchema 并导入
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
        pageType: previewData.pageType,
        bizSchemas: previewData.bizSchemas,
        lastBizMode: previewData.lastBizMode,
      }
      importSchema(pageSchema)

    } catch {
      // 忽略格式错误，回退到 localStorage
    }
  }

  // 如果仍无 schema，尝试从后端恢复：分层继承时优先加载合并后 schema
  if (!designerStore.schema.length) {
    const pageId = route.query.pageId as string
    const versionId = route.query.version as string
    if (pageId) {
      // ?version= 预览特定版本：优先加载该版本 schema
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
            }
            importSchema(pageSchema)
          }
        } catch {
          /* 版本加载失败，回退到合并 schema */
        }
      }
      // 未指定版本或版本加载失败：优先 loadPage（含完整 bizSchemas），失败再回退 loadMergedSchema
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
          // 填充表单控件值
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
