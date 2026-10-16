<template>
  <div class="card case-card full-screen-table">
    <div class="case-list-header">
      <div class="case-list-title">{{ detail.batchId || '批次详情' }}</div>
      <button class="btn" @click="goBack">返回批次列表</button>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="table-empty">加载中...</div>

    <!-- 批次概览 -->
    <div v-else-if="detail.batchId" class="batch-summary">
      <div class="summary-card">
        <div class="summary-item">
          <span class="summary-label">批次ID</span>
          <span class="summary-value">{{ detail.batchId }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">状态</span>
          <el-tag :type="statusTagType(detail.overallStatus)" size="small">{{ statusLabel(detail.overallStatus) }}</el-tag>
        </div>
      </div>
      <div class="summary-card summary-stats">
        <div class="stat-item">
          <span class="stat-num">{{ detail.total }}</span>
          <span class="stat-label">总用例</span>
        </div>
        <div class="stat-item stat-pass">
          <span class="stat-num">{{ detail.passed }}</span>
          <span class="stat-label">成功</span>
        </div>
        <div class="stat-item stat-fail">
          <span class="stat-num">{{ detail.failed }}</span>
          <span class="stat-label">失败</span>
        </div>
        <div class="stat-item stat-running">
          <span class="stat-num">{{ detail.running }}</span>
          <span class="stat-label">运行中</span>
        </div>
        <div class="stat-item">
          <span class="stat-num">{{ detail.pending }}</span>
          <span class="stat-label">待执行</span>
        </div>
        <div class="stat-item stat-error">
          <span class="stat-num">{{ detail.error }}</span>
          <span class="stat-label">异常</span>
        </div>
      </div>
    </div>

    <!-- 用例执行结果 -->
    <div v-if="detail.batchId" class="results-section">
      <div class="results-tabs">
        <button
          :class="['tab-btn', { active: activeTab === 'all' }]"
          @click="activeTab = 'all'"
        >全部 ({{ detail.executions.length }})</button>
        <button
          :class="['tab-btn tab-pass', { active: activeTab === 'passed' }]"
          @click="activeTab = 'passed'"
        >成功 ({{ passedExecutions.length }})</button>
        <button
          :class="['tab-btn tab-fail', { active: activeTab === 'failed' }]"
          @click="activeTab = 'failed'"
        >失败 ({{ failedExecutions.length }})</button>
      </div>

      <div class="execution-list">
        <div
          v-for="exec in filteredExecutions"
          :key="exec.id"
          :class="['execution-card', exec.status.toLowerCase()]"
        >
          <div class="execution-header">
            <div class="execution-info">
              <span class="execution-id">#{{ exec.id }}</span>
              <span class="execution-case-id">用例ID: {{ exec.testCaseId }}</span>
              <span class="execution-case-name" :title="caseNameOf(exec.testCaseId)">用例名称: {{ caseNameOf(exec.testCaseId) }}</span>
              <span class="execution-duration">{{ formatDuration(exec.durationMs) }}</span>
            </div>
            <div class="execution-actions">
              <el-tag :type="execStatusTag(exec.status)" size="small">{{ execStatusLabel(exec.status) }}</el-tag>
              <el-button
                v-if="exec.reportUrl"
                type="primary"
                link
                size="small"
                @click="openReport(exec.reportUrl)"
              >查看报告</el-button>
              <!-- 已有缓存的记录：提前告诉用户再点一次是免费的（不会重复消耗额度） -->
              <el-tag
                v-if="hasCachedAnalysis(exec)"
                type="success"
                size="small"
                effect="plain"
                title="已有分析结果，再次点击直接回放，不消耗额度"
              >已诊断</el-tag>
              <!-- AI 诊断：只有「失败/异常且报错正文非空」才显示（后端其他情况会返回 400） -->
              <el-button
                v-if="canDiagnose(exec)"
                type="warning"
                link
                size="small"
                :loading="aiLoading && aiCurrentId === String(exec.id)"
                :disabled="aiLoading"
                @click="handleAiDiagnosis(exec)"
              >AI 诊断</el-button>
            </div>
          </div>
          <div v-if="exec.errorMessage" class="execution-error">
            <span class="error-label">错误信息：</span>{{ exec.errorMessage }}
          </div>
        </div>
        <div v-if="filteredExecutions.length === 0" class="table-empty">暂无数据</div>
      </div>
    </div>

    <!-- 未找到 -->
    <div v-else-if="!loading" class="table-empty">批次不存在</div>

    <!-- AI 诊断结果（与测试结果列表页共用同一弹窗） -->
    <AiDiagnosisDialog v-model="aiDialogVisible" :result="aiResult" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
// @ts-ignore - <script setup> 隐式导出，需 Volar Takeover 模式才能识别
import AiDiagnosisDialog from './AiDiagnosisDialog.vue'
import { useAiDiagnosis } from './useAiDiagnosis'
import { fetchBatchDetail } from './api'
import type { BatchDetail, CaseExecution, AiAnalysisResult } from './types'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const activeTab = ref<'all' | 'passed' | 'failed'>('all')

const detail = ref<BatchDetail>({
  batchId: '',
  overallStatus: 'PENDING',
  total: 0,
  passed: 0,
  failed: 0,
  running: 0,
  pending: 0,
  error: 0,
  executions: []
})

const passedExecutions = computed(() => detail.value.executions.filter(e => e.status === 'PASSED'))
const failedExecutions = computed(() => detail.value.executions.filter(e => e.status === 'FAILED' || e.status === 'ERROR'))

const filteredExecutions = computed<CaseExecution[]>(() => {
  if (activeTab.value === 'passed') return passedExecutions.value
  if (activeTab.value === 'failed') return failedExecutions.value
  return detail.value.executions
})

onMounted(async () => {
  const batchId = route.params.id as string
  loading.value = true
  try {
    detail.value = await fetchBatchDetail(decodeURIComponent(batchId))
  } catch {
    detail.value.batchId = ''
  } finally {
    loading.value = false
  }
})

function goBack() {
  router.push('/reports')
}

function openReport(url: string) {
  window.open(url, '_blank')
}

function formatDuration(ms: number) {
  if (!ms && ms !== 0) return '—'
  if (ms < 1000) return `${ms}ms`
  const seconds = Math.floor(ms / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remainSeconds = seconds % 60
  return `${minutes}m${remainSeconds}s`
}

/** 用例名称：后端在批次详情里下发了 testCaseId -> 名称 的映射 */
function caseNameOf(testCaseId: number | string) {
  return detail.value.caseNames?.[String(testCaseId)] || '—'
}

// ===== AI 诊断（GET /api/executions/{id}/ai-analysis）=====
// 交互逻辑（并发保护 / 缓存回放 / 错误提示）抽到 useAiDiagnosis，测试结果列表页复用同一套
const {
  visible: aiDialogVisible,
  loading: aiLoading,
  currentId: aiCurrentId,
  result: aiResult,
  diagnose: runAiDiagnosis
} = useAiDiagnosis((execId, data) => rememberAnalysis(execId, data))

/** 只有终态失败、且确实有报错正文的记录才值得诊断（否则后端返回 400） */
function canDiagnose(exec: CaseExecution) {
  return (exec.status === 'FAILED' || exec.status === 'ERROR') && !!exec.errorMessage
}

/** 该记录是否已有 AI 诊断结果（有 = 已持久化缓存，点击是回放、不花额度） */
function hasCachedAnalysis(exec: CaseExecution) {
  return !!detail.value.aiAnalysis?.[String(exec.id)]?.analysis
}

/** 点击诊断：命中本地缓存就直接回放，否则交给 useAiDiagnosis 去请求接口 */
function handleAiDiagnosis(exec: CaseExecution) {
  runAiDiagnosis(exec.id, buildCachedResult(exec))
}

/** 批次详情接口下发的历史分析结果 → 弹窗结构；没有则返回 null */
function buildCachedResult(exec: CaseExecution): AiAnalysisResult | null {
  const cached = detail.value.aiAnalysis?.[String(exec.id)]
  if (!cached?.analysis) return null
  return {
    executionId: String(exec.id),
    cached: true,
    model: cached.model || '',
    analyzedAt: cached.analyzedAt || '',
    errorMessage: cached.errorMessage || exec.errorMessage || '',
    analysis: cached.analysis
  }
}

/** 记住本次结果，同一页内再点就是秒开（无需再问后端） */
function rememberAnalysis(execId: string, data: AiAnalysisResult) {
  if (!detail.value.aiAnalysis) detail.value.aiAnalysis = {}
  detail.value.aiAnalysis[execId] = data
}

function statusLabel(status: string) {
  const map: Record<string, string> = { PASSED: '通过', FAILED: '失败', RUNNING: '运行中', PENDING: '待执行' }
  return map[status] || status
}

function statusTagType(status: string) {
  const map: Record<string, string> = { PASSED: 'success', FAILED: 'danger', RUNNING: 'warning', PENDING: 'info' }
  return map[status] || 'info'
}

function execStatusLabel(status: string) {
  const map: Record<string, string> = { PASSED: '通过', FAILED: '失败', RUNNING: '运行中', PENDING: '待执行', ERROR: '异常' }
  return map[status] || status
}

function execStatusTag(status: string) {
  const map: Record<string, string> = { PASSED: 'success', FAILED: 'danger', RUNNING: 'warning', PENDING: 'info', ERROR: 'danger' }
  return map[status] || 'info'
}
</script>

<style scoped>
/* 批次概览 */
.batch-summary {
  display: flex;
  gap: 20px;
  margin-bottom: 24px;
  flex-wrap: wrap;
}
.summary-card {
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 16px 20px;
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}
.summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.summary-label {
  font-size: 12px;
  color: #6b7280;
}
.summary-value {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}
.summary-stats {
  gap: 32px;
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.stat-num {
  font-size: 24px;
  font-weight: 700;
}
.stat-pass .stat-num { color: #16a34a; }
.stat-fail .stat-num { color: #dc2626; }
.stat-running .stat-num { color: #d97706; }
.stat-error .stat-num { color: #9333ea; }
.stat-label {
  font-size: 12px;
  color: #6b7280;
}

/* Tab 切换 */
.results-tabs {
  display: flex;
  gap: 4px;
  margin-bottom: 16px;
  border-bottom: 2px solid #e5e7eb;
  padding-bottom: 0;
}
.tab-btn {
  padding: 8px 18px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 14px;
  color: #6b7280;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: all 0.2s;
}
.tab-btn:hover { color: #374151; }
.tab-btn.active {
  color: #111827;
  font-weight: 600;
  border-bottom-color: #2563eb;
}
.tab-btn.tab-pass.active { border-bottom-color: #16a34a; color: #16a34a; }
.tab-btn.tab-fail.active { border-bottom-color: #dc2626; color: #dc2626; }

/* 用例执行卡片 */
.execution-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 14px 18px;
  margin-bottom: 10px;
}
.execution-card.failed,
.execution-card.error {
  border-left: 4px solid #dc2626;
}
.execution-card.passed {
  border-left: 4px solid #16a34a;
}
.execution-card.running {
  border-left: 4px solid #d97706;
}
.execution-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.execution-info {
  display: flex;
  gap: 16px;
  align-items: center;
  font-size: 14px;
}
.execution-id {
  font-weight: 600;
  color: #111827;
}
.execution-case-id {
  color: #6b7280;
}
.execution-case-name {
  color: #374151;
  font-weight: 500;
  max-width: 420px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.execution-duration {
  color: #9ca3af;
  font-size: 13px;
}
.execution-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.execution-error {
  margin-top: 10px;
  background: #fef2f2;
  border: 1px solid #fecaca;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 13px;
  color: #dc2626;
}
.error-label {
  font-weight: 600;
}

.table-empty {
  padding: 32px;
  text-align: center;
  color: #9ca3af;
  font-size: 14px;
}
.case-list-header { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; }
</style>
