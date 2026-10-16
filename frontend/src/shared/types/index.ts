/** 侧边栏菜单项 */
export interface MenuItem {
  id: string
  label: string
  path: string
}

/** 侧边栏菜单模块 */
export interface MenuModule {
  id: string
  title: string
  open: boolean
  children: MenuItem[]
}

/**
 * 后端分页对象（MyBatis-Plus Page 序列化结果）
 * 后端 6 个列表接口的 data 已由此结构返回，列表数据在 records 里
 */
export interface PageResult<T> {
  /** 当前页数据 */
  records: T[]
  /** 符合条件的总条数 */
  total: number
  /** 每页条数 */
  size?: number
  /** 当前页码，从 1 开始 */
  current?: number
  /** 总页数 */
  pages?: number
}
