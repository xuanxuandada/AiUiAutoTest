<template>
  <div class="tree-config-layout">
    <div class="tree-sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="tree-sidebar-inner" v-show="!sidebarCollapsed">
        <div class="tree-sidebar-header">分类列表</div>
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
          <template #default="{ node, data }">
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
                  @click.stop="deleteNode(data, node)"
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
        <div class="case-list-title">测试套件分类配置</div>
      </div>

      <!-- 编辑表单 -->
      <div class="edit-form" v-if="editingNode">
        <div class="edit-form-header">
          <span>{{ editingNode.id ? '编辑节点' : '新增节点' }}</span>
        </div>
        <div class="edit-form-body">
          <div class="form-item">
            <label>节点名称</label>
            <el-input
              v-model="editLabel"
              placeholder="请输入节点名称"
              size="large"
              @keyup.enter="saveEdit"
            />
          </div>
          <div class="form-item" v-if="parentNode">
            <label>父级节点</label>
            <span class="parent-label">{{ parentNode.label }}</span>
          </div>
          <div class="form-actions">
            <el-button @click="cancelEdit">取消</el-button>
            <el-button type="primary" @click="saveEdit" :disabled="!editLabel.trim()">保存</el-button>
          </div>
        </div>
      </div>

      <div class="edit-form empty-state" v-else>
        <div class="empty-text">请在左侧分类树中选择或添加节点</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

// ============ 树形数据 ============
let nextId = 100

function createNode(label, children = []) {
  return { id: nextId++, label, children }
}

const treeData = ref([
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
])

const defaultProps = {
  children: 'children',
  label: 'label'
}

// ============ 侧栏状态 ============
const sidebarCollapsed = ref(false)
const filterText = ref('')
const treeRef = ref(null)

watch(filterText, (val) => {
  treeRef.value?.filter(val)
})

function filterNode(value, data) {
  if (!value) return true
  return data.label.indexOf(value) !== -1
}

// ============ 编辑状态 ============
const editingNode = ref(null)
const editLabel = ref('')
const parentNode = ref(null)

function handleNodeClick(data) {
  editingNode.value = data
  editLabel.value = data.label
  parentNode.value = findParent(treeData.value, data.id)
}

function findParent(nodes, targetId) {
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
  parentNode.value = null
}

function addChildNode(data) {
  editingNode.value = { id: null, label: '' }
  editLabel.value = ''
  parentNode.value = data
}

function editNode(data) {
  editingNode.value = data
  editLabel.value = data.label
  parentNode.value = findParent(treeData.value, data.id)
}

function saveEdit() {
  const label = editLabel.value.trim()
  if (!label) return

  if (editingNode.value.id === null) {
    // 新增
    const newNode = createNode(label)
    if (parentNode.value) {
      if (!parentNode.value.children) {
        parentNode.value.children = []
      }
      parentNode.value.children.push(newNode)
    } else {
      treeData.value.push(newNode)
    }
  } else {
    // 编辑
    const node = findNodeById(treeData.value, editingNode.value.id)
    if (node) {
      node.label = label
    }
  }
  cancelEdit()
}

function cancelEdit() {
  editingNode.value = null
  editLabel.value = ''
  parentNode.value = null
}

function findNodeById(nodes, targetId) {
  for (const node of nodes) {
    if (node.id === targetId) return node
    if (node.children) {
      const found = findNodeById(node.children, targetId)
      if (found) return found
    }
  }
  return null
}

function deleteNode(data, node) {
  if (data.id === 1) {
    ElMessage.warning('不能删除根节点')
    return
  }
  const parent = findParent(treeData.value, data.id)
  if (parent && parent.children) {
    parent.children = parent.children.filter((child) => child.id !== data.id)
  }
  if (editingNode.value?.id === data.id) {
    cancelEdit()
  }
}

// ============ 拖拽 ============
function allowDrop(draggingNode, dropNode, type) {
  return true
}

function handleDrop(draggingNode, dropNode, dropType, ev) {
  // 拖拽后的额外处理
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
