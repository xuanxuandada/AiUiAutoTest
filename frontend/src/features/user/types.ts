/** 租户下拉选项 */
export interface TenantOption {
  id: string | number
  name: string
  code: string
}

/** 租户配置 */
export interface TenantConfig {
  id: string | number
  name: string
  code: string
  contact: string
  phone: string
  status: 'active' | 'inactive'
  createTime?: string
  updateTime?: string
}

/** 分页结果 */
export interface PaginatedResult<T> {
  records: T[]
  total: number
  page: number
  pageSize: number
}

/** 保存租户配置参数 */
export interface SaveTenantConfigParams {
  id?: string | number
  name: string
  code: string
  contact: string
  phone: string
  status: 'active' | 'inactive'
}

/** 租户下的角色定义 */
export interface TenantRole {
  id: string | number
  tenantId: string | number
  tenantName: string
  name: string
  code: string
  description: string
  status: 'active' | 'inactive'
  createTime?: string
  updateTime?: string
}

/** 保存租户角色参数 */
export interface SaveTenantRoleParams {
  id?: string | number
  tenantId: string | number
  name: string
  code: string
  description: string
  status: 'active' | 'inactive'
}

/** 用户列表项 */
export interface UserItem {
  id: string | number
  password: string
  name: string
  phone: string
  age: number
  tenantId: string | number
  roleId: string | number
  createTime?: string
  updateTime?: string
}

/** 保存用户参数 */
export interface SaveUserParams {
  id: string | number
  name: string
  phone: string
  age: number
  tenantId: string | number
  roleId: string | number
}

/** 当前用户的 AI 配置（GET /api/user/ai-config 返回，后端不返回明文 apiKey） */
export interface AiConfig {
  /** 掩码后的 API Key，如 sk-****abcd */
  apiKeyMasked?: string
  /** 是否已配置 Key */
  apiKeyConfigured?: boolean
  baseUrl?: string
  modelName?: string
  modelFamily?: string
  timeout?: number
  /** 0=禁用 1=启用 */
  enabled?: number
  updateTime?: string
  [prop: string]: unknown
}

/** 保存 AI 配置参数（PUT /api/user/ai-config） */
export interface SaveAiConfigParams {
  /** 明文 API Key，留空表示不修改 */
  apiKey?: string
  baseUrl?: string
  modelName?: string
  modelFamily?: string
  timeout?: number
  enabled?: number
}
