<template>
  <aside class="sidebar">
    <div class="logo">测试即用户</div>
    <nav class="menu">
      <div v-for="module in menuData" :key="module.id" class="menu-section">
        <button class="menu-title" @click="$emit('toggle-module', module.id)">
          <span>{{ module.title }}</span>
          <span class="arrow" :class="{ open: module.open }">▾</span>
        </button>
        <ul v-if="module.open" class="submenu">
          <li
            v-for="item in module.children"
            :key="item.id"
            :class="{ active: activeItem === item.id }"
            @click="handleSelect(item)"
          >
            {{ item.label }}
          </li>
        </ul>
      </div>
    </nav>
    <div class="sidebar-footer" v-if="displayUser" @click="goProfile">
      <span class="user-avatar">{{ displayUser.name.charAt(0).toUpperCase() }}</span>
      <div class="user-info">
        <span class="user-name">{{ displayUser.name }}</span>
        <span class="tenant-name" v-if="displayTenant">{{ displayTenant.name }}</span>
      </div>
      <button class="logout-btn" @click.stop="$emit('logout')" title="退出登录">⏻</button>
    </div>
    <div
      class="sidebar-credit"
      :class="{ 'is-visible': authorRevealed }"
      :style="{ transitionDuration: AUTHOR_FADE_MS + 'ms' }"
    >
      作者：梁梓轩
    </div>
  </aside>
</template>

<script lang="ts">
export default {}
</script>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../features/auth/authStore'
import { useIdleReveal, AUTHOR_FADE_MS } from '../composables/useIdleReveal'

// 作者署名是隐藏彩蛋：页面静止 5 分钟后缓慢淡入，出现后一直留着
const { revealed: authorRevealed } = useIdleReveal({ hideOnActivity: false })

const props = defineProps({
  menuData: { type: Array, required: true },
  activeItem: { type: String, required: true },
  user: { type: Object, default: null },
  tenant: { type: Object, default: null }
})

const { user: storeUser, currentTenant: storeTenant } = useAuthStore()

// 优先用 prop，fallback 到 store（防止 prop 传递丢失）
const displayUser = computed(() => props.user || storeUser.value)
const displayTenant = computed(() => props.tenant || storeTenant.value)

const emit = defineEmits(['toggle-module', 'select-item', 'logout'])
const router = useRouter()

function handleSelect(item: { id: string; label: string; path?: string }) {
  emit('select-item', item)
  if (item.path) {
    router.push(item.path)
  }
}

function goProfile() {
  router.push('/profile')
}
</script>
