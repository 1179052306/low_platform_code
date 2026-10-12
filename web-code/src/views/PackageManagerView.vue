<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import DxDataGrid, { DxColumn, DxToolbar, DxItem } from 'devextreme-vue/data-grid'
import DxButton from 'devextreme-vue/button'
import { t } from '@/store/lang'
import type { AppPackage } from '@/types/schema'
import { listPackages, savePackage, deletePackage } from '@/store/package'
import PackageDialog from '@/components/designer/PackageDialog.vue'

const pkgList = ref<AppPackage[]>([])
const showPkgDialog = ref(false)
const editingPkg = ref<AppPackage | undefined>(undefined)
const selectedPkg = ref<AppPackage | undefined>(undefined)

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
    <DxDataGrid
      :data-source="pkgList"
      :selection="{ mode: 'single', showCheckBoxesMode: 'none' }"
      :hover-state-enabled="true"
      :show-borders="true"
      :show-column-lines="false"
      :show-row-lines="true"
      :paging="{ enabled: false }"
      height="100%"
      key-expr="id"
      @selection-changed="onSelectionChanged"
    >
      <DxColumn data-field="name" :caption="t('包名称')" />
      <DxColumn data-field="version" :caption="t('版本号')" :width="100" />
      <DxColumn data-field="description" :caption="t('描述')" />
      <DxColumn data-field="updatedAt" :caption="t('更新时间')" :width="160" />
      <DxToolbar>
        <DxItem location="after">
          <template #default>
            <DxButton :text="t('新建应用包')" type="default" @click="addPkg" />
          </template>
        </DxItem>
        <DxItem location="after">
          <template #default>
            <DxButton :text="t('编辑')" :disabled="!selectedPkg" @click="editPkg" />
          </template>
        </DxItem>
        <DxItem location="after">
          <template #default>
            <DxButton :text="t('删除')" :disabled="!selectedPkg" @click="onDeletePkg" />
          </template>
        </DxItem>
      </DxToolbar>
    </DxDataGrid>
    <PackageDialog v-model:visible="showPkgDialog" :pkg="editingPkg" @save="onSavePkg" />
  </div>
</template>

<style scoped>
.pkg-manage {
  height: 100%;
  padding: 12px;
  background: #f5f5f5;
}
</style>