<script setup lang="ts">
withDefaults(
  defineProps<{
    src?: string
    title?: string
    width?: number
    height?: number
    border?: boolean
  }>(),
  {
    src: 'https://vuejs.org',
    title: '内嵌网页',
    width: 400,
    height: 300,
    border: false,
  },
)
</script>

<template>
  <iframe
    class="iframe-view"
    :src="src"
    :title="title"
    :style="{ width: width + 'px', height: height + 'px', border: border ? '1px solid #d0d0d0' : 'none' }"
  />
</template>

<style scoped>
.iframe-view {
  display: block;
  box-sizing: border-box;
}
</style>
