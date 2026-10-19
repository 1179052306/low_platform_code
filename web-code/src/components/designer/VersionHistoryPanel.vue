<script setup lang="ts">
import { computed, watch } from 'vue'
import { ElDrawer, ElButton, ElMessage, ElMessageBox, ElTag } from 'element-plus'
import LcTable from '@/materials/components/LcTable.vue'
import { t } from '@/store/lang'
import { useVersionHistory } from '@/composables/useVersionHistory'
import type { MetaVersion } from '@/types/schema'

const props = defineProps<{
  visible: boolean
  metaKey: string
  pageId?: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  rollback: [versionId: string]
}>()

const { versions, loading, getVersionHistory, rollbackVersion } = useVersionHistory()

const versionColumns = computed(() => [
  { dataField: 'versionNo', caption: t('版本号'), width: 80 },
  { dataField: 'changeLog', caption: t('变更说明') },
  { dataField: 'breaking', caption: t('BREAKING'), width: 90, cellTemplate: 'breakingTpl' },
  { dataField: 'createdBy', caption: t('创建人'), width: 100 },
  { dataField: 'createdAt', caption: t('时间'), width: 160 },
  { dataField: '_op', caption: t('操作'), width: 120, cellTemplate: 'actionTpl' },
])

watch(
  () => props.visible,
  async (v) => {
    if (v && props.metaKey) {
      await getVersionHistory(props.metaKey)
    }
  },
)

function onPreview(version: MetaVersion) {
  if (!props.pageId || !version.versionId) return
  const url = `/preview?pageId=${encodeURIComponent(props.pageId)}&version=${encodeURIComponent(version.versionId)}`
  window.open(url, '_blank')
}

async function onRollback(version: MetaVersion) {
  try {
    await ElMessageBox.confirm(
      t('确认回滚到版本') + ` ${version.versionNo}？`,
      t('提示'),
      { type: 'warning' },
    )
    const ok = await rollbackVersion(props.metaKey, version.versionId)
    if (ok) {
      ElMessage.success(t('回滚成功'))
      emit('rollback', version.versionId)
      emit('update:visible', false)
    } else {
      ElMessage.error(t('回滚失败'))
    }
  } catch {
    /* 取消 */
  }
}

function onClose() {
  emit('update:visible', false)
}
</script>

<template>
  <ElDrawer
    :model-value="visible"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
    :title="t('版本历史')"
    size="500px"
    append-to-body
  >
    <div v-loading="loading" class="version-history-grid">
      <LcTable
        :data-source="versions"
        :show-borders="true"
        :column-auto-width="true"
        :filter-row="{ visible: true }"
        height="100%"
        :columns="versionColumns"
      >
        <template #breakingTpl="{ value }">
          <ElTag v-if="value" type="danger" size="small">{{ t('是') }}</ElTag>
          <ElTag v-else type="info" size="small">{{ t('否') }}</ElTag>
        </template>
        <template #actionTpl="{ data }">
          <ElButton type="primary" link @click="onPreview(data as MetaVersion)">{{ t('预览') }}</ElButton>
          <ElButton type="warning" link @click="onRollback(data as MetaVersion)">{{ t('回滚') }}</ElButton>
        </template>
      </LcTable>
    </div>
    <template #footer>
      <ElButton @click="onClose">{{ t('关闭') }}</ElButton>
    </template>
  </ElDrawer>
</template>

<style scoped>
.version-history-grid {
  height: 100%;
}
</style>