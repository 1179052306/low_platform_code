<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import DxColorBox from 'devextreme-vue/color-box'
import DxTextBox from 'devextreme-vue/text-box'
import DxNumberBox from 'devextreme-vue/number-box'
import { t } from '@/store/lang'
import type { Theme, ThemeColors } from '@/types/schema'
import {
  loadThemes,
  saveTheme,
  deleteTheme,
  getDefaultThemeId,
  setDefaultThemeId,
  batchApplyTheme,
} from '@/composables/useTheme'
import { listSavedPages } from '@/store/persistence'

const props = defineProps<{ visible: boolean; currentThemeId?: string }>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
  select: [themeId: string]
}>()

const themes = ref<Theme[]>([])
const loading = ref(false)
const keyword = ref('')
const viewMode = ref<'select' | 'manage' | 'edit'>('select')
const defaultThemeId = ref<string | null>(null)
const errorMsg = ref('')

const editingTheme = ref<Theme | null>(null)
const editName = ref('')
const editColors = ref<ThemeColors>(blankColors())
const editRadius = ref(0)
const saving = ref(false)

const batchTheme = ref<Theme | null>(null)
const pageList = ref<{ pageId: string; pageName: string }[]>([])
const selectedPageIds = ref<Set<string>>(new Set())

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
    themes.value = await loadThemes()
    defaultThemeId.value = await getDefaultThemeId()
  } catch (e) {
    errorMsg.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      viewMode.value = 'select'
      fetchThemes()
    }
  }
)

function onSelectTheme(th: Theme) {
  emit('select', th.id)
  emit('update:visible', false)
}

function onClose() {
  emit('update:visible', false)
}

function startNewTheme() {
  editingTheme.value = null
  editName.value = ''
  editColors.value = blankColors()
  editRadius.value = 0
  viewMode.value = 'edit'
}

function startEditTheme(th: Theme) {
  editingTheme.value = th
  editName.value = th.name
  editColors.value = { ...th.colors }
  editRadius.value = th.radius
  viewMode.value = 'edit'
}

function copyPreset(th: Theme) {
  editingTheme.value = null
  editName.value = th.name + t('副本')
  editColors.value = { ...th.colors }
  editRadius.value = th.radius
  viewMode.value = 'edit'
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
    viewMode.value = 'manage'
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
  try {
    const pages = await listSavedPages()
    pageList.value = pages.map((p) => ({
      pageId: p.pageId,
      pageName: p.pageName,
    }))
  } catch (e) {
    errorMsg.value = (e as Error).message
  }
}

function togglePage(pageId: string) {
  const s = new Set(selectedPageIds.value)
  if (s.has(pageId)) s.delete(pageId)
  else s.add(pageId)
  selectedPageIds.value = s
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
</script>

<template>
  <div v-if="visible" class="dialog-overlay" @click.self="onClose">
    <div class="dialog-container">
      <div class="dialog-header">
        <span class="dialog-title">{{
          viewMode === 'edit'
            ? t('主题编辑')
            : viewMode === 'manage'
            ? t('主题管理')
            : t('选择主题')
        }}</span>
        <button class="dialog-close" @click="onClose">✕</button>
      </div>

      <div v-if="errorMsg" class="dialog-error">{{ errorMsg }}</div>

      <div class="dialog-body">
        <!-- 选择视图 -->
        <template v-if="viewMode === 'select'">
          <div class="select-toolbar">
            <input
              v-model="keyword"
              class="search-input"
              :placeholder="t('搜索主题名称')"
            />
            <button class="action-btn" @click="viewMode = 'manage'">
              {{ t('管理主题') }}
            </button>
          </div>
          <div v-if="loading" class="empty-tip">{{ t('加载中') }}</div>
          <div v-else-if="!filteredThemes.length" class="empty-tip">
            {{ t('暂无主题') }}
          </div>
          <div v-else class="theme-grid">
            <div
              v-for="th in filteredThemes"
              :key="th.id"
              class="theme-card"
              :class="{ selected: th.id === currentThemeId }"
              @click="onSelectTheme(th)"
            >
              <div
                class="card-preview"
                :style="{ background: th.colors.pageBg }"
              >
                <div
                  class="preview-bar"
                  :style="{ background: th.colors.primary }"
                ></div>
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
                </div>
              </div>
              <div class="card-info">
                <span class="card-name">{{ th.name }}</span>
                <span v-if="th.isPreset" class="preset-badge">{{
                  t('预置')
                }}</span>
                <span v-if="th.id === defaultThemeId" class="default-badge">{{
                  t('默认')
                }}</span>
              </div>
            </div>
          </div>
        </template>

        <!-- 管理视图 -->
        <template v-else-if="viewMode === 'manage'">
          <div class="manage-toolbar">
            <button class="action-btn" @click="viewMode = 'select'">
              {{ t('返回选择') }}
            </button>
            <button class="action-btn primary-btn" @click="startNewTheme">
              {{ t('新增主题') }}
            </button>
          </div>
          <div class="manage-list">
            <div v-for="th in themes" :key="th.id" class="manage-row">
              <div class="manage-info">
                <span
                  class="preview-dot"
                  :style="{ background: th.colors.primary }"
                ></span>
                <span class="manage-name">{{ th.name }}</span>
                <span v-if="th.isPreset" class="preset-badge">{{
                  t('预置')
                }}</span>
                <span v-if="th.id === defaultThemeId" class="default-badge">{{
                  t('默认')
                }}</span>
              </div>
              <div class="manage-actions">
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
        </template>

        <!-- 编辑视图 -->
        <template v-else>
          <div class="edit-form">
            <div class="form-item">
              <label class="form-label">{{ t('主题名称') }}</label>
              <DxTextBox
                v-model="editName"
                :placeholder="t('请输入主题名称')"
              />
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
            <button class="action-btn" @click="viewMode = 'manage'">
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
        </template>
      </div>

      <!-- 批量应用浮层 -->
      <div
        v-if="batchTheme"
        class="batch-overlay"
        @click.self="batchTheme = null"
      >
        <div class="batch-container">
          <div class="batch-header">
            <span>{{ t('批量应用主题') }}「{{ batchTheme.name }}」</span>
            <button class="dialog-close" @click="batchTheme = null">✕</button>
          </div>
          <div class="batch-body">
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
          </div>
          <div class="batch-footer">
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
  </div>
</template>

<style scoped>
.dialog-overlay {
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
.dialog-container {
  background: #fff;
  border-radius: 0;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.18);
  width: 900px;
  max-width: 92vw;
  max-height: 85vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}
.dialog-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
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
.dialog-error {
  padding: 8px 24px;
  background: #fef0f0;
  color: #f56c6c;
  font-size: 13px;
  flex-shrink: 0;
}
.dialog-body {
  padding: 20px 24px;
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
.empty-tip {
  text-align: center;
  color: #909399;
  padding: 40px 0;
  font-size: 14px;
}
.select-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  flex-shrink: 0;
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
.theme-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  align-items: start;
}
.theme-card {
  border: 2px solid #e4e7ed;
  background: #fff;
  cursor: pointer;
  padding: 0;
  overflow: hidden;
}
.theme-card:hover {
  border-color: #1976d2;
}
.theme-card.selected {
  border-color: #1976d2;
  background: #e8f0fe;
}
.card-preview {
  height: 100px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  box-sizing: border-box;
}
.preview-bar {
  height: 10px;
  width: 100%;
}
.preview-row {
  display: flex;
  gap: 8px;
  margin-top: auto;
}
.preview-dot {
  display: inline-block;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  flex-shrink: 0;
}
.card-info {
  padding: 12px 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-name {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}
.preset-badge {
  font-size: 11px;
  padding: 1px 6px;
  background: #e4e7ed;
  color: #606266;
  border-radius: 0;
}
.default-badge {
  font-size: 11px;
  padding: 1px 6px;
  background: #1976d2;
  color: #fff;
  border-radius: 0;
}
.manage-toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-shrink: 0;
}
.manage-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.manage-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border: 1px solid #e4e7ed;
  background: #fff;
}
.manage-info {
  display: flex;
  align-items: center;
  gap: 10px;
}
.manage-name {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}
.manage-actions {
  display: flex;
  gap: 8px;
}
.row-btn {
  padding: 5px 14px;
  border: 1px solid #dcdfe6;
  border-radius: 0;
  background: #fff;
  color: #606266;
  font-size: 13px;
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
.edit-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.form-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.form-label {
  font-size: 13px;
  color: #606266;
}
.color-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.color-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.edit-preview {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  border: 1px solid #e4e7ed;
  min-height: 120px;
}
.preview-text {
  font-size: 16px;
  font-weight: 600;
}
.edit-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding-top: 16px;
  flex-shrink: 0;
}
.batch-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 10;
}
.batch-container {
  background: #fff;
  width: 480px;
  max-width: 90%;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.2);
}
.batch-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid #e4e7ed;
  font-size: 15px;
  font-weight: 600;
}
.batch-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 20px;
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
.batch-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 14px 20px;
  border-top: 1px solid #e4e7ed;
}
</style>