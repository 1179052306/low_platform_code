<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import DxDropDownBox from 'devextreme-vue/drop-down-box'
import DxDataGrid from 'devextreme-vue/data-grid'
import DxButton from 'devextreme-vue/button'
import CustomStore from 'devextreme/data/custom_store'
import { httpRequest, extractPayload } from '@/utils/request'
import { pageModal } from '@/components/renderer/pageModal'
import { loadPage } from '@/store/persistence'
import { t } from '@/store/lang'

const props = withDefaults(
  defineProps<{
    refPageId?: string
    displayField?: string
    valueField?: string
    placeholder?: string
    value?: any
    disabled?: boolean
    visible?: boolean
  }>(),
  {
    refPageId: '',
    displayField: '',
    valueField: 'id',
    placeholder: '请选择',
    value: null,
    disabled: false,
    visible: true,
  },
)

const emit = defineEmits<{
  (e: 'valueChanged', value: unknown): void
  (e: 'selectionChanged', data: Record<string, unknown> | null): void
}>()

const isOpen = ref(false)
const displayValue = ref('')
const selectedRowData = ref<Record<string, unknown> | null>(null)
const gridColumns = ref<{ dataField: string; caption: string }[]>([])
const tableName = ref('')

const valueExpr = computed(() => props.valueField || 'id')
const displayExpr = computed(() => props.displayField || 'name')

async function loadPageMeta() {
  if (!props.refPageId) {
    gridColumns.value = []
    tableName.value = ''
    return
  }
  try {
    const page = await loadPage(props.refPageId)
    if (!page) return
    tableName.value = page.tableName || ''
    const schema = page.bizSchemas?.table?.[0]
    let columns: { dataField: string; caption: string }[] = []
    if (schema && Array.isArray((schema as any).props?.columns)) {
      columns = (schema as any).props.columns
        .filter((c: any) => c.visible !== false)
        .map((c: any) => ({
          dataField: c.dataField || '',
          caption: c.caption || c.dataField || '',
        }))
    }
    if (!columns.length) {
      const formNodes = page.root || []
      for (const node of formNodes) {
        if (node.dbField) {
          columns.push({ dataField: node.dbField, caption: node.fieldName || node.dbField })
        }
      }
    }
    gridColumns.value = columns
  } catch {
    gridColumns.value = []
  }
}

watch(() => props.refPageId, loadPageMeta, { immediate: false })
onMounted(loadPageMeta)

const dataSource = computed(() => {
  if (!props.refPageId || !tableName.value) return null
  return new CustomStore({
    key: valueExpr.value,
    load: async (loadOptions: any) => {
      try {
        const params: Record<string, unknown> = { pageId: props.refPageId }
        if (loadOptions.skip !== undefined) params.skip = loadOptions.skip
        if (loadOptions.take !== undefined) params.take = loadOptions.take
        if (loadOptions.filter) {
          params.filterData = JSON.stringify(convertFilter(loadOptions.filter))
        }
        const result = await httpRequest('/api/lowcode/page/query-data', {
          method: 'POST',
          body: JSON.stringify(params),
        })
        if (!result.ok) throw new Error(`HTTP ${result.status}`)
        const payload = extractPayload(result.data)
        return { data: payload.data, totalCount: payload.totalCount }
      } catch (err) {
        console.error('[基础资料数据加载失败]', err)
        throw err
      }
    },
  })
})

function convertFilter(dxFilter: unknown): unknown[] {
  const result: unknown[] = []
  if (!Array.isArray(dxFilter)) return result
  if (dxFilter.length >= 3 && typeof dxFilter[0] === 'string') {
    result.push({ Symbol: 'and' })
    result.push({ Id: dxFilter[0], Symbol: dxFilter[1], Val: dxFilter[2], TableAlias: 'T' })
  } else {
    for (let i = 0; i < dxFilter.length; i++) {
      const item = dxFilter[i]
      if (Array.isArray(item)) {
        const inner = convertFilter(item)
        if (inner.length > 0) {
          let logic = 'and'
          if (i > 0 && (dxFilter[i - 1] === 'and' || dxFilter[i - 1] === 'or')) {
            logic = dxFilter[i - 1] as string
          }
          result.push({ Symbol: logic })
          for (let j = 1; j < inner.length; j++) result.push(inner[j])
        }
      }
    }
  }
  return result
}

function onSelectionChanged(e: { selectedRowsData: Record<string, unknown>[] }) {
  if (e.selectedRowsData.length > 0) {
    const row = e.selectedRowsData[0]
    selectedRowData.value = row
    const val = row[valueExpr.value]
    displayValue.value = String(row[displayExpr.value] ?? '')
    emit('valueChanged', val)
    emit('selectionChanged', row)
    isOpen.value = false
  }
}

watch(
  () => props.value,
  async (newVal) => {
    if (newVal == null || newVal === '') {
      displayValue.value = ''
      selectedRowData.value = null
      return
    }
    if (selectedRowData.value && selectedRowData.value[valueExpr.value] === newVal) return
    if (!dataSource.value) return
    try {
      const store = dataSource.value
      const data = await store.load({})
      const row = (data as any).data?.find((r: any) => r[valueExpr.value] === newVal)
      if (row) {
        displayValue.value = String(row[displayExpr.value] ?? '')
        selectedRowData.value = row
      }
    } catch {
      displayValue.value = String(newVal)
    }
  },
  { immediate: true },
)

async function openPopupPage() {
  if (!props.refPageId || props.disabled) return
  try {
    const result = await pageModal.openPage(props.refPageId, {}, { mode: 'popup', title: t('选择基础资料'), width: 900, height: 600, bizMode: 'table' })
    if (result && typeof result === 'object') {
      const row = result as Record<string, unknown>
      selectedRowData.value = row
      const val = row[valueExpr.value]
      displayValue.value = String(row[displayExpr.value] ?? '')
      emit('valueChanged', val)
      emit('selectionChanged', row)
    }
  } catch (err) {
    console.error('[打开基础资料页面失败]', err)
  }
}
</script>

<template>
  <div v-show="visible" class="dx-base-data-control">
    <DxDropDownBox
      v-model:value="isOpen"
      :display-value="displayValue"
      :placeholder="placeholder"
      :disabled="disabled"

      :drop-down-options="{ width: 500 }"
      content-template="gridContent"
    >
      <template #gridContent>
        <DxDataGrid
          v-if="dataSource"
          :data-source="dataSource"
          :columns="gridColumns"
          :selection="{ mode: 'single', showCheckBoxesMode: 'none' }"
          :hover-state-enabled="true"
          height="250"
          :filter-row="{ visible: true }"
          :selected-row-keys="value != null ? [value] : []"
          @selection-changed="onSelectionChanged"
        />
        <div v-else class="no-data-tip">{{ t('请先配置关联基础资料页面') }}</div>
      </template>
    </DxDropDownBox>
    <DxButton
      icon="search"
      styling-mode="text"
      :width="28"
      :height="28"
      :disabled="disabled"
      :hint="t('打开选择')"
      @click="openPopupPage"
    />
  </div>
</template>

<style scoped>
.dx-base-data-control {
  display: flex;
  align-items: center;
  gap: 2px;
  width: 100%;

  box-sizing: border-box;
}
.dx-base-data-control > :deep(.dx-dropdownbox) {
  flex: 1;
  min-width: 0;
}
.dx-base-data-control > :deep(.dx-button) {
  flex-shrink: 0;
}
.no-data-tip {
  padding: 20px;
  text-align: center;
  color: #999;
  font-size: 14px;
}
</style>
