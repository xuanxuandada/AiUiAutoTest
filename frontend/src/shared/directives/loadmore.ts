/**
 * el-select 滚动加载指令（需配合 :teleported="false"）
 * 用法: v-loadmore="loadMoreFn"
 *
 * Element Plus 2.x 下拉面板使用 <el-scrollbar>，滚动容器为 .el-scrollbar__wrap
 */
const loadmoreDirective = {
  mounted(el: HTMLElement, binding: any) {
    const fn = binding.value
    if (typeof fn !== 'function') return

    let cleanup: (() => void) | null = null
    let attachedEl: HTMLElement | null = null

    function setupScroll(dropdown: HTMLElement) {
      // 已绑定到同一个元素则跳过；元素变了则先清理旧的再绑定新的
      if (attachedEl === dropdown) return
      if (cleanup) {
        cleanup()
        cleanup = null
        attachedEl = null
      }

      let ticking = false
      const handler = () => {
        if (ticking) return
        ticking = true
        requestAnimationFrame(() => {
          const { scrollTop, scrollHeight, clientHeight } = dropdown
          if (scrollHeight - scrollTop - clientHeight < 20) {
            fn()
          }
          ticking = false
        })
      }
      dropdown.addEventListener('scroll', handler)
      cleanup = () => dropdown.removeEventListener('scroll', handler)
      attachedEl = dropdown
    }

    function tryAttach() {
      // Element Plus 2.x: 下拉内部用 <el-scrollbar>，滚动容器是 .el-scrollbar__wrap
      // 兼容旧版: .el-select-dropdown__wrap
      const dropdown =
        el.querySelector('.el-scrollbar__wrap') as HTMLElement | null ||
        el.querySelector('.el-select-dropdown__wrap') as HTMLElement | null
      if (dropdown) {
        setupScroll(dropdown)
      }
    }

    // 下拉面板在打开时才动态创建，用 MutationObserver 监听 DOM 变化
    const observer = new MutationObserver(tryAttach)
    observer.observe(el, { childList: true, subtree: true })
    tryAttach()

    ;(el as any)._loadmoreCleanup = () => {
      observer.disconnect()
      cleanup?.()
    }
  },
  unmounted(el: HTMLElement) {
    ;(el as any)._loadmoreCleanup?.()
  }
}

export default loadmoreDirective
