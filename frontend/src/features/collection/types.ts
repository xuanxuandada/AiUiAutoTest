/** 用例集行数据（列表展示） */
export interface CollectionRow {
  id: number | string
  name: string
  description: string
  updated: string
  status: string
  /** 关联的用例 ID 列表 */
  caseIds: (number | string)[]
}

/** 创建/更新用例集请求参数 */
export interface CollectionRequest {
  name: string
  description?: string
  caseIds?: (number | string)[]
  status?: string
}
