<script setup lang="ts">
withDefaults(
  defineProps<{
    size?: number
  }>(),
  { size: 16 }
)
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 20 20"
    fill="none"
    stroke="currentColor"
    stroke-width="2"
    stroke-linecap="round"
    stroke-linejoin="round"
    class="search-icon"
  >
    <circle cx="8.5" cy="8.5" r="5.5" />
    <line x1="13" y1="13" x2="17.5" y2="17.5" />
  </svg>
</template>

<style scoped>
.search-icon {
  display: inline-block;
  vertical-align: middle;
  flex-shrink: 0;
}
</style>