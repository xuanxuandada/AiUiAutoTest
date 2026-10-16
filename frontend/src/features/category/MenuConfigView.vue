<template>
  <div class="menu-config-page">
    <div class="card">
      <div class="card-header">
        <span class="card-title">菜单配置</span>
        <el-button type="primary" @click="openAddModule">新增模块</el-button>
      </div>

      <el-table :data="menuTree" row-key="id" stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" />
        <el-table-column prop="label" label="菜单名称" min-width="160">
          <template #default="{ row }">
            <span :style="{ paddingLeft: row._level * 20 + 'px' }">
              <span v-if="row._level === 0" style="font-weight: 600">📁 {{ row.label }}</span>
              <span v-else>📄 {{ row.label }}</span>
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" min-width="160">
          <template #default="{ row }">
            <code v-if="row.path" class="path-code">{{ row.path }}</code>
            <span v-else class="text-muted">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="90">
          <template #default="{ row }">
            <el-tag :type="row._level === 0 ? '' : 'info'" size="small">
              {{ row._level === 0 ? '模块' : '菜单项' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="visible" label="可见" width="70" align="center">
          <template #default="{ row }">
            <el-switch v-model="row.visible" size="small" @change="handleToggle(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEditItem(row)">编辑</el-button>
            <el-button
              v-if="row._level === 0"
              type="success" link @click="openAddChild(row)"
            >新增子项</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="480px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px" size="large">
        <el-form-item v-if="isModule" label="模块名称" prop="label">
          <el-input v-model="form.label" placeholder="请输入模块名称" />
        </el-form-item>
        <el-form-item v-else label="菜单名称" prop="label">
          <el-input v-model="form.label" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item v-if="!isModule" label="路由路径" prop="path">
          <el-input v-model="form.path" placeholder="如 /user/roles" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="1" :max="99" />
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
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

interface MenuNode {
  id: number
  label: string
  path?: string
  visible: boolean
  sort: number
  parentId: number | null
  _level: number
  children?: MenuNode[]
}

const formRef = ref(null)
const saving = ref(false)
const dialogVisible = ref(false)
const isModule = ref(true)
const isEditing = ref(false)
const editingId = ref(0)
const parentModuleId = ref<number | null>(null)
let nextId = 100

const form = reactive({
  label: '',
  path: '',
  sort: 1
})

const rules = {
  label: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  path: [{ required: true, message: '请输入路由路径', trigger: 'blur' }]
}

const dialogTitle = computed(() => {
  if (isEditing.value) return isModule.value ? '编辑模块' : '编辑菜单项'
  return isModule.value ? '新增模块' : '新增菜单项'
})

// 扁平化的菜单树（展开后）
const menuTree = computed<MenuNode[]>(() => {
  const result: MenuNode[] = []
  rawModules.value
    .sort((a, b) => a.sort - b.sort)
    .forEach(mod => {
      result.push(mod)
      const children = rawItems.value
        .filter(item => item.parentId === mod.id)
        .sort((a, b) => a.sort - b.sort)
      children.forEach(c => result.push(c))
    })
  return result
})

// ===== 原始数据 =====
const rawModules = ref<MenuNode[]>([
  { id: 1, label: '用例管理', visible: true, sort: 1, parentId: null, _level: 0 },
  { id: 2, label: '测试报告', visible: true, sort: 2, parentId: null, _level: 0 },
  { id: 3, label: '权限管理', visible: true, sort: 3, parentId: null, _level: 0 },
  { id: 4, label: '基础配置', visible: true, sort: 4, parentId: null, _level: 0 }
])

const rawItems = ref<MenuNode[]>([
  { id: 11, label: '测试用例', path: '/cases', visible: true, sort: 1, parentId: 1, _level: 1 },
  { id: 12, label: '测试套件', path: '/suites', visible: true, sort: 2, parentId: 1, _level: 1 },
  { id: 21, label: '测试报告 1', path: '/b/1', visible: true, sort: 1, parentId: 2, _level: 1 },
  { id: 22, label: '测试报告 2', path: '/b/2', visible: true, sort: 2, parentId: 2, _level: 1 },
  { id: 31, label: '租户配置', path: '/user/tenant', visible: true, sort: 1, parentId: 3, _level: 1 },
  { id: 32, label: '个人信息', path: '/user/profile', visible: true, sort: 2, parentId: 3, _level: 1 },
  { id: 33, label: '角色管理', path: '/user/roles', visible: true, sort: 3, parentId: 3, _level: 1 },
  { id: 41, label: '用例分类配置', path: '/c/1', visible: true, sort: 1, parentId: 4, _level: 1 },
  { id: 42, label: '测试套件分类配置', path: '/c/2', visible: true, sort: 2, parentId: 4, _level: 1 },
  { id: 43, label: '菜单配置', path: '/c/menu', visible: true, sort: 3, parentId: 4, _level: 1 }
])

function getMaxSort(parentId: number | null): number {
  const list = parentId === null ? rawModules.value : rawItems.value.filter(i => i.parentId === parentId)
  return list.length > 0 ? Math.max(...list.map(i => i.sort)) + 1 : 1
}

// ===== 新增模块 =====
function openAddModule() {
  isEditing.value = false
  isModule.value = true
  parentModuleId.value = null
  form.label = ''
  form.path = ''
  form.sort = getMaxSort(null)
  dialogVisible.value = true
}

// ===== 新增子项 =====
function openAddChild(row: MenuNode) {
  isEditing.value = false
  isModule.value = false
  parentModuleId.value = row.id
  form.label = ''
  form.path = ''
  form.sort = getMaxSort(row.id)
  dialogVisible.value = true
}

// ===== 编辑 =====
function openEditItem(row: MenuNode) {
  isEditing.value = true
  editingId.value = row.id
  isModule.value = row._level === 0
  parentModuleId.value = row._level === 0 ? null : row.parentId
  form.label = row.label
  form.path = row.path || ''
  form.sort = row.sort
  dialogVisible.value = true
}

// ===== 保存 =====
async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || (isEditing.value && editingId.value === 0)) return

  saving.value = true
  try {
    if (isModule.value) {
      if (isEditing.value) {
        const mod = rawModules.value.find(m => m.id === editingId.value)
        if (mod) {
          mod.label = form.label
          mod.sort = form.sort
        }
      } else {
        rawModules.value.push({
          id: nextId++, label: form.label, visible: true, sort: form.sort,
          parentId: null, _level: 0
        })
      }
    } else {
      if (isEditing.value) {
        const item = rawItems.value.find(i => i.id === editingId.value)
        if (item) {
          item.label = form.label
          item.path = form.path
          item.sort = form.sort
        }
      } else {
        rawItems.value.push({
          id: nextId++, label: form.label, path: form.path, visible: true,
          sort: form.sort, parentId: parentModuleId.value!, _level: 1
        })
      }
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
  } finally {
    saving.value = false
  }
}

// ===== 切换可见 =====
function handleToggle(row: MenuNode) {
  // 本地演示：切换可见性（未持久化）
}

// ===== 删除 =====
async function handleDelete(row: MenuNode) {
  const name = row.label
  const hasChildren = row._level === 0 && rawItems.value.some(i => i.parentId === row.id)
  const msg = hasChildren
    ? `模块「${name}」下有子菜单项，删除后子项也将移除，确认删除？`
    : `确认删除「${name}」吗？`

  try {
    await ElMessageBox.confirm(msg, '删除确认', {
      type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
    })
    if (row._level === 0) {
      rawModules.value = rawModules.value.filter(m => m.id !== row.id)
      rawItems.value = rawItems.value.filter(i => i.parentId !== row.id)
    } else {
      rawItems.value = rawItems.value.filter(i => i.id !== row.id)
    }
    ElMessage.success('删除成功')
  } catch { /* 取消 */ }
}
</script>

<style scoped>
.menu-config-page {
  max-width: 1100px;
}
.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
  padding: 24px;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}
.path-code {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #6366f1;
  background: #eef2ff;
  padding: 2px 8px;
  border-radius: 4px;
}
.text-muted {
  color: #9ca3af;
}
</style>
