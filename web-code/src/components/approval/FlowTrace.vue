<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElTimeline, ElTimelineItem, ElTag, ElMessage } from 'element-plus'
import { t } from '@/store/lang'
import { useApproval } from '@/composables/useApproval'
import type { ApprovalRecord } from '@/types/schema'

const props = defineProps<{
  bizCode: string
  bizId: string
}>()

const { getApprovalHistory } = useApproval()
const history = ref<ApprovalRecord[]>([])

watch(
  () => [props.bizCode, props.bizId],
  async () => {
    if (props.bizCode && props.bizId) {
      try {
        history.value = await getApprovalHistory(props.bizCode, props.bizId)
      } catch (err) {
        ElMessage.error(err instanceof Error ? err.message : String(err))
        history.value = []
      }
    }
  },
  { immediate: true },
)
</script>

<template>
  <div class="flow-trace">
    <ElTimeline>
      <ElTimelineItem
        v-for="record in history"
        :key="record.id"
        :timestamp="record.operatedAt"
        :type="record.action === 'approve' ? 'success' : record.action === 'reject' ? 'danger' : 'primary'"
      >
        <div class="trace-item">
          <span class="trace-node">{{ record.currentNode }}</span>
          <ElTag size="small" :type="record.action === 'approve' ? 'success' : record.action === 'reject' ? 'danger' : 'info'">
            {{ record.action }}
          </ElTag>
          <span class="trace-operator">{{ record.operator }}</span>
          <p v-if="record.comment" class="trace-comment">{{ record.comment }}</p>
        </div>
      </ElTimelineItem>
    </ElTimeline>
    <div v-if="!history.length" class="trace-empty">{{ t('暂无审批记录') }}</div>
  </div>
</template>

<style scoped>
.flow-trace {
  padding: 12px;
}
.trace-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.trace-node {
  font-weight: 600;
  font-size: 13px;
}
.trace-operator {
  font-size: 12px;
  color: #999;
}
.trace-comment {
  width: 100%;
  margin: 4px 0 0;
  font-size: 13px;
  color: #666;
}
.trace-empty {
  text-align: center;
  color: #ccc;
  padding: 20px;
}
</style>