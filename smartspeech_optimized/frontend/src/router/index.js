import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../pages/LoginPage.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/',
    redirect: '/interview',
  },
  {
    path: '/interview',
    name: 'interview',
    component: () => import('../pages/InterviewPage.vue'),
    meta: { requiresAuth: true, title: '开始面试' },
  },
  {
    path: '/reports',
    name: 'reports',
    component: () => import('../pages/ReportsPage.vue'),
    meta: { requiresAuth: true, title: '历史报告' },
  },
  {
    path: '/reports/:id',
    name: 'report-detail',
    component: () => import('../pages/ReportDetailPage.vue'),
    meta: { requiresAuth: true, title: '报告详情' },
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/interview',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (!auth.ready) await auth.checkSession()

  if (to.meta.requiresAuth && !auth.user) {
    return {
      name: 'login',
      query: { redirect: to.fullPath },
    }
  }
  if (to.meta.public && auth.user) {
    return { name: 'interview' }
  }

  document.title = to.meta.title
    ? `${to.meta.title} | 智能体模拟面试系统`
    : '智能体模拟面试系统'
  return true
})

export default router
