<script setup lang="ts">
import PageModalItem from './PageModalItem.vue'
import { pageModal } from './pageModal'
</script>

<template>
  <PageModalItem
    v-for="(modal, index) in pageModal.stack.value"
    :key="modal.id"
    :modal="modal"
    :stack-index="index"
  />
</template>