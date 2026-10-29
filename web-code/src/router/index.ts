import { createRouter, createWebHashHistory } from 'vue-router'
import { isLoggedIn, loadMenus, loadPermissions, permCodes } from '@/store/auth'
import PreviewView from '@/views/PreviewView.vue'

/** 动态注册的视图组件映射（相对路径 glob——alias 写法在部分 vite 版本不展开导致 loader 全空） */
const viewModules = import.meta.glob('../views/**/*.vue')

/** component 字符串 → [loader, 组件名(文件名，供 keep-alive/tab)] */
function resolveView(comp: string): { loader: () => Promise<unknown>; componentName: string } | null {
  const viewKey = comp.startsWith('vue:') ? comp.slice(4) : comp
  if (!viewKey) return null
  const loader = viewModules[`../views/${viewKey}.vue`] as (() => Promise<unknown>) | undefined
  if (!loader) {
    console.warn('[router] 动态菜单组件未命中 viewModules：', viewKey)
    return null
  }
  const componentName = viewKey.split('/').pop() || viewKey
  return { loader, componentName }
}

/** 静态路由保留名单（动态菜单 route_path 不得与之冲突） */
const RESERVED_PATHS = new Set([
  '/login', '/home', '/preview', '/pages', '/designer', '/',
  '/api-manage', '/system-manage', '/cache-manage', '/lang-manage',

  '/page-lang-manage', '/lang-list-manage', '/theme-manage', '/meta-manage',
  '/biz-meta', '/data-dict', '/package-manage', '/workflow-designer',
  '/biz-flow-designer', '/todo',
])

interface MenuNode {
  id?: string
  menuType?: string
  menuName?: string
  nameKey?: string
  menuCode?: string
  routePath?: string
  component?: string
  children?: MenuNode[]
}

/** 已注册动态路由标记（防重复 addRoute） */
let dynamicRoutesAdded = false

/**
 * 菜单树 → 动态路由（component 双模式）：
 * - vue:system/UserManageView → viewModules 动态导入（裸路径默认 vue:）
 * - page:{pageId} → 低代码配置页面，跳现有渲染路由 /preview?pageId=xxx
 */
export function registerDynamicRoutes(menuTreeData: MenuNode[]): void {
  const flat: MenuNode[] = []
  const walk = (nodes: MenuNode[]): void => {
    for (const n of nodes || []) {
      if (n.menuType === 'menu') flat.push(n)
      if (n.children?.length) walk(n.children)
    }
  }
  walk(menuTreeData || [])
  for (const m of flat) {
    const path = m.routePath?.trim()
    if (!path || RESERVED_PATHS.has(path)) continue
    const comp = (m.component || '').trim()
    if (comp.startsWith('page:')) {
      // 低代码配置页面：ConfigPageView 作为真实路由在 HomeView 内容区（Tab）渲染，不整页跳转
      router.addRoute('home', {
        path,
        name: 'dyn-' + (m.menuCode || path).replace(/[^\w]/g, '_'),
        component: PreviewView,
        meta: { title: m.menuName, dynamic: true, componentName: 'PreviewView', pageId: comp.slice(5) },
      })
      continue
    }
    const view = resolveView(comp)
    if (!view) continue
    router.addRoute('home', {
      path,
      name: 'dyn-' + (m.menuCode || path).replace(/[^\w]/g, '_'),
      component: view.loader as never,
      meta: { title: m.menuName, dynamic: true, componentName: view.componentName },
    })
  }
  dynamicRoutesAdded = true
}

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
    },
    {
      // 布局壳：顶栏 + 侧边菜单 + Tab 页签 + 主内容区
      path: '/home',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      children: [
        {
          // 工作台（首页 tab 的内容）
          path: '',
          name: 'workbench',
          component: () => import('@/views/WorkbenchView.vue'),
        },
        {

          path: '/api-manage',
          name: 'api-manage',
          component: () => import('@/views/ApiManageView.vue'),
        },
        {
          path: '/system-manage',
          name: 'system-manage',
          component: () => import('@/views/SystemManageView.vue'),
        },
        {

          path: '/cache-manage',
          name: 'cache-manage',
          component: () => import('@/views/CacheManageView.vue'),
        },
        {
          path: '/lang-manage',
          name: 'lang-manage',
          component: () => import('@/views/LangManageView.vue'),
        },
        {
          path: '/page-lang-manage',
          name: 'page-lang-manage',
          component: () => import('@/views/PageLangManageView.vue'),
        },
        {
          path: '/lang-list-manage',
          name: 'lang-list-manage',
          component: () => import('@/views/LangListManageView.vue'),
        },
        {
          path: '/theme-manage',
          name: 'theme-manage',
          component: () => import('@/views/ThemeManageView.vue'),
        },
        {
          // 元数据管理（方向二：统一元数据驱动）
          path: '/meta-manage',
          name: 'meta-manage',
          component: () => import('@/views/MetaManageView.vue'),
        },
        {
          // 业务元数据配置（业务对象/字段/动作/状态机）
          path: '/biz-meta',
          name: 'biz-meta',
          component: () => import('@/views/BizMetaView.vue'),
        },
        {
          // 数据字典管理（FE-S10 P2）
          path: '/data-dict',
          name: 'data-dict',
          component: () => import('@/views/DataDictView.vue'),
        },
        {
          // 应用包管理（FE-S5）
          path: '/package-manage',
          name: 'package-manage',
          component: () => import('@/views/PackageManagerView.vue'),
        },
        {
          // 流程设计器（FE-S6）
          path: '/workflow-designer',
          name: 'workflow-designer',
          component: () => import('@/views/WorkflowDesignerView.vue'),
        },
        {
          // 业务流设计器
          path: '/biz-flow-designer',
          name: 'biz-flow-designer',
          component: () => import('@/views/WorkflowDesignerView.vue'),
        },
        {
          // 待办列表（FE-S6）
          path: '/todo',
          name: 'todo',
          component: () => import('@/views/TodoView.vue'),
        },
      ],
    },
    {
      path: '/',
      name: 'root',
      redirect: { name: 'home' },
    },
    {
      // 预览页为全屏独立页（面向最终浏览），不进框架壳
      path: '/preview',
      name: 'preview',
      component: () => import('@/views/PreviewView.vue'),
    },
    {
      // 页面管理为全屏独立页（在新标签页打开），不进框架壳
      path: '/pages',
      name: 'page-manage',
      component: () => import('@/views/PageManageView.vue'),
    },
    {

      // 可视化设计器为全屏独立页（在新标签页打开），不进框架壳
      path: '/designer',
      name: 'designer',
      component: () => import('@/views/DesignerView.vue'),
    },
  ],
})

/** 登录守卫：未登录重定向登录页；已登录首跳时拉菜单并注册动态路由 */
router.beforeEach(async (to) => {
  const authed = isLoggedIn()
  if (to.name === 'login') {
    return authed ? { name: 'home' } : true
  }
  if (!authed) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  // 已登录且动态路由未注册：拉菜单/权限后注册，再重新进入目标（触发匹配）
  if (!dynamicRoutesAdded && to.name !== 'login') {
    try {
      const menus = await loadMenus()
      registerDynamicRoutes(menus as MenuNode[])
      if (permCodes.value.length === 0) {
        await loadPermissions().catch(() => undefined)
      }
      // 拉到菜单才标记完成；空菜单（旧会话 token 失效等）下次导航重试，HomeView 挂载时也会兜底重拉
      if (menus && menus.length > 0) {
        dynamicRoutesAdded = true
        return { ...to, replace: true }
      }
    } catch {
      // 菜单拉取异常不阻塞导航（静态路由仍可用），不标记完成，下次导航重试
    }
  }
  return true
})

export default router
