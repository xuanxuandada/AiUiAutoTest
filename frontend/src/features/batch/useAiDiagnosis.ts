import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchAiAnalysis } from './api'
import type { AiAnalysisResult } from './types'

/**
 * AI 诊断交互（批次详情页 / 批次列表页共用）。
 *
 * 接口是「用户点了才调」，而且用的是用户自己的 API Key，所以：
 * - loading 做成单例：同一时刻只允许一条在诊断。后端是「分析完才写缓存」，
 *   两次并发点击会各自调一次模型、各花一次额度，并发保护只能靠前端。
 * - 已有缓存时由调用方传进来（cached），直接回放、不发请求。
 *
 * @param onAnalyzed 拿到新结果后的回调（页面可据此把结果记进本地缓存，下次点击即回放）
 */
export function useAiDiagnosis(onAnalyzed?: (executionId: string, data: AiAnalysisResult) => void) {
  const router = useRouter()
  const visible = ref(false)
  const loading = ref(false)
  const currentId = ref('')
  const result = ref<AiAnalysisResult | null>(null)

  function showResult(data: AiAnalysisResult) {
    result.value = data
    visible.value = true
  }

  /**
   * @param executionId 执行记录 ID（雪花 ID，全程当字符串用，不能 Number()）
   * @param cached 已有的缓存结果；传了就只弹窗、不请求、不花额度
   */
  async function diagnose(executionId: string | number, cached?: AiAnalysisResult | null) {
    if (loading.value) return
    const id = String(executionId)

    if (cached?.analysis) {
      showResult(cached)
      return
    }

    currentId.value = id
    loading.value = true
    try {
      const data = await fetchAiAnalysis(id)
      showResult(data)
      onAnalyzed?.(id, data)
    } catch (e: any) {
      const msg = e?.message || 'AI 诊断失败'
      if (e?.code === 'ECONNABORTED') {
        ElMessage.warning('AI 分析超时了，请稍后重试（后端可能仍在计算）')
      } else if (e?.bizCode === 400 && String(msg).includes('还没有配置')) {
        // 最常见：当前登录用户没配自己的 Key（每问一次花的是用户自己的额度）
        ElMessageBox.confirm(msg, '需要先配置 AI Key', {
          confirmButtonText: '去配置',
          cancelButtonText: '稍后'
        })
          .then(() => router.push('/profile'))
          .catch(() => {})
      } else if (e?.bizCode === 400 && String(msg).includes('没有失败信息')) {
        ElMessage.warning('该记录没有可分析的报错内容')
      } else {
        ElMessage.error(msg)
      }
    } finally {
      loading.value = false
    }
  }

  return { visible, loading, currentId, result, diagnose, showResult }
}
