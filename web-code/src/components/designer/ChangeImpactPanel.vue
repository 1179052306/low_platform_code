<script setup lang="ts">
import { ElDrawer, ElTable, ElTableColumn, ElTag, ElButton } from 'element-plus'
import { t } from '@/store/lang'
import type { BreakingChange } from '@/types/schema'

defineProps<{
  visible: boolean
  changes: BreakingChange[]
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const typeLabels: Record<string, string> = {
  FIELD_TYPE_CHANGE: '字段类型变更',
  FIELD_DELETE: '字段删除',
  FIELD_RENAME: '字段重命名',
  TABLE_RENAME: '表重命名',
}
</script>

<template>
  <ElDrawer
    :model-value="visible"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
    :title="t('变更影响分析')"
    size="450px"
    append-to-body
  >
    <ElTable :data="changes">
      <ElTableColumn :label="t('变更类型')" width="120">
        <template #default="{ row }">
          <ElTag type="warning" size="small">{{ t(typeLabels[row.type] || row.type) }}</ElTag>
        </template>
      </ElTableColumn>
      <ElTableColumn prop="description" :label="t('说明')" />
    </ElTable>
    <template #footer>
      <ElButton @click="emit('update:visible', false)">{{ t('关闭') }}</ElButton>
    </template>
  </ElDrawer>
</template>