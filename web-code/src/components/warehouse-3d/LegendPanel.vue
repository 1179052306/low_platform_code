<template>
  <div class="legend-panel">
    <div class="legend-title">库位状态</div>
    <div v-for="item in items" :key="item.label" class="legend-item">
      <span class="legend-color" :style="{ background: item.color }" />
      <span class="legend-label">{{ item.label }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
const items = [
  { label: '空库位', color: '#3a3d44' },
  { label: '部分占用', color: '#66bb6a' },
  { label: '满库位', color: '#2e7d32' },
  { label: '锁定', color: '#ff9800' },
  { label: '预留', color: '#42a5f5' },
  { label: '禁用', color: '#616161' },
  { label: '异常', color: '#ef5350' },
]
</script>

<style scoped>
.legend-panel {
  position: absolute;
  bottom: 10px;
  left: 10px;
  background: rgba(26, 29, 36, 0.9);
  border: 1px solid rgba(100, 116, 139, 0.3);
  border-radius: 4px;
  padding: 8px 12px;
  pointer-events: auto;
  backdrop-filter: blur(8px);
}
.legend-title {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 6px;
  color: #e8edf2;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  line-height: 22px;
  color: #b0b8c4;
}
.legend-color {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 2px;
  border: 1px solid rgba(255, 255, 255, 0.15);
}
</style>