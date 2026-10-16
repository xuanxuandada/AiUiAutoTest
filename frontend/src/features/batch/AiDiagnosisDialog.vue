<template>
  <el-dialog
    :model-value="modelValue"
    title="AI 诊断"
    width="680px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="result" class="ai-result">
      <div class="ai-meta">
        <el-tag v-if="result.cached" type="info" size="small">上次分析结果</el-tag>
        <span class="ai-meta-text">
          模型：{{ result.model || '—' }} · 分析时间：{{ formatAnalyzedAt(result.analyzedAt) }}
        </span>
      </div>

      <el-collapse class="ai-raw">
        <el-collapse-item title="报错原文" name="raw">
          <div class="ai-raw-text">{{ result.errorMessage || '—' }}</div>
        </el-collapse-item>
      </el-collapse>

      <!-- analysis 是模型生成的纯文本：只用插值渲染，绝不 v-html -->
      <div class="ai-analysis">
        <div v-for="(sec, i) in sections" :key="i" class="ai-analysis-section">
          <div v-if="sec.title" class="ai-analysis-title">【{{ sec.title }}】</div>
          <div class="ai-analysis-body">{{ sec.body }}</div>
        </div>
        <div v-if="sections.length === 0" class="ai-analysis-body">{{ result.analysis }}</div>
      </div>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { splitAiAnalysis } from '../../shared/utils'
import type { AiAnalysisResult } from './types'

const props = defineProps<{
  modelValue: boolean
  result: AiAnalysisResult | null
}>()

const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()

const sections = computed(() => splitAiAnalysis(props.result?.analysis || ''))

/** 后端下发 2026-08-29T15:03:11.482，展示成 2026-08-29 15:03:11 */
function formatAnalyzedAt(value?: string) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}
</script>

<style scoped>
.ai-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.ai-meta-text {
  font-size: 12px;
  color: #6b7280;
}
.ai-raw {
  margin-bottom: 14px;
}
.ai-raw-text {
  font-family: ui-monospace, Consolas, Menlo, monospace;
  font-size: 12px;
  line-height: 1.6;
  color: #4b5563;
  white-space: pre-wrap;
  word-break: break-word;
}
.ai-analysis-section + .ai-analysis-section {
  margin-top: 14px;
}
.ai-analysis-title {
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 4px;
}
.ai-analysis-body {
  font-size: 13px;
  line-height: 1.8;
  color: #374151;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
