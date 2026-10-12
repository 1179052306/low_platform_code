<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import {
  ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElSwitch,
  ElButton, ElMessage, ElMessageBox, ElTabs, ElTabPane, ElEmpty,
} from 'element-plus'
import DxDataGrid, { DxColumn } from 'devextreme-vue/data-grid'
import { t } from '@/store/lang'
import {
  listBizMeta, saveBizMeta, deleteBizMeta,
  listFieldMeta, saveFieldMeta, deleteFieldMeta,
  listActionMeta, saveActionMeta, deleteActionMeta,
  listStateMachineMeta, saveStateMachineMeta, deleteStateMachineMeta,
} from '@/store/biz-meta'
import {
  type BizMeta, type FieldMeta, type ActionMeta, type StateMachineMeta,
  COLUMN_TYPES, DATA_TYPES, STRATEGY_TYPES,
} from '@/types/biz-meta'

const bizList = ref<BizMeta[]>([])
const selectedBiz = ref<BizMeta | null>(null)
const fieldList = ref<FieldMeta[]>([])
const actionList = ref<ActionMeta[]>([])
const stateList = ref<StateMachineMeta[]>([])
const activeTab = ref('field')

const showBizDialog = ref(false)
const editingBiz = ref<BizMeta | null>(null)

const showFieldDialog = ref(false)
const editingField = ref<FieldMeta | null>(null)

const showActionDialog = ref(false)
const editingAction = ref<ActionMeta | null>(null)

const showStateDialog = ref(false)
const editingState = ref<StateMachineMeta | null>(null)

async function refreshBiz() {
  bizList.value = await listBizMeta()
}
onMounted(() => { refreshBiz() })

function onBizSelect(e: { selectedRowsData: BizMeta[] }) {
  const row = e.selectedRowsData[0]
  selectedBiz.value = row ? { ...row } : null
}

watch(selectedBiz, async (biz) => {
  if (!biz) { fieldList.value = []; actionList.value = []; stateList.value = []; return }
  fieldList.value = await listFieldMeta(biz.bizCode)
  actionList.value = await listActionMeta(biz.bizCode)
  stateList.value = await listStateMachineMeta(biz.bizCode)
})

function addBiz() {
  editingBiz.value = { id: '', bizCode: '', bizName: '', mainTable: '', mainTableAlias: 'T', statusField: '', logicDeleteField: 'deleted', version: 1, remark: '' }
  showBizDialog.value = true
}
function editBiz(row: BizMeta) {
  editingBiz.value = { ...row }
  showBizDialog.value = true
}
async function saveBiz() {
  if (!editingBiz.value) return
  const b = editingBiz.value
  if (!b.bizCode || !b.bizName || !b.mainTable) { ElMessage.warning(t('请填写业务编码、名称、主表')); return }
  try {
    await saveBizMeta(b)
    ElMessage.success(t('保存成功'))
    showBizDialog.value = false
    await refreshBiz()
  } catch (e) { ElMessage.error((e as Error).message) }
}
async function removeBiz(row: BizMeta) {
  try { await ElMessageBox.confirm(t('确认删除该业务对象及其所有字段/动作/状态机配置？'), t('提示'), { type: 'warning' }) }
  catch { return }
  try { await deleteBizMeta(row.bizCode); ElMessage.success(t('删除成功')); await refreshBiz() }
  catch (e) { ElMessage.error((e as Error).message) }
}

function addField() {
  if (!selectedBiz.value) return
  editingField.value = { id: '', bizCode: selectedBiz.value.bizCode, fieldName: '', columnType: 'TEXT', dataType: 'VARCHAR', isRequired: false, isUnique: false, isSystemField: false, permissionExpr: '', defaultValueExpr: '', validationExpr: '', sortNo: fieldList.value.length }
  showFieldDialog.value = true
}
function editField(row: FieldMeta) { editingField.value = { ...row }; showFieldDialog.value = true }
async function saveField() {
  if (!editingField.value) return
  const f = editingField.value
  if (!f.fieldName) { ElMessage.warning(t('请填写字段名')); return }
  try {
    await saveFieldMeta(f)
    ElMessage.success(t('保存成功'))
    showFieldDialog.value = false
    if (selectedBiz.value) fieldList.value = await listFieldMeta(selectedBiz.value.bizCode)
  } catch (e) { ElMessage.error((e as Error).message) }
}
async function removeField(row: FieldMeta) {
  try { await ElMessageBox.confirm(t('确认删除该字段？'), t('提示'), { type: 'warning' }) }
  catch { return }
  try { await deleteFieldMeta(row.id); ElMessage.success(t('删除成功')); if (selectedBiz.value) fieldList.value = await listFieldMeta(selectedBiz.value.bizCode) }
  catch (e) { ElMessage.error((e as Error).message) }
}

function addAction() {
  if (!selectedBiz.value) return
  editingAction.value = { id: '', bizCode: selectedBiz.value.bizCode, action: '', strategyType: 'SINGLE', preHookPlugins: '', postHookPlugins: '', statusTransition: '', dataScopeExpr: '', enabled: true }
  showActionDialog.value = true
}
function editAction(row: ActionMeta) { editingAction.value = { ...row }; showActionDialog.value = true }
async function saveAction() {
  if (!editingAction.value) return
  const a = editingAction.value
  if (!a.action) { ElMessage.warning(t('请填写动作名称')); return }
  try {
    await saveActionMeta(a)
    ElMessage.success(t('保存成功'))
    showActionDialog.value = false
    if (selectedBiz.value) actionList.value = await listActionMeta(selectedBiz.value.bizCode)
  } catch (e) { ElMessage.error((e as Error).message) }
}
async function removeAction(row: ActionMeta) {
  try { await ElMessageBox.confirm(t('确认删除该动作？'), t('提示'), { type: 'warning' }) }
  catch { return }
  try { await deleteActionMeta(row.id); ElMessage.success(t('删除成功')); if (selectedBiz.value) actionList.value = await listActionMeta(selectedBiz.value.bizCode) }
  catch (e) { ElMessage.error((e as Error).message) }
}

function addState() {
  if (!selectedBiz.value) return
  editingState.value = { id: '', bizCode: selectedBiz.value.bizCode, currentState: '', nextState: '', event: '', guardExpr: '', action: '', enabled: true }
  showStateDialog.value = true
}
function editState(row: StateMachineMeta) { editingState.value = { ...row }; showStateDialog.value = true }
async function saveState() {
  if (!editingState.value) return
  const s = editingState.value
  if (!s.currentState || !s.nextState || !s.event || !s.action) { ElMessage.warning(t('请填写完整状态迁移信息')); return }
  try {
    await saveStateMachineMeta(s)
    ElMessage.success(t('保存成功'))
    showStateDialog.value = false
    if (selectedBiz.value) stateList.value = await listStateMachineMeta(selectedBiz.value.bizCode)
  } catch (e) { ElMessage.error((e as Error).message) }
}
async function removeState(row: StateMachineMeta) {
  try { await ElMessageBox.confirm(t('确认删除该状态迁移？'), t('提示'), { type: 'warning' }) }
  catch { return }
  try { await deleteStateMachineMeta(row.id); ElMessage.success(t('删除成功')); if (selectedBiz.value) stateList.value = await listStateMachineMeta(selectedBiz.value.bizCode) }
  catch (e) { ElMessage.error((e as Error).message) }
}
</script>

<template>
  <div class="biz-meta-page">
    <div class="biz-meta-layout">
      <aside class="biz-aside">
        <div class="aside-header">
          <span class="aside-title">{{ t('业务对象') }}</span>
          <div class="aside-actions">
            <ElButton type="primary" size="small" @click="addBiz">+ {{ t('新增') }}</ElButton>
            <ElButton size="small" @click="refreshBiz">{{ t('刷新') }}</ElButton>
          </div>
        </div>
        <DxDataGrid
          :data-source="bizList"
          :show-borders="true"
          :column-auto-width="true"
          :selected-row-keys="selectedBiz ? [selectedBiz] : []"
          height="calc(100vh - 100px)"
          @selection-changed="onBizSelect"
        >
          <DxColumn data-field="bizCode" :caption="t('业务编码')" width="140" />
          <DxColumn data-field="bizName" :caption="t('业务名称')" width="120" />
          <DxColumn data-field="mainTable" :caption="t('主表')" width="120" />
          <DxColumn :caption="t('操作')" width="120" cell-template="bizActionTpl" />
          <template #bizActionTpl="{ data }">
            <ElButton type="primary" link @click="editBiz(data)">{{ t('编辑') }}</ElButton>
            <ElButton type="danger" link @click="removeBiz(data)">{{ t('删除') }}</ElButton>
          </template>
        </DxDataGrid>
      </aside>

      <main class="biz-main">
        <template v-if="selectedBiz">
          <div class="main-header">
            <span class="main-title">{{ selectedBiz.bizName }}（{{ selectedBiz.bizCode }}）</span>
          </div>
          <ElTabs v-model="activeTab" class="main-tabs">
            <ElTabPane :label="t('字段配置')" name="field">
              <div class="tab-toolbar">
                <ElButton type="primary" size="small" @click="addField">+ {{ t('新增字段') }}</ElButton>
              </div>
              <DxDataGrid :data-source="fieldList" :show-borders="true" :column-auto-width="true" height="calc(100vh - 220px)">
                <DxColumn data-field="fieldName" :caption="t('字段名')" width="140" />
                <DxColumn data-field="columnType" :caption="t('列类型')" width="120" />
                <DxColumn data-field="dataType" :caption="t('数据类型')" width="100" />
                <DxColumn data-field="isRequired" :caption="t('必填')" width="60" />
                <DxColumn data-field="isUnique" :caption="t('唯一')" width="60" />
                <DxColumn data-field="defaultValueExpr" :caption="t('默认值表达式')" width="160" />
                <DxColumn data-field="validationExpr" :caption="t('校验表达式')" width="160" />
                <DxColumn data-field="sortNo" :caption="t('排序')" width="60" />
                <DxColumn :caption="t('操作')" width="120" cell-template="fieldActionTpl" />
                <template #fieldActionTpl="{ data }">
                  <ElButton type="primary" link @click="editField(data)">{{ t('编辑') }}</ElButton>
                  <ElButton type="danger" link @click="removeField(data)">{{ t('删除') }}</ElButton>
                </template>
              </DxDataGrid>
            </ElTabPane>

            <ElTabPane :label="t('动作配置')" name="action">
              <div class="tab-toolbar">
                <ElButton type="primary" size="small" @click="addAction">+ {{ t('新增动作') }}</ElButton>
              </div>
              <DxDataGrid :data-source="actionList" :show-borders="true" :column-auto-width="true" height="calc(100vh - 220px)">
                <DxColumn data-field="action" :caption="t('动作')" width="100" />
                <DxColumn data-field="strategyType" :caption="t('策略类型')" width="100" />
                <DxColumn data-field="statusTransition" :caption="t('状态迁移')" width="160" />
                <DxColumn data-field="preHookPlugins" :caption="t('前置钩子')" width="160" />
                <DxColumn data-field="postHookPlugins" :caption="t('后置钩子')" width="160" />
                <DxColumn data-field="enabled" :caption="t('启用')" width="60" />
                <DxColumn :caption="t('操作')" width="120" cell-template="actionActionTpl" />
                <template #actionActionTpl="{ data }">
                  <ElButton type="primary" link @click="editAction(data)">{{ t('编辑') }}</ElButton>
                  <ElButton type="danger" link @click="removeAction(data)">{{ t('删除') }}</ElButton>
                </template>
              </DxDataGrid>
            </ElTabPane>

            <ElTabPane :label="t('状态机配置')" name="state">
              <div class="tab-toolbar">
                <ElButton type="primary" size="small" @click="addState">+ {{ t('新增状态迁移') }}</ElButton>
              </div>
              <DxDataGrid :data-source="stateList" :show-borders="true" :column-auto-width="true" height="calc(100vh - 220px)">
                <DxColumn data-field="currentState" :caption="t('当前状态')" width="120" />
                <DxColumn data-field="nextState" :caption="t('下一状态')" width="120" />
                <DxColumn data-field="event" :caption="t('事件')" width="100" />
                <DxColumn data-field="action" :caption="t('动作')" width="100" />
                <DxColumn data-field="guardExpr" :caption="t('守卫表达式')" width="180" />
                <DxColumn data-field="enabled" :caption="t('启用')" width="60" />
                <DxColumn :caption="t('操作')" width="120" cell-template="stateActionTpl" />
                <template #stateActionTpl="{ data }">
                  <ElButton type="primary" link @click="editState(data)">{{ t('编辑') }}</ElButton>
                  <ElButton type="danger" link @click="removeState(data)">{{ t('删除') }}</ElButton>
                </template>
              </DxDataGrid>
            </ElTabPane>
          </ElTabs>
        </template>
        <ElEmpty v-else :description="t('请从左侧选择业务对象')" class="empty-tip" />
      </main>
    </div>

    <ElDialog v-model="showBizDialog" :title="t('业务对象编辑')" width="600px" append-to-body>
      <ElForm v-if="editingBiz" label-width="120px">
        <ElFormItem :label="t('业务编码')"><ElInput v-model="editingBiz.bizCode" :placeholder="t('如 purchaseOrder')" /></ElFormItem>
        <ElFormItem :label="t('业务名称')"><ElInput v-model="editingBiz.bizName" /></ElFormItem>
        <ElFormItem :label="t('主表')"><ElInput v-model="editingBiz.mainTable" :placeholder="t('如 po_main')" /></ElFormItem>
        <ElFormItem :label="t('主表别名')"><ElInput v-model="editingBiz.mainTableAlias" /></ElFormItem>
        <ElFormItem :label="t('状态字段')"><ElInput v-model="editingBiz.statusField" :placeholder="t('如 status')" /></ElFormItem>
        <ElFormItem :label="t('逻辑删除字段')"><ElInput v-model="editingBiz.logicDeleteField" /></ElFormItem>
        <ElFormItem :label="t('备注')"><ElInput v-model="editingBiz.remark" type="textarea" :rows="2" /></ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showBizDialog = false">{{ t('取消') }}</ElButton>
        <ElButton type="primary" @click="saveBiz">{{ t('保存') }}</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="showFieldDialog" :title="t('字段编辑')" width="600px" append-to-body>
      <ElForm v-if="editingField" label-width="120px">
        <ElFormItem :label="t('字段名')"><ElInput v-model="editingField.fieldName" /></ElFormItem>
        <ElFormItem :label="t('列类型')">
          <ElSelect v-model="editingField.columnType" style="width: 100%">
            <ElOption v-for="c in COLUMN_TYPES" :key="c.value" :label="t(c.label)" :value="c.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('数据类型')">
          <ElSelect v-model="editingField.dataType" style="width: 100%">
            <ElOption v-for="d in DATA_TYPES" :key="d.value" :label="d.label" :value="d.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('必填')"><ElSwitch v-model="editingField.isRequired" /></ElFormItem>
        <ElFormItem :label="t('唯一')"><ElSwitch v-model="editingField.isUnique" /></ElFormItem>
        <ElFormItem :label="t('系统字段')"><ElSwitch v-model="editingField.isSystemField" /></ElFormItem>
        <ElFormItem :label="t('默认值表达式')"><ElInput v-model="editingField.defaultValueExpr" :placeholder="t('如 now() 或 false 或 DRAFT')" /></ElFormItem>
        <ElFormItem :label="t('校验表达式')"><ElInput v-model="editingField.validationExpr" :placeholder="t('如 value > 0')" /></ElFormItem>
        <ElFormItem :label="t('权限表达式')"><ElInput v-model="editingField.permissionExpr" /></ElFormItem>
        <ElFormItem :label="t('排序')"><ElInput v-model.number="editingField.sortNo" type="number" /></ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showFieldDialog = false">{{ t('取消') }}</ElButton>
        <ElButton type="primary" @click="saveField">{{ t('保存') }}</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="showActionDialog" :title="t('动作编辑')" width="600px" append-to-body>
      <ElForm v-if="editingAction" label-width="120px">
        <ElFormItem :label="t('动作名称')"><ElInput v-model="editingAction.action" :placeholder="t('如 create/update/submit')" /></ElFormItem>
        <ElFormItem :label="t('策略类型')">
          <ElSelect v-model="editingAction.strategyType" style="width: 100%">
            <ElOption v-for="s in STRATEGY_TYPES" :key="s.value" :label="t(s.label)" :value="s.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('状态迁移')"><ElInput v-model="editingAction.statusTransition" :placeholder="t('如 DRAFT->SUBMITTED')" /></ElFormItem>
        <ElFormItem :label="t('前置钩子插件')"><ElInput v-model="editingAction.preHookPlugins" /></ElFormItem>
        <ElFormItem :label="t('后置钩子插件')"><ElInput v-model="editingAction.postHookPlugins" /></ElFormItem>
        <ElFormItem :label="t('数据范围表达式')"><ElInput v-model="editingAction.dataScopeExpr" /></ElFormItem>
        <ElFormItem :label="t('启用')"><ElSwitch v-model="editingAction.enabled" /></ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showActionDialog = false">{{ t('取消') }}</ElButton>
        <ElButton type="primary" @click="saveAction">{{ t('保存') }}</ElButton>
      </template>
    </ElDialog>

    <ElDialog v-model="showStateDialog" :title="t('状态迁移编辑')" width="600px" append-to-body>
      <ElForm v-if="editingState" label-width="120px">
        <ElFormItem :label="t('当前状态')"><ElInput v-model="editingState.currentState" :placeholder="t('如 DRAFT')" /></ElFormItem>
        <ElFormItem :label="t('下一状态')"><ElInput v-model="editingState.nextState" :placeholder="t('如 SUBMITTED')" /></ElFormItem>
        <ElFormItem :label="t('事件')"><ElInput v-model="editingState.event" :placeholder="t('如 SUBMIT')" /></ElFormItem>
        <ElFormItem :label="t('动作')"><ElInput v-model="editingState.action" :placeholder="t('如 submit')" /></ElFormItem>
        <ElFormItem :label="t('守卫表达式')"><ElInput v-model="editingState.guardExpr" /></ElFormItem>
        <ElFormItem :label="t('启用')"><ElSwitch v-model="editingState.enabled" /></ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showStateDialog = false">{{ t('取消') }}</ElButton>
        <ElButton type="primary" @click="saveState">{{ t('保存') }}</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.biz-meta-page {
  width: 100%;
  height: 100vh;
  box-sizing: border-box;
  background: #f5f7fa;
}
.biz-meta-layout {
  display: flex;
  height: 100%;
  gap: 12px;
  padding: 12px;
  box-sizing: border-box;
}
.biz-aside {
  flex: 0 0 420px;
  background: #fff;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.aside-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
}
.aside-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.aside-actions {
  display: flex;
  gap: 8px;
}
.biz-aside :deep(.dx-datagrid) {
  flex: 1;
  overflow: auto;
}
.biz-main {
  flex: 1;
  background: #fff;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 12px;
}
.main-header {
  margin-bottom: 8px;
}
.main-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.main-tabs {
  flex: 1;
  overflow: hidden;
}
.tab-toolbar {
  margin-bottom: 8px;
}
.empty-tip {
  margin: auto;
}
</style>