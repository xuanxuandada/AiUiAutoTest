/** 套件内用例 */
export interface SuiteCase {
  id: number
  title: string
  precondition: string
  steps: string
  expected: string
  priority: string
  module: string
  creator: string
  routeUrl: string
  remark: string
}

/** 测试套件行数据（列表展示） */
export interface SuiteRow {
  id: number | string
  name: string
  description: string
  updated: string
  status: string
  caseIds: (number | string)[]
  /** 是否添加登录态 */
  useLoginState?: boolean
  /** Copy as fetch 命令文本 */
  loginFetchCommand?: string
}

/** 创建/更新套件请求参数 */
export interface SuiteRequest {
  name: string
  description?: string
  caseIds?: (number | string)[]
  scheduleCron?: string
  status?: string
  /** 是否添加登录态 */
  useLoginState?: boolean
  /** Copy as fetch 命令文本 */
  loginFetchCommand?: string
}
