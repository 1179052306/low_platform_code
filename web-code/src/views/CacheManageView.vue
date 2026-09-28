<script setup lang="ts">
import { ref, onMounted } from 'vue'
import DxDataGrid, {
  DxToolbar,
  DxItem,
  DxColumn,
} from 'devextreme-vue/data-grid'
import DxButton from 'devextreme-vue/button'
import DxLoadPanel from 'devextreme-vue/load-panel'
import { httpRequest } from '@/utils/request'
import { t, loadModule } from '@/store/lang'

defineOptions({ name: 'CacheManageView' })

/** 实体元数据 + 缓存指标（合并后给表格展示） */
interface EntityWithMetrics {
  cacheName: string
  tableName: string
  idColumn: string
  fieldCount: number
  schemaVersion: number
  ttl: number
  maxCapacity: number
  relations: any[]
  l1HitRate: number
  l1HitCount: number
  l1MissCount: number
  l2HitRate: number
  l2HitCount: number
  l2MissCount: number
  dbLoadCount: number
  avgDbLoadMillis: number
  invalidateCount: number
}

const statsList = ref<EntityWithMetrics[]>([])
const loadPanelVisible = ref(false)
const loadPanelText = ref('')

/** 查询测试面板状态 */
const queryCacheName = ref('B_ITEM')
const queryId = ref('10001')
const queryProjection = ref('PDA')
const queryResult = ref<any>(null)
const queryVisible = ref(false)

/** 管理接口 admin token（从 localStorage 读取或用户输入） */
const adminToken = ref(localStorage.getItem('basedata_admin_token') || '')

/** 保存 admin token 到 localStorage */
function saveAdminToken() {
  localStorage.setItem('basedata_admin_token', adminToken.value)
  alert(t('Admin Token 已保存'))
}

function formatRate(val: number): string {
  if (val == null) return '-'
  return (val * 100).toFixed(2) + '%'
}

/**
 * 加载实体列表 + 每个实体的缓存指标。
 * 先调 GET /api/basedata/entities 获取实体列表，
 * 再对每个实体调 GET /api/basedata/metrics 获取指标，合并后展示。
 */
async function loadStats() {
  loadPanelVisible.value = true
  loadPanelText.value = t('加载缓存统计...')
  try {
    const entityResp = await httpRequest('/api/basedata/entities', {
      method: 'GET',
    })
    const entityData = entityResp.data as any
    const entities: any[] = (entityData && entityData.data) || entityData || []

    const results: EntityWithMetrics[] = []
    for (const entity of entities) {
      const metrics = await loadMetrics(entity.cacheName)
      results.push({
        cacheName: entity.cacheName,
        tableName: entity.tableName,
        idColumn: entity.idColumn,
        fieldCount: entity.fieldCount,
        schemaVersion: entity.schemaVersion,
        ttl: entity.ttl,
        maxCapacity: entity.maxCapacity,
        relations: entity.relations || [],
        l1HitRate: metrics.l1HitRate || 0,
        l1HitCount: metrics.l1HitCount || 0,
        l1MissCount: metrics.l1MissCount || 0,
        l2HitRate: metrics.l2HitRate || 0,
        l2HitCount: metrics.l2HitCount || 0,
        l2MissCount: metrics.l2MissCount || 0,
        dbLoadCount: metrics.dbLoadCount || 0,
        avgDbLoadMillis: metrics.avgDbLoadMillis || 0,
        invalidateCount: metrics.invalidateCount || 0,
      })
    }
    statsList.value = results
  } catch (e) {
    console.error('加载缓存统计失败', e)
  } finally {
    loadPanelVisible.value = false
  }
}

/** 获取单个实体的缓存指标 */
async function loadMetrics(cacheName: string): Promise<any> {
  try {
    const resp = await httpRequest(
      `/api/basedata/metrics?cacheName=${cacheName}`,
      { method: 'GET' }
    )
    const data = resp.data as any
    return (data && data.data) || data || {}
  } catch {
    return {}
  }
}

/** 清除指定实体的全部缓存（调用 invalidateAll 端点清空 L1+L2+空值缓存） */
async function evictCache(cacheName: string) {
  if (!confirm(`确认清除 ${cacheName} 的全部缓存？`)) return
  loadPanelVisible.value = true
  loadPanelText.value = t('清除 ') + cacheName + t(' 缓存...')
  try {
    const resp = await httpRequest(
      `/api/basedata/invalidateAll?cacheName=${encodeURIComponent(cacheName)}`,
      {
        method: 'POST',
        headers: { 'X-Admin-Token': adminToken.value },
      }
    )
    const data = resp.data as any
    const result = (data && data.data) || data || {}
    if (result.success) {
      alert(t('已清除 ') + cacheName + t(' 全部缓存'))
      await loadStats()
    } else {
      alert(t('清除失败: ') + (result.error || t('未知错误')))
    }
  } catch (e) {
    console.error('清除缓存失败', e)
    alert(t('清除缓存失败: ') + String(e))
  } finally {
    loadPanelVisible.value = false
  }
}

/** 发布新元数据：从 DB 重新加载 → 编译 → 原子切换 */
async function publishMetadata() {
  if (!confirm(t('确认从数据库重新加载元数据并发布？'))) return
  loadPanelVisible.value = true
  loadPanelText.value = t('发布元数据...')
  try {
    const resp = await httpRequest('/api/basedata/publish', {
      method: 'POST',
      headers: { 'X-Admin-Token': adminToken.value },
    })
    const data = resp.data as any
    const result = (data && data.data) || data || {}
    if (result.success) {
      alert(t('发布成功，当前版本: ') + result.version)
      await loadStats()
    } else {
      alert(t('发布失败: ') + (result.error || t('未知错误')))
    }
  } catch (e) {
    console.error('发布元数据失败', e)
  } finally {
    loadPanelVisible.value = false
  }
}

/** 回滚到上一个元数据版本 */
async function rollbackMetadata() {
  if (!confirm(t('确认回滚到上一个元数据版本？'))) return
  loadPanelVisible.value = true
  loadPanelText.value = t('回滚元数据...')
  try {
    const resp = await httpRequest('/api/basedata/rollback', {
      method: 'POST',
      headers: { 'X-Admin-Token': adminToken.value },
    })
    const data = resp.data as any
    const result = (data && data.data) || data || {}
    if (result.success) {
      alert(t('回滚成功，当前版本: ') + result.version)
      await loadStats()
    } else {
      alert(t('回滚失败: ') + (result.error || t('无历史版本')))
    }
  } catch (e) {
    console.error('回滚元数据失败', e)
  } finally {
    loadPanelVisible.value = false
  }
}

/** 查询测试：调 GET /api/basedata/get 查询单条数据 */
async function testQuery() {
  queryVisible.value = true
  try {
    const params = new URLSearchParams({
      cacheName: queryCacheName.value,
      id: queryId.value,
    })
    if (queryProjection.value) {
      params.append('projection', queryProjection.value)
    }
    const resp = await httpRequest(`/api/basedata/get?${params}`, {
      method: 'GET',
    })
    const data = resp.data as any
    queryResult.value = (data && data.data) || data || {}
  } catch (e) {
    console.error('查询失败', e)
    queryResult.value = { error: String(e) }
  } finally {
    queryVisible.value = false
  }
}

onMounted(() => {
  loadModule('cache')
  loadStats()
})
</script>

<template>
  <div class="cache-manage">
    <DxLoadPanel
      :visible="loadPanelVisible"
      :message="loadPanelText"
      :show-indicator="true"
      :close-on-outside-click="false"
    />
    <DxDataGrid
      class="cache-grid"
      :data-source="statsList"
      :hover-state-enabled="true"
      :show-borders="true"
      :show-column-lines="false"
      :show-row-lines="true"
      :paging="{ enabled: false }"
      height="60%"
      key-expr="cacheName"
    >
      <DxToolbar>
        <DxItem location="after">
          <template #default>
            <DxButton :text="t('刷新统计')" icon="refresh" @click="loadStats" />
          </template>
        </DxItem>
        <DxItem location="after">
          <template #default>
            <DxButton
              :text="t('发布元数据')"
              type="default"
              @click="publishMetadata"
            />
          </template>
        </DxItem>
        <DxItem location="after">
          <template #default>
            <DxButton
              :text="t('回滚元数据')"
              type="normal"
              @click="rollbackMetadata"
            />
          </template>
        </DxItem>
        <DxItem name="columnChooser" />
      </DxToolbar>

      <DxColumn data-field="cacheName" :caption="t('缓存名')" width="140" />
      <DxColumn data-field="tableName" :caption="t('表名')" width="150" />
      <DxColumn
        data-field="fieldCount"
        :caption="t('字段数')"
        width="80"
        alignment="center"
      />
      <DxColumn
        data-field="schemaVersion"
        :caption="t('Schema版本')"
        width="90"
        alignment="center"
      />
      <DxColumn
        data-field="l1HitRate"
        :caption="t('L1命中率')"
        width="100"
        alignment="center"
        :calculate-display-value="(e: EntityWithMetrics) => formatRate(e.l1HitRate)"
      />
      <DxColumn
        data-field="l1HitCount"
        :caption="t('L1命中')"
        width="80"
        alignment="center"
      />
      <DxColumn
        data-field="l1MissCount"
        :caption="t('L1未命中')"
        width="90"
        alignment="center"
      />
      <DxColumn
        data-field="l2HitRate"
        :caption="t('L2命中率')"
        width="100"
        alignment="center"
        :calculate-display-value="(e: EntityWithMetrics) => formatRate(e.l2HitRate)"
      />
      <DxColumn
        data-field="l2HitCount"
        :caption="t('L2命中')"
        width="80"
        alignment="center"
      />
      <DxColumn
        data-field="dbLoadCount"
        :caption="t('DB加载')"
        width="80"
        alignment="center"
      />
      <DxColumn
        data-field="avgDbLoadMillis"
        :caption="t('平均DB耗时(ms)')"
        width="120"
        alignment="center"
        :calculate-display-value="(e: EntityWithMetrics) => e.avgDbLoadMillis?.toFixed(2) || '-'"
      />
      <DxColumn
        data-field="invalidateCount"
        :caption="t('失效次数')"
        width="90"
        alignment="center"
      />
      <DxColumn
        :caption="t('操作')"
        width="100"
        alignment="center"
        cell-template="actionCell"
      />

      <template #actionCell="{ data }">
        <DxButton
          :text="t('清除')"
          icon="trash"
          type="danger"
          :element-attr="{ class: 'cache-action-btn' }"
          @click="evictCache(data.cacheName)"
        />
      </template>
    </DxDataGrid>

    <!-- 查询测试面板 -->
    <div class="query-panel">
      <div class="query-title">{{ t('查询测试') }}</div>
      <div class="query-form">
        <label>{{ t('缓存名:') }}</label>
        <input v-model="queryCacheName" placeholder="B_ITEM" />
        <label>ID:</label>
        <input v-model="queryId" placeholder="10001" />
        <label>{{ t('投影:') }}</label>
        <input v-model="queryProjection" placeholder="PDA" />
        <button @click="testQuery" :disabled="queryVisible">
          {{ t('查询') }}
        </button>
      </div>
      <div v-if="queryResult" class="query-result">
        <pre>{{ JSON.stringify(queryResult, null, 2) }}</pre>
      </div>
    </div>

    <!-- 管理授权面板 -->
    <div class="admin-panel">
      <div class="query-title">{{ t('管理授权') }}</div>
      <div class="query-form">
        <label>Admin Token:</label>
        <input
          v-model="adminToken"
          type="password"
          :placeholder="t('输入管理 Token')"
          style="width: 200px"
        />
        <button @click="saveAdminToken">{{ t('保存') }}</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cache-manage {
  height: 100%;
  width: 100%;
  padding: 8px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.cache-grid {
  flex: 1;
}

.cache-action-btn {
  margin: 0 2px;
}

.query-panel {
  height: 35%;
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow: auto;
}

.query-title {
  font-weight: bold;
  font-size: 14px;
  margin-bottom: 4px;
}

.query-form {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.query-form label {
  font-size: 13px;
}

.query-form input {
  width: 120px;
  padding: 4px 6px;
  border: 1px solid #ccc;
  border-radius: 3px;
  font-size: 13px;
}

.query-form button {
  padding: 4px 16px;
  background: #4a90d9;
  color: white;
  border: none;
  border-radius: 3px;
  cursor: pointer;
  font-size: 13px;
}

.query-form button:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.query-result {
  flex: 1;
  overflow: auto;
  background: #f5f5f5;
  border: 1px solid #eee;
  border-radius: 3px;
  padding: 8px;
  font-size: 13px;
  font-family: monospace;
}

.query-result pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.admin-panel {
  border: 1px solid #ddd;
  border-radius: 4px;
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
</style>
