<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import DxDataGrid from 'devextreme-vue/data-grid'
import DxTextBox from 'devextreme-vue/text-box'
import DxSelectBox from 'devextreme-vue/select-box'

import DxButton from 'devextreme-vue/button'
import AdvancedSearchPanel from '../renderer/AdvancedSearchPanel.vue'
import type {
  GridColumn,
  SearchCondition,
} from '../renderer/AdvancedSearchPanel.vue'
import type { ApiService } from '@/api/registry'
import {
  listApiServices,
  addApiService,
  updateApiService,
} from '@/store/api-store'
import { t } from '@/store/lang'

const props = defineProps<{
  visible: boolean
  /** 当前选中的 serviceId */
  modelValue?: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'update:modelValue': [value: string]
  select: [serviceId: string, service: ApiService]
}>()

// ================ API 列表 ================
const apiList = ref<ApiService[]>([])
const selectedServiceId = ref('')

// ================ 高级查询 ================
const advSearchConditions = ref<SearchCondition[]>([])

const advColumns: GridColumn[] = [
  { dataField: 'id', caption: t('服务ID'), dataType: 'string' },
  { dataField: 'description', caption: t('描述'), dataType: 'string' },
  { dataField: 'url', caption: 'URL', dataType: 'string' },
  { dataField: 'method', caption: t('方法'), dataType: 'string' },
  { dataField: 'permCode', caption: t('权限码'), dataType: 'string' },
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

const filteredList = computed(() => {
  const conds = advSearchConditions.value.filter(
    (c) => c.field && c.operator && c.value !== ''
  )
  if (conds.length === 0) return apiList.value
  return apiList.value.filter((s) =>
    conds.every((c) =>
      matchCondition((s as unknown as Record<string, unknown>)[c.field], c)
    )
  )
})

async function refreshList() {
  try {
    apiList.value = await listApiServices()
  } catch {
    /* ignore */
  }
}

// ================ 编辑表单 ================
const editing = ref(false)
const isAdding = ref(false)
const form = ref<ApiService>({
  id: '',
  url: '',
  method: 'GET',
  permCode: '',
  description: '',
  group: '',
})
const formError = ref('')

const methodOptions = [
  { label: 'GET', value: 'GET' },
  { label: 'POST', value: 'POST' },
  { label: 'PUT', value: 'PUT' },
  { label: 'DELETE', value: 'DELETE' },
  { label: 'PATCH', value: 'PATCH' },
]

function startAdd() {
  isAdding.value = true
  editing.value = true
  form.value = {
    id: '',
    url: '',
    method: 'GET',
    permCode: '',
    description: '',
    group: '',
  }
  formError.value = ''
}

function startEdit(service: ApiService) {
  isAdding.value = false
  editing.value = true
  form.value = { ...service }
  formError.value = ''
}

function cancelEdit() {
  editing.value = false
  formError.value = ''
}

async function saveForm() {
  formError.value = ''
  const s = form.value
  if (!s.id.trim()) {
    formError.value = t('服务 ID 不能为空')
    return
  }
  if (!s.url.trim()) {
    formError.value = t('URL 不能为空')
    return
  }
  try {
    if (isAdding.value) {
      await addApiService({
        id: s.id.trim(),
        url: s.url.trim(),
        method: s.method,
        permCode: s.permCode?.trim() || undefined,
        description: s.description?.trim() || undefined,
        group: s.group?.trim() || undefined,
      })
    } else {
      await updateApiService({
        id: s.id.trim(),
        url: s.url.trim(),
        method: s.method,
        permCode: s.permCode?.trim() || undefined,
        description: s.description?.trim() || undefined,
        group: s.group?.trim() || undefined,
      })
    }
    await refreshList()
    editing.value = false
  } catch (err) {
    formError.value = err instanceof Error ? err.message : String(err)
  }
}

// ================ 选择确认 ================
const selectedServiceDesc = computed(() => {
  const s = apiList.value.find((x) => x.id === selectedServiceId.value)
  return s?.description || ''
})

function onSelectionChanged(e: { selectedRowsData: ApiService[] }) {
  if (e.selectedRowsData.length > 0) {
    selectedServiceId.value = e.selectedRowsData[0].id
  }
}

/** 双击行直接确认选择 */
function onRowDblClick(e: { data?: ApiService }) {
  if (e.data) {
    selectedServiceId.value = e.data.id
    confirmSelect()
  }
}

function closeDialog() {
  emit('update:visible', false)
}

function confirmSelect() {
  const service = apiList.value.find((s) => s.id === selectedServiceId.value)
  if (service) {
    emit('update:modelValue', service.id)
    emit('select', service.id, service)
  }
  emit('update:visible', false)
}

function clearSelection() {
  selectedServiceId.value = ''
  emit('update:modelValue', '')
}

/** 查看详情弹窗 */
const detailVisible = ref(false)
const detailService = computed(() =>
  apiList.value.find((s) => s.id === selectedServiceId.value)
)
function openDetail() {
  if (selectedServiceId.value) detailVisible.value = true
}

// ================ 弹窗打开时初始化 ================
watch(
  () => props.visible,
  async (v) => {
    if (v) {
      await refreshList()
      selectedServiceId.value = props.modelValue || ''
      editing.value = false
    }
  }
)
</script>

<template>
  <Teleport to="body" v-if="visible">
    <div class="asd-overlay">
      <div class="asd-dialog">
        <div class="asd-header">
          <span>{{ t('选择 API 服务') }}</span>
          <button class="asd-close" @click="closeDialog">×</button>
        </div>
        <div class="asd-body">
          <!-- 左侧：API 列表 -->
          <div class="asd-list-panel">
            <div class="asd-list-toolbar">
              <div class="asd-selected-bar" v-if="selectedServiceId">
                <span class="asd-bar-icon">✓</span>
                <span>{{ t('已选：') }}{{ selectedServiceId }}</span>
                <span class="asd-bar-desc" v-if="selectedServiceDesc">
                  {{ t('（') }}{{ selectedServiceDesc }}{{ t('）') }}
                </span>
              </div>
              <div class="asd-selected-bar asd-bar-empty" v-else>
                {{ t('未选择 API 服务') }}
              </div>
              <button
                v-if="selectedServiceId"
                class="asd-view-btn"
                @click="openDetail"
              >
                {{ t('查看详情') }}
              </button>
              <button class="asd-add-btn" @click="startAdd">
                {{ t('+ 新增') }}
              </button>
            </div>
            <AdvancedSearchPanel
              :columns="advColumns"
              grid-id="api_select_dialog"
              @search="onAdvSearch"
              @reset="onAdvSearchReset"
            />
            <DxDataGrid
              class="asd-grid"
              :data-source="filteredList"
              :selected-row-keys="selectedServiceId ? [selectedServiceId] : []"
              :columns="[
                { dataField: 'id', caption: t('服务ID'), width: 140 },
                { dataField: 'description', caption: t('描述') },
                { dataField: 'url', caption: 'URL' },
                { dataField: 'method', caption: t('方法'), width: 70 },
                { dataField: 'permCode', caption: t('权限码'), width: 100 },
              ]"
              :selection="{ mode: 'single', showCheckBoxesMode: 'none' }"
              :hover-state-enabled="true"
              :show-borders="true"
              :show-column-lines="false"
              :show-row-lines="true"
              :paging="{ enabled: false }"
              :focused-row-enabled="true"
              :focused-row-key="selectedServiceId || null"
              height="100%"
              key-expr="id"
              @selection-changed="onSelectionChanged"
              @row-dbl-click="(e: any) => onRowDblClick(e)"
            >
              <template #cellTemplate="{ data }">
                <span>{{ data.value }}</span>
              </template>
            </DxDataGrid>
          </div>
          <!-- 右侧：新增/编辑表单 -->
          <div class="asd-form-panel" v-if="editing">
            <div class="asd-form-title">
              {{ isAdding ? t('新增 API 服务') : t('修改 API 服务') }}
            </div>
            <div class="asd-form-body">
              <div class="asd-form-row">
                <label class="asd-required">{{ t('服务 ID') }}</label>
                <DxTextBox
                  :value="form.id"
                  :read-only="!isAdding"
                  :placeholder="t('如 order.list')"
                  @value-changed="(e: any) => (form.id = e.value)"
                />
              </div>
              <div class="asd-form-row">
                <label class="asd-required">URL</label>
                <DxTextBox
                  :value="form.url"
                  placeholder="/api/order/list"
                  @value-changed="(e: any) => (form.url = e.value)"
                />
              </div>
              <div class="asd-form-row">
                <label>{{ t('请求方法') }}</label>
                <DxSelectBox
                  :items="methodOptions"
                  display-expr="label"
                  value-expr="value"
                  :value="form.method"
                  @value-changed="(e: any) => (form.method = e.value)"
                />
              </div>
              <div class="asd-form-row">
                <label>{{ t('权限码') }}</label>
                <DxTextBox
                  :value="form.permCode"
                  :placeholder="t('如 order:view')"
                  @value-changed="(e: any) => (form.permCode = e.value)"
                />
              </div>
              <div class="asd-form-row">
                <label>{{ t('描述') }}</label>
                <DxTextBox
                  :value="form.description"
                  :placeholder="t('如 订单列表')"
                  @value-changed="(e: any) => (form.description = e.value)"
                />
              </div>
              <div class="asd-form-row">
                <label>{{ t('分组') }}</label>
                <DxTextBox
                  :value="form.group"
                  :placeholder="t('如 订单')"
                  @value-changed="(e: any) => (form.group = e.value)"
                />
              </div>
              <div class="asd-form-error" v-if="formError">{{ formError }}</div>
            </div>
            <div class="asd-form-actions">
              <DxButton :text="t('取消')" @click="cancelEdit" />
              <DxButton :text="t('保存')" type="default" @click="saveForm" />
            </div>
          </div>
          <!-- 右侧占位（未编辑时） -->
          <div class="asd-form-panel asd-form-empty" v-else>
            <span>{{
              t('点击「+ 新增」添加 API，或选择列表中的 API 后点「修改」')
            }}</span>
            <button
              v-if="selectedServiceId"
              class="asd-edit-btn"
              @click="
                startEdit(
                  apiList.find((s) => s.id === selectedServiceId)!
                )
              "
            >
              {{ t('修改当前选中') }}
            </button>
          </div>
        </div>
        <div class="asd-footer">
          <div class="asd-selected-info" v-if="selectedServiceId">
            <span class="asd-selected-icon">✓</span>
            <span>{{ t('已选：') }}{{ selectedServiceId }}</span>
            <span class="asd-selected-desc" v-if="selectedServiceDesc">
              {{ t('（') }}{{ selectedServiceDesc }}{{ t('）') }}
            </span>
          </div>
          <div class="asd-selected-info asd-selected-empty" v-else>
            <span>{{ t('未选择 API 服务') }}</span>
          </div>
          <div class="asd-footer-actions">
            <button class="asd-btn" @click="clearSelection">
              {{ t('清除') }}
            </button>
            <button class="asd-btn" @click="closeDialog">
              {{ t('取消') }}
            </button>
            <button
              class="asd-btn asd-btn-primary"
              :disabled="!selectedServiceId"
              @click="confirmSelect"
            >
              {{ t('确定') }}
            </button>
          </div>
        </div>
        <!-- 查看详情弹窗 -->
        <div
          class="asd-detail-overlay"
          v-if="detailVisible && detailService"
          @click="detailVisible = false"
        >
          <div class="asd-detail-dialog" @click.stop>
            <div class="asd-detail-header">
              <span>{{ t('API 服务详情') }}</span>
              <button class="asd-close" @click="detailVisible = false">
                ×
              </button>
            </div>
            <div class="asd-detail-body">
              <div class="asd-detail-row">
                <label>{{ t('服务 ID') }}</label>
                <span>{{ detailService.id }}</span>
              </div>
              <div class="asd-detail-row">
                <label>URL</label>
                <span>{{ detailService.url }}</span>
              </div>
              <div class="asd-detail-row">
                <label>{{ t('请求方法') }}</label>
                <span>{{ detailService.method || 'GET' }}</span>
              </div>
              <div class="asd-detail-row">
                <label>{{ t('权限码') }}</label>
                <span>{{ detailService.permCode || '-' }}</span>
              </div>
              <div class="asd-detail-row">
                <label>{{ t('描述') }}</label>
                <span>{{ detailService.description || '-' }}</span>
              </div>
              <div class="asd-detail-row">
                <label>{{ t('分组') }}</label>
                <span>{{ detailService.group || '-' }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.asd-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.45);
  z-index: 20000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.asd-dialog {
  background: #fff;
  border-radius: 8px;
  width: 900px;
  max-width: 95vw;
  height: 580px;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}
.asd-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
  font-size: 15px;
  font-weight: 600;
}
.asd-close {
  border: none;
  background: none;
  font-size: 18px;
  color: #999;
  cursor: pointer;
}
.asd-close:hover {
  color: #333;
}
.asd-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}
.asd-list-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  border-right: 1px solid #eee;
}
.asd-list-toolbar {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
  align-items: center;
}
.asd-selected-bar {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #fff;
  background: #67c23a;
  padding: 4px 10px;
  border-radius: 4px;
}
.asd-bar-icon {
  font-weight: 700;
}
.asd-bar-desc {
  opacity: 0.9;
}
.asd-bar-empty {
  background: #f0f0f0;
  color: #999;
}
.asd-search {
  flex: 1;
  padding: 4px 8px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 13px;
}
.asd-add-btn {
  padding: 4px 12px;
  background: #409eff;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.asd-add-btn:hover {
  background: #66b1ff;
}
.asd-view-btn {
  padding: 4px 12px;
  background: #909399;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.asd-view-btn:hover {
  background: #a6a9ad;
}
.asd-detail-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 21000;
  display: flex;
  align-items: center;
  justify-content: center;
}
.asd-detail-dialog {
  background: #fff;
  border-radius: 8px;
  width: 460px;
  max-width: 90vw;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
}
.asd-detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
  font-size: 15px;
  font-weight: 600;
}
.asd-detail-body {
  padding: 16px;
}
.asd-detail-row {
  display: flex;
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
}
.asd-detail-row:last-child {
  border-bottom: none;
}
.asd-detail-row label {
  width: 90px;
  flex-shrink: 0;
  color: #999;
  font-size: 13px;
}
.asd-detail-row span {
  flex: 1;
  color: #333;
  font-size: 13px;
  word-break: break-all;
}
.asd-grid {
  flex: 1;
  overflow: hidden;
}
.asd-form-panel {
  width: 320px;
  display: flex;
  flex-direction: column;
  padding: 12px 16px;
}
.asd-form-empty {
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 13px;
  gap: 12px;
}
.asd-edit-btn {
  padding: 6px 16px;
  background: #67c23a;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.asd-form-title {
  font-size: 14px;
  font-weight: 600;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 12px;
}
.asd-form-body {
  flex: 1;
  overflow-y: auto;
}
.asd-form-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
}
.asd-form-row label {
  font-size: 12px;
  color: #666;
}
.asd-required::before {
  content: '*';
  color: #ef4444;
  margin-right: 4px;
  font-weight: 700;
}
.asd-form-error {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 4px;
}
.asd-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 8px;
  border-top: 1px solid #f0f0f0;
}
.asd-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  border-top: 1px solid #eee;
}
.asd-selected-info {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #fff;
  background: #67c23a;
  padding: 4px 12px;
  border-radius: 4px;
}
.asd-selected-icon {
  font-weight: 700;
}
.asd-selected-desc {
  opacity: 0.9;
}
.asd-selected-empty {
  background: #f0f0f0;
  color: #999;
}
.asd-footer-actions {
  display: flex;
  gap: 8px;
}
.asd-btn {
  padding: 6px 16px;
  border: 1px solid #ddd;
  background: #fff;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.asd-btn:hover {
  border-color: #409eff;
  color: #409eff;
}
.asd-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.asd-btn-primary {
  background: #409eff;
  color: #fff;
  border-color: #409eff;
}
.asd-btn-primary:hover {
  background: #66b1ff;
  color: #fff;
}
</style>