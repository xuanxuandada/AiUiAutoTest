import { createRouter, createWebHistory } from 'vue-router'
import CaseListView from '../features/case/index.vue'
import CaseCreateView from '../features/case/CaseCreateView.vue'
import CaseDetailView from '../features/case/CaseDetailView.vue'
import LoginView from '../features/auth/LoginView.vue'
import RegisterView from '../features/auth/RegisterView.vue'
import ForgotPasswordView from '../features/auth/ForgotPasswordView.vue'
import TenantSelectView from '../features/auth/TenantSelectView.vue'
import ProfileView from '../features/auth/ProfileView.vue'
import TestSuiteView from '../features/suite/index.vue'
import CollectionView from '../features/collection/index.vue'
import SuiteCreateView from '../features/suite/SuiteCreateView.vue'
import SuiteEditView from '../features/suite/SuiteEditView.vue'
import SuiteCaseView from '../features/suite/SuiteCaseView.vue'
import AttachmentView from '../features/attachments/index.vue'
import B1View from '../features/batch/BatchListView.vue'
import BatchDetailView from '../features/batch/BatchDetailView.vue'
import CaseCategoryView from '../features/category/CaseCategoryView.vue'
import SuiteCategoryView from '../features/category/SuiteCategoryView.vue'
import MenuConfigView from '../features/category/MenuConfigView.vue'
import TenantConfigView from '../features/user/TenantConfigView.vue'
import UserProfileView from '../features/user/UserProfileView.vue'
import RoleManagementView from '../features/user/RoleManagementView.vue'

const routes = [
  { path: '/login', name: 'Login', component: LoginView, meta: { title: '登录', noAuth: true } },
  { path: '/register', name: 'Register', component: RegisterView, meta: { title: '用户注册', noAuth: true } },
  { path: '/forgot-password', name: 'ForgotPassword', component: ForgotPasswordView, meta: { title: '忘记密码', noAuth: true } },
  { path: '/select-tenant', name: 'TenantSelect', component: TenantSelectView, meta: { title: '选择租户' } },
  { path: '/profile', name: 'Profile', component: ProfileView, meta: { title: '个人信息' } },
  { path: '/', redirect: '/cases' },
  { path: '/cases', name: 'CaseList', component: CaseListView, meta: { title: '测试用例', activeItem: 'A1' } },
  { path: '/cases/new', name: 'CaseCreate', component: CaseCreateView, meta: { title: '用例新建', activeItem: 'A1' } },
  { path: '/cases/:id/edit', name: 'CaseDetail', component: CaseDetailView, meta: { title: '用例详情', activeItem: 'A1' } },
  { path: '/suites', name: 'SuiteList', component: TestSuiteView, meta: { title: '测试套件', activeItem: 'A2' } },
  { path: '/suites/new', name: 'SuiteCreate', component: SuiteCreateView, meta: { title: '新建套件', activeItem: 'A2' } },
  { path: '/suites/:id/edit', name: 'SuiteEdit', component: SuiteEditView, meta: { title: '编辑套件', activeItem: 'A2' } },
  { path: '/suites/:id/cases', name: 'SuiteCases', component: SuiteCaseView, meta: { title: '套件用例', activeItem: 'A2' } },
  { path: '/collections', name: 'CollectionList', component: CollectionView, meta: { title: '用例集', activeItem: 'A3' } },
  { path: '/attachments', name: 'Attachments', component: AttachmentView, meta: { title: '附件列表', activeItem: 'B1' } },
  { path: '/reports', name: 'ReportList', component: B1View, meta: { title: '测试结果', activeItem: 'B1' } },
  { path: '/reports/:id', name: 'ReportDetail', component: BatchDetailView, meta: { title: '批次详情', activeItem: 'B1' } },
  { path: '/base-config/case-categories', name: 'C1', component: CaseCategoryView, meta: { title: '用例分类配置', activeItem: 'C1' } },
  { path: '/base-config/suite-categories', name: 'C2', component: SuiteCategoryView, meta: { title: '测试套件分类配置', activeItem: 'C2' } },
  { path: '/base-config/menu', name: 'MenuConfig', component: MenuConfigView, meta: { title: '菜单配置', activeItem: 'C3' } },
  { path: '/user/tenant', name: 'TenantConfig', component: TenantConfigView, meta: { title: '租户配置', activeItem: 'D1' } },
  { path: '/user/profile', name: 'UserProfile', component: UserProfileView, meta: { title: '个人信息', activeItem: 'D2' } },
  { path: '/user/roles', name: 'RoleManagement', component: RoleManagementView, meta: { title: '角色管理', activeItem: 'D3' } },
  { path: '/:pathMatch(.*)*', redirect: '/cases' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由导航守卫 — 需要登录才能访问
router.beforeEach((to) => {
  const token = localStorage.getItem('token')

  // 已登录用户访问免登录页面（/login /register 等），重定向到首页
  if (to.meta?.noAuth && token) {
    return '/cases'
  }

  // 免登录页面直接放行
  if (to.meta?.noAuth) return true
  // 验证通过但未登录，允许进入租户选择页
  if (to.path === '/select-tenant' && sessionStorage.getItem('pendingAuth')) return true
  // 未登录则跳转登录页
  if (!token) {
    return '/login'
  }
  // 已登录放行
  return true
})

export default router
