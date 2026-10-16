import { ref, computed, toValue, type Ref } from 'vue'

/**
 * 通用分页组合式函数
 * @param items 响应式数据源（总数据列表），支持 Ref 或普通数组
 * @param defaultPageSize 默认每页条数
 */
export function usePagination<T>(items: Ref<T[]> | T[], defaultPageSize = 10) {
  const pageSizes = [5, 10, 20, 30, 50]
  const pageSize = ref(defaultPageSize)
  const currentPage = ref(1)

  // 使用 toValue 统一处理 Ref 和普通数组
  const source = computed(() => toValue(items))

  const totalItems = computed(() => source.value.length)
  const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize.value)))

  const pagedItems = computed(() => {
    const start = (currentPage.value - 1) * pageSize.value
    return source.value.slice(start, start + pageSize.value)
  })

  function changePage(page: number) {
    if (page < 1 || page > totalPages.value) return
    currentPage.value = page
  }

  function updatePageSize(size: number) {
    pageSize.value = size
    if (currentPage.value > totalPages.value) {
      currentPage.value = totalPages.value
    }
  }

  // 当 pageSize 改变时自动修正当前页
  function clampPage() {
    if (currentPage.value > totalPages.value) {
      currentPage.value = totalPages.value
    }
  }

  return {
    pageSizes,
    pageSize,
    currentPage,
    totalItems,
    totalPages,
    pagedItems,
    changePage,
    updatePageSize,
    clampPage
  }
}
