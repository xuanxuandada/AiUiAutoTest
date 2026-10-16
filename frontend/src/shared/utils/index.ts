import type { PageResult } from '../types'

/**
 * 归一化后端列表响应：兼容「分页对象」与「纯数组」两种形态。
 *
 * 后端 6 个列表接口已由「返回数组」改为「返回分页对象」
 * （data = { records, total, size, current, pages }），
 * 未改造的接口仍是数组。统一转成 { records, total }，避免页面直接 data.map 报错。
 */
export function normalizePage<T>(data: any): PageResult<T> {
  if (Array.isArray(data)) {
    return { records: data as T[], total: data.length }
  }
  return {
    records: Array.isArray(data?.records) ? (data.records as T[]) : [],
    total: Number(data?.total ?? 0),
    size: data?.size,
    current: data?.current,
    pages: data?.pages
  }
}

/**
 * 拆分 AI 诊断的文本。
 *
 * AI 诊断返回的是**纯文本**（不是 JSON），固定三段、用中文方括号标记：
 * 【报错含义】…… 【可能原因】…… 【解决建议】……
 *
 * @returns [{ title: '报错含义', body: '……' }, ...]；格式不符时 title 为空字符串
 */
export function splitAiAnalysis(text: string): Array<{ title: string; body: string }> {
  if (!text) return []
  return text
    .split(/(?=【[^】]+】)/)
    .filter((part) => part.trim())
    .map((part) => {
      const m = part.match(/^【([^】]+)】([\s\S]*)$/)
      return m ? { title: m[1], body: m[2].trim() } : { title: '', body: part.trim() }
    })
}

/**
 * 格式化日期
 * @param date 日期对象或字符串
 * @param fmt 格式模板，如 'YYYY-MM-DD'
 */
export function formatDate(date: Date | string | number, fmt = 'YYYY-MM-DD'): string {
  const d = new Date(date)
  const map: Record<string, number> = {
    'YYYY': d.getFullYear(),
    'MM': d.getMonth() + 1,
    'DD': d.getDate(),
    'HH': d.getHours(),
    'mm': d.getMinutes(),
    'ss': d.getSeconds()
  }
  let result = fmt
  for (const key of Object.keys(map)) {
    result = result.replace(key, String(map[key]).padStart(2, '0'))
  }
  return result
}

/**
 * 防抖
 * @param fn 要执行的函数
 * @param delay 延迟毫秒数
 */
export function debounce<T extends (...args: any[]) => any>(fn: T, delay: number): (...args: Parameters<T>) => void {
  let timer: ReturnType<typeof setTimeout> | null = null
  return (...args: Parameters<T>) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn(...args), delay)
  }
}

/**
 * 节流
 * @param fn 要执行的函数
 * @param interval 间隔毫秒数
 */
export function throttle<T extends (...args: any[]) => any>(fn: T, interval: number): (...args: Parameters<T>) => void {
  let lastTime = 0
  return (...args: Parameters<T>) => {
    const now = Date.now()
    if (now - lastTime >= interval) {
      lastTime = now
      fn(...args)
    }
  }
}

/**
 * 生成唯一 ID
 */
export function generateId(): number {
  return Date.now() + Math.floor(Math.random() * 1000)
}

/**
 * 将带编号的文本拆分为 Map<序号, 原文>
 * 例: "1.打开百度页面\n2.输入内容" → Map { 1 => "1.打开百度页面", 2 => "2.输入内容" }
 * 支持中文编号符号：1. 1。1、
 *
 * 注意：按「行」拆分，每行视为一项（与界面"每行一个步骤"约定一致）。
 * 不能用整段文本按 /(?=\d+[.。、])/ 全局切割，否则步骤内容里的数字会被误判为编号，
 * 例如文件路径 "C:\...\BC19039-10.4.pdf" 中的 "10." 会把该步骤截断、数据丢失。
 */
function splitNumberedItems(text: string): Map<number, string> {
  const map = new Map<number, string>()
  if (!text?.trim()) return map
  const lines = text
    .split('\n')
    .map(s => s.trim())
    .filter(s => s)
  for (const line of lines) {
    const match = line.match(/^(\d+)[.。、]/)
    if (match) {
      map.set(parseInt(match[1], 10), line)
    }
  }
  return map
}

/**
 * 将步骤文本和预期文本按数字编号对应，生成 StepItem[]
 * - 步骤: "1.打开百度页面  2.输入框输入123456"
 * - 预期: "2.输入框显示123456"
 * → [{step:"1.打开百度页面", expected:""}, {step:"2.输入框输入123456", expected:"2.输入框显示123456"}]
 *
 * 匹配规则（按优先级）：
 * 1. 步骤、预期都带编号 → 按编号匹配
 * 2. 步骤带编号、预期无编号 → 预期按行顺序对应到步骤（防止预期被丢弃）
 * 3. 都无编号（或仅预期带编号）→ 按行一一对应兜底（兼容旧格式）
 */
export function parseNumberedSteps(
  stepsText: string,
  expectedText: string
): Array<{ step: string; expected: string }> {
  const stepsMap = splitNumberedItems(stepsText)
  const expectedMap = splitNumberedItems(expectedText)

  // 规则1：步骤、预期都带编号 → 按编号匹配
  if (stepsMap.size > 0 && expectedMap.size > 0) {
    const allNums = new Set([...stepsMap.keys(), ...expectedMap.keys()])
    const sortedNums = [...allNums].sort((a, b) => a - b)
    return sortedNums
      .filter(num => stepsMap.has(num))
      .map(num => ({
        step: stepsMap.get(num)!,
        expected: expectedMap.get(num) || ''
      }))
  }

  // 规则2：步骤带编号、预期无编号 → 预期按行顺序对应到步骤
  if (stepsMap.size > 0) {
    const sortedNums = [...stepsMap.keys()].sort((a, b) => a - b)
    const expectedLines = expectedText
      .split('\n')
      .map(s => s.trim())
      .filter(s => s)
    return sortedNums.map((num, index) => ({
      step: stepsMap.get(num)!,
      expected: expectedLines[index] || ''
    }))
  }

  // 规则3：无编号（或仅预期带编号）→ 按行一一对应兜底（兼容旧逻辑）
  const stepLines = stepsText.split('\n').filter(s => s.trim())
  const expectedLines = expectedText.split('\n')
  const maxLen = Math.max(stepLines.length, expectedLines.length)
  const result: Array<{ step: string; expected: string }> = []
  for (let i = 0; i < maxLen; i++) {
    const step = (stepLines[i] || '').trim()
    const expected = (expectedLines[i] || '').trim()
    if (step || expected) {
      result.push({ step, expected })
    }
  }
  return result
}
