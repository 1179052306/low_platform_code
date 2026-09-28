<script lang="ts">
import { defineComponent, computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getSession } from '@/store/auth'
import { listSavedPages } from '@/store/persistence'
import { listApiServices } from '@/store/api-store'
import { t, loadModule } from '@/store/lang'

export default defineComponent({
  name: 'WorkbenchView',
  setup() {
    const router = useRouter()

    const session = getSession()
    const username = computed(() => session?.username ?? '未登录')

    /** 按时段问候 */
    const greeting = computed(() => {
      const h = new Date().getHours()
      if (h < 6) return t('夜深了')
      if (h < 12) return t('上午好')
      if (h < 14) return t('中午好')
      if (h < 18) return t('下午好')
      return t('晚上好')
    })

    const todayText = computed(() => {
      const d = new Date()
      const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
      return `${d.getFullYear()} 年 ${
        d.getMonth() + 1
      } 月 ${d.getDate()} 日 星期${week}`
    })

    const pageCount = ref(0)
    const apiCount = ref(0)

    onMounted(async () => {
      loadModule('home')
      try {
        const pages = await listSavedPages()
        pageCount.value = pages.length
      } catch {
        /* 后端不可用时显示 0 */
      }
      try {
        const apis = await listApiServices()
        apiCount.value = apis.length
      } catch {
        /* 后端不可用时显示 0 */
      }
    })

    const shortcuts = [
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
      {

        title: 'API 服务管理',
        desc: '维护服务注册表，收口远程请求白名单',
        icon: '⛁',
        path: '/api-manage',
      },
    ]

    function goShortcut(path: string): void {
      if (path === '/pages') {
        const url = `${window.location.origin}${window.location.pathname}#${path}`
        window.open(url, '_blank')
        return
      }
      router.push(path)
    }

    return {
      username,
      greeting,
      todayText,
      pageCount,
      apiCount,
      shortcuts,
      goShortcut,
      t,
    }
  },
})
</script>

<template>
  <div class="workbench">
    <!-- 欢迎横幅 -->
    <section class="welcome-banner">
      <div class="banner-glow"></div>
      <div class="banner-content">
        <h1>{{ greeting }}，{{ username }}</h1>
        <p>{{ todayText }}　·　{{ t('欢迎回到低代码工作台') }}</p>
      </div>
    </section>

    <!-- 统计卡片 -->
    <section class="stat-row">
      <div class="stat-card">
        <span class="stat-icon stat-icon-blue">▤</span>
        <div class="stat-info">
          <div class="stat-num">{{ pageCount }}</div>
          <div class="stat-label">{{ t('已建页面') }}</div>
        </div>
      </div>
      <div class="stat-card">
        <span class="stat-icon stat-icon-indigo">⛁</span>
        <div class="stat-info">
          <div class="stat-num">{{ apiCount }}</div>
          <div class="stat-label">{{ t('注册 API 服务') }}</div>
        </div>
      </div>
    </section>

    <!-- 快捷入口 -->
    <section class="shortcut-section">
      <h2 class="section-title">{{ t('快捷入口') }}</h2>
      <div class="shortcut-row">
        <button
          v-for="item in shortcuts"
          :key="item.title"
          class="shortcut-card"
          @click="goShortcut(item.path)"
        >
          <span class="shortcut-icon">{{ item.icon }}</span>
          <strong>{{ t(item.title) }}</strong>
          <p>{{ t(item.desc) }}</p>
          <span class="shortcut-go">{{ t('进入 →') }}</span>
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.workbench {
  padding: 0;
}

/* 欢迎横幅 */
.welcome-banner {
  position: relative;
  border-radius: 14px;
  background: linear-gradient(120deg, #0b1f4b 0%, #1976d2 70%, #2563eb 100%);
  color: #fff;
  padding: 34px 32px;
  overflow: hidden;
  margin-bottom: 20px;
}
.banner-glow {
  position: absolute;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  background: rgba(125, 211, 252, 0.28);
  filter: blur(60px);
  top: -140px;
  right: -60px;
}
.banner-content {
  position: relative;
}
.welcome-banner h1 {
  font-size: 24px;
  font-weight: 700;
  margin-bottom: 8px;
}
.welcome-banner p {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.78);
}

/* 统计卡片 */
.stat-row {
  display: flex;
  gap: 16px;
  margin-bottom: 26px;
}
.stat-card {
  flex: 1;
  max-width: 320px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
}
.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}
.stat-icon-blue {
  background: #e3f0fd;
  color: #1976d2;
}
.stat-icon-indigo {
  background: #e8e7fd;
  color: #2563eb;
}
.stat-num {
  font-size: 26px;
  font-weight: 700;
  color: #111827;
  line-height: 1.1;
}
.stat-label {
  font-size: 12px;
  color: #9ca3af;
  margin-top: 2px;
}

/* 快捷入口 */
.section-title {
  font-size: 15px;
  color: #374151;
  margin-bottom: 14px;
}
.shortcut-row {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.shortcut-card {
  position: relative;
  width: 250px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 20px;
  text-align: left;
  cursor: pointer;
  transition: transform 0.15s, box-shadow 0.15s, border-color 0.15s;
}
.shortcut-card:hover {
  transform: translateY(-3px);
  border-color: #1976d2;
  box-shadow: 0 8px 22px rgba(25, 60, 120, 0.12);
}
.shortcut-icon {
  display: inline-flex;
  width: 40px;
  height: 40px;
  border-radius: 9px;
  background: #e3f0fd;
  color: #1976d2;
  align-items: center;
  justify-content: center;
  font-size: 17px;
  margin-bottom: 12px;
}
.shortcut-card strong {
  display: block;
  font-size: 15px;
  color: #1f2937;
  margin-bottom: 6px;
}
.shortcut-card p {
  font-size: 12.5px;
  color: #9ca3af;
  line-height: 1.6;
  margin-bottom: 12px;
}
.shortcut-go {
  font-size: 12px;
  color: #1976d2;
  opacity: 0;
  transition: opacity 0.15s;
}
.shortcut-card:hover .shortcut-go {
  opacity: 1;
}
</style>