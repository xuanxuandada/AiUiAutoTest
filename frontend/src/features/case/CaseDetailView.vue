<template>
  <div class="card case-card full-screen-table">
    <div class="case-list-header">
      <div class="case-list-title">编辑用例</div>
      <div style="display:flex;align-items:center;gap:12px;">
        <el-button @click="goBack">返回用例列表</el-button>
      </div>
    </div>
    <div v-if="notFound" class="table-empty">用例不存在</div>
    <div v-else-if="caseData.id" class="case-table">
      <!-- 表头（模仿新建用例横向样式） -->
      <div class="case-table-row case-table-head">
        <span>ID</span>
        <span><span class="required-star">*</span>前置条件</span>
        <span><span class="required-star">*</span>用例名称</span>
        <span>级别</span>
        <span>步骤</span>
        <span>预期</span>
        <span>备注</span>
      </div>

      <!-- 横向编辑行 -->
      <div class="case-table-row case-table-edit">
        <span class="row-index" :title="caseData.id">{{ caseData.id }}</span>
        <div>
          <el-input
            v-model="caseData.precondition"
            placeholder="请输入网址"
            size="small"
            :disabled="isReadonly"
            @blur="checkPreconditionUrl"
          />
        </div>
        <div>
          <el-input
            v-model="caseData.title"
            placeholder="用例名称"
            size="small"
            :disabled="isReadonly"
          />
        </div>
        <div>
          <el-select v-model="caseData.priority" placeholder="-" size="small" clearable style="width:100%" :disabled="isReadonly">
            <el-option value="P0" label="P0" />
            <el-option value="P1" label="P1" />
            <el-option value="P2" label="P2" />
            <el-option value="P3" label="P3" />
          </el-select>
        </div>
        <div>
          <el-input
            v-model="caseData.steps"
            type="textarea"
            placeholder="每行一个步骤"
            :rows="5"
            size="small"
            :disabled="isReadonly"
          />
        </div>
        <div>
          <el-input
            v-model="caseData.expected"
            type="textarea"
            placeholder="每行一个预期"
            :rows="5"
            size="small"
            :disabled="isReadonly"
          />
        </div>
        <div>
          <el-input
            v-model="caseData.remark"
            placeholder="备注"
            size="small"
            :disabled="isReadonly"
          />
        </div>
      </div>

      <div class="form-actions">
        <el-button @click="goBack">返回</el-button>
        <el-button v-if="!isReadonly" type="primary" @click="handleSave">保存</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { fetchCaseById, updateCase } from './api'
import { parseNumberedSteps } from '../../shared/utils'
import { useCaseStore } from './caseStore'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const { loadCaseList } = useCaseStore()

const notFound = ref(false)
const isReadonly = ref(route.query.readonly === '1')
const caseData = ref({
  id: '' as number | string,
  title: '',
  precondition: '',
  steps: '',
  expected: '',
  remark: '',
  priority: ''
})

onMounted(async () => {
  const id = route.params.id as string
  try {
    const detail = await fetchCaseById(id)
    caseData.value = {
      id: String(detail.id),
      title: detail.name || '',
      precondition: detail.targetUrl || '',
      steps: Array.isArray(detail.steps)
        ? detail.steps.map((s: any) => s.step || '').join('\n')
        : '',
      expected: Array.isArray(detail.steps)
        ? detail.steps.map((s: any) => s.expected || '').join('\n')
        : '',
      remark: '',
      priority: detail.priority != null ? `P${detail.priority}` : ''
    }
  } catch {
    notFound.value = true
  }
})

function goBack() {
  router.push('/cases')
}

/** 前置条件 URL 协议检查 */
function checkPreconditionUrl() {
  const val = caseData.value.precondition?.trim()
  if (!val) return
  if (/^https?:\/\//i.test(val)) return
  if (val.includes('.') && !/\s/.test(val)) {
    ElMessage.warning(`建议补充协议头，如：https://${val}`)
  }
}

async function handleSave() {
  if (!caseData.value.precondition?.trim()) {
    ElMessage.warning('前置条件不能为空')
    return
  }
  const id = route.params.id as string

  // 将 textarea 文本按数字编号对应生成步骤数组（无编号则按行对应）
  const steps = parseNumberedSteps(caseData.value.steps, caseData.value.expected)

  // 用例级文本（过滤空行），与创建接口的 expectedResult / originDescription 对齐
  const normalizedSteps = caseData.value.steps
    .split('\n')
    .map((s) => s.trim())
    .filter((s) => s)
    .join('\n')
  const normalizedExpected = caseData.value.expected
    .split('\n')
    .map((s) => s.trim())
    .filter((s) => s)
    .join('\n')

  const priorityNum = caseData.value.priority ? parseInt(caseData.value.priority.replace('P', '')) : 0

  try {
    await updateCase(id, {
      name: caseData.value.title,
      targetUrl: caseData.value.precondition,
      steps,
      priority: priorityNum,
      // 同步用例级字段，避免套件等页面读取 expectedResult 时仍是旧值
      expectedResult: normalizedExpected,
      originDescription: normalizedSteps + '\n' + normalizedExpected
    })
    // 先刷新模块级列表缓存，避免返回列表时短暂显示旧数据
    try {
      await loadCaseList()
    } catch {
      /* 刷新失败不影响跳转 */
    }
    ElMessage.success('保存成功')
    router.push('/cases')
  } catch {
    ElMessage.error('保存失败，请重试')
  }
}
</script>

<style scoped>
.case-table {
  width: 100%;
  overflow: auto;
  margin-bottom: 12px;
}
.case-table-row {
  display: grid;
  grid-template-columns: 120px 1.4fr 1.4fr 80px 2fr 1.6fr 1fr;
  gap: 4px;
  align-items: start;
  padding: 10px 4px;
}
.case-table-row + .case-table-row {
  border-top: 1px solid #f0f0f0;
}
.case-table-row.case-table-head {
  font-weight: 600;
  align-items: center;
  color: #909399;
  font-size: 13px;
  padding: 10px 4px 8px;
  border-bottom: 1px solid #eee;
}
.case-table-row > div {
  overflow: hidden;
}
.row-index {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding-top: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 去掉输入框/文本域的多余线框（模仿新建用例样式） */
.case-table-row :deep(.el-input__wrapper) {
  box-shadow: none;
  background: transparent;
  padding: 0 8px;
}
.case-table-row :deep(.el-textarea__inner) {
  border: 1px solid #e5e7eb;
  box-shadow: none;
  border-radius: 4px;
  background: #fff;
  padding: 4px 8px;
}

.form-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 12px;
}
.required-star {
  color: #f56c6c;
  margin-right: 1px;
}
.table-empty {
  padding: 32px;
  text-align: center;
  color: #9ca3af;
}
</style>
