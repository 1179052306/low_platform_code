<script setup lang="ts">
import { ElLoading } from 'element-plus'
import { ref, watch, onUnmounted, computed } from 'vue'
import { designerStore } from '@/store/designer'

const props = defineProps<{
  visible?: boolean
  message?: string
  shading?: boolean
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  shown: []
  hidden: []
}>()

const isDesign = computed(() => !designerStore.previewMode)
const loadingInstance = ref<any>(null)

watch(
  () => [props.visible, isDesign.value] as const,
  ([v, design]) => {
    if (design) return
    if (v && !loadingInstance.value) {
      loadingInstance.value = ElLoading.service({
        fullscreen: true,
        text: props.message ?? '加载中...',
        background: props.shading !== false ? 'rgba(0, 0, 0, 0.7)' : 'transparent',
      })
      emit('shown')
    } else if (!v && loadingInstance.value) {
      loadingInstance.value.close()
      loadingInstance.value = null
      emit('hidden')
    }
  },
  { immediate: true },
)

onUnmounted(() => {
  loadingInstance.value?.close()
})
</script>

<template>
  <div v-if="isDesign" class="el-load-panel-placeholder">
    <span>◍</span>
    <span>{{ message ?? '加载遮罩' }}</span>
  </div>
  <span v-else style="display:none" />
</template>

<style scoped>
.el-load-panel-placeholder {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: 1px dashed var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 13px;
  width: 100%;
  box-sizing: border-box;
}
</style>