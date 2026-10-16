<template>
  <div class="role-page">
    <!-- 左侧租户树 -->
    <div ref="sidebarRef" class="role-sidebar">
      <div class="sidebar-title">租户列表</div>
      <el-tree
        ref="treeRef"
        :data="tenantTree"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
        highlight-current
        default-expand-all
        @node-click="onTreeClick"
      />
      <div v-if="treeLoading" class="tree-loading">加载中...</div>
    </div>

    <!-- 右侧 -->
    <div class="role-main">
      <div class="card">
        <div class="card-header">
          <span class="card-title">角色列表</span>
          <el-button type="primary" @click="openAddDialog">新增角色</el-button>
        </div>

        <div class="toolbar">
          <el-select
            v-model="filterTenantId"
            placeholder="按租户筛选"
            clearable
            class="tenant-filter"
            @visible-change="onFilterTenantSelectOpen"
          >
            <el-option
              v-for="t in filterTenantOptions"
              :key="t.id"
              :label="t.name"
              :value="t.id"
            />
            <el-option
              v-if="filterTenantsLoading"
              value=""
              disabled
            >
              <span class="loading-text">加载中...</span>
            </el-option>
          </el-select>
          <span v-if="filterTenantsError" class="select-error-inline">{{ filterTenantsError }}</span>
          <el-input
            v-model="searchText"
            placeholder="搜索角色名称"
            prefix-icon="Search"
            clearable
            class="search-input"
          />
        </div>

      <el-table :data="filteredRoles" stripe>
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="name" label="角色名称" min-width="80" />
        <el-table-column prop="tenantName" label="租户" min-width="70">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.tenantName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="100" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="70">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEditDialog(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>

    <!-- 新增/编辑角色弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? '编辑角色' : '新增角色'"
      width="480px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" size="large">
        <el-form-item label="所属租户" prop="tenantId">
          <el-select
            v-model="form.tenantId"
            placeholder="请选择租户"
            style="width: 100%"
            @visible-change="onRoleTenantSelectOpen"
          >
            <el-option
              v-for="t in selectTenants"
              :key="t.id"
              :label="t.name"
              :value="t.id"
            />
            <el-option
              v-if="selectTenantsLoading"
              value=""
              disabled
            >
              <span class="loading-text">加载中...</span>
            </el-option>
          </el-select>
          <div v-if="selectTenantsError" class="select-error">{{ selectTenantsError }}</div>
        </el-form-item>
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="form.code" placeholder="请输入角色编码" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="2"
            placeholder="请输入角色描述"
          />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch
            v-model="form.status"
            active-text="启用"
            inactive-text="停用"
            :active-value="'active'"
            :inactive-value="'inactive'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ saving ? '保存中...' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { getTenantOptions, getAllRoles, saveRole, deleteRole } from './api'
import { useSelectLoadMore } from '../../shared/composables/useSelectLoadMore'
import type { TenantOption, TenantRole, SaveTenantRoleParams } from './types'

// ===== 状态 =====
const formRef = ref<FormInstance | null>(null)
const treeRef = ref(null)
const sidebarRef = ref<HTMLElement | null>(null)
const saving = ref(false)
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<string | number>('')
const searchText = ref('')
const filterTenantId = ref<string | number | ''>('')
const {
  options: filterTenantOptions,
  loading: filterTenantsLoading,
  error: filterTenantsError,
  noMore: filterTenantsNoMore,
  loadMore: loadMoreFilterTenants,
  reset: resetFilterTenants
} = useSelectLoadMore((page, pageSize) => getTenantOptions(page, pageSize), 50)
const {
  options: selectTenants,
  loading: selectTenantsLoading,
  error: selectTenantsError,
  noMore: selectTenantsNoMore,
  loadMore: loadMoreSelectTenants,
  reset: resetSelectTenants
} = useSelectLoadMore((page, pageSize) => getTenantOptions(page, pageSize), 50)
// 左侧树独立数据源，不与筛选下拉共享，避免互相干扰
const {
  options: treeTenants,
  loading: treeLoading,
  error: treeError,
  loadMore: loadMoreTreeTenants
} = useSelectLoadMore((page, pageSize) => getTenantOptions(page, pageSize), 50)

const roles = ref<TenantRole[]>([])

// ===== 租户树 =====
interface TreeNode {
  id: string
  name: string
  tenantId?: string | number
  children?: TreeNode[]
}

const tenantTree = computed<TreeNode[]>(() => [
  {
    id: 'all',
    name: '全部租户',
    tenantId: '',
    children: treeTenants.value.map(t => ({
      id: String(t.id),
      name: t.name,
      tenantId: t.id
    }))
  }
])

function onTreeClick(data: TreeNode) {
  filterTenantId.value = data.tenantId ?? ''
}

const form = reactive({
  tenantId: '' as string | number,
  name: '',
  code: '',
  description: '',
  status: 'active' as 'active' | 'inactive'
})

const rules = {
  tenantId: [{ required: true, message: '请选择所属租户', trigger: 'change' }],
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }]
}

// ===== 显示用的角色列表 =====
const displayRoles = computed<TenantRole[]>(() => roles.value)

const filteredRoles = computed(() => {
  let list = displayRoles.value
  if (filterTenantId.value !== '' && filterTenantId.value !== null) {
    list = list.filter(r => r.tenantId === filterTenantId.value)
  }
  const kw = searchText.value.trim().toLowerCase()
  if (kw) {
    list = list.filter(r => r.name.toLowerCase().includes(kw))
  }
  return list
})

// ===== 下拉滚动加载 =====
// 通过 document 级 scroll 事件委托实现，不依赖 Element Plus 内部 DOM 结构
type LoadMoreFn = () => void

let activeLoadMore: LoadMoreFn | null = null

function onDocumentScroll(e: Event) {
  if (!activeLoadMore) return
  const target = e.target as HTMLElement
  // 判断 scroll 事件来源是否为 el-select 下拉面板内部的滚动容器
  const isSelectDropdown =
    target.classList.contains('el-scrollbar__wrap') ||
    target.classList.contains('el-select-dropdown__wrap') ||
    target.closest('.el-select-dropdown') !== null
  if (!isSelectDropdown) return

  const { scrollTop, scrollHeight, clientHeight } = target
  if (scrollHeight - scrollTop - clientHeight < 20) {
    activeLoadMore()
  }
}

document.addEventListener('scroll', onDocumentScroll, true)

function onFilterTenantSelectOpen(visible: boolean) {
  if (visible) {
    if (filterTenantOptions.value.length === 0 && !filterTenantsLoading.value && (!filterTenantsNoMore.value || filterTenantsError.value)) {
      resetFilterTenants()
    }
    activeLoadMore = loadMoreFilterTenants
  } else {
    activeLoadMore = null
  }
}

// ===== 新增/编辑弹窗租户下拉 =====
function onRoleTenantSelectOpen(visible: boolean) {
  if (visible) {
    if (selectTenants.value.length === 0 && !selectTenantsLoading.value && (!selectTenantsNoMore.value || selectTenantsError.value)) {
      resetSelectTenants()
    }
    activeLoadMore = loadMoreSelectTenants
  } else {
    activeLoadMore = null
  }
}

// ===== 左侧树滚动加载 =====
function onSidebarScroll() {
  const el = sidebarRef.value
  if (!el) return
  const { scrollTop, scrollHeight, clientHeight } = el
  if (scrollHeight - scrollTop - clientHeight < 30) {
    loadMoreTreeTenants()
  }
}

onMounted(async () => {
  // 树初始加载
  loadMoreTreeTenants()
  // 筛选下拉初始加载
  loadMoreFilterTenants()
  // 弹窗下拉预加载，避免打开时 select 反显原始 ID
  loadMoreSelectTenants()
  try {
    roles.value = await getAllRoles()
  } catch (e: any) {
    ElMessage.error(e?.message || '获取角色列表失败')
  }
  // 监听侧边栏滚动
  sidebarRef.value?.addEventListener('scroll', onSidebarScroll)
})

onBeforeUnmount(() => {
  document.removeEventListener('scroll', onDocumentScroll, true)
  sidebarRef.value?.removeEventListener('scroll', onSidebarScroll)
  activeLoadMore = null
})

function openAddDialog() {
  isEditing.value = false
  editingId.value = ''
  form.tenantId = filterTenantId.value !== '' ? filterTenantId.value : (selectTenants.value[0]?.id ?? '')
  form.name = ''
  form.code = ''
  form.description = ''
  form.status = 'active'
  dialogVisible.value = true
}

async function openEditDialog(row: TenantRole) {
  isEditing.value = true
  editingId.value = row.id
  form.tenantId = row.tenantId
  form.name = row.name
  form.code = row.code || ''
  form.description = row.description
  form.status = row.status
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const params: SaveTenantRoleParams = {
      id: isEditing.value ? editingId.value : undefined,
      tenantId: form.tenantId,
      name: form.name,
      code: form.code,
      description: form.description,
      status: form.status
    }

    const saved = await saveRole(params)

    if (isEditing.value) {
      const idx = roles.value.findIndex(r => r.id === editingId.value)
      if (idx !== -1) {
        roles.value[idx] = { ...roles.value[idx], ...saved }
      }
      ElMessage.success('编辑成功')
    } else {
      roles.value.push(saved)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '保存角色失败')
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: TenantRole) {
  try {
    await ElMessageBox.confirm(`确认删除角色「${row.name}」吗？`, '删除确认', {
      type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
    })
    await deleteRole(row.id)
    roles.value = roles.value.filter(r => r.id !== row.id)
    ElMessage.success('删除成功')
  } catch (e: any) {
    if (e?.message && e.message !== 'cancel') {
      ElMessage.error(e.message)
    }
  }
}
</script>

<style scoped>
.role-page {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.role-sidebar {
  width: 160px;
  flex-shrink: 0;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 12px;
  padding: 10px;
  font-size: 13px;
  max-height: calc(100vh - 100px);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}
.tree-loading {
  text-align: center;
  color: #909399;
  font-size: 12px;
  padding: 6px 0;
}
.sidebar-title {
  font-size: 13px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 8px;
}
.role-main {
  flex: 1;
  min-width: 0;
}
.role-main .card {
  width: auto !important;
  max-width: none !important;
}
.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
  padding: 20px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
}
.tenant-filter {
  width: 160px;
}
.search-input {
  max-width: 240px;
}
.select-error {
  font-size: 12px;
  color: #f56c6c;
  margin-top: 4px;
  line-height: 1.4;
}
.select-error-inline {
  font-size: 12px;
  color: #f56c6c;
  margin-left: 8px;
  white-space: nowrap;
}
.loading-text {
  color: #909399;
  font-size: 13px;
}
</style>

<style>
.role-page .card {
  width: 100% !important;
  max-width: none !important;
}
</style>
