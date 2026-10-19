<script setup lang="ts">
import { computed } from 'vue'
import { ElButton } from 'element-plus'

const props = defineProps<{
  text?: string
  type?: 'default' | 'primary' | 'success' | 'warning' | 'danger' | 'info'
  stylingMode?: 'contained' | 'outlined' | 'text'
  readOnly?: boolean
  disabled?: boolean
  icon?: string
  width?: number | string
  height?: number | string
  fontColor?: string
  bgColor?: string
  hoverColor?: string
  useSubmitBehavior?: boolean
}>()

const emit = defineEmits<{
  click: [e: MouseEvent]
}>()

const btnType = computed(() => props.type ?? 'default')

const btnStyle = computed(() => {
  const s: Record<string, string> = {}
  if (props.fontColor) s['--el-button-text-color'] = props.fontColor
  if (props.bgColor) s['--el-button-bg-color'] = props.bgColor
  if (props.hoverColor) s['--el-button-hover-bg-color'] = props.hoverColor
  if (props.width) s.width = typeof props.width === 'number' ? `${props.width}px` : props.width
  if (props.height) s.height = typeof props.height === 'number' ? `${props.height}px` : props.height
  return s
})

const isPlain = computed(() => props.stylingMode === 'outlined')
const isText = computed(() => props.stylingMode === 'text')
</script>

<template>
  <ElButton
    :type="btnType"
    :plain="isPlain"
    :text="isText"
    :disabled="readOnly || disabled"
    :native-type="useSubmitBehavior ? 'submit' : 'button'"
    :style="btnStyle"
    @click="(e: MouseEvent) => emit('click', e)"
  >
    <span v-if="icon" class="el-btn-icon">{{ icon }}</span>
    <span v-if="text">{{ text }}</span>
  </ElButton>
</template>

<style scoped>
.el-btn-icon {
  margin-right: 4px;
}
</style>