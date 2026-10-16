import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

/** 页面静止多久后开始显示（毫秒），作者署名用：5 分钟 */
export const AUTHOR_IDLE_REVEAL_MS = 5 * 60 * 1000

/** 渐显 / 渐隐时长（毫秒），值越大越"慢慢显示出来" */
export const AUTHOR_FADE_MS = 4000

export interface IdleRevealOptions {
  /** 静止多少毫秒后显示，默认 AUTHOR_IDLE_REVEAL_MS（5 分钟） */
  idleMs?: number
  /**
   * 用户再次操作时是否重新隐藏，默认 true（回到静止计时）。
   * 传 false 表示「出现过就一直留着」——显示后即停止监听，不再复位。
   */
  hideOnActivity?: boolean
}

/**
 * 空闲检测：页面持续无操作（鼠标/键盘/滚轮/触摸/滚动）达到 idleMs 后把 revealed 置为 true。
 * hideOnActivity 为 true 时，用户恢复操作会让 revealed 复位（渐隐）并重新计时；
 * 为 false 时只触发一次，显示后立即摘掉监听。
 * 使用方通常把它绑到 opacity 上，配合 transition 实现缓慢淡入。
 */
export function useIdleReveal(options: IdleRevealOptions = {}): { revealed: Ref<boolean> } {
  const idleMs = options.idleMs ?? AUTHOR_IDLE_REVEAL_MS
  const hideOnActivity = options.hideOnActivity ?? true

  const revealed = ref(false)
  let timer: number | undefined
  let listening = false
  // 鼠标移动等事件触发极其频繁，用 1s 节流避免反复清/设计时器
  let lastResetAt = 0

  const events = ['mousemove', 'mousedown', 'keydown', 'wheel', 'touchstart', 'pointerdown'] as const

  function armTimer() {
    if (timer !== undefined) {
      clearTimeout(timer)
    }
    timer = window.setTimeout(() => {
      timer = undefined
      revealed.value = true
      // 不再需要隐藏时，没必要继续监听
      if (!hideOnActivity) {
        detach()
      }
    }, idleMs)
  }

  function attach() {
    if (listening) return
    listening = true
    events.forEach((name) => window.addEventListener(name, onActivity, { passive: true }))
    // capture: true 才能捕获内层容器（.content 等）的滚动
    window.addEventListener('scroll', onActivity, { passive: true, capture: true })
  }

  function detach() {
    if (!listening) return
    listening = false
    events.forEach((name) => window.removeEventListener(name, onActivity))
    window.removeEventListener('scroll', onActivity, { capture: true } as EventListenerOptions)
    if (timer !== undefined) {
      clearTimeout(timer)
      timer = undefined
    }
  }

  function onActivity() {
    const now = Date.now()
    if (now - lastResetAt < 1000) return
    lastResetAt = now

    if (hideOnActivity && revealed.value) {
      revealed.value = false
    }
    if (!revealed.value) {
      armTimer()
    }
  }

  onMounted(() => {
    attach()
    armTimer()
  })

  onBeforeUnmount(detach)

  return { revealed }
}
