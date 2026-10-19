<script setup lang="ts">
import { ElButton } from 'element-plus'

const props = defineProps<{
  text?: string
  icon?: string
  readOnly?: boolean
  disabled?: boolean
  direction?: 'up' | 'down' | 'left' | 'right'
}>()

const emit = defineEmits<{
  click: [e: MouseEvent]
}>()
</script>

<template>
  <div class="el-speed-dial" :class="`el-speed-dial-${direction ?? 'up'}`">
    <ElButton
      circle
      :disabled="readOnly || disabled"
      @click="(e: MouseEvent) => emit('click', e)"
    >
      <span v-if="icon">{{ icon }}</span>
      <span v-else>+</span>
    </ElButton>
  </div>
</template>

<style scoped>
.el-speed-dial {
  position: fixed;
  z-index: 100;
}
.el-speed-dial-up { bottom: 24px; right: 24px; }
.el-speed-dial-down { top: 24px; right: 24px; }
.el-speed-dial-left { bottom: 24px; right: 24px; }
.el-speed-dial-right { bottom: 24px; left: 24px; }
</style>