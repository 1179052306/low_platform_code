<script lang="ts">
import {
  defineComponent,
  computed,
  ref,
  watch,
  nextTick,
  onMounted,
  onBeforeUnmount,
} from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getSession, logout } from '@/store/auth'
import {
  t,
  currentLang,
  langList,
  langLoading,
  switchLang,
  loadModule,
} from '@/store/lang'
import GlobalWatermark from '@/components/GlobalWatermark.vue'
import SearchIcon from '@/components/SearchIcon.vue'
import ThemeSwitchDialog from '@/components/designer/ThemeSwitchDialog.vue'
import type { Theme } from '@/types/schema'
import {
  loadThemes,
  getDefaultThemeId,
  applyTheme,
} from '@/composables/useTheme'

/** 大面板功能项 */
interface PanelItem {
  title: string
  desc: string
  icon: string
  path: string
}
interface PanelGroup {
  title: string
  items: PanelItem[]
}
/** 侧边栏菜单：path 直跳；panel 点击弹出二级功能大面板 */
interface MenuItem {
  key: string
  title: string
  icon: string
  path?: string
  panel?: { groups: PanelGroup[] }
}

const MENUS: MenuItem[] = [
  { key: 'home', title: '首页', icon: '⌂', path: '/home' },
  {
    key: 'page-dev',
    title: '页面开发',
    icon: '▤',
    panel: {
      groups: [
        {
          title: '页面搭建',
          items: [
            {
              title: '页面管理',
              desc: '模块树管理页面，新建、编辑、复制与删除',
              icon: '▤',
              path: '/pages',
            },
            {
              title: '系统基础资料',
              desc: '系统增删改查，管理页面所属系统',
              icon: '⚙',
              path: '/system-manage',
            },

          ],
        },
      ],
    },
  },
  {
    key: 'system-config',
    title: '系统设置',
    icon: '⚙',
    panel: {
      groups: [
        {
          title: '外观与主题',
          items: [
            {
              title: '主题管理',
              desc: '管理页面主题配色，预置/自定义主题与批量应用',
              icon: '🎨',
              path: '/theme-manage',
            },
          ],
        },
        {
          title: '国际化',
          items: [
            {
              title: '系统翻译',
              desc: '系统固定页面多语言翻译管理',
              icon: '🌐',
              path: '/lang-manage',
            },
            {
              title: '页面翻译',
              desc: '设计器页面多语言翻译，按系统/模块/页面层级',
              icon: '🌐',
              path: '/page-lang-manage',
            },
            {
              title: '语言管理',
              desc: '管理语言列表，新增/编辑/删除语言',
              icon: '🌐',
              path: '/lang-list-manage',
            },
          ],
        },
      ],
    },
  },
  {
    key: 'service-gov',
    title: '服务治理',
    icon: '⛁',
    panel: {
      groups: [
        {
          title: '接口与缓存',
          items: [
            {
              title: 'API 服务管理',
              desc: '维护服务注册表，收口远程请求白名单',
              icon: '⛁',
              path: '/api-manage',
            },
            {
              title: '基础数据缓存',
              desc: '多级缓存管理，查看命中率/预热/清除',
              icon: '⛁',
              path: '/cache-manage',
            },
          ],
        },
      ],
    },
  },
]

/** 路由 → Tab 元数据（框架壳内可开 Tab 的路由） */
const ROUTE_TAB_META: Record<string, { title: string; componentName: string }> =
  {
    '/home': { title: '首页', componentName: 'WorkbenchView' },

    '/api-manage': { title: 'API 服务管理', componentName: 'ApiManageView' },
    '/system-manage': {
      title: '系统基础资料',
      componentName: 'SystemManageView',
    },
    '/cache-manage': {
      title: '基础数据缓存',
      componentName: 'CacheManageView',
    },
    '/lang-manage': {
      title: '系统翻译',
      componentName: 'LangManageView',
    },
    '/page-lang-manage': {
      title: '页面翻译',
      componentName: 'PageLangManageView',
    },
    '/lang-list-manage': {
      title: '语言管理',
      componentName: 'LangListManageView',
    },
    '/theme-manage': {
      title: '主题管理',
      componentName: 'ThemeManageView',
    },
  }

interface TabItem {
  path: string
  title: string
  componentName: string
  closable: boolean
}

const HOME_TAB: TabItem = {
  path: '/home',
  title: '首页',
  componentName: 'WorkbenchView',
  closable: false,
}

export default defineComponent({
  name: 'HomeView',
  components: { GlobalWatermark, SearchIcon, ThemeSwitchDialog },
  setup() {
    const router = useRouter()
    const route = useRoute()

    const session = getSession()
    const username = computed(() => session?.username ?? '未登录')

    // ================ 侧边栏 ================
    const collapsed = ref(false)
    /** 当前弹出大面板的菜单 key（单开互斥，'' 为全收起） */
    const openPanelKey = ref('')
    const panelKeyword = ref('')

    /** 当前路由归属的一级菜单 key（用于菜单高亮） */
    const activeMenuKey = computed(() => {
      for (const m of MENUS) {
        if (m.path === route.path) return m.key
        if (
          m.panel?.groups.some((g) =>
            g.items.some((i) => i.path === route.path)
          )
        )
          return m.key
      }
      return ''
    })

    const openedPanel = computed(
      () => MENUS.find((m) => m.key === openPanelKey.value)?.panel ?? null
    )

    /** 面板内按关键词过滤后的分组 */
    const filteredPanelGroups = computed<PanelGroup[]>(() => {
      const panel = openedPanel.value
      if (!panel) return []
      const kw = panelKeyword.value.trim().toLowerCase()
      if (!kw) return panel.groups
      const result: PanelGroup[] = []
      for (const g of panel.groups) {
        const items = g.items.filter(
          (i) =>
            i.title.toLowerCase().includes(kw) ||
            i.desc.toLowerCase().includes(kw)
        )
        if (items.length) result.push({ title: g.title, items })
      }
      return result
    })

    function onMenuClick(item: MenuItem): void {
      if (item.path) {
        // 直跳菜单：跳转并收起面板
        openPanelKey.value = ''
        panelKeyword.value = ''
        router.push(item.path)
        return
      }
      // 面板菜单：单开互斥，再次点击收起
      if (openPanelKey.value === item.key) {
        openPanelKey.value = ''
      } else {
        openPanelKey.value = item.key
        panelKeyword.value = ''
      }
    }

    function onPanelItemClick(item: PanelItem): void {
      closePanel()
      if (item.path === '/pages') {
        const url = `${window.location.origin}${window.location.pathname}#${item.path}`
        window.open(url, '_blank')
        return
      }
      router.push(item.path)
    }

    function closePanel(): void {
      openPanelKey.value = ''
      panelKeyword.value = ''
    }

    // ================ Tab 页签 ================
    const tabs = ref<TabItem[]>([{ ...HOME_TAB }])
    /** 刷新计数：变化时强制重建当前视图组件 */
    const viewKey = ref(0)
    /** 正在刷新的组件名：从 keep-alive include 中临时剔除以丢弃缓存 */
    const excludedName = ref('')

    const activePath = computed(() => route.path)

    /** keep-alive include：所有 tab 的组件名（剔除正在刷新的） */
    const includeNames = computed(() =>
      [...new Set(tabs.value.map((t) => t.componentName))].filter(
        (n) => n !== excludedName.value
      )
    )

    // 路由变化 → 自动新增/激活对应 tab（直接刷新浏览器也能恢复已开页签）
    watch(
      () => route.path,
      (p) => {
        const meta = ROUTE_TAB_META[p]
        if (!meta) return
        if (!tabs.value.some((t) => t.path === p)) {
          tabs.value.push({
            path: p,
            title: meta.title,
            componentName: meta.componentName,
            closable: true,
          })
        }
      },
      { immediate: true }
    )

    function closeTab(tab: TabItem): void {
      const idx = tabs.value.indexOf(tab)
      if (idx < 0) return
      const remaining = tabs.value.filter((t) => t !== tab)
      tabs.value = remaining
      // 关闭的是当前激活 tab → 激活相邻（右侧优先，否则左侧）
      if (route.path === tab.path) {
        const next =
          remaining[Math.min(idx, remaining.length - 1)] ?? remaining[0]
        if (next) router.push(next.path)
      }
    }

    function activateTab(tab: TabItem): void {
      if (route.path !== tab.path) router.push(tab.path)
    }

    // ---- Tab 右键菜单 ----
    const ctxMenu = ref<{
      visible: boolean
      x: number
      y: number
      tab: TabItem | null
    }>({ visible: false, x: 0, y: 0, tab: null })

    function openCtxMenu(e: MouseEvent, tab: TabItem): void {
      ctxMenu.value = { visible: true, x: e.clientX, y: e.clientY, tab }
    }

    function closeCtxMenu(): void {
      ctxMenu.value.visible = false
    }

    /** 刷新 tab：先激活，再从 keep-alive 缓存中剔除并强制重建 */
    async function refreshTab(tab: TabItem): Promise<void> {
      closeCtxMenu()
      if (route.path !== tab.path) router.push(tab.path)
      excludedName.value = tab.componentName
      await nextTick()
      viewKey.value += 1
      await nextTick()
      excludedName.value = ''
    }

    /** 按下标条件批量关闭 tab（关闭左侧/右侧/其他/所有） */
    function closeTabsByIndex(
      target: TabItem,
      keep: (i: number) => boolean
    ): void {
      const idx = tabs.value.indexOf(target)
      const remaining = tabs.value.filter((t, i) => keep(i) || !t.closable)
      tabs.value = remaining
      // 当前激活 tab 被关闭 → 激活剩余中靠近原位置的那个
      if (!remaining.some((t) => t.path === route.path)) {
        const fallback =
          remaining.slice(
            Math.max(0, Math.min(idx, remaining.length - 1))
          )[0] ?? HOME_TAB
        router.push(fallback.path)
      }
    }

    /** 全屏打开：对当前子页面区域做浏览器全屏（等同 F11，按 Esc 退出） */
    async function fullscreenTab(tab: TabItem): Promise<void> {
      closeCtxMenu()
      if (route.path !== tab.path) {
        router.push(tab.path)
        await nextTick()
      }
      const el = mainContentRef.value
      if (!el) return
      if (document.fullscreenElement) {
        await document.exitFullscreen()
      }
      await el.requestFullscreen()
    }

    function onCtxAction(action: string): void {
      const tab = ctxMenu.value.tab
      if (!tab) return
      closeCtxMenu()
      const idx = tabs.value.indexOf(tab)
      switch (action) {
        case 'refresh':
          void refreshTab(tab)
          break
        case 'close-left':
          closeTabsByIndex(tab, (i) => i >= idx)
          break
        case 'close-right':
          closeTabsByIndex(tab, (i) => i <= idx)
          break
        case 'close-other':
          closeTabsByIndex(tab, (i) => i === idx)
          break
        case 'close-all':
          closeTabsByIndex(tab, () => false)
          break
        case 'fullscreen':
          void fullscreenTab(tab)
          break
      }
    }

    // ================ 顶栏全局菜单搜索 ================
    const globalKeyword = ref('')
    const globalSearchOpen = ref(false)
    const globalSearchRef = ref<HTMLElement | null>(null)
    /** 搜索框展开态：默认只显示放大镜，点击/有值时展开输入框 */
    const searchExpanded = ref(false)
    const searchInputRef = ref<HTMLInputElement | null>(null)

    async function expandSearch(): Promise<void> {
      if (searchExpanded.value) return
      searchExpanded.value = true
      await nextTick()
      requestAnimationFrame(() => {
        searchInputRef.value?.focus()
      })
    }

    function onSearchBlur(): void {
      if (!globalKeyword.value) searchExpanded.value = false
    }

    interface MenuEntry {
      title: string
      desc: string
      icon: string
      path: string
      breadcrumb: string
    }
    const allMenuEntries = computed<MenuEntry[]>(() => {
      const list: MenuEntry[] = []
      for (const m of MENUS) {
        if (m.path) {
          list.push({
            title: m.title,
            desc: m.title,
            icon: m.icon,
            path: m.path,
            breadcrumb: m.title,
          })
        }
        if (m.panel) {
          for (const g of m.panel.groups) {
            for (const it of g.items) {
              list.push({
                title: it.title,
                desc: it.desc,
                icon: it.icon,
                path: it.path,
                breadcrumb: `${m.title} / ${g.title} / ${it.title}`,
              })
            }
          }
        }
      }
      return list
    })

    const globalSearchResults = computed<MenuEntry[]>(() => {
      const kw = globalKeyword.value.trim().toLowerCase()
      if (!kw) return []
      return allMenuEntries.value
        .filter(
          (e) =>
            e.title.toLowerCase().includes(kw) ||
            e.breadcrumb.toLowerCase().includes(kw) ||
            e.desc.toLowerCase().includes(kw)
        )
        .slice(0, 12)
    })

    function onGlobalSearchInput(): void {
      globalSearchOpen.value = globalKeyword.value.trim().length > 0
    }

    function clearGlobalSearch(): void {
      globalKeyword.value = ''
      globalSearchOpen.value = false
      searchExpanded.value = false
    }

    function onGlobalResultClick(entry: MenuEntry): void {
      router.push(entry.path)
      clearGlobalSearch()
    }

    // ================ 用户下拉 ================
    const userMenuOpen = ref(false)
    const userMenuRef = ref<HTMLElement | null>(null)
    /** 主内容区容器：Tab 页"全屏打开"的全屏目标 */
    const mainContentRef = ref<HTMLElement | null>(null)

    // ================ 语言切换 ================
    const langMenuOpen = ref(false)
    const langMenuRef = ref<HTMLElement | null>(null)

    function toggleLangMenu(): void {
      langMenuOpen.value = !langMenuOpen.value
    }

    async function onLangSelect(locale: string): Promise<void> {
      langMenuOpen.value = false
      await switchLang(locale)
      await loadModule('home', 'menu')
    }

    const currentLangName = computed(
      () =>
        langList.value.find((l) => l.locale === currentLang.value)?.name ??
        '简体中文'
    )

    const userActions = [
      { key: 'theme', title: '切换主题', icon: '🎨' },
      { key: 'password', title: '修改密码', icon: '🔑' },
      { key: 'online', title: '在线人数', icon: '👥', badge: '0' },
      { key: 'about', title: '关于平台', icon: 'ℹ' },
    ] as const

    function toggleUserMenu(): void {
      userMenuOpen.value = !userMenuOpen.value
    }

    function onUserAction(key: string): void {
      userMenuOpen.value = false
      if (key === 'theme') {
        themeSwitchVisible.value = true
        return
      }
      if (key === 'password') {
        alert(t('修改密码功能待接入'))
        return
      }
      if (key === 'online') {
        alert(t('当前在线人数：0'))
        return
      }
      if (key === 'about') {
        alert(t('低代码平台 演示环境\nVue3 + DevExtreme + vue-router'))
        return
      }
    }

    function onLogout(): void {
      if (!session) return
      const ok = confirm(t('确定退出登录吗？') + session.username)
      if (!ok) return
      logout()
      router.push('/login')
    }

    /** 全局点击：关闭用户下拉 / Tab 右键菜单 / 大面板 / 语言菜单 */
    function onDocClick(e: MouseEvent): void {
      const el = e.target as HTMLElement
      if (
        userMenuOpen.value &&
        userMenuRef.value &&
        !userMenuRef.value.contains(el)
      ) {
        userMenuOpen.value = false
      }
      if (
        langMenuOpen.value &&
        langMenuRef.value &&
        !langMenuRef.value.contains(el)
      ) {
        langMenuOpen.value = false
      }
      if (ctxMenu.value.visible) closeCtxMenu()
      if (
        globalSearchOpen.value &&
        globalSearchRef.value &&
        !globalSearchRef.value.contains(el)
      ) {
        globalSearchOpen.value = false
      }
      if (openPanelKey.value) {
        const inSidebar = !!el.closest('.side-menu')
        const inPanel = !!el.closest('.mega-panel')
        if (!inSidebar && !inPanel) closePanel()
      }
    }

    // ================ 全局主题 ================
    const themeSwitchVisible = ref(false)
    const homePageRef = ref<HTMLElement | null>(null)
    const currentThemeName = ref('')

    async function applyDefaultTheme(): Promise<void> {
      try {
        const themeId = await getDefaultThemeId()
        if (!themeId) return
        const list = await loadThemes()
        const th = list.find((tm) => tm.id === themeId)
        if (th && homePageRef.value) {
          applyTheme(homePageRef.value, th)
          currentThemeName.value = th.name
        }
      } catch {
        // ignore
      }
    }

    function onThemeSwitch(th: Theme): void {
      if (homePageRef.value) {
        applyTheme(homePageRef.value, th)
        currentThemeName.value = th.name
      }
    }

    onMounted(() => {
      document.addEventListener('click', onDocClick)
      loadModule('home', 'menu')
      applyDefaultTheme()
    })
    onBeforeUnmount(() => {
      document.removeEventListener('click', onDocClick)
    })

    return {
      username,
      collapsed,
      MENUS,
      openPanelKey,
      activeMenuKey,
      openedPanel,
      filteredPanelGroups,
      panelKeyword,
      onMenuClick,
      onPanelItemClick,
      tabs,
      activePath,
      includeNames,
      viewKey,
      closeTab,
      activateTab,
      refreshTab,
      ctxMenu,
      openCtxMenu,
      onCtxAction,
      userMenuOpen,
      userMenuRef,
      toggleUserMenu,
      onLogout,
      userActions,
      onUserAction,
      mainContentRef,
      globalKeyword,
      globalSearchOpen,
      globalSearchRef,
      globalSearchResults,
      onGlobalSearchInput,
      clearGlobalSearch,
      onGlobalResultClick,
      searchExpanded,
      searchInputRef,
      expandSearch,
      onSearchBlur,
      t,
      currentLang,
      langList,
      langLoading,
      langMenuOpen,
      langMenuRef,
      toggleLangMenu,
      onLangSelect,
      currentLangName,
      themeSwitchVisible,
      homePageRef,
      currentThemeName,
      onThemeSwitch,
    }
  },
})
</script>

<template>
  <div class="home-page" ref="homePageRef">
    <div v-if="langLoading" class="lang-loading-overlay">
      <div class="lang-loading-box">
        <div class="lang-loading-spinner"></div>
        <span class="lang-loading-text">{{ t('正在切换语言...') }}</span>
      </div>
    </div>
    <!-- 顶部栏 -->
    <header class="top-bar">
      <div class="top-left">
        <span class="logo-mark">◧</span>
        <span class="logo-text">{{ t('低代码平台') }}</span>
        <button
          class="collapse-btn"
          :title="collapsed ? t('展开菜单') : t('收起菜单')"
          @click="collapsed = !collapsed"
        >
          ☰
        </button>
      </div>
      <div class="top-notice">
        <span class="notice-icon">📣</span>
        <span class="notice-text">{{
          t('【测试环境】当前为低代码平台演示环境')
        }}</span>
      </div>
      <div class="top-right" ref="userMenuRef">
        <div
          class="top-search"
          :class="{ expanded: searchExpanded || !!globalKeyword }"
          ref="globalSearchRef"
        >
          <button
            class="top-search-icon"
            :title="t('搜索菜单')"
            @click.stop="expandSearch"
            @mousedown.prevent
          >
            <SearchIcon :size="16" />
          </button>
          <input
            ref="searchInputRef"
            class="top-search-input"
            v-model="globalKeyword"
            type="text"
            :placeholder="t('搜索菜单')"
            @input="onGlobalSearchInput"
            @blur="onSearchBlur"
            @click.stop
          />
          <span
            v-if="globalKeyword"
            class="top-search-clear"
            @click.stop="clearGlobalSearch"
            >×</span
          >
          <div
            v-if="globalKeyword && globalSearchResults.length"
            class="global-search-dropdown"
          >
            <button
              v-for="(entry, idx) in globalSearchResults"
              :key="idx"
              class="global-search-item"
              @click.stop="onGlobalResultClick(entry)"
            >
              <span class="global-search-icon">{{ entry.icon }}</span>
              <span class="global-search-main">
                <span class="global-search-title">{{ entry.title }}</span>
                <span class="global-search-path">{{ entry.breadcrumb }}</span>
              </span>
            </button>
          </div>
          <div
            v-if="globalKeyword && !globalSearchResults.length"
            class="global-search-dropdown"
          >
            <div class="global-search-empty">{{ t('未找到匹配的菜单') }}</div>
          </div>
        </div>

        <div class="lang-switcher" ref="langMenuRef">
          <button class="lang-chip" @click.stop="toggleLangMenu">
            <span class="lang-icon">🌐</span>
            <span class="lang-name">{{ currentLangName }}</span>
            <span class="lang-caret">▾</span>
          </button>
          <div v-if="langMenuOpen" class="lang-dropdown">
            <button
              v-for="lang in langList"
              :key="lang.locale"
              class="lang-item"
              :class="{ active: currentLang === lang.locale }"
              @click.stop="onLangSelect(lang.locale)"
            >
              <span class="lang-item-check">{{
                currentLang === lang.locale ? '✓' : ''
              }}</span>
              <span class="lang-item-name">{{ lang.name }}</span>
            </button>
          </div>
        </div>
        <button class="user-chip" @click.stop="toggleUserMenu">
          <span class="user-avatar">{{
            username.slice(0, 1).toUpperCase()
          }}</span>
          <span class="user-name">{{ username }}</span>
          <span class="user-caret">▾</span>
        </button>
        <div v-if="userMenuOpen" class="user-dropdown">
          <div class="user-card">
            <div class="user-card-avatar">
              {{ username.slice(0, 1).toUpperCase() }}
            </div>
            <div class="user-card-info">
              <div class="user-card-name">{{ username }}</div>
              <div class="user-card-role">{{ t('【系统管理员】') }}</div>
            </div>
          </div>
          <div class="user-card-tip">
            {{ t('欢迎使用低代码平台，祝您工作顺利。') }}
          </div>
          <div class="user-dropdown-divider"></div>
          <button
            v-for="act in userActions"
            :key="act.key"
            class="dropdown-item"
            @click.stop="onUserAction(act.key)"
          >
            <span class="dropdown-item-icon">{{ act.icon }}</span>
            <span class="dropdown-item-text">{{ t(act.title) }}</span>
            <span v-if="'badge' in act && act.badge" class="dropdown-item-badge"
              >[{{ act.badge }}]</span
            >
          </button>
          <div class="user-dropdown-divider"></div>
          <button class="dropdown-item logout" @click.stop="onLogout">
            <span class="dropdown-item-icon">⏻</span>
            <span class="dropdown-item-text">{{ t('退出登录') }}</span>
          </button>
        </div>
      </div>
    </header>

    <div class="home-layout">
      <!-- 左侧深色菜单 -->
      <aside class="side-menu" :class="{ collapsed }">
        <nav class="menu-list">
          <button
            v-for="item in MENUS"
            :key="item.key"
            class="menu-item"
            :class="{
              active: activeMenuKey === item.key,
              expanded: openPanelKey === item.key && !!item.panel,
            }"
            :title="collapsed ? t(item.title) : undefined"
            @click.stop="onMenuClick(item)"
          >
            <span class="menu-icon">{{ item.icon }}</span>
            <span v-if="!collapsed" class="menu-title">{{
              t(item.title)
            }}</span>
            <span
              v-if="!collapsed && item.panel"
              class="menu-arrow"
              :class="{ open: openPanelKey === item.key }"
              >▸</span
            >
          </button>
        </nav>
      </aside>

      <!-- 右侧：Tab 栏 + 主内容 -->
      <div class="main-wrap">
        <!-- Tab 页签栏 -->
        <div class="tab-bar">
          <button
            v-for="tab in tabs"
            :key="tab.path"
            class="tab-item"
            :class="{ active: activePath === tab.path }"
            @click="activateTab(tab)"
            @contextmenu.prevent="openCtxMenu($event, tab)"
          >
            <span class="tab-title">{{ t(tab.title) }}</span>
            <span
              class="tab-refresh"
              :title="t('刷新')"
              @click.stop="refreshTab(tab)"
              >↻</span
            >
            <span
              v-if="tab.closable"
              class="tab-close"
              :title="t('关闭')"
              @click.stop="closeTab(tab)"
              >×</span
            >
          </button>
        </div>

        <!-- Tab 右键菜单 -->
        <div
          v-if="ctxMenu.visible"
          class="ctx-menu"
          :style="{ left: ctxMenu.x + 'px', top: ctxMenu.y + 'px' }"
          @click.stop
        >
          <button class="ctx-item" @click="onCtxAction('refresh')">
            {{ t('刷新') }}
          </button>
          <button class="ctx-item" @click="onCtxAction('close-left')">
            {{ t('关闭左侧') }}
          </button>
          <button class="ctx-item" @click="onCtxAction('close-right')">
            {{ t('关闭右侧') }}
          </button>
          <button class="ctx-item" @click="onCtxAction('close-other')">
            {{ t('关闭其他') }}
          </button>
          <button class="ctx-item" @click="onCtxAction('close-all')">
            {{ t('关闭所有') }}
          </button>
          <button class="ctx-item" @click="onCtxAction('fullscreen')">
            {{ t('全屏打开') }}
          </button>
        </div>

        <!-- 主内容区：keep-alive 保持各 Tab 页面状态 -->
        <main class="main-content" ref="mainContentRef">
          <router-view v-slot="{ Component }">
            <keep-alive :include="includeNames">
              <component :is="Component" :key="activePath + '-' + viewKey" />
            </keep-alive>
          </router-view>
        </main>
      </div>
    </div>

    <!-- 二级功能大面板（贴侧边栏右侧，覆盖主内容区） -->
    <transition name="panel-slide">
      <div
        v-if="openedPanel"
        class="mega-panel"
        :style="{
          left: (collapsed ? 64 : 200) + 'px',
          width: 'min(680px, calc(100vw - ' + (collapsed ? 76 : 212) + 'px))',
        }"
      >
        <div class="panel-search">
          <span class="panel-search-icon"><SearchIcon :size="16" /></span>
          <input
            :value="panelKeyword"
            type="text"
            :placeholder="t('请输入菜单关键词')"
            @input="panelKeyword = ($event.target as HTMLInputElement).value"
          />
          <span
            v-if="panelKeyword"
            class="panel-search-clear"
            @click="panelKeyword = ''"
            >×</span
          >
        </div>
        <div
          v-if="panelKeyword && filteredPanelGroups.length"
          class="panel-search-count"
        >
          {{ t('共') }}
          {{ filteredPanelGroups.reduce((s, g) => s + g.items.length, 0) }}
          {{ t('个结果') }}
        </div>
        <div class="panel-body">
          <div
            v-for="group in filteredPanelGroups"
            :key="group.title"
            class="panel-group"
          >
            <div class="panel-group-title">{{ t(group.title) }}</div>
            <div class="panel-grid">
              <button
                v-for="gItem in group.items"
                :key="gItem.path"
                class="panel-item"
                :title="t(gItem.desc)"
                @click="onPanelItemClick(gItem)"
              >
                <span class="panel-item-icon">{{ gItem.icon }}</span>
                <span class="panel-item-title">{{ t(gItem.title) }}</span>
              </button>
            </div>
          </div>
          <div v-if="!filteredPanelGroups.length" class="panel-empty">
            {{ t('未找到匹配的菜单') }}
          </div>
        </div>
      </div>
    </transition>
    <GlobalWatermark />
    <ThemeSwitchDialog
      v-model:visible="themeSwitchVisible"
      @select="onThemeSwitch"
    />
  </div>
</template>

<style scoped>
.home-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--lc-page-bg, #f0f2f5);
  overflow: hidden;
}

/* ================ 顶部栏 ================ */
.top-bar {
  height: 52px;
  flex-shrink: 0;
  background: var(--lc-card-bg, #fff);
  border-bottom: 1px solid var(--lc-border, #e5e7eb);
  display: flex;
  align-items: center;
  padding: 0 16px;
  gap: 16px;
  position: relative;
  z-index: 40;
}
.top-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.logo-mark {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--lc-primary, #2563eb);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
}
.logo-text {
  font-size: 16px;
  font-weight: 600;
  color: var(--lc-text-primary, #1f2937);
  white-space: nowrap;
}
.collapse-btn {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 6px;
  font-size: 16px;
  color: #4b5563;
  cursor: pointer;
  transition: background 0.15s;
}
.collapse-btn:hover {
  background: #f3f4f6;
}
.top-notice {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  overflow: hidden;
}
.notice-icon {
  font-size: 15px;
}
.notice-text {
  font-size: 13px;
  color: #ff5722;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.top-right {
  position: relative;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}
.top-search {
  width: 36px;
  position: relative;
  display: flex;
  align-items: center;
  height: 34px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  padding: 0 6px;
  gap: 8px;
  transition: width 0.22s ease, border-color 0.15s, background 0.15s;
}
.top-search.expanded {
  width: 220px;
  border-color: #e5e7eb;
  background: #f9fafb;
}
.top-search.expanded:focus-within {
  border-color: var(--lc-primary, #1976d2);
  background: #fff;
}
.top-search-icon {
  font-size: 14px;
  color: #6b7280;
  flex-shrink: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 0;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  transition: background 0.15s, color 0.15s;
}
.top-search-icon:hover {
  background: #f3f4f6;
  color: var(--lc-primary, #1976d2);
}
.top-search-input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  outline: none;
  font-size: 13px;
  color: #374151;
  height: 100%;
  width: 0;
  opacity: 0;
  padding: 0;
  transition: opacity 0.18s ease;
}
.top-search.expanded .top-search-input {
  width: auto;
  opacity: 1;
  padding: 0 2px;
}
.top-search-input::placeholder {
  color: #9ca3af;
}
.top-search-clear {
  font-size: 14px;
  color: #9ca3af;
  cursor: pointer;
  flex-shrink: 0;
  line-height: 1;
}
.top-search-clear:hover {
  color: #dc2626;
}
.global-search-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(15, 40, 80, 0.14);
  padding: 6px;
  z-index: 60;
  max-height: 360px;
  overflow-y: auto;
}
.global-search-item {
  width: 100%;
  border: none;
  background: transparent;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  text-align: left;
}
.global-search-item:hover {
  background: #f0f7ff;
}
.global-search-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 13px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.global-search-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.global-search-title {
  font-size: 13px;
  color: #1f2937;
  font-weight: 500;
}
.global-search-path {
  font-size: 11px;
  color: #9ca3af;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.global-search-empty {
  text-align: center;
  font-size: 13px;
  color: #9ca3af;
  padding: 18px 0;
}
.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.15s;
}
.user-chip:hover {
  background: #f3f4f6;
}
.lang-switcher {
  position: relative;
  flex-shrink: 0;
}
.lang-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.15s;
}
.lang-chip:hover {
  background: #f3f4f6;
}
.lang-icon {
  font-size: 14px;
}
.lang-name {
  font-size: 13px;
  color: #374151;
  white-space: nowrap;
}
.lang-caret {
  font-size: 10px;
  color: #9ca3af;
}
.lang-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 160px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(15, 40, 80, 0.14);
  padding: 6px;
  z-index: 60;
}
.lang-item {
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  font-size: 13px;
  border-radius: 6px;
  padding: 8px 10px;
  cursor: pointer;
  color: #374151;
  display: flex;
  align-items: center;
  gap: 8px;
}
.lang-item:hover {
  background: #f0f7ff;
}
.lang-item.active {
  background: #eff6ff;
  color: var(--lc-primary, #2563eb);
  font-weight: 600;
}
.lang-item-check {
  width: 16px;
  color: var(--lc-primary, #2563eb);
  flex-shrink: 0;
}
.lang-item-name {
  flex: 1;
}
.user-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--lc-primary, #1976d2);
  color: #fff;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.user-name {
  font-size: 13px;
  color: #374151;
}
.user-caret {
  font-size: 10px;
  color: #9ca3af;
}
.user-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 220px;
  background: var(--lc-card-bg, #fff);
  border: 1px solid var(--lc-border, #e5e7eb);
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(15, 40, 80, 0.14);
  padding: 12px;
  z-index: 60;
}
.user-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 4px 10px;
}
.user-card-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: var(--lc-primary, #2563eb);
  color: #fff;
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.user-card-info {
  flex: 1;
  min-width: 0;
}
.user-card-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--lc-text-primary, #1f2937);
}
.user-card-role {
  font-size: 12px;
  color: #6b7280;
  margin-top: 2px;
}
.user-card-tip {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
  padding: 0 4px 8px;
}
.user-dropdown-divider {
  height: 1px;
  background: #f0f0f0;
  margin: 4px 0;
}
.dropdown-item {
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  font-size: 13px;
  border-radius: 6px;
  padding: 8px 10px;
  cursor: pointer;
  color: #374151;
  display: flex;
  align-items: center;
  gap: 10px;
}
.dropdown-item:hover {
  background: #f3f4f6;
}
.dropdown-item-icon {
  font-size: 14px;
  width: 18px;
  text-align: center;
  flex-shrink: 0;
}
.dropdown-item-text {
  flex: 1;
}
.dropdown-item-badge {
  font-size: 12px;
  color: #9ca3af;
}
.dropdown-item.logout {
  color: #dc2626;
}
.dropdown-item.logout:hover {
  background: #fef2f2;
}

/* ================ 布局 ================ */
.home-layout {
  flex: 1;
  display: flex;
  min-height: 0;
  position: relative;
}

/* 深色侧边菜单 */
.side-menu {
  width: 200px;
  flex-shrink: 0;
  background: #2b3a4a;
  overflow-y: auto;
  overflow-x: hidden;
  transition: width 0.2s ease;
  padding: 10px 0;
  position: relative;
  z-index: 30;
}
.side-menu.collapsed {
  width: 64px;
}
.menu-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0 8px;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: none;
  background: transparent;
  color: rgba(255, 255, 255, 0.78);
  font-size: 14px;
  padding: 12px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.menu-item:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}
.menu-item.active {
  background: var(--lc-primary, #1976d2);
  color: #fff;
}
.menu-item.expanded {
  background: rgba(25, 118, 210, 0.35);
  color: #fff;
}
.menu-icon {
  width: 22px;
  text-align: center;
  font-size: 15px;
  flex-shrink: 0;
}
.menu-title {
  flex: 1;
  text-align: left;
  white-space: nowrap;
}
.menu-arrow {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
  transition: transform 0.18s;
}
.menu-arrow.open {
  transform: rotate(90deg);
}

/* ================ 主内容区（Tab + 视图） ================ */
.main-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

/* Tab 页签栏 */
.tab-bar {
  flex-shrink: 0;
  background: var(--lc-card-bg, #fff);
  border-bottom: 1px solid var(--lc-border, #e5e7eb);
  display: flex;
  align-items: flex-end;
  padding: 6px 10px 0;
  gap: 4px;
  overflow-x: auto;
}
.tab-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid transparent;
  border-bottom: none;
  background: transparent;
  color: #6b7280;
  font-size: 13px;
  padding: 8px 12px;
  border-radius: 8px 8px 0 0;
  cursor: pointer;
  white-space: nowrap;
  position: relative;
  transition: color 0.15s, background 0.15s;
}
.tab-item:hover {
  color: var(--lc-primary, #1976d2);
  background: #f0f6fd;
}
.tab-item.active {
  color: var(--lc-primary, #1976d2);
  background: #f0f2f5;
  border-color: #e5e7eb;
  font-weight: 600;
}
.tab-item.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: var(--lc-primary, #1976d2);
}
.tab-refresh {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  line-height: 1;
  color: #9ca3af;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.tab-refresh:hover {
  background: #dbeafe;
  color: var(--lc-primary, #2563eb);
}
.tab-close {
  width: 16px;
  height: 16px;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  line-height: 1;
  color: #9ca3af;
  transition: background 0.15s, color 0.15s;
}
.tab-close:hover {
  background: #fecaca;
  color: #dc2626;
}

/* Tab 右键菜单 */
.ctx-menu {
  position: fixed;
  z-index: 100;
  min-width: 128px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 10px 30px rgba(15, 40, 80, 0.16);
  padding: 5px;
}
.ctx-item {
  display: block;
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  font-size: 13px;
  color: #374151;
  padding: 8px 12px;
  border-radius: 6px;
  cursor: pointer;
}
.ctx-item:hover {
  background: #f0f6fd;
  color: var(--lc-primary, #1976d2);
}

/* 主内容区 */
.main-content {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
/* Tab 全屏打开（F11 效果）：仅显示当前子页面内容，按 Esc 退出 */
.main-content:fullscreen {
  background: #fff;
  padding: 16px;
}

/* ================ 二级功能大面板 ================ */
.mega-panel {
  position: fixed;
  top: 60px;
  bottom: 12px;
  background: linear-gradient(160deg, #1e2f42 0%, #24384e 100%);
  border-radius: 10px;
  box-shadow: 0 14px 40px rgba(10, 25, 45, 0.35);
  z-index: 35;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}
.panel-slide-enter-from,
.panel-slide-leave-to {
  opacity: 0;
  transform: translateX(-10px);
}
.panel-search {
  padding: 14px 16px 8px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.panel-search-icon {
  display: flex;
  align-items: center;
  color: rgba(255, 255, 255, 0.5);
  flex-shrink: 0;
}
.panel-search input {
  flex: 1;
  min-width: 0;
  height: 34px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
  font-size: 13px;
  padding: 0 12px;
  outline: none;
  transition: border-color 0.15s, background 0.15s;
}
.panel-search input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}
.panel-search input:focus {
  border-color: rgba(100, 181, 246, 0.7);
  background: rgba(255, 255, 255, 0.12);
}
.panel-search-clear {
  font-size: 16px;
  color: rgba(255, 255, 255, 0.5);
  cursor: pointer;
  flex-shrink: 0;
  line-height: 1;
}
.panel-search-clear:hover {
  color: #fff;
}
.panel-search-count {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.45);
  padding: 0 16px 6px;
}
.panel-body {
  flex: 1;
  overflow-y: auto;
  padding: 6px 16px 16px;
}
.panel-group {
  margin-bottom: 14px;
}
.panel-group-title {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.45);
  padding: 8px 2px;
  letter-spacing: 1px;
}
.panel-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.panel-item {
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.05);
  border-radius: 9px;
  padding: 14px 8px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  transition: background 0.15s, border-color 0.15s, transform 0.12s;
}
.panel-item:hover {
  background: rgba(25, 118, 210, 0.32);
  border-color: rgba(100, 181, 246, 0.55);
  transform: translateY(-2px);
}
.panel-item-icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.1);
  color: #7dd3fc;
  font-size: 15px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.panel-item-title {
  font-size: 12.5px;
  color: rgba(255, 255, 255, 0.88);
  text-align: center;
}
.panel-empty {
  text-align: center;
  color: rgba(255, 255, 255, 0.4);
  font-size: 13px;
  padding: 40px 0;
}

/* ================ 响应式 ================ */
@media (max-width: 760px) {
  .top-notice {
    display: none;
  }
}

/* ================ 语言切换 loading ================ */
.lang-loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(255, 255, 255, 0.6);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
}
.lang-loading-box {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border: 1px solid #d1d5db;
  padding: 16px 32px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}
.lang-loading-spinner {
  width: 20px;
  height: 20px;
  border: 2px solid #e5e7eb;
  border-top-color: #2563eb;
  border-radius: 50%;
  animation: lang-spin 0.6s linear infinite;
}
@keyframes lang-spin {
  to {
    transform: rotate(360deg);
  }
}
.lang-loading-text {
  font-size: 14px;
  color: #374151;
}
</style>
