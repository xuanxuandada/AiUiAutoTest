import http from '../../shared/utils/http'
import type { LoginParams, RegisterParams, ForgotPasswordParams, ChangePasswordParams, SendCodeParams, UserInfo, TenantInfo, VerifyParams, VerifyResult } from './types'
import { encryptPassword } from '../../shared/utils/crypto'

const BASE_URL = '/api/auth'

/** API 统一响应结构 */
interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/**
 * 步骤1：验证用户名密码 — POST /api/auth/verify
 */
export async function verify(params: VerifyParams): Promise<VerifyResult> {
  const res = await http.post<ApiResponse<VerifyResult>>(`${BASE_URL}/verify`, {
    name: params.name,
    password: encryptPassword(params.password),
  })
  if (res.data.code === 200) {
    return res.data.data
  }
  throw new Error(res.data.message || '验证失败')
}

/**
 * 步骤2：获取用户可选租户 — GET /api/auth/tenants?name=xxx&password=xxx&tenantId=xxx
 * 后端返回单个租户对象，统一包装为数组
 */
export async function getTenants(name: string, password: string | number, tenantId?: string | number): Promise<TenantInfo[]> {
  const res = await http.get<ApiResponse<TenantInfo | TenantInfo[]>>(`${BASE_URL}/tenants`, {
    params: { name, password: encryptPassword(password), tenantId }
  })
  if (res.data.code === 200) {
    const data = res.data.data
    return Array.isArray(data) ? data : [data]
  }
  throw new Error(res.data.message || '获取租户选项失败')
}

/**
 * 登录
 */
export async function login(params: LoginParams): Promise<UserInfo> {
  const res = await http.post<ApiResponse<UserInfo>>(`${BASE_URL}/login`, {
    name: params.name,
    password: encryptPassword(params.password),
    tenantId: params.tenantid,
  })
  if (res.data.code === 200) {
    return res.data.data
  }
  throw new Error(res.data.message || '登录失败')
}

/**
 * 注册
 */
export async function register(params: RegisterParams): Promise<void> {
  const res = await http.post('/api/register', {
    ...params,
    tenantId: 1,
    roleId: 1,
    password: encryptPassword(params.password),
  })
  if (res.data.code !== 200) {
    throw new Error(res.data.message || '注册失败')
  }
}

/**
 * 发送短信验证码（忘记密码用）
 */
export async function sendCode(params: SendCodeParams): Promise<void> {
  // TODO: 对接真实 API
  // await axios.post(`${BASE_URL}/send-code`, params)
}

/**
 * 忘记密码
 */
export async function forgotPassword(params: ForgotPasswordParams): Promise<void> {
  // TODO: 对接真实 API
  // await axios.post(`${BASE_URL}/forgot-password`, {
  //   ...params,
  //   password: encryptPassword(params.password),
  // })
}

/**
 * 退出登录
 */
export async function logout(): Promise<void> {
  // TODO: 对接真实 API
  // await axios.post(`${BASE_URL}/logout`)
}

/**
 * 修改密码 — PUT /api/auth/updatePassword
 */
export async function changePassword(params: ChangePasswordParams): Promise<void> {
  const res = await http.put<ApiResponse<null>>(`${BASE_URL}/updatePassword`, {
    oldPassword: encryptPassword(params.oldPassword),
    newPassword: encryptPassword(params.newPassword),
  })
  if (res.data.code !== 200) {
    throw new Error(res.data.message || '修改密码失败')
  }
}
