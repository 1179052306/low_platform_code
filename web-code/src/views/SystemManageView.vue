<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'

import DxTextBox from 'devextreme-vue/text-box'
import DxButton from 'devextreme-vue/button'
import AdvancedSearchPanel from '@/components/renderer/AdvancedSearchPanel.vue'
import type {
  GridColumn,
  SearchCondition,
} from '@/components/renderer/AdvancedSearchPanel.vue'
import {
  listSystems,
  saveSystem,
  deleteSystem,
  type SystemInfo,
} from '@/store/persistence'
import { ElMessage } from 'element-plus'
import { t, loadModule } from '@/store/lang'

const systemList = ref<SystemInfo[]>([])

// ================ 高级查询 ================
const advSearchConditions = ref<SearchCondition[]>([])

const advColumns: GridColumn[] = [
  { dataField: 'id', caption: t('系统ID'), dataType: 'string' },
  { dataField: 'name', caption: t('系统名称'), dataType: 'string' },
  { dataField: 'sort', caption: t('排序'), dataType: 'number' },
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
  if (conds.length === 0) return systemList.value
  return systemList.value.filter((s) =>
    conds.every((c) =>
      matchCondition((s as unknown as Record<string, unknown>)[c.field], c)
    )
  )
})

async function refreshList() {
  try {
    systemList.value = await listSystems()
  } catch (e) {
    ElMessage.error(
      t('加载系统列表失败：') + (e instanceof Error ? e.message : String(e))
    )
  }
}
onMounted(() => {
  loadModule('system')
  refreshList()
})

// ================ 编辑表单（弹窗） ================
const editing = ref(false)
const isAdding = ref(false)
const form = ref<SystemInfo>({
  id: '',
  name: '',
  sort: 0,
})
const idError = ref('')
const nameError = ref('')

function startAdd() {
  isAdding.value = true
  editing.value = true
  form.value = {
    id: '',
    name: '',
    sort: systemList.value.length,
  }
  idError.value = ''
  nameError.value = ''
}

function startEdit(system: SystemInfo) {
  isAdding.value = false
  editing.value = true
  form.value = { ...system }
  idError.value = ''
  nameError.value = ''
}

function cancelEdit() {
  editing.value = false
  idError.value = ''
  nameError.value = ''
}

async function saveForm() {
  idError.value = ''
  nameError.value = ''
  const s = form.value
  if (!s.id.trim()) {
    idError.value = t('系统 ID 不能为空')
    return
  }
  if (!s.name.trim()) {
    nameError.value = t('系统名称不能为空')
    return
  }
  try {
    await saveSystem({
      id: s.id.trim(),
      name: s.name.trim(),
      sort: s.sort,
    })
    await refreshList()
    editing.value = false
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : String(err))
  }
}

async function deleteSystemHandler(s: SystemInfo) {
  if (s.id === 'sys_default') {
    ElMessage.warning(t('默认系统不能删除'))
    return
  }
  if (confirm(t('确认删除系统「') + s.name + t('」？'))) {
    try {
      await deleteSystem(s.id)
      await refreshList()
      if (editing.value && form.value.id === s.id) {
        editing.value = false
      }
    } catch (err) {
      ElMessage.error(
        '删除失败：' + (err instanceof Error ? err.message : String(err))
      )
    }
  }
}
</script>

<template>
  <div class="system-manage">
    <!-- 顶部工具栏 -->
    <div class="sm-header">
      <div class="sm-page-title">
        <i class="dx-icon-folder sm-title-icon"></i>
        <span>{{ t('系统基础资料') }}</span>
      </div>
      <DxButton
        :text="t('新增系统')"
        icon="add"
        type="default"
        styling-mode="contained"
        @click="startAdd"
      />
    </div>

    <!-- 查询面板 -->
    <div class="sm-search">
      <AdvancedSearchPanel
        :columns="advColumns"
        grid-id="system_manage_view"
        @search="onAdvSearch"
        @reset="onAdvSearchReset"
      />
    </div>

    <!-- 卡片列表 -->
    <div class="sm-cards-scroll">
      <div v-if="filteredList.length === 0" class="sm-empty">
        <i class="dx-icon-box sm-empty-icon"></i>
        <span class="sm-empty-text">{{ t('暂无系统数据') }}</span>
        <span class="sm-empty-hint">{{ t('点击右上角「新增系统」添加') }}</span>
      </div>
      <div class="sm-cards" v-else>
        <div v-for="s in filteredList" :key="s.id" class="sm-card">
          <!-- 左侧图标区 -->
          <div class="sm-card-icon">
            <i class="dx-icon-folder"></i>
          </div>
          <!-- 右侧内容区 -->
          <div class="sm-card-content">
            <div class="sm-card-head">
              <div class="sm-card-id-wrap">
                <span class="sm-card-id">{{ s.id }}</span>
                <span class="sm-badge-default" v-if="s.id === 'sys_default'">
                  {{ t('默认') }}
                </span>
              </div>
              <div class="sm-card-btns">
                <DxButton
                  icon="edit"
                  styling-mode="text"
                  :hint="t('修改')"
                  @click="startEdit(s)"
                />
                <DxButton
                  v-if="s.id !== 'sys_default'"
                  icon="trash"
                  styling-mode="text"
                  :hint="t('删除')"
                  @click="deleteSystemHandler(s)"
                />
              </div>
            </div>
            <div class="sm-card-name">{{ s.name }}</div>
            <div class="sm-card-foot">
              <span class="sm-sort-badge">
                <i class="dx-icon-sort sm-sort-icon"></i>
                {{ t('排序') }}
                <span class="sm-sort-num">{{ s.sort }}</span>
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <div class="sm-overlay" v-if="editing" @click.self="cancelEdit">
      <div class="sm-modal">
        <div class="sm-modal-header">
          <div class="sm-modal-title-wrap">
            <i
              class="sm-modal-title-icon"
              :class="isAdding ? 'dx-icon-add' : 'dx-icon-edit'"
            ></i>
            <span class="sm-modal-title">
              {{ isAdding ? t('新增系统') : t('修改系统') }}
            </span>
          </div>
          <DxButton icon="close" styling-mode="text" @click="cancelEdit" />
        </div>
        <div class="sm-modal-body">
          <!-- 系统 ID -->
          <div class="sm-field">
            <label class="sm-label sm-required">{{ t('系统 ID') }}</label>
            <DxTextBox
              :value="form.id"
              :read-only="!isAdding"
              :placeholder="t('如 sys_wms')"
              @value-changed="(e: any) => (form.id = e.value)"
            />
            <div class="sm-field-error" v-if="idError">{{ idError }}</div>
          </div>
          <!-- 系统名称 -->
          <div class="sm-field">
            <label class="sm-label sm-required">{{ t('系统名称') }}</label>
            <DxTextBox
              :value="form.name"
              :placeholder="t('如 仓储管理系统')"
              @value-changed="(e: any) => (form.name = e.value)"
            />
            <div class="sm-field-error" v-if="nameError">{{ nameError }}</div>
          </div>
          <!-- 排序 -->
          <div class="sm-field">
            <label class="sm-label">{{ t('排序') }}</label>
            <DxTextBox
              :value="String(form.sort)"
              @value-changed="(e: any) => (form.sort = Number(e.value) || 0)"
            />
          </div>
        </div>
        <div class="sm-modal-footer">
          <DxButton
            :text="t('取消')"
            styling-mode="outlined"
            @click="cancelEdit"
          />
          <DxButton
            v-if="!isAdding && form.id !== 'sys_default'"
            :text="t('删除')"
            icon="trash"
            type="danger"
            styling-mode="outlined"
            @click="deleteSystemHandler(form)"
          />
          <DxButton
            :text="t('保存')"
            icon="save"
            type="default"
            styling-mode="contained"
            @click="saveForm"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.system-manage {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f7fa;
  overflow: hidden;
}

/* ================ 顶部工具栏 ================ */
.sm-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}
.sm-page-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}
.sm-title-icon {
  color: #2563eb;
  font-size: 22px;
}

/* ================ 查询面板 ================ */
.sm-search {
  padding: 12px 24px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
}

/* ================ 卡片列表 ================ */
.sm-cards-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}
.sm-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 10px;
}
.sm-empty-icon {
  font-size: 72px;
  color: #d1d5db;
}
.sm-empty-text {
  font-size: 16px;
  color: #9ca3af;
  font-weight: 500;
}
.sm-empty-hint {
  font-size: 13px;
  color: #c0c6cf;
}
.sm-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
  align-items: start;
}
.sm-card {
  display: flex;
  min-height: 160px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 2px;
  transition: box-shadow 0.18s, border-color 0.18s;
}
.sm-card:hover {
  border-color: #93c5fd;
  box-shadow: 0 4px 14px rgba(37, 99, 235, 0.1);
}
.sm-card-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  flex-shrink: 0;
  background: #eff6ff;
  color: #2563eb;
  border-right: 1px solid #e5e7eb;
  font-size: 30px;
  transition: background 0.18s, color 0.18s;
}
.sm-card:hover .sm-card-icon {
  background: #dbeafe;
  color: #1d4ed8;
}
.sm-card-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 18px 14px 16px 18px;
  min-width: 0;
}
.sm-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.sm-card-id-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.sm-card-id {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sm-badge-default {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  color: #16a34a;
  background: #dcfce7;
  padding: 2px 8px;
  border-radius: 2px;
  line-height: 1.4;
}
.sm-card-btns {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}
.sm-card-btns :deep(.dx-button) {
  min-width: 34px;
}
.sm-card-name {
  font-size: 14px;
  color: #4b5563;
  line-height: 1.5;
  margin-bottom: 14px;
  word-break: break-all;
}
.sm-card-foot {
  margin-top: auto;
}
.sm-sort-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  color: #6b7280;
  background: #f3f4f6;
  padding: 4px 10px;
  border-radius: 2px;
}
.sm-sort-icon {
  font-size: 14px;
  color: #9ca3af;
}
.sm-sort-num {
  font-weight: 700;
  color: #2563eb;
  margin-left: 2px;
}

/* ================ 编辑弹窗 ================ */
.sm-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.sm-modal {
  width: 520px;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 2px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.16);
}
.sm-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px 16px 24px;
  border-bottom: 1px solid #e5e7eb;
}
.sm-modal-title-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}
.sm-modal-title-icon {
  color: #2563eb;
  font-size: 20px;
}
.sm-modal-title {
  font-size: 17px;
  font-weight: 600;
  color: #1f2937;
}
.sm-modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}
.sm-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 20px;
}
.sm-label {
  font-size: 13px;
  color: #4b5563;
  font-weight: 500;
}
.sm-required::before {
  content: '*';
  color: #ef4444;
  margin-right: 4px;
  font-weight: 700;
}
.sm-field-error {
  color: #ef4444;
  font-size: 12px;
  margin-top: 2px;
}
.sm-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 24px;
  border-top: 1px solid #e5e7eb;
}
</style>
