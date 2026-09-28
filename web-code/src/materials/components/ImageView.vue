<script setup lang="ts">
withDefaults(
  defineProps<{
    src?: string
    alt?: string
    fit?: 'contain' | 'cover' | 'fill' | 'none' | 'scale-down'
    borderRadius?: number
    width?: number
    height?: number
  }>(),
  {
    src: "data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' width='320' height='160'%3E%3Crect width='100%25' height='100%25' fill='%23e8e8e8'/%3E%3Ctext x='50%25' y='50%25' text-anchor='middle' dominant-baseline='middle' font-size='16' fill='%23999'%3E图片%3C/text%3E%3C/svg%3E",
    alt: '图片',
    fit: 'cover',
    borderRadius: 0,
    width: 320,
    height: 160,
  },
)
</script>

<template>
  <img
    class="image-view"
    :src="src"
    :alt="alt"
    :style="{ width: width + 'px', height: height + 'px', objectFit: fit, borderRadius: borderRadius + 'px' }"
  />
</template>

<style scoped>
.image-view {
  display: block;
}
</style>
