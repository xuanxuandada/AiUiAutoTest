<template>
  <div class="card case-card full-screen-table">
    <div class="case-list-header">
      <div class="case-list-title">测试结果</div>
    </div>

    <div class="table-placeholder">
      <div class="table-header">
        <span>序号</span>
        <span>批次ID</span>
        <span>总用例</span>
        <span>成功</span>
        <span>失败</span>
        <span>运行中</span>
        <span>待执行</span>
        <span>异常</span>
        <span>创建人</span>
        <span>持续时间</span>
        <span>创建时间</span>
        <span>状态</span>
        <span>操作</span>
      </div>
      <div v-if="loading" class="table-empty">加载中...</div>
      <div v-else-if="batches.length === 0" class="table-empty">暂无测试批次</div>
      <div class="table-row" v-for="(item, index) in batches" :key="item.batchId">
        <span>{{ (currentPage - 1) * pageSize + index + 1 }}</span>
        <span :title="item.batchId">{{ item.batchId }}</span>
        <span>{{ item.total }}</span>
        <span class="text-pass">{{ item.passed }}</span>
        <span class="text-fail">{{ item.failed }}</span>
        <span class="text-running">{{ item.running }}</span>
        <span>{{ item.pending }}</span>
        <span>{{ item.error }}</span>
        <span>{{ item.creator || '—' }}</span>
        <span>{{ formatDuration(item.durationMs) }}</span>
        <span>{{ formatTime(item.createTime) }}</span>
        <span>
          <el-tag :type="statusTagType(item.overallStatus)" size="small">{{ statusLabel(item.overallStatus) }}</el-tag>
        </span>
        <span>
          <el-button type="primary" link size="small" :disabled="item.overallStatus === 'RUNNING'" @click="goToDetail(item.batchId)">详情</el-button>
        </span>
      </div>
    </div>

    <PaginationControl
      v-if="total > 0"
      :total-items="total"
      :current-page="currentPage"
      :page-size="pageSize"
      :page-sizes="[5, 10, 20, 50]"
      @update:currentPage="changePage"
      @update:pageSize="updatePageSize"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PaginationControl from '../../shared/components/PaginationControl.vue'
import { fetchBatches } from './api'
import type { BatchItem } from './types'

const router = useRouter()

/** 当前页数据（后端分页，前端不再本地切片） */
const batches = ref<BatchItem[]>([])
/** 后端返回的批次总数 */
const total = ref(0)
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)

async function loadBatches() {
  loading.value = true
  try {
    const res = await fetchBatches(currentPage.value, pageSize.value)
    batches.value = res.records
    total.value = res.total
  } catch {
    batches.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadBatches()
})

function changePage(page: number) {
  currentPage.value = page
  loadBatches()
}

function updatePageSize(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadBatches()
}

function goToDetail(batchId: string) {
  router.push(`/reports/${encodeURIComponent(batchId)}`)
}

function statusLabel(status: string) {
  const map: Record<string, string> = { PASSED: '通过', FAILED: '失败', RUNNING: '运行中', PENDING: '待执行' }
  return map[status] || status
}

function statusTagType(status: string) {
  const map: Record<string, string> = { PASSED: 'success', FAILED: 'danger', RUNNING: 'warning', PENDING: 'info' }
  return map[status] || 'info'
}

function formatTime(dateStr?: string) {
  if (!dateStr) return '—'
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return dateStr
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/** 持续时间格式化：1234 → 1s，65000 → 1m5s */
function formatDuration(ms?: number | string) {
  if (ms === null || ms === undefined || ms === '') return '—'
  const value = Number(ms)
  if (!Number.isFinite(value) || value < 0) return '—'
  if (value < 1000) return `${value}ms`
  const seconds = Math.floor(value / 1000)
  if (seconds < 60) return `${seconds}s`
  return `${Math.floor(seconds / 60)}m${seconds % 60}s`
}
</script>

<style scoped>
.text-pass { color: #16a34a; font-weight: 600; }
.text-fail { color: #dc2626; font-weight: 600; }
.text-running { color: #d97706; font-weight: 600; }

.table-header,
.table-row {
  display: grid;
  grid-template-columns: 50px minmax(150px, 2fr) 70px 60px 60px 60px 60px 60px 90px 90px 130px 80px 60px;
  gap: 10px;
  align-items: center;
  padding: 10px 12px;
}
.table-header {
  font-weight: 700;
  color: #111827;
  border-bottom: 1px solid #eee;
  padding: 12px 12px 8px;
}
.table-row {
  margin-top: 8px;
}
.table-row > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.table-row > span:last-child {
  overflow: visible;
  white-space: nowrap;
}
.table-empty {
  padding: 32px;
  text-align: center;
  color: #9ca3af;
  font-size: 14px;
}
</style>
