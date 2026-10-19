<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import LcTable from '@/materials/components/LcTable.vue'

import { t } from '@/store/lang'
import type { AppPackage } from '@/types/schema'
import { listPackages, savePackage, deletePackage } from '@/store/package'
import PackageDialog from '@/components/designer/PackageDialog.vue'

const pkgList = ref<AppPackage[]>([])
const showPkgDialog = ref(false)
const editingPkg = ref<AppPackage | undefined>(undefined)
const selectedPkg = ref<AppPackage | undefined>(undefined)

const columns = computed(() => [
  { dataField: 'name', caption: t('包名称') },
  { dataField: 'version', caption: t('版本号'), width: 100 },
  { dataField: 'description', caption: t('描述') },
  { dataField: 'updatedAt', caption: t('更新时间'), width: 160 },
])

async function refresh() {
  pkgList.value = await listPackages()
}

onMounted(() => {
  refresh()
})

function onSelectionChanged(e: { selectedRowsData: AppPackage[] }) {
  selectedPkg.value = e.selectedRowsData.length > 0 ? e.selectedRowsData[0] : undefined
}

function addPkg() {
  editingPkg.value = undefined
  showPkgDialog.value = true
}

function editPkg() {
  if (selectedPkg.value) {
    editingPkg.value = { ...selectedPkg.value }
    showPkgDialog.value = true
  }
}

async function onSavePkg(pkg: AppPackage) {
  try {
    await savePackage(pkg)
    await refresh()
    ElMessage.success(t('保存成功'))
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : String(err))
  }
}

async function onDeletePkg() {
  if (!selectedPkg.value) return
  try {
    await ElMessageBox.confirm(t('确认删除此应用包？'), t('提示'), { type: 'warning' })
    await deletePackage(selectedPkg.value.id)
    await refresh()
    ElMessage.success(t('删除成功'))
  } catch {
    /* 取消 */
  }
}
</script>

<template>
  <div class="pkg-manage">
    <div class="pkg-toolbar">
      <ElButton type="default" @click="addPkg">{{ t('新建应用包') }}</ElButton>
      <ElButton :disabled="!selectedPkg" @click="editPkg">{{ t('编辑') }}</ElButton>
      <ElButton :disabled="!selectedPkg" @click="onDeletePkg">{{ t('删除') }}</ElButton>
    </div>
    <LcTable
      class="pkg-grid"
      :data-source="pkgList"
      :columns="columns"
      :selection="{ mode: 'single', showCheckBoxesMode: 'none' }"
      :show-borders="true"
      :show-column-lines="false"
      :show-row-lines="true"
      :paging="{ enabled: false }"
      height="100%"
      key-expr="id"
      @selection-changed="onSelectionChanged"
    />
    <PackageDialog v-model:visible="showPkgDialog" :pkg="editingPkg" @save="onSavePkg" />
  </div>
</template>

<style scoped>
.pkg-manage {
  height: 100%;
  padding: 12px;
  background: #f5f5f5;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.pkg-toolbar {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.pkg-grid {
  flex: 1;
  overflow: hidden;
  min-height: 0;
}
</style>