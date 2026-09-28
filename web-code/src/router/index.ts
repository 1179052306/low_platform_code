import { createRouter, createWebHashHistory } from 'vue-router'
import { isLoggedIn } from '@/store/auth'

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

/** 登录守卫：未登录重定向登录页（携带原地址），已登录访问登录页跳主页 */
router.beforeEach((to) => {
  const authed = isLoggedIn()
  if (to.name === 'login') {
    return authed ? { name: 'home' } : true
  }
  if (!authed) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
