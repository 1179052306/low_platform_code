<template>
  <div class="warehouse-3d-view">
    <div class="w3d-top-bar">
      <Toolbar3D
        :edit-mode="editMode"
        :current-floor="currentFloor"
        :floor-count="floorCount"
        :view-level="viewLevel"
        :warehouse-name="currentWhName"
        @view-mode-change="setViewMode"
        @fit-to-view="fitToView"
        @toggle-edit="toggleEditMode"
        @add-area="showAddAreaDialog = true"
        @add-rack="showAddRackDialog = true"
        @gen-locations="showGenLocDialog = true"
        @floor-change="setFloor"
        @back-to-park="exitToPark"
        @update:show-areas="showAreas = $event"
        @update:show-channels="showChannels = $event"
        @update:show-obstacles="showObstacles = $event"
        @update:show-grid="showGrid = $event"
        @update:show-labels="showLabels = $event"
      />
    </div>

    <el-splitter class="w3d-splitter" direction="horizontal">
      <el-splitter-panel :size="75" :min="50">
        <div class="w3d-canvas-area">
          <SceneContainer
            ref="sceneRef"
            :warehouse="viewLevel === 'warehouse' ? warehouse : null"
            :park="viewLevel === 'park' ? park : null"
            :view-mode="viewMode"
            :edit-mode="editMode"
            :show-areas="showAreas"
            :show-channels="showChannels"
            :show-obstacles="showObstacles"
            :show-grid="showGrid"
            :show-labels="showLabels"
            @location-click="onLocationClick"
            @warehouse-click="onWarehouseClick"
            @fps-update="fps = $event"
          />
          <LegendPanel v-if="viewLevel === 'warehouse'" />
          <div class="w3d-status-bar">
            <span>FPS: {{ fps }}</span>
            <span v-if="viewLevel === 'park'">园区模式</span>
            <template v-else>
              <span>楼层: {{ currentFloor }}F / {{ floorCount }}F</span>
              <span>库位: {{ locationCount }}</span>
            </template>
            <span v-if="selectedInfo">选中: {{ selectedInfo.code }}</span>
            <span :class="['w3d-edit-tag', { active: editMode }]">{{ editMode ? '编辑中' : '只读' }}</span>
          </div>
        </div>
      </el-splitter-panel>
      <el-splitter-panel :size="25" :min="200">
        <div class="w3d-side-panel">
          <template v-if="viewLevel === 'park'">
            <div class="park-panel">
              <div class="park-title">{{ park?.name || '园区' }}</div>
              <div class="park-subtitle">{{ park?.code }} | {{ park?.warehouses.length || 0 }} 个仓库</div>
              <div
                v-for="wh in park?.warehouses || []"
                :key="wh.id"
                class="wh-card"
                @click="enterWarehouse(wh.id)"
              >
                <div class="wh-card-header">
                  <span class="wh-card-name">{{ wh.name }}</span>
                  <span class="wh-card-code">{{ wh.code }}</span>
                </div>
                <div class="wh-card-meta">
                  <span>{{ wh.floorCount }}F</span>
                  <span>{{ countWhLocations(wh) }} 库位</span>
                  <span>{{ wh.length }}×{{ wh.width }}m</span>
                </div>
                <div class="wh-card-hint">点击进入 →</div>
              </div>
            </div>
          </template>
          <template v-else>
            <el-tabs v-model="activeTab">
              <el-tab-pane label="库位详情" name="detail">
                <div v-if="selectedInfo" class="loc-detail">
                  <p><b>库位编码：</b>{{ selectedInfo.code }}</p>
                  <p><b>库位ID：</b>{{ selectedInfo.id }}</p>
                </div>
                <div v-else class="loc-empty">点击库位查看详情</div>
              </el-tab-pane>
              <el-tab-pane label="仓库信息" name="info">
                <div v-if="currentWh" class="wh-info">
                  <p><b>仓库名：</b>{{ currentWh.name }}</p>
                  <p><b>编码：</b>{{ currentWh.code }}</p>
                  <p><b>尺寸：</b>{{ currentWh.length }} × {{ currentWh.width }} × {{ currentWh.height }} m</p>
                  <p><b>楼层数：</b>{{ floorCount }}</p>
                  <p><b>当前楼层库区：</b>{{ currentFloorAreas.length }}</p>
                  <p><b>当前楼层库位：</b>{{ locationCount }}</p>
                </div>
              </el-tab-pane>
              <el-tab-pane label="库区列表" name="areas">
                <div v-if="editMode" style="margin-bottom: 8px">
                  <el-button size="small" type="primary" @click="showAddAreaDialog = true">新增库区</el-button>
                </div>
                <div v-for="area in currentFloorAreas" :key="area.id" class="area-item">
                  <div class="area-header">
                    <span class="area-code">{{ area.code }}</span>
                    <span class="area-name">{{ area.name }}</span>
                    <span class="area-type">{{ areaTypeLabel(area.areaType) }}</span>
                  </div>
                  <div class="area-meta">
                    货架 {{ area.racks.length }} 个 | 库位 {{ totalLocationsInArea(area) }} 个
                  </div>
                  <div v-if="editMode" class="area-actions">
                    <el-button size="small" text @click="startAddRack(area)">添加货架</el-button>
                  </div>
                </div>
              </el-tab-pane>
            </el-tabs>
          </template>
        </div>
      </el-splitter-panel>
    </el-splitter>

    <el-dialog v-model="showAddAreaDialog" title="新增库区" width="420px" :close-on-click-modal="false">
      <el-form label-width="80px" size="small">
        <el-form-item label="编码"><el-input v-model="newArea.code" placeholder="如 F1-E" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="newArea.name" placeholder="如 E区" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="newArea.areaType" style="width: 100%">
            <el-option label="存储区" value="storage" />
            <el-option label="拣货区" value="picking" />
            <el-option label="暂存区" value="staging" />
            <el-option label="收货区" value="receiving" />
            <el-option label="发货区" value="shipping" />
          </el-select>
        </el-form-item>
        <el-form-item label="X坐标"><el-input-number v-model="newArea.x" :min="0" :max="100" :precision="1" /></el-form-item>
        <el-form-item label="Z坐标"><el-input-number v-model="newArea.z" :min="0" :max="60" :precision="1" /></el-form-item>
        <el-form-item label="长度"><el-input-number v-model="newArea.length" :min="5" :max="50" :precision="1" /></el-form-item>
        <el-form-item label="宽度"><el-input-number v-model="newArea.width" :min="5" :max="30" :precision="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddAreaDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmAddArea">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showAddRackDialog" title="新增货架" width="420px" :close-on-click-modal="false">
      <el-form label-width="80px" size="small">
        <el-form-item label="所属库区">
          <el-select v-model="newRack.areaId" style="width: 100%" placeholder="选择库区">
            <el-option v-for="area in currentFloorAreas" :key="area.id" :label="area.code + ' ' + area.name" :value="area.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="编码"><el-input v-model="newRack.code" placeholder="如 R-E01" /></el-form-item>
        <el-form-item label="X坐标"><el-input-number v-model="newRack.x" :min="0" :max="100" :precision="1" /></el-form-item>
        <el-form-item label="Z坐标"><el-input-number v-model="newRack.z" :min="0" :max="60" :precision="1" /></el-form-item>
        <el-form-item label="层数"><el-input-number v-model="newRack.levels" :min="1" :max="10" /></el-form-item>
        <el-form-item label="每层格数"><el-input-number v-model="newRack.baysPerLevel" :min="1" :max="30" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddRackDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmAddRack">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showGenLocDialog" title="批量生成库位" width="380px" :close-on-click-modal="false">
      <el-form label-width="80px" size="small">
        <el-form-item label="选择货架">
          <el-select v-model="genLocRackId" style="width: 100%" placeholder="选择货架">
            <el-option v-for="rack in allRacks" :key="rack.id" :label="rack.code" :value="rack.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <div v-if="genLocResult" class="gen-loc-result">{{ genLocResult }}</div>
      <template #footer>
        <el-button @click="showGenLocDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmGenLocations">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import {
  ElSplitter, ElSplitterPanel, ElTabs, ElTabPane,
  ElDialog, ElForm, ElFormItem, ElInput, ElInputNumber, ElSelect, ElOption, ElButton,
  ElMessage,
} from 'element-plus'
import SceneContainer from '@/components/warehouse-3d/SceneContainer.vue'
import Toolbar3D from '@/components/warehouse-3d/Toolbar3D.vue'
import LegendPanel from '@/components/warehouse-3d/LegendPanel.vue'
import type { Warehouse, WarehouseArea, AreaType, Rack } from '@/types/warehouse-3d'
import {
  park, warehouse, viewLevel, viewMode, editMode, showAreas, showChannels, showObstacles, showGrid, showLabels,
  fps, locationCount, selectedInfo, wsLocationUpdates,
  currentFloor, floorCount,
  loadMockData, setViewMode, toggleEditMode, setSelectedLocation, stopMockWs, setFloor,
  addArea, addRack, generateLocations, getCurrentFloorAreas, enterWarehouse, exitToPark, getCurrentWarehouse,
} from '@/store/warehouse3d'

const sceneRef = ref<InstanceType<typeof SceneContainer>>()
const activeTab = ref('detail')

const showAddAreaDialog = ref(false)
const showAddRackDialog = ref(false)
const showGenLocDialog = ref(false)

const newArea = ref({
  code: '', name: '', areaType: 'storage' as AreaType,
  x: 10, z: 40, length: 15, width: 10,
})
const newRack = ref({
  areaId: '', code: '', x: 10, z: 10, levels: 3, baysPerLevel: 10,
})
const genLocRackId = ref('')
const genLocResult = ref('')

const currentWh = computed<Warehouse | null>(() => getCurrentWarehouse())
const currentWhName = computed(() => currentWh.value?.name || '')
const currentFloorAreas = computed<WarehouseArea[]>(() => getCurrentFloorAreas())
const allRacks = computed<Rack[]>(() => {
  const racks: Rack[] = []
  for (const area of currentFloorAreas.value) {
    racks.push(...area.racks)
  }
  return racks
})

onMounted(() => { loadMockData() })
onBeforeUnmount(() => { stopMockWs() })

function onLocationClick(id: string, code: string) {
  setSelectedLocation(id, code)
  activeTab.value = 'detail'
}
function onWarehouseClick(whId: string) {
  enterWarehouse(whId)
}
function fitToView() { sceneRef.value?.fitToView() }

function areaTypeLabel(t: string): string {
  const map: Record<string, string> = { storage: '存储', picking: '拣货', staging: '暂存', receiving: '收货', shipping: '发货' }
  return map[t] || t
}
function totalLocationsInArea(area: WarehouseArea): number {
  return area.racks.reduce((sum, r) => sum + r.locations.length, 0)
}
function countWhLocations(wh: Warehouse): number {
  let n = 0
  for (const area of wh.areas) {
    for (const rack of area.racks) {
      n += rack.locations.length
    }
  }
  return n
}

function confirmAddArea() {
  if (!newArea.value.code || !newArea.value.name) {
    ElMessage.warning('请填写编码和名称')
    return
  }
  const area = addArea(newArea.value)
  if (area) {
    ElMessage.success(`库区 ${area.code} 创建成功`)
    showAddAreaDialog.value = false
    newArea.value = { code: '', name: '', areaType: 'storage', x: 10, z: 40, length: 15, width: 10 }
    activeTab.value = 'areas'
  }
}

function startAddRack(area: WarehouseArea) {
  newRack.value.areaId = area.id
  newRack.value.code = `R-${area.code}-01`
  newRack.value.x = area.x + 1
  newRack.value.z = area.z + 1
  showAddRackDialog.value = true
}

function confirmAddRack() {
  if (!newRack.value.areaId || !newRack.value.code) {
    ElMessage.warning('请选择库区并填写编码')
    return
  }
  const len = newRack.value.baysPerLevel * 1.8
  const rack = addRack({
    areaId: newRack.value.areaId,
    code: newRack.value.code,
    x: newRack.value.x,
    z: newRack.value.z,
    length: len,
    width: 1.0,
    levels: newRack.value.levels,
    baysPerLevel: newRack.value.baysPerLevel,
  })
  if (rack) {
    const count = generateLocations(rack.id)
    ElMessage.success(`货架 ${rack.code} 创建成功，生成 ${count} 个库位`)
    showAddRackDialog.value = false
    newRack.value = { areaId: '', code: '', x: 10, z: 10, levels: 3, baysPerLevel: 10 }
  }
}

function confirmGenLocations() {
  if (!genLocRackId.value) {
    ElMessage.warning('请选择货架')
    return
  }
  const count = generateLocations(genLocRackId.value)
  genLocResult.value = `已生成 ${count} 个库位`
  ElMessage.success(`已生成 ${count} 个库位`)
}

watch(wsLocationUpdates, (updates) => {
  if (updates.length > 0) {
    sceneRef.value?.updateLocationStatus(updates)
  }
})
</script>

<style scoped>
.warehouse-3d-view { display: flex; flex-direction: column; height: 100%; width: 100%; background: #1a1d24; }
.w3d-top-bar { flex-shrink: 0; }
.w3d-splitter { flex: 1; min-height: 0; }
.w3d-canvas-area { position: relative; width: 100%; height: 100%; }
.w3d-status-bar {
  position: absolute; bottom: 0; right: 0;
  display: flex; gap: 16px; padding: 4px 12px;
  background: rgba(26, 29, 36, 0.9);
  border-top: 1px solid rgba(100, 116, 139, 0.2); border-left: 1px solid rgba(100, 116, 139, 0.2);
  font-size: 12px; color: #b0b8c4; pointer-events: auto;
  backdrop-filter: blur(8px);
}
.w3d-edit-tag { color: #78909c; }
.w3d-edit-tag.active { color: #ff9800; font-weight: 600; }
.w3d-side-panel { height: 100%; overflow: auto; padding: 0; background: #252830; }
.w3d-side-panel :deep(.el-tabs__header) { margin: 0; }
.w3d-side-panel :deep(.el-tabs__nav-wrap) { background: #1a1d24; padding: 0 8px; }
.w3d-side-panel :deep(.el-tabs__item) { color: #78909c; font-size: 13px; }
.w3d-side-panel :deep(.el-tabs__item.is-active) { color: #e8edf2; }
.w3d-side-panel :deep(.el-tabs__active-bar) { background: #4a90d9; }
.w3d-side-panel :deep(.el-tabs__content) { padding: 12px; }
.loc-detail p, .wh-info p { margin: 8px 0; font-size: 13px; color: #b0b8c4; }
.loc-detail b, .wh-info b { color: #e8edf2; }
.loc-empty { color: #78909c; font-size: 13px; text-align: center; margin-top: 30px; }
.area-item {
  padding: 10px; border: 1px solid rgba(100, 116, 139, 0.15); margin-bottom: 8px;
  background: rgba(26, 29, 36, 0.5); border-radius: 4px; transition: border-color 0.2s;
}
.area-item:hover { border-color: rgba(74, 144, 217, 0.4); }
.area-header { display: flex; align-items: center; gap: 8px; }
.area-code { font-weight: 600; color: #e8edf2; font-size: 13px; }
.area-name { color: #b0b8c4; font-size: 13px; }
.area-type { margin-left: auto; color: #78909c; font-size: 11px; padding: 2px 6px; background: rgba(100, 116, 139, 0.1); border-radius: 3px; }
.area-meta { font-size: 12px; color: #78909c; margin-top: 6px; }
.area-actions { margin-top: 6px; }
.gen-loc-result { color: #66bb6a; font-size: 13px; margin-top: 8px; }
.park-panel { padding: 16px; }
.park-title { font-size: 16px; font-weight: 600; color: #e8edf2; margin-bottom: 4px; }
.park-subtitle { font-size: 12px; color: #78909c; margin-bottom: 16px; }
.wh-card {
  padding: 14px; border: 1px solid rgba(100, 116, 139, 0.2); margin-bottom: 10px;
  background: rgba(26, 29, 36, 0.6); border-radius: 6px; cursor: pointer; transition: all 0.2s;
}
.wh-card:hover { border-color: #4a90d9; background: rgba(74, 144, 217, 0.08); transform: translateX(2px); }
.wh-card-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.wh-card-name { font-size: 14px; font-weight: 600; color: #e8edf2; }
.wh-card-code { font-size: 12px; color: #78909c; }
.wh-card-meta { display: flex; gap: 12px; font-size: 12px; color: #b0b8c4; }
.wh-card-hint { font-size: 11px; color: #4a90d9; margin-top: 6px; text-align: right; }
</style>