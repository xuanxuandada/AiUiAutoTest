<template>
  <div class="pagination-controls">
    <div class="pagination-left">
      <span>每页显示</span>
      <select v-model.number="localPageSize" @change="emitPageSizeChange">
        <option v-for="size in pageSizes" :key="size" :value="size">{{ size }}</option>
      </select>
      <span>条，共 {{ totalItems }} 条</span>
    </div>
    <div class="pagination-right">
      <button class="btn" :disabled="currentPage <= 1" @click="changePage(currentPage - 1)">上一页</button>
      <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
      <button class="btn" :disabled="currentPage >= totalPages" @click="changePage(currentPage + 1)">下一页</button>
      <span class="jump-label">跳至</span>
      <input
        v-model="jumpPage"
        class="jump-input"
        type="number"
        :min="1"
        :max="totalPages"
        @keyup.enter="handleJump"
      />
      <button class="btn btn-go" @click="handleJump">GO</button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'PaginationControl',
  props: {
    totalItems: {
      type: Number,
      required: true
    },
    currentPage: {
      type: Number,
      required: true
    },
    pageSize: {
      type: Number,
      required: true
    },
    pageSizes: {
      type: Array,
      default: () => [5, 10, 20, 30, 50]
    }
  },
  data() {
    return {
      localPageSize: this.pageSize,
      jumpPage: ''
    }
  },
  computed: {
    totalPages() {
      return Math.max(1, Math.ceil(this.totalItems / this.localPageSize))
    }
  },
  watch: {
    pageSize(newValue) {
      this.localPageSize = newValue
    }
  },
  methods: {
    changePage(page) {
      if (page < 1 || page > this.totalPages) return
      this.$emit('update:currentPage', page)
    },
    emitPageSizeChange() {
      this.$emit('update:pageSize', this.localPageSize)
    },
    handleJump() {
      const page = Number(this.jumpPage)
      if (Number.isNaN(page) || page < 1 || page > this.totalPages) {
        this.jumpPage = ''
        return
      }
      this.jumpPage = ''
      this.changePage(page)
    }
  }
}
</script>

<style scoped>
.pagination-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 18px;
  margin-top: 18px;
  border-top: 1px solid #e5e7eb;
  background: #f8fafc;
  border-radius: 0 0 16px 16px;
}
.pagination-left,
.pagination-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pagination-left select {
  min-width: 90px;
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  background: #ffffff;
}
.pagination-right button {
  min-width: 76px;
}
.jump-label {
  font-size: 13px;
  color: #6b7280;
  margin-left: 4px;
}
.jump-input {
  width: 52px;
  padding: 4px 6px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  text-align: center;
  font-size: 13px;
}
.jump-input::-webkit-inner-spin-button,
.jump-input::-webkit-outer-spin-button {
  -webkit-appearance: none;
  margin: 0;
}
.btn-go {
  min-width: 42px !important;
  padding: 4px 10px;
  background: #409eff;
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
}
.btn-go:hover {
  background: #337ecc;
}
</style>
