<script setup lang="ts">
import { computed } from 'vue'
import { ElCarousel, ElCarouselItem } from 'element-plus'

const props = defineProps<{
  dataSource?: any[]
  items?: any[]
  width?: number | string
  height?: number | string
  loop?: boolean
  showNavButtons?: boolean
  selectedIndex?: number
}>()

const emit = defineEmits<{
  selectionChanged: [e: { selectedIndex: number }]
}>()

const data = computed(() => props.dataSource ?? props.items ?? [])
const carouselHeight = computed(() => props.height ? (typeof props.height === 'number' ? `${props.height}px` : props.height) : '300px')
</script>

<template>
  <ElCarousel
    :height="carouselHeight"
    :loop="loop !== false"
    :arrow="showNavButtons !== false ? 'hover' : 'never'"
    :initial-index="selectedIndex ?? 0"
    @change="(i: number) => emit('selectionChanged', { selectedIndex: i })"
  >
    <ElCarouselItem
      v-for="(item, i) in data"
      :key="i"
    >
      <img
        v-if="typeof item === 'string' || item?.src"
        :src="typeof item === 'string' ? item : item.src"
        :alt="item?.alt"
        style="width: 100%; height: 100%; object-fit: cover;"
      >
      <div v-else>{{ item }}</div>
    </ElCarouselItem>
  </ElCarousel>
</template>