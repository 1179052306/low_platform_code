<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import DxTextBox from 'devextreme-vue/text-box'
import DxButton from 'devextreme-vue/button'
import { httpRequest } from '@/utils/request'
import { t, loadModule, type LangInfo } from '@/store/lang'
import {
  listSystems,
  listModules,
  listSavedPages,
  loadPage,
  type SystemInfo,
  type ModuleInfo,
  type SavedPageMeta,
} from '@/store/persistence'
import type { ComponentSchema } from '@/types/schema'
import { ElMessage } from 'element-plus'

defineOptions({ name: 'PageLangManageView' })

interface LangTextRow {
  id?: number
  locale: string
  textKey: string
  textVal: string
  module: string
  updatedAt?: string
}

// ================ 语言 ================
const langs = ref<LangInfo[]>([])
const selectedLocale = ref<string>('')
const isZh = computed(() => selectedLocale.value === 'zh-CN')
const selectedLangName = computed(
  () => langs.value.find((l) => l.locale === selectedLocale.value)?.name ?? ''
)

async function loadLangs() {
  try {
    const res = await httpRequest('/api/lowcode/lang/list-all', {
      method: 'POST',
    })
    const body = res.data as { res?: boolean; data?: LangInfo[] }
    if (body && body.res !== false && body.data) {
      langs.value = body.data
      if (!selectedLocale.value && body.data.length > 0) {
        const firstNonZh = body.data.find((l) => l.locale !== 'zh-CN')
        selectedLocale.value = firstNonZh?.locale ?? body.data[0].locale
      }
    }
  } catch (e) {
    console.error('[page-lang] loadLangs failed:', e)
  }
}

function onLangSelect(locale: string) {
  selectedLocale.value = locale
}

// ================ 系统/模块/页面 ================
const systems = ref<SystemInfo[]>([])
const modules = ref<ModuleInfo[]>([])
const allPages = ref<SavedPageMeta[]>([])
const activeSystemId = ref<string>('')
const activeModuleId = ref<string>('')
const expandedSet = ref<Set<string>>(new Set())

const activeSystemName = computed(
  () => systems.value.find((s) => s.id === activeSystemId.value)?.name ?? ''
)

async function refreshSystems() {
  try {
    systems.value = await listSystems()
    if (!activeSystemId.value && systems.value.length > 0) {
      activeSystemId.value = systems.value[0].id
    }
  } catch (e) {
    console.error('[page-lang] refreshSystems failed:', e)
  }
}

async function refreshModules() {
  try {
    modules.value = await listModules()
  } catch (e) {
    console.error('[page-lang] refreshModules failed:', e)
  }
}

async function refreshPages() {
  try {
    allPages.value = await listSavedPages()
  } catch (e) {
    console.error('[page-lang] refreshPages failed:', e)
  }
}

// 模块树扁平化
interface FlatModule extends ModuleInfo {
  depth: number
  hasChildren: boolean
  expanded: boolean
}

const flatModules = computed<FlatModule[]>(() => {
  const childrenMap = new Map<string, ModuleInfo[]>()
  const roots: ModuleInfo[] = []
  for (const m of modules.value) {
    if (m.parentId) {
      const children = childrenMap.get(m.parentId) || []
      children.push(m)
      childrenMap.set(m.parentId, children)
    } else {
      roots.push(m)
    }
  }
  const result: FlatModule[] = []
  const walk = (mods: ModuleInfo[], depth: number) => {
    for (const m of mods) {
      const children = childrenMap.get(m.id) || []
      result.push({
        ...m,
        depth,
        hasChildren: children.length > 0,
        expanded: expandedSet.value.has(m.id),
      })
      if (children.length > 0 && expandedSet.value.has(m.id)) {
        walk(children, depth + 1)
      }
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
}

function modulePageCount(moduleId: string): number {
  const sysId = activeSystemId.value
  return allPages.value.filter(
    (p) =>
      (p.systemId || 'sys_default') === sysId &&
      (p.moduleId || 'mod_default') === moduleId
  ).length
}

// 当前系统+模块的页面列表
const pages = computed(() => {
  const sysId = activeSystemId.value
  const moduleId = activeModuleId.value
  if (!moduleId) return []
  return allPages.value.filter(
    (p) =>
      (p.systemId || 'sys_default') === sysId &&
      (p.moduleId || 'mod_default') === moduleId
  )
})

// ================ 页面翻译编辑 ================
const selectedPage = ref<SavedPageMeta | null>(null)
const pageTexts = ref<
  { textKey: string; textVal: string; module: string; exists: boolean }[]
>([])
const pageTextLoading = ref(false)
const autoTranslating = ref(false)
const existingTextMap = ref<Map<string, LangTextRow>>(new Map())

function scanChineseText(node: ComponentSchema, results: Set<string>) {
  if (!node || !node.props) return
  const TEXT_KEYS = [
    'text',
    'placeholder',
    'title',
    'hint',
    'label',
    'summaryText',
    'emptyPanelText',
  ]
  for (const key of TEXT_KEYS) {
    const val = node.props[key]
    if (typeof val === 'string' && /[\u4e00-\u9fff]/.test(val)) {
      results.add(val)
    }
  }
  const columns = node.props.columns
  if (Array.isArray(columns)) {
    for (const col of columns as Record<string, unknown>[]) {
      if (
        typeof col.caption === 'string' &&
        /[\u4e00-\u9fff]/.test(col.caption)
      ) {
        results.add(col.caption)
      }
    }
  }
  if (node.children) {
    for (const child of node.children) {
      scanChineseText(child, results)
    }
  }
}

async function selectPage(page: SavedPageMeta) {
  selectedPage.value = page
  if (isZh.value) return

  pageTextLoading.value = true
  try {
    const schema = await loadPage(page.pageId)
    if (!schema || !schema.root) {
      ElMessage.error(t('加载页面数据失败'))
      return
    }

    const chineseSet = new Set<string>()
    for (const node of schema.root) {
      scanChineseText(node, chineseSet)
    }

    const moduleName = schema.pageName || page.pageId
    pageTexts.value = Array.from(chineseSet)
      .sort()
      .map((text) => {
        const existing = existingTextMap.value.get(text)
        return {
          textKey: text,
          textVal: existing?.textVal ?? '',
          module: existing?.module ?? moduleName,
          exists: !!existing,
        }
      })

    if (pageTexts.value.length === 0) {
      ElMessage.info(t('该页面未找到中文文本'))
    }
  } catch (e) {
    console.error('[page-lang] selectPage failed:', e)
    ElMessage.error(t('加载页面失败'))
  } finally {
    pageTextLoading.value = false
  }
}

const pageTextStats = computed(() => {
  const total = pageTexts.value.length
  const filled = pageTexts.value.filter((r) => r.textVal.trim()).length
  return { total, filled, unfilled: total - filled }
})

async function loadExistingTranslations() {
  if (!selectedLocale.value || isZh.value) {
    existingTextMap.value = new Map()
    return
  }
  try {
    const res = await httpRequest('/api/lowcode/lang/page', {
      method: 'POST',
      body: JSON.stringify({
        locale: selectedLocale.value,
        page: 1,
        size: 10000,
      }),
    })
    const body = res.data as {
      res?: boolean
      data?: { list?: LangTextRow[] }
    }
    if (body && body.res !== false && body.data) {
      const map = new Map<string, LangTextRow>()
      for (const row of body.data.list ?? []) {
        map.set(row.textKey, row)
      }
      existingTextMap.value = map
    }
  } catch (e) {
    console.error('[page-lang] loadExisting failed:', e)
  }
}

async function autoTranslate() {
  const toTranslate = pageTexts.value
    .filter((r) => !r.textVal.trim())
    .map((r) => r.textKey)
  if (toTranslate.length === 0) {
    ElMessage.info(t('没有需要自动翻译的文本'))
    return
  }
  autoTranslating.value = true
  try {
    const res = await httpRequest('/api/lowcode/lang/auto-translate', {
      method: 'POST',
      body: JSON.stringify({
        texts: toTranslate,
        targetLang: selectedLocale.value,
      }),
    })
    const body = res.data as { res?: boolean; data?: Record<string, string> }
    if (body && body.res !== false && body.data) {
      let count = 0
      for (const row of pageTexts.value) {
        if (!row.textVal.trim() && body.data[row.textKey]) {
          row.textVal = body.data[row.textKey]
          count++
        }
      }
      ElMessage.success(t('自动翻译完成') + ` (${count} ${t('条')})`)
    } else {
      ElMessage.error(t('自动翻译失败'))
    }
  } catch (e) {
    ElMessage.error(t('自动翻译失败'))
  } finally {
    autoTranslating.value = false
  }
}

async function savePageTranslations() {
  if (!selectedPage.value) return
  const toSave = pageTexts.value.filter((r) => r.textVal.trim())
  if (toSave.length === 0) {
    ElMessage.warning(t('请至少填写一条译文'))
    return
  }
  try {
    const items = toSave.map((r) => ({
      textKey: r.textKey,
      textVal: r.textVal,
      module: r.module,
    }))
    const res = await httpRequest('/api/lowcode/lang/import', {
      method: 'POST',
      body: JSON.stringify({ locale: selectedLocale.value, items }),
    })
    const body = res.data as { res?: boolean }
    if (body && body.res !== false) {
      ElMessage.success(t('保存成功') + ` (${toSave.length} ${t('条')})`)
      await loadExistingTranslations()
    } else {
      ElMessage.error(t('保存失败'))
    }
  } catch (e) {
    ElMessage.error(t('保存失败'))
  }
}

// ================ 导出/导入 ================
const exportDialogVisible = ref(false)
const exportLoading = ref(false)

function getPageModulesBySystem(): string[] {
  const sysId = activeSystemId.value
  const mods = new Set<string>()
  for (const p of allPages.value) {
    if ((p.systemId || 'sys_default') === sysId && p.pageName) {
      mods.add(p.pageName)
    }
  }
  return Array.from(mods)
}

function getPageModulesByModule(): string[] {
  const sysId = activeSystemId.value
  const modId = activeModuleId.value
  const mods = new Set<string>()
  for (const p of allPages.value) {
    if (
      (p.systemId || 'sys_default') === sysId &&
      (p.moduleId || 'mod_default') === modId &&
      p.pageName
    ) {
      mods.add(p.pageName)
    }
  }
  return Array.from(mods)
}

async function exportByScope(scope: 'global' | 'system' | 'module') {
  let moduleFilter: string[] | undefined
  let label = 'all'

  if (scope === 'system') {
    moduleFilter = getPageModulesBySystem()
    label = activeSystemName.value || 'system'
    if (moduleFilter.length === 0) {
      ElMessage.warning(t('当前系统下没有页面'))
      return
    }
  } else if (scope === 'module') {
    moduleFilter = getPageModulesByModule()
    const modName = modules.value.find(
      (m) => m.id === activeModuleId.value
    )?.name
    label = modName || 'module'
    if (moduleFilter.length === 0) {
      ElMessage.warning(t('当前模块下没有页面'))
      return
    }
  }

  exportLoading.value = true
  try {
    const payload: Record<string, unknown> = { locale: selectedLocale.value }
    if (moduleFilter) {
      payload.modules = moduleFilter
    }
    const res = await httpRequest('/api/lowcode/lang/export', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    const body = res.data as { res?: boolean; data?: unknown[] }
    if (body && body.res !== false && body.data) {
      const json = JSON.stringify(body.data, null, 2)
      const blob = new Blob([json], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `page_translations_${label}_${
        selectedLocale.value
      }_${Date.now()}.json`
      a.click()
      URL.revokeObjectURL(url)
      ElMessage.success(t('导出成功') + ` (${body.data.length} ${t('条')})`)
      exportDialogVisible.value = false
    }
  } catch (e) {
    ElMessage.error(t('导出失败'))
  } finally {
    exportLoading.value = false
  }
}

const importDialogVisible = ref(false)
const importText = ref('')
const importLoading = ref(false)
const importFileInput = ref<HTMLInputElement>()

function triggerImportFile() {
  importFileInput.value?.click()
}

async function onImportFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    importText.value = await file.text()
    ElMessage.success(t('文件已加载') + `: ${file.name}`)
  } catch (err) {
    ElMessage.error(t('读取文件失败'))
  } finally {
    input.value = ''
  }
}

async function doImport() {
  if (!importText.value.trim()) {
    ElMessage.warning(t('请粘贴导入数据'))
    return
  }
  importLoading.value = true
  try {
    const data = JSON.parse(importText.value)
    if (!Array.isArray(data)) {
      ElMessage.error(t('数据格式错误'))
      return
    }
    const res = await httpRequest('/api/lowcode/lang/import-batch', {
      method: 'POST',
      body: JSON.stringify({ data }),
    })
    const body = res.data as { res?: boolean; data?: { count: number } }
    if (body && body.res !== false && body.data) {
      ElMessage.success(t('导入成功') + ` (${body.data.count} ${t('条')})`)
      importDialogVisible.value = false
      importText.value = ''
      await loadExistingTranslations()
    }
  } catch (e) {
    ElMessage.error(t('导入失败'))
  } finally {
    importLoading.value = false
  }
}

// ================ 系统选择弹窗 ================
const systemPickerVisible = ref(false)

function pickSystem(id: string) {
  activeSystemId.value = id
  activeModuleId.value = ''
  selectedPage.value = null
  systemPickerVisible.value = false
}

function selectModule(id: string) {
  activeModuleId.value = id
  selectedPage.value = null
}

// ================ 初始化 ================
watch(selectedLocale, async () => {
  selectedPage.value = null
  await loadExistingTranslations()
})

onMounted(async () => {
  loadModule('common')
  await loadLangs()
  await refreshSystems()
  await refreshModules()
  await refreshPages()
  await loadExistingTranslations()
})
</script>

<template>
  <div class="plm">
    <!-- 语言栏 -->
    <div class="plm-lang-bar">
      <span class="plm-lang-label">{{ t('语言：') }}</span>
      <div class="plm-lang-pills">
        <button
          v-for="lang in langs"
          :key="lang.locale"
          class="plm-lang-pill"
          :class="{ active: selectedLocale === lang.locale }"
          @click="onLangSelect(lang.locale)"
        >
          {{ lang.name }}
        </button>
      </div>
      <div class="plm-lang-right">
        <DxButton
          :text="t('导出')"
          type="normal"
          @click="exportDialogVisible = true"
        />
        <DxButton
          :text="t('导入')"
          type="normal"
          @click="importDialogVisible = true"
        />
      </div>
    </div>

    <div v-if="isZh" class="plm-zh-tip">
      {{ t('简体中文为默认语言，无需翻译。请选择其他语言进行编辑。') }}
    </div>

    <div v-else class="plm-main">
      <!-- 左侧：系统+模块 -->
      <div class="plm-sidebar">
        <button class="plm-sys-btn" @click="systemPickerVisible = true">
          <span class="plm-sys-icon">▤</span>
          <span class="plm-sys-name">{{
            activeSystemName || t('选择系统')
          }}</span>
          <span class="plm-sys-arrow">▸</span>
        </button>
        <div class="plm-module-tree">
          <div
            v-for="m in flatModules"
            :key="m.id"
            class="plm-module-item"
            :class="{ active: activeModuleId === m.id }"
            :style="{ paddingLeft: 16 + m.depth * 20 + 'px' }"
            @click="selectModule(m.id)"
          >
            <span
              v-if="m.hasChildren"
              class="plm-expand"
              @click.stop="toggleExpand(m.id)"
            >
              {{ m.expanded ? '▾' : '▸' }}
            </span>
            <span v-else class="plm-expand-placeholder"></span>
            <span class="plm-module-name">{{ m.name }}</span>
            <span class="plm-module-count">{{ modulePageCount(m.id) }}</span>
          </div>
        </div>
      </div>

      <!-- 右侧：页面列表 / 翻译编辑 -->
      <div class="plm-content">
        <!-- 页面列表 -->
        <div v-if="!selectedPage" class="plm-page-list">
          <div v-if="!activeModuleId" class="plm-empty">
            {{ t('请选择左侧模块') }}
          </div>
          <div v-else-if="pages.length === 0" class="plm-empty">
            {{ t('该模块下暂无页面') }}
          </div>
          <div v-else class="plm-page-grid">
            <button
              v-for="p in pages"
              :key="p.pageId"
              class="plm-page-card"
              @click="selectPage(p)"
            >
              <span class="plm-page-card-icon">📄</span>
              <span class="plm-page-card-name">{{ p.pageName }}</span>
              <span class="plm-page-card-id">{{ p.pageId }}</span>
            </button>
          </div>
        </div>

        <!-- 翻译编辑 -->
        <div v-else class="plm-translate">
          <div class="plm-translate-header">
            <button class="plm-back-link" @click="selectedPage = null">
              ← {{ t('返回页面列表') }}
            </button>
            <span class="plm-translate-page-name">
              {{ selectedPage.pageName }}
            </span>
            <div class="plm-translate-actions">
              <span class="plm-translate-stats">
                {{ t('共') }} {{ pageTextStats.total }} {{ t('条') }}，
                {{ t('已翻译') }} {{ pageTextStats.filled }} {{ t('条') }}
              </span>
              <DxButton
                :text="t('自动翻译')"
                type="normal"
                :disabled="autoTranslating"
                @click="autoTranslate"
              />
              <DxButton
                :text="t('一键保存')"
                type="default"
                @click="savePageTranslations"
              />
            </div>
          </div>

          <div v-if="pageTextLoading" class="plm-empty">
            {{ t('加载中...') }}
          </div>
          <div v-else-if="pageTexts.length === 0" class="plm-empty">
            {{ t('该页面未找到中文文本') }}
          </div>
          <div v-else class="plm-table-wrap">
            <table class="plm-table">
              <thead>
                <tr>
                  <th width="40">{{ t('序号') }}</th>
                  <th>{{ t('中文原文') }}</th>
                  <th>{{ t('译文') }}</th>
                  <th width="60">{{ t('状态') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, idx) in pageTexts" :key="idx">
                  <td class="plm-td-center">{{ idx + 1 }}</td>
                  <td>{{ row.textKey }}</td>
                  <td>
                    <input
                      v-model="row.textVal"
                      class="plm-input"
                      :placeholder="t('请输入译文')"
                    />
                  </td>
                  <td class="plm-td-center">
                    <span v-if="row.exists" class="plm-tag plm-tag-exist">{{
                      t('已有')
                    }}</span>
                    <span v-else class="plm-tag plm-tag-new">{{
                      t('新增')
                    }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>

    <!-- 导出弹窗 -->
    <div
      v-if="exportDialogVisible"
      class="plm-overlay"
      @click.self="exportDialogVisible = false"
    >
      <div class="plm-export-dialog">
        <div class="plm-sys-dialog-title">{{ t('选择导出范围') }}</div>
        <div class="plm-export-body">
          <button
            class="plm-export-option"
            :disabled="exportLoading"
            @click="exportByScope('global')"
          >
            <span class="plm-export-icon">🌐</span>
            <span class="plm-export-label">{{ t('全局导出') }}</span>
            <span class="plm-export-desc">{{
              t('导出所有系统的全部页面翻译')
            }}</span>
          </button>
          <button
            class="plm-export-option"
            :disabled="exportLoading || !activeSystemId"
            @click="exportByScope('system')"
          >
            <span class="plm-export-icon">▤</span>
            <span class="plm-export-label">{{ t('按系统导出') }}</span>
            <span class="plm-export-desc">{{
              activeSystemName
                ? t('当前系统') + ': ' + activeSystemName
                : t('请先选择系统')
            }}</span>
          </button>
          <button
            class="plm-export-option"
            :disabled="exportLoading || !activeModuleId"
            @click="exportByScope('module')"
          >
            <span class="plm-export-icon">▸</span>
            <span class="plm-export-label">{{ t('按模块导出') }}</span>
            <span class="plm-export-desc">{{
              activeModuleId
                ? t('当前系统') + ': ' + activeSystemName
                : t('请先选择模块')
            }}</span>
          </button>
        </div>
        <div class="plm-import-actions">
          <DxButton :text="t('取消')" @click="exportDialogVisible = false" />
        </div>
      </div>
    </div>

    <!-- 系统选择弹窗 -->
    <div
      v-if="systemPickerVisible"
      class="plm-overlay"
      @click.self="systemPickerVisible = false"
    >
      <div class="plm-sys-dialog">
        <div class="plm-sys-dialog-title">{{ t('选择系统') }}</div>
        <div class="plm-sys-dialog-body">
          <div class="plm-sys-grid">
            <button
              v-for="s in systems"
              :key="s.id"
              class="plm-sys-card"
              :class="{ active: activeSystemId === s.id }"
              @click="pickSystem(s.id)"
            >
              <span class="plm-sys-card-icon">▤</span>
              <span class="plm-sys-card-name">{{ s.name }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 导入弹窗 -->
    <div
      v-if="importDialogVisible"
      class="plm-overlay"
      @click.self="importDialogVisible = false"
    >
      <div class="plm-import-dialog">
        <div class="plm-sys-dialog-title">{{ t('导入翻译数据') }}</div>
        <div class="plm-import-body">
          <div class="plm-import-file-row">
            <DxButton
              :text="t('选择JSON文件')"
              type="normal"
              @click="triggerImportFile"
            />
            <span class="plm-import-hint">{{ t('或直接粘贴JSON数据') }}</span>
          </div>
          <textarea
            v-model="importText"
            class="plm-import-textarea"
            :placeholder="t('请粘贴导出的JSON数据')"
          />
        </div>
        <div class="plm-import-actions">
          <DxButton :text="t('取消')" @click="importDialogVisible = false" />
          <DxButton
            :text="t('导入')"
            type="default"
            :disabled="importLoading"
            @click="doImport"
          />
        </div>
        <input
          ref="importFileInput"
          type="file"
          accept=".json"
          style="display: none"
          @change="onImportFile"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.plm {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f5f5;
}
.plm-lang-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.plm-lang-label {
  font-size: 14px;
  color: #4b5563;
  font-weight: 500;
}
.plm-lang-pills {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
}
.plm-lang-pill {
  border: 1px solid #d1d5db;
  background: #fff;
  color: #374151;
  font-size: 13px;
  padding: 6px 16px;
  cursor: pointer;
  border-radius: 0;
}
.plm-lang-pill:hover {
  border-color: #2563eb;
  color: #2563eb;
}
.plm-lang-pill.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}
.plm-lang-right {
  display: flex;
  gap: 8px;
}
.plm-zh-tip {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  color: #6b7280;
}
.plm-main {
  flex: 1;
  display: flex;
  min-height: 0;
}

/* 左侧 */
.plm-sidebar {
  width: 260px;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.plm-sys-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 16px;
  border: none;
  border-bottom: 1px solid #e5e7eb;
  background: #f9fafb;
  cursor: pointer;
  font-size: 15px;
  color: #1f2937;
  text-align: left;
}
.plm-sys-btn:hover {
  background: #f0f7ff;
}
.plm-sys-icon {
  font-size: 18px;
  color: #2563eb;
}
.plm-sys-name {
  flex: 1;
  font-weight: 600;
}
.plm-sys-arrow {
  color: #9ca3af;
}
.plm-module-tree {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}
.plm-module-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 13px;
  color: #374151;
}
.plm-module-item:hover {
  background: #f0f7ff;
}
.plm-module-item.active {
  background: #2563eb;
  color: #fff;
}
.plm-expand {
  width: 16px;
  text-align: center;
  font-size: 12px;
  flex-shrink: 0;
}
.plm-expand-placeholder {
  width: 16px;
  flex-shrink: 0;
}
.plm-module-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.plm-module-count {
  font-size: 12px;
  color: #9ca3af;
  flex-shrink: 0;
}
.plm-module-item.active .plm-module-count {
  color: rgba(255, 255, 255, 0.7);
}

/* 右侧 */
.plm-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 16px 20px;
}
.plm-page-list {
  flex: 1;
  overflow-y: auto;
}
.plm-empty {
  text-align: center;
  padding: 60px 0;
  font-size: 15px;
  color: #9ca3af;
}
.plm-page-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.plm-page-card {
  border: 1px solid #e5e7eb;
  background: #fff;
  padding: 20px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
  border-radius: 0;
}
.plm-page-card:hover {
  border-color: #2563eb;
  background: #f0f7ff;
}
.plm-page-card-icon {
  font-size: 20px;
}
.plm-page-card-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}
.plm-page-card-id {
  font-size: 12px;
  color: #9ca3af;
}

/* 翻译编辑 */
.plm-translate {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.plm-translate-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.plm-back-link {
  border: none;
  background: transparent;
  color: #2563eb;
  font-size: 13px;
  cursor: pointer;
}
.plm-back-link:hover {
  text-decoration: underline;
}
.plm-translate-page-name {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  flex: 1;
}
.plm-translate-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.plm-translate-stats {
  font-size: 13px;
  color: #6b7280;
}
.plm-table-wrap {
  flex: 1;
  overflow-y: auto;
  margin-top: 12px;
}
.plm-table {
  width: 100%;
  border-collapse: collapse;
}
.plm-table th {
  background: #f9fafb;
  font-size: 13px;
  color: #4b5563;
  font-weight: 600;
  padding: 10px 12px;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
}
.plm-table td {
  font-size: 13px;
  color: #374151;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
}
.plm-table tr:hover td {
  background: #f9fafb;
}
.plm-td-center {
  text-align: center;
}
.plm-input {
  width: 100%;
  border: 1px solid #d1d5db;
  padding: 6px 10px;
  font-size: 13px;
  outline: none;
  border-radius: 0;
}
.plm-input:focus {
  border-color: #2563eb;
}
.plm-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 0;
}
.plm-tag-exist {
  background: #f0f7ff;
  color: #2563eb;
}
.plm-tag-new {
  background: #fef3c7;
  color: #d97706;
}

/* 弹窗 */
.plm-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.plm-sys-dialog {
  width: 900px;
  background: #fff;
  display: flex;
  flex-direction: column;
  max-height: 80vh;
}
.plm-sys-dialog-title {
  font-size: 17px;
  font-weight: 600;
  color: #1f2937;
  padding: 20px 24px 16px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.plm-sys-dialog-body {
  padding: 24px;
  flex: 1;
  overflow-y: auto;
}
.plm-sys-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  align-items: start;
}
.plm-sys-card {
  border: 1px solid #e5e7eb;
  background: #fff;
  padding: 20px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: flex-start;
  border-radius: 0;
}
.plm-sys-card:hover {
  border-color: #2563eb;
  background: #f0f7ff;
}
.plm-sys-card.active {
  border-color: #2563eb;
  background: #f0f7ff;
}
.plm-sys-card-icon {
  font-size: 20px;
  color: #2563eb;
}
.plm-sys-card-name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

/* 导出弹窗 */
.plm-export-dialog {
  width: 560px;
  background: #fff;
  display: flex;
  flex-direction: column;
  max-height: 80vh;
}
.plm-export-body {
  padding: 24px;
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.plm-export-option {
  display: flex;
  align-items: center;
  gap: 12px;
  border: 1px solid #d1d5db;
  background: #fff;
  padding: 16px 20px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s;
}
.plm-export-option:hover:not(:disabled) {
  border-color: #2563eb;
}
.plm-export-option:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.plm-export-icon {
  font-size: 20px;
  flex-shrink: 0;
}
.plm-export-label {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  flex-shrink: 0;
  min-width: 100px;
}
.plm-export-desc {
  font-size: 13px;
  color: #6b7280;
  flex: 1;
}

/* 导入弹窗 */
.plm-import-dialog {
  width: 800px;
  background: #fff;
  display: flex;
  flex-direction: column;
  max-height: 80vh;
}
.plm-import-body {
  padding: 24px;
  flex: 1;
  overflow-y: auto;
}
.plm-import-file-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.plm-import-hint {
  font-size: 13px;
  color: #9ca3af;
}
.plm-import-textarea {
  width: 100%;
  min-height: 300px;
  border: 1px solid #d1d5db;
  padding: 10px;
  font-size: 13px;
  font-family: monospace;
  outline: none;
  border-radius: 0;
  resize: vertical;
}
.plm-import-textarea:focus {
  border-color: #2563eb;
}
.plm-import-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #e5e7eb;
  flex-shrink: 0;
}
</style>