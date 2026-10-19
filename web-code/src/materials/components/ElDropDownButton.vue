<script setup lang="ts">
import { ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus'
import { computed } from 'vue'
import { designerStore } from '@/store/designer'

const props = defineProps<{
  text?: string
  icon?: string
  readOnly?: boolean
  disabled?: boolean
  items?: { text?: string; icon?: string; onClick?: () => void }[]
  splitButton?: boolean
}>()

const emit = defineEmits<{
  click: []
  itemClick: [item: any]
}>()

const isDesign = computed(() => !designerStore.previewMode)
const displayText = computed(() => props.text || '下拉按钮')

function onItemClick(item: any) {
  emit('itemClick', item)
  item?.onClick?.()
}
</script>

<template>
  <ElDropdown
    :disabled="readOnly || disabled || isDesign"
    :split-button="splitButton"
    @click="() => emit('click')"
  >
    <span class="el-dd-btn-text">
      <span v-if="icon">{{ icon }}</span>
      <span>{{ displayText }}</span>
    </span>
    <template #dropdown>
      <ElDropdownMenu>
        <ElDropdownItem
          v-for="(item, i) in items"
          :key="i"
          @click="onItemClick(item)"
        >
          <span v-if="item.icon">{{ item.icon }}</span>
          {{ item.text }}
        </ElDropdownItem>
        <ElDropdownItem v-if="!items || !items.length" disabled>
          （请配置下拉项）
        </ElDropdownItem>
      </ElDropdownMenu>
    </template>
  </ElDropdown>
</template>

<style scoped>
.el-dd-btn-text {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  font-size: 13px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: var(--el-fill-color-blank);
  white-space: nowrap;
}
</style>