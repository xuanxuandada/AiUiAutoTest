<template>
  <div class="card case-card full-screen-table">
    <div class="case-list-header">
      <div
        class="case-list-title"
        :class="{ 'has-login': useLoginState }"
        @click="showLoginConfig = !showLoginConfig"
        title="点击配置登录态"
      >
        {{ suiteName }} — 用例管理
        <span v-if="useLoginState" class="login-badge">已配置登录态</span>
      </div>
      <button class="btn" @click="goBack">返回套件列表</button>
    </div>

    <!-- 登录态配置（点击套件名称展开） -->
    <div v-if="showLoginConfig" class="login-state-bar">
      <div class="login-state-row">
        <span class="login-state-label">是否添加登录态</span>
        <el-tooltip placement="top" effect="dark" :show-after="100">
          <template #content>
            <div class="login-help-content">
              <div class="login-help-title">获取登录态：</div>
              <div class="login-help-step">1. 按 F12 打开开发者工具 → Console（控制台），粘贴这段脚本，回车</div>
              <div class="login-help-script-wrap">
                <pre class="login-help-script">{{ loginHelpScript }}</pre>
                <el-button type="primary" size="small" class="login-help-copy-btn" @click="copyLoginScript">
                  复制脚本
                </el-button>
              </div>
            </div>
          </template>
          <el-icon class="login-help-icon"><QuestionFilled /></el-icon>
        </el-tooltip>
        <el-switch
          v-model="useLoginState"
          active-text="是"
          inactive-text="否"
          :disabled="isReadonly"
          @change="onLoginStateChange"
        />
      </div>
      <div v-if="useLoginState" class="login-fetch-area">
        <el-input
          v-model="loginFetchCommand"
          type="textarea"
          :rows="6"
          placeholder="在此粘贴 Copy as fetch 内容..."
          :disabled="isReadonly"
          class="fetch-textarea"
        />
      </div>
      <div v-if="!isReadonly" class="login-save-row">
        <el-button type="primary" size="small" @click="handleSaveLogin">保存登录态配置</el-button>
      </div>
    </div>

    <div v-if="!isReadonly" class="add-case-bar">
      <el-button type="primary" @click="openDialog">
        <el-icon class="btn-icon"><Plus /></el-icon>
        添加用例
      </el-button>
      <el-button type="success" @click="handleSave" style="margin-left:12px;">保存</el-button>
    </div>

    <!-- 已有用例列表 -->
    <div class="table-placeholder" style="margin-top: 16px">
      <div class="table-header">
        <span>ID</span>
        <span>序号</span>
        <span>路由地址</span>
        <span>用例名称</span>
        <span>优先级</span>
        <span>模块</span>
        <span><span class="required-star">*</span>前置条件</span>
        <span>步骤</span>
        <span>预期结果</span>
        <span>转换状态</span>
        <span>备注</span>
        <span>创建人</span>
        <span>操作</span>
      </div>
      <div v-if="suiteCases.length === 0" class="table-empty">暂无用例，请点击上方按钮新增</div>
      <div class="table-row" v-for="(row, index) in pagedCases" :key="row.id">
        <span>{{ row.id }}</span>
        <span>{{ (casePage - 1) * casePageSize + index + 1 }}</span>
        <span>{{ row.routeUrl || '—' }}</span>
        <span>{{ row.title }}</span>
        <span>
          <el-tag :type="priorityType(row.priority)" size="small">{{ row.priority || '—' }}</el-tag>
        </span>
        <span>{{ row.module || '—' }}</span>
        <!-- 前置条件 -->
        <span v-if="editingId !== row.id">{{ row.precondition || '—' }}</span>
        <el-input v-else v-model="editForm.precondition" type="textarea" :rows="5" size="small" class="inline-textarea" />
        <!-- 步骤 -->
        <span v-if="editingId !== row.id">{{ row.steps || '—' }}</span>
        <el-input v-else v-model="editForm.steps" type="textarea" :rows="5" size="small" class="inline-textarea" />
        <!-- 预期结果 -->
        <span v-if="editingId !== row.id">{{ row.expected || '—' }}</span>
        <el-input v-else v-model="editForm.expected" type="textarea" :rows="5" size="small" class="inline-textarea" />
        <!-- 转换状态 -->
        <span>
          <el-tag v-if="row.copyStatus === 'CONVERTED'" type="success" size="small">已转换</el-tag>
          <el-tag v-else-if="row.copyStatus === 'PENDING'" type="warning" size="small">转换中</el-tag>
          <el-tag v-else-if="row.copyStatus === 'FAILED'" type="danger" size="small">转换失败</el-tag>
          <span v-else class="text-muted">继承父用例</span>
        </span>
        <!-- 备注 -->
        <span v-if="editingId !== row.id">{{ row.remarks || row.remark || '—' }}</span>
        <el-input v-else v-model="editForm.remark" type="textarea" :rows="5" size="small" class="inline-textarea" />
        <span>{{ row.creator || '—' }}</span>
        <span>
          <template v-if="!isReadonly">
            <template v-if="editingId === row.id">
              <el-button type="primary" link size="small" @click="saveEdit(row.id)">保存</el-button>
              <el-button link size="small" @click="cancelEdit">取消</el-button>
            </template>
            <template v-else>
              <el-button type="primary" link size="small" @click="startEdit(row)">编辑</el-button>
              <el-button type="danger" link size="small" @click="handleDelete(row.id)">删除</el-button>
            </template>
          </template>
          <span v-else class="text-muted">—</span>
        </span>
      </div>
    </div>

    <!-- 分页 -->
    <PaginationControl
      v-if="suiteCases.length > 0"
      :total-items="suiteCases.length"
      :current-page="casePage"
      :page-size="casePageSize"
      :page-sizes="[5, 10, 20]"
      @update:currentPage="casePage = $event"
      @update:pageSize="casePageSize = $event"
    />

    <!-- 新增用例弹框 -->
    <el-dialog
      v-model="dialogVisible"
      title="选择用例"
      width="900px"
      :close-on-click-modal="false"
    >
      <!-- 筛选区 -->
      <div class="dialog-filter-bar">
        <span class="filter-label">ID</span>
        <el-input v-model="filterId" placeholder="请输入ID" clearable class="filter-input filter-input--sm" />
        <span class="filter-label">模块</span>
        <el-select v-model="filterModule" placeholder="全部模块" clearable class="filter-select">
          <el-option label="登录模块" value="登录模块" />
          <el-option label="搜索模块" value="搜索模块" />
          <el-option label="订单模块" value="订单模块" />
          <el-option label="支付模块" value="支付模块" />
        </el-select>
        <span class="filter-label">用例名称</span>
        <el-input v-model="filterName" placeholder="请输入" clearable class="filter-input" />
        <span class="filter-label">创建人</span>
        <el-input v-model="filterCreator" placeholder="请输入" clearable class="filter-input" />
        <div class="filter-actions">
          <el-button type="primary" @click="doFilter">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </div>
      </div>

      <!-- 可选用例表格 -->
      <el-table
        ref="tableRef"
        :data="dialogPagedCases"
        style="width: 100%; margin-top: 16px"
        @selection-change="handleSelectionChange"
        @select-all="handleSelectAll"
        @select="handleSelect"
        max-height="360"
        row-key="id"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column type="index" label="序号" width="60" :index="(idx) => (dialogPage - 1) * dialogPageSize + idx + 1" />
        <el-table-column prop="id" label="ID" width="55" />
        <el-table-column prop="title" label="用例名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="steps" label="步骤" min-width="130" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="75">
          <template #default="{ row }">
            <el-tag :type="priorityType(row.priority)" size="small">{{ row.priority }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="module" label="模块" width="110" />
        <el-table-column prop="creator" label="创建人" width="90" />
      </el-table>

      <!-- 弹框内分页 -->
      <PaginationControl
        :total-items="filteredAvailCases.length"
        :current-page="dialogPage"
        :page-size="dialogPageSize"
        :page-sizes="[5, 10, 20]"
        @update:currentPage="dialogPage = $event"
        @update:pageSize="dialogPageSize = $event"
      />

      <!-- 已选用例预览 -->
      <div v-if="selectedCount > 0" class="selected-preview">
        <div class="selected-preview-title">已选用例（{{ selectedCount }}）</div>
        <div class="selected-tags">
          <el-tag
            v-for="item in selectedList"
            :key="item.id"
            closable
            class="selected-tag"
            @close="removeSelected(item.id)"
          >
            [{{ item.id }}] {{ item.title }}
          </el-tag>
        </div>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmAdd" :disabled="selectedCount === 0">
          确认新增 ({{ selectedCount }})
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Plus, QuestionFilled } from '@element-plus/icons-vue'
import PaginationControl from '../../shared/components/PaginationControl.vue'
import { useSuiteStore } from './suiteStore'
import { fetchSuiteById, saveLoginFetchCommand, fetchSuiteCases, updateSuiteCaseCopy, removeSuiteCase } from './api'
import { fetchAllCases } from '../case/api'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const route = useRoute()
const { updateSuite } = useSuiteStore()

const isReadonly = ref(route.query.readonly === '1')

const suiteId = route.params.id as string
const suiteName = ref('')
const suiteCases = ref<any[]>([])
const suiteCaseIds = ref<string[]>([]) // 本地维护的 caseIds 列表

// ===== 登录态配置 =====
const useLoginState = ref(false)
const loginFetchCommand = ref('')
const showLoginConfig = ref(true)

// ===== 获取登录态：一键复制控制台脚本 =====
const loginHelpScript = `(() => {
  const data = {};
  for (let i = 0; i < localStorage.length; i++) {
    const k = localStorage.key(i);
    data[k] = localStorage.getItem(k);
  }
  copy(JSON.stringify(data));
  console.log('已复制 localStorage 登录态 JSON 到剪贴板');
})();`

async function copyLoginScript() {
  try {
    await navigator.clipboard.writeText(loginHelpScript)
    ElMessage.success('脚本已复制，请到浏览器控制台粘贴执行')
  } catch {
    // 兜底：非安全上下文或旧浏览器
    const ta = document.createElement('textarea')
    ta.value = loginHelpScript
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    try {
      document.execCommand('copy')
      ElMessage.success('脚本已复制，请到浏览器控制台粘贴执行')
    } catch {
      ElMessage.error('复制失败，请手动选择复制')
    } finally {
      document.body.removeChild(ta)
    }
  }
}

function onLoginStateChange(val: boolean) {
  if (!val) loginFetchCommand.value = ''
}

async function handleSaveLogin() {
  if (!loginFetchCommand.value.trim()) {
    ElMessage.warning('请先粘贴登录态提取命令')
    return
  }
  try {
    await saveLoginFetchCommand(suiteId, loginFetchCommand.value, useLoginState.value ? 1 : 0)
    ElMessage.success('登录态配置已保存')
  } catch {
    ElMessage.error('保存登录态配置失败')
  }
}

// ===== 弹框 =====
const dialogVisible = ref(false)
const tableRef = ref<any>(null)
const selectedMap = ref<Map<number | string, any>>(new Map())
const selectedCount = computed(() => selectedMap.value.size)
const selectedList = computed(() => Array.from(selectedMap.value.values()))

function removeSelected(id: number | string) {
  selectedMap.value.delete(id)
}

// ===== 可选用例（从 API 加载） =====
const availCases = ref<any[]>([])

async function loadAvailCases(keyword = '') {
  try {
    // 弹框里除了「名称」，还有 ID/模块/创建人筛选只能在本地做，所以这里逐页拉全；
    // 「名称」筛选已下推到后端 /api/cases?keyword=，能少拿不少数据
    const list = await fetchAllCases(keyword)
    availCases.value = list.map((item: any) => ({
      id: String(item.id),
      title: item.name || '',
      priority: item.priority != null ? `P${item.priority}` : '',
      module: item.categoryName || '',
      creator: '—',
      steps: Array.isArray(item.steps) ? item.steps.map((s: any) => s.step || '').join('\n') : '',
      precondition: item.targetUrl || '',
      // 预期结果：优先取用例级 expectedResult，兼容无该字段时从 steps[].expected 拼接
      expected:
        item.expectedResult ||
        (Array.isArray(item.steps) ? item.steps.map((s: any) => s.expected || '').join('\n') : ''),
      remark: '',
      routeUrl: item.targetUrl || ''
    }))
  } catch {
    availCases.value = []
  }
}

// ===== 筛选 =====
const filterId = ref('')
const filterModule = ref('')
const filterName = ref('')
const filterCreator = ref('')
const filterIdApplied = ref('')
const filterModuleApplied = ref('')
const filterNameApplied = ref('')
const filterCreatorApplied = ref('')

// ===== 已有用例分页 =====
const casePage = ref(1)
const casePageSize = ref(10)

const pagedCases = computed(() => {
  const start = (casePage.value - 1) * casePageSize.value
  return suiteCases.value.slice(start, start + casePageSize.value)
})
const dialogPage = ref(1)
const dialogPageSize = ref(5)

const dialogPagedCases = computed(() => {
  const start = (dialogPage.value - 1) * dialogPageSize.value
  return filteredAvailCases.value.slice(start, start + dialogPageSize.value)
})
const filteredAvailCases = computed(() => {
  let list = availCases.value
  const id = filterIdApplied.value.trim()
  const m = filterModuleApplied.value
  const n = filterNameApplied.value.trim().toLowerCase()
  const c = filterCreatorApplied.value.trim().toLowerCase()
  if (id) list = list.filter(row => String(row.id) === id)
  if (m) list = list.filter(row => row.module === m)
  if (n) list = list.filter(row => row.title.toLowerCase().includes(n))
  if (c) list = list.filter(row => row.creator.toLowerCase().includes(c))
  return list
})

onMounted(async () => {
  await loadSuiteCases()
})

function openDialog() {
  selectedMap.value = new Map()
  // resetFilter 里已包含一次「无关键字」的重新拉取
  resetFilter()
  dialogVisible.value = true
  nextTick(() => tableRef.value?.clearSelection())
}

function doFilter() {
  filterIdApplied.value = filterId.value
  filterModuleApplied.value = filterModule.value
  filterNameApplied.value = filterName.value
  filterCreatorApplied.value = filterCreator.value
  dialogPage.value = 1
  // 「名称」可能变了 → 重新按名称向后端取数（其余筛选仍是本地过滤）
  loadAvailCases(filterName.value.trim())
}

function resetFilter() {
  filterId.value = ''
  filterModule.value = ''
  filterName.value = ''
  filterCreator.value = ''
  filterIdApplied.value = ''
  filterModuleApplied.value = ''
  filterNameApplied.value = ''
  filterCreatorApplied.value = ''
  dialogPage.value = 1
  // 名称清空后要把后端的筛选也解除
  loadAvailCases()
}

// 整页全选/全不选
function handleSelectAll(rows: any[]) {
  if (rows.length === 0) {
    dialogPagedCases.value.forEach(row => selectedMap.value.delete(row.id))
  } else {
    rows.forEach(row => selectedMap.value.set(row.id, row))
  }
}

// 单行勾选/取消
function handleSelect(_rows: any[], row: any) {
  if (selectedMap.value.has(row.id)) {
    selectedMap.value.delete(row.id)
  } else {
    selectedMap.value.set(row.id, row)
  }
}

function handleSelectionChange(_rows: any[]) {
  // 由 handleSelectAll / handleSelect 处理
}

// 切页后恢复当前页勾选状态
watch(dialogPage, () => {
  nextTick(() => {
    dialogPagedCases.value.forEach(row => {
      const shouldCheck = selectedMap.value.has(row.id)
      tableRef.value?.toggleRowSelection(row, shouldCheck)
    })
  })
})

function confirmAdd() {
  const list = Array.from(selectedMap.value.values())
  if (list.length === 0) return
  // 去重后追加到本地列表
  const newIds = list.map((c: any) => String(c.id)).filter((id: string) => !suiteCaseIds.value.includes(id))
  if (newIds.length === 0) {
    ElMessage.info('所选用例已全部关联')
    dialogVisible.value = false
    return
  }
  // 追加到 caseIds 和用例展示列表
  suiteCaseIds.value = [...suiteCaseIds.value, ...newIds]
  const newCases = list.filter((c: any) => newIds.includes(String(c.id))).map((c: any) => ({
    id: String(c.id),
    title: c.title || '',
    priority: c.priority || '',
    module: c.module || '—',
    precondition: c.precondition || '',
    steps: c.steps || '',
    expected: c.expected || '',
    remark: c.remark || '',
    routeUrl: c.routeUrl || '',
    creator: c.creator || '—',
    copyStatus: 'PENDING'
  }))
  suiteCases.value = [...suiteCases.value, ...newCases]
  ElMessage.success(`已添加 ${newIds.length} 个用例（请点击保存提交）`)
  dialogVisible.value = false
}

// ===== 加载套件已有用例 =====
async function loadSuiteCases() {
  try {
    const suite = await fetchSuiteById(suiteId)
    suiteName.value = suite.name || ''
    // 登录态状态：优先取后端返回的 loginEnabled，兼容旧的 useLoginState
    useLoginState.value = suite.loginEnabled !== undefined ? !!suite.loginEnabled : (suite.useLoginState ?? false)
    // 登录态 text：优先取后端返回的 logintext，兼容旧的 loginFetchCommand
    loginFetchCommand.value = suite.logintext || suite.loginFetchCommand || ''
    const caseIds: string[] = (suite.caseIds || []).map((id: any) => String(id))
    suiteCaseIds.value = caseIds
    if (caseIds.length === 0) {
      suiteCases.value = []
      return
    }
    // 通过新接口获取套件内用例（副本优先，无副本继承父用例）
    const list = await fetchSuiteCases(suiteId)
    suiteCases.value = list.map((item: any) => ({
      id: String(item.caseId),
      title: item.name || '',
      priority: item.priority != null ? `P${item.priority}` : '',
      module: item.categoryName || '—',
      precondition: item.targetUrl || '',
      steps: Array.isArray(item.steps)
        ? item.steps.map((s: any) => s.step || s.prompt || '').join('\n')
        : '',
      expected: item.expectedResult || '',
      remark: '',
      routeUrl: item.targetUrl || '',
      creator: '—',
      copyStatus: item.copyStatus ?? null
    }))
  } catch {
    ElMessage.error('套件不存在')
    router.replace('/suites')
  }
}

// ===== 保存 =====
const saving = ref(false)
async function handleSave() {
  saving.value = true
  try {
    await updateSuite(suiteId, { caseIds: suiteCaseIds.value })
    // 顺便保存登录态（始终调用，loginEnabled 跟随开关状态）
    await saveLoginFetchCommand(suiteId, loginFetchCommand.value, useLoginState.value ? 1 : 0)
    // 保存成功后重新拉取套件用例，刷新预期结果/转换状态，无需手动刷新页面
    await loadSuiteCases()
    ElMessage.success('保存成功，新增用例的自然语言步骤正在后台 AI 转换')
  } catch {
    ElMessage.error('保存失败，请重试')
  } finally {
    saving.value = false
  }
}

// ===== 行内编辑 =====
const editingId = ref<number | string | null>(null)
const editForm = reactive({ precondition: '', steps: '', expected: '', remark: '' })

function startEdit(row: any) {
  editingId.value = row.id
  editForm.precondition = row.precondition || ''
  editForm.steps = row.steps || ''
  editForm.expected = row.expected || ''
  editForm.remark = row.remarks || row.remark || ''
}

function cancelEdit() {
  editingId.value = null
}

async function saveEdit(caseId: number | string) {
  const row = suiteCases.value.find((c) => String(c.id) === String(caseId))
  if (!row) return
  // textarea 按行拆分 → 自然语言步骤数组（不含 action，触发后端异步 AI 转换）
  const steps = editForm.steps
    .split('\n')
    .map((s: string) => s.trim())
    .filter(Boolean)
    .map((s: string) => ({ step: s }))
  const expectedResult = editForm.expected?.trim() || null
  try {
    await updateSuiteCaseCopy(suiteId, caseId, { steps, expectedResult })
    // 更新本地行数据，copyStatus 置为转换中
    row.steps = editForm.steps
    row.expected = editForm.expected
    row.copyStatus = 'PENDING'
    editingId.value = null
    if (editForm.precondition !== row.precondition) {
      ElMessage.warning('前置条件继承父用例，副本保存不包含前置条件，如需修改请到用例库编辑')
    }
    ElMessage.success('保存成功，AI 转换中...')
  } catch {
    ElMessage.error('保存失败，请重试')
  }
}

async function handleDelete(caseId: number | string) {
  try {
    await ElMessageBox.confirm('确定要从套件中移除该用例吗？', '提示', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' })
  } catch {
    return // 用户取消
  }
  try {
    // 调用删除接口：删除套件关联 + 套件副本，不影响父用例
    await removeSuiteCase(suiteId, caseId)
    suiteCaseIds.value = suiteCaseIds.value.filter((id) => String(id) !== String(caseId))
    suiteCases.value = suiteCases.value.filter((c) => String(c.id) !== String(caseId))
    ElMessage.success('已移除')
  } catch {
    ElMessage.error('移除失败，请重试')
  }
}

function priorityType(p: string) {
  const map: Record<string, string> = { P0: 'danger', P1: 'warning', P2: 'info', P3: '' }
  return map[p] || ''
}

function goBack() {
  router.push('/suites')
}
</script>

<style scoped>
.add-case-bar {
  margin: 16px 0;
}
/* 登录态配置 */
.login-state-bar {
  margin: 0 0 16px 0;
  padding: 16px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}
.login-state-row {
  display: flex;
  align-items: center;
  gap: 12px;
}
.login-state-label {
  font-size: 14px;
  font-weight: 500;
  color: #374151;
}
.login-help-icon {
  font-size: 15px;
  color: #9ca3af;
  cursor: help;
  transition: color 0.2s;
}
.login-help-icon:hover {
  color: #2563eb;
}
.login-help-content {
  max-width: 480px;
  line-height: 1.6;
}
.login-help-title {
  font-weight: 600;
  margin-bottom: 4px;
}
.login-help-step {
  margin-bottom: 6px;
}
.login-help-script {
  margin: 0;
  padding: 8px 10px;
  background: #1f2937;
  color: #e5e7eb;
  border-radius: 6px;
  font-size: 11px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
.login-help-script-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
}
.login-help-copy-btn {
  align-self: flex-end;
}
.login-fetch-area {
  margin-top: 12px;
}
.login-save-row {
  margin-top: 12px;
}
.fetch-textarea {
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 12px;
}
/* 套件标题可点击 */
.case-list-title {
  cursor: pointer;
  user-select: none;
  transition: color 0.2s;
}
.case-list-title:hover {
  color: #2563eb;
}
.case-list-title.has-login {
  color: #059669;
}
.login-badge {
  display: inline-block;
  margin-left: 10px;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 500;
  color: #059669;
  background: #d1fae5;
  border-radius: 10px;
  vertical-align: middle;
}
.table-empty {
  padding: 32px;
  text-align: center;
  color: #9ca3af;
  font-size: 14px;
}
.btn-icon {
  margin-right: 4px;
  vertical-align: -1px;
}

/* 弹框内筛选栏 */
.dialog-filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px 16px;
}
.filter-label {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
  white-space: nowrap;
}
.filter-select {
  width: 130px;
}
.filter-input {
  width: 150px;
}
.filter-input--sm {
  width: 90px;
}
.filter-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
  white-space: nowrap;
}

/* 表格列对齐 */
.table-header,
.table-row {
  grid-template-columns: 80px 50px 90px minmax(100px, 1fr) 65px 70px minmax(80px, 0.8fr) minmax(120px, 1.2fr) minmax(120px, 1.2fr) 90px minmax(70px, 0.7fr) 65px 90px;
}
.table-header span,
.table-row span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 已选用例预览 */
.selected-preview {
  margin-top: 12px;
  padding: 12px;
  background: #f0f9ff;
  border: 1px solid #bae6fd;
  border-radius: 8px;
}
.selected-preview-title {
  font-size: 13px;
  font-weight: 600;
  color: #0369a1;
  margin-bottom: 8px;
}
.selected-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.inline-textarea {
  width: 100%;
}
</style>
