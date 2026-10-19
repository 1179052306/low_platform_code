<script setup lang="ts">
import DialogMaxButton from './DialogMaxButton.vue'

defineProps<{
  title: string
  isMaximized: boolean
}>()

defineEmits<{
  toggle: []
}>()
</script>

<template>
  <div class="dialog-max-header">
    <span class="dialog-max-title">{{ title }}</span>
    <DialogMaxButton :maximized="isMaximized" @toggle="$emit('toggle')" />
  </div>
</template>

<style scoped>
.dialog-max-header {
  display: flex;
  align-items: center;
  width: 100%;
  height: 48px;
  padding: 0 48px 0 0;
  box-sizing: border-box;
}
.dialog-max-title {
  flex: 1;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-left: 12px;
}
</style>