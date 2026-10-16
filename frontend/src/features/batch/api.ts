import http from '../../shared/utils/http'
import { normalizePage } from '../../shared/utils'
import type { PageResult } from '../../shared/types'
import type { BatchItem, BatchDetail, AiAnalysisResult } from './types'

const BASE_URL = '/api/executions/batches'
const DETAIL_URL = '/api/executions/batch'
const EXEC_URL = '/api/executions'

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/**
 * 获取批次列表 — GET /api/executions/batches?page=1&pageSize=20
 * 后端已改为分页：数据在 data.records；
 * 注意 data.total 是「批次总数」，每条记录里的 total 才是「该批次用例数」。
 */
export async function fetchBatches(page = 1, pageSize = 20): Promise<PageResult<BatchItem>> {
  const res = await http.get<ApiResponse<PageResult<BatchItem> | BatchItem[]>>(BASE_URL, {
    params: { page, pageSize }
  })
  if (res.data.code === 200) return normalizePage<BatchItem>(res.data.data)
  throw new Error(res.data.message || '获取批次列表失败')
}

/** 获取批次详情 — GET /api/executions/batch/{batchId} */
export async function fetchBatchDetail(batchId: string): Promise<BatchDetail> {
  const res = await http.get<ApiResponse<BatchDetail>>(`${DETAIL_URL}/${batchId}`)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '获取批次详情失败')
}

/** 获取执行报告 URL — GET /api/executions/{id}/report */
export async function fetchExecutionReportUrl(id: number | string): Promise<string> {
  const res = await http.get<ApiResponse<string>>(`${EXEC_URL}/${id}/report`)
  if (res.data.code === 200) return res.data.data
  throw new Error(res.data.message || '获取执行报告失败')
}

/**
 * AI 诊断 — GET /api/executions/{id}/ai-analysis
 *
 * 三个注意点（详见后端《前端AI诊断提示词》）：
 * 1. 首次会真的调大模型，耗时可能几秒到几十秒 → 单独放宽超时（后端默认 60s，用户可能配更大）
 * 2. 业务失败时 **HTTP 仍是 200**，错误只体现在 body.code → 这里手动抛异常让组件统一 catch
 * 3. id 是雪花 ID 字符串，直接拼进 URL，千万别先 Number()
 */
export async function fetchAiAnalysis(executionId: string | number): Promise<AiAnalysisResult> {
  const res = await http.get<ApiResponse<AiAnalysisResult>>(`${EXEC_URL}/${executionId}/ai-analysis`, {
    timeout: 120000
  })
  if (res.data.code === 200) return res.data.data
  const err = new Error(res.data.message || 'AI 诊断失败') as Error & { bizCode?: number }
  err.bizCode = res.data.code
  throw err
}
