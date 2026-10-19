<script setup lang="ts">
/**
 * ElListBox — 替换 DxList
 * 用 ElScroll + v-for 渲染列表项，支持单选/多选，DevExtreme 风格 props。
 */
import { computed, ref, watch } from 'vue'
import { ElScrollbar, ElCheckbox } from 'element-plus'
import { resolveDataSource } from '../dx-to-el'

const props = defineProps<{
  value?: any
  modelValue?: any
  dataSource?: any
  items?: any[]
  displayExpr?: string | ((item: any) => string)
  valueExpr?: string | ((item: any) => any)
  selectedItemKeys?: any[]
  selectionMode?: 'single' | 'multiple' | 'none'
  height?: number | string
  readOnly?: boolean
  disabled?: boolean
  searchEnabled?: boolean
  noDataText?: string
}>()

const emit = defineEmits<{
  'update:value': [val: any[]]
  'update:modelValue': [val: any[]]
  valueChanged: [e: { value: any[]; selectedItems: any[] }]
  selectionChanged: [e: { selectedItems: any[]; selectedItemKeys: any[] }]
  itemClick: [e: { item: any; key: any }]
}>()

const list = ref<any[]>([])
const selectedKeys = ref<any[]>([])
const searchText = ref('')

function getKey(item: any): any {
  if (typeof props.valueExpr === 'function') return props.valueExpr(item)
  if (props.valueExpr) return item?.[props.valueExpr]
  return item
}
function getLabel(item: any): string {
  if (typeof props.displayExpr === 'function') return props.displayExpr(item)
  if (props.displayExpr) return item?.[props.displayExpr] ?? ''
  return String(item)
}

async function loadData() {
  const ds = props.dataSource ?? props.items
  if (!ds) { list.value = []; return }
  list.value = await resolveDataSource(ds)
}
watch(() => [props.dataSource, props.items], loadData, { immediate: true })

function syncFromProps() {
  const keys = props.selectedItemKeys ?? (Array.isArray(props.modelValue) ? props.modelValue : (props.modelValue != null ? [props.modelValue] : (Array.isArray(props.value) ? props.value : (props.value != null ? [props.value] : []))))
  selectedKeys.value = Array.isArray(keys) ? [...keys] : []
}
watch(() => [props.selectedItemKeys, props.modelValue, props.value], syncFromProps, { immediate: true })

const filtered = computed(() => {
  if (!props.searchEnabled || !searchText.value) return list.value
  const kw = searchText.value.toLowerCase()
  return list.value.filter((it) => getLabel(it).toLowerCase().includes(kw))
})

function isSelected(key: any): boolean {
  return selectedKeys.value.some((k) => k === key)
}

function toggle(item: any) {
  const key = getKey(item)
  if (props.readOnly || props.disabled) return
  emit('itemClick', { item, key })
  if (props.selectionMode === 'none') return
  if (props.selectionMode === 'multiple') {
    if (isSelected(key)) selectedKeys.value = selectedKeys.value.filter((k) => k !== key)
    else selectedKeys.value = [...selectedKeys.value, key]
  } else {
    selectedKeys.value = isSelected(key) ? [] : [key]
  }
  emitChange()
}

function emitChange() {
  const items = list.value.filter((it) => isSelected(getKey(it)))
  emit('update:value', selectedKeys.value)
  emit('update:modelValue', selectedKeys.value)
  emit('valueChanged', { value: selectedKeys.value, selectedItems: items })
  emit('selectionChanged', { selectedItems: items, selectedItemKeys: selectedKeys.value })
}

const heightStyle = computed(() => {
  const h = props.height
  if (h == null) return '300px'
  return typeof h === 'number' ? `${h}px` : h
})
</script>

<template>
  <div class="el-list-box" :class="{ 'is-disabled': readOnly || disabled }">
    <input
      v-if="searchEnabled"
      v-model="searchText"
      class="el-list-box__search"
      placeholder="搜索"
      :disabled="readOnly || disabled"
    >
    <ElScrollbar :height="heightStyle">
      <ul class="el-list-box__ul">
        <li
          v-for="item in filtered"
          :key="getKey(item)"
          class="el-list-box__item"
          :class="{ 'is-selected': isSelected(getKey(item)) }"
          @click="toggle(item)"
        >
          <ElCheckbox
            v-if="selectionMode === 'multiple'"
            :model-value="isSelected(getKey(item))"
            :disabled="readOnly || disabled"
            @click.stop="toggle(item)"
          />
          <span class="el-list-box__label">{{ getLabel(item) }}</span>
        </li>
        <li v-if="!filtered.length" class="el-list-box__empty">
          {{ noDataText ?? '暂无数据' }}
        </li>
      </ul>
    </ElScrollbar>
  </div>
</template>

<style scoped>
.el-list-box {
  width: 100%;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  background: var(--el-bg-color, #fff);
  box-sizing: border-box;
  overflow: hidden;
}
.el-list-box__search {
  width: 100%;
  box-sizing: border-box;
  border: none;
  border-bottom: 1px solid var(--el-border-color, #dcdfe6);
  padding: 8px 12px;
  outline: none;
  font-size: 14px;
}
.el-list-box__ul {
  list-style: none;
  margin: 0;
  padding: 0;
}
.el-list-box__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  cursor: pointer;
  user-select: none;
  font-size: 14px;
  color: var(--el-text-color-regular, #606266);
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  transition: background-color 0.2s;
}
.el-list-box__item:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}
.el-list-box__item.is-selected {
  background: var(--el-color-primary-light-9, #ecf5ff);
  color: var(--el-color-primary, #409eff);
}
.el-list-box__label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.el-list-box__empty {
  padding: 16px;
  text-align: center;
  color: var(--el-text-color-placeholder, #a8abb2);
  font-size: 14px;
}
.el-list-box.is-disabled .el-list-box__item {
  cursor: not-allowed;
}
</style>