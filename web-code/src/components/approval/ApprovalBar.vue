<script setup lang="ts">
import { ElButton } from 'element-plus'
import { t } from '@/store/lang'

defineProps<{
  bizCode?: string
  bizId?: string
  currentNode?: string
}>()

const emit = defineEmits<{
  approve: []
  reject: []
  transfer: []
  countersign: []
}>()
</script>

<template>
  <div class="approval-bar">
    <span class="ab-node" v-if="currentNode">{{ t('当前节点') }}：{{ currentNode }}</span>
    <ElButton type="success" @click="emit('approve')">{{ t('同意') }}</ElButton>
    <ElButton type="danger" @click="emit('reject')">{{ t('驳回') }}</ElButton>
    <ElButton @click="emit('transfer')">{{ t('转办') }}</ElButton>
    <ElButton @click="emit('countersign')">{{ t('加签') }}</ElButton>
  </div>
</template>

<style scoped>
.approval-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #f0f7ff;
  border-radius: 4px;
}
.ab-node {
  font-size: 13px;
  color: #409eff;
  margin-right: auto;
}
</style>