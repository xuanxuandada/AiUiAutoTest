/** 批次列表项 */
export interface BatchItem {
  batchId: string
  overallStatus: 'PASSED' | 'FAILED' | 'RUNNING' | 'PENDING'
  total: number
  passed: number
  failed: number
  running: number
  pending: number
  error: number
  createTime?: string
  /** 创建人（后端回填用户名） */
  creator?: string
  /** 持续时间(毫秒)，后端可能以字符串下发（Long 转 String 保精度） */
  durationMs?: number | string
}

/** 单条用例执行记录 */
export interface CaseExecution {
  /** 雪花 ID，后端以字符串下发，禁止 Number() 转换（19 位会丢精度） */
  id: string | number
  testCaseId: string | number
  status: 'PASSED' | 'FAILED' | 'RUNNING' | 'PENDING' | 'ERROR'
  reportUrl?: string
  durationMs: number
  errorMessage?: string
}

/**
 * AI 诊断结果 — GET /api/executions/{id}/ai-analysis
 * 注意：失败时 HTTP 仍是 200，错误信息在 body.code/message 里
 */
export interface AiAnalysisResult {
  /** 执行记录 ID（字符串，别转 Number） */
  executionId: string
  /** false = 本次真的调了模型；true = 回放上次结果，没消费额度 */
  cached: boolean
  model: string
  /** ISO 风格，如 2026-08-29T15:03:11.482，展示前需格式化 */
  analyzedAt: string
  /** 送去分析的报错原文 */
  errorMessage: string
  /** 三段式纯文本：【报错含义】【可能原因】【解决建议】，含换行符 */
  analysis: string
}

/** 批次详情 */
export interface BatchDetail extends BatchItem {
  executions: CaseExecution[]
  /** testCaseId -> 用例名称 */
  caseNames?: Record<string, string>
  /**
   * executionId -> 已持久化的 AI 诊断结果（只有诊断过的记录才有这个 key）。
   * 用来在刷新页面后直接回放上次结论，不重复花用户额度。
   */
  aiAnalysis?: Record<string, AiAnalysisResult>
}
