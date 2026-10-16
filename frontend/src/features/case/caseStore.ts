import { ref, computed } from 'vue'
import type { CaseRow, SavedCase, CaseTreeNode, CreateCaseRequest } from './types'
import { createCase as apiCreateCase, fetchCategoryTree, fetchCaseList, toTreeNodes } from './api'
import { parseNumberedSteps } from '../../shared/utils'

/** 创建一个空用例行 */
function createEmptyRow(id: number): CaseRow {
  return {
    id,
    caseName: '',
    categoryId: undefined,
    categoryName: '',
    precondition: '',
    steps: [''],
    expected: [''],
    remarks: '',
    priority: ''
  }
}

// ============ 辅助函数 ============
function getTenantId(): number | string {
  try {
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    return user.tenantId || ''
  } catch {
    return ''
  }
}

// ============ 模块级单例状态 ============
const caseRows = ref<CaseRow[]>([
  createEmptyRow(1)
])
const suiteName = ref('')
const headers = ref('')
const stepMode = ref<'single' | 'multi'>('single')
const suiteOptions = ref<string[]>(['基础套件', '回归套件', '冒烟套件'])
const caseListRows = ref<SavedCase[]>([])
/** 服务端返回的用例总数（用于分页控件「共 X 条」） */
const caseTotal = ref(0)

// ============ 树形分类（从 API 加载） ============
const caseTreeData = ref<CaseTreeNode[]>([])
const treeLoading = ref(false)

async function loadCategoryTree() {
  const tid = getTenantId()
  if (!tid) return
  treeLoading.value = true
  try {
    const categories = await fetchCategoryTree(tid)
    caseTreeData.value = toTreeNodes(categories)
  } catch {
    // 加载失败时保留旧数据，不覆盖
  } finally {
    treeLoading.value = false
  }
}

/**
 * 加载用例列表（服务端分页）
 * @param page 页码，从 1 开始
 * @param pageSize 每页条数
 */
async function loadCaseList(page = 1, pageSize = 20) {
  try {
    const { records, total } = await fetchCaseList({ page, pageSize })
    caseTotal.value = total
    caseListRows.value = records.map((item: any) => ({
      id: String(item.id),
      title: item.name || '',
      precondition: item.targetUrl || '',
      steps: Array.isArray(item.steps)
        ? item.steps.map((s: any) => s.step || '').join('\n')
        : '',
      expected: Array.isArray(item.steps)
        ? item.steps.map((s: any) => s.expected || '').join('\n')
        : '',
      remark: '',
      priority: item.priority != null ? `P${item.priority}` : ''
    }))
  } catch {
    // 加载失败保持空列表
  }
}

/** 刷新分类树（供 CaseCategoryView 增删改后调用） */
async function refreshCategoryTree() {
  await loadCategoryTree()
}

// ============ 模块级单例方法 ============

function addRow() {
  const nextId = caseRows.value.length
    ? Math.max(...caseRows.value.map((r) => r.id)) + 1
    : 1
  caseRows.value.push(createEmptyRow(nextId))
}

function removeRow(id: number) {
  if (caseRows.value.length <= 1) return
  caseRows.value = caseRows.value.filter((row) => row.id !== id)
}

function addStepLine(rowId: number) {
  const row = caseRows.value.find((item) => item.id === rowId)
  if (row) {
    row.steps.push('')
    row.expected.push('')
  }
}

function removeStepLine(rowId: number, stepIndex: number) {
  const row = caseRows.value.find((item) => item.id === rowId)
  if (row) {
    row.steps = row.steps.filter((_, index) => index !== stepIndex)
    row.expected = row.expected.filter((_, index) => index !== stepIndex)
  }
}

function updateStepsText(rowId: number, value: string) {
  const row = caseRows.value.find((item) => item.id === rowId)
  if (row) {
    row.steps = value ? value.split('\n') : ['']
    // 同步 expected 行数（保持与 steps 一致）
    const lines = value ? value.split('\n').length : 1
    while (row.expected.length < lines) row.expected.push('')
    while (row.expected.length > lines) row.expected.pop()
  }
}

function updateCaseRow(rowId: number, field: keyof CaseRow, value: any) {
  const row = caseRows.value.find((item) => item.id === rowId)
  if (row && field in row) {
    (row as any)[field] = value
  }
}

function updateStepLine(rowId: number, stepIndex: number, value: string) {
  const row = caseRows.value.find((item) => item.id === rowId)
  if (row && row.steps[stepIndex] !== undefined) {
    row.steps.splice(stepIndex, 1, value)
  }
}

/** 多行模式下更新某一步的预期结果 */
function updateExpectedLine(rowId: number, stepIndex: number, value: string) {
  const row = caseRows.value.find((item) => item.id === rowId)
  if (row && row.expected[stepIndex] !== undefined) {
    row.expected.splice(stepIndex, 1, value)
  }
}

/** 单行模式下更新全部预期结果文本（按换行拆分） */
function updateExpectedText(rowId: number, value: string) {
  const row = caseRows.value.find((item) => item.id === rowId)
  if (row) {
    row.expected = value ? value.split('\n') : ['']
  }
}

function copyPreviousRow(id: number) {
  const index = caseRows.value.findIndex((row) => row.id === id)
  if (index > 0) {
    const prevRow = caseRows.value[index - 1]
    const currentRow = caseRows.value[index]
    currentRow.caseName = prevRow.caseName
    currentRow.categoryId = prevRow.categoryId
    currentRow.categoryName = prevRow.categoryName
    currentRow.precondition = prevRow.precondition
    currentRow.steps = prevRow.steps.map((step) => step)
    currentRow.expected = prevRow.expected.map((e) => e)
    currentRow.remarks = prevRow.remarks
    currentRow.priority = prevRow.priority
  }
}

function addSuiteOption(name: string) {
  const trimmedName = (name || '').trim()
  if (!trimmedName) return
  const exists = suiteOptions.value.some(
    (option) => option.trim().toLowerCase() === trimmedName.toLowerCase()
  )
  if (!exists) {
    suiteOptions.value = [...suiteOptions.value, trimmedName]
  }
  suiteName.value = trimmedName
}

function saveCases() {
  const normalizedSteps = (row: CaseRow) => row.steps.filter((s) => s.trim()).join('\n')
  const normalizedExpected = (row: CaseRow) => row.expected.filter((e) => e.trim()).join('\n')
  const hasContent = (row: CaseRow) =>
    row.caseName.trim() ||
    row.precondition.trim() ||
    row.expected.some((e) => e.trim()) ||
    row.remarks.trim() ||
    row.steps.some((step) => step.trim())

  // 优先级映射：P0→0, P1→1, P2→2, P3→3
  const mapPriority = (p: string): number => {
    const num = parseInt(p.replace('P', ''), 10)
    return isNaN(num) ? 0 : num
  }

  // 构建步骤对象数组（按数字编号对应，无编号则按行对应）
  const buildSteps = (row: CaseRow) => {
    const stepsText = row.steps.join('\n')
    const expectedText = row.expected.join('\n')
    return parseNumberedSteps(stepsText, expectedText)
  }

  // 逐行调用 POST /api/cases
  const validRows = caseRows.value.filter(hasContent)
  if (!validRows.length) {
    alert('没有可保存的用例')
    return false
  }

  return Promise.all(
    validRows.map((row) => {
      const payload: CreateCaseRequest = {
        categoryId: row.categoryId ?? '',
        categoryName: row.categoryName,
        name: row.caseName.trim() || `用例 ${row.id}`,
        originDescription: normalizedSteps(row) + '\n' + normalizedExpected(row),
        targetUrl: row.precondition,
        // 用例级预期结果（后端独立字段，避免只依赖 steps[].expected）
        expectedResult: normalizedExpected(row),
        steps: buildSteps(row),
        priority: mapPriority(row.priority),
      }
      return apiCreateCase(payload)
    })
  )
    .then(() => {
      const savedListRows = validRows.map((row, index) => ({
        id: Date.now() + index,
        title: row.caseName.trim() || `用例 ${index + 1}`,
        precondition: row.precondition,
        steps: normalizedSteps(row),
        expected: normalizedExpected(row),
        remark: row.remarks,
        priority: row.priority || ''
      }))
      caseListRows.value = [...savedListRows, ...caseListRows.value]
      alert('保存成功')
    })
    .catch((err) => {
      alert('保存失败，请重试')
      throw err
    })
}

function clearCreateForm() {
  caseRows.value = [createEmptyRow(1)]
  suiteName.value = ''
  headers.value = ''
  stepMode.value = 'single'
}

/** 用例 Store — 模块级单例，所有调用者共享同一状态 */
export function useCaseStore() {
  return {
    caseRows,
    suiteName,
    headers,
    stepMode,
    suiteOptions,
    caseListRows,
    caseTotal,
    caseTreeData,
    treeLoading,
    loadCategoryTree,
    refreshCategoryTree,
    loadCaseList,
    addRow,
    removeRow,
    addStepLine,
    removeStepLine,
    updateStepsText,
    updateCaseRow,
    updateStepLine,
    updateExpectedLine,
    updateExpectedText,
    copyPreviousRow,
    addSuiteOption,
    saveCases,
    clearCreateForm
  }
}
