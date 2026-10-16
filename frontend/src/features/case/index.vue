<template>
  <div class="case-layout">
    <div class="case-sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="case-sidebar-inner" v-show="!sidebarCollapsed">
        <div class="case-sidebar-header">用例分类</div>
        <el-input
          placeholder="输入关键字进行过滤"
          v-model="filterText"
          clearable
          size="small"
        />
        <el-tree
          class="filter-tree"
          :data="caseTreeData"
          :props="defaultProps"
          default-expand-all
          :filter-node-method="filterNode"
          @node-click="handleNodeClick"
          ref="treeRef"
        />
      </div>
      <button class="sidebar-toggle" @click="sidebarCollapsed = !sidebarCollapsed">
        <span v-if="sidebarCollapsed">›</span>
        <span v-else>‹</span>
      </button>
    </div>
    <div class="card case-card full-screen-table case-main">
      <div class="case-list-header">
        <div class="case-list-title">测试用例列表</div>
        <button class="btn primary" @click="goToCreate">新建</button>
      </div>

      <div class="table-placeholder">
        <div class="table-header">
          <span>ID</span>
          <span>序号</span>
          <span>用例名称</span>
          <span>重要程度</span>
          <span>前置条件</span>
          <span>步骤</span>
          <span>预期结果</span>
          <span>备注</span>
          <span>操作</span>
        </div>
        <div class="table-row" v-for="row in pagedRows" :key="row.id">
          <span :title="row.id">{{ row.id }}</span>
          <span :title="row.index">{{ row.index }}</span>
          <span><a class="case-link" @click="goToCaseView(row.id)" :title="row.title">{{ row.title }}</a></span>
          <span :title="row.priority">{{ row.priority || '—' }}</span>
          <span :title="row.precondition">{{ row.precondition }}</span>
          <span :title="row.steps">{{ row.steps }}</span>
          <span :title="row.expected">{{ row.expected }}</span>
          <span :title="row.remark">{{ row.remark }}</span>
          <span>
            <el-button type="primary" link size="small" @click="goToCaseEdit(row.id)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row.id)">删除</el-button>
          </span>
        </div>
      </div>
      <PaginationControl
        :total-items="totalItems"
        :current-page="currentPage"
        :page-size="pageSize"
        :page-sizes="pageSizes"
        @update:currentPage="changePage"
        @update:pageSize="updatePageSize"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import PaginationControl from '../../shared/components/PaginationControl.vue'
import { useCaseStore } from './caseStore'
import { deleteCase } from './api'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const { caseListRows, caseTotal, caseTreeData, loadCategoryTree, loadCaseList } = useCaseStore()

// ===== 服务端分页：页码/每页条数都是后端参数，前端不再本地切片 =====
const pageSizes = [5, 10, 20, 30, 50]
const pageSize = ref(10)
const currentPage = ref(1)
const totalItems = computed(() => caseTotal.value)

function reload(resetPage = false) {
  if (resetPage) currentPage.value = 1
  loadCaseList(currentPage.value, pageSize.value)
}

onMounted(() => {
  loadCategoryTree()
  reload()
})

function changePage(page) {
  currentPage.value = page
  loadCaseList(currentPage.value, pageSize.value)
}

function updatePageSize(size) {
  pageSize.value = size
  reload(true)
}

// 序号按「服务端页码」累计，不再依赖本地数组下标
const pagedRows = computed(() =>
  caseListRows.value.map((row, index) => ({
    ...row,
    index: (currentPage.value - 1) * pageSize.value + index + 1
  }))
)

function goToCreate() {
  router.push('/cases/new')
}

function goToCaseEdit(id) {
  router.push(`/cases/${id}/edit`)
}

function goToCaseView(id) {
  router.push(`/cases/${id}/edit?readonly=1`)
}

async function handleDelete(id) {
  try {
    await ElMessageBox.confirm('确定要删除该用例吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteCase(id)
    ElMessage.success('删除成功')
    // 删的正好是当前页最后一条时，回退一页，避免停在空页
    if (caseListRows.value.length <= 1 && currentPage.value > 1) {
      currentPage.value -= 1
    }
    reload()
  } catch {
    // 取消或失败
  }
}

// ============ 树形控件 ============
const sidebarCollapsed = ref(false)
const filterText = ref('')
const treeRef = ref(null)

const defaultProps = {
  children: 'children',
  label: 'label'
}

watch(filterText, (val) => {
  treeRef.value?.filter(val)
})

function filterNode(value, data) {
  if (!value) return true
  return data.label.indexOf(value) !== -1
}

function handleNodeClick(data) {
  // 点击节点时可根据分类过滤表格
}
</script>

<style scoped>
.case-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.case-sidebar {
  width: 240px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  position: relative;
  transition: width 0.25s ease, padding 0.25s ease;
  overflow: hidden;
}
.case-sidebar.collapsed {
  width: 40px;
  padding: 16px 8px;
}
.case-sidebar-inner {
  width: 208px;
}
.case-sidebar-header {
  font-size: 15px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 12px;
  white-space: nowrap;
}
.case-sidebar .el-input {
  margin-bottom: 8px;
}
.case-sidebar .el-tree {
  font-size: 13px;
}
.sidebar-toggle {
  position: absolute;
  top: 50%;
  right: 2px;
  transform: translateY(-50%);
  width: 22px;
  height: 22px;
  border: none;
  background: #f3f4f6;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #6b7280;
  line-height: 1;
  transition: background 0.2s, right 0.25s ease;
  padding: 0;
  z-index: 1;
}
.sidebar-toggle:hover {
  background: #e5e7eb;
  color: #374151;
}
.case-sidebar.collapsed .sidebar-toggle {
  right: 9px;
}
.case-main {
  flex: 1;
  min-width: 0;
}
.card.case-card.full-screen-table {
  width: 100% !important;
  max-width: none !important;
  margin: 0 !important;
}
.table-placeholder {
  background: rgba(255,255,255,0.9);
  width: 100% !important;
  max-width: none !important;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 6px 18px rgba(15,23,42,0.06);
}
.table-header {
  display: grid;
  grid-template-columns: 80px 1fr 120px 1fr 2fr 1fr 1fr;
  gap: 16px;
  padding: 14px 18px;
  align-items: center;
  font-weight: 700;
  color: #111827;
}
.table-row {
  display: grid;
  grid-template-columns: 80px 1fr 120px 1fr 2fr 1fr 1fr;
  gap: 16px;
  align-items: center;
  margin-top: 12px;
}
.table-row > span, .table-row > div {
  width: 100%;
}
.table-row .card-item {
  background: #ffffff;
  border: 1px solid rgba(229,231,235,0.8);
  padding: 14px;
  border-radius: 12px;
  box-shadow: 0 8px 18px rgba(15,23,42,0.04);
}
.table-row .card-item small {
  display:block;
  margin-top:6px;
  color:#6b7280;
}
.priority-pill {
  display:inline-block;
  padding:6px 10px;
  border-radius:999px;
  background:#f3f4f6;
  color:#111827;
  font-weight:600;
  text-align:center;
}
.table-row .table-cell-steps {
  color:#374151;
}
.case-list-header { display:flex; justify-content:space-between; align-items:center; }

/* 表格列对齐 */
.case-main .table-header,
.case-main .table-row {
  grid-template-columns: 130px 50px minmax(140px, 1.5fr) 80px minmax(110px, 1fr) minmax(110px, 1fr) minmax(130px, 1.2fr) minmax(90px, 1fr) 100px;
}

.case-main .table-row > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.case-main .table-row > span:last-child {
  overflow: visible;
  white-space: nowrap;
}

.case-link {
  color: #2563eb;
  cursor: pointer;
  text-decoration: none;
}
.case-link:hover {
  text-decoration: underline;
  color: #1d4ed8;
}
</style>
