<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import DxColorBox from 'devextreme-vue/color-box'
import DxTextBox from 'devextreme-vue/text-box'
import DxNumberBox from 'devextreme-vue/number-box'
import { t, loadModule } from '@/store/lang'
import type { Theme, ThemeColors } from '@/types/schema'
import {
  loadThemes,
  saveTheme,
  deleteTheme,
  getDefaultThemeId,
  setDefaultThemeId,
  batchApplyTheme,
  invalidateThemesCache,
} from '@/composables/useTheme'
import { listSavedPages } from '@/store/persistence'

const themes = ref<Theme[]>([])
const loading = ref(false)
const keyword = ref('')
const defaultThemeId = ref<string | null>(null)
const errorMsg = ref('')

const editingTheme = ref<Theme | null>(null)
const editName = ref('')
const editColors = ref<ThemeColors>(blankColors())
const editRadius = ref(0)
const saving = ref(false)
const showEditPanel = ref(false)

const batchTheme = ref<Theme | null>(null)
const pageList = ref<{ pageId: string; pageName: string }[]>([])
const selectedPageIds = ref<Set<string>>(new Set())
const batchLoading = ref(false)

function blankColors(): ThemeColors {
  return {
    primary: '#1976d2',
    primaryHover: '#1565c0',
    success: '#52c41a',
    warning: '#faad14',
    danger: '#f5222d',
    info: '#909399',
    pageBg: '#f0f2f5',
    cardBg: '#ffffff',
    textPrimary: '#303133',
    textSecondary: '#606266',
    border: '#dcdfe6',
  }
}

const filteredThemes = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return themes.value
  return themes.value.filter((th) => th.name.toLowerCase().includes(kw))
})

async function fetchThemes() {
  loading.value = true
  errorMsg.value = ''
  try {
    invalidateThemesCache()
    themes.value = await loadThemes()
    defaultThemeId.value = await getDefaultThemeId()
  } catch (e) {
    errorMsg.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

function startNewTheme() {
  editingTheme.value = null
  editName.value = ''
  editColors.value = blankColors()
  editRadius.value = 0
  showEditPanel.value = true
}

function startEditTheme(th: Theme) {
  editingTheme.value = th
  editName.value = th.name
  editColors.value = { ...th.colors }
  editRadius.value = th.radius
  showEditPanel.value = true
}

function copyPreset(th: Theme) {
  editingTheme.value = null
  editName.value = th.name + t('副本')
  editColors.value = { ...th.colors }
  editRadius.value = th.radius
  showEditPanel.value = true
}

function cancelEdit() {
  showEditPanel.value = false
  editingTheme.value = null
}

async function onSaveTheme() {
  if (!editName.value.trim()) {
    errorMsg.value = t('主题名称不能为空')
    return
  }
  saving.value = true
  errorMsg.value = ''
  try {
    const id = editingTheme.value?.id || crypto.randomUUID()
    await saveTheme({
      id,
      name: editName.value.trim(),
      colors: editColors.value,
      radius: editRadius.value,
    })
    await fetchThemes()
    cancelEdit()
  } catch (e) {
    errorMsg.value = (e as Error).message
  } finally {
    saving.value = false
  }
}

async function onDeleteTheme(th: Theme) {
  if (!confirm(t('确认删除主题') + '「' + th.name + '」？')) return
  errorMsg.value = ''
  try {
    await deleteTheme(th.id)
    await fetchThemes()
  } catch (e) {
    errorMsg.value = (e as Error).message
  }
}

async function onSetDefault(th: Theme) {
  errorMsg.value = ''
  try {
    await setDefaultThemeId(th.id)
    defaultThemeId.value = th.id
  } catch (e) {
    errorMsg.value = (e as Error).message
  }
}

async function startBatchApply(th: Theme) {
  batchTheme.value = th
  selectedPageIds.value = new Set()
  errorMsg.value = ''
  batchLoading.value = true
  try {
    const pages = await listSavedPages()
    pageList.value = pages.map((p) => ({
      pageId: p.pageId,
      pageName: p.pageName,
    }))
  } catch (e) {
    errorMsg.value = (e as Error).message
  } finally {
    batchLoading.value = false
  }
}

function togglePage(pageId: string) {
  const s = new Set(selectedPageIds.value)
  if (s.has(pageId)) s.delete(pageId)
  else s.add(pageId)
  selectedPageIds.value = s
}

function selectAllPages() {
  selectedPageIds.value = new Set(pageList.value.map((p) => p.pageId))
}

function clearAllPages() {
  selectedPageIds.value = new Set()
}

async function confirmBatchApply() {
  if (!batchTheme.value) return
  if (selectedPageIds.value.size === 0) {
    errorMsg.value = t('请至少选择一个页面')
    return
  }
  errorMsg.value = ''
  try {
    await batchApplyTheme(
      batchTheme.value.id,
      Array.from(selectedPageIds.value)
    )
    batchTheme.value = null
    selectedPageIds.value = new Set()
  } catch (e) {
    errorMsg.value = (e as Error).message
  }
}

const colorFields: { key: keyof ThemeColors; label: string }[] = [
  { key: 'primary', label: '主色' },
  { key: 'primaryHover', label: '主色悬停' },
  { key: 'success', label: '成功色' },
  { key: 'warning', label: '警告色' },
  { key: 'danger', label: '危险色' },
  { key: 'info', label: '信息色' },
  { key: 'pageBg', label: '页面背景' },
  { key: 'cardBg', label: '卡片背景' },
  { key: 'textPrimary', label: '主文字' },
  { key: 'textSecondary', label: '次文字' },
  { key: 'border', label: '边框' },
]

onMounted(() => {
  fetchThemes()
  loadModule('theme', 'manage')
})
</script>

<template>
  <div class="theme-manage-page">
    <div class="page-header">
      <span class="page-title">{{ t('主题管理') }}</span>
      <div class="header-actions">
        <input
          v-model="keyword"
          class="search-input"
          :placeholder="t('搜索主题名称')"
        />
        <button class="action-btn primary-btn" @click="startNewTheme">
          {{ t('新增主题') }}
        </button>
      </div>
    </div>

    <div v-if="errorMsg" class="page-error">{{ errorMsg }}</div>

    <div v-if="loading" class="empty-tip">{{ t('加载中') }}</div>
    <div v-else-if="!filteredThemes.length" class="empty-tip">
      {{ t('暂无主题') }}
    </div>
    <div v-else class="theme-grid">
      <div v-for="th in filteredThemes" :key="th.id" class="theme-card">
        <div class="card-preview" :style="{ background: th.colors.pageBg }">
          <div
            class="preview-bar"
            :style="{ background: th.colors.primary }"
          ></div>
          <div class="preview-text" :style="{ color: th.colors.textPrimary }">
            {{ th.name }}
          </div>
          <div class="preview-row">
            <span
              class="preview-dot"
              :style="{ background: th.colors.success }"
            ></span>
            <span
              class="preview-dot"
              :style="{ background: th.colors.warning }"
            ></span>
            <span
              class="preview-dot"
              :style="{ background: th.colors.danger }"
            ></span>
            <span
              class="preview-dot"
              :style="{ background: th.colors.info }"
            ></span>
          </div>
        </div>
        <div class="card-info">
          <div class="card-info-left">
            <span class="card-name">{{ th.name }}</span>
            <span v-if="th.isPreset" class="preset-badge">{{ t('预置') }}</span>
            <span v-if="th.id === defaultThemeId" class="default-badge">{{
              t('默认')
            }}</span>
          </div>
          <div class="card-actions">
            <button class="row-btn" @click="copyPreset(th)">
              {{ t('复制') }}
            </button>
            <button
              v-if="!th.isPreset"
              class="row-btn"
              @click="startEditTheme(th)"
            >
              {{ t('编辑') }}
            </button>
            <button
              v-if="th.id !== defaultThemeId"
              class="row-btn"
              @click="onSetDefault(th)"
            >
              {{ t('设为默认') }}
            </button>
            <button class="row-btn" @click="startBatchApply(th)">
              {{ t('批量应用') }}
            </button>
            <button
              v-if="!th.isPreset"
              class="row-btn danger-btn"
              @click="onDeleteTheme(th)"
            >
              {{ t('删除') }}
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 编辑面板（右侧抽屉） -->
    <div v-if="showEditPanel" class="edit-overlay" @click.self="cancelEdit">
      <div class="edit-panel">
        <div class="edit-header">
          <span class="edit-title">
            {{ editingTheme ? t('编辑主题') : t('新增主题') }}
          </span>
          <button class="dialog-close" @click="cancelEdit">✕</button>
        </div>
        <div class="edit-body">
          <div class="form-item">
            <label class="form-label">{{ t('主题名称') }}</label>
            <DxTextBox v-model="editName" :placeholder="t('请输入主题名称')" />
          </div>
          <div class="form-item">
            <label class="form-label">{{ t('圆角(px)') }}</label>
            <DxNumberBox v-model="editRadius" :min="0" :max="20" :step="1" />
          </div>
          <div class="color-grid">
            <div v-for="f in colorFields" :key="f.key" class="color-item">
              <label class="form-label">{{ t(f.label) }}</label>
              <DxColorBox v-model="editColors[f.key]" />
            </div>
          </div>
          <div
            class="edit-preview"
            :style="{
              background: editColors.pageBg,
              color: editColors.textPrimary,
              borderRadius: editRadius + 'px',
            }"
          >
            <div
              class="preview-bar"
              :style="{ background: editColors.primary }"
            ></div>
            <div
              class="preview-text"
              :style="{ color: editColors.textPrimary }"
            >
              {{ editName || t('主题预览') }}
            </div>
            <div class="preview-row">
              <span
                class="preview-dot"
                :style="{ background: editColors.success }"
              ></span>
              <span
                class="preview-dot"
                :style="{ background: editColors.warning }"
              ></span>
              <span
                class="preview-dot"
                :style="{ background: editColors.danger }"
              ></span>
            </div>
          </div>
        </div>
        <div class="edit-footer">
          <button class="action-btn" @click="cancelEdit">
            {{ t('取消') }}
          </button>
          <button
            class="action-btn primary-btn"
            :disabled="saving"
            @click="onSaveTheme"
          >
            {{ saving ? t('保存中') : t('保存') }}
          </button>
        </div>
      </div>
    </div>

    <!-- 批量应用弹窗 -->
    <div v-if="batchTheme" class="edit-overlay" @click.self="batchTheme = null">
      <div class="batch-container">
        <div class="edit-header">
          <span class="edit-title">
            {{ t('批量应用主题') }}「{{ batchTheme.name }}」
          </span>
          <button class="dialog-close" @click="batchTheme = null">✕</button>
        </div>
        <div class="batch-body">
          <div v-if="batchLoading" class="empty-tip">{{ t('加载中') }}</div>
          <template v-else>
            <div class="batch-toolbar">
              <span class="batch-count">
                {{ t('已选') }} {{ selectedPageIds.size }} /
                {{ pageList.length }}
              </span>
              <button class="row-btn" @click="selectAllPages">
                {{ t('全选') }}
              </button>
              <button class="row-btn" @click="clearAllPages">
                {{ t('清空') }}
              </button>
            </div>
            <div v-if="!pageList.length" class="empty-tip">
              {{ t('暂无页面') }}
            </div>
            <div
              v-for="p in pageList"
              :key="p.pageId"
              class="batch-row"
              @click="togglePage(p.pageId)"
            >
              <input
                type="checkbox"
                :checked="selectedPageIds.has(p.pageId)"
                @click.stop="togglePage(p.pageId)"
              />
              <span>{{ p.pageName }}</span>
            </div>
          </template>
        </div>
        <div class="edit-footer">
          <button class="action-btn" @click="batchTheme = null">
            {{ t('取消') }}
          </button>
          <button class="action-btn primary-btn" @click="confirmBatchApply">
            {{ t('应用') }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.theme-manage-page {
  padding: 24px;
  height: 100%;
  box-sizing: border-box;
  overflow-y: auto;
}
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}
.page-title {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.search-input {
  width: 280px;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 0;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
}
.search-input:focus {
  border-color: #1976d2;
}
.page-error {
  padding: 10px 16px;
  background: #fef0f0;
  color: #f56c6c;
  font-size: 13px;
  margin-bottom: 16px;
  border: 1px solid #fbc0c0;
}
.empty-tip {
  text-align: center;
  color: #909399;
  padding: 60px 0;
  font-size: 14px;
}
.theme-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 20px;
  align-items: start;
}
.theme-card {
  border: 1px solid #e4e7ed;
  background: #fff;
  overflow: hidden;
}
.theme-card:hover {
  border-color: #1976d2;
}
.card-preview {
  height: 120px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  box-sizing: border-box;
}
.preview-bar {
  height: 10px;
  width: 100%;
}
.preview-text {
  font-size: 14px;
  font-weight: 500;
}
.preview-row {
  display: flex;
  gap: 8px;
  margin-top: auto;
}
.preview-dot {
  width: 14px;
  height: 14px;
  display: inline-block;
  border-radius: 50%;
}
.card-info {
  padding: 12px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.card-info-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-name {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}
.preset-badge {
  font-size: 11px;
  padding: 2px 6px;
  background: #e8f0fe;
  color: #1976d2;
  border: 1px solid #b3d4fe;
}
.default-badge {
  font-size: 11px;
  padding: 2px 6px;
  background: #f0f9eb;
  color: #67c23a;
  border: 1px solid #b3e19d;
}
.card-actions {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.row-btn {
  padding: 4px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 0;
  background: #fff;
  color: #606266;
  font-size: 12px;
  cursor: pointer;
}
.row-btn:hover {
  border-color: #1976d2;
  color: #1976d2;
}
.danger-btn:hover {
  border-color: #f56c6c;
  color: #f56c6c;
}
.action-btn {
  padding: 8px 20px;
  border: 1px solid #dcdfe6;
  border-radius: 0;
  background: #fff;
  color: #606266;
  font-size: 14px;
  cursor: pointer;
}
.action-btn:hover {
  border-color: #1976d2;
  color: #1976d2;
}
.primary-btn {
  background: #1976d2;
  color: #fff;
  border-color: #1976d2;
}
.primary-btn:hover {
  background: #1565c0;
  border-color: #1565c0;
  color: #fff;
}
.primary-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.dialog-close {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 18px;
  color: #909399;
  padding: 0;
  line-height: 1;
}
.dialog-close:hover {
  color: #303133;
}
.edit-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}
.edit-panel {
  background: #fff;
  width: 640px;
  max-width: 92vw;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.edit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}
.edit-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.edit-body {
  padding: 20px 24px;
  flex: 1;
  overflow-y: auto;
}
.form-item {
  margin-bottom: 16px;
}
.form-label {
  display: block;
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
}
.color-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.color-item {
  display: flex;
  flex-direction: column;
}
.edit-preview {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border: 1px solid #e4e7ed;
}
.edit-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #e4e7ed;
  flex-shrink: 0;
}
.batch-container {
  background: #fff;
  width: 520px;
  max-width: 92vw;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.batch-body {
  padding: 16px 24px;
  flex: 1;
  overflow-y: auto;
}
.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid #e4e7ed;
}
.batch-count {
  font-size: 13px;
  color: #606266;
}
.batch-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  cursor: pointer;
  font-size: 14px;
  color: #303133;
}
.batch-row:hover {
  background: #f5f7fa;
}
</style>