<script setup lang="ts">
import { ElIcon } from 'element-plus'
</script>

<template>
  <ElIcon class="el-load-indicator is-loading">
    <svg viewBox="0 0 1024 1024" xmlns="http://www.w3.org/2000/svg">
      <path fill="currentColor" d="M512 64a32 32 0 0 1 32 32v192a32 32 0 0 1-64 0V96a32 32 0 0 1 32-32z" />
      <path fill="currentColor" d="M512 832a32 32 0 0 1 32 32v64a32 32 0 1 1-64 0v-64a32 32 0 0 1 32-32z" opacity=".5" />
      <path fill="currentColor" d="M960 512a32 32 0 0 1-32 32H736a32 32 0 1 1 0-64h192a32 32 0 0 1 32 32z" opacity=".8" />
      <path fill="currentColor" d="M288 512a32 32 0 0 1-32 32H96a32 32 0 1 1 0-64h160a32 32 0 0 1 32 32z" opacity=".3" />
    </svg>
  </ElIcon>
</template>

<style scoped>
.el-load-indicator.is-loading {
  animation: rotating 2s linear infinite;
}
@keyframes rotating {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}
</style>