<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElButton, ElMessage } from 'element-plus'
import DxDataGrid, { DxColumn, DxToolbar, DxItem } from 'devextreme-vue/data-grid'
import DxButton from 'devextreme-vue/button'
import { t } from '@/store/lang'
import type { TodoItem } from '@/types/schema'
import { httpRequest } from '@/utils/request'

const todoList = ref<TodoItem[]>([])
const activeTab = ref('todo')

async function refresh() {
  try {
    const res = await httpRequest('/api/lowcode/todo/list', {
      method: 'POST',
      body: JSON.stringify({ type: activeTab.value }),
    })
    if (!res.ok) {
      const errData = res.data as { msg?: string; message?: string } | undefined
      ElMessage.error(errData?.msg || errData?.message || `加载待办失败 (HTTP ${res.status})`)
      todoList.value = []
      return
    }
    const body = res.data as { res?: boolean; msg?: string; data?: TodoItem[] }
    if (body && body.res === false) {
      ElMessage.error(body.msg || '加载待办失败')
      todoList.value = []
      return
    }
    todoList.value = body.data || []
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : String(err))
    todoList.value = []
  }
}

onMounted(() => {
  refresh()
})

function onTabChange(tab: string) {
  activeTab.value = tab
  refresh()
}
</script>

<template>
  <div class="todo-view">
    <div class="todo-tabs">
      <button
        :class="['todo-tab', activeTab === 'todo' ? 'active' : '']"
        @click="onTabChange('todo')"
      >{{ t('待办') }}</button>
      <button
        :class="['todo-tab', activeTab === 'done' ? 'active' : '']"
        @click="onTabChange('done')"
      >{{ t('已办') }}</button>
    </div>
    <DxDataGrid
      :data-source="todoList"
      :hover-state-enabled="true"
      :show-borders="true"
      :show-column-lines="false"
      :show-row-lines="true"
      :paging="{ pageSize: 20 }"
      height="100%"
      key-expr="id"
    >
      <DxColumn data-field="title" :caption="t('标题')" />
      <DxColumn data-field="workflowName" :caption="t('流程')" :width="120" />
      <DxColumn data-field="currentNode" :caption="t('当前节点')" :width="120" />
      <DxColumn data-field="initiator" :caption="t('发起人')" :width="100" />
      <DxColumn data-field="createdAt" :caption="t('创建时间')" :width="160" />
    </DxDataGrid>
  </div>
</template>

<style scoped>
.todo-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 12px;
  background: #f5f5f5;
  gap: 8px;
}
.todo-tabs {
  display: flex;
  gap: 4px;
}
.todo-tab {
  padding: 6px 16px;
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.todo-tab.active {
  background: #409eff;
  color: #fff;
  border-color: #409eff;
}
</style>