<template>
  <div class="login-wrapper">
    <div class="login-card">
      <div class="login-title">忘记密码</div>

      <!-- 步骤进度栏 -->
      <el-steps :active="step" align-center class="forgot-steps">
        <el-step title="验证身份" />
        <el-step title="重置密码" />
      </el-steps>

      <!-- 第一步：验证身份 -->
      <el-form
        v-if="step === 0"
        ref="step1Ref"
        :model="form"
        :rules="step1Rules"
        label-width="0"
        size="large"
        @keyup.enter="goStep2"
      >
        <el-form-item prop="phone">
          <el-input
            v-model="form.phone"
            placeholder="手机号"
            prefix-icon="Phone"
          />
        </el-form-item>
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="smsCode">
          <div class="code-row">
            <el-input
              v-model="form.smsCode"
              placeholder="验证码"
              prefix-icon="Message"
              class="code-input"
            />
            <el-button
              class="send-btn"
              :disabled="countdown > 0"
              :loading="sending"
              @click="handleSendCode"
            >
              {{ countdown > 0 ? `${countdown}s` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            class="login-btn"
            @click="goStep2"
          >
            下一步
          </el-button>
        </el-form-item>
      </el-form>

      <!-- 第二步：重置密码 -->
      <el-form
        v-else
        ref="step2Ref"
        :model="form"
        :rules="step2Rules"
        label-width="0"
        size="large"
        @keyup.enter="handleReset"
      >
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="新密码"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="确认新密码"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <div class="step-actions">
            <el-button size="large" @click="step = 0">上一步</el-button>
            <el-button
              type="primary"
              size="large"
              :loading="loading"
              @click="handleReset"
            >
              {{ loading ? '重置中...' : '确认重置' }}
            </el-button>
          </div>
        </el-form-item>
      </el-form>

      <div class="login-extra">
        <span class="text-muted">想起密码了？</span>
        <el-button type="primary" link @click="goLogin">返回登录</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { forgotPassword, sendCode } from './api'

const router = useRouter()

const step = ref(0)
const step1Ref = ref(null)
const step2Ref = ref(null)
const loading = ref(false)
const sending = ref(false)
const countdown = ref(0)
let timer = null

const form = reactive({
  phone: '',
  username: '',
  smsCode: '',
  password: '',
  confirmPassword: ''
})

// ===== 第一步校验 =====
const step1Rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号格式', trigger: 'blur' }
  ],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  smsCode: [{ required: true, message: '请输入验证码', trigger: 'blur' }]
}

// ===== 第二步校验 =====
const validateConfirm = (_rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const step2Rules = {
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

function goLogin() {
  router.push('/login')
}

// ===== 第一步 → 第二步 =====
async function goStep2() {
  if (!step1Ref.value) return
  try {
    await step1Ref.value.validate()
    step.value = 1
  } catch {
    // 校验失败
  }
}

// ===== 发送验证码 =====
async function handleSendCode() {
  if (!/^1[3-9]\d{9}$/.test(form.phone)) {
    ElMessage.warning('请先输入正确的手机号')
    return
  }

  sending.value = true
  try {
    await sendCode({ phone: form.phone })
    ElMessage.success('验证码已发送')
    countdown.value = 60
    timer = setInterval(() => {
      countdown.value--
      if (countdown.value <= 0) {
        clearInterval(timer)
        timer = null
      }
    }, 1000)
  } catch (e) {
    ElMessage.error('验证码发送失败，请稍后重试')
  } finally {
    sending.value = false
  }
}

// ===== 提交重置 =====
async function handleReset() {
  if (!step2Ref.value) return
  try {
    await step2Ref.value.validate()
  } catch {
    return
  }

  loading.value = true
  try {
    await forgotPassword({
      phone: form.phone,
      username: form.username,
      smsCode: form.smsCode,
      password: form.password
    })
    ElMessage.success('密码重置成功，请用新密码登录')
    router.push('/login')
  } catch (e) {
    ElMessage.error('重置失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
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
  width: 440px;
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
  margin-bottom: 24px;
}

/* 步骤条 */
.forgot-steps {
  margin-bottom: 32px;
}
.forgot-steps :deep(.el-step__title) {
  font-size: 13px;
}

.login-btn {
  width: 100%;
}
.step-actions {
  display: flex;
  gap: 12px;
  width: 100%;
}
.step-actions .el-button {
  flex: 1;
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

/* 验证码行 */
.code-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.code-row .code-input {
  flex: 1;
}
.code-row .send-btn {
  width: 120px;
  flex-shrink: 0;
  height: 40px;
  font-size: 13px;
  white-space: nowrap;
}
</style>
