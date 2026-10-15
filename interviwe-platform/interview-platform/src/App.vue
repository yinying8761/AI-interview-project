<template>
  <!-- 未登录：只渲染路由视图（登录/注册页本身有完整布局） -->
  <router-view v-if="!authStore.isLoggedIn" />

  <!-- 已登录：完整布局 -->
  <el-container v-else class="app-layout">
    <!-- 侧边栏 -->
    <el-aside width="220px" class="app-aside">
      <div class="logo">
        <el-icon size="22" color="#fff"><Monitor /></el-icon>
        <span>面试平台管理</span>
      </div>

      <el-menu
        :default-active="activeMenu"
        router
        background-color="#1a2038"
        text-color="#b0bec5"
        active-text-color="#ffffff"
        class="side-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/records">
          <el-icon><VideoCamera /></el-icon>
          <span>面试记录</span>
        </el-menu-item>
        <el-menu-item index="/config">
          <el-icon><Setting /></el-icon>
          <span>系统配置</span>
        </el-menu-item>
        <el-menu-item index="/questions">
          <el-icon><Document /></el-icon>
          <span>题库管理</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><UserFilled /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
      </el-menu>

      <!-- 底部用户信息 -->
      <div class="aside-footer">
        <el-avatar :size="32" style="background:#409EFF;flex-shrink:0">
          {{ authStore.username?.charAt(0) }}
        </el-avatar>
        <div class="user-info">
          <span class="user-name">{{ authStore.username }}</span>
          <el-tag size="small" :type="roleTagType">{{ authStore.userRole }}</el-tag>
        </div>
        <el-tooltip content="退出登录" placement="top">
          <el-button link style="color:#b0bec5" @click="handleLogout">
            <el-icon size="18"><SwitchButton /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <!-- 顶部导航 -->
      <el-header class="app-header">
        <div class="header-left">
          <span class="page-title">{{ currentTitle }}</span>
        </div>
        <div class="header-right">
          <el-tag type="success" size="small">Vue3 + TypeScript</el-tag>
          <span class="time">{{ currentTime }}</span>
        </div>
      </el-header>

      <!-- 内容 -->
      <el-main class="app-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const activeMenu = computed(() => {
  const path = route.path
  if (path.startsWith('/records')) return '/records'
  if (path.startsWith('/questions')) return '/questions'
  if (path.startsWith('/users')) return '/users'
  return '/config'
})

const titleMap: Record<string, string> = {
  '/records': '面试记录',
  '/config': '系统配置',
  '/questions': '题库管理',
  '/questions/create': '新建题目',
  '/users': '用户管理'
}

const currentTitle = computed(() => {
  if (route.path.startsWith('/questions/edit/')) return '编辑题目'
  if (route.path.match(/\/records\/\d+\/score/)) return '面试打分'
  if (route.path.match(/\/records\/\d+\/report/)) return '面试报告'
  return titleMap[route.path] || '面试平台管理系统'
})

const roleTagType = computed(() => {
  const map: Record<string, string> = { '管理员': 'danger', '面试官': 'primary', '候选人': 'success' }
  return map[authStore.userRole] ?? 'info'
})

async function handleLogout() {
  await ElMessageBox.confirm('确认退出登录？', '退出', { type: 'warning' })
  authStore.logout()
  router.push('/login')
}

const currentTime = ref('')
let timer: ReturnType<typeof setInterval>

function updateTime() {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit'
  })
}

onMounted(() => {
  updateTime()
  timer = setInterval(updateTime, 1000)
})

onUnmounted(() => clearInterval(timer))
</script>

<style lang="scss">
* { box-sizing: border-box; margin: 0; padding: 0; }

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: #f0f2f5;
}

.app-layout {
  height: 100vh;
  overflow: hidden;
}

.app-aside {
  background: #1a2038;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;

  .logo {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 18px 20px;
    color: #fff;
    font-size: 15px;
    font-weight: 700;
    border-bottom: 1px solid rgba(255,255,255,0.08);
    letter-spacing: 0.5px;
  }

  .side-menu {
    flex: 1;
    border-right: none;
    overflow-y: auto;

    .el-menu-item {
      height: 50px;
      line-height: 50px;
      font-size: 14px;
      &.is-active {
        background: #409EFF !important;
        color: #fff !important;
        border-radius: 0 24px 24px 0;
        margin-right: 12px;
      }
      &:hover {
        background: rgba(255,255,255,0.08) !important;
      }
    }
  }

  .aside-footer {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 12px 16px;
    border-top: 1px solid rgba(255,255,255,0.08);
    background: rgba(0,0,0,0.15);

    .user-info {
      flex: 1;
      min-width: 0;
      display: flex;
      flex-direction: column;
      gap: 3px;
      .user-name {
        font-size: 13px;
        color: #e0e0e0;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    }
  }
}

.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #EBEEF5;
  padding: 0 24px;
  height: 56px;
  box-shadow: 0 1px 4px rgba(0,21,41,0.08);

  .page-title {
    font-size: 16px;
    font-weight: 600;
    color: #303133;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 16px;
    .time {
      font-size: 13px;
      color: #909399;
    }
  }
}

.app-main {
  background: #f0f2f5;
  overflow-y: auto;
  padding: 0;
}

// 路由过渡动画
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

// Element Plus 全局调整
.el-card {
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06) !important;
}
</style>
