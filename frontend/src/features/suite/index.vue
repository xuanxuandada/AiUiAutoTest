<template>
  <div class="suite-layout">
    <div class="suite-sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="suite-sidebar-inner" v-show="!sidebarCollapsed">
        <div class="suite-sidebar-header">测试套件分类</div>
        <el-input
          placeholder="输入关键字进行过滤"
          v-model="filterText"
          clearable
          size="small"
        />
        <el-tree
          class="filter-tree"
          :data="treeData"
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
    <div class="card case-card full-screen-table suite-main">
      <div class="case-list-header">
        <div class="case-list-title">测试套件列表</div>
      </div>
      <div class="filter-bar">
        <div class="filter-row">
          <span class="filter-label">ID</span>
          <el-input
            v-model="idText"
            placeholder="请输入ID"
            clearable
            class="filter-input filter-input--sm"
            @keyup.enter="doSearch"
          />
          <span class="filter-label">套件名称</span>
          <el-input
            v-model="searchText"
            placeholder="请输入套件名称"
            clearable
            class="filter-input"
            @keyup.enter="doSearch"
          />
          <div class="filter-actions">
            <el-button type="primary" @click="doSearch">
              <el-icon class="btn-icon"><Search /></el-icon>
              查询
            </el-button>
            <el-button @click="resetSearch">
              <el-icon class="btn-icon"><Refresh /></el-icon>
              重置
            </el-button>
          </div>
        </div>
        <div class="filter-row filter-row--right">
          <el-button type="success" @click="goToCreate">
            <el-icon class="btn-icon"><Plus /></el-icon>
            新建套件
          </el-button>
        </div>
      </div>
      <div class="table-placeholder">
        <div class="table-header">
          <span>ID</span>
          <span>序号</span>
          <span>套件名称</span>
          <span>更新时间</span>
          <span>操作</span>
        </div>
        <div class="table-row" v-for="row in pagedRows" :key="row.id">
          <span :title="row.id">{{ row.id }}</span>
          <span :title="row.index">{{ row.index }}</span>
          <span><a class="suite-link" @click="goToCasesView(row.id)" :title="row.name">{{ row.name }}</a></span>
          <span :title="row.updated">{{ row.updated }}</span>
          <span>
            <el-button type="success" link size="small" @click="handleLaunch(row)">启动</el-button>
            <el-button type="primary" link size="small" @click="goToCases(row.id)">编辑用例</el-button>
            <el-button type="primary" link size="small" @click="goToEdit(row.id)">编辑</el-button>
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
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import PaginationControl from '../../shared/components/PaginationControl.vue'
import { useSuiteStore } from './suiteStore'
import { launchSuite } from './api'
import { ElMessageBox, ElMessage } from 'element-plus'

const router = useRouter()
const { rows, total: suiteTotal, searchKeyword, idKeyword, loadSuiteList } = useSuiteStore()

// ===== 服务端分页：页码/每页条数都是后端参数，前端不再本地切片 =====
const pageSizes = [5, 10, 20, 30, 50]
const pageSize = ref(10)
const currentPage = ref(1)
const totalItems = computed(() => suiteTotal.value)

function reload(resetPage = false) {
  if (resetPage) currentPage.value = 1
  loadSuiteList(currentPage.value, pageSize.value)
}

onMounted(() => {
  reload()
})

const idText = ref('')
const searchText = ref('')

function doSearch() {
  idKeyword.value = idText.value.trim()
  searchKeyword.value = searchText.value.trim()
  // 筛选条件变化后回到第 1 页
  reload(true)
}

function resetSearch() {
  idText.value = ''
  searchText.value = ''
  idKeyword.value = ''
  searchKeyword.value = ''
  reload(true)
}

function changePage(page) {
  currentPage.value = page
  loadSuiteList(currentPage.value, pageSize.value)
}

function updatePageSize(size) {
  pageSize.value = size
  reload(true)
}

const pagedRows = computed(() =>
  rows.value.map((row, index) => ({
    ...row,
    index: (currentPage.value - 1) * pageSize.value + index + 1
  }))
)

// ============ 树形控件 ============
const sidebarCollapsed = ref(false)
const filterText = ref('')
const treeRef = ref(null)

const treeData = [
  {
    id: 1,
    label: '全部分类',
    children: [
      {
        id: 2,
        label: '回归测试',
        children: [
          { id: 5, label: '核心功能回归' },
          { id: 6, label: '边界回归' }
        ]
      },
      {
        id: 3,
        label: '冒烟测试',
        children: [
          { id: 7, label: '主流程冒烟' },
          { id: 8, label: '登录冒烟' }
        ]
      },
      {
        id: 4,
        label: '功能测试',
        children: [
          { id: 9, label: '新增功能' },
          { id: 10, label: '历史功能' }
        ]
      }
    ]
  }
]

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
  // TODO: 根据选中节点过滤表格数据
}

function goToCreate() {
  router.push('/suites/new')
}

function goToEdit(id) {
  router.push(`/suites/${id}/edit`)
}

function goToCases(id) {
  router.push(`/suites/${id}/cases`)
}

function goToCasesView(id) {
  router.push(`/suites/${id}/cases?readonly=1`)
}

async function handleLaunch(row) {
  try {
    await ElMessageBox.confirm(
      `确定要启动套件「${row.name}」吗？`,
      '启动确认',
      { confirmButtonText: '确定启动', cancelButtonText: '取消', type: 'warning' }
    )
    await launchSuite(row.id)
    ElMessage.success(`套件「${row.name}」已启动`)
  } catch (e) {
    if (e?.message && e.message !== 'cancel') {
      ElMessage.error(e.message)
    }
  }
}
</script>

<style scoped>
.suite-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.suite-sidebar {
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
.suite-sidebar.collapsed {
  width: 40px;
  padding: 16px 8px;
}
.suite-sidebar-inner {
  width: 208px;
}
.suite-sidebar-header {
  font-size: 15px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 12px;
  white-space: nowrap;
}
.suite-sidebar .el-input {
  margin-bottom: 8px;
}
.suite-sidebar .el-tree {
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
.suite-sidebar.collapsed .sidebar-toggle {
  right: 9px;
}
.suite-main {
  flex: 1;
  min-width: 0;
}
/* ===== 筛选栏 ===== */
.filter-bar {
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 12px 16px;
  margin-bottom: 16px;
}
.filter-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.filter-row + .filter-row {
  margin-top: 12px;
}
.filter-label {
  font-size: 14px;
  font-weight: 500;
  color: #374151;
  white-space: nowrap;
}
.filter-input {
  width: 180px;
}
.filter-input--sm {
  width: 100px;
}
.filter-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
  white-space: nowrap;
}
.filter-row--right {
  justify-content: flex-end;
}
.btn-icon {
  margin-right: 4px;
  vertical-align: -1px;
}

/* 表格列对齐 */
.suite-main .table-header,
.suite-main .table-row {
  grid-template-columns: 130px 50px minmax(180px, 2fr) 120px 200px;
}

.suite-main .table-row > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 表格行悬停 */
.suite-main .table-row {
  transition: all 0.2s ease;
}
.suite-main .table-row:hover {
  background: #f0f4ff;
  border-color: #93c5fd;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.08);
}

/* 套件名称链接 */
.suite-link {
  color: #2563eb;
  cursor: pointer;
  text-decoration: none;
}
.suite-link:hover {
  text-decoration: underline;
  color: #1d4ed8;
}

/* 操作按钮间距 */
.suite-main .table-row .el-button + .el-button {
  margin-left: 4px;
}

/* 表格容器 */
.suite-main .table-placeholder {
  background: #ffffff;
  border-radius: 12px;
  padding: 8px 16px 16px;
}

/* 侧边栏优化 */
.suite-sidebar .el-tree {
  font-size: 13px;
  background: transparent;
}
.suite-sidebar :deep(.el-tree-node__content) {
  border-radius: 8px;
  padding: 4px 8px;
  transition: background 0.15s;
}
.suite-sidebar :deep(.el-tree-node__content:hover) {
  background: #f0f4ff;
}
.suite-sidebar :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background: #e0e7ff;
  color: #1d4ed8;
}
</style>
