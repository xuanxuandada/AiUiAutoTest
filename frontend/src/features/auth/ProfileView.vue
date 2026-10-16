<template>
  <div class="profile-page">
    <div class="profile-card">
      <!-- 头像区 -->
      <div class="avatar-section">
        <div class="avatar-circle">{{ userInitial }}</div>
        <div class="avatar-username">{{ currentUser?.name }}</div>
      </div>

      <!-- 信息区 -->
      <div class="info-section">
        <div class="info-row">
          <span class="info-label">用户ID</span>
          <span class="info-value">{{ currentUser?.userId ?? '—' }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">所属租户</span>
          <span class="info-value">{{ currentTenant?.name || '未分配' }}</span>
        </div>
      </div>

      <!-- AI 接口配置 -->
      <div class="ai-section">
        <div class="section-title">
          <span>AI 接口配置</span>
          <el-button
            v-if="!aiEditing"
            type="primary"
            link
            :disabled="aiLoading"
            @click="startEditAiConfig"
          >
            编辑
          </el-button>
        </div>

        <template v-if="aiEditing">
          <el-input
            v-model="aiForm.apiKey"
            placeholder="请输入新的 API Key"
            show-password
            size="large"
            :disabled="aiSaving"
          />
          <div class="ai-actions">
            <el-button
              type="primary"
              :loading="aiSaving"
              @click="handleSaveAiConfig"
            >
              {{ aiSaving ? '保存中...' : '保存' }}
            </el-button>
            <el-button :disabled="aiSaving" @click="cancelEditAiConfig">取消</el-button>
          </div>
        </template>

        <template v-else>
          <el-input
            :model-value="apiKeyDisplay"
            :placeholder="apiKeyPlaceholder"
            disabled
            size="large"
          />
          <div class="ai-tip">出于安全考虑，后端仅返回掩码后的 Key；点「编辑」可重新设置。</div>
        </template>
      </div>

      <!-- 修改密码 -->
      <div class="password-section">
        <div class="section-title">修改密码</div>
        <el-form
          ref="pwdFormRef"
          :model="pwdForm"
          :rules="pwdRules"
          label-width="0"
          size="large"
        >
          <el-form-item prop="oldPassword">
            <el-input
              v-model="pwdForm.oldPassword"
              type="password"
              placeholder="当前密码"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item prop="newPassword">
            <el-input
              v-model="pwdForm.newPassword"
              type="password"
              placeholder="新密码"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="pwdForm.confirmPassword"
              type="password"
              placeholder="确认新密码"
              prefix-icon="Lock"
              show-password
            />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              class="submit-btn"
              :loading="changing"
              @click="handleChangePassword"
            >
              {{ changing ? '修改中...' : '确认修改' }}
            </el-button>
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance } from 'element-plus'
import { useAuthStore } from './authStore'
import { changePassword } from './api'
import { getAiConfig, saveAiConfig } from '../user/api'
import type { AiConfig } from '../user/types'

const router = useRouter()
const { user: currentUser, currentTenant, logout } = useAuthStore()

const pwdFormRef = ref<FormInstance | null>(null)
const changing = ref(false)

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const userInitial = computed(() =>
  currentUser.value?.name?.charAt(0).toUpperCase() || '?'
)

const validateConfirm = (_rule: any, value: string, callback: any) => {
  if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const validateNewPassword = (_rule: any, value: string, callback: any) => {
  if (value && value === pwdForm.oldPassword) {
    callback(new Error('新密码不能与当前密码相同'))
  } else {
    callback()
  }
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' },
    { validator: validateNewPassword, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

// ===== AI 接口配置（GET / PUT /api/user/ai-config）=====
const aiConfig = ref<AiConfig | null>(null)
const aiLoading = ref(false)
const aiLoaded = ref(false)
const aiError = ref('')
const aiEditing = ref(false)
const aiSaving = ref(false)
const aiForm = reactive({ apiKey: '' })

/** 只读态展示：掩码 Key；未配置时留空走 placeholder */
const apiKeyDisplay = computed(() => aiConfig.value?.apiKeyMasked || '')

const apiKeyPlaceholder = computed(() => {
  if (aiLoading.value && !aiLoaded.value) return '加载中...'
  if (aiError.value) return aiError.value
  if (apiKeyDisplay.value) return ''
  return aiLoaded.value ? '暂未配置 API Key' : '加载中...'
})

async function loadAiConfig() {
  aiLoading.value = true
  aiError.value = ''
  try {
    aiConfig.value = await getAiConfig()
  } catch (e: any) {
    aiConfig.value = null
    aiError.value = e?.message || '获取 AI 配置失败'
  } finally {
    aiLoaded.value = true
    aiLoading.value = false
  }
}

function startEditAiConfig() {
  // 掩码值不可用，编辑时置空让用户重新输入
  aiForm.apiKey = ''
  aiEditing.value = true
}

function cancelEditAiConfig() {
  aiForm.apiKey = ''
  aiEditing.value = false
}

async function handleSaveAiConfig() {
  const apiKey = aiForm.apiKey.trim()
  if (!apiKey) {
    ElMessage.warning('请输入 API Key')
    return
  }

  // 保留后端已有的其它字段，避免只改 Key 时把配置覆盖成空
  const current = aiConfig.value || {}
  aiSaving.value = true
  try {
    await saveAiConfig({
      apiKey,
      baseUrl: current.baseUrl as string | undefined,
      modelName: current.modelName as string | undefined,
      modelFamily: current.modelFamily as string | undefined,
      timeout: current.timeout as number | undefined,
      enabled: current.enabled as number | undefined,
    })
    ElMessage.success('AI 配置保存成功')
    aiEditing.value = false
    aiForm.apiKey = ''
    await loadAiConfig()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存 AI 配置失败')
  } finally {
    aiSaving.value = false
  }
}

onMounted(loadAiConfig)

async function handleChangePassword() {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return

  changing.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    await logout()
    router.replace('/login')
  } catch {
    ElMessage.error('密码修改失败')
  } finally {
    changing.value = false
  }
}
</script>

<style scoped>
.profile-page {
  max-width: 520px;
  margin: 0 auto;
}
.profile-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
  padding: 40px;
}
.avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  margin-bottom: 32px;
}
.avatar-circle {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: #2563eb;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 12px;
}
.avatar-username {
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}
.info-section {
  margin-bottom: 32px;
  padding: 20px;
  background: #f9fafb;
  border-radius: 12px;
}
.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
}
.info-row + .info-row {
  border-top: 1px solid #e5e7eb;
}
.info-label {
  font-size: 14px;
  color: #6b7280;
}
.info-value {
  font-size: 14px;
  color: #1f2937;
  font-weight: 500;
}
.ai-section {
  margin-bottom: 32px;
  padding: 20px;
  background: #f9fafb;
  border-radius: 12px;
}
.ai-section .section-title {
  margin-bottom: 12px;
}
.ai-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #9ca3af;
}
.ai-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}
/* 非编辑态：置灰展示，与可编辑态区分 */
.ai-section :deep(.el-input.is-disabled .el-input__wrapper) {
  background-color: #f3f4f6;
  box-shadow: 0 0 0 1px #e5e7eb inset;
}
.ai-section :deep(.el-input.is-disabled .el-input__inner) {
  color: #4b5563;
  -webkit-text-fill-color: #4b5563;
}
.password-section {
  padding-top: 24px;
  border-top: 1px solid #e5e7eb;
}
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 20px;
}
.submit-btn {
  width: 100%;
}
</style>
