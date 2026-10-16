<template>
  <div class="login-wrapper">
    <div class="login-card">
      <div class="login-title">测试即用户</div>
      <div class="login-subtitle">请登录以继续</div>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="name">
          <el-input
            v-model="form.name"
            placeholder="用户名"
            prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-extra">
        <el-button type="primary" link @click="goRegister">用户注册</el-button>
        <el-button type="primary" link @click="goForgotPassword">忘记密码</el-button>
      </div>
      <div
        class="login-author"
        :class="{ 'is-visible': authorRevealed }"
        :style="{ transitionDuration: AUTHOR_FADE_MS + 'ms' }"
      >
        作者：梁梓轩
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from './authStore'
import { useIdleReveal, AUTHOR_FADE_MS } from '../../shared/composables/useIdleReveal'
import { ElMessage } from 'element-plus'

// 作者署名是隐藏彩蛋：页面静止 5 分钟后缓慢淡入，出现后一直留着
const { revealed: authorRevealed } = useIdleReveal({ hideOnActivity: false })

const router = useRouter()
const route = useRoute()
const { doVerify } = useAuthStore()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  name: '',
  password: ''
})

const rules = {
  name: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function goRegister() {
  router.push('/register')
}

function goForgotPassword() {
  router.push('/forgot-password')
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    // 步骤1：验证用户名密码
    await doVerify({ name: form.name, password: form.password })
    sessionStorage.setItem('pendingAuth', '1')
    const redirect = (route.query.redirect as string) || '/cases'
    router.push({ path: '/select-tenant', query: { redirect } })
  } catch (e) {
    ElMessage.error('登录失败，请检查用户名和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrapper {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef2f7;
}
.login-card {
  width: 400px;
  padding: 40px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}
.login-title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
  text-align: center;
  margin-bottom: 6px;
}
.login-subtitle {
  font-size: 14px;
  color: #6b7280;
  text-align: center;
  margin-bottom: 32px;
}
.login-btn {
  width: 100%;
  margin-top: 8px;
}
.login-extra {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}
.login-author {
  margin-top: 8px;
  font-size: 12px;
  color: #b0b7c3;
  text-align: center;
  letter-spacing: 0.5px;
  /* 默认不可见，静置后由 transition 缓慢淡入 */
  opacity: 0;
  pointer-events: none;
  user-select: none;
  transition-property: opacity;
  transition-timing-function: ease-in-out;
}
.login-author.is-visible {
  opacity: 1;
}
</style>
