import { ref } from 'vue'
import type { SuiteRow, SuiteCase } from './types'
import { fetchSuiteList, createSuite as apiCreateSuite, updateSuite as apiUpdateSuite } from './api'

// 模块级单例状态
/** 当前页数据（搜索时是「全量过滤后的当前页」） */
const rows = ref<SuiteRow[]>([])
/** 总条数：无筛选时是后端 total，有筛选时是过滤后的条数 */
const total = ref(0)
const loading = ref(false)

const searchKeyword = ref('')
const idKeyword = ref('')

/** 后端套件原始数据 → 表格行 */
function mapRow(item: any): SuiteRow {
  return {
    id: String(item.id),
    name: item.name || '',
    description: item.description || '',
    updated: item.updateTime ? item.updateTime.slice(0, 10) : '—',
    status: item.status || '',
    caseIds: (item.caseIds || []).map((id: any) => String(id)),
    useLoginState: item.loginEnabled !== undefined ? !!item.loginEnabled : (item.useLoginState ?? false),
    loginFetchCommand: item.logintext || item.loginFetchCommand || ''
  }
}

/**
 * 加载套件列表
 * 名称/ID 筛选都下推到后端（/api/suites?keyword=&id=），前端不再本地过滤/切片
 */
async function loadSuiteList(page = 1, pageSize = 20) {
  const keyword = searchKeyword.value.trim()
  const idText = idKeyword.value.trim()
  // 后端 id 参数是 Long，传非数字会 400，这里先挡一道
  const id = /^\d+$/.test(idText) ? idText : undefined
  loading.value = true
  try {
    const res = await fetchSuiteList(page, pageSize, { keyword: keyword || undefined, id })
    rows.value = res.records.map(mapRow)
    total.value = res.total
  } catch {
    // 加载失败保持旧数据
  } finally {
    loading.value = false
  }
}

/** 创建套件 */
async function addSuite(data: { name: string; owner?: string; description?: string }) {
  const result = await apiCreateSuite({
    name: data.name,
    description: data.description || ''
  })
  return result
}

function getSuiteById(id: number | string) {
  return rows.value.find((row) => String(row.id) === String(id)) ?? null
}

/** 更新套件 */
async function updateSuite(id: number | string, data: { name?: string; owner?: string; description?: string; caseIds?: (number | string)[]; useLoginState?: boolean; loginFetchCommand?: string }) {
  const payload: any = {}
  if (data.name) payload.name = data.name
  if (data.description !== undefined) payload.description = data.description
  if (data.caseIds) payload.caseIds = data.caseIds
  if (data.useLoginState !== undefined) payload.useLoginState = data.useLoginState
  if (data.loginFetchCommand !== undefined) payload.loginFetchCommand = data.loginFetchCommand
  await apiUpdateSuite(id, payload)
  const index = rows.value.findIndex((row) => String(row.id) === String(id))
  if (index !== -1) {
    rows.value[index] = { ...rows.value[index], ...data, updated: new Date().toISOString().slice(0, 10) }
  }
}

/** 关联用例到套件 */
async function addCaseToSuite(suiteId: number | string, caseId: number | string) {
  const suite = rows.value.find((row) => String(row.id) === String(suiteId))
  if (!suite) return
  const newIds = [...suite.caseIds, String(caseId)]
  await apiUpdateSuite(suiteId, { name: suite.name, caseIds: newIds })
  suite.caseIds = newIds
}

/** 从套件移除用例 */
async function removeCaseFromSuite(suiteId: number | string, caseId: number | string) {
  const suite = rows.value.find((row) => String(row.id) === String(suiteId))
  if (!suite) return
  const newIds = suite.caseIds.filter((id) => String(id) !== String(caseId))
  await apiUpdateSuite(suiteId, { name: suite.name, caseIds: newIds })
  suite.caseIds = newIds
}

/** 测试套件 Store — 单例 */
export function useSuiteStore() {
  return { rows, total, loading, searchKeyword, idKeyword, loadSuiteList, addSuite, getSuiteById, updateSuite, addCaseToSuite, removeCaseFromSuite }
}
