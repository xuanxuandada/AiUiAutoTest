<template>
  <div class="tree-config-layout">
    <div class="tree-sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="tree-sidebar-inner" v-show="!sidebarCollapsed">
        <div class="tree-sidebar-header">
          <span>分类列表</span>
          <el-button type="primary" link size="small" @click="addRootNode">+ 父</el-button>
        </div>
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
          node-key="id"
          draggable
          :allow-drop="allowDrop"
          @node-drop="handleDrop"
        >
          <template #default="{ data }">
            <span class="custom-tree-node">
              <span class="tree-label">{{ data.label }}</span>
              <span class="tree-actions">
                <el-button
                  type="primary"
                  link
                  size="small"
                  @click.stop="addChildNode(data)"
                >+ 子</el-button>
                <el-button
                  type="warning"
                  link
                  size="small"
                  @click.stop="editNode(data)"
                >编辑</el-button>
                <el-button
                  type="danger"
                  link
                  size="small"
                  @click.stop="deleteNode(data)"
                >删除</el-button>
              </span>
            </span>
          </template>
        </el-tree>
      </div>
      <button class="sidebar-toggle" @click="sidebarCollapsed = !sidebarCollapsed">
        <span v-if="sidebarCollapsed">›</span>
        <span v-else>‹</span>
      </button>
    </div>

    <div class="card case-card full-screen-table tree-main">
      <div class="case-list-header">
        <div class="case-list-title">用例分类配置</div>
      </div>

      <!-- 编辑表单 -->
      <div class="edit-form" v-if="editingNode">
        <div class="edit-form-header">
          <span>{{ editingNode.id ? '编辑节点' : '新增节点' }}</span>
        </div>
        <div class="edit-form-body">
          <div class="form-item">
            <label>节点名称 <span class="required">*</span></label>
            <el-input
              v-model="editLabel"
              placeholder="请输入节点名称"
              size="large"
              @keyup.enter="saveEdit"
            />
          </div>
          <div class="form-item">
            <label>编码</label>
            <el-input
              v-model="editCode"
              placeholder="唯一编码，如 ECOMMERCE_SYSTEM（可选）"
              size="large"
            />
          </div>
          <div class="form-item">
            <label>描述</label>
            <el-input
              v-model="editDescription"
              type="textarea"
              :rows="2"
              placeholder="分类描述（可选）"
            />
          </div>
          <div class="form-item" v-if="parentNode">
            <label>父级节点</label>
            <span class="parent-label">{{ parentNode.label }}</span>
          </div>
          <div class="form-actions">
            <el-button @click="cancelEdit">取消</el-button>
            <el-button type="primary" :loading="saving" @click="saveEdit" :disabled="!editLabel.trim()">保存</el-button>
          </div>
        </div>
      </div>

      <div class="edit-form empty-state" v-else>
        <div class="empty-text">请在左侧分类树中选择或添加节点</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchCategoryTree,
  createCategory,
  updateCategory,
  deleteCategory,
  toTreeNodes
} from '../../features/case/api'
import { useCaseStore } from '../../features/case/caseStore'
import type { CaseTreeNode } from '../../features/case/types'

// ============ 内部类型 ============
interface EditState {
  id: string | number | null
  label: string
}

// ============ 从 store 获取共享状态 ============
const { loadCategoryTree, refreshCategoryTree } = useCaseStore()

function getTenantId(): number | string {
  try {
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    return user.tenantId || ''
  } catch { return '' }
}

// ============ 树形数据 ============
const treeData = ref<CaseTreeNode[]>([])

const defaultProps = {
  children: 'children',
  label: 'label'
}

async function loadTree() {
  const tid = getTenantId()
  if (!tid) return
  try {
    const categories = await fetchCategoryTree(tid)
    treeData.value = toTreeNodes(categories)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载分类失败')
  }
}

onMounted(async () => {
  await loadTree()
  await loadCategoryTree()
})

// ============ 侧栏状态 ============
const sidebarCollapsed = ref(false)
const filterText = ref('')
const treeRef = ref<any>(null)

watch(filterText, (val) => {
  ;(treeRef.value as any)?.filter(val)
})

function filterNode(value: string, data: CaseTreeNode) {
  if (!value) return true
  return data.label.indexOf(value) !== -1
}

// ============ 编辑状态 ============
const editingNode = ref<EditState | null>(null)
const editLabel = ref('')
const editCode = ref('')
const editDescription = ref('')
const parentNode = ref<CaseTreeNode | null>(null)
const saving = ref(false)

function handleNodeClick(data: CaseTreeNode) {
  editingNode.value = { id: data.id, label: data.label }
  editLabel.value = data.label
  editCode.value = (data as any).code || ''
  editDescription.value = (data as any).description || ''
  parentNode.value = findParent(treeData.value, data.id)
}

function findParent(nodes: CaseTreeNode[], targetId: string | number): CaseTreeNode | null {
  for (const node of nodes) {
    if (node.children) {
      if (node.children.some((child) => child.id === targetId)) {
        return node
      }
      const found = findParent(node.children, targetId)
      if (found) return found
    }
  }
  return null
}

function addRootNode() {
  editingNode.value = { id: null, label: '' }
  editLabel.value = ''
  editCode.value = ''
  editDescription.value = ''
  parentNode.value = null
}

function addChildNode(data: CaseTreeNode) {
  editingNode.value = { id: null, label: '' }
  editLabel.value = ''
  editCode.value = ''
  editDescription.value = ''
  parentNode.value = data
}

function editNode(data: CaseTreeNode) {
  editingNode.value = { id: data.id, label: data.label }
  editLabel.value = data.label
  editCode.value = (data as any).code || ''
  editDescription.value = (data as any).description || ''
  parentNode.value = findParent(treeData.value, data.id)
}

async function saveEdit() {
  const label = editLabel.value.trim()
  if (!label || !editingNode.value) return
  saving.value = true

  const code = editCode.value.trim() || undefined
  const description = editDescription.value.trim() || undefined

  try {
    const tid = getTenantId()
    if (editingNode.value.id === null) {
      // 新增
      await createCategory({
        tenantId: tid,
        name: label,
        code,
        description,
        parentId: parentNode.value?.id ?? null
      })
      ElMessage.success('创建成功')
    } else {
      // 编辑
      await updateCategory({
        id: editingNode.value.id,
        name: label,
        code,
        description,
        parentId: parentNode.value?.id ?? null
      })
      ElMessage.success('保存成功')
    }
    cancelEdit()
    await loadTree()
    await refreshCategoryTree()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function cancelEdit() {
  editingNode.value = null
  editLabel.value = ''
  editCode.value = ''
  editDescription.value = ''
  parentNode.value = null
}

async function deleteNode(data: CaseTreeNode) {
  try {
    await ElMessageBox.confirm(`确认删除分类「${data.label}」吗？`, '删除确认', {
      type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteCategory(data.id)
    ElMessage.success('删除成功')
    if (editingNode.value?.id === data.id) cancelEdit()
    await loadTree()
    await refreshCategoryTree()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

// ============ 拖拽 ============
function allowDrop(_draggingNode: any, _dropNode: any, _type: string) {
  return true
}

function handleDrop(_draggingNode: any, _dropNode: any, _dropType: string, _ev: any) {
  // 拖拽后的额外处理（可扩展：调 API 更新 parentId / sortOrder）
}
</script>

<style scoped>
.tree-config-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.tree-sidebar {
  width: 300px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  position: relative;
  transition: width 0.25s ease, padding 0.25s ease;
  overflow: hidden;
}
.tree-sidebar.collapsed {
  width: 40px;
  padding: 16px 8px;
}
.tree-sidebar-inner {
  width: 268px;
}
.tree-sidebar-header {
  font-size: 15px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 12px;
  white-space: nowrap;
}
.tree-sidebar .el-input {
  margin-bottom: 8px;
}
.tree-toolbar {
  margin-bottom: 8px;
}
.tree-sidebar .el-tree {
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
.tree-sidebar.collapsed .sidebar-toggle {
  right: 9px;
}
.tree-main {
  flex: 1;
  min-width: 0;
}
.custom-tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex: 1;
  padding-right: 6px;
}
.tree-label {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.tree-actions {
  display: none;
  gap: 2px;
  flex-shrink: 0;
}
.custom-tree-node:hover .tree-actions {
  display: inline-flex;
}
.edit-form {
  background: #fff;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  margin-top: 16px;
}
.edit-form-header {
  font-size: 15px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 16px;
}
.edit-form-body {
  max-width: 500px;
}
.form-item {
  margin-bottom: 16px;
}
.form-item label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 6px;
}
.required {
  color: #f56c6c;
}
.parent-label {
  color: #6b7280;
  font-size: 14px;
}
.form-actions {
  display: flex;
  gap: 8px;
  margin-top: 24px;
}
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}
.empty-text {
  color: #9ca3af;
  font-size: 14px;
}
</style>
