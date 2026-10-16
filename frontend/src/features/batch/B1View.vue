<template>
  <div class="card case-card full-screen-table">
    <div class="case-list-header">
      <div class="case-list-title">测试报告 1</div>
    </div>

    <!-- 套件结果列表 -->
    <div class="section-title">套件执行结果</div>
    <div class="table-placeholder">
      <div class="table-header">
        <span>序号</span>
        <span>套件名称</span>
        <span>用例数</span>
        <span>通过</span>
        <span>失败</span>
        <span>跳过</span>
        <span>耗时</span>
        <span>状态</span>
        <span>操作</span>
      </div>
      <div class="table-row" v-for="(item, index) in suiteResults" :key="item.id">
        <span>{{ index + 1 }}</span>
        <span><a class="suite-link" @click="goToSuite(item.id)">{{ item.name }}</a></span>
        <span>{{ item.total }}</span>
        <span class="text-pass">{{ item.passed }}</span>
        <span class="text-fail">{{ item.failed }}</span>
        <span class="text-skip">{{ item.skipped || 0 }}</span>
        <span>{{ item.duration }}</span>
        <span>
          <el-tag :type="statusType(item.status)" size="small">{{ item.status }}</el-tag>
        </span>
        <span>
          <el-button type="primary" link size="small" @click="goToSuite(item.id)">详情</el-button>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import http from '../../shared/utils/http'

const router = useRouter()

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

const suiteResults = ref<any[]>([])

async function loadBatches() {
  try {
    const res = await http.get<ApiResponse<any[]>>('/api/executions/batches')
    if (res.data.code === 200) {
      suiteResults.value = (res.data.data || []).map((item: any) => ({
        id: item.id || item.suiteId,
        name: item.suiteName || item.name || '',
        total: item.totalCases ?? item.total ?? 0,
        passed: item.passedCases ?? item.passed ?? 0,
        failed: item.failedCases ?? item.failed ?? 0,
        skipped: item.skippedCases ?? item.skipped ?? 0,
        duration: item.duration || '—',
        status: item.status || '—'
      }))
    }
  } catch {
    suiteResults.value = []
  }
}

onMounted(() => {
  loadBatches()
})

function goToSuite(id: number | string) {
  router.push(`/suites/${id}/cases`)
}

function statusType(status: string) {
  if (status === '通过' || status === 'PASSED') return 'success'
  if (status === '部分失败' || status === 'PARTIAL') return 'warning'
  return 'danger'
}
</script>

<style scoped>
.section-title {
  font-size: 16px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 12px;
}

.table-header,
.table-row {
  grid-template-columns: minmax(140px, 2fr) 80px 70px 70px 70px 90px 100px 70px;
}
.text-pass { color: #16a34a; }
.text-fail { color: #dc2626; }
.text-skip { color: #9ca3af; }

.suite-link {
  color: #2563eb;
  cursor: pointer;
  text-decoration: none;
}
.suite-link:hover {
  text-decoration: underline;
  color: #1d4ed8;
}
</style>
