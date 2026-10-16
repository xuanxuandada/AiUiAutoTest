import { ref, computed } from 'vue'
import type { CollectionRow } from './types'
import {
  fetchCollectionList,
  createCollection as apiCreateCollection,
  updateCollection as apiUpdateCollection,
  deleteCollection as apiDeleteCollection
} from './api'

// 模块级单例状态
const rows = ref<CollectionRow[]>([])
const loading = ref(false)

const searchKeyword = ref('')
const idKeyword = ref('')

const filteredRows = computed(() => {
  const nameKw = searchKeyword.value.trim().toLowerCase()
  const idKw = idKeyword.value.trim()
  if (!nameKw && !idKw) return rows.value
  return rows.value.filter((row) => {
    const matchName = !nameKw || row.name.toLowerCase().includes(nameKw)
    const matchId = !idKw || String(row.id) === idKw
    return matchName && matchId
  })
})

/** 加载用例集列表 */
async function loadCollectionList() {
  loading.value = true
  try {
    const list = await fetchCollectionList()
    rows.value = list.map((item: any) => ({
      id: String(item.id),
      name: item.name || '',
      description: item.description || '',
      updated: item.updateTime ? item.updateTime.slice(0, 10) : '—',
      status: item.status || '',
      caseIds: (item.caseIds || []).map((id: any) => String(id))
    }))
  } catch {
    // 加载失败保持旧数据
  } finally {
    loading.value = false
  }
}

/** 创建用例集 */
async function addCollection(data: { name: string; description?: string }) {
  return await apiCreateCollection({
    name: data.name,
    description: data.description || ''
  })
}

/** 更新用例集 */
async function updateCollection(id: number | string, data: { name?: string; description?: string }) {
  const payload: any = {}
  if (data.name) payload.name = data.name
  if (data.description !== undefined) payload.description = data.description
  await apiUpdateCollection(id, payload)
  const index = rows.value.findIndex((row) => String(row.id) === String(id))
  if (index !== -1) {
    rows.value[index] = { ...rows.value[index], ...data, updated: new Date().toISOString().slice(0, 10) }
  }
}

/** 删除用例集 */
async function removeCollection(id: number | string) {
  await apiDeleteCollection(id)
  rows.value = rows.value.filter((row) => String(row.id) !== String(id))
}

/** 用例集 Store — 单例 */
export function useCollectionStore() {
  return { rows, filteredRows, loading, searchKeyword, idKeyword, loadCollectionList, addCollection, updateCollection, removeCollection }
}
