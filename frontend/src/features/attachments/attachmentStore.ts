import { ref } from 'vue'
import type { AttachmentRow } from './types'

// 模块级单例状态
const rows = ref<AttachmentRow[]>(
  Array.from({ length: 24 }, (_, index) => ({
    id: index + 1,
    status: '待匹配',
    batch: 'BJ20260629008',
    file: '1828.xlsx',
    customer: '客户A',
    quote: 'Q20260630',
    model: 'M202606'
  }))
)

/** 附件 Store — 单例 */
export function useAttachmentStore() {
  return { rows }
}
