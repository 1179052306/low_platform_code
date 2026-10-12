<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'

import DxDataGrid, { DxToolbar, DxItem } from 'devextreme-vue/data-grid'
import DxTextBox from 'devextreme-vue/text-box'
import DxSelectBox from 'devextreme-vue/select-box'
import DxButton from 'devextreme-vue/button'
import { ElTabs, ElTabPane, ElMessage, ElMessageBox } from 'element-plus'
import AdvancedSearchPanel from '@/components/renderer/AdvancedSearchPanel.vue'
import type {
  GridColumn,
  SearchCondition,
} from '@/components/renderer/AdvancedSearchPanel.vue'
import type { ApiService } from '@/api/registry'
import type { GatewayPermissionRule, FieldPermissionRule } from '@/types/schema'
import {
  listApiServices,
  addApiService,
  updateApiService,
  listGatewayPermissions,
  saveGatewayPermission,
  deleteGatewayPermission,
  listFieldPermissions,
  saveFieldPermission,
  deleteFieldPermission,
  listRoles,
} from '@/store/api-store'
import GatewayPermissionDialog from '@/components/designer/GatewayPermissionDialog.vue'
import FieldPermissionDialog from '@/components/designer/FieldPermissionDialog.vue'
import { t, loadModule } from '@/store/lang'

const activeTab = ref('apiList')

const apiList = ref<ApiService[]>([])

// ================ 高级查询 ================
const advSearchConditions = ref<SearchCondition[]>([])

const advColumns: GridColumn[] = [
  { dataField: 'id', caption: t('服务ID'), dataType: 'string' },
  { dataField: 'description', caption: t('描述'), dataType: 'string' },
  { dataField: 'url', caption: 'URL', dataType: 'string' },
  { dataField: 'method', caption: t('方法'), dataType: 'string' },
  { dataField: 'permCode', caption: t('权限码'), dataType: 'string' },
  { dataField: 'group', caption: t('分组'), dataType: 'string' },
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
onMounted(() => {
  loadModule('api')
  refreshList()
  refreshGatewayPerms()
  refreshFieldPerms()
  refreshRoles()
})

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

// ================ 网关权限矩阵 ================
const gatewayPermList = ref<GatewayPermissionRule[]>([])
const showGatewayPermDialog = ref(false)
const editingGatewayRule = ref<GatewayPermissionRule | undefined>(undefined)
const selectedGatewayRule = ref<GatewayPermissionRule | undefined>(undefined)
const roles = ref<{ code: string; name: string }[]>([])

async function refreshGatewayPerms() {
  try {
    gatewayPermList.value = await listGatewayPermissions()
  } catch {
    gatewayPermList.value = []
  }
}

function startAddGatewayPerm() {
  editingGatewayRule.value = undefined
  showGatewayPermDialog.value = true
}

function startEditGatewayPerm(rule: GatewayPermissionRule) {
  editingGatewayRule.value = { ...rule }
  showGatewayPermDialog.value = true
}

function onGatewayPermSelectionChanged(e: { selectedRowsData: GatewayPermissionRule[] }) {
  selectedGatewayRule.value = e.selectedRowsData.length > 0 ? e.selectedRowsData[0] : undefined
}

async function onSaveGatewayPerm(rule: GatewayPermissionRule) {
  try {
    await saveGatewayPermission(rule)
    await refreshGatewayPerms()
    ElMessage.success(t('保存成功'))
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : String(err))
  }
}

async function onDeleteGatewayPerm(rule: GatewayPermissionRule) {
  try {
    await ElMessageBox.confirm(t('确认删除此网关权限规则？'), t('提示'), {
      type: 'warning',
    })
    await deleteGatewayPermission(rule.id)
    await refreshGatewayPerms()
    ElMessage.success(t('删除成功'))
  } catch {
    /* 取消删除 */
  }
}

// ================ 字段权限 ================
const fieldPermList = ref<FieldPermissionRule[]>([])
const showFieldPermDialog = ref(false)
const editingFieldRule = ref<FieldPermissionRule | undefined>(undefined)
const selectedFieldRule = ref<FieldPermissionRule | undefined>(undefined)

async function refreshFieldPerms() {
  try {
    fieldPermList.value = await listFieldPermissions()
  } catch {
    fieldPermList.value = []
  }
}

function startAddFieldPerm() {
  editingFieldRule.value = undefined
  showFieldPermDialog.value = true
}

function startEditFieldPerm(rule: FieldPermissionRule) {
  editingFieldRule.value = { ...rule }
  showFieldPermDialog.value = true
}

function onFieldPermSelectionChanged(e: { selectedRowsData: FieldPermissionRule[] }) {
  selectedFieldRule.value = e.selectedRowsData.length > 0 ? e.selectedRowsData[0] : undefined
}

async function onSaveFieldPerm(rule: FieldPermissionRule) {
  try {
    await saveFieldPermission(rule)
    await refreshFieldPerms()
    ElMessage.success(t('保存成功'))
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : String(err))
  }
}

async function onDeleteFieldPerm(rule: FieldPermissionRule) {
  try {
    await ElMessageBox.confirm(t('确认删除此字段权限规则？'), t('提示'), {
      type: 'warning',
    })
    await deleteFieldPermission(rule.id)
    await refreshFieldPerms()
    ElMessage.success(t('删除成功'))
  } catch {
    /* 取消删除 */
  }
}

// ================ 角色列表加载 ================
async function refreshRoles() {
  try {
    roles.value = await listRoles()
  } catch {
    roles.value = []
  }
}
</script>

<template>
  <div class="api-manage">
    <ElTabs v-model="activeTab" class="am-tabs">
      <!-- Tab 1: 非网关 API 登记（现有逻辑保留） -->
      <ElTabPane :label="t('API登记')" name="apiList">
        <div class="am-body">
          <!-- 左侧：API 列表 -->
          <div class="am-list-panel">
            <AdvancedSearchPanel
              :columns="advColumns"
              grid-id="api_manage_view"
              @search="onAdvSearch"
              @reset="onAdvSearchReset"
            />
            <DxDataGrid
              class="am-grid"
              :data-source="filteredList"
              :columns="[
                { dataField: 'id', caption: t('服务ID'), width: 140 },
                { dataField: 'description', caption: t('描述') },
                { dataField: 'url', caption: 'URL' },
                { dataField: 'method', caption: t('方法'), width: 70 },
                { dataField: 'permCode', caption: t('权限码'), width: 110 },
                { dataField: 'group', caption: t('分组'), width: 90 },
              ]"
              :selection="{ mode: 'single', showCheckBoxesMode: 'none' }"
              :hover-state-enabled="true"
              :show-borders="true"
              :show-column-lines="false"
              :show-row-lines="true"
              :paging="{ enabled: false }"
              height="100%"
              key-expr="id"
              @selection-changed="
                (e: any) => {
                  if (e.selectedRowsData.length > 0) startEdit(e.selectedRowsData[0])
                }
              "
            >
              <DxToolbar>
                <DxItem location="after">
                  <template #default>
                    <DxButton
                      :text="t('新增 API')"
                      type="default"
                      @click="startAdd"
                    />
                  </template>
                </DxItem>
                <DxItem name="columnChooser" />
                <DxItem name="searchPanel" :visible="false" />
              </DxToolbar>
            </DxDataGrid>
          </div>
          <!-- 右侧：新增/编辑表单 -->
          <div class="am-form-panel" v-if="editing">
            <div class="am-form-title">
              {{ isAdding ? t('新增 API 服务') : t('修改 API 服务') }}
            </div>
            <div class="am-form-body">
              <div class="am-form-row">
                <label class="am-required">{{ t('服务 ID') }}</label>
                <DxTextBox
                  :value="form.id"
                  :read-only="!isAdding"
                  :placeholder="t('如 order.list')"
                  @value-changed="(e: any) => (form.id = e.value)"
                />
              </div>
              <div class="am-form-row">
                <label class="am-required">URL</label>
                <DxTextBox
                  :value="form.url"
                  placeholder="/api/order/list"
                  @value-changed="(e: any) => (form.url = e.value)"
                />
              </div>
              <div class="am-form-row">
                <label>{{ t('请求方法') }}</label>
                <DxSelectBox
                  :items="methodOptions"
                  display-expr="label"
                  value-expr="value"
                  :value="form.method"
                  @value-changed="(e: any) => (form.method = e.value)"
                />
              </div>
              <div class="am-form-row">
                <label>{{ t('权限码') }}</label>
                <DxTextBox
                  :value="form.permCode"
                  :placeholder="t('如 order:view')"
                  @value-changed="(e: any) => (form.permCode = e.value)"
                />
              </div>
              <div class="am-form-row">
                <label>{{ t('描述') }}</label>
                <DxTextBox
                  :value="form.description"
                  :placeholder="t('如 订单列表')"
                  @value-changed="(e: any) => (form.description = e.value)"
                />
              </div>
              <div class="am-form-row">
                <label>{{ t('分组') }}</label>
                <DxTextBox
                  :value="form.group"
                  :placeholder="t('如 订单')"
                  @value-changed="(e: any) => (form.group = e.value)"
                />
              </div>
              <div class="am-form-error" v-if="formError">{{ formError }}</div>
            </div>
            <div class="am-form-actions">
              <DxButton :text="t('取消')" @click="cancelEdit" />
              <DxButton :text="t('保存')" type="default" @click="saveForm" />
            </div>
          </div>
          <div class="am-form-panel am-form-empty" v-else>
            <span>{{
              t('点击「+ 新增 API」添加，或点击列表中的 API 进行修改')
            }}</span>
          </div>
        </div>
      </ElTabPane>

      <!-- Tab 2: 网关权限矩阵（新增） -->
      <ElTabPane :label="t('网关权限矩阵')" name="gatewayPermission">
        <div class="am-tab-body">
          <DxDataGrid
            class="am-grid"
            :data-source="gatewayPermList"
            :columns="[
              { dataField: 'bizCode', caption: t('业务编码'), width: 140 },
              { dataField: 'action', caption: t('操作动作'), width: 120 },
              { dataField: 'allowedRoles', caption: t('允许角色') },
              { dataField: 'deniedRoles', caption: t('拒绝角色') },
              { dataField: 'description', caption: t('描述') },
              { dataField: 'enabled', caption: t('启用'), width: 70, dataType: 'boolean' },
            ]"
            :selection="{ mode: 'single', showCheckBoxesMode: 'none' }"
            :hover-state-enabled="true"
            :show-borders="true"
            :show-column-lines="false"
            :show-row-lines="true"
            :paging="{ enabled: false }"
            height="100%"
            key-expr="id"
            @selection-changed="onGatewayPermSelectionChanged"
          >
            <DxToolbar>
              <DxItem location="after">
                <template #default>
                  <DxButton :text="t('新增权限规则')" type="default" @click="startAddGatewayPerm" />
                </template>
              </DxItem>
              <DxItem location="after">
                <template #default>
                  <DxButton
                    :text="t('编辑')"
                    :disabled="!selectedGatewayRule"
                    @click="() => { if (selectedGatewayRule) startEditGatewayPerm(selectedGatewayRule) }"
                  />
                </template>
              </DxItem>
              <DxItem location="after">
                <template #default>
                  <DxButton
                    :text="t('删除')"
                    :disabled="!selectedGatewayRule"
                    @click="() => { if (selectedGatewayRule) onDeleteGatewayPerm(selectedGatewayRule) }"
                  />
                </template>
              </DxItem>
              <DxItem name="columnChooser" />
              <DxItem name="searchPanel" :visible="false" />
            </DxToolbar>
          </DxDataGrid>
        </div>
      </ElTabPane>

      <!-- Tab 3: 字段权限（新增） -->
      <ElTabPane :label="t('字段权限')" name="fieldPermission">
        <div class="am-tab-body">
          <DxDataGrid
            class="am-grid"
            :data-source="fieldPermList"
            :columns="[
              { dataField: 'bizCode', caption: t('业务编码'), width: 140 },
              { dataField: 'fieldName', caption: t('字段名'), width: 140 },
              { dataField: 'permType', caption: t('权限类型'), width: 100 },
              { dataField: 'roles', caption: t('适用角色') },
              { dataField: 'description', caption: t('描述') },
              { dataField: 'enabled', caption: t('启用'), width: 70, dataType: 'boolean' },
            ]"
            :selection="{ mode: 'single', showCheckBoxesMode: 'none' }"
            :hover-state-enabled="true"
            :show-borders="true"
            :show-column-lines="false"
            :show-row-lines="true"
            :paging="{ enabled: false }"
            height="100%"
            key-expr="id"
            @selection-changed="onFieldPermSelectionChanged"
          >
            <DxToolbar>
              <DxItem location="after">
                <template #default>
                  <DxButton :text="t('新增字段权限')" type="default" @click="startAddFieldPerm" />
                </template>
              </DxItem>
              <DxItem location="after">
                <template #default>
                  <DxButton
                    :text="t('编辑')"
                    :disabled="!selectedFieldRule"
                    @click="() => { if (selectedFieldRule) startEditFieldPerm(selectedFieldRule) }"
                  />
                </template>
              </DxItem>
              <DxItem location="after">
                <template #default>
                  <DxButton
                    :text="t('删除')"
                    :disabled="!selectedFieldRule"
                    @click="() => { if (selectedFieldRule) onDeleteFieldPerm(selectedFieldRule) }"
                  />
                </template>
              </DxItem>
              <DxItem name="columnChooser" />
              <DxItem name="searchPanel" :visible="false" />
            </DxToolbar>
          </DxDataGrid>
        </div>
      </ElTabPane>
    </ElTabs>

    <GatewayPermissionDialog
      v-model:visible="showGatewayPermDialog"
      :rule="editingGatewayRule"
      :roles="roles"
      @save="onSaveGatewayPerm"
    />
    <FieldPermissionDialog
      v-model:visible="showFieldPermDialog"
      :rule="editingFieldRule"
      :roles="roles"
      @save="onSaveFieldPerm"
    />
  </div>
</template>

<style scoped>
.api-manage {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f5f5;
}

.am-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 12px;
}
.am-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
}
.am-tabs :deep(.el-tab-pane) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.am-tab-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 6px;
  overflow: hidden;
}

.am-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  padding: 12px;
  gap: 12px;
}
.am-list-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 6px;
  overflow: hidden;
}
.am-list-toolbar {
  display: flex;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid #f0f0f0;
}
.am-search {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 13px;
}
.am-add-btn {
  padding: 6px 14px;
  background: #409eff;
  color: #fff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.am-add-btn:hover {
  background: #66b1ff;
}
.am-grid {
  flex: 1;
  overflow: hidden;
}
.am-form-panel {
  width: 340px;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 6px;
  padding: 16px;
}
.am-form-empty {
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 13px;
}
.am-form-title {
  font-size: 14px;
  font-weight: 600;
  padding-bottom: 10px;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 14px;
}
.am-form-body {
  flex: 1;
  overflow-y: auto;
}
.am-form-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 14px;
}
.am-form-row label {
  font-size: 12px;
  color: #666;
}
.am-required::before {
  content: '*';
  color: #ef4444;
  margin-right: 4px;
  font-weight: 700;
}
.am-form-error {
  color: #f56c6c;
  font-size: 12px;
  margin-top: 4px;
}
.am-form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid #f0f0f0;
}
</style>