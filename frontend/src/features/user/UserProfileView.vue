<template>
  <div class="user-list-page">
    <div class="card">
      <div class="card-title">用户列表</div>

      <div class="toolbar">
        <el-input
          v-model="searchText"
          placeholder="搜索用户名 / 手机号"
          prefix-icon="Search"
          clearable
          class="search-input"
        />
      </div>

      <el-table v-loading="userLoading" :data="users" stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="name" label="用户名" min-width="120" />
        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column prop="age" label="年龄" width="80" align="center" />
        <el-table-column prop="tenantId" label="所属租户" min-width="120">
          <template #default="{ row }">
            {{ getTenantName(row.tenantId) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEditDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <PaginationControl
        :total-items="userTotal"
        :current-page="userPage"
        :page-size="userPageSize"
        :page-sizes="userPageSizes"
        @update:currentPage="changeUserPage"
        @update:pageSize="updateUserPageSize"
      />
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="'编辑用户 — ' + editForm.name"
      width="480px"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="editForm"
        :rules="rules"
        label-width="90px"
        size="large"
      >
        <el-form-item label="用户名" prop="name">
          <el-input v-model="editForm.name" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="年龄">
          <el-input-number v-model="editForm.age" :min="1" :max="150" />
        </el-form-item>
        <el-form-item label="所属租户" prop="tenantId">
          <el-select
            v-model="editForm.tenantId"
            placeholder="请选择租户"
            class="full-width"
            @visible-change="onTenantSelectOpen"
          >
            <el-option
              v-for="t in tenantOptions"
              :key="t.id"
              :label="t.name"
              :value="t.id"
            />
            <el-option
              v-if="tenantLoading"
              value=""
              disabled
              class="loading-option"
            >
              <span class="loading-text">加载中...</span>
            </el-option>
          </el-select>
          <div v-if="tenantError" class="select-error">{{ tenantError }}</div>
        </el-form-item>
        <el-form-item label="角色" prop="roleId">
          <el-select
            v-model="editForm.roleId"
            placeholder="请选择角色"
            class="full-width"
          >
            <el-option label="管理员" :value="1" />
            <el-option label="普通用户" :value="2" />
          </el-select>
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
import { ref, reactive, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import PaginationControl from '../../shared/components/PaginationControl.vue'
import { getUserList, searchUsers, saveUser, getTenantOptions } from './api'
import { useSelectLoadMore } from '../../shared/composables/useSelectLoadMore'
import type { UserItem, TenantOption } from './types'

const formRef = ref<FormInstance | null>(null)
const saving = ref(false)
const dialogVisible = ref(false)
const searchText = ref('')
const users = ref<UserItem[]>([])
const tableTenants = ref<TenantOption[]>([])
const {
  options: tenantOptions,
  loading: tenantLoading,
  error: tenantError,
  noMore: tenantNoMore,
  loadMore: loadMoreTenants,
  reset: resetTenants
} = useSelectLoadMore((page, pageSize) => getTenantOptions(page, pageSize))
// ===== 服务端分页：page/pageSize 传给后端，前端不再本地切片 =====
const userTotal = ref(0)
const userLoading = ref(false)
const userPage = ref(1)
const userPageSize = ref(10)
const userPageSizes = [5, 10, 20, 50]
let searchTimer: ReturnType<typeof setTimeout> | null = null

async function loadUsers(resetPage = false) {
  if (resetPage) userPage.value = 1
  userLoading.value = true
  try {
    const keyword = searchText.value.trim()
    const { records, total } = keyword
      ? await searchUsers(keyword, userPage.value, userPageSize.value)
      : await getUserList(userPage.value, userPageSize.value)
    users.value = records
    userTotal.value = total
  } catch (e: any) {
    ElMessage.error(e?.message || '加载用户列表失败')
  } finally {
    userLoading.value = false
  }
}

watch(searchText, () => {
  if (searchTimer) clearTimeout(searchTimer)
  // 搜索条件变化 → 回到第 1 页再查
  searchTimer = setTimeout(() => loadUsers(true), 300)
})

onMounted(() => loadUsers())

function changeUserPage(page: number) {
  userPage.value = page
  loadUsers()
}

function updateUserPageSize(size: number) {
  userPageSize.value = size
  loadUsers(true)
}

const editForm = reactive({
  id: '' as string | number,
  name: '',
  phone: '',
  age: 18,
  tenantId: '' as string | number,
  roleId: '' as string | number
})

const rules = {
  name: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  tenantId: [{ required: true, message: '请选择租户', trigger: 'change' }],
  roleId: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

function getTenantName(tenantId: string | number): string {
  return tableTenants.value.find(t => t.id === tenantId)?.name || `租户${tenantId}`
}

// ===== 下拉滚动加载（document 级事件委托） =====
type LoadMoreFn = () => void
let activeLoadMore: LoadMoreFn | null = null

function onDocumentScroll(e: Event) {
  if (!activeLoadMore) return
  const target = e.target as HTMLElement
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

function onTenantSelectOpen(visible: boolean) {
  if (visible) {
    // 仅在数据为空且未在加载中、且不是"已无更多数据"时触发首次加载
    // 如果之前加载失败（有 error），允许重新尝试
    if (tenantOptions.value.length === 0 && !tenantLoading.value && (!tenantNoMore.value || tenantError.value)) {
      resetTenants()
    }
    activeLoadMore = loadMoreTenants
  } else {
    activeLoadMore = null
  }
}

onMounted(async () => {
  // 预加载租户数据：表格列显示 + 下拉框选项
  try {
    const result = await getTenantOptions(1, 200)
    tableTenants.value = result.records
  } catch (e: any) { /* 表格租户名降级显示 */ }
  // 预加载下拉框第一页数据，避免弹窗打开时 select 反显原始 ID
  loadMoreTenants()
})

onBeforeUnmount(() => {
  document.removeEventListener('scroll', onDocumentScroll, true)
  activeLoadMore = null
})

function openEditDialog(row: UserItem) {
  editForm.id = row.id
  editForm.name = row.name
  editForm.phone = row.phone
  editForm.age = row.age
  editForm.tenantId = row.tenantId
  editForm.roleId = row.roleId
  dialogVisible.value = true
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    await saveUser({
      id: editForm.id,
      name: editForm.name,
      phone: editForm.phone,
      age: editForm.age,
      tenantId: editForm.tenantId,
      roleId: editForm.roleId
    })
    const idx = users.value.findIndex(u => u.id === editForm.id)
    if (idx !== -1) {
      users.value[idx] = { ...users.value[idx], ...editForm }
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.user-list-page {
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
  margin-bottom: 16px;
}
.toolbar {
  margin-bottom: 16px;
}
.search-input {
  max-width: 320px;
}
.full-width {
  width: 100%;
}
.select-error {
  font-size: 12px;
  color: #f56c6c;
  margin-top: 4px;
  line-height: 1.4;
}
.loading-text {
  color: #909399;
  font-size: 13px;
}
</style>
