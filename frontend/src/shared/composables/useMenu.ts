import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import type { MenuModule } from '../types'

/** 侧边栏菜单组合式函数 */
export function useMenu() {
  const route = useRoute()

  const menuData = ref<MenuModule[]>([
    {
      id: 'A',
      title: '用例管理',
      open: true,
      children: [
        { id: 'A1', label: '测试用例', path: '/cases' },
        { id: 'A2', label: '测试套件', path: '/suites' },
        { id: 'A3', label: '用例集', path: '/collections' }
      ]
    },
    {
      id: 'B',
      title: '测试报告',
      open: false,
      children: [
        { id: 'B1', label: '测试结果', path: '/reports' }
      ]
    },
    {
      id: 'D',
      title: '权限管理',
      open: false,
      children: [
        { id: 'D1', label: '租户配置', path: '/user/tenant' },
        { id: 'D2', label: '个人信息', path: '/user/profile' },
        { id: 'D3', label: '角色管理', path: '/user/roles' }
      ]
    },
    {
      id: 'C',
      title: '基础配置',
      open: false,
      children: [
        { id: 'C1', label: '用例分类配置', path: '/base-config/case-categories' },
        { id: 'C2', label: '测试套件分类配置', path: '/base-config/suite-categories' },
        { id: 'C3', label: '菜单配置', path: '/base-config/menu' }
      ]
    }
  ])

  const activeItem = ref('A1')

  const pageTitle = computed(() => {
    return route.meta?.title as string || ''
  })

  function toggleModule(moduleId: string) {
    menuData.value = menuData.value.map((module) => {
      if (module.id === moduleId) {
        return { ...module, open: !module.open }
      }
      return module
    })
  }

  function selectItem(item: { id: string; path: string }) {
    activeItem.value = item.id
    // 路由跳转在 SidebarMenu 中通过 router-link 或 router.push 完成
  }

  // 监听路由变化更新 activeItem
  function syncActiveItem() {
    if (route.meta?.activeItem) {
      activeItem.value = route.meta.activeItem as string
    }
  }

  return {
    menuData,
    activeItem,
    pageTitle,
    toggleModule,
    selectItem,
    syncActiveItem
  }
}
