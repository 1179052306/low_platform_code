<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElButton,
  ElSwitch,
  ElInputNumber,
  ElMessage,
  ElMessageBox,
} from 'element-plus'
import LcTable from '@/materials/components/LcTable.vue'
import type { LcColumn } from '@/materials/components/LcTable.vue'

import { t } from '@/store/lang'
import type { DataDict, DataDictItem } from '@/types/dict'
import {
  listDataDicts,
  saveDataDict,
  deleteDataDict,
  listDictItems,
  saveDictItem,
  deleteDictItem,
} from '@/store/api-store'

const dictList = ref<DataDict[]>([])
const selectedDict = ref<DataDict | undefined>(undefined)
const dictItems = ref<DataDictItem[]>([])

const showDictDialog = ref(false)
const editingDict = ref<DataDict | null>(null)
const showItemDialog = ref(false)
const editingItem = ref<DataDictItem | null>(null)

const dictColumns = computed<LcColumn[]>(() => [
  { dataField: 'code', caption: t('编码'), width: 140 },
  { dataField: 'name', caption: t('名称') },
  { dataField: 'enabled', caption: t('启用'), width: 60, dataType: 'boolean' },
])

const dictItemColumns = computed<LcColumn[]>(() => [
  { dataField: 'value', caption: t('值'), width: 120 },
  { dataField: 'label', caption: t('标签') },
  { dataField: 'sortNo', caption: t('排序'), width: 70 },
  { dataField: 'enabled', caption: t('启用'), width: 60, dataType: 'boolean' },
])

async function refreshDicts() {
  try {
    dictList.value = await listDataDicts()
  } catch {
    dictList.value = []
  }
}

async function refreshItems(dictCode: string) {
  try {
    dictItems.value = await listDictItems(dictCode)
  } catch {
    dictItems.value = []
  }
}

onMounted(async () => {
  await refreshDicts()
})

function onDictSelectionChanged(e: { selectedRowsData: DataDict[] }) {
  if (e.selectedRowsData.length > 0) {
    selectedDict.value = e.selectedRowsData[0]
    refreshItems(selectedDict.value.code)
  } else {
    selectedDict.value = undefined
    dictItems.value = []
  }
}

function addDict() {
  editingDict.value = {
    code: '',
    name: '',
    description: '',
    enabled: true,

  }
  showDictDialog.value = true
}

function editDict(dict: DataDict) {
  editingDict.value = { ...dict }
  showDictDialog.value = true
}

async function onSaveDict() {
  if (!editingDict.value) return
  if (!editingDict.value.code.trim() || !editingDict.value.name.trim()) {
    ElMessage.warning(t('编码和名称不能为空'))
    return
  }
  try {
    await saveDataDict(editingDict.value)
    await refreshDicts()
    showDictDialog.value = false
    ElMessage.success(t('保存成功'))
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : String(err))
  }
}

async function onDeleteDict(dict: DataDict) {
  try {
    await ElMessageBox.confirm(t('确认删除此字典及其所有字典项？'), t('提示'), {
      type: 'warning',
    })
    await deleteDataDict(dict.code)
    await refreshDicts()
    selectedDict.value = undefined
    dictItems.value = []
    ElMessage.success(t('删除成功'))
  } catch {
    /* 取消 */
  }
}

function addItem() {
  if (!selectedDict.value) return
  editingItem.value = {
    id: `di_${Date.now().toString(36)}`,
    dictCode: selectedDict.value.code,
    value: '',
    label: '',
    enabled: true,
    sortNo: dictItems.value.length + 1,
  }
  showItemDialog.value = true
}

function editItem(item: DataDictItem) {
  editingItem.value = { ...item }
  showItemDialog.value = true
}

async function onSaveItem() {
  if (!editingItem.value) return
  if (!editingItem.value.value.trim() || !editingItem.value.label.trim()) {
    ElMessage.warning(t('值和标签不能为空'))
    return
  }
  try {
    await saveDictItem(editingItem.value)
    await refreshItems(editingItem.value.dictCode)
    showItemDialog.value = false
    ElMessage.success(t('保存成功'))
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : String(err))
  }
}

async function onDeleteItem(item: DataDictItem) {
  try {
    await ElMessageBox.confirm(t('确认删除此字典项？'), t('提示'), {
      type: 'warning',
    })
    await deleteDictItem(item.id)
    if (selectedDict.value) await refreshItems(selectedDict.value.code)
    ElMessage.success(t('删除成功'))
  } catch {
    /* 取消 */
  }
}
</script>

<template>
  <div class="dict-manage">
    <div class="dict-body">
      <!-- 左侧：字典列表 -->
      <div class="dict-list-panel">
        <div class="dict-panel-title">{{ t('数据字典') }}</div>
        <div class="dict-toolbar">
          <ElButton type="default" @click="addDict">{{ t('新增字典') }}</ElButton>
          <ElButton
            :disabled="!selectedDict"
            @click="() => { if (selectedDict) editDict(selectedDict) }"
          >{{ t('编辑') }}</ElButton>
          <ElButton
            :disabled="!selectedDict"
            @click="() => { if (selectedDict) onDeleteDict(selectedDict) }"
          >{{ t('删除') }}</ElButton>
        </div>
        <LcTable
          class="dict-grid"
          :data-source="dictList"
          :columns="dictColumns"
          :selection="{ mode: 'single', showCheckBoxesMode: 'none' }"
          :show-borders="true"
          :show-column-lines="false"
          :show-row-lines="true"
          :paging="{ enabled: false }"
          height="100%"
          key-expr="code"
          @selection-changed="onDictSelectionChanged"
        />
      </div>
      <!-- 右侧：字典项列表 -->
      <div class="dict-items-panel">
        <div class="dict-panel-title">
          {{ t('字典项') }}
          <span v-if="selectedDict" class="dict-current">— {{ selectedDict.name }}</span>
        </div>
        <div class="dict-toolbar">
          <ElButton type="default" @click="addItem">{{ t('新增项') }}</ElButton>
          <ElButton
            @click="() => { if (dictItems[0]) editItem(dictItems[0]) }"
          >{{ t('编辑') }}</ElButton>
          <ElButton
            @click="() => { if (dictItems[0]) onDeleteItem(dictItems[0]) }"
          >{{ t('删除') }}</ElButton>
        </div>
        <LcTable
          v-if="selectedDict"
          class="dict-grid"
          :data-source="dictItems"
          :columns="dictItemColumns"
          :show-borders="true"
          :show-column-lines="false"
          :show-row-lines="true"
          :paging="{ enabled: false }"
          height="100%"
          key-expr="id"
        />
        <div v-else class="dict-items-empty">
          {{ t('请选择左侧字典') }}
        </div>
      </div>
    </div>

    <!-- 字典编辑弹窗 -->
    <ElDialog
      v-model="showDictDialog"
      :title="editingDict?.code ? t('编辑字典') : t('新增字典')"
      width="440px"
      append-to-body
    >
      <ElForm v-if="editingDict" label-width="80px">
        <ElFormItem :label="t('编码')">
          <ElInput v-model="editingDict.code" :placeholder="t('如 order_status')" />
        </ElFormItem>
        <ElFormItem :label="t('名称')">
          <ElInput v-model="editingDict.name" :placeholder="t('如 订单状态')" />
        </ElFormItem>
        <ElFormItem :label="t('描述')">
          <ElInput v-model="editingDict.description" type="textarea" :rows="2" />
        </ElFormItem>

        <ElFormItem :label="t('启用')">
          <ElSwitch v-model="editingDict.enabled" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showDictDialog = false">{{ t('取消') }}</ElButton>
        <ElButton type="primary" @click="onSaveDict">{{ t('保存') }}</ElButton>
      </template>
    </ElDialog>

    <!-- 字典项编辑弹窗 -->
    <ElDialog
      v-model="showItemDialog"
      :title="editingItem?.value ? t('编辑字典项') : t('新增字典项')"
      width="440px"
      append-to-body
    >
      <ElForm v-if="editingItem" label-width="80px">
        <ElFormItem :label="t('值')">
          <ElInput v-model="editingItem.value" :placeholder="t('存储值')" />
        </ElFormItem>
        <ElFormItem :label="t('标签')">
          <ElInput v-model="editingItem.label" :placeholder="t('显示文本')" />
        </ElFormItem>
        <ElFormItem :label="t('排序')">
          <ElInputNumber v-model="editingItem.sortNo" :min="1" />
        </ElFormItem>
        <ElFormItem :label="t('启用')">
          <ElSwitch v-model="editingItem.enabled" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showItemDialog = false">{{ t('取消') }}</ElButton>
        <ElButton type="primary" @click="onSaveItem">{{ t('保存') }}</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.dict-manage {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f5f5;
}
.dict-body {
  flex: 1;
  display: flex;
  overflow: hidden;
  padding: 12px;
  gap: 12px;
}
.dict-list-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 6px;
  overflow: hidden;
}
.dict-items-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 6px;
  overflow: hidden;
}
.dict-panel-title {
  padding: 10px 14px;
  font-size: 14px;
  font-weight: 600;
  border-bottom: 1px solid #f0f0f0;
}
.dict-toolbar {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f0f0;
}
.dict-current {
  color: #409eff;
  font-weight: 400;
}
.dict-grid {
  flex: 1;
  overflow: hidden;
}
.dict-items-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
  font-size: 13px;
}
</style>