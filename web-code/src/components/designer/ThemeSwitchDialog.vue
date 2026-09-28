<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { t } from '@/store/lang'
import type { Theme } from '@/types/schema'
import {
  loadThemes,
  getDefaultThemeId,
  setDefaultThemeId,
} from '@/composables/useTheme'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  'update:visible': [value: boolean]
  select: [theme: Theme]
}>()

const themes = ref<Theme[]>([])
const loading = ref(false)
const keyword = ref('')
const currentThemeId = ref<string | null>(null)
const errorMsg = ref('')

const filteredThemes = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return themes.value
  return themes.value.filter((th) => th.name.toLowerCase().includes(kw))
})

async function fetchData() {
  loading.value = true
  errorMsg.value = ''
  try {
    themes.value = await loadThemes()
    currentThemeId.value = await getDefaultThemeId()
  } catch (e) {
    errorMsg.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (v) => {
    if (v) fetchData()
  }
)

async function onSelectTheme(th: Theme) {
  errorMsg.value = ''
  try {
    await setDefaultThemeId(th.id)
    currentThemeId.value = th.id
    emit('select', th)
    emit('update:visible', false)
  } catch (e) {
    errorMsg.value = (e as Error).message
  }
}

function onClose() {
  emit('update:visible', false)
}
</script>

<template>
  <div v-if="visible" class="dialog-overlay" @click.self="onClose">
    <div class="dialog-container">
      <div class="dialog-header">
        <span class="dialog-title">{{ t('切换主题') }}</span>
        <button class="dialog-close" @click="onClose">✕</button>
      </div>
      <div v-if="errorMsg" class="dialog-error">{{ errorMsg }}</div>
      <div class="dialog-body">
        <div class="select-toolbar">
          <input
            v-model="keyword"
            class="search-input"
            :placeholder="t('搜索主题名称')"
          />
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
            <div class="card-preview" :style="{ background: th.colors.pageBg }">
              <div
                class="preview-topbar"
                :style="{
                  background: th.colors.cardBg,
                  borderBottomColor: th.colors.border,
                }"
              >
                <span
                  class="preview-logo-dot"
                  :style="{ background: th.colors.primary }"
                ></span>
                <span
                  class="preview-topbar-line"
                  :style="{ background: th.colors.border }"
                ></span>
                <span
                  class="preview-topbar-line short"
                  :style="{ background: th.colors.border }"
                ></span>
              </div>
              <div
                class="preview-sidebar"
                :style="{
                  background: th.colors.cardBg,
                  borderRightColor: th.colors.border,
                }"
              >
                <span
                  class="preview-side-item active"
                  :style="{ background: th.colors.primary }"
                ></span>
                <span
                  class="preview-side-item"
                  :style="{ background: th.colors.border }"
                ></span>
                <span
                  class="preview-side-item"
                  :style="{ background: th.colors.border }"
                ></span>
              </div>
              <div class="preview-content">
                <div
                  class="preview-card"
                  :style="{
                    background: th.colors.cardBg,
                    borderColor: th.colors.border,
                  }"
                >
                  <span
                    class="preview-card-title"
                    :style="{ color: th.colors.textPrimary }"
                    >{{ th.name }}</span
                  >
                  <span
                    class="preview-card-desc"
                    :style="{ color: th.colors.textSecondary }"
                    >Aa Bb Cc</span
                  >
                  <div class="preview-btn-row">
                    <span
                      class="preview-btn"
                      :style="{
                        background: th.colors.primary,
                        color: th.colors.cardBg,
                      }"
                      >OK</span
                    >
                    <span
                      class="preview-btn ghost"
                      :style="{
                        borderColor: th.colors.border,
                        color: th.colors.textSecondary,
                      }"
                      >···</span
                    >
                  </div>
                </div>
                <div class="preview-dots">
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
            </div>
            <div class="card-info">
              <span class="card-name" :style="{ color: '#303133' }">{{
                th.name
              }}</span>
              <span v-if="th.isPreset" class="preset-badge">{{
                t('预置')
              }}</span>
              <span v-if="th.id === currentThemeId" class="current-badge"
                >✓</span
              >
            </div>
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
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.18);
  width: 720px;
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
}
.select-toolbar {
  margin-bottom: 16px;
}
.search-input {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #dcdfe6;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
}
.search-input:focus {
  border-color: #1976d2;
}
.empty-tip {
  text-align: center;
  color: #909399;
  padding: 40px 0;
  font-size: 14px;
}
.theme-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  align-items: start;
}
.theme-card {
  border: 2px solid #e4e7ed;
  background: #fff;
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.theme-card:hover {
  border-color: #1976d2;
  box-shadow: 0 4px 12px rgba(25, 118, 210, 0.15);
}
.theme-card.selected {
  border-color: #1976d2;
  box-shadow: 0 4px 12px rgba(25, 118, 210, 0.2);
}
.card-preview {
  height: 140px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.preview-topbar {
  height: 22px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 8px;
  border-bottom: 1px solid;
  flex-shrink: 0;
}
.preview-logo-dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  flex-shrink: 0;
}
.preview-topbar-line {
  width: 40px;
  height: 5px;
  flex-shrink: 0;
}
.preview-topbar-line.short {
  width: 24px;
}
.preview-sidebar {
  width: 32px;
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: 8px 6px;
  border-right: 1px solid;
  flex-shrink: 0;
}
.preview-side-item {
  height: 5px;
  border-radius: 2px;
}
.preview-side-item.active {
  height: 14px;
}
.preview-content {
  flex: 1;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
}
.preview-card {
  border: 1px solid;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.preview-card-title {
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.preview-card-desc {
  font-size: 9px;
}
.preview-btn-row {
  display: flex;
  gap: 5px;
  margin-top: 2px;
}
.preview-btn {
  font-size: 8px;
  padding: 2px 7px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  letter-spacing: 0.5px;
}
.preview-btn.ghost {
  border: 1px solid;
}
.preview-dots {
  display: flex;
  gap: 5px;
  margin-top: auto;
}
.preview-dot {
  width: 10px;
  height: 10px;
  display: inline-block;
  border-radius: 50%;
}
.card-info {
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-name {
  font-size: 14px;
  font-weight: 500;
}
.preset-badge {
  font-size: 11px;
  padding: 1px 5px;
  background: #e8f0fe;
  color: #1976d2;
  border: 1px solid #b3d4fe;
}
.current-badge {
  margin-left: auto;
  color: #1976d2;
  font-weight: 700;
}
</style>