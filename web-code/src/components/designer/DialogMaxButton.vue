<script setup lang="ts">
defineProps<{
  maximized: boolean
}>()

defineEmits<{
  toggle: []
}>()
</script>

<template>
  <button
    class="dialog-max-btn"
    @click="$emit('toggle')"
    :title="maximized ? '还原' : '最大化'"
  >
    <svg v-if="!maximized" viewBox="0 0 16 16" width="14" height="14">
      <rect x="2.5" y="2.5" width="11" height="11" fill="none" stroke="currentColor" stroke-width="1.4" />
    </svg>
    <svg v-else viewBox="0 0 16 16" width="14" height="14">
      <rect x="2.5" y="4.5" width="9" height="9" fill="none" stroke="currentColor" stroke-width="1.4" />
      <path d="M5 4.5 V2 H14 V11 H11.5" fill="none" stroke="currentColor" stroke-width="1.4" />
    </svg>
  </button>
</template>