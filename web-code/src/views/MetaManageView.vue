<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElDialog, ElForm, ElFormItem, ElInput, ElSelect, ElOption, ElSwitch, ElButton, ElMessage, ElMessageBox } from 'element-plus'
import LcTable from '@/materials/components/LcTable.vue'
import { t } from '@/store/lang'
import { loadMetaList, saveMetaItem, deleteMetaItem } from '@/store/meta'
import { META_DOMAINS, type MetaItem, type MetaDomain } from '@/types/meta'

const list = ref<MetaItem[]>([])
const selectedDomain = ref<MetaDomain | ''>('')
const selectedMetaType = ref('')

const showEditDialog = ref(false)
const editingItem = ref<MetaItem | null>(null)
const contentText = ref('')

const filteredList = computed(() =>
  list.value.filter((m) => {
    if (selectedDomain.value && m.domain !== selectedDomain.value) return false
    if (selectedMetaType.value && m.metaType !== selectedMetaType.value) return false
    return true
  }),
)

const metaColumns = computed(() => [
  { dataField: 'domain', caption: t('域'), width: 100 },
  { dataField: 'metaType', caption: t('类型'), width: 120 },
  { dataField: 'metaKey', caption: t('键'), width: 180 },
  { dataField: 'metaName', caption: t('名称'), width: 180 },
  { dataField: 'enabled', caption: t('启用'), width: 80 },
  { dataField: 'updatedAt', caption: t('更新时间'), width: 160 },
  { dataField: '_op', caption: t('操作'), width: 140, cellTemplate: 'actionTpl' },
])

async function refresh() {
  list.value = await loadMetaList()
}

onMounted(async () => {
  await refresh()
})

function selectDomain(domain: MetaDomain | '') {
  selectedDomain.value = domain
  selectedMetaType.value = ''
}

function addMeta() {
  editingItem.value = {
    id: `meta_${Date.now().toString(36)}`,
    domain: selectedDomain.value || 'structure',
    metaType: '',
    metaKey: '',
    metaName: '',
    content: {},
    enabled: true,
  }
  contentText.value = '{}'
  showEditDialog.value = true
}

function editMeta(item: MetaItem) {
  editingItem.value = JSON.parse(JSON.stringify(item))
  contentText.value = JSON.stringify(item.content ?? {}, null, 2)
  showEditDialog.value = true
}

async function saveEdit() {
  if (!editingItem.value) return
  if (!editingItem.value.metaKey || !editingItem.value.metaName) {
    ElMessage.warning(t('请填写元数据键和名称'))
    return
  }
  try {
    editingItem.value.content = JSON.parse(contentText.value || '{}')
  } catch {
    ElMessage.error(t('内容 JSON 格式错误'))
    return
  }
  try {
    await saveMetaItem(editingItem.value)
    ElMessage.success(t('保存成功'))
    showEditDialog.value = false
    await refresh()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function removeMeta(item: MetaItem) {
  try {
    await ElMessageBox.confirm(t('确认删除该元数据项？'), t('提示'), {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await deleteMetaItem(item.id)
    ElMessage.success(t('删除成功'))
    await refresh()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}
</script>

<template>
  <div class="meta-manage">
    <div class="meta-layout">
      <aside class="meta-aside">
        <div class="meta-aside-title">{{ t('元数据域') }}</div>
        <div
          class="domain-item"
          :class="{ active: selectedDomain === '' }"
          @click="selectDomain('')"
        >
          {{ t('全部') }}
        </div>
        <div
          v-for="d in META_DOMAINS"
          :key="d.value"
          class="domain-item"
          :class="{ active: selectedDomain === d.value }"
          @click="selectDomain(d.value)"
        >
          {{ t(d.label) }}
          <div v-if="selectedDomain === d.value" class="domain-types">
            <div
              v-for="tp in d.types"
              :key="tp.value"
              class="type-item"
              :class="{ active: selectedMetaType === tp.value }"
              @click.stop="selectedMetaType = tp.value"
            >
              {{ t(tp.label) }}
            </div>
          </div>
        </div>
      </aside>

      <main class="meta-main">
        <div class="meta-toolbar">
          <ElButton type="primary" @click="addMeta">+ {{ t('新增元数据') }}</ElButton>
          <ElButton @click="refresh">{{ t('刷新') }}</ElButton>
        </div>
        <LcTable
          :data-source="filteredList"
          :show-borders="true"
          :column-auto-width="true"
          height="calc(100vh - 120px)"
          :columns="metaColumns"
        >
          <template #actionTpl="{ data }">
            <ElButton type="primary" link @click="editMeta(data as any)">{{ t('编辑') }}</ElButton>
            <ElButton type="danger" link @click="removeMeta(data as any)">{{ t('删除') }}</ElButton>
          </template>
        </LcTable>
      </main>
    </div>

    <ElDialog
      v-model="showEditDialog"
      :title="t('元数据编辑')"
      width="640px"
      append-to-body
    >
      <ElForm v-if="editingItem" label-width="100px">
        <ElFormItem :label="t('域')">
          <ElSelect v-model="editingItem.domain" style="width: 180px">
            <ElOption v-for="d in META_DOMAINS" :key="d.value" :label="t(d.label)" :value="d.value" />
          </ElSelect>
        </ElFormItem>
        <ElFormItem :label="t('类型')">
          <ElInput v-model="editingItem.metaType" :placeholder="t('元数据类型')" />
        </ElFormItem>
        <ElFormItem :label="t('键')">
          <ElInput v-model="editingItem.metaKey" :placeholder="t('元数据键')" />
        </ElFormItem>
        <ElFormItem :label="t('名称')">
          <ElInput v-model="editingItem.metaName" :placeholder="t('元数据名称')" />
        </ElFormItem>
        <ElFormItem :label="t('内容')">
          <ElInput v-model="contentText" type="textarea" :rows="8" :placeholder="t('JSON 内容')" />
        </ElFormItem>
        <ElFormItem :label="t('启用')">
          <ElSwitch v-model="editingItem.enabled" />
        </ElFormItem>
      </ElForm>
      <template #footer>
        <ElButton @click="showEditDialog = false">{{ t('取消') }}</ElButton>
        <ElButton type="primary" @click="saveEdit">{{ t('保存') }}</ElButton>
      </template>
    </ElDialog>
  </div>
</template>

<style scoped>
.meta-manage {
  width: 100%;
  height: 100vh;
  box-sizing: border-box;
}
.meta-layout {
  display: flex;
  height: 100%;
}
.meta-aside {
  width: 220px;
  border-right: 1px solid #ebeef5;
  padding: 12px;
  overflow-y: auto;
}
.meta-aside-title {
  font-weight: 600;
  margin-bottom: 12px;
}
.domain-item {
  padding: 8px 12px;
  cursor: pointer;
  border-radius: 4px;
  margin-bottom: 4px;
}
.domain-item.active {
  background: #ecf5ff;
  color: #409eff;
}
.domain-types {
  margin-top: 4px;
  padding-left: 12px;
}
.type-item {
  padding: 4px 8px;
  cursor: pointer;
  font-size: 13px;
  border-radius: 4px;
}
.type-item.active {
  background: #ecf5ff;
  color: #409eff;
}
.meta-main {
  flex: 1;
  padding: 12px;
  overflow: hidden;
}
.meta-toolbar {
  margin-bottom: 12px;
  display: flex;
  gap: 8px;
}
</style>
