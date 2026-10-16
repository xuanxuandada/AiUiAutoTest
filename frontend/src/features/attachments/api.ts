import type { AttachmentRow } from './types'

const BASE_URL = '/api/attachments'

/**
 * 获取附件列表
 */
export async function fetchAttachmentList(): Promise<AttachmentRow[]> {
  // TODO: 对接真实 API
  // const res = await axios.get(BASE_URL)
  // return res.data
  return []
}
