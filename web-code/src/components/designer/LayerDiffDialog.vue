<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElDialog, ElButton, ElTable, ElTableColumn, ElMessage } from 'element-plus'
import { t } from '@/store/lang'
import { designerStore } from '@/store/designer'
import { useMerge, type LayerDiffItem } from '@/composables/useMerge'
import { useDialogDragAndMax } from './useDialogDragAndMax'
import DialogMaxHeader from './DialogMaxHeader.vue'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const { loadMerged, diffLayers } = useMerge()
const diffList = ref<LayerDiffItem[]>([])
const loading = ref(false)

watch(
  () => props.visible,
  async (v) => {
    if (!v) return
    loading.value = true
    try {
      const merged = await loadMerged()
      diffList.value = diffLayers(merged.root, designerStore.schema)
    } catch {
      ElMessage.warning(t('合并接口未就绪，无法对比'))
      diffList.value = []
    } finally {
      loading.value = false
    }
  },
)

function onClose() {
  emit('update:visible', false)
}

const { isMaximized, toggleMaximize, resetDialog } = useDialogDragAndMax()
watch(() => props.visible, (v) => { if (!v) resetDialog() })

function statusText(status: LayerDiffItem['status']): string {
  switch (status) {
    case 'added':
      return t('新增')
    case 'modified':
      return t('修改')
    case 'removed':
      return t('删除')
  }
}
</script>

<template>
  <ElDialog
    :model-value="visible"
    @update:model-value="(v: boolean) => emit('update:visible', v)"
    width="640px"
    append-to-body
    draggable
    :fullscreen="isMaximized"
  >
    <template #header>
      <DialogMaxHeader :title="t('层间差异对比')" :is-maximized="isMaximized" @toggle="toggleMaximize" />
    </template>
    <div v-if="loading" class="diff-loading">{{ t('加载中...') }}</div>
    <div v-else-if="!diffList.length" class="diff-empty">{{ t('无差异') }}</div>
    <ElTable v-else :data="diffList" border size="small" style="width: 100%">
      <ElTableColumn :label="t('节点 ID')" prop="id" width="240" />
      <ElTableColumn :label="t('类型')" prop="type" width="160" />
      <ElTableColumn :label="t('状态')" width="100">
        <template #default="{ row }">
          <span :class="`diff-status-${row.status}`">{{ statusText(row.status) }}</span>
        </template>
      </ElTableColumn>
    </ElTable>
    <template #footer>
      <ElButton @click="onClose">{{ t('关闭') }}</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.diff-loading,
.diff-empty {
  text-align: center;
  padding: 40px 0;
  color: #909399;
}
.diff-status-added {
  color: #67c23a;
}
.diff-status-modified {
  color: #e6a23c;
}
.diff-status-removed {
  color: #f56c6c;
}
</style>