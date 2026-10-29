<template>
  <div class="toolbar-3d">
    <div class="toolbar-section">
      <el-select v-model="viewMode" size="small" style="width: 90px" @change="onViewModeChange">
        <el-option label="透视" value="perspective" />
        <el-option label="俯视" value="top" />
        <el-option label="侧视" value="side" />
        <el-option label="前视" value="front" />
      </el-select>
      <el-button size="small" @click="emit('fitToView')">适应</el-button>
    </div>

    <div class="toolbar-divider"></div>

    <template v-if="viewLevel === 'warehouse'">
      <div class="toolbar-section">
        <el-button size="small" type="warning" @click="emit('backToPark')">← 返回园区</el-button>
        <span class="toolbar-wh-name">{{ warehouseName }}</span>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-section">
        <span class="toolbar-label">楼层</span>
        <el-select
          :model-value="currentFloor"
          size="small"
          style="width: 70px"
          @change="onFloorChange"
        >
          <el-option
            v-for="f in floorOptions"
            :key="f"
            :label="f + 'F'"
            :value="f"
          />
        </el-select>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-section">
        <el-button
          size="small"
          :type="editMode ? 'primary' : 'default'"
          @click="emit('toggleEdit')"
        >{{ editMode ? '退出编辑' : '编辑' }}</el-button>
        <template v-if="editMode">
          <el-button size="small" type="success" @click="emit('addArea')">+ 库区</el-button>
          <el-button size="small" type="success" @click="emit('addRack')">+ 货架</el-button>
          <el-button size="small" type="success" @click="emit('genLocations')">生成库位</el-button>
        </template>
      </div>

      <div class="toolbar-divider"></div>

      <div class="toolbar-section">
        <el-checkbox v-model="showAreas" @change="emit('update:showAreas', showAreas)">库区</el-checkbox>
        <el-checkbox v-model="showChannels" @change="emit('update:showChannels', showChannels)">通道</el-checkbox>
        <el-checkbox v-model="showObstacles" @change="emit('update:showObstacles', showObstacles)">障碍</el-checkbox>
        <el-checkbox v-model="showGrid" @change="emit('update:showGrid', showGrid)">网格</el-checkbox>
        <el-checkbox v-model="showLabels" @change="emit('update:showLabels', showLabels)">标签</el-checkbox>
      </div>
    </template>

    <template v-else>
      <div class="toolbar-section">
        <span class="toolbar-label">园区总览 — 点击仓库建筑进入内部</span>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElSelect, ElOption, ElButton, ElCheckbox } from 'element-plus'
import type { ViewMode } from '@/types/warehouse-3d'

const props = defineProps<{
  editMode?: boolean
  currentFloor?: number
  floorCount?: number
  viewLevel?: string
  warehouseName?: string
}>()

const emit = defineEmits<{
  viewModeChange: [mode: ViewMode]
  fitToView: []
  toggleEdit: []
  addArea: []
  addRack: []
  genLocations: []
  floorChange: [floor: number]
  backToPark: []
  'update:showAreas': [val: boolean]
  'update:showChannels': [val: boolean]
  'update:showObstacles': [val: boolean]
  'update:showGrid': [val: boolean]
  'update:showLabels': [val: boolean]
}>()

const viewMode = ref<ViewMode>('perspective')
const showAreas = ref(true)
const showChannels = ref(true)
const showObstacles = ref(true)
const showGrid = ref(true)
const showLabels = ref(true)

const floorOptions = computed(() => {
  const count = props.floorCount || 1
  return Array.from({ length: count }, (_, i) => i + 1)
})

function onViewModeChange(val: ViewMode) {
  emit('viewModeChange', val)
}

function onFloorChange(val: number) {
  emit('floorChange', val)
}
</script>

<style scoped>
.toolbar-3d {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: linear-gradient(180deg, #ffffff 0%, #f5f7fa 100%);
  border-bottom: 1px solid #dcdfe6;
  pointer-events: auto;
  flex-wrap: wrap;
}
.toolbar-section {
  display: flex;
  align-items: center;
  gap: 6px;
}
.toolbar-divider {
  width: 1px;
  height: 20px;
  background: #dcdfe6;
  margin: 0 2px;
}
.toolbar-label {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
}
.toolbar-wh-name {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}
</style>