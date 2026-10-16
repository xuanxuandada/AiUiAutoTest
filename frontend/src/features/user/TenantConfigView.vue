<template>
  <div class="tenant-config-page">
    <!-- 租户列表 -->
    <div class="card">
      <div class="card-header">
        <span class="card-title">租户列表</span>
        <el-button type="primary" @click="openCreateDialog">新建租户</el-button>
      </div>

      <div class="toolbar">
        <el-input
          v-model="searchText"
          placeholder="搜索租户名称 / 编码 / 联系人"
          prefix-icon="Search"
          clearable
          class="search-input"
        />
      </div>

      <el-table :data="tenants" stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" :index="getTableIndex" />
        <el-table-column prop="name" label="租户名称" min-width="120" />
        <el-table-column prop="code" label="租户编码" min-width="120" />
        <el-table-column prop="contact" label="联系人" min-width="100" />
        <el-table-column prop="phone" label="联系电话" min-width="120" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openRoleDialog(row)">角色配置</el-button>
            <el-button type="primary" link @click="openEditDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <PaginationControl
        :total-items="totalItems"
        :current-page="currentPage"
        :page-size="pageSize"
        @update:currentPage="onPageChange"
        @update:pageSize="onPageSizeChange"
      />
    </div>

    <!-- 新建/编辑租户弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isCreating ? '新建租户' : '编辑租户 — ' + editForm.name"
      width="560px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="editForm"
        :rules="rules"
        label-width="90px"
        size="large"
      >
        <el-form-item label="租户名称" prop="name">
          <el-input v-model="editForm.name" placeholder="请输入租户名称" />
        </el-form-item>
        <el-form-item label="租户编码" prop="code">
          <el-input v-model="editForm.code" placeholder="请输入租户编码" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch
            v-model="editForm.status"
            active-text="启用"
            inactive-text="停用"
            :active-value="'active'"
            :inactive-value="'inactive'"
          />
        </el-form-item>
        <el-form-item label="联系人" prop="contact">
          <el-input v-model="editForm.contact" placeholder="请输入联系人" />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model="editForm.phone" placeholder="请输入联系电话" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ saving ? '保存中...' : '保存' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 租户角色配置弹窗 -->
    <el-dialog
      v-model="roleDialogVisible"
      :title="`角色配置 — ${currentTenantName}`"
      width="900px"
      destroy-on-close
      @closed="onRoleDialogClosed"
    >
      <!-- 角色列表 -->
      <div class="role-toolbar">
        <span class="role-section-title">角色列表</span>
        <el-button type="primary" size="small" @click="openAddRole">新增角色</el-button>
      </div>
      <el-table :data="currentTenantRoles" stripe style="width: 100%; margin-bottom: 16px">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="name" label="角色名称" min-width="130" />
        <el-table-column prop="description" label="描述" min-width="180" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEditRole(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDeleteRole(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="roleDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑角色弹窗 -->
    <el-dialog
      v-model="roleFormVisible"
      :title="isEditingRole ? '编辑角色' : '新增角色'"
      width="480px"
      destroy-on-close
    >
      <el-form ref="roleFormRef" :model="roleForm" :rules="roleRules" label-width="80px" size="large">
        <el-form-item label="角色名称" prop="name">
          <el-input v-model="roleForm.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input v-model="roleForm.code" placeholder="请输入角色编码" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="roleForm.description"
            type="textarea"
            :rows="2"
            placeholder="请输入角色描述"
          />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch
            v-model="roleForm.status"
            active-text="启用"
            inactive-text="停用"
            :active-value="'active'"
            :inactive-value="'inactive'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="roleFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRole" @click="handleSaveRole">
          {{ savingRole ? '保存中...' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import PaginationControl from '../../shared/components/PaginationControl.vue'
import { getTenantList, searchTenants, saveTenantConfig, getTenantRoles, saveRole, deleteRole } from './api'
import type { TenantConfig, TenantRole, SaveTenantRoleParams } from './types'

// ===== 租户相关状态 =====
const formRef = ref<FormInstance | null>(null)
const saving = ref(false)
const dialogVisible = ref(false)
const isCreating = ref(false)
const searchText = ref('')
const tenants = ref<TenantConfig[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const totalItems = ref(0)
let searchTimer: ReturnType<typeof setTimeout> | null = null

// 加载数据
async function loadTenants() {
  try {
    const kw = searchText.value.trim()
    const result = kw
      ? await searchTenants(kw, currentPage.value, pageSize.value)
      : await getTenantList(currentPage.value, pageSize.value)
    tenants.value = result.records
    totalItems.value = result.total
  } catch (e: any) {
    ElMessage.error(e?.message || '获取租户列表失败')
  }
}

function onPageChange(page: number) {
  currentPage.value = page
  loadTenants()
}

function onPageSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadTenants()
}

function getTableIndex(idx: number) {
  return (currentPage.value - 1) * pageSize.value + idx + 1
}

// 监听搜索输入，防抖 300ms 调接口，重置到第一页
watch(searchText, () => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    currentPage.value = 1
    loadTenants()
  }, 300)
})

const editForm = reactive({
  id: '' as string | number,
  name: '',
  code: '',
  contact: '',
  phone: '',
  status: 'active' as 'active' | 'inactive'
})

const rules = {
  name: [{ required: true, message: '请输入租户名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入租户编码', trigger: 'blur' }]
}

// ===== 角色相关状态 =====
const roleFormRef = ref<FormInstance | null>(null)
const roleDialogVisible = ref(false)
const roleFormVisible = ref(false)
const savingRole = ref(false)
const isEditingRole = ref(false)
const editingRoleId = ref<string | number>('')
const currentTenantId = ref<string | number>('')
const currentTenantName = ref('')
const currentTenantRoles = ref<TenantRole[]>([])

const roleForm = reactive({
  name: '',
  code: '',
  description: '',
  status: 'active' as 'active' | 'inactive'
})

const roleRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入角色编码', trigger: 'blur' }]
}

onMounted(() => {
  loadTenants()
})

// ===== 租户操作 =====
function openCreateDialog() {
  isCreating.value = true
  editForm.id = ''
  editForm.name = ''
  editForm.code = ''
  editForm.contact = ''
  editForm.phone = ''
  editForm.status = 'active'
  dialogVisible.value = true
}

function openEditDialog(row: TenantConfig) {
  isCreating.value = false
  editForm.id = row.id
  editForm.name = row.name
  editForm.code = row.code
  editForm.contact = row.contact || ''
  editForm.phone = row.phone || ''
  editForm.status = row.status
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    await saveTenantConfig({
      id: isCreating.value ? undefined : editForm.id,
      name: editForm.name,
      code: editForm.code,
      contact: editForm.contact,
      phone: editForm.phone,
      status: editForm.status
    })
    ElMessage.success(isCreating.value ? '创建成功' : '保存成功')
    dialogVisible.value = false
    loadTenants()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// ===== 角色对话框 =====
async function openRoleDialog(row: TenantConfig) {
  currentTenantId.value = row.id
  currentTenantName.value = row.name
  try {
    currentTenantRoles.value = await getTenantRoles(row.id)
  } catch (e: any) {
    ElMessage.error(e?.message || '获取角色列表失败')
    currentTenantRoles.value = []
  }
  roleDialogVisible.value = true
}

function onRoleDialogClosed() {
  currentTenantRoles.value = []
}

// ===== 新增角色 =====
function openAddRole() {
  isEditingRole.value = false
  editingRoleId.value = ''
  roleForm.name = ''
  roleForm.code = ''
  roleForm.description = ''
  roleForm.status = 'active'
  roleFormVisible.value = true
}

// ===== 编辑角色 =====
function openEditRole(row: TenantRole) {
  isEditingRole.value = true
  editingRoleId.value = row.id
  roleForm.name = row.name
  roleForm.code = row.code || ''
  roleForm.description = row.description
  roleForm.status = row.status
  roleFormVisible.value = true
}

// ===== 保存角色 =====
async function handleSaveRole() {
  const valid = await roleFormRef.value?.validate().catch(() => false)
  if (!valid) return

  savingRole.value = true
  try {
    const params: SaveTenantRoleParams = {
      id: isEditingRole.value ? editingRoleId.value : undefined,
      tenantId: currentTenantId.value,
      name: roleForm.name,
      code: roleForm.code,
      description: roleForm.description,
      status: roleForm.status
    }

    const saved = await saveRole(params)

    if (isEditingRole.value) {
      const idx = currentTenantRoles.value.findIndex(r => r.id === editingRoleId.value)
      if (idx !== -1) {
        currentTenantRoles.value[idx] = { ...currentTenantRoles.value[idx], ...saved }
      }
      ElMessage.success('编辑成功')
    } else {
      currentTenantRoles.value.push(saved)
      ElMessage.success('新增成功')
    }
    roleFormVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '保存角色失败')
  } finally {
    savingRole.value = false
  }
}

// ===== 删除角色 =====
async function handleDeleteRole(row: TenantRole) {
  try {
    await ElMessageBox.confirm(`确认删除角色「${row.name}」吗？`, '删除确认', {
      type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
    })
    await deleteRole(row.id)
    currentTenantRoles.value = currentTenantRoles.value.filter(r => r.id !== row.id)
    ElMessage.success('删除成功')
  } catch (e: any) {
    if (e?.message && e.message !== 'cancel') {
      ElMessage.error(e.message)
    }
  }
}
</script>

<style scoped>
.tenant-config-page {
  max-width: 1100px;
}
.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
  padding: 24px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.toolbar {
  margin-bottom: 16px;
}
.search-input {
  max-width: 320px;
}
/* 角色配置区域 */
.role-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.role-section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}
</style>
