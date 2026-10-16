import http from '../../shared/utils/http'
import { normalizePage } from '../../shared/utils'
import type { PageResult } from '../../shared/types'
import type { CaseRow, SavedCase, CreateCaseRequest, UpdateCaseRequest, CaseCategory, CreateCaseCategoryParams, UpdateCaseCategoryParams, CaseTreeNode } from './types'

const BASE_URL = '/api/cases'
const CATEGORY_URL = '/api/case/categories'

/** API 统一响应结构 */
interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 用例列表查询参数 */
export interface CaseListParams {
  page?: number
  pageSize?: number
  /** 按类型筛选 */
  type?: string
  /** 按标签筛选 */
  tags?: string
  /** 按用例名称模糊匹配 */
  keyword?: string
}

/**
 * 获取用例列表 — GET /api/cases?type=&tags=&keyword=&page=1&pageSize=20
 * 后端已改为分页：数据在 data.records，总数在 data.total
 */
export async function fetchCaseList(params: CaseListParams = {}): Promise<PageResult<any>> {
  const { page = 1, pageSize = 20, type, tags, keyword } = params
  const res = await http.get<ApiResponse<PageResult<any> | any[]>>(BASE_URL, {
    params: { page, pageSize, type, tags, keyword }
  })
  if (res.data.code === 200) return normalizePage<any>(res.data.data)
  throw new Error(res.data.message || '获取用例列表失败')
}

/**
 * 拉取全部用例（逐页循环）— 仅供「套件选例弹框」这类需要全量数据的场景使用。
 *
 * @param keyword 按名称筛选（下推到后端）；腾下的 ID/模块/创建人 筛选只能在前端做
 */
export async function fetchAllCases(keyword = '', pageSize = 100, maxPages = 50): Promise<any[]> {
  const all: any[] = []
  for (let page = 1; page <= maxPages; page++) {
    const { records, total } = await fetchCaseList({ page, pageSize, keyword: keyword || undefined })
    all.push(...records)
    if (records.length === 0 || records.length < pageSize || all.length >= total) break
  }
  return all
}

/**
 * 创建用例 — POST /api/cases
 */
export async function createCase(payload: CreateCaseRequest): Promise<void> {
  await http.post(`${BASE_URL}`, payload)
}

/**
 * 获取用例详情 — GET /api/cases/{id}
 */
export async function fetchCaseById(id: number | string): Promise<any> {
  const res = await http.get<ApiResponse<any>>(`${BASE_URL}/${id}`)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '获取用例详情失败')
}

/**
 * 更新用例 — PUT /api/cases/{id}
 */
export async function updateCase(id: number | string, payload: UpdateCaseRequest): Promise<void> {
  await http.put(`${BASE_URL}/${id}`, payload)
}

/**
 * 删除用例 — DELETE /api/cases/{id}
 */
export async function deleteCase(id: number | string): Promise<void> {
  await http.delete(`${BASE_URL}/${id}`)
}

// ===== 用例分类 API =====

/**
 * 获取分类树 — GET /api/case/categories/tree?tenantId=xxx
 */
export async function fetchCategoryTree(tenantId: number | string): Promise<CaseCategory[]> {
  const res = await http.get<ApiResponse<CaseCategory[]>>(`${CATEGORY_URL}/tree`, {
    params: { tenantId }
  })
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '获取分类树失败')
}

/**
 * 获取平铺列表 — GET /api/case/categories?tenantId=xxx
 */
export async function fetchCategoryList(tenantId: number | string): Promise<CaseCategory[]> {
  const res = await http.get<ApiResponse<CaseCategory[]>>(CATEGORY_URL, {
    params: { tenantId }
  })
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '获取分类列表失败')
}

/**
 * 获取子分类 — GET /api/case/categories?parentId=xxx
 */
export async function fetchChildCategories(parentId: number | string): Promise<CaseCategory[]> {
  const res = await http.get<ApiResponse<CaseCategory[]>>(CATEGORY_URL, {
    params: { parentId }
  })
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '获取子分类失败')
}

/**
 * 获取分类详情 — GET /api/case/categories/{id}
 */
export async function fetchCategoryById(id: number | string): Promise<CaseCategory> {
  const res = await http.get<ApiResponse<CaseCategory>>(`${CATEGORY_URL}/${id}`)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '获取分类详情失败')
}

/**
 * 创建分类 — POST /api/case/categories
 */
export async function createCategory(params: CreateCaseCategoryParams): Promise<CaseCategory> {
  const res = await http.post<ApiResponse<CaseCategory>>(CATEGORY_URL, params)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '创建分类失败')
}

/**
 * 更新分类 — PUT /api/case/categories/{id}
 */
export async function updateCategory(params: UpdateCaseCategoryParams): Promise<CaseCategory> {
  const { id, ...rest } = params
  const res = await http.put<ApiResponse<CaseCategory>>(`${CATEGORY_URL}/${id}`, rest)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '更新分类失败')
}

/**
 * 删除分类 — DELETE /api/case/categories/{id}
 */
export async function deleteCategory(id: number | string): Promise<void> {
  const res = await http.delete<ApiResponse<null>>(`${CATEGORY_URL}/${id}`)
  if (res.data.code !== 200) {
    throw new Error(res.data.message || '删除分类失败')
  }
}

/**
 * 将后端分类树转为 el-tree 所需格式（保留 code/description 方便回显）
 */
export function toTreeNodes(categories: CaseCategory[]): CaseTreeNode[] {
  return categories.map(cat => {
    const node: CaseTreeNode = {
      id: String(cat.id),
      label: cat.name,
      code: (cat as any).code || '',
      description: (cat as any).description || '',
    }
    // 只有当后端明确返回了非空 children 数组时，才递归构建子节点；
    // 空数组或 undefined/null 都不设置 children，这样 el-tree 才能正确识别叶子节点
    if (cat.children && cat.children.length > 0) {
      node.children = toTreeNodes(cat.children)
    }
    return node
  })
}
