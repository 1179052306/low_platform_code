<script lang="ts">
import { defineComponent, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { login } from '@/store/auth'
import { t, loadModule } from '@/store/lang'

export default defineComponent({
  name: 'LoginView',
  setup() {
    const router = useRouter()
    const route = useRoute()
    const username = ref('')
    const password = ref('')
    const errorMessage = ref('')
    const loggingIn = ref(false)

    onMounted(() => loadModule('login'))

    async function onLogin(): Promise<void> {
      if (loggingIn.value) return
      errorMessage.value = ''
      loggingIn.value = true
      // 模拟网络延迟，后台接口就绪后由真实请求取代
      await new Promise((r) => setTimeout(r, 400))
      try {
        login(username.value, password.value)
        const redirect = (route.query.redirect as string) || '/home'
        router.push(redirect)
      } catch (err) {
        errorMessage.value = err instanceof Error ? err.message : String(err)
      } finally {
        loggingIn.value = false
      }
    }

    return { username, password, errorMessage, loggingIn, onLogin, t }
  },
})
</script>

<template>
  <div class="login-page">
    <!-- 左侧品牌区 -->
    <aside class="brand-pane">
      <div class="brand-glow brand-glow-1"></div>
      <div class="brand-glow brand-glow-2"></div>
      <div class="brand-grid"></div>
      <div class="brand-content">
        <div class="brand-logo">
          <span class="brand-logo-mark">◧</span>
          <span class="brand-logo-text">{{ t('低代码平台') }}</span>
        </div>
        <h1 class="brand-headline">
          {{ t('可视化搭建') }}<br />{{ t('让应用生长更轻快') }}
        </h1>
        <ul class="brand-features">
          <li>
            <span class="feature-dot"></span>
            <div>
              <strong>{{ t('拖拽式设计器') }}</strong>
              <p>{{ t('控件物料拖拽布局，属性可视化配置') }}</p>
            </div>
          </li>
          <li>
            <span class="feature-dot"></span>
            <div>
              <strong>{{ t('统一 API 治理') }}</strong>
              <p>{{ t('服务注册表白名单收口，杜绝任意请求') }}</p>
            </div>
          </li>
          <li>
            <span class="feature-dot"></span>
            <div>
              <strong>{{ t('页面即数据') }}</strong>
              <p>{{ t('页面元数据持久化，随时预览与恢复') }}</p>
            </div>
          </li>
        </ul>
      </div>
    </aside>

    <!-- 右侧登录表单 -->
    <main class="form-pane">
      <div class="login-card">
        <h2 class="login-title">{{ t('欢迎回来') }}</h2>
        <p class="login-subtitle">{{ t('请登录您的账号以继续') }}</p>

        <div v-if="errorMessage" class="login-error">{{ t(errorMessage) }}</div>

        <div class="form-field">
          <label>{{ t('用户名') }}</label>
          <input
            v-model="username"
            type="text"
            :placeholder="t('请输入用户名')"
            autocomplete="username"
            @keyup.enter="onLogin"
          />
        </div>
        <div class="form-field">
          <label>{{ t('密码') }}</label>
          <input
            v-model="password"
            type="password"
            :placeholder="t('请输入密码')"
            autocomplete="current-password"
            @keyup.enter="onLogin"
          />
        </div>

        <button class="login-btn" :disabled="loggingIn" @click="onLogin">
          <span v-if="loggingIn" class="spinner"></span>
          {{ loggingIn ? t('登录中…') : t('登 录') }}
        </button>

        <p class="login-hint">{{ t('演示账号：admin　密码：123456') }}</p>
      </div>
    </main>
  </div>
</template>

<style scoped>
.login-page {
  display: flex;
  height: 100%;
  overflow: hidden;
}

/* ================ 左侧品牌区 ================ */
.brand-pane {
  position: relative;
  flex: 1 1 55%;
  background: linear-gradient(135deg, #0b1f4b 0%, #1976d2 60%, #2563eb 100%);
  color: #fff;
  overflow: hidden;
  display: flex;
  align-items: center;
}
.brand-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(70px);
  opacity: 0.35;
}
.brand-glow-1 {
  width: 420px;
  height: 420px;
  background: #64b5f6;
  top: -120px;
  right: -80px;
  animation: drift 9s ease-in-out infinite alternate;
}
.brand-glow-2 {
  width: 320px;
  height: 320px;
  background: #7c6cf0;
  bottom: -100px;
  left: -60px;
  animation: drift 11s ease-in-out infinite alternate-reverse;
}
@keyframes drift {
  from {
    transform: translate(0, 0);
  }
  to {
    transform: translate(40px, 28px);
  }
}
.brand-grid {
  position: absolute;
  inset: 0;
  background-image: linear-gradient(
      rgba(255, 255, 255, 0.06) 1px,
      transparent 1px
    ),
    linear-gradient(90deg, rgba(255, 255, 255, 0.06) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.9), transparent 75%);
}
.brand-content {
  position: relative;
  padding: 0 9%;
  animation: rise 0.7s ease both;
}
@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.brand-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 42px;
}
.brand-logo-mark {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
}
.brand-logo-text {
  font-size: 19px;
  font-weight: 600;
  letter-spacing: 1px;
}
.brand-headline {
  font-size: 34px;
  line-height: 1.45;
  font-weight: 700;
  margin-bottom: 40px;
}
.brand-features {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 22px;
}
.brand-features li {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}
.feature-dot {
  flex-shrink: 0;
  width: 10px;
  height: 10px;
  margin-top: 6px;
  border-radius: 50%;
  background: #7dd3fc;
  box-shadow: 0 0 0 4px rgba(125, 211, 252, 0.18);
}
.brand-features strong {
  display: block;
  font-size: 15px;
  margin-bottom: 3px;
}
.brand-features p {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.72);
}

/* ================ 右侧表单区 ================ */
.form-pane {
  flex: 1 1 45%;
  background: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
}
.login-card {
  width: 380px;
  max-width: 100%;
  background: #fff;
  border-radius: 14px;
  padding: 40px 36px 30px;
  box-shadow: 0 10px 40px rgba(25, 60, 120, 0.1);
  animation: rise 0.7s ease 0.1s both;
}
.login-title {
  font-size: 24px;
  color: #1f2937;
  margin-bottom: 6px;
}
.login-subtitle {
  font-size: 13px;
  color: #9ca3af;
  margin-bottom: 26px;
}
.login-error {
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #dc2626;
  font-size: 13px;
  border-radius: 6px;
  padding: 8px 12px;
  margin-bottom: 16px;
}
.form-field {
  margin-bottom: 16px;
}
.form-field label {
  display: block;
  font-size: 13px;
  color: #4b5563;
  margin-bottom: 6px;
}
.form-field input {
  width: 100%;
  height: 40px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  padding: 0 12px;
  font-size: 14px;
  outline: none;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.form-field input:focus {
  border-color: #1976d2;
  box-shadow: 0 0 0 3px rgba(25, 118, 210, 0.14);
}
.login-btn {
  width: 100%;
  height: 42px;
  margin-top: 8px;
  border: none;
  border-radius: 8px;
  background: #2563eb;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: transform 0.12s, box-shadow 0.15s, opacity 0.15s;
}
.login-btn:hover:not(:disabled) {
  box-shadow: 0 6px 18px rgba(37, 99, 235, 0.35);
}
.login-btn:active:not(:disabled) {
  transform: scale(0.98);
}
.login-btn:disabled {
  opacity: 0.7;
  cursor: default;
}
.spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.login-hint {
  margin-top: 20px;
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
}

/* ================ 响应式 ================ */
@media (max-width: 900px) {
  .brand-pane {
    display: none;
  }
  .form-pane {
    flex: 1;
  }
}
</style>