<template>
  <div class="collection-layout">
    <div class="card case-card full-screen-table">
      <div class="case-list-header">
        <div class="case-list-title">用例集列表</div>
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
          <span class="filter-label">用例集名称</span>
          <el-input
            v-model="searchText"
            placeholder="请输入用例集名称"
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
          <el-button type="success" @click="openCreate">
            <el-icon class="btn-icon"><Plus /></el-icon>
            新建用例集
          </el-button>
        </div>
      </div>
      <div class="table-placeholder">
        <div class="table-header">
          <span>ID</span>
          <span>序号</span>
          <span>用例集名称</span>
          <span>更新时间</span>
          <span>操作</span>
        </div>
        <div class="table-row" v-for="row in pagedRows" :key="row.id">
          <span :title="row.id">{{ row.id }}</span>
          <span :title="row.index">{{ row.index }}</span>
          <span :title="row.name">{{ row.name }}</span>
          <span :title="row.updated">{{ row.updated }}</span>
          <span>
            <el-button type="success" link size="small" @click="handleLaunch(row)">启动</el-button>
            <el-button type="primary" link size="small" @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
          </span>
        </div>
        <el-empty v-if="pagedRows.length === 0" description="暂无用例集" :image-size="80" />
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

    <!-- 新建 / 编辑用例集弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" destroy-on-close>
      <el-form :model="form" label-width="90px">
        <el-form-item label="用例集名称" required>
          <el-input v-model="form.name" placeholder="请输入用例集名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import PaginationControl from '../../shared/components/PaginationControl.vue'
import { usePagination } from '../../shared/composables/usePagination'
import { useCollectionStore } from './collectionStore'
import { launchCollection } from './api'

const {
  filteredRows,
  searchKeyword,
  idKeyword,
  loadCollectionList,
  addCollection,
  updateCollection,
  removeCollection
} = useCollectionStore()

onMounted(() => {
  loadCollectionList()
})

const idText = ref('')
const searchText = ref('')

function doSearch() {
  idKeyword.value = idText.value.trim()
  searchKeyword.value = searchText.value.trim()
}

function resetSearch() {
  idText.value = ''
  searchText.value = ''
  idKeyword.value = ''
  searchKeyword.value = ''
}

const {
  pageSizes,
  pageSize,
  currentPage,
  totalItems,
  totalPages,
  pagedItems,
  changePage,
  updatePageSize
} = usePagination(filteredRows, 10)

const pagedRows = computed(() =>
  pagedItems.value.map((row, index) => ({
    ...row,
    index: (currentPage.value - 1) * pageSize.value + index + 1
  }))
)

watch(pageSize, () => {
  if (currentPage.value > totalPages.value) {
    currentPage.value = totalPages.value
  }
})

// ============ 新建 / 编辑 ============
const dialogVisible = ref(false)
const dialogTitle = ref('新建用例集')
const saving = ref(false)
const editingId = ref(null)
const form = ref({ name: '', description: '' })

function openCreate() {
  editingId.value = null
  dialogTitle.value = '新建用例集'
  form.value = { name: '', description: '' }
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  dialogTitle.value = '编辑用例集'
  form.value = { name: row.name || '', description: row.description || '' }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入用例集名称')
    return
  }
  saving.value = true
  try {
    if (editingId.value === null) {
      await addCollection({ name: form.value.name.trim(), description: form.value.description })
      ElMessage.success('用例集创建成功')
    } else {
      await updateCollection(editingId.value, { name: form.value.name.trim(), description: form.value.description })
      ElMessage.success('用例集更新成功')
    }
    dialogVisible.value = false
    await loadCollectionList()
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

// ============ 删除 ============
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定要删除用例集「${row.name}」吗？`,
      '删除确认',
      { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' }
    )
    await removeCollection(row.id)
    ElMessage.success(`用例集「${row.name}」已删除`)
  } catch {
    // 取消或失败
  }
}

// ============ 启动 ============
async function handleLaunch(row) {
  try {
    await ElMessageBox.confirm(
      `确定要启动用例集「${row.name}」吗？`,
      '启动确认',
      { confirmButtonText: '确定启动', cancelButtonText: '取消', type: 'warning' }
    )
    await launchCollection(row.id)
    ElMessage.success(`用例集「${row.name}」已启动`)
  } catch (e) {
    if (e?.message && e.message !== 'cancel') {
      ElMessage.error(e.message)
    }
  }
}
</script>

<style scoped>
.collection-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
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
.collection-layout .table-header,
.collection-layout .table-row {
  grid-template-columns: 130px 50px minmax(180px, 2fr) 120px 200px;
}

.collection-layout .table-row > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.collection-layout .table-row {
  transition: all 0.2s ease;
}
.collection-layout .table-row:hover {
  background: #f0f4ff;
  border-color: #93c5fd;
  box-shadow: 0 2px 8px rgba(37, 99, 235, 0.08);
}

/* 操作按钮间距 */
.collection-layout .table-row .el-button + .el-button {
  margin-left: 4px;
}

/* 表格容器 */
.collection-layout .table-placeholder {
  background: #ffffff;
  border-radius: 12px;
  padding: 8px 16px 16px;
}
</style>
