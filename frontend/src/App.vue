<template>
  <div id="app">
    <template v-if="!isLoginPage">
      <SidebarMenu
        :menuData="menuData"
        :activeItem="activeItem"
        :user="user ?? undefined"
        :tenant="currentTenant ?? undefined"
        @toggle-module="toggleModule"
        @select-item="selectItem"
        @logout="handleLogout"
      />

      <main class="content">
        <PageHeader :title="pageTitle" badge="测试即用户" />
        <router-view />
      </main>
    </template>
    <div v-else class="fullscreen-layout">
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
// @ts-ignore - <script setup> 隐式导出，需 Volar Takeover 模式才能识别
import SidebarMenu from './shared/components/SidebarMenu.vue'
import PageHeader from './shared/components/PageHeader.vue'
import { useMenu } from './shared/composables/useMenu'
import { useAuthStore } from './features/auth/authStore'
import { ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const { user, currentTenant, logout } = useAuthStore()
const { menuData, activeItem, pageTitle, toggleModule, selectItem, syncActiveItem } = useMenu()

const isLoginPage = computed(() => ['Login', 'Register', 'ForgotPassword'].includes(route.name as string))

// 同步路由激活项
watch(
  () => route.meta,
  () => syncActiveItem(),
  { immediate: true }
)

async function handleLogout() {
  await logout()
  ElMessage.success('已退出登录')
  router.replace('/login')
}
</script>

<style>
* {
  box-sizing: border-box;
}
body {
  margin: 0;
  font-family: "Helvetica Neue", Helvetica, Arial, sans-serif;
  background: #eef2f7;
}
#app {
  display: flex;
  height: 100vh;
  overflow: hidden;
}
.sidebar {
  width: 240px;
  background: #ffffff;
  border-right: 1px solid #e5e7eb;
  padding: 24px 0;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.sidebar-footer {
  margin-top: auto;
  padding: 16px 20px;
  border-top: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  transition: background 0.2s ease;
}
.sidebar-footer:hover {
  background: #f8fafc;
}
.sidebar-footer .logout-btn {
  margin-left: auto;
  width: 30px;
  height: 30px;
  border: none;
  background: transparent;
  border-radius: 6px;
  cursor: pointer;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #9ca3af;
  transition: all 0.2s ease;
}
.sidebar-footer .logout-btn:hover {
  background: #fef2f2;
  color: #dc2626;
}
.sidebar-credit {
  margin-top: auto;
  padding: 10px 20px 16px;
  font-size: 12px;
  color: #9ca3af;
  text-align: center;
  letter-spacing: 0.5px;
  /* 默认不可见，静置 5 分钟后由 transition 缓慢淡入（时长由组件内联设置） */
  opacity: 0;
  pointer-events: none;
  user-select: none;
  transition-property: opacity;
  transition-timing-function: ease-in-out;
}
.sidebar-credit.is-visible {
  opacity: 1;
}
/* 已有用户信息栏时，署名紧跟其下，避免两个 auto 平分空白 */
.sidebar-footer + .sidebar-credit {
  margin-top: 0;
}
.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #2563eb;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
  flex-shrink: 0;
}
.user-name {
  font-size: 13px;
  color: #374151;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-info {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.tenant-name {
  font-size: 11px;
  color: #9ca3af;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.logo {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
  text-align: center;
  padding: 16px 24px;
  border-bottom: 1px solid #e5e7eb;
}
.menu {
  padding: 16px 0;
  flex: 1 1 auto;
  overflow-y: auto;
}
.menu-section + .menu-section {
  margin-top: 8px;
}
.menu-title {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: transparent;
  border: none;
  color: #374151;
  font-size: 14px;
  padding: 12px 20px;
  cursor: pointer;
}
.menu-title:hover {
  background: #f8fafc;
}
.arrow {
  transition: transform 0.2s ease;
}
.arrow.open {
  transform: rotate(180deg);
}
.submenu {
  list-style: none;
  margin: 0;
  padding: 0 0 0 8px;
}
.submenu li {
  padding: 10px 20px;
  margin-top: 4px;
  border-radius: 9999px;
  cursor: pointer;
  color: #4b5563;
  transition: background 0.2s ease, color 0.2s ease;
}
.submenu li:hover,
.submenu li.active {
  background: #eff6ff;
  color: #1d4ed8;
}
.content {
  flex: 1;
  padding: 12px 16px;
  min-width: 0;
  width: calc(100vw - 240px);
  max-width: calc(100vw - 240px);
  overflow-y: auto;
}
.content > .card,
.content .card,
.card.case-card.full-screen-table {
  display: block;
  width: calc(100vw - 260px) !important;
  max-width: calc(100vw - 260px) !important;
  min-width: 0 !important;
  margin: 0 !important;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 18px;
}
.page-title {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.title-text {
  font-size: 20px;
  font-weight: 700;
  color: #111827;
}
.title-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 9999px;
  background: #e0f2fe;
  color: #0c4a6e;
  font-size: 12px;
}
.tabs-row {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.tab {
  padding: 8px 18px;
  border-radius: 9999px;
  border: 1px solid transparent;
  background: #f8fafc;
  color: #374151;
  cursor: pointer;
  transition: all 0.2s ease;
}
.tab.active {
  background: #2563eb;
  color: #ffffff;
}
.card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.08);
  padding: 18px;
  margin-bottom: 16px;
}
.filter-row {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 16px;
  align-items: end;
}
.filter-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.filter-item label {
  font-size: 12px;
  color: #6b7280;
}
.filter-item input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  background: #f9fafb;
  color: #111827;
}
.filter-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}
.btn {
  padding: 10px 18px;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  background: #ffffff;
  color: #374151;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease;
}
.btn:hover {
  background: #f8fafc;
}
.btn.primary {
  background: #2563eb;
  border-color: #2563eb;
  color: #ffffff;
}
.create-meta {
  display: grid;
  gap: 10px;
  margin-bottom: 18px;
}
.create-meta-label {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}
.required {
  color: #dc2626;
  margin-left: 4px;
}
.logout-btn {
  padding: 6px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: #6b7280;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.2s ease;
}
.logout-btn:hover {
  background: #fef2f2;
  border-color: #fca5a5;
  color: #dc2626;
}
.fullscreen-layout {
  flex: 1;
  width: 100%;
  min-width: 100%;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}
.create-meta-input,
.create-meta select {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid #d1d5db;
  border-radius: 12px;
  background: #ffffff;
  color: #111827;
}
.step-mode-switch {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.step-mode-buttons {
  display: flex;
  gap: 10px;
}
.step-mode-buttons .btn.small {
  min-width: 96px;
}
.step-mode-buttons .btn.small.active {
  background: #2563eb;
  color: #ffffff;
  border-color: #2563eb;
}
.single-step-box textarea {
  min-height: 220px;
}
.step-item textarea {
  min-height: 220px;
}
.status-card {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.status-btn {
  padding: 8px 14px;
  border-radius: 9999px;
  border: 1px solid #d1d5db;
  background: #ffffff;
  color: #4b5563;
  cursor: pointer;
  transition: all 0.2s ease;
}
.status-btn.active {
  background: #eef2ff;
  border-color: #93c5fd;
  color: #1d4ed8;
}

.content-body {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  padding: 20px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.05);
}
.table-placeholder {
  display: grid;
  gap: 10px;
  padding: 16px;
  background: #f8fafc;
  border-radius: 20px;
  width: 100%;
}
.content .card {
  width: 100%;
  max-width: none;
}
.case-card.full-screen-table {
  width: 100%;
}
.attachment-table .table-header,
.attachment-table .table-row {
  display: grid;
  grid-template-columns: 60px minmax(180px, 1.4fr) minmax(220px, 1.8fr) minmax(220px, 2fr) minmax(220px, 2.2fr) minmax(180px, 1.4fr) minmax(120px, 1fr) minmax(180px, 1.2fr);
  gap: 12px;
  align-items: center;
  color: #4b5563;
  font-size: 13px;
}
.table-header,
.table-row {
  display: grid;
  grid-template-columns: 60px minmax(200px, 2fr) minmax(220px, 2.2fr) minmax(240px, 2.6fr) minmax(260px, 3fr) 1.2fr;
  gap: 12px;
  align-items: center;
  color: #4b5563;
  font-size: 13px;
}
.table-header {
  font-weight: 700;
  color: #111827;
  padding: 16px 0 12px;
}
.table-row {
  padding: 18px 16px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 16px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}
.table-row:last-child {
  margin-bottom: 0;
}
.status-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px 10px;
  border-radius: 9999px;
  background: #f8fafc;
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
}
.case-card {
  padding: 20px;
}
.case-list-header,
.create-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}
.case-list-title {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}
.case-list-header .btn.primary {
  padding: 10px 20px;
}
.case-list-header .btn {
  min-width: 110px;
}
.case-table {
  display: grid;
  gap: 10px;
}
.case-table-row {
  display: grid;
  grid-template-columns: 50px minmax(220px, 2.2fr) minmax(180px, 1.6fr) minmax(200px, 1.8fr) minmax(240px, 2.8fr) minmax(180px, 1.5fr) minmax(160px, 1.3fr) minmax(150px, 1fr);
  gap: 12px;
  align-items: start;
}
.required-star {
  color: #dc2626 !important;
  font-size: 16px;
  line-height: 1;
  margin-right: 4px;
}
.case-table-head .required-star,
.create-meta-label .required-star {
  color: #dc2626 !important;
}
.case-table-row .textarea-with-star {
  display: grid;
  gap: 6px;
}
.case-table-head {
  font-weight: 700;
  color: #111827;
  background: #f8fafc;
  padding: 14px 16px;
  border-radius: 12px;
}
.case-table-row input,
.case-table-row textarea {
  width: 100%;
  min-width: 0;
  padding: 14px 14px;
  border: 1px solid #d1d5db;
  border-radius: 12px;
  background: #ffffff;
  resize: vertical;
  font-family: inherit;
  height: 100%;
}
.case-table-row textarea {
  min-height: 100px;
}
.steps-cell {
  display: grid;
  gap: 12px;
  min-height: 180px;
  align-content: start;
}
.step-cell-wrapper {
  display: grid;
  gap: 8px;
}
.invalid-field {
  border-color: #dc2626 !important;
  box-shadow: 0 0 0 1px rgba(220, 38, 38, 0.18);
}
.invalid-message {
  color: #dc2626 !important;
  font-size: 13px;
  margin-top: 6px;
}
.invalid-group {
  border: 1px solid #dc2626;
  border-radius: 12px;
  box-shadow: inset 0 0 0 1px rgba(220, 38, 38, 0.12);
}
.invalid-group textarea {
  border-color: transparent;
}

.step-item {
  position: relative;
  display: grid;
  gap: 12px;
  background: #ffffff;
  border: 1px solid #dbeafe;
  border-radius: 18px;
  padding: 18px 18px 20px;
  box-shadow: 0 2px 5px rgba(15, 23, 42, 0.08);
}
.step-item textarea {
  min-height: 55px;
  border-color: #d1d5db;
}
.step-item-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}
.step-item-label {
  font-size: 13px;
  font-weight: 700;
  color: #111827;
}
.btn.small {
  width: fit-content;
  padding: 4px 8px;
  font-size: 12px;
  border-radius: 10px;
}
.btn.small.delete {
  position: absolute;
  top: 10px;
  right: 10px;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: 9999px;
  color: #b91c1c;
  background: rgba(254, 226, 226, 0.9);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}
.btn.small.add {
  align-self: flex-start;
  color: #16a34a;
  border: 1px solid #16a34a;
  background: #ecfdf5;
}
.btn.small.add:hover {
  background: #dcfce7;
}
.case-table-row span {
  color: #374151;
}
.row-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.case-table-row .btn.copy {
  min-width: 86px;
  padding: 6px 10px;
  font-size: 12px;
  border-color: #2563eb;
  color: #2563eb;
  background: #eff6ff;
}
.case-table-row .btn.copy:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.row-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.case-table-row .btn.remove {
  min-width: 58px;
  padding: 4px 8px;
  font-size: 12px;
  border-color: #ef4444;
  color: #ef4444;
  background: #fff1f2;
}
.create-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-start;
  margin-top: 12px;
}
.floating-actions {
  position: fixed;
  top: 50%;
  right: 16px;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 8px;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.12);
  z-index: 20;
}
.floating-actions .btn {
  width: 56px;
  height: 56px;
  min-width: 56px;
  padding: 5px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  line-height: 1.2;
  text-align: center;
  white-space: normal;
}
.floating-actions .btn.primary {
  background: #2563eb;
  color: #fff;
  border-color: transparent;
}
.floating-actions .btn.add-row {
  background: #22c55e;
  color: #fff;
  border-color: transparent;
}
.floating-actions .btn:not(.primary):not(.add-row) {
  background: #f8fafc;
  color: #111827;
}
.bottom-pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
  padding: 16px 18px;
  border-top: 1px solid #e5e7eb;
  background: #f8fafc;
  border-radius: 0 0 16px 16px;
}
.bottom-pagination .pagination-left,
.bottom-pagination .pagination-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.bottom-pagination select {
  min-width: 90px;
  padding: 6px 10px;
  border: 1px solid #d1d5db;
  border-radius: 10px;
  background: #ffffff;
}
.bottom-pagination .btn {
  min-width: 76px;
}
@media (max-width: 1200px) {
  .filter-row {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  #app {
    flex-direction: column;
  }
  .sidebar {
    width: 100%;
    border-right: none;
    border-bottom: 1px solid #e5e7eb;
  }
  .content {
    padding: 16px;
  }
  .filter-row {
    grid-template-columns: 1fr;
  }
  .tabs-row {
    width: 100%;
  }
}
</style>
