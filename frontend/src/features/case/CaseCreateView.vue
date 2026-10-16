<template>
  <div class="card case-card full-screen-table">
    <div class="case-list-header">
      <div class="case-list-title">新建用例</div>
      <div style="display:flex;align-items:center;gap:12px;">
        <div class="mode-switch-inline">
          <span style="font-size:13px;color:#909399;">步骤模式</span>
          <el-button
            size="small"
            :type="stepMode === 'single' ? 'primary' : 'default'"
            @click="switchMode('single')"
          >单行</el-button>
          <el-button
            size="small"
            :type="stepMode === 'multi' ? 'primary' : 'default'"
            @click="switchMode('multi')"
          >多行</el-button>
        </div>
        <el-button @click="goBack">返回用例列表</el-button>
      </div>
    </div>

    <div class="case-table">
      <div class="case-table-row case-table-head">
        <span>#</span>
        <span><span class="required-star">*</span>前置条件</span>
        <span><span class="required-star">*</span>用例名称</span>
        <span>分类</span>
        <span>级别</span>
        <span><span class="required-star">*</span>步骤</span>
        <span>预期</span>
        <span>备注</span>
        <span></span>
      </div>

      <div class="case-table-row" v-for="(row, index) in pagedCaseRows" :key="row.id">
        <span class="row-index">{{ index + 1 }}</span>
        <div>
          <el-input
            v-model="row.precondition"
            placeholder="请输入网址"
            size="small"
            class="mt-1"
            @blur="handlePreconditionBlur(row.id)"
            :class="{ 'is-error': invalidRows[row.id]?.precondition }"
          />
          <div class="error-hint" v-if="invalidRows[row.id]?.precondition">必填</div>
        </div>
        <div>
          <el-input
            v-model="row.caseName"
            placeholder="用例名称"
            size="small"
            @blur="validateRowField(row.id, 'caseName')"
            :class="{ 'is-error': invalidRows[row.id]?.caseName }"
          />
          <div class="error-hint" v-if="invalidRows[row.id]?.caseName">必填</div>
        </div>
        <div>
          <el-tree-select
            v-model="row.categoryId"
            :data="categoryTreeData"
            :props="{ label: 'label', value: 'id', children: 'children', disabled: (data, node) => node ? !node.isLeaf : false }"
            node-key="id"
            placeholder="选择"
            clearable
            check-strictly
            :render-after-expand="false"
            size="small"
            style="width:100%"
            @change="(val) => onCategoryChange(row, val)"
          />
        </div>
        <div>
          <el-select v-model="row.priority" placeholder="-" size="small" clearable style="width:100%">
            <el-option value="P0" label="P0" />
            <el-option value="P1" label="P1" />
            <el-option value="P2" label="P2" />
            <el-option value="P3" label="P3" />
          </el-select>
        </div>
        <div class="step-cell-wrapper">
          <div v-if="stepMode === 'multi'" :class="['steps-cell', { 'invalid-group': invalidRows[row.id]?.steps }]">
            <div class="step-item" v-for="(step, stepIndex) in row.steps" :key="stepIndex">
              <div class="step-item-header">
                <span class="step-item-label">{{ stepIndex + 1 }}</span>
                <el-button
                  size="small"
                  circle
                  :disabled="row.steps.length <= 1"
                  @click.prevent="removeStepLine(row.id, stepIndex)"
                  style="width:20px;height:20px;min-height:20px;padding:0;font-size:12px;"
                >×</el-button>
              </div>
              <el-input
                v-model="row.steps[stepIndex]"
                type="textarea"
                placeholder="步骤内容"
                :rows="2"
                @blur="validateRowSteps(row.id, stepIndex)"
                :class="{ 'is-error': invalidRows[row.id]?.steps?.[stepIndex] }"
              />
            </div>
            <el-button size="small" @click.prevent="addStepLine(row.id)">+ 步骤</el-button>
          </div>
          <div v-else :class="['single-step-box', { 'invalid-group': invalidRows[row.id]?.steps }]">
            <el-input
              :model-value="row.steps.join('\n')"
              type="textarea"
              placeholder="每行一个步骤"
              :rows="3"
              @input="updateStepsText(row.id, $event)"
              @blur="validateRowSteps(row.id)"
            />
            <div class="error-hint" v-if="invalidRows[row.id]?.steps">请输入步骤</div>
          </div>
        </div>
        <div class="expected-cell-wrapper">
          <div v-if="stepMode === 'multi'" class="steps-cell">
            <div class="step-item" v-for="(_exp, stepIndex) in row.expected" :key="stepIndex">
              <span class="step-item-label">{{ stepIndex + 1 }}</span>
              <el-input
                v-model="row.expected[stepIndex]"
                type="textarea"
                placeholder="预期结果"
                :rows="2"
              />
            </div>
          </div>
          <div v-else class="single-step-box">
            <el-input
              :model-value="row.expected.join('\n')"
              type="textarea"
              placeholder="每行一个预期"
              :rows="2"
              @input="updateExpectedText(row.id, $event)"
            />
          </div>
        </div>
        <div>
          <el-input v-model="row.remarks" placeholder="备注" size="small" />
        </div>
        <div class="row-actions">
          <el-button
            size="small"
            circle
            :disabled="index === 0"
            @click.prevent="copyPreviousRow(row.id)"
            title="复制上一行"
            style="width:24px;height:24px;min-height:24px;padding:0;display:inline-flex;align-items:center;justify-content:center;"
          >复</el-button>
          <el-button
            size="small"
            circle
            type="danger"
            @click="removeRow(row.id)"
            title="删除此行"
            style="width:24px;height:24px;min-height:24px;padding:0;display:inline-flex;align-items:center;justify-content:center;"
          >删</el-button>
        </div>
      </div>
    </div>

    <div class="pagination-row">
      <PaginationControl
        :total-items="totalItems"
        v-model:current-page="currentPage"
        v-model:page-size="localPageSize"
        :page-sizes="pageSizes"
      />
    </div>

    <div class="form-actions">
      <el-button @click="addRow">＋ 添加行</el-button>
      <el-button type="primary" @click="handleSave">保存用例</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useCaseStore } from './caseStore'
import { fetchCategoryTree, toTreeNodes } from './api'
import PaginationControl from '../../shared/components/PaginationControl.vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const store = useCaseStore()

// ============ 直接引用 store 状态 ============
const caseRows = store.caseRows
const stepMode = store.stepMode

// ============ 用例分类树数据 ============
const categoryTreeData = ref([])

function getTenantId() {
  try {
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    return user.tenantId || ''
  } catch { return '' }
}

async function loadCategories() {
  const tid = getTenantId()
  if (!tid) return
  try {
    const categories = await fetchCategoryTree(tid)
    categoryTreeData.value = toTreeNodes(categories)
  } catch { /* 降级 */ }
}

// 页面加载时预加载分类数据，确保下拉打开时数据已就绪（popper 才能正确计算高度）
loadCategories()

/** 从树形数据中递归查找分类名称 */
function findCategoryName(nodes, id) {
  for (const node of nodes) {
    if (node.id === id) return node.label
    if (node.children) {
      const found = findCategoryName(node.children, id)
      if (found) return found
    }
  }
  return ''
}

/** 树选择器变化时更新 categoryName */
function onCategoryChange(row, val) {
  row.categoryName = val ? findCategoryName(categoryTreeData.value, val) : ''
}

// ============ 本地状态（表单相关） ============
const invalidRows = ref({})

// ============ 本地分页 ============
const pageSizes = [5, 10, 20, 30, 50]
const localPageSize = ref(10)
const currentPage = ref(1)

const totalItems = computed(() => caseRows.value.length)
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / localPageSize.value)))
const pagedCaseRows = computed(() => {
  const start = (currentPage.value - 1) * localPageSize.value
  return caseRows.value.slice(start, start + localPageSize.value)
})

// ============ 导航 ============
function goBack() {
  router.push('/cases')
}

// ============ 模式切换 ============
function switchMode(mode) {
  if (mode === stepMode.value) return
  store.stepMode.value = mode

  // 清除错误和表单
  invalidRows.value = {}

  caseRows.value.forEach((row) => {
    store.updateStepsText(row.id, '')
    store.updateExpectedText(row.id, '')
    store.updateCaseRow(row.id, 'caseName', '')
    store.updateCaseRow(row.id, 'precondition', '')
    store.updateCaseRow(row.id, 'priority', '')
    store.updateCaseRow(row.id, 'remarks', '')
  })
}

// ============ Store 方法代理 ============
function addRow() { store.addRow() }
function removeRow(id) { store.removeRow(id) }
function addStepLine(rowId) { store.addStepLine(rowId) }
function removeStepLine(rowId, stepIndex) {
  const rowErrors = { ...(invalidRows.value[rowId] || {}) }
  if (rowErrors.steps && typeof rowErrors.steps === 'object') {
    const nextSteps = {}
    Object.keys(rowErrors.steps).forEach((index) => {
      const idx = Number(index)
      if (idx === stepIndex) return
      if (idx > stepIndex) {
        nextSteps[idx - 1] = true
      } else {
        nextSteps[idx] = true
      }
    })
    setInvalidRowField(rowId, 'steps', nextSteps)
  }
  store.removeStepLine(rowId, stepIndex)
}
function copyPreviousRow(rowId) { store.copyPreviousRow(rowId) }
function updateCaseRow(rowId, field, value) { store.updateCaseRow(rowId, field, value) }
function updateStepLine(rowId, stepIndex, value) {
  store.updateStepLine(rowId, stepIndex, value)
  if (invalidRows.value[rowId]?.steps) {
    validateRowSteps(rowId, stepIndex)
  }
}
function updateStepsText(rowId, value) {
  store.updateStepsText(rowId, value)
  if (invalidRows.value[rowId]?.steps) {
    validateRowSteps(rowId)
  }
}
function updateExpectedLine(rowId, stepIndex, value) {
  store.updateExpectedLine(rowId, stepIndex, value)
}
function updateExpectedText(rowId, value) {
  store.updateExpectedText(rowId, value)
}

// ============ 验证逻辑 ============
function validateRowField(rowId, field) {
  const row = caseRows.value.find((item) => item.id === rowId)
  if (!row) return
  const invalid = !row[field]?.trim()
  setInvalidRowField(rowId, field, invalid)
}

/** 前置条件 URL 协议检查 */
function handlePreconditionBlur(rowId) {
  validateRowField(rowId, 'precondition')
  const row = caseRows.value.find((item) => item.id === rowId)
  if (!row) return
  const val = row.precondition?.trim()
  if (!val) return
  // 已带 http:// 或 https:// 不提醒
  if (/^https?:\/\//i.test(val)) return
  // 看起来像裸域名（含点且无空格）→ 提醒
  if (val.includes('.') && !/\s/.test(val)) {
    ElMessage.warning(`建议补充协议头，如：https://${val}`)
  }
}

function setInvalidRowField(rowId, field, invalid) {
  const nextRows = { ...invalidRows.value }
  const rowErrors = { ...(nextRows[rowId] || {}) }

  if (typeof invalid === 'object') {
    if (Object.keys(invalid).length) {
      rowErrors[field] = invalid
    } else {
      delete rowErrors[field]
    }
  } else if (invalid) {
    rowErrors[field] = true
  } else {
    delete rowErrors[field]
  }

  if (Object.keys(rowErrors).length) {
    nextRows[rowId] = rowErrors
  } else {
    delete nextRows[rowId]
  }
  invalidRows.value = nextRows
}

function validateRowSteps(rowId, stepIndex = null) {
  const row = caseRows.value.find((item) => item.id === rowId)
  if (!row) return

  if (stepMode.value === 'multi') {
    const invalidSteps = invalidRows.value[rowId]?.steps ? { ...invalidRows.value[rowId].steps } : {}
    if (stepIndex !== null) {
      if (!row.steps[stepIndex]?.trim()) {
        invalidSteps[stepIndex] = true
      } else {
        delete invalidSteps[stepIndex]
      }
    } else {
      row.steps.forEach((step, index) => {
        if (!step.trim()) {
          invalidSteps[index] = true
        } else {
          delete invalidSteps[index]
        }
      })
    }
    setInvalidRowField(rowId, 'steps', invalidSteps)
  } else {
    const invalid = !row.steps.some((step) => step.trim())
    setInvalidRowField(rowId, 'steps', invalid)
  }
}

// ============ 保存 ============
function handleSave() {
  const errors = []
  const _invalidRows = {}

  caseRows.value.forEach((row, index) => {
    const rowErrors = {}
    if (!row.caseName.trim()) {
      rowErrors.caseName = true
      errors.push(`第 ${index + 1} 行：用例名称不能为空`)
    }    if (!row.precondition?.trim()) {
      rowErrors.precondition = true
      errors.push(`第 ${index + 1} 行：前置条件不能为空`)
    }    const invalidSteps = {}
    row.steps.forEach((step, stepIndex) => {
      if (!step.trim()) {
        invalidSteps[stepIndex] = true
      }
    })
    if (Object.keys(invalidSteps).length) {
      rowErrors.steps = invalidSteps
      errors.push(`第 ${index + 1} 行：步骤不能为空`)
    }
    if (Object.keys(rowErrors).length) {
      _invalidRows[row.id] = rowErrors
    }
  })

  invalidRows.value = _invalidRows
  if (errors.length) {
    alert(errors.join('\n'))
    return
  }

  invalidRows.value = {}
  store.saveCases().then(() => {
    goBack()
  })
}

// ============ 分页 ============
function changePage(page) {
  if (page < 1 || page > totalPages.value) return
  currentPage.value = page
}

</script>

<style scoped>

.case-table {
  width: 100%;
  overflow: auto;
  flex: 1;
  margin-bottom: 12px;
}
.case-table-row {
  display: grid;
  grid-template-columns: 28px 1fr 1.2fr 120px 60px 2fr 1.5fr 1fr 56px;
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
.case-table-row > div:has(.el-tree-select),
.case-table-row > div:has(.el-select) {
  overflow: visible;
}

.row-index {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding-top: 6px;
}

/* 去掉输入框/文本域的多余线框 */
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

.step-cell-wrapper { grid-column: 6 / 7; }
.expected-cell-wrapper { grid-column: 7 / 8; }

.steps-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.step-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.step-item-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.step-item-label {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

.single-step-box {
  display: flex;
  flex-direction: column;
}

.row-actions {
  display: flex;
  gap: 2px;
  align-items: center;
  padding-top: 4px;
}
.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.error-hint {
  font-size: 12px;
  color: #f56c6c;
  margin-top: 2px;
}

.mb-1 { margin-bottom: 4px; }

.pagination-row {
  margin-bottom: 12px;
}

.form-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.mode-switch-inline {
  display: flex;
  align-items: center;
  gap: 6px;
}

.required-star {
  color: #f56c6c;
  margin-right: 1px;
}
</style>
