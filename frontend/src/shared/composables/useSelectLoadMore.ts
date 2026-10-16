import { ref } from 'vue'
import type { PaginatedResult } from '../../features/user/types'

/**
 * el-select 下拉无限滚动加载
 * @param fetchFn 分页加载函数，返回 PaginatedResult
 */
export function useSelectLoadMore<T>(
  fetchFn: (page: number, pageSize: number) => Promise<PaginatedResult<T>>,
  pageSize = 20
) {
  const options = ref<T[]>([])
  const page = ref(1)
  const loading = ref(false)
  const noMore = ref(false)
  const error = ref<string | null>(null)

  async function loadMore() {
    if (loading.value || noMore.value) {
      return
    }
    loading.value = true
    error.value = null
    try {
      const result = await fetchFn(page.value, pageSize)
      if (!result || !Array.isArray(result.records)) {
        const msg = 'API 返回数据格式错误：缺少 records 字段或 records 不是数组'
        error.value = msg
        return
      }
      options.value.push(...result.records)
      page.value++
      if (result.records.length < pageSize) {
        noMore.value = true
      }
    } catch (e: any) {
      const msg = e?.message || '加载选项失败'
      error.value = msg
    } finally {
      loading.value = false
    }
  }

  function reset() {
    options.value = []
    page.value = 1
    noMore.value = false
    error.value = null
    loadMore()
  }

  return { options, loading, noMore, error, loadMore, reset }
}
