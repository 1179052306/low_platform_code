<script setup lang="ts">
import ApprovalBar from '@/components/approval/ApprovalBar.vue'

const props = defineProps<{
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
  <ApprovalBar
    :biz-code="bizCode"
    :biz-id="bizId"
    :current-node="currentNode"
    @approve="emit('approve')"
    @reject="emit('reject')"
    @transfer="emit('transfer')"
    @countersign="emit('countersign')"
  />
</template>