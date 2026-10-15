import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes = [
  // ========== 不需要登录 ==========
  {
    path: '/login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { title: '注册', public: true }
  },

  // ========== 需要登录 ==========
  { path: '/', redirect: '/dashboard' },

  {
    path: '/dashboard',
    component: () => import('@/views/dashboard/DashboardView.vue'),
    meta: { title: '仪表盘' }
  },

  {
    path: '/config',
    component: () => import('@/views/config/SystemConfig.vue'),
    meta: { title: '系统配置' }
  },
  {
    path: '/questions',
    component: () => import('@/views/questions/QuestionList.vue'),
    meta: { title: '题库管理' }
  },
  {
    path: '/questions/create',
    component: () => import('@/views/questions/QuestionForm.vue'),
    meta: { title: '新建题目' }
  },
  {
    path: '/questions/edit/:id',
    component: () => import('@/views/questions/QuestionForm.vue'),
    meta: { title: '编辑题目' }
  },
  {
    path: '/users',
    component: () => import('@/views/users/UserList.vue'),
    meta: { title: '用户管理' }
  },

  // ========== 面试记录 ==========
  {
    path: '/records',
    component: () => import('@/views/records/RecordList.vue'),
    meta: { title: '面试记录' }
  },
  {
    path: '/records/:id/score',
    component: () => import('@/views/records/ScoreView.vue'),
    meta: { title: '面试打分' }
  },
  {
    path: '/records/:id/report',
    component: () => import('@/views/records/ReportView.vue'),
    meta: { title: '面试报告' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})


// 路由守卫：未登录时跳转到登录页
router.beforeEach((to, _from, next) => {
  // pinia 在 router.beforeEach 中需要通过函数调用方式获取 store
  const authStore = useAuthStore()

  if (to.meta.public) {
    // 已登录用户访问登录/注册页，直接跳到首页
    if (authStore.isLoggedIn) {
      return next('/')
    }
    return next()
  }

  if (!authStore.isLoggedIn) {
    return next('/login')
  }

  next()
})


export default router
