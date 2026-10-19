<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import LcTable from '@/materials/components/LcTable.vue'
import type { LcColumn } from '@/materials/components/LcTable.vue'
import { httpRequest } from '@/utils/request'
import { t, loadModule, type LangInfo } from '@/store/lang'
import {
  ElMessage,
  ElMessageBox,
  ElButton,
  ElInput,
  ElCheckbox,
  ElInputNumber,
} from 'element-plus'

defineOptions({ name: 'LangListManageView' })

const langs = ref<LangInfo[]>([])
const loading = ref(false)

const langColumns = computed<LcColumn[]>(() => [
  { dataField: 'locale', caption: t('语言代码'), width: 160 },
  { dataField: 'name', caption: t('语言名称'), width: 200 },
  { dataField: 'sortOrder', caption: t('排序'), width: 100, alignment: 'center' },
  { dataField: 'enabled', caption: t('启用'), width: 100, alignment: 'center', cellTemplate: 'enabledCell' },
  { dataField: 'isRtl', caption: t('RTL'), width: 100, alignment: 'center', cellTemplate: 'rtlCell' },
  { dataField: '__action', caption: t('操作'), width: 120, alignment: 'center', cellTemplate: 'actionCell' },
])

async function loadLangs() {
  loading.value = true
  try {
    const res = await httpRequest('/api/lowcode/lang/list-all', {
      method: 'POST',
    })
    const body = res.data as { res?: boolean; data?: LangInfo[] }
    if (body && body.res !== false && body.data) {
      langs.value = body.data
    }
  } catch (e) {
    console.error('[lang-list] loadLangs failed:', e)
    ElMessage.error(t('加载语言列表失败'))
  } finally {
    loading.value = false
  }
}

// ================ 新增/编辑语言弹窗 ================
const dialogVisible = ref(false)
const editing = ref(false)
const form = ref<{
  locale: string
  name: string
  sortOrder: number
  enabled: boolean
  isRtl: boolean
}>({
  locale: '',
  name: '',
  sortOrder: 0,
  enabled: true,
  isRtl: false,
})

function openAdd() {
  editing.value = false
  form.value = {
    locale: '',
    name: '',
    sortOrder: langs.value.length,
    enabled: true,
    isRtl: false,
  }
  dialogVisible.value = true
}

function openEdit(row: LangInfo) {
  editing.value = true
  form.value = {
    locale: row.locale,
    name: row.name,
    sortOrder: row.sortOrder,
    enabled: row.enabled,
    isRtl: row.isRtl,
  }
  dialogVisible.value = true
}

async function save() {
  if (!form.value.locale.trim()) {
    ElMessage.warning(t('语言代码不能为空'))
    return
  }
  if (!form.value.name.trim()) {
    ElMessage.warning(t('语言名称不能为空'))
    return
  }
  try {
    const url = editing.value
      ? '/api/lowcode/lang/update-lang'
      : '/api/lowcode/lang/add-lang'
    const res = await httpRequest(url, {
      method: 'POST',
      body: JSON.stringify(form.value),
    })
    const body = res.data as { res?: boolean }
    if (body && body.res !== false) {
      ElMessage.success(t('保存成功'))
      dialogVisible.value = false
      await loadLangs()
    } else {
      ElMessage.error(t('保存失败'))
    }
  } catch (e) {
    ElMessage.error(t('保存失败'))
  }
}

async function deleteLang(row: LangInfo) {
  if (row.locale === 'zh-CN') {
    ElMessage.warning(t('简体中文为默认语言，不可删除'))
    return
  }
  try {
    await ElMessageBox.confirm(
      t('确认删除该语言？') + t('其下所有翻译将一并删除。'),
      t('提示'),
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    const res = await httpRequest('/api/lowcode/lang/delete-lang', {
      method: 'POST',
      body: JSON.stringify({ locale: row.locale }),
    })
    const body = res.data as { res?: boolean }
    if (body && body.res !== false) {
      ElMessage.success(t('删除成功'))
      await loadLangs()
    }
  } catch (e) {
    ElMessage.error(t('删除失败'))
  }
}

onMounted(() => {
  loadModule('common')
  loadLangs()
})
</script>

<template>
  <div class="lang-list-manage">
    <div class="llm-body">
      <div class="llm-toolbar">
        <ElButton type="default" @click="openAdd">{{ t('新增语言') }}</ElButton>
      </div>

      <LcTable
        class="llm-grid"
        :data-source="langs"
        :columns="langColumns"
        :show-borders="true"
        :show-column-lines="false"
        :show-row-lines="true"
        :paging="{ enabled: false }"
        height="100%"
        key-expr="locale"
        :load-panel-enabled="loading"
      >
        <template #enabledCell="{ data }">
          <span :class="data.enabled ? 'llm-yes' : 'llm-no'">
            {{ data.enabled ? t('是') : t('否') }}
          </span>
        </template>
        <template #rtlCell="{ data }">
          <span :class="data.isRtl ? 'llm-yes' : 'llm-no'">
            {{ data.isRtl ? t('是') : t('否') }}
          </span>
        </template>
        <template #actionCell="{ data }">
          <div class="llm-action-row">
            <button
              class="llm-action-btn"
              :title="t('编辑')"
              @click="openEdit(data as any)"
            >
              ✎
            </button>
            <button
              v-if="data.locale !== 'zh-CN'"
              class="llm-action-btn llm-action-del"
              :title="t('删除')"
              @click="deleteLang(data as any)"
            >
              🗑
            </button>
          </div>
        </template>
      </LcTable>
    </div>

    <!-- 新增/编辑语言弹窗 -->
    <div
      v-if="dialogVisible"
      class="llm-overlay"
      @click.self="dialogVisible = false"
    >
      <div class="llm-dialog">
        <div class="llm-dialog-title">
          {{ editing ? t('编辑语言') : t('新增语言') }}
        </div>
        <div class="llm-dialog-body">
          <div class="llm-form-row">
            <label class="llm-form-label">{{ t('语言代码') }}</label>
            <ElInput
              v-model="form.locale"
              :placeholder="t('如 en-US、ja-JP、fr-FR')"
              :readonly="editing"
            />
          </div>
          <div class="llm-form-row">
            <label class="llm-form-label">{{ t('语言名称') }}</label>
            <ElInput
              v-model="form.name"
              :placeholder="t('如 English、日本語、Français')"
            />
          </div>
          <div class="llm-form-row">
            <label class="llm-form-label">{{ t('排序') }}</label>
            <ElInputNumber v-model="form.sortOrder" />
          </div>
          <div class="llm-form-row llm-form-row-inline">
            <label class="llm-form-label">{{ t('启用') }}</label>
            <ElCheckbox v-model="form.enabled" />
          </div>
          <div class="llm-form-row llm-form-row-inline">
            <label class="llm-form-label">{{ t('RTL（从右到左）') }}</label>
            <ElCheckbox v-model="form.isRtl" />
          </div>
        </div>
        <div class="llm-dialog-actions">
          <ElButton @click="dialogVisible = false">{{ t('取消') }}</ElButton>
          <ElButton type="default" @click="save">{{ t('保存') }}</ElButton>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.lang-list-manage {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f5f5;
}
.llm-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px 20px;
  min-height: 0;
  gap: 12px;
}
.llm-toolbar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-shrink: 0;
}
.llm-grid {
  flex: 1;
  min-height: 0;
}
.llm-action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.llm-action-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 15px;
  color: #6b7280;
  padding: 4px;
}
.llm-action-btn:hover {
  color: #2563eb;
}
.llm-action-del:hover {
  color: #dc2626;
}
.llm-yes {
  color: #2563eb;
  font-weight: 600;
}
.llm-no {
  color: #9ca3af;
}

.llm-overlay {
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
.llm-dialog {
  width: 600px;
  background: #fff;
  display: flex;
  flex-direction: column;
  max-height: 80vh;
}
.llm-dialog-title {
  font-size: 17px;
  font-weight: 600;
  color: #1f2937;
  padding: 20px 24px 16px;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.llm-dialog-body {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
  overflow-y: auto;
}
.llm-form-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.llm-form-row-inline {
  flex-direction: row;
  align-items: center;
  gap: 12px;
}
.llm-form-label {
  font-size: 13px;
  color: #4b5563;
  font-weight: 500;
}
.llm-dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #e5e7eb;
  flex-shrink: 0;
}
</style>