import http from '../../shared/utils/http'
import { normalizePage } from '../../shared/utils'
import type { PageResult } from '../../shared/types'
import type { SuiteRow, SuiteRequest } from './types'

const BASE_URL = '/api/suites'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/** 套件列表查询参数 */
export interface SuiteListParams {
  /** 按套件名称模糊匹配（纯数字时也按 ID 精确匹配） */
  keyword?: string
  /** 按套件 ID 精确匹配，与 keyword 同时传时为 AND */
  id?: string | number
}

/**
 * 获取测试套件列表 — GET /api/suites?keyword=&id=&page=1&pageSize=20
 * 后端已改为分页：数据在 data.records，总数在 data.total
 */
export async function fetchSuiteList(page = 1, pageSize = 20, params: SuiteListParams = {}): Promise<PageResult<any>> {
  const { keyword, id } = params
  const res = await http.get<ApiResponse<PageResult<any> | any[]>>(BASE_URL, {
    params: { page, pageSize, keyword, id }
  })
  if (res.data.code === 200) return normalizePage<any>(res.data.data)
  throw new Error(res.data.message || '获取套件列表失败')
}

/**
 * 获取套件详情 — GET /api/suites/{id}
 */
export async function fetchSuiteById(id: number | string): Promise<any> {
  const res = await http.get<ApiResponse<any>>(`${BASE_URL}/${id}`)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '获取套件详情失败')
}

/**
 * 创建套件 — POST /api/suites
 */
export async function createSuite(payload: SuiteRequest): Promise<any> {
  const res = await http.post<ApiResponse<any>>(BASE_URL, payload)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '创建套件失败')
}

/**
 * 更新套件 — PUT /api/suites/{id}
 */
export async function updateSuite(id: number | string, payload: SuiteRequest): Promise<any> {
  const res = await http.put<ApiResponse<any>>(`${BASE_URL}/${id}`, payload)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '更新套件失败')
}

/**
 * 查询套件下用例详情（合并副本覆盖） — GET /api/suites/{suiteId}/cases
 * 每项包含 caseId/name/targetUrl/priority/categoryName/steps/expectedResult/hasCopy/copyStatus
 */
export async function fetchSuiteCases(suiteId: number | string): Promise<any[]> {
  const res = await http.get<ApiResponse<any[]>>(`${BASE_URL}/${suiteId}/cases`)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '获取套件用例失败')
}

/**
 * 更新套件内单个用例副本（触发 AI 转换） — PUT /api/suites/{suiteId}/cases/{caseId}
 * payload: { steps: [{ step }] | null, expectedResult: string | null }
 * - steps 传自然语言步骤（无 action）→ 后端异步 AI 转换
 * - steps 传 Midscene 格式（含 action）→ 直接保存，不调 AI
 * - steps 传 null → 清空副本，恢复继承父用例
 */
export async function updateSuiteCaseCopy(
  suiteId: number | string,
  caseId: number | string,
  payload: { steps?: { step: string }[] | null; expectedResult?: string | null }
): Promise<any> {
  const res = await http.put<ApiResponse<any>>(`${BASE_URL}/${suiteId}/cases/${caseId}`, payload)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '保存用例副本失败')
}

/**
 * 从套件移除单个用例（删除关联记录 + 套件副本，不影响父用例） — DELETE /api/suites/{suiteId}/cases/{caseId}
 */
export async function removeSuiteCase(suiteId: number | string, caseId: number | string): Promise<any> {
  const res = await http.delete<ApiResponse<any>>(`${BASE_URL}/${suiteId}/cases/${caseId}`)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '移除用例失败')
}

/**
 * 保存登录态提取命令 — PUT /api/suites/loginFetchCommand/{id}
 * body: { "logintext": "登录态提取命令内容", "loginEnabled": 1 }
 */
export async function saveLoginFetchCommand(id: number | string, logintext: string, loginEnabled: number): Promise<any> {
  const res = await http.put<ApiResponse<any>>(`${BASE_URL}/loginFetchCommand/${id}`, { logintext, loginEnabled })
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '保存登录态失败')
}

/**
 * 启动套件 — POST /api/executions/suite/{id}
 */
export async function launchSuite(id: number | string): Promise<any> {
  const res = await http.post<ApiResponse<any>>(`/api/executions/suite/${id}`)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '启动失败')
}
