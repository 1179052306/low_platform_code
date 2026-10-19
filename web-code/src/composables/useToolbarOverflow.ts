import { ref, watch, onUnmounted, nextTick, type Ref } from 'vue'

export function useToolbarOverflow(toolbarRef: Ref<HTMLElement | undefined>) {
  const overflowIndices = ref<Set<number>>(new Set())
  const btnWidths = new Map<number, number>()
  let ro: ResizeObserver | null = null
  let debounceTimer: number | null = null
  let mo: MutationObserver | null = null

  function calc() {
    const el = toolbarRef.value
    if (!el) return
    const buttons = Array.from(el.querySelectorAll<HTMLElement>('[data-toolbar-btn]'))
    if (!buttons.length) {
      overflowIndices.value = new Set()
      return
    }

    const toolbarWidth = el.clientWidth
    const moreBtn = el.querySelector<HTMLElement>('[data-more-btn]')
    const moreWidth = moreBtn ? moreBtn.offsetWidth + 8 : 0

    for (const btn of buttons) {
      const idx = Number(btn.getAttribute('data-toolbar-btn'))
      if (!btnWidths.has(idx)) {
        btnWidths.set(idx, btn.offsetWidth + 8)
      }
    }

    const sorted = buttons.map(btn => Number(btn.getAttribute('data-toolbar-btn'))).sort((a, b) => a - b)
    let total = 0
    const hidden = new Set<number>()
    for (const idx of sorted) {
      const w = btnWidths.get(idx) || 0
      total += w
    }
    if (total <= toolbarWidth) {
      overflowIndices.value = new Set()
      return
    }
    for (let i = sorted.length - 1; i >= 0; i--) {
      hidden.add(sorted[i])
      const remaining = sorted.slice(0, i).reduce((sum, idx) => sum + (btnWidths.get(idx) || 0), 0)
      if (remaining + moreWidth <= toolbarWidth) break
    }
    overflowIndices.value = hidden
  }

  function debouncedCalc() {
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = window.setTimeout(() => {
      btnWidths.clear()
      calc()
    }, 50)
  }

  function setupObservers() {
    const el = toolbarRef.value
    if (!el) return
    ro = new ResizeObserver(() => debouncedCalc())
    ro.observe(el)
    mo = new MutationObserver(() => debouncedCalc())
    mo.observe(el, { childList: true, subtree: true, attributes: true, attributeFilter: ['style', 'class', 'data-toolbar-btn'] })
  }

  function teardown() {
    if (ro) { ro.disconnect(); ro = null }
    if (mo) { mo.disconnect(); mo = null }
    if (debounceTimer) { clearTimeout(debounceTimer); debounceTimer = null }
  }

  watch(toolbarRef, (el) => {
    teardown()
    if (el) {
      nextTick(() => {
        setupObservers()
        calc()
      })
    }
  }, { immediate: true })

  onUnmounted(teardown)

  return { overflowIndices }
}