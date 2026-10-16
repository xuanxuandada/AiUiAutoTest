import http from '../../shared/utils/http'
import type { CollectionRow, CollectionRequest } from './types'

const BASE_URL = '/api/collections'

/** API 统一响应结构 */
interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/**
 * 获取用例集列表 — GET /api/collections
 */
export async function fetchCollectionList(): Promise<CollectionRow[]> {
  const res = await http.get<ApiResponse<any[]>>(BASE_URL)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '获取用例集列表失败')
}

/**
 * 获取用例集详情 — GET /api/collections/{id}
 */
export async function fetchCollectionById(id: number | string): Promise<any> {
  const res = await http.get<ApiResponse<any>>(`${BASE_URL}/${id}`)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '获取用例集详情失败')
}

/**
 * 创建用例集 — POST /api/collections
 */
export async function createCollection(payload: CollectionRequest): Promise<any> {
  const res = await http.post<ApiResponse<any>>(BASE_URL, payload)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '创建用例集失败')
}

/**
 * 更新用例集 — PUT /api/collections/{id}
 */
export async function updateCollection(id: number | string, payload: CollectionRequest): Promise<any> {
  const res = await http.put<ApiResponse<any>>(`${BASE_URL}/${id}`, payload)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '更新用例集失败')
}

/**
 * 删除用例集 — DELETE /api/collections/{id}
 */
export async function deleteCollection(id: number | string): Promise<void> {
  const res = await http.delete<ApiResponse<null>>(`${BASE_URL}/${id}`)
  if (res.data.code === 200) return
  throw new Error(res.data.message || '删除用例集失败')
}

/**
 * 启动用例集 — POST /api/executions/collection/{id}
 */
export async function launchCollection(id: number | string): Promise<any> {
  const res = await http.post<ApiResponse<any>>(`/api/executions/collection/${id}`)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '启动失败')
}
