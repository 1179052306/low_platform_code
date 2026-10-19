<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElPopover } from 'element-plus'
import { designerStore } from '@/store/designer'

const props = defineProps<{
  value?: any
  modelValue?: any
  readOnly?: boolean
  disabled?: boolean
  placeholder?: string
  contentTemplate?: string
  dropDownOptions?: any
}>()

const emit = defineEmits<{
  'update:value': [val: any]
  'update:modelValue': [val: any]
  valueChanged: [e: { value: any }]
  opened: []
  closed: []
}>()

const visible = ref(false)
const isDesign = computed(() => !designerStore.previewMode)
</script>

<template>
  <ElPopover
    v-model:visible="visible"
    trigger="click"
    :width="dropDownOptions?.width ?? 300"
    placement="bottom-start"
    :disabled="isDesign"
    @show="() => emit('opened')"
    @hide="() => emit('closed')"
  >
    <template #reference>
      <div class="el-dropdown-box-trigger" :class="{ disabled: readOnly || disabled }">
        <span>{{ placeholder ?? '请选择' }}</span>
      </div>
    </template>
    <slot />
  </ElPopover>
</template>

<style scoped>
.el-dropdown-box-trigger {
  min-height: 32px;
  padding: 0 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  display: flex;
  align-items: center;
  cursor: pointer;
  background: var(--el-fill-color-blank);
  width: 100%;
  box-sizing: border-box;
}
.el-dropdown-box-trigger.disabled {
  background: var(--el-fill-color-light);
  cursor: not-allowed;
  color: var(--el-text-color-placeholder);
}
</style>