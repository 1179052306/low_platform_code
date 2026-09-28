<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import DxDataGrid, { DxColumn } from 'devextreme-vue/data-grid'
import DxTextBox from 'devextreme-vue/text-box'
import DxButton from 'devextreme-vue/button'
import { httpRequest } from '@/utils/request'
import { t, loadModule, type LangInfo } from '@/store/lang'
import { SYSTEM_TEXTS } from '@/store/system-texts'
import { ElMessage, ElMessageBox } from 'element-plus'

defineOptions({ name: 'LangManageView' })

interface LangTextRow {
  id?: number
  locale: string
  textKey: string
  textVal: string
  module: string
  updatedAt?: string
}

const SYSTEM_MODULES = [
  'common',
  'home',
  'login',
  'workbench',
  'system-manage',
  'api-manage',
  'cache-manage',
  'page-manage',
  'lang-manage',
  'designer',
]

function isSystemModule(module: string): boolean {
  return SYSTEM_MODULES.includes(module)
}

const langs = ref<LangInfo[]>([])
const selectedLocale = ref<string>('')
const keyword = ref('')
const allTextList = ref<LangTextRow[]>([])
const loading = ref(false)

const textList = computed(() =>
  allTextList.value.filter((r) => isSystemModule(r.module))
)
const totalCount = computed(() => textList.value.length)

const selectedLangName = computed(
  () => langs.value.find((l) => l.locale === selectedLocale.value)?.name ?? ''
)
const isZh = computed(() => selectedLocale.value === 'zh-CN')

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
    console.error('[lang-manage] loadLangs failed:', e)
  }
}

async function loadTextList() {
  if (!selectedLocale.value || isZh.value) {
    allTextList.value = []
    return
  }
  loading.value = true
  try {
    const res = await httpRequest('/api/lowcode/lang/page', {
      method: 'POST',
      body: JSON.stringify({
        locale: selectedLocale.value,
        keyword: keyword.value,
        page: 1,
        size: 10000,
      }),
    })
    const body = res.data as {
      res?: boolean
      data?: { list?: LangTextRow[]; total?: number }
    }
    if (body && body.res !== false && body.data) {
      allTextList.value = body.data.list ?? []
    }
  } catch (e) {
    console.error('[lang-manage] loadTextList failed:', e)
    ElMessage.error(t('加载翻译列表失败'))
  } finally {
    loading.value = false
  }
}

function onLangSelect(locale: string) {
  selectedLocale.value = locale
}

let searchTimer: ReturnType<typeof setTimeout> | null = null
watch(keyword, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => loadTextList(), 300)
})
watch(selectedLocale, () => loadTextList())

// ================ 单条编辑弹窗 ================
const editDialogVisible = ref(false)
const editForm = ref<LangTextRow>({
  locale: '',
  textKey: '',
  textVal: '',
  module: 'common',
})
const isEditing = ref(false)

function openAdd() {
  isEditing.value = false
  editForm.value = {
    locale: selectedLocale.value,
    textKey: '',
    textVal: '',
    module: 'common',
  }
  editDialogVisible.value = true
}

function openEdit(row: LangTextRow) {
  isEditing.value = true
  editForm.value = { ...row }
  editDialogVisible.value = true
}

async function saveForm() {
  if (!editForm.value.textKey.trim()) {
    ElMessage.warning(t('中文原文不能为空'))
    return
  }
  try {
    const res = await httpRequest('/api/lowcode/lang/save', {
      method: 'POST',
      body: JSON.stringify(editForm.value),
    })
    const body = res.data as { res?: boolean }
    if (body && body.res !== false) {
      ElMessage.success(t('保存成功'))
      editDialogVisible.value = false
      await loadTextList()
    } else {
      ElMessage.error(t('保存失败'))
    }
  } catch (e) {
    ElMessage.error(
      t('保存失败') + ': ' + (e instanceof Error ? e.message : String(e))
    )
  }
}

async function deleteRow(row: LangTextRow) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(t('确认删除该翻译？'), t('提示'), {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    const res = await httpRequest('/api/lowcode/lang/delete', {
      method: 'POST',
      body: JSON.stringify({ id: row.id }),
    })
    const body = res.data as { res?: boolean }
    if (body && body.res !== false) {
      ElMessage.success(t('删除成功'))
      await loadTextList()
    }
  } catch (e) {
    ElMessage.error(t('删除失败'))
  }
}

// ================ 批量翻译系统文本 ================
const batchDialogVisible = ref(false)
const batchRows = ref<
  { textKey: string; textVal: string; module: string; exists: boolean }[]
>([])
const batchLoading = ref(false)
const batchKeyword = ref('')
const autoTranslating = ref(false)

async function openBatch() {
  if (isZh.value) {
    ElMessage.warning(t('请先选择非中文语言'))
    return
  }
  batchDialogVisible.value = true
  batchLoading.value = true
  batchKeyword.value = ''
  try {
    const existingMap = new Map<string, LangTextRow>()
    for (const row of allTextList.value) {
      existingMap.set(row.textKey, row)
    }
    batchRows.value = SYSTEM_TEXTS.map((text) => {
      const existing = existingMap.get(text)
      return {
        textKey: text,
        textVal: existing?.textVal ?? '',
        module: existing?.module ?? 'common',
        exists: !!existing,
      }
    })
  } finally {
    batchLoading.value = false
  }
}

const batchFilteredRows = computed(() => {
  const kw = batchKeyword.value.trim().toLowerCase()
  if (!kw) return batchRows.value
  return batchRows.value.filter(
    (r) =>
      r.textKey.toLowerCase().includes(kw) ||
      r.textVal.toLowerCase().includes(kw)
  )
})

const batchStats = computed(() => {
  const total = batchRows.value.length
  const filled = batchRows.value.filter((r) => r.textVal.trim()).length
  const existing = batchRows.value.filter((r) => r.exists).length
  return { total, filled, existing, newCount: total - existing }
})

async function autoTranslate() {
  const toTranslate = batchRows.value
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
      for (const row of batchRows.value) {
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

async function batchSave() {
  const toSave = batchRows.value.filter((r) => r.textVal.trim())
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
      ElMessage.success(t('批量保存成功') + ` (${toSave.length} ${t('条')})`)
      batchDialogVisible.value = false
      await loadTextList()
    } else {
      ElMessage.error(t('批量保存失败'))
    }
  } catch (e) {
    ElMessage.error(t('批量保存失败'))
  }
}

function batchExport() {
  const data = batchRows.value.map((r) => ({
    textKey: r.textKey,
    textVal: r.textVal,
    module: r.module,
  }))
  const json = JSON.stringify(data, null, 2)
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `system_texts_${selectedLocale.value}_${Date.now()}.json`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(t('导出成功') + ` (${data.length} ${t('条')})`)
}

const batchImportInput = ref<HTMLInputElement>()

function triggerBatchImport() {
  batchImportInput.value?.click()
}

async function batchImportFile(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  try {
    const text = await file.text()
    const data = JSON.parse(text) as {
      textKey: string
      textVal: string
      module?: string
    }[]
    if (!Array.isArray(data)) {
      ElMessage.error(t('数据格式错误：应为数组'))
      return
    }
    const map = new Map<string, { val: string; module?: string }>()
    for (const item of data) {
      if (item.textKey) {
        map.set(item.textKey, { val: item.textVal ?? '', module: item.module })
      }
    }
    let count = 0
    for (const row of batchRows.value) {
      const matched = map.get(row.textKey)
      if (matched && matched.val) {
        row.textVal = matched.val
        if (matched.module) {
          row.module = matched.module
        }
        count++
      }
    }
    ElMessage.success(t('导入成功') + ` (${count} ${t('条')})`)
  } catch (err) {
    ElMessage.error(
      t('导入失败') + ': ' + (err instanceof Error ? err.message : String(err))
    )
  } finally {
    input.value = ''
  }
}

// ================ 导出/导入 ================
async function exportLang() {
  try {
    const res = await httpRequest('/api/lowcode/lang/export', {
      method: 'POST',
      body: JSON.stringify({ locale: selectedLocale.value }),
    })
    const body = res.data as { res?: boolean; data?: unknown[] }
    if (body && body.res !== false && body.data) {
      const json = JSON.stringify(body.data, null, 2)
      const blob = new Blob([json], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `translations_${selectedLocale.value}_${Date.now()}.json`
      a.click()
      URL.revokeObjectURL(url)
      ElMessage.success(t('导出成功'))
    } else {
      ElMessage.error(t('导出失败'))
    }
  } catch (e) {
    ElMessage.error(t('导出失败'))
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
      ElMessage.error(t('数据格式错误：应为数组'))
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
      await loadTextList()
    } else {
      ElMessage.error(t('导入失败'))
    }
  } catch (e) {
    ElMessage.error(
      t('导入失败') + ': ' + (e instanceof Error ? e.message : String(e))
    )
  } finally {
    importLoading.value = false
  }
}

onMounted(() => {
  loadModule('common')
  loadLangs()
})
</script>

<template>
  <div class="lang-manage">
    <div class="lm-body">
      <!-- 语言栏 -->
      <div class="lm-lang-bar">
        <span class="lm-lang-label">{{ t('语言：') }}</span>
        <div class="lm-lang-pills">
          <button
            v-for="lang in langs"
            :key="lang.locale"
            class="lm-lang-pill"
            :class="{
              active: selectedLocale === lang.locale,
              disabled: !lang.enabled,
            }"
            @click="onLangSelect(lang.locale)"
          >
            {{ lang.name }}
            <span v-if="!lang.enabled" class="lm-lang-disabled-tag">{{
              t('禁用')
            }}</span>
          </button>
        </div>
      </div>

      <div v-if="isZh" class="lm-zh-tip">
        {{ t('简体中文为默认语言，无需翻译。请选择其他语言进行编辑。') }}
      </div>

      <div v-else class="lm-content">
        <!-- 工具栏 -->
        <div class="lm-toolbar">
          <div class="lm-search">
            <DxTextBox
              v-model="keyword"
              :placeholder="t('搜索中文原文或译文')"
              width="280px"
              mode="search"
            />
          </div>
          <div class="lm-toolbar-right">
            <span class="lm-count"
              >{{ t('共') }} {{ totalCount }} {{ t('条') }}</span
            >
            <DxButton
              :text="t('导出')"
              type="normal"
              icon="export"
              @click="exportLang"
            />
            <DxButton
              :text="t('导入')"
              type="normal"
              icon="import"
              @click="importDialogVisible = true"
            />
            <DxButton
              :text="t('批量翻译系统文本')"
              type="normal"
              icon="edit"
              @click="openBatch"
            />
            <DxButton :text="t('新增翻译')" type="default" @click="openAdd" />
          </div>
        </div>

        <!-- 翻译列表 -->
        <DxDataGrid
          class="lm-grid"
          :data-source="textList"
          :hover-state-enabled="true"
          :show-borders="true"
          :show-column-lines="false"
          :show-row-lines="true"
          :paging="{ enabled: false }"
          height="100%"
          key-expr="id"
          :load-panel="{ enabled: loading }"
        >
          <DxColumn data-field="textKey" :caption="t('中文原文')" width="300" />
          <DxColumn data-field="textVal" :caption="t('译文')" />
          <DxColumn
            data-field="module"
            :caption="t('模块')"
            width="120"
            alignment="center"
          />
          <DxColumn
            data-field="updatedAt"
            :caption="t('更新时间')"
            width="180"
            alignment="center"
          />
          <DxColumn
            :caption="t('操作')"
            width="120"
            alignment="center"
            cell-template="actionCell"
          />
          <template #actionCell="{ data }">
            <div class="lm-action-row">
              <button
                class="lm-action-btn"
                :title="t('编辑')"
                @click="openEdit(data.data)"
              >
                ✎
              </button>
              <button
                class="lm-action-btn lm-action-del"
                :title="t('删除')"
                @click="deleteRow(data.data)"
              >
                🗑
              </button>
            </div>
          </template>
        </DxDataGrid>
      </div>
    </div>

    <!-- 单条编辑弹窗 -->
    <div
      v-if="editDialogVisible"
      class="lm-edit-overlay"
      @click.self="editDialogVisible = false"
    >
      <div class="lm-edit-dialog">
        <div class="lm-edit-title">
          {{ isEditing ? t('编辑翻译') : t('新增翻译')
          }}<span class="lm-edit-lang">{{ selectedLangName }}</span>
        </div>
        <div class="lm-edit-body">
          <div class="lm-edit-row">
            <label class="lm-edit-label">{{ t('中文原文') }}</label>
            <DxTextBox
              v-model="editForm.textKey"
              :placeholder="t('请输入中文原文')"
              :read-only="isEditing"
            />
          </div>
          <div class="lm-edit-row">
            <label class="lm-edit-label">{{ t('译文') }}</label>
            <DxTextBox
              v-model="editForm.textVal"
              :placeholder="t('请输入译文')"
            />
          </div>
          <div class="lm-edit-row">
            <label class="lm-edit-label">{{ t('模块') }}</label>
            <DxTextBox
              v-model="editForm.module"
              :placeholder="t('如 common、home、page')"
            />
          </div>
        </div>
        <div class="lm-edit-actions">
          <DxButton :text="t('取消')" @click="editDialogVisible = false" />
          <DxButton :text="t('保存')" type="default" @click="saveForm" />
        </div>
      </div>
    </div>

    <!-- 批量翻译弹窗 -->
    <div
      v-if="batchDialogVisible"
      class="lm-edit-overlay"
      @click.self="batchDialogVisible = false"
    >
      <div class="lm-batch-dialog">
        <div class="lm-edit-title">
          {{ t('批量翻译系统文本')
          }}<span class="lm-edit-lang">{{ selectedLangName }}</span>
        </div>

        <div class="lm-batch-toolbar">
          <DxTextBox
            v-model="batchKeyword"
            :placeholder="t('搜索中文原文或译文')"
            width="300px"
            mode="search"
          />
          <div class="lm-batch-toolbar-right">
            <span class="lm-batch-stats">
              {{ t('共') }} {{ batchStats.total }} {{ t('条') }}，
              {{ t('已翻译') }} {{ batchStats.filled }} {{ t('条') }}，
              {{ t('新增') }} {{ batchStats.newCount }} {{ t('条') }}
            </span>
            <DxButton
              :text="t('导出待翻译')"
              type="normal"
              icon="export"
              @click="batchExport"
            />
            <DxButton
              :text="t('导入翻译结果')"
              type="normal"
              icon="import"
              @click="triggerBatchImport"
            />
            <DxButton
              :text="t('自动翻译')"
              type="normal"
              icon="refresh"
              :disabled="autoTranslating"
              @click="autoTranslate"
            />
          </div>
        </div>

        <div v-if="batchLoading" class="lm-import-loading">
          {{ t('加载中...') }}
        </div>
        <div v-else class="lm-batch-table-wrap">
          <table class="lm-import-table">
            <thead>
              <tr>
                <th width="40">{{ t('序号') }}</th>
                <th>{{ t('中文原文') }}</th>
                <th>{{ t('译文') }}</th>
                <th width="60">{{ t('状态') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(row, idx) in batchFilteredRows" :key="idx">
                <td class="lm-td-center">{{ idx + 1 }}</td>
                <td>{{ row.textKey }}</td>
                <td>
                  <input
                    v-model="row.textVal"
                    class="lm-import-input"
                    :placeholder="t('请输入译文')"
                  />
                </td>
                <td class="lm-td-center">
                  <span v-if="row.exists" class="lm-tag lm-tag-exist">{{
                    t('已有')
                  }}</span>
                  <span v-else class="lm-tag lm-tag-new">{{ t('新增') }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="lm-edit-actions">
          <DxButton :text="t('取消')" @click="batchDialogVisible = false" />
          <DxButton
            :text="t('一键保存') + ` (${batchStats.filled})`"
            type="default"
            @click="batchSave"
          />
        </div>
        <input
          ref="batchImportInput"
          type="file"
          accept=".json"
          style="display: none"
          @change="batchImportFile"
        />
      </div>
    </div>

    <!-- 导入弹窗 -->
    <div
      v-if="importDialogVisible"
      class="lm-edit-overlay"
      @click.self="importDialogVisible = false"
    >
      <div class="lm-edit-dialog lm-import-dialog">
        <div class="lm-edit-title">{{ t('导入翻译数据') }}</div>
        <div class="lm-edit-body">
          <div class="lm-edit-row">
            <div class="lm-import-file-row">
              <DxButton
                :text="t('选择JSON文件')"
                type="normal"
                icon="import"
                @click="triggerImportFile"
              />
              <span class="lm-import-hint">{{ t('或直接粘贴JSON数据') }}</span>
            </div>
            <textarea
              v-model="importText"
              class="lm-import-textarea"
              :placeholder="t('请粘贴导出的JSON数据')"
            />
          </div>
        </div>
        <div class="lm-edit-actions">
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
.lang-manage {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f5f5;
}
.lm-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px 20px;
  min-height: 0;
  gap: 12px;
}
.lm-lang-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
  flex-wrap: wrap;
}
.lm-lang-label {
  font-size: 14px;
  color: #4b5563;
  font-weight: 500;
}
.lm-lang-pills {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.lm-lang-pill {
  border: 1px solid #d1d5db;
  background: #fff;
  color: #374151;
  font-size: 13px;
  padding: 6px 16px;
  cursor: pointer;
  border-radius: 0;
  display: flex;
  align-items: center;
  gap: 6px;
}
.lm-lang-pill:hover {
  border-color: #2563eb;
  color: #2563eb;
}
.lm-lang-pill.active {
  background: #2563eb;
  border-color: #2563eb;
  color: #fff;
}
.lm-lang-pill.disabled {
  opacity: 0.55;
}
.lm-lang-disabled-tag {
  font-size: 11px;
  padding: 1px 6px;
  background: #f3f4f6;
  color: #6b7280;
  border-radius: 0;
}
.lm-lang-pill.active .lm-lang-disabled-tag {
  background: rgba(255, 255, 255, 0.2);
  color: #fff;
}
.lm-zh-tip {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  color: #6b7280;
}
.lm-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  gap: 8px;
}
.lm-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
}
.lm-toolbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.lm-count {
  font-size: 13px;
  color: #6b7280;
}
.lm-grid {
  flex: 1;
  min-height: 0;
}
.lm-action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.lm-action-btn {
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 15px;
  color: #6b7280;
  padding: 4px;
}
.lm-action-btn:hover {
  color: #2563eb;
}
.lm-action-del:hover {
  color: #dc2626;
}

.lm-edit-overlay {
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
.lm-edit-dialog {
  width: 600px;
  background: #fff;
  display: flex;
  flex-direction: column;
  max-height: 80vh;
}
.lm-import-dialog {
  width: 800px;
}
.lm-import-file-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}
.lm-import-hint {
  font-size: 13px;
  color: #9ca3af;
}
.lm-edit-title {
  font-size: 17px;
  font-weight: 600;
  color: #1f2937;
  padding: 20px 24px 16px;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.lm-edit-lang {
  font-size: 13px;
  font-weight: 400;
  color: #6b7280;
}
.lm-edit-body {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  flex: 1;
  overflow-y: auto;
}
.lm-edit-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.lm-edit-label {
  font-size: 13px;
  color: #4b5563;
  font-weight: 500;
}
.lm-edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid #e5e7eb;
  flex-shrink: 0;
}
.lm-import-textarea {
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
.lm-import-textarea:focus {
  border-color: #2563eb;
}

/* 批量翻译弹窗 */
.lm-batch-dialog {
  width: 1000px;
  background: #fff;
  display: flex;
  flex-direction: column;
  max-height: 85vh;
}
.lm-batch-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  flex-shrink: 0;
  border-bottom: 1px solid #e5e7eb;
  gap: 16px;
}
.lm-batch-toolbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.lm-batch-stats {
  font-size: 13px;
  color: #6b7280;
}
.lm-batch-table-wrap {
  flex: 1;
  overflow-y: auto;
  padding: 0 24px;
}
.lm-import-loading {
  text-align: center;
  padding: 40px 0;
  font-size: 15px;
  color: #6b7280;
}
.lm-import-table {
  width: 100%;
  border-collapse: collapse;
}
.lm-import-table th {
  background: #f9fafb;
  font-size: 13px;
  color: #4b5563;
  font-weight: 600;
  padding: 10px 12px;
  border-bottom: 1px solid #e5e7eb;
  text-align: left;
}
.lm-import-table td {
  font-size: 13px;
  color: #374151;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
}
.lm-import-table tr:hover td {
  background: #f9fafb;
}
.lm-td-center {
  text-align: center;
}
.lm-import-input {
  width: 100%;
  border: 1px solid #d1d5db;
  padding: 6px 10px;
  font-size: 13px;
  outline: none;
  border-radius: 0;
}
.lm-import-input:focus {
  border-color: #2563eb;
}
.lm-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 0;
}
.lm-tag-exist {
  background: #f0f7ff;
  color: #2563eb;
}
.lm-tag-new {
  background: #fef3c7;
  color: #d97706;
}
</style>
