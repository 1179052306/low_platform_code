<script setup lang="ts">
import FlowTrace from './FlowTrace.vue'

defineProps<{
  bizCode: string
  bizId: string
}>()
</script>

<template>
  <FlowTrace :biz-code="bizCode" :biz-id="bizId" />
</template>