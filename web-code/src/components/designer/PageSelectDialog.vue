<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import DxDataGrid from 'devextreme-vue/data-grid'
import DxTreeView from 'devextreme-vue/tree-view'
import AdvancedSearchPanel from '../renderer/AdvancedSearchPanel.vue'
import type {
  GridColumn,
  SearchCondition,
} from '../renderer/AdvancedSearchPanel.vue'
import {
  listModules,
  listPagesByModule,
  type ModuleInfo,
  type SavedPageMeta,
} from '@/store/persistence'
import { t } from '@/store/lang'

const props = defineProps<{
  visible: boolean
  /** 当前选中的 pageId */
  modelValue?: string
  /** 需要过滤掉的页面 ID（通常是当前正在编辑的页面） */
  excludePageId?: string
  /** 只显示指定页面类型（如 'basedata'），不传则显示全部 */
  pageType?: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'update:modelValue': [value: string]
  select: [pageId: string, pageName: string]
}>()

// ================ 模块树（DevExtreme TreeView） ================
const modules = ref<ModuleInfo[]>([])
const activeModuleId = ref('')
const moduleSearch = ref('')

interface TreeItem {
  id: string
  text: string
  parentId?: string
  expanded?: boolean
}

const treeItems = computed<TreeItem[]>(() =>
  modules.value.map((m) => ({
    id: m.id,
    text: m.name,
    parentId: m.parentId || undefined,
  }))
)

function onTreeItemClick(e: { itemData?: { id?: string | number } }) {
  if (e.itemData?.id) selectModule(String(e.itemData.id))
}

// ================ 页面列表 ================
const pages = ref<SavedPageMeta[]>([])
const selectedPageId = ref('')

const selectedPageName = computed(() => {
  const p = pages.value.find((x) => x.pageId === selectedPageId.value)
  return p?.pageName || ''
})

async function refreshModules() {
  try {
    modules.value = await listModules()
  } catch {
    return
  }
  if (
    !activeModuleId.value ||
    !modules.value.find((m) => m.id === activeModuleId.value)
  ) {
    activeModuleId.value = modules.value[0]?.id || ''
  }
}

async function refreshPages() {
  if (activeModuleId.value) {
    try {
      pages.value = await listPagesByModule(activeModuleId.value)
    } catch {
      pages.value = []
    }
  } else {
    pages.value = []
  }
}

async function selectModule(id: string) {
  activeModuleId.value = id
  await refreshPages()
}

function selectPage(p: SavedPageMeta) {
  selectedPageId.value = p.pageId
}

function confirmSelect() {
  if (!selectedPageId.value) return
  const p = pages.value.find((x) => x.pageId === selectedPageId.value)
  emit('select', selectedPageId.value, p?.pageName || '')
  emit('update:modelValue', selectedPageId.value)
  emit('update:visible', false)
}

function closeDialog() {
  emit('update:visible', false)
}

// ================ 高级查询 ================
const advSearchConditions = ref<SearchCondition[]>([])

const advColumns: GridColumn[] = [
  { dataField: 'pageName', caption: t('页面名称'), dataType: 'string' },
  { dataField: 'pageId', caption: t('页面编码'), dataType: 'string' },
]

function onAdvSearch(conditions: SearchCondition[]) {
  advSearchConditions.value = conditions
}

function onAdvSearchReset() {
  advSearchConditions.value = []
}

function matchCondition(val: unknown, cond: SearchCondition): boolean {
  const s = String(val ?? '')
  const v = cond.value
  switch (cond.operator) {
    case 'contains':
      return s.includes(v)
    case 'notcontains':
      return !s.includes(v)
    case '=':
      return s === v
    case '<>':
      return s !== v
    case 'startswith':
      return s.startsWith(v)
    case 'endswith':
      return s.endsWith(v)
    default:
      return true
  }
}

const filteredPages = computed(() => {
  let list = pages.value.filter((p) => p.enabled !== false)
  if (props.pageType) {
    list = list.filter((p) => p.pageType === props.pageType)
  }
  if (props.excludePageId) {
    list = list.filter((p) => p.pageId !== props.excludePageId)
  }
  const conds = advSearchConditions.value.filter(
    (c) => c.field && c.operator && c.value !== ''
  )
  if (conds.length === 0) return list
  return list.filter((p) =>
    conds.every((c) =>
      matchCondition((p as unknown as Record<string, unknown>)[c.field], c)
    )
  )
})

// 弹窗打开时初始化
watch(
  () => props.visible,
  async (val) => {
    if (val) {
      await refreshModules()
      selectedPageId.value = props.modelValue || ''
      moduleSearch.value = ''
      advSearchConditions.value = []
      if (selectedPageId.value) {
        for (const m of modules.value) {
          const ps = await listPagesByModule(m.id)
          if (ps.some((p) => p.pageId === selectedPageId.value)) {
            activeModuleId.value = m.id
            break
          }
        }
      }
      await refreshPages()
    }
  }
)
</script>

<template>
  <Teleport to="body">
    <div v-if="visible" class="psd-overlay" @click.self="closeDialog">
      <div class="psd-dialog">
        <div class="psd-header">
          <span>{{ t('选择页面') }}</span>
          <button class="psd-close" @click="closeDialog">✕</button>
        </div>
        <div class="psd-body">
          <!-- 左侧模块树（DevExtreme TreeView + 搜索框） -->
          <div class="psd-sidebar">
            <div class="psd-sidebar-search">
              <input
                v-model="moduleSearch"
                class="psd-search-input"
                :placeholder="t('搜索模块...')"
              />
            </div>
            <DxTreeView
              class="psd-tree"
              :data-source="treeItems"
              data-structure="plain"
              key-expr="id"
              parent-id-expr="parentId"
              display-expr="text"
              :search-value="moduleSearch"
              search-mode="contains"
              :selected-item-keys="activeModuleId ? [activeModuleId] : []"
              selection-mode="single"
              :show-borders="true"
              :expand-by-click="true"
              height="100%"
              @item-click="onTreeItemClick"
            />
          </div>
          <!-- 右侧：高级查询 + 页面表格 -->
          <div class="psd-content">
            <AdvancedSearchPanel
              :columns="advColumns"
              grid-id="page_select_dialog"
              @search="onAdvSearch"
              @reset="onAdvSearchReset"
            />
            <DxDataGrid
              class="psd-grid"
              :data-source="filteredPages"
              :selected-row-keys="selectedPageId ? [selectedPageId] : []"
              :columns="[
                { dataField: 'pageName', caption: t('页面名称') },
                { dataField: 'pageId', caption: t('页面编码') },
              ]"
              :selection="{ mode: 'single', showCheckBoxesMode: 'none' }"
              :hover-state-enabled="true"
              :show-borders="true"
              :show-column-lines="false"
              :show-row-lines="true"
              :paging="{ enabled: false }"
              :filter-row="{ visible: true }"
              height="100%"
              key-expr="pageId"
              @selection-changed="(e: any) => {
                if (e.selectedRowsData.length > 0) selectPage(e.selectedRowsData[0])
              }"
            />
          </div>
        </div>
        <div class="psd-footer">
          <span class="psd-selected-info" v-if="selectedPageId">
            {{ t('已选：') }}{{ selectedPageName }} ({{ selectedPageId }})
          </span>
          <div class="psd-footer-actions">
            <button class="psd-btn" @click="closeDialog">
              {{ t('取消') }}
            </button>
            <button
              class="psd-btn psd-btn-primary"
              :disabled="!selectedPageId"
              @click="confirmSelect"
            >
              {{ t('确定') }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.psd-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 20000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.psd-dialog {
  background: #fff;
  border-radius: 8px;
  width: 860px;
  max-width: 95vw;
  height: 560px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}
.psd-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
  font-size: 15px;
  font-weight: 600;
}
.psd-close {
  border: none;
  background: none;
  font-size: 16px;
  color: #999;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
}
.psd-close:hover {
  background: #f0f0f0;
}
.psd-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}
.psd-sidebar {
  width: 220px;
  border-right: 1px solid #eee;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow: hidden;
}
.psd-sidebar-search {
  padding: 8px;
  border-bottom: 1px solid #eee;
  flex-shrink: 0;
}
.psd-search-input {
  width: 100%;
  box-sizing: border-box;
  padding: 5px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  font-size: 12px;
  outline: none;
}
.psd-search-input:focus {
  border-color: #2563eb;
}
.psd-tree {
  flex: 1;
  overflow: hidden;
}
.psd-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 8px;
  gap: 8px;
}
.psd-grid {
  flex: 1;
  overflow: hidden;
}
.psd-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-top: 1px solid #eee;
}
.psd-selected-info {
  font-size: 13px;
  color: #2563eb;
}
.psd-footer-actions {
  display: flex;
  gap: 8px;
}
.psd-btn {
  border: 1px solid #d1d5db;
  background: #fff;
  color: #374151;
  padding: 6px 16px;
  border-radius: 6px;
  font-size: 13px;
  cursor: pointer;
}
.psd-btn:hover {
  background: #f3f4f6;
}
.psd-btn-primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}
.psd-btn-primary:hover {
  background: #4338ca;
}
.psd-btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>

