<script setup lang="ts">
import { ElButton, ElTooltip, ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus'

const props = defineProps<{
  items?: any[]
  readOnly?: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{
  itemClick: [item: any]
}>()

function onItemClick(item: any) {
  emit('itemClick', item)
  item?.onClick?.()
}
</script>

<template>
  <div class="el-toolbar-box">
    <template v-for="(item, i) in items" :key="i">
      <ElDropdown
        v-if="item.items?.length"
        :disabled="readOnly || disabled"
        @command="(cmd: any) => onItemClick(cmd)"
      >
        <ElButton :disabled="readOnly || disabled">
          <span v-if="item.icon">{{ item.icon }}</span>
          {{ item.text ?? item.label }}
        </ElButton>
        <template #dropdown>
          <ElDropdownMenu>
            <ElDropdownItem
              v-for="(sub, j) in item.items"
              :key="j"
              :command="sub"
            >
              <span v-if="sub.icon">{{ sub.icon }}</span>
              {{ sub.text ?? sub.label }}
            </ElDropdownItem>
          </ElDropdownMenu>
        </template>
      </ElDropdown>
      <ElTooltip
        v-else-if="item.tooltip"
        :content="item.tooltip"
        placement="top"
      >
        <ElButton
          :type="item.type ?? 'default'"
          :disabled="readOnly || disabled || item.disabled"
          @click="onItemClick(item)"
        >
          <span v-if="item.icon">{{ item.icon }}</span>
          {{ item.text ?? item.label }}
        </ElButton>
      </ElTooltip>
      <ElButton
        v-else
        :type="item.type ?? 'default'"
        :disabled="readOnly || disabled || item.disabled"
        @click="onItemClick(item)"
      >
        <span v-if="item.icon">{{ item.icon }}</span>
        {{ item.text ?? item.label }}
      </ElButton>
    </template>
    <slot />
  </div>
</template>

<style scoped>
.el-toolbar-box {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
</style>