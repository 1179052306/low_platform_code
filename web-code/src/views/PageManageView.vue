<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import DxTextBox from 'devextreme-vue/text-box'
import DxSelectBox from 'devextreme-vue/select-box'
import DxButton from 'devextreme-vue/button'
import SearchIcon from '@/components/SearchIcon.vue'

const router = useRouter()

import {
  listSystems,
  listModules,
  addModule,
  renameModule,
  deleteModule,
  listSavedPages,
  loadPage,
  savePage,
  deletePage,
  setPageEnabled,
  copyPage,
  listDeletedPages,
  restorePage,
  hardDeletePage,
  genPageId,
  type SystemInfo,
  type ModuleInfo,
  type SavedPageMeta,
} from '@/store/persistence'
import type { PageSchema, PageType } from '@/types/schema'
import { ElMessage } from 'element-plus'
import { t, loadModule } from '@/store/lang'

// ================ 系统状态 ================
const systems = ref<SystemInfo[]>([])
const activeSystemId = ref<string>('')
const systemPickerVisible = ref(false)
const systemSearchKeyword = ref('')
const activeSystemName = computed(
  () =>
    systems.value.find((s) => s.id === activeSystemId.value)?.name || '未选择'
)
const filteredSystems = computed(() => {
  const kw = systemSearchKeyword.value.trim().toLowerCase()
  if (!kw) return systems.value
  return systems.value.filter(
    (s) => s.name.toLowerCase().includes(kw) || s.id.toLowerCase().includes(kw)
  )
})

function selectSystem(id: string) {
  activeSystemId.value = id
  systemPickerVisible.value = false
}

watch(systemPickerVisible, (v) => {
  if (!v) systemSearchKeyword.value = ''
})

// ================ 模块状态 ================
const modules = ref<ModuleInfo[]>([])
const activeModuleId = ref<string>('')
const moduleEditingId = ref<string | null>(null)
const moduleEditName = ref('')
/** 展开的模块 id 集合 */
const expandedSet = ref<Set<string>>(new Set())

/** 扁平化模块节点（带层级信息，用于树形列表渲染） */
interface FlatModule extends ModuleInfo {
  depth: number
  hasChildren: boolean
  expanded: boolean
}

/** 构建模块树并扁平化为带 depth 的列表 */
const flatModules = computed<FlatModule[]>(() => {
  const all = modules.value
  const childrenMap = new Map<string, ModuleInfo[]>()
  const roots: ModuleInfo[] = []
  for (const m of all) {
    if (m.parentId && all.some((x) => x.id === m.parentId)) {
      const arr = childrenMap.get(m.parentId!) || []
      arr.push(m)
      childrenMap.set(m.parentId!, arr)
    } else {
      roots.push(m)
    }
  }
  const result: FlatModule[] = []
  const walk = (list: ModuleInfo[], depth: number) => {
    for (const m of list) {
      const children = childrenMap.get(m.id) || []
      const expanded = expandedSet.value.has(m.id)
      result.push({ ...m, depth, hasChildren: children.length > 0, expanded })
      if (expanded && children.length > 0) walk(children, depth + 1)
    }
  }
  walk(roots, 0)
  return result
})

function toggleExpand(id: string) {
  if (expandedSet.value.has(id)) {
    expandedSet.value.delete(id)
  } else {
    expandedSet.value.add(id)
  }
  // 触发 computed 重新计算
  expandedSet.value = new Set(expandedSet.value)
}

// ================ 页面状态 ================
const allPages = ref<SavedPageMeta[]>([])
const pageSearchKeyword = ref('')

/** 当前系统+模块的页面列表（从全量页面中过滤） */
const pages = computed(() => {
  const sysId = activeSystemId.value
  const moduleId = activeModuleId.value
  return allPages.value.filter(
    (p) =>
      (p.systemId || 'sys_default') === sysId &&
      (p.moduleId || 'mod_default') === moduleId
  )
})

/** 模块页面计数（当前系统下） */
function modulePageCount(moduleId: string): number {
  const sysId = activeSystemId.value
  return allPages.value.filter(
    (p) =>
      (p.systemId || 'sys_default') === sysId &&
      (p.moduleId || 'mod_default') === moduleId
  ).length
}

/** 过滤后的页面列表（按关键词搜索名称和编码） */
const filteredPages = computed(() => {
  const kw = pageSearchKeyword.value.trim().toLowerCase()
  if (!kw) return pages.value
  return pages.value.filter(
    (p) =>
      p.pageName.toLowerCase().includes(kw) ||
      p.pageId.toLowerCase().includes(kw)
  )
})

// ================ 弹窗状态 ================
const pageDialogVisible = ref(false)
const pageDialogMode = ref<'add' | 'edit' | 'copy'>('add')
const pageDialogCode = ref('')
const pageDialogName = ref('')
const pageDialogModuleId = ref('')
const pageDialogSystemId = ref('')
/** 页面业务类型（新增/编辑弹窗中） */
const pageDialogType = ref<PageType>('document')
const editingPageId = ref<string | null>(null)
const copySourcePageId = ref<string | null>(null)
const pageDialogError = ref('')

const moduleDialogVisible = ref(false)
const moduleDialogName = ref('')
const moduleDialogParentId = ref<string | undefined>(undefined)
const moduleDialogTitle = ref('新增模块')

/** 当前展开操作菜单的模块 ID（点击 ⋯ 按钮切换，避免 hover 误点） */
const moduleActionsOpenId = ref<string | null>(null)
/** 操作菜单浮层位置（屏幕坐标，配合 Teleport 使用） */
const moduleMenuPos = ref({ x: 0, y: 0 })

/** 当前展开操作菜单的模块对象（供 Teleport 菜单使用） */
const moduleActionsTarget = computed(() =>
  moduleActionsOpenId.value
    ? flatModules.value.find((m) => m.id === moduleActionsOpenId.value) || null
    : null
)

/** 点击 ⋯ 按钮展开/收起操作菜单（浮层定位到按钮下方，避免内联挤压） */
function openModuleMenu(m: FlatModule, event: MouseEvent) {
  if (moduleActionsOpenId.value === m.id) {
    moduleActionsOpenId.value = null
    return
  }
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  moduleMenuPos.value = {
    x: rect.right - 120,
    y: rect.bottom + 4,
  }
  moduleActionsOpenId.value = m.id
}

/** 页面类型选项 */
const pageTypeOptions: { label: string; value: PageType }[] = [
  { label: '单据', value: 'document' },
  { label: '基础资料', value: 'basedata' },
  { label: '自定义', value: 'custom' },
]

/** 页面类型 → 中文标签 */
function pageTypeLabel(t?: PageType): string {
  if (t === 'basedata') return '基础资料'
  if (t === 'custom') return '自定义'
  return '单据'
}

/** 根据页面类型计算设计器跳转 URL：custom 进默认设计器，单据/基础资料进业务设计器(mode=biz) */
function buildDesignerUrl(pageId: string, pageType?: PageType): string {
  const mode = pageType === 'custom' ? '' : 'biz'
  const pt = pageType ? `&pageType=${encodeURIComponent(pageType)}` : ''
  const query = `pageId=${encodeURIComponent(pageId)}${mode ? `&mode=${mode}` : ''}${pt}`
  return `${window.location.origin}${window.location.pathname}#/designer?${query}`
}

// ================ 刷新数据 ================
async function refreshSystems() {
  try {
    systems.value = await listSystems()
  } catch (e) {
    ElMessage.error(
      t('加载系统失败：') + (e instanceof Error ? e.message : String(e))
    )
    return
  }
  if (
    !activeSystemId.value ||
    !systems.value.find((s) => s.id === activeSystemId.value)
  ) {
    activeSystemId.value = systems.value[0]?.id || ''
  }
}

async function refreshModules() {
  try {
    modules.value = await listModules()
  } catch (e) {
    ElMessage.error(
      t('加载模块失败：') + (e instanceof Error ? e.message : String(e))
    )
    return
  }
  if (
    !activeModuleId.value ||
    !modules.value.find((m) => m.id === activeModuleId.value)
  ) {
    activeModuleId.value = modules.value[0]?.id || ''
  }
}

async function refreshAllPages() {
  try {
    allPages.value = await listSavedPages()
  } catch (e) {
    ElMessage.error(
      t('加载页面失败：') + (e instanceof Error ? e.message : String(e))
    )
  }
}

async function refreshAll() {
  await refreshSystems()
  await refreshModules()
  await refreshAllPages()
}

onMounted(() => {
  loadModule('page')
  refreshAll()
})

// ================ 模块操作 ================
function selectModule(id: string) {
  activeModuleId.value = id
  moduleActionsOpenId.value = null
}

function startEditModule(m: ModuleInfo) {
  moduleEditingId.value = m.id
  moduleEditName.value = m.name
  moduleActionsOpenId.value = null
}

async function confirmEditModule() {
  if (moduleEditingId.value && moduleEditName.value.trim()) {
    try {
      await renameModule(moduleEditingId.value, moduleEditName.value)
    } catch (e) {
      ElMessage.error(
        t('重命名失败：') + (e instanceof Error ? e.message : String(e))
      )
      return
    }
    moduleEditingId.value = null
    await refreshModules()
  }
}

function cancelEditModule() {
  moduleEditingId.value = null
}

async function removeModule(m: ModuleInfo) {
  if (m.id === 'mod_default') {
    alert(t('默认模块不能删除'))
    return
  }
  if (
    confirm(t('确认删除模块「') + m.name + t('」？其下页面将移至默认模块。'))
  ) {
    try {
      await deleteModule(m.id)
    } catch (e) {
      ElMessage.error(
        t('删除模块失败：') + (e instanceof Error ? e.message : String(e))
      )
      return
    }
    moduleActionsOpenId.value = null
    await refreshAll()
  }
}

function openAddModuleDialog() {
  moduleDialogTitle.value = '新增模块'
  moduleDialogName.value = ''
  moduleDialogParentId.value = undefined
  moduleDialogVisible.value = true
}

/** 新增同级模块 */
function openAddSiblingDialog(m: ModuleInfo) {
  moduleDialogTitle.value = '新增同级模块'
  moduleDialogName.value = ''
  moduleDialogParentId.value = m.parentId
  moduleActionsOpenId.value = null
  moduleDialogVisible.value = true
}

/** 新增子级模块 */
function openAddChildDialog(m: ModuleInfo) {
  moduleDialogTitle.value = `在「${m.name}」下新增子模块`
  moduleDialogName.value = ''
  moduleDialogParentId.value = m.id
  // 自动展开父节点
  if (!expandedSet.value.has(m.id)) toggleExpand(m.id)
  moduleActionsOpenId.value = null
  moduleDialogVisible.value = true
}

async function confirmAddModule() {
  const name = moduleDialogName.value.trim()
  if (!name) return
  try {
    await addModule(name, moduleDialogParentId.value)
  } catch (e) {
    ElMessage.error(
      t('新增模块失败：') + (e instanceof Error ? e.message : String(e))
    )
    return
  }
  moduleDialogVisible.value = false
  await refreshModules()
}

// ================ 页面操作 ================
const activeModuleName = computed(
  () => modules.value.find((m) => m.id === activeModuleId.value)?.name || ''
)

function openAddPageDialog() {
  pageDialogMode.value = 'add'
  pageDialogCode.value = ''
  pageDialogName.value = ''
  pageDialogSystemId.value = activeSystemId.value
  pageDialogModuleId.value = activeModuleId.value
  pageDialogType.value = 'document'
  editingPageId.value = null
  pageDialogError.value = ''
  pageDialogVisible.value = true
}

function openEditPageDialog(p: SavedPageMeta) {
  pageDialogMode.value = 'edit'
  pageDialogCode.value = p.pageId
  pageDialogName.value = p.pageName
  pageDialogSystemId.value = p.systemId || 'sys_default'
  pageDialogModuleId.value = p.moduleId || 'mod_default'
  pageDialogType.value = p.pageType || 'document'
  editingPageId.value = p.pageId
  pageDialogError.value = ''
  pageDialogVisible.value = true
}

async function confirmPageDialog() {
  const name = pageDialogName.value.trim()
  const code = pageDialogCode.value.trim()
  if (!name) {
    pageDialogError.value = '请输入页面名称'
    return
  }
  if (pageDialogMode.value === 'add') {
    if (!code) {
      pageDialogError.value = '请输入页面编码'
      return
    }
    if (allPages.value.some((p) => p.pageId === code)) {
      pageDialogError.value = `编码「${code}」已存在，请使用其他编码`
      return
    }
    const schema: PageSchema = {
      version: '1.0',
      pageId: code,
      pageName: name,
      systemId: pageDialogSystemId.value,
      moduleId: pageDialogModuleId.value,
      pageType: pageDialogType.value,
      pageCss: { background: '#ffffff' },
      root: [],
    }
    try {
      await savePage(schema)
    } catch (e) {
      pageDialogError.value =
        '保存失败：' + (e instanceof Error ? e.message : String(e))
      return
    }
    pageDialogVisible.value = false
    await refreshAllPages()
    const url = buildDesignerUrl(code, pageDialogType.value)
    window.open(url, '_blank')
  } else if (pageDialogMode.value === 'copy') {
    if (!code) {
      pageDialogError.value = '请输入页面编码'
      return
    }
    if (!copySourcePageId.value) return
    if (allPages.value.some((p) => p.pageId === code)) {
      pageDialogError.value = `编码「${code}」已存在，请使用其他编码`
      return
    }
    try {
      await copyPage(copySourcePageId.value, {
        newPageId: code,
        pageName: name,
        systemId: pageDialogSystemId.value,
        moduleId: pageDialogModuleId.value,
        pageType: pageDialogType.value,
      })
    } catch (e) {
      pageDialogError.value =
        '复制失败：' + (e instanceof Error ? e.message : String(e))
      return
    }
    pageDialogVisible.value = false
    await refreshAllPages()
  } else {
    if (!editingPageId.value) return
    const page = await loadPage(editingPageId.value)
    if (page) {
      page.pageName = name
      page.systemId = pageDialogSystemId.value
      page.moduleId = pageDialogModuleId.value
      page.pageType = pageDialogType.value
      try {
        await savePage(page)
      } catch (e) {
        pageDialogError.value =
          '保存失败：' + (e instanceof Error ? e.message : String(e))
        return
      }
      pageDialogVisible.value = false
      await refreshAllPages()
    }
  }
}

function editPage(p: SavedPageMeta) {
  const url = buildDesignerUrl(p.pageId, p.pageType)
  window.open(url, '_blank')
}

async function previewPage(p: SavedPageMeta) {
  const page = await loadPage(p.pageId)
  if (page) {
    sessionStorage.setItem(
      'lowcode-preview-data',
      JSON.stringify({
        pageId: page.pageId,
        pageName: page.pageName,
        tableName: page.tableName || '',
        pageCss: page.pageCss || { background: '#ffffff' },
        schema: page.root,
      })
    )
  }
  window.open(
    `${window.location.origin}${window.location.pathname}#/preview`,
    '_blank'
  )
}

async function removePage(p: SavedPageMeta) {
  if (
    confirm(
      t('确认删除页面？\n\n名称：') +
        p.pageName +
        t('\n编码：') +
        p.pageId +
        t('\n\n删除后可在回收站恢复。')
    )
  ) {
    try {
      await deletePage(p.pageId)
    } catch (e) {
      ElMessage.error(
        t('删除失败：') + (e instanceof Error ? e.message : String(e))
      )
      return
    }
    await refreshAllPages()
  }
}

function copyPageHandler(p: SavedPageMeta) {
  pageDialogMode.value = 'copy'
  pageDialogCode.value = genPageId()
  pageDialogName.value = `${p.pageName}_副本`
  pageDialogSystemId.value = p.systemId || activeSystemId.value
  pageDialogModuleId.value = p.moduleId || activeModuleId.value
  pageDialogType.value = p.pageType || 'document'
  copySourcePageId.value = p.pageId
  editingPageId.value = null
  pageDialogError.value = ''
  pageDialogVisible.value = true
}

async function toggleEnabled(p: SavedPageMeta) {
  const action = p.enabled ? '禁用' : '启用'
  if (
    confirm(
      t('确认') +
        action +
        t('页面？\n\n名称：') +
        p.pageName +
        t('\n编码：') +
        p.pageId
    )
  ) {
    try {
      await setPageEnabled(p.pageId, !p.enabled)
    } catch (e) {
      ElMessage.error(
        action + t('失败：') + (e instanceof Error ? e.message : String(e))
      )
      return
    }
    await refreshAllPages()
  }
}

// ================ 回收站 ================
const trashDialogVisible = ref(false)
const deletedPages = ref<SavedPageMeta[]>([])
const trashSearchKeyword = ref('')

/** 回收站中过滤后的页面列表 */
const filteredDeletedPages = computed(() => {
  const kw = trashSearchKeyword.value.trim().toLowerCase()
  if (!kw) return deletedPages.value
  return deletedPages.value.filter(
    (p) =>
      p.pageName.toLowerCase().includes(kw) ||
      p.pageId.toLowerCase().includes(kw)
  )
})

async function openTrashDialog() {
  try {
    deletedPages.value = await listDeletedPages()
  } catch (e) {
    ElMessage.error(
      t('加载回收站失败：') + (e instanceof Error ? e.message : String(e))
    )
    return
  }
  trashSearchKeyword.value = ''
  trashDialogVisible.value = true
}

async function restorePageHandler(p: SavedPageMeta) {
  if (
    confirm(
      t('确认恢复页面？\n\n名称：') + p.pageName + t('\n编码：') + p.pageId
    )
  ) {
    try {
      await restorePage(p.pageId)
    } catch (e) {
      ElMessage.error(
        t('恢复失败：') + (e instanceof Error ? e.message : String(e))
      )
      return
    }
    deletedPages.value = await listDeletedPages()
    await refreshAllPages()
  }
}

async function hardDeletePageHandler(p: SavedPageMeta) {
  if (
    confirm(
      t('确认彻底删除页面？此操作不可恢复。\n\n名称：') +
        p.pageName +
        t('\n编码：') +
        p.pageId
    )
  ) {
    try {
      await hardDeletePage(p.pageId)
    } catch (e) {
      ElMessage.error(
        t('删除失败：') + (e instanceof Error ? e.message : String(e))
      )
      return
    }
    deletedPages.value = await listDeletedPages()
  }
}

function formatTime(iso: string): string {
  try {
    const d = new Date(iso)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(
      2,
      '0'
    )}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(
      2,
      '0'
    )}:${String(d.getMinutes()).padStart(2, '0')}`
  } catch {
    return iso
  }
}
</script>

<template>
  <div class="page-manage">
    <!-- 顶栏 -->
    <header class="pm-header">
      <div class="pm-header-left">
        <h1 class="pm-title">{{ t('页面管理') }}</h1>
      </div>
      <div class="pm-header-center">
        <button class="pm-system-trigger" @click="systemPickerVisible = true">
          <svg
            class="pm-system-trigger-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <rect x="2" y="3" width="20" height="14" rx="2" />
            <line x1="8" y1="21" x2="16" y2="21" />
            <line x1="12" y1="17" x2="12" y2="21" />
          </svg>
          <span class="pm-system-trigger-name">{{ activeSystemName }}</span>
          <svg
            class="pm-system-trigger-arrow"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="2"
          >
            <polyline points="6 9 12 15 18 9" />
          </svg>
        </button>
      </div>

      <div class="pm-header-right">
        <button class="pm-btn" @click="router.push('/api-manage')">
          {{ t('API 服务管理') }}
        </button>
      </div>
    </header>

    <div class="pm-body">
      <!-- 左侧：模块列表 -->
      <aside class="pm-sidebar">
        <!-- 模块列表 -->
        <div class="pm-sidebar-header">
          <span>{{ t('模块') }}</span>
          <button class="pm-btn pm-btn-sm" @click="openAddModuleDialog">
            {{ t('+ 新增') }}
          </button>
        </div>
        <ul class="pm-module-list">
          <li
            v-for="m in flatModules"
            :key="m.id"
            class="pm-module-item"
            :class="{ active: m.id === activeModuleId }"
            :style="{ paddingLeft: `${16 + m.depth * 20}px` }"
            @click="selectModule(m.id)"
          >
            <template v-if="moduleEditingId === m.id">
              <input
                class="pm-module-edit-input"
                v-model="moduleEditName"
                @keyup.enter="confirmEditModule"
                @keyup.esc="cancelEditModule"
                @blur="confirmEditModule"
                autofocus
              />
            </template>
            <template v-else>
              <span
                v-if="m.hasChildren"
                class="pm-module-toggle"
                @click.stop="toggleExpand(m.id)"
              >
                {{ m.expanded ? '▾' : '▸' }}
              </span>
              <span
                v-else
                class="pm-module-toggle pm-toggle-placeholder"
              ></span>
              <span
                class="pm-module-name"
                :title="m.name"
                @dblclick="startEditModule(m)"
                >{{ m.name }}</span
              >
              <span class="pm-module-count">{{ modulePageCount(m.id) }}</span>
              <button
                class="pm-module-more-btn"
                :class="{ active: moduleActionsOpenId === m.id }"
                :title="t('操作')"
                @click.stop="openModuleMenu(m, $event)"
              >
                ⋮
              </button>
            </template>
          </li>
        </ul>
      </aside>

      <!-- 右侧：页面卡片 -->
      <main class="pm-main">
        <div class="pm-main-header">
          <h2 class="pm-module-title">{{ activeModuleName }}</h2>
          <div class="pm-main-actions">
            <div class="pm-search-wrap">
              <span class="pm-search-icon"><SearchIcon :size="16" /></span>
              <input
                class="pm-search-input"
                v-model="pageSearchKeyword"
                :placeholder="t('搜索页面名称或编码')"
              />
            </div>
            <button class="pm-btn pm-btn-primary" @click="openAddPageDialog">
              {{ t('+ 新增页面') }}
            </button>
            <button class="pm-btn" @click="openTrashDialog">
              {{ t('回收站') }}
            </button>
          </div>
        </div>

        <div v-if="filteredPages.length === 0" class="pm-empty">
          <p>
            {{
              pages.length === 0
                ? t('暂无页面，点击「新增页面」创建')
                : t('未找到匹配的页面')
            }}
          </p>
        </div>

        <div v-else class="pm-card-grid">
          <div
            v-for="p in filteredPages"
            :key="p.pageId"
            class="pm-card"
            :class="{ 'pm-card-disabled': !p.enabled }"
            :title="t('双击编辑')"
            @dblclick="editPage(p)"
          >
            <div class="pm-card-top">
              <div class="pm-card-avatar">
                {{ (p.pageName || '?').charAt(0) }}
              </div>
              <div class="pm-card-info">
                <span class="pm-card-name">{{ p.pageName }}</span>
                <span class="pm-card-id">{{ p.pageId }}</span>
              </div>
              <button
                class="pm-card-status"
                :class="p.enabled ? 'pm-status-on' : 'pm-status-off'"
                @click.stop="toggleEnabled(p)"
              >
                {{ p.enabled ? t('启用') : t('禁用') }}
              </button>
            </div>
            <div class="pm-card-meta">
              <span
                class="pm-card-type"
                :class="
                  p.pageType === 'basedata'
                    ? 'pm-type-basedata'
                    : p.pageType === 'custom'
                    ? 'pm-type-custom'
                    : 'pm-type-document'
                "
                >{{ pageTypeLabel(p.pageType) }}</span
              >
              <span class="pm-card-time"
                >{{ t('更新于') }} {{ formatTime(p.savedAt) }}</span
              >
            </div>
            <div class="pm-card-actions">
              <button
                class="pm-btn pm-btn-sm pm-btn-primary"
                @click="editPage(p)"
              >
                {{ t('编辑') }}
              </button>
              <button class="pm-btn pm-btn-sm" @click="previewPage(p)">
                {{ t('预览') }}
              </button>
              <button class="pm-btn pm-btn-sm" @click="copyPageHandler(p)">
                {{ t('复制') }}
              </button>
              <button class="pm-btn pm-btn-sm" @click="openEditPageDialog(p)">
                {{ t('设置') }}
              </button>
              <button
                class="pm-btn pm-btn-sm pm-btn-danger"
                @click="removePage(p)"
              >
                {{ t('删除') }}
              </button>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- 新增/编辑页面弹窗 -->
    <Teleport to="body">
      <div
        v-if="pageDialogVisible"
        class="pm-dialog-overlay"
        @click.self="pageDialogVisible = false"
      >
        <div class="pm-dialog">
          <h3 class="pm-dialog-title">
            {{
              pageDialogMode === 'add'
                ? t('新增页面')
                : pageDialogMode === 'copy'
                ? t('复制页面')
                : t('编辑页面')
            }}
          </h3>
          <div class="pm-dialog-body">
            <label class="pm-dialog-label pm-required">{{
              t('页面编码')
            }}</label>
            <DxTextBox
              :value="pageDialogCode"
              :placeholder="t('页面唯一标识，如 page_order_list')"
              :read-only="pageDialogMode === 'edit'"
              @value-changed="(e: any) => (pageDialogCode = e.value)"
            />
            <label class="pm-dialog-label pm-required">{{
              t('页面名称')
            }}</label>
            <DxTextBox
              :value="pageDialogName"
              :placeholder="t('请输入页面名称')"
              @value-changed="(e: any) => (pageDialogName = e.value)"
              @enter-key="confirmPageDialog"
            />
            <label class="pm-dialog-label">{{ t('类型') }}</label>
            <DxSelectBox
              :value="pageDialogType"
              :data-source="pageTypeOptions"
              display-expr="label"
              value-expr="value"
              @value-changed="(e: any) => (pageDialogType = e.value)"
            />
            <label class="pm-dialog-label pm-required">{{
              t('所属系统')
            }}</label>
            <DxSelectBox
              :value="pageDialogSystemId"
              :data-source="systems"
              display-expr="name"
              value-expr="id"
              @value-changed="(e: any) => (pageDialogSystemId = e.value)"
            />
            <label class="pm-dialog-label pm-required">{{
              t('所属模块')
            }}</label>
            <DxSelectBox
              :value="pageDialogModuleId"
              :data-source="modules"
              display-expr="name"
              value-expr="id"
              @value-changed="(e: any) => (pageDialogModuleId = e.value)"
            />
            <div v-if="pageDialogError" class="pm-dialog-error">
              {{ pageDialogError }}
            </div>
          </div>
          <div class="pm-dialog-footer">
            <DxButton :text="t('取消')" @click="pageDialogVisible = false" />
            <DxButton
              :text="t('确定')"
              type="default"
              @click="confirmPageDialog"
            />
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 新增模块弹窗 -->
    <Teleport to="body">
      <div
        v-if="moduleDialogVisible"
        class="pm-dialog-overlay"
        @click.self="moduleDialogVisible = false"
      >
        <div class="pm-dialog pm-dialog-sm">
          <h3 class="pm-dialog-title">{{ moduleDialogTitle }}</h3>
          <div class="pm-dialog-body">
            <label class="pm-dialog-label pm-required">{{
              t('模块名称')
            }}</label>
            <DxTextBox
              :value="moduleDialogName"
              :placeholder="t('请输入模块名称')"
              @value-changed="(e: any) => (moduleDialogName = e.value)"
              @enter-key="confirmAddModule"
            />
          </div>
          <div class="pm-dialog-footer">
            <DxButton :text="t('取消')" @click="moduleDialogVisible = false" />
            <DxButton
              :text="t('确定')"
              type="default"
              @click="confirmAddModule"
            />
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 回收站弹窗 -->
    <Teleport to="body">
      <div
        v-if="trashDialogVisible"
        class="pm-dialog-overlay"
        @click.self="trashDialogVisible = false"
      >
        <div class="pm-dialog pm-dialog-trash">
          <h3 class="pm-dialog-title">{{ t('回收站') }}</h3>
          <div class="pm-dialog-body pm-trash-body">
            <div v-if="deletedPages.length > 0" class="pm-trash-search-wrap">
              <span class="pm-search-icon"><SearchIcon :size="16" /></span>
              <input
                class="pm-trash-search-input"
                v-model="trashSearchKeyword"
                :placeholder="t('搜索页面名称或编码')"
              />
            </div>
            <div v-if="deletedPages.length === 0" class="pm-empty">
              <p>{{ t('回收站为空') }}</p>
            </div>
            <div v-else-if="filteredDeletedPages.length === 0" class="pm-empty">
              <p>{{ t('未找到匹配的页面') }}</p>
            </div>
            <ul v-else class="pm-trash-list">
              <li
                v-for="p in filteredDeletedPages"
                :key="p.pageId"
                class="pm-trash-item"
              >
                <div class="pm-trash-info">
                  <span class="pm-trash-name">{{ p.pageName }}</span>
                  <span class="pm-trash-id">{{ p.pageId }}</span>
                  <span class="pm-trash-time">
                    {{ t('删除于') }} {{ formatTime(p.deletedAt || p.savedAt) }}
                  </span>
                </div>
                <div class="pm-trash-actions">
                  <button
                    class="pm-btn pm-btn-sm pm-btn-success"
                    @click="restorePageHandler(p)"
                  >
                    {{ t('恢复') }}
                  </button>
                  <button
                    class="pm-btn pm-btn-sm pm-btn-danger"
                    @click="hardDeletePageHandler(p)"
                  >
                    {{ t('彻底删除') }}
                  </button>
                </div>
              </li>
            </ul>
          </div>
          <div class="pm-dialog-footer">
            <button class="pm-btn" @click="trashDialogVisible = false">
              {{ t('关闭') }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 系统选择弹窗 -->
    <Teleport to="body">
      <div
        v-if="systemPickerVisible"
        class="pm-sys-picker-overlay"
        @click.self="systemPickerVisible = false"
      >
        <div class="pm-sys-picker-dialog">
          <div class="pm-sys-picker-header">
            <span class="pm-sys-picker-title">{{ t('选择系统') }}</span>
            <button
              class="pm-sys-picker-close"
              @click="systemPickerVisible = false"
            >
              ✕
            </button>
          </div>
          <div class="pm-sys-picker-search">
            <span class="pm-sys-picker-search-icon"
              ><SearchIcon :size="18"
            /></span>
            <input
              v-model="systemSearchKeyword"
              class="pm-sys-picker-search-input"
              :placeholder="t('搜索系统名称或编码')"
            />
          </div>
          <div class="pm-sys-picker-body">
            <div
              v-for="s in filteredSystems"
              :key="s.id"
              class="pm-sys-card"
              :class="{ selected: s.id === activeSystemId }"
              @click="selectSystem(s.id)"
            >
              <div class="pm-sys-card-icon">
                <svg
                  viewBox="0 0 24 24"
                  fill="none"
                  stroke="currentColor"
                  stroke-width="2"
                >
                  <rect x="2" y="3" width="20" height="14" rx="2" />
                  <line x1="8" y1="21" x2="16" y2="21" />
                  <line x1="12" y1="17" x2="12" y2="21" />
                </svg>
              </div>
              <div class="pm-sys-card-info">
                <span class="pm-sys-card-name">{{ s.name }}</span>
                <span class="pm-sys-card-id">{{ s.id }}</span>
              </div>
              <svg
                v-if="s.id === activeSystemId"
                class="pm-sys-card-check"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="3"
              >
                <polyline points="20 6 9 17 4 12" />
              </svg>
            </div>
            <div
              v-if="filteredSystems.length === 0"
              class="pm-sys-picker-empty"
            >
              {{ t('未找到匹配的系统') }}
            </div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 模块操作下拉菜单（Teleport 浮层，避免内联挤压和 overflow 截断） -->
    <Teleport to="body">
      <div
        v-if="moduleActionsTarget"
        class="pm-module-menu-overlay"
        @click="moduleActionsOpenId = null"
      >
        <div
          class="pm-module-menu"
          :style="{
            top: moduleMenuPos.y + 'px',
            left: moduleMenuPos.x + 'px',
          }"
          @click.stop
        >
          <button
            class="pm-menu-item"
            @click="
              moduleActionsTarget && openAddSiblingDialog(moduleActionsTarget)
            "
          >
            <span class="pm-menu-icon">↔</span>{{ t('新增同级') }}
          </button>
          <button
            class="pm-menu-item"
            @click="
              moduleActionsTarget && openAddChildDialog(moduleActionsTarget)
            "
          >
            <span class="pm-menu-icon">↕</span>{{ t('新增子级') }}
          </button>
          <button
            class="pm-menu-item"
            @click="moduleActionsTarget && startEditModule(moduleActionsTarget)"
          >
            <span class="pm-menu-icon">✎</span>{{ t('重命名') }}
          </button>
          <button
            v-if="moduleActionsTarget.id !== 'mod_default'"
            class="pm-menu-item pm-menu-danger"
            @click="moduleActionsTarget && removeModule(moduleActionsTarget)"
          >
            <span class="pm-menu-icon">✕</span>{{ t('删除') }}
          </button>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.page-manage {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f7fa;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

/* 顶栏 */
.pm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #eef0f4;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  flex-shrink: 0;
  z-index: 10;
}
.pm-header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}
.pm-header-center {
  display: flex;
  align-items: center;
}
.pm-system-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid #d1d5db;
  background: #fff;
  border-radius: 6px;
  padding: 6px 14px;
  font-size: 13px;
  color: #374151;
  cursor: pointer;
  transition: border-color 0.15s;
}
.pm-system-trigger:hover {
  border-color: #3b82f6;
}
.pm-system-trigger-icon {
  width: 16px;
  height: 16px;
  color: #6b7280;
  flex-shrink: 0;
}
.pm-system-trigger-name {
  font-weight: 500;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pm-system-trigger-arrow {
  width: 14px;
  height: 14px;
  color: #9ca3af;
  flex-shrink: 0;
}
.pm-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pm-title {
  font-size: 19px;
  font-weight: 700;
  color: #111827;
  margin: 0;
  letter-spacing: -0.3px;
}

/* 主体 */
.pm-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 左侧模块栏 */
.pm-sidebar {
  width: 240px;
  background: #fff;
  border-right: 1px solid #e8e8e8;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}

.pm-sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 8px;
  font-size: 13px;
  font-weight: 600;
  color: #6b7280;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.pm-module-list {
  list-style: none;
  margin: 0;
  padding: 0;
  flex: 1;
  overflow-y: auto;
}
.pm-module-item {
  display: flex;
  align-items: center;
  padding: 8px 16px;
  cursor: pointer;
  gap: 4px;
  transition: background 0.15s;
}
.pm-module-toggle {
  width: 16px;
  text-align: center;
  font-size: 12px;
  color: #6b7280;
  cursor: pointer;
  flex-shrink: 0;
}
.pm-toggle-placeholder {
  cursor: default;
}
.pm-module-item:hover {
  background: #f3f4f6;
}
.pm-module-item.active {
  background: #eff6ff;
  color: #2563eb;
  font-weight: 600;
  box-shadow: inset 3px 0 0 #2563eb;
}
.pm-module-name {
  flex: 1;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pm-module-count {
  font-size: 12px;
  color: #9ca3af;
  background: #f3f4f6;
  border-radius: 10px;
  padding: 1px 8px;
}
.pm-module-item.active .pm-module-count {
  background: #bfdbfe;
  color: #2563eb;
}
.pm-module-more-btn {
  border: none;
  background: none;
  cursor: pointer;
  font-size: 14px;
  font-weight: 700;
  color: #9ca3af;
  padding: 4px 8px;
  border-radius: 6px;
  line-height: 1;
  flex-shrink: 0;
  transition: background 0.15s, color 0.15s;
}
.pm-module-more-btn:hover,
.pm-module-more-btn.active {
  background: #eef0f4;
  color: #2563eb;
}
.pm-icon-btn {
  border: none;
  background: none;
  cursor: pointer;
  font-size: 13px;
  color: #9ca3af;
  padding: 2px 6px;
  border-radius: 4px;
}
.pm-icon-btn:hover {
  background: #e5e7eb;
  color: #374151;
}
.pm-icon-danger:hover {
  color: #ef4444;
}
.pm-module-edit-input {
  flex: 1;
  font-size: 14px;
  padding: 2px 6px;
  border: 1px solid #2563eb;
  border-radius: 4px;
  outline: none;
}

/* 右侧主区域 */
.pm-main {
  flex: 1;
  overflow-y: auto;
  padding: 28px 36px;
}
.pm-main-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
}
.pm-module-title {
  font-size: 22px;
  font-weight: 700;
  color: #111827;
  margin: 0;
  letter-spacing: -0.3px;
}
.pm-main-actions {
  display: flex;
  gap: 12px;
  align-items: center;
}
.pm-search-wrap {
  position: relative;
  display: flex;
  align-items: center;
}
.pm-search-icon {
  position: absolute;
  left: 10px;
  display: flex;
  align-items: center;
  color: #9ca3af;
  pointer-events: none;
}
.pm-search-input {
  padding: 7px 12px 7px 32px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 13px;
  width: 240px;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.pm-search-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}
.pm-empty {
  text-align: center;
  padding: 80px 0;
  color: #9ca3af;
  font-size: 15px;
}

/* 页面卡片网格 */
.pm-card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}
.pm-card {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #eef0f4;
  padding: 18px;
  transition: box-shadow 0.2s, border-color 0.2s, transform 0.2s;
  display: flex;
  flex-direction: column;
  gap: 14px;
  cursor: pointer;
}
.pm-card:hover {
  box-shadow: 0 6px 20px rgba(37, 99, 235, 0.1);
  border-color: #bfdbfe;
  transform: translateY(-2px);
}
.pm-card-top {
  display: flex;
  align-items: center;
  gap: 12px;
}
.pm-card-avatar {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: #10b981;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.pm-card-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}
.pm-card-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pm-card-id {
  font-size: 12px;
  color: #9ca3af;
  font-family: 'Consolas', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pm-card-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}
.pm-card-type {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 10px;
  flex-shrink: 0;
}
.pm-type-document {
  background: #dbeafe;
  color: #2563eb;
}
.pm-type-basedata {
  background: #dcfce7;
  color: #16a34a;
}
.pm-type-custom {
  background: #fef3c7;
  color: #d97706;
}
.pm-card-time {
  font-size: 12px;
  color: #9ca3af;
}
.pm-card-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  padding-top: 12px;
  border-top: 1px solid #f3f4f6;
}
.pm-card-disabled {
  background: #fffbeb;
  border-color: #fde68a;
}
.pm-card-disabled .pm-card-avatar {
  background: #f59e0b;
}
.pm-card-status {
  font-size: 11px;
  padding: 3px 10px;
  border-radius: 11px;
  font-weight: 500;
  flex-shrink: 0;
  margin-left: auto;
  cursor: pointer;
  border: none;
  transition: opacity 0.15s, transform 0.15s;
}
.pm-card-status:hover {
  opacity: 0.8;
  transform: scale(1.05);
}
.pm-status-on {
  background: #dcfce7;
  color: #16a34a;
}
.pm-status-off {
  background: #fef3c7;
  color: #d97706;
}

/* 按钮 */
.pm-btn {
  border: 1px solid #d1d5db;
  background: #fff;
  color: #374151;
  padding: 7px 14px;
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}
.pm-btn:hover {
  background: #f3f4f6;
  border-color: #9ca3af;
}
.pm-btn-sm {
  padding: 5px 11px;
  font-size: 12px;
}
.pm-btn-primary {
  background: #3b82f6;
  border-color: transparent;
  color: #fff;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.25);
}
.pm-btn-primary:hover {
  background: #2563eb;
  box-shadow: 0 3px 10px rgba(59, 130, 246, 0.35);
}
.pm-btn-danger {
  color: #ef4444;
  border-color: #fca5a5;
}
.pm-btn-danger:hover {
  background: #fef2f2;
  border-color: #ef4444;
}
.pm-btn-warning {
  color: #d97706;
  border-color: #fcd34d;
}
.pm-btn-warning:hover {
  background: #fffbeb;
  border-color: #d97706;
}
.pm-btn-success {
  color: #16a34a;
  border-color: #86efac;
}
.pm-btn-success:hover {
  background: #f0fdf4;
  border-color: #16a34a;
}
.pm-btn-text {
  border: none;
  background: none;
  color: #6b7280;
}
.pm-btn-text:hover {
  background: #f3f4f6;
  color: #374151;
}

/* 弹窗 */
.pm-dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.pm-dialog {
  background: #fff;
  border-radius: 8px;
  width: 420px;
  max-width: 95vw;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
}
.pm-dialog-sm {
  width: 360px;
}
.pm-dialog-title {
  margin: 0;
  padding: 16px 20px;
  font-size: 16px;
  font-weight: 600;
  border-bottom: 1px solid #e8e8e8;
}
.pm-dialog-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.pm-dialog-label {
  font-size: 13px;
  color: #6b7280;
  font-weight: 500;
}
.pm-required::before {
  content: '*';
  color: #ef4444;
  margin-right: 4px;
  font-weight: 700;
}
.pm-dialog-input,
.pm-dialog-select {
  padding: 8px 12px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  font-size: 14px;
  outline: none;
}
.pm-dialog-input:focus,
.pm-dialog-select:focus {
  border-color: #2563eb;
}
.pm-dialog-input:disabled {
  background: #f3f4f6;
  color: #9ca3af;
}
.pm-dialog-error {
  color: #ef4444;
  font-size: 13px;
  margin-top: 4px;
}
.pm-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 12px 20px;
  border-top: 1px solid #e8e8e8;
}

/* 系统选择弹窗 */
.pm-sys-picker-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  z-index: 20000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.pm-sys-picker-dialog {
  background: #fff;
  border-radius: 8px;
  width: 900px;
  max-width: 94vw;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 6px 24px rgba(0, 0, 0, 0.12);
  overflow: hidden;
}
.pm-sys-picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 24px 32px;
  border-bottom: 1px solid #eee;
}
.pm-sys-picker-title {
  font-size: 18px;
  font-weight: 600;
  color: #111827;
}
.pm-sys-picker-close {
  border: none;
  background: none;
  font-size: 16px;
  color: #9ca3af;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
}
.pm-sys-picker-close:hover {
  background: #f3f4f6;
  color: #374151;
}
.pm-sys-picker-search {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 16px 32px;
  border-bottom: 1px solid #eee;
}
.pm-sys-picker-search-icon {
  display: flex;
  align-items: center;
  color: #9ca3af;
}
.pm-sys-picker-search-input {
  width: 280px;
  border: 1px solid #d1d5db;
  padding: 8px 14px;
  font-size: 14px;
  outline: none;
  background: #fff;
  color: #111827;
}
.pm-sys-picker-search-input:focus {
  border-color: #3b82f6;
}
.pm-sys-picker-body {
  padding: 28px 32px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  grid-auto-rows: min-content;
  align-content: start;
  gap: 20px;
  flex: 1;
  overflow-y: auto;
}
.pm-sys-picker-empty {
  grid-column: 1 / -1;
  text-align: center;
  padding: 48px 0;
  color: #9ca3af;
  font-size: 15px;
}
.pm-sys-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 24px;
  min-height: 120px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
  background: #fff;
}
.pm-sys-card:hover {
  border-color: #93c5fd;
  background: #f8fafc;
}
.pm-sys-card.selected {
  border-color: #3b82f6;
  background: #eff6ff;
}
.pm-sys-card-icon {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  background: #e0e7ef;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.pm-sys-card-icon svg {
  width: 24px;
  height: 24px;
  color: #4b5563;
}
.pm-sys-card.selected .pm-sys-card-icon {
  background: #3b82f6;
}
.pm-sys-card.selected .pm-sys-card-icon svg {
  color: #fff;
}
.pm-sys-card-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  flex: 1;
  min-width: 0;
}
.pm-sys-card-name {
  font-size: 15px;
  font-weight: 500;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pm-sys-card-id {
  font-size: 12px;
  color: #9ca3af;
}
.pm-sys-card-check {
  width: 18px;
  height: 18px;
  color: #3b82f6;
  flex-shrink: 0;
}

/* 回收站弹窗 */
.pm-dialog-trash {
  width: 560px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}
.pm-trash-body {
  padding: 0;
  overflow-y: auto;
  flex: 1;
}
.pm-trash-search-wrap {
  position: relative;
  display: flex;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid #f3f4f6;
  flex-shrink: 0;
}
.pm-trash-search-input {
  padding: 7px 12px 7px 32px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font-size: 13px;
  width: 100%;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.pm-trash-search-input:focus {
  border-color: #3b82f6;
  box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.1);
}
.pm-trash-list {
  list-style: none;
  margin: 0;
  padding: 0;
}
.pm-trash-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid #f3f4f6;
  gap: 12px;
}
.pm-trash-item:hover {
  background: #f9fafb;
}
.pm-trash-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}
.pm-trash-name {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pm-trash-id {
  font-size: 12px;
  color: #9ca3af;
  font-family: 'Consolas', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pm-trash-time {
  font-size: 12px;
  color: #d97706;
}
.pm-trash-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* 模块操作下拉浮层菜单 */
.pm-module-menu-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
}
.pm-module-menu {
  position: fixed;
  width: 120px;
  background: #fff;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.12);
  padding: 4px 0;
  z-index: 10000;
}
.pm-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  border: none;
  background: none;
  cursor: pointer;
  padding: 8px 14px;
  font-size: 13px;
  color: #374151;
  transition: background 0.12s, color 0.12s;
}
.pm-menu-item:hover {
  background: #f3f4f6;
}
.pm-menu-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  font-size: 13px;
  color: #6b7280;
}
.pm-menu-danger {
  color: #ef4444;
}
.pm-menu-danger:hover {
  background: #fef2f2;
}
.pm-menu-danger .pm-menu-icon {
  color: #ef4444;
}
</style>