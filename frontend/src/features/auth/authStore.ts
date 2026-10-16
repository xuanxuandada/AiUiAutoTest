import { ref, computed } from 'vue'
import type { UserInfo, LoginParams, TenantInfo, VerifyParams } from './types'
import { login as apiLogin, logout as apiLogout, getTenants as apiGetTenants, verify as apiVerify } from './api'

// 模块级单例状态
const user = ref<UserInfo | null>(
  localStorage.getItem('user') ? JSON.parse(localStorage.getItem('user')!) : null
)
const token = ref<string | null>(localStorage.getItem('token'))
const currentTenant = ref<TenantInfo | null>(
  localStorage.getItem('tenant') ? JSON.parse(localStorage.getItem('tenant')!) : null
)
const isLoggedIn = computed(() => !!token.value)

/** 暂存登录凭据，供步骤2/3使用 */
const pendingCreds = ref<{ name: string; password: string | number; userId?: string | number } | null>(null)

/** 步骤1：验证用户名密码 */
async function doVerify(params: VerifyParams) {
  const result = await apiVerify(params)
  pendingCreds.value = { name: params.name, password: params.password, userId: result.userId }
  return result
}

/** 步骤3：正式登录，获取 token */
async function login(params: LoginParams) {
  const userInfo = await apiLogin(params)
  user.value = userInfo
  localStorage.setItem('user', JSON.stringify(userInfo))
  token.value = userInfo.token
  localStorage.setItem('token', userInfo.token)
  pendingCreds.value = null
  sessionStorage.removeItem('pendingAuth')
}

async function logout() {
  await apiLogout()
  user.value = null
  token.value = null
  currentTenant.value = null
  pendingCreds.value = null
  localStorage.removeItem('user')
  localStorage.removeItem('token')
  localStorage.removeItem('tenant')
}

/** 步骤2：获取可选租户列表（需传密码再次验证） */
async function fetchTenants(): Promise<TenantInfo[]> {
  const creds = pendingCreds.value
  if (!creds) throw new Error('登录凭据已过期，请重新登录')
  return apiGetTenants(creds.name, creds.password, creds.userId)
}

/** 认证 Store — 单例 */
export function useAuthStore() {
  return { user, token, currentTenant, isLoggedIn, doVerify, login, logout, fetchTenants, pendingCreds }
}
