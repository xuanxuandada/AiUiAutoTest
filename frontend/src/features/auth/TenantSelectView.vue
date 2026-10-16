<template>
  <div class="tenant-select-wrapper">
    <div class="tenant-select-card">
      <div class="tenant-title">选择租户</div>
      <div class="tenant-subtitle">请选择要进入的租户</div>

      <el-form v-if="loading" class="tenant-loading">
        <el-skeleton :rows="3" animated />
      </el-form>

      <div v-else class="tenant-list">
        <div
          v-for="t in tenants"
          :key="t.id"
          class="tenant-item"
          :class="{ active: selectedId === t.id }"
          @click="selectedId = t.id"
        >
          <el-icon :size="20"><OfficeBuilding /></el-icon>
          <span class="tenant-name">{{ t.name }}</span>
          <el-icon v-if="selectedId === t.id" class="tenant-check" color="#409eff"><CircleCheckFilled /></el-icon>
        </div>
      </div>

      <el-button
        type="primary"
        class="tenant-btn"
        :loading="submitting"
        :disabled="!selectedId"
        @click="handleConfirm"
      >
        {{ submitting ? '进入中...' : '确认进入' }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from './authStore'
import { ElMessage } from 'element-plus'
import type { TenantInfo } from './types'

const router = useRouter()
const route = useRoute()
const { pendingCreds, currentTenant, fetchTenants, login } = useAuthStore()

const tenants = ref<TenantInfo[]>([])
const selectedId = ref<string | number | null>(null)
const loading = ref(true)
const submitting = ref(false)

onMounted(async () => {
  // 未验证则跳回登录页
  if (!pendingCreds.value) {
    router.replace('/login')
    return
  }

  try {
    // 步骤2：传密码查租户
    tenants.value = await fetchTenants()
  } catch {
    ElMessage.error('获取租户列表失败')
  } finally {
    loading.value = false
  }
})

async function handleConfirm() {
  if (!selectedId.value || !pendingCreds.value) return

  submitting.value = true
  try {
    // 步骤3：传 name + password + tenantId 正式登录拿 token
    await login({
      name: pendingCreds.value.name,
      password: pendingCreds.value.password,
      tenantid: selectedId.value!
    })

    const tenant = tenants.value.find((t: TenantInfo) => t.id === selectedId.value) || null
    currentTenant.value = tenant
    if (tenant) localStorage.setItem('tenant', JSON.stringify(tenant))

    ElMessage.success(`已选择租户：${tenant?.name}`)
    const redirect = (route.query.redirect as string) || '/cases'
    router.push(redirect)
  } catch {
    ElMessage.error('登录失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.tenant-select-wrapper {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef2f7;
}
.tenant-select-card {
  width: 420px;
  padding: 40px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}
.tenant-title {
  font-size: 22px;
  font-weight: 700;
  color: #1f2937;
  text-align: center;
  margin-bottom: 6px;
}
.tenant-subtitle {
  font-size: 14px;
  color: #6b7280;
  text-align: center;
  margin-bottom: 28px;
}
.tenant-loading {
  margin-bottom: 20px;
}
.tenant-list {
  margin-bottom: 24px;
}
.tenant-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 10px;
}
.tenant-item:hover {
  border-color: #409eff;
  background: #ecf5ff;
}
.tenant-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}
.tenant-name {
  flex: 1;
  font-size: 15px;
  font-weight: 500;
  color: #1f2937;
}
.tenant-check {
  flex-shrink: 0;
}
.tenant-btn {
  width: 100%;
}
</style>
