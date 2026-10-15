import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue'),
    meta: { title: '注册', public: true }
  },
  {
    path: '/oauth/callback',
    name: 'OAuthCallback',
    component: () => import('../views/OAuthCallback.vue'),
    meta: { title: '第三方登录', public: true, hidden: true }
  },
  {
    path: '/',
    component: () => import('../views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '首页', icon: 'Odometer' }
      },
      {
        path: 'scenarios',
        name: 'Scenarios',
        component: () => import('../views/ScenarioList.vue'),
        meta: { title: '面试场景', icon: 'Aim' }
      },
      {
        path: 'interview/:recordId',
        name: 'InterviewRoom',
        component: () => import('../views/InterviewRoom.vue'),
        meta: { title: '面试进行中', hidden: true, interviewOnly: true }
      },
      {
        path: 'interview-room/:recordId',
        name: 'InterviewVideoRoom',
        component: () => import('../views/InterviewVideoRoom.vue'),
        meta: { title: 'AI 模拟面试', hidden: true, videoRoom: true }
      },
      {
        path: 'records',
        name: 'MyRecords',
        component: () => import('../views/MyRecords.vue'),
        meta: { title: '面试记录', icon: 'Document' }
      },
      {
        path: 'resume',
        name: 'Resume',
        component: () => import('../views/ResumeUpload.vue'),
        meta: { title: '我的简历', icon: 'Document' }
      },
      {
        path: 'questions',
        name: 'QuestionBank',
        component: () => import('../views/QuestionBank.vue'),
        meta: { title: '题库管理', icon: 'Notebook', permission: 'question:view', candidateDenied: true }
      },
      {
        path: 'question-tags',
        name: 'TagManage',
        component: () => import('../views/TagManage.vue'),
        meta: { title: '标签管理', icon: 'CollectionTag', permission: 'question:create' }
      },
      {
        path: 'question-papers',
        name: 'PaperList',
        component: () => import('../views/PaperList.vue'),
        meta: { title: '试卷管理', icon: 'Document', permission: 'question:view' }
      },


      {
        path: 'profile',
        name: 'Profile',
        component: () => import('../views/Profile.vue'),
        meta: { title: '个人中心', icon: 'User', hidden: true }
      },
      {
        path: 'admin/users',
        name: 'AdminUsers',
        component: () => import('../views/AdminUsers.vue'),
        meta: { title: '用户管理', icon: 'UserFilled', permission: 'user:view' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const store = useUserStore()
  const pageTitle = to.path === '/questions' && store.isInterviewerPortal
    ? '题库参考'
    : to.meta.title

  document.title = pageTitle
    ? `${pageTitle} - 多模态智能模拟面试评测平台`
    : '多模态智能模拟面试评测平台'

  // 公开页面直接放行
  if (to.meta.public) {
    // 已登录用户访问登录页 → 跳转到首页
    if (to.path === '/login' && store.isLoggedIn) {
      return next('/dashboard')
    }
    return next()
  }

  // 未登录 → 跳转登录页
  if (!store.isLoggedIn) {
    return next('/login')
  }

  // 权限检查
  if (to.meta.permission && !store.hasPermission(to.meta.permission)) {
    return next('/dashboard')
  }

  // 面试答题仅允许候选人进入，管理员即使拥有接口权限也不能参加面试
  if (to.meta.interviewOnly && (!store.isCandidate || store.isAdmin)) {
    return next('/dashboard')
  }

  // 视频面试间放行候选人与面试官（主持人），仅排除管理员
  if (to.meta.videoRoom && store.isAdmin) {
    return next('/dashboard')
  }

  // 题库管理是后台功能，候选人不能通过直达 URL 进入
  if (to.meta.candidateDenied && store.isCandidatePortal) {
    return next('/dashboard')
  }

  next()
})

export default router
