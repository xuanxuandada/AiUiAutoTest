<template>
  <div class="card case-card full-screen-table">
    <div class="case-list-header">
      <div class="case-list-title">附件列表</div>
    </div>

    <div class="table-placeholder">
      <div class="table-header">
        <span>序号</span>
        <span>状态</span>
        <span>报价批次号</span>
        <span>附件名称</span>
        <span>客户简称</span>
        <span>报价单号</span>
        <span>模号</span>
      </div>

      <div class="table-row" v-for="row in pagedRows" :key="row.id">
        <span>{{ row.index }}</span>
        <span>
          <span class="status-label">{{ row.status }}</span>
        </span>
        <span>{{ row.batch }}</span>
        <span>{{ row.file }}</span>
        <span>{{ row.customer }}</span>
        <span>{{ row.quote }}</span>
        <span>{{ row.model }}</span>
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
</template>

<script setup>
import { computed, watch } from 'vue'
import PaginationControl from '../../shared/components/PaginationControl.vue'
import { useAttachmentStore } from './attachmentStore'
import { usePagination } from '../../shared/composables/usePagination'

const { rows } = useAttachmentStore()

const {
  pageSizes,
  pageSize,
  currentPage,
  totalItems,
  totalPages,
  pagedItems,
  changePage,
  updatePageSize
} = usePagination(rows, 10)

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
</script>

<style scoped>
.card.case-card.full-screen-table {
  width: 100% !important;
  max-width: none !important;
  margin: 0 !important;
}

.table-placeholder {
  background: rgba(255, 255, 255, 0.92);
  width: 100% !important;
  max-width: none !important;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
}

.table-header,
.table-row {
  display: grid;
  grid-template-columns: 80px 120px 1fr 1.4fr 1fr 1fr 1fr;
  gap: 16px;
  align-items: center;
}

.table-header {
  padding: 14px 18px;
  font-weight: 700;
  color: #111827;
}

.table-row {
  margin-top: 12px;
  padding: 14px 18px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid rgba(229, 231, 235, 0.85);
}

.status-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  padding: 6px 10px;
  border-radius: 999px;
  background: #f3f4f6;
  color: #111827;
  font-size: 12px;
  font-weight: 600;
}

.case-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
