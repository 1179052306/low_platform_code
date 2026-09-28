<script setup lang="ts">
withDefaults(
  defineProps<{
    text?: string
    href?: string
    target?: '_blank' | '_self'
    underline?: boolean
    color?: string
    fontSize?: number
  }>(),
  {
    text: '链接',
    href: 'https://cn.vuejs.org',
    target: '_blank',
    underline: false,
    color: '#337ab7',
    fontSize: 14,
  },
)
</script>

<template>
  <a
    class="link-view"
    :href="href"
    :target="target"
    :style="{ color, fontSize: fontSize + 'px', textDecoration: underline ? 'underline' : 'none' }"
  >
    {{ text }}
  </a>
</template>

<style scoped>
.link-view {
  cursor: pointer;
}
.link-view:hover {
  text-decoration: underline;
}
</style>
