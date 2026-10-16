import http from '../../shared/utils/http'
import { normalizePage } from '../../shared/utils'
import type { PageResult } from '../../shared/types'
import type { TenantConfig, TenantOption, PaginatedResult, SaveTenantConfigParams, TenantRole, SaveTenantRoleParams, UserItem, SaveUserParams, AiConfig, SaveAiConfigParams } from './types'

const BASE_URL = '/api/tenants'

/** API 统一响应结构 */
interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

// ===== 租户 API（已对接 /api/tenants）=====

/**
 * 获取租户下拉选项 — GET /api/tenants/options（支持分页/无限滚动）
 */
export async function getTenantOptions(page = 1, pageSize = 20): Promise<PaginatedResult<TenantOption>> {
  const res = await http.get<ApiResponse<PaginatedResult<TenantOption> | TenantOption[]>>(`${BASE_URL}/options`, {
    params: { page, pageSize }
  })
  if (res.data.code === 200) {
    const data = res.data.data
    // 兼容两种后端返回格式：
    // 1. 分页对象 { records: [...], total, page, pageSize }
    // 2. 纯数组  [...]
    if (Array.isArray(data)) {
      return { records: data, total: data.length, page: 1, pageSize: data.length }
    }
    if (data && typeof data === 'object' && Array.isArray(data.records)) {
      return data
    }
    // 返回空
    return { records: [], total: 0, page: 1, pageSize: 20 }
  }
  throw new Error(res.data.message || '获取租户选项失败')
}

/**
 * 搜索租户 — GET /api/tenants/search
 */
export async function searchTenants(keyword: string, page = 1, pageSize = 20): Promise<PaginatedResult<TenantConfig>> {
  const res = await http.get<ApiResponse<PaginatedResult<TenantConfig>>>(`${BASE_URL}/search`, {
    params: { keyword, page, pageSize }
  })
  if (res.data.code === 200) {
    return res.data.data
  }
  throw new Error(res.data.message || '搜索租户失败')
}

/**
 * 获取租户列表 — GET /api/tenants
 */
export async function getTenantList(page = 1, pageSize = 20): Promise<PaginatedResult<TenantConfig>> {
  const res = await http.get<ApiResponse<PaginatedResult<TenantConfig>>>(BASE_URL, {
    params: { page, pageSize }
  })
  if (res.data.code === 200) {
    return res.data.data
  }
  throw new Error(res.data.message || '获取租户列表失败')
}

/**
 * 保存租户配置（新建/编辑）— POST /api/tenants
 */
export async function saveTenantConfig(params: SaveTenantConfigParams): Promise<TenantConfig> {
  const res = await http.post<ApiResponse<TenantConfig>>(BASE_URL, params)
  if (res.data.code === 200) {
    return res.data.data
  }
  throw new Error(res.data.message || '保存租户配置失败')
}

// ===== 角色 API（已对接 /api/roles）=====

/**
 * 获取指定租户下的角色列表 — GET /api/roles?tenantId=xxx
 */
export async function getTenantRoles(tenantId: string | number): Promise<TenantRole[]> {
  const res = await http.get<ApiResponse<TenantRole[]>>('/api/roles', {
    params: { tenantId }
  })
  if (res.data.code === 200) {
    return res.data.data
  }
  throw new Error(res.data.message || '获取租户角色失败')
}

/**
 * 获取所有角色（用于角色管理页）— GET /api/roles
 */
export async function getAllRoles(): Promise<TenantRole[]> {
  const res = await http.get<ApiResponse<TenantRole[]>>('/api/roles')
  if (res.data.code === 200) {
    return res.data.data
  }
  throw new Error(res.data.message || '获取角色列表失败')
}

/**
 * 保存角色（新增/编辑）— POST /api/roles
 */
export async function saveRole(params: SaveTenantRoleParams): Promise<TenantRole> {
  const res = await http.post<ApiResponse<TenantRole>>('/api/roles', params)
  if (res.data.code === 200) {
    return res.data.data
  }
  throw new Error(res.data.message || '保存角色失败')
}

/**
 * 删除角色 — DELETE /api/roles/{id}
 */
export async function deleteRole(roleId: string | number): Promise<void> {
  const res = await http.delete<ApiResponse<null>>(`/api/roles/${roleId}`)
  if (res.data.code !== 200) {
    throw new Error(res.data.message || '删除角色失败')
  }
}

/**
 * 搜索用户 — GET /api/users/search?keyword=&page=1&pageSize=20
 * 后端已改为分页：数据在 data.records，总数在 data.total
 */
export async function searchUsers(keyword: string, page = 1, pageSize = 20): Promise<PageResult<UserItem>> {
  const res = await http.get<ApiResponse<PageResult<UserItem> | UserItem[]>>('/api/users/search', {
    params: { keyword, page, pageSize }
  })
  if (res.data.code === 200) return normalizePage<UserItem>(res.data.data)
  throw new Error(res.data.message || '搜索用户失败')
}

/**
 * 获取用户列表 — GET /api/users?page=1&pageSize=20
 */
export async function getUserList(page = 1, pageSize = 20): Promise<PageResult<UserItem>> {
  const res = await http.get<ApiResponse<PageResult<UserItem> | UserItem[]>>('/api/users', {
    params: { page, pageSize }
  })
  if (res.data.code === 200) return normalizePage<UserItem>(res.data.data)
  throw new Error(res.data.message || '获取用户列表失败')
}

/**
 * 保存用户信息 — PUT /api/users
 */
export async function saveUser(params: SaveUserParams): Promise<void> {
  const res = await http.put<ApiResponse<null>>(`/api/users/${params.id}`, params)
  if (res.data.code !== 200) {
    throw new Error(res.data.message || '保存用户失败')
  }
}

/**
 * 获取当前登录用户的 AI 配置 — GET /api/user/ai-config
 * 后端只返回掩码后的 Key（apiKeyMasked），不返回明文
 */
export async function getAiConfig(): Promise<AiConfig> {
  const res = await http.get<ApiResponse<AiConfig>>('/api/user/ai-config')
  if (res.data.code === 200) {
    return res.data.data ?? {}
  }
  throw new Error(res.data.message || '获取 AI 配置失败')
}

/**
 * 保存当前登录用户的 AI 配置 — PUT /api/user/ai-config
 * @param params apiKey 留空时不应传该字段，避免后端将其清空
 */
export async function saveAiConfig(params: SaveAiConfigParams): Promise<AiConfig> {
  const body: SaveAiConfigParams = { ...params }
  if (!body.apiKey) delete body.apiKey
  const res = await http.put<ApiResponse<AiConfig>>('/api/user/ai-config', body)
  if (res.data.code === 200) {
    return res.data.data ?? {}
  }
  throw new Error(res.data.message || '保存 AI 配置失败')
}
