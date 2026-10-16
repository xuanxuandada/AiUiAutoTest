/** 用例行数据 */
export interface CaseRow {
  id: number
  caseName: string
  categoryId?: number | string
  categoryName?: string
  precondition: string
  steps: string[]
  expected: string[]
  remarks: string
  priority: string
}

/** 保存到列表的用例数据 */
export interface SavedCase {
  id: number | string
  title: string
  precondition: string
  steps: string
  expected: string
  remark: string
  priority: string
}

/** 步骤对象 */
export interface StepItem {
  step: string
  expected: string
}

/** 用例创建请求参数（POST /api/cases） */
export interface CreateCaseRequest {
  categoryId?: number | string
  categoryName?: string
  name: string
  originDescription?: string
  targetUrl?: string
  /** 用例级预期结果（独立于 steps[].expected，与后端 expectedResult 字段对齐） */
  expectedResult?: string
  steps: StepItem[]
  type?: string
  tags?: string
  priority: number
  status?: string
}

/** 用例更新请求参数（PUT /api/cases/{id}） */
export interface UpdateCaseRequest {
  name: string
  targetUrl?: string
  steps: StepItem[]
  priority: number
  /** 用例级预期结果（独立于 steps[].expected，与后端 expectedResult 字段对齐） */
  expectedResult?: string
  /** 原始描述（步骤+预期，与创建接口 originDescription 对齐） */
  originDescription?: string
}

/** 用例分类树节点 */
export interface CaseTreeNode {
  id: string
  label: string
  children?: CaseTreeNode[]
  autoInclude?: boolean
}

// ===== 用例分类 API 相关 =====

/** 分类实体（与后端对应） */
export interface CaseCategory {
  id: number | string
  name: string
  parentId: number | string | null
  tenantId: number | string
  sortOrder?: number
  createTime?: string
  updateTime?: string
  children?: CaseCategory[]
}

/** 创建分类参数 */
export interface CreateCaseCategoryParams {
  tenantId: number | string
  name: string
  code?: string
  description?: string
  parentId?: number | string | null
  sortOrder?: number
}

/** 更新分类参数 */
export interface UpdateCaseCategoryParams {
  id: number | string
  name: string
  code?: string
  description?: string
  parentId?: number | string | null
  sortOrder?: number
}
