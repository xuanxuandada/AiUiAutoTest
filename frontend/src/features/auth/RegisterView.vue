<template>
  <div class="login-wrapper">
    <div class="login-card">
      <div class="login-title">用户注册</div>
      <div class="login-subtitle">创建您的账号</div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        size="large"
        @keyup.enter="handleRegister"
      >
        <el-form-item prop="name">
          <el-input
            v-model="form.name"
            placeholder="用户名"
            prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="phone">
          <el-input
            v-model="form.phone"
            placeholder="手机号"
            prefix-icon="Phone"
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
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="确认密码"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            class="login-btn"
            :loading="loading"
            @click="handleRegister"
          >
            {{ loading ? '注册中...' : '注 册' }}
          </el-button>
        </el-form-item>
      </el-form>

      <div class="login-extra">
        <span class="text-muted">已有账号？</span>
        <el-button type="primary" link @click="goLogin">返回登录</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from './api'

const router = useRouter()

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  name: '',
  phone: '',
  password: '',
  confirmPassword: ''
})

const validateConfirm = (_rule: any, value: string, callback: any) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules = {
  name: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号格式', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

function goLogin() {
  router.push('/login')
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await register({ name: form.name, phone: form.phone, password: form.password })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e: any) {
    ElMessage.error(e?.message || '注册失败，请稍后重试')
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
  justify-content: center;
  align-items: center;
  gap: 4px;
  margin-top: 16px;
}
.text-muted {
  color: #6b7280;
  font-size: 14px;
}
</style>
