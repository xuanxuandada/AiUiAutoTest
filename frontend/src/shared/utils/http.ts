import axios from 'axios'
import JSONbig from 'json-bigint'
import { ElMessage } from 'element-plus'

/**
 * 解决 JS Number 精度问题（后端 Long 型 ID 超过 2^53-1 会丢失精度）
 * 使用 json-bigint 解析响应，大整数自动转为字符串
 */
const JSONBig = JSONbig({ storeAsString: true })

// 全局配置：响应数据用 JSONbig 解析，保留大整数精度
axios.defaults.transformResponse = [
  (data) => {
    if (typeof data === 'string') {
      try {
        return JSONBig.parse(data)
      } catch {
        return data
      }
    }
    return data
  }
]

// 请求拦截：自动从 localStorage 读取 token 并附加到请求头
axios.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：401 表示 token 过期，清空登录状态跳转登录页
axios.interceptors.response.use(
  (res) => {
    // HTTP 200 但业务 code 为 401，token 过期
    if (res.data?.code === 401) {
      ElMessage.error(res.data?.message || '登录已过期，请重新登录')
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      localStorage.removeItem('tenant')
      window.location.href = '/login'
    }
    // 如果 transformResponse 未生效，手动用 JSONbig 再解析一次
    if (typeof res.data === 'string') {
      try {
        res.data = JSONBig.parse(res.data)
      } catch { /* 保持原样 */ }
    }
    return res
  },
  (err) => {
    if (err?.response) {
      // 提取后端返回的业务错误信息（HTTP 非 2xx 场景，如 500）
      // 后端响应体可能是 { code, message } 对象，也可能是纯文本
      let data = err.response.data
      if (typeof data === 'string') {
        try {
          data = JSONBig.parse(data)
        } catch { /* 保持原样 */ }
      }
      const backendMsg = typeof data === 'string' ? data : data?.message || data?.msg
      if (backendMsg) err.message = backendMsg

      if (err.response.status === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('user')
        localStorage.removeItem('tenant')
        window.location.href = '/login'
      }
    }
    return Promise.reject(err)
  }
)

export default axios
