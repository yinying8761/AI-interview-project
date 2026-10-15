<template>
  <CandidateLayout v-if="isCandidatePortal" />

  <el-container v-else :class="['layout-container', isInterviewerPortal ? 'interviewer-layout' : 'admin-layout']">
    <!-- ==================== 侧边栏 ==================== -->
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="layout-aside">
      <!-- Logo -->
      <div class="aside-logo" @click="$router.push('/dashboard')">
        <span class="logo-icon">{{ isInterviewerPortal ? '👨‍💼' : '🤖' }}</span>
        <span v-show="!isCollapsed" class="logo-text">
          {{ isInterviewerPortal ? '面试官工作台' : '智能面试平台' }}
        </span>
      </div>

      <!-- 菜单 -->
      <el-menu :default-active="activeMenu" :collapse="isCollapsed" :collapse-transition="false"
        :background-color="isInterviewerPortal ? '#102a43' : '#1d1e1f'"
        text-color="#a6adb4" :active-text-color="isInterviewerPortal ? '#2dd4bf' : '#409EFF'"
        class="aside-menu" router>
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <template #title>{{ isInterviewerPortal ? '评审工作台' : '首页' }}</template>
        </el-menu-item>

        <el-menu-item index="/scenarios">
          <el-icon><Aim /></el-icon>
          <template #title>面试场景</template>
        </el-menu-item>

        <el-menu-item index="/records">
          <el-icon><Document /></el-icon>
          <template #title>{{ isInterviewerPortal ? '面试评审' : '面试记录' }}</template>
        </el-menu-item>
        <el-menu-item v-if="store.hasPermission('question:view')" index="/questions">
          <el-icon><Notebook /></el-icon>
          <template #title>{{ isInterviewerPortal ? '题库参考' : '题库管理' }}</template>
        </el-menu-item>
        <el-menu-item v-if="store.hasPermission('question:create')" index="/question-tags">
          <el-icon><Collection /></el-icon>
          <template #title>标签管理</template>
        </el-menu-item>
        <el-menu-item v-if="store.hasPermission('question:view')" index="/question-papers">
          <el-icon><Document /></el-icon>
          <template #title>试卷管理</template>
        </el-menu-item>

        <el-menu-item v-if="store.hasPermission('user:view')" index="/admin/users">
          <el-icon><UserFilled /></el-icon>
          <template #title>用户管理</template>
        </el-menu-item>
      </el-menu>

      <!-- 折叠按钮 -->
      <div class="aside-collapse" @click="isCollapsed = !isCollapsed">
        <el-icon v-if="isCollapsed"><Expand /></el-icon>
        <el-icon v-else><Fold /></el-icon>
      </div>
    </el-aside>

    <!-- ==================== 右侧 ==================== -->
    <el-container>
      <!-- 顶栏 -->
      <el-header class="layout-header" height="56px">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">
              {{ isInterviewerPortal ? '面试官工作台' : '首页' }}
            </el-breadcrumb-item>
            <el-breadcrumb-item v-if="route.meta.title && route.meta.title !== '首页'">
              {{ pageTitle }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="32" :src="store.userInfo?.avatar"
                style="background: #409EFF; font-size: 14px">
                {{ (store.userInfo?.nickname || store.userInfo?.username || '?').charAt(0) }}
              </el-avatar>
              <span class="user-name">{{ store.userInfo?.nickname || store.userInfo?.username || '用户' }}</span>
              <el-icon style="margin-left: 4px"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 内容区 -->
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'
import CandidateLayout from './CandidateLayout.vue'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const isCollapsed = ref(false)

const isCandidatePortal = computed(() => store.isCandidatePortal)
const isInterviewerPortal = computed(() => store.isInterviewerPortal)
const pageTitle = computed(() => {
  if (route.path === '/questions' && isInterviewerPortal.value) return '题库参考'
  return route.meta.title
})

const activeMenu = computed(() => {
  // 面试页面高亮场景菜单
  if (route.path.startsWith('/interview')) return '/scenarios'
  return route.path
})

const handleCommand = async (command) => {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
      await store.doLogout()
      ElMessage.success('已退出登录')
      router.push('/login')
    } catch (e) { /* cancelled */ }
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.layout-aside {
  background: #1d1e1f;
  display: flex;
  flex-direction: column;
  transition: width 0.3s;
  overflow: hidden;
}

.aside-logo {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  cursor: pointer;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.logo-icon {
  font-size: 24px;
}

.logo-text {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  margin-left: 8px;
  white-space: nowrap;
}

.aside-menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
}

.aside-menu .el-menu-item {
  height: 48px;
  line-height: 48px;
}

.aside-menu .el-menu-item:hover {
  background: rgba(255, 255, 255, 0.05);
}

.aside-menu .el-menu-item.is-active {
  background: rgba(64, 158, 255, 0.15);
  border-right: 3px solid #409EFF;
}

.aside-collapse {
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a6adb4;
  cursor: pointer;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.aside-collapse:hover {
  color: #409EFF;
  background: rgba(255, 255, 255, 0.05);
}

.layout-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  z-index: 1;
}

.header-left {
  display: flex;
  align-items: center;
}

.header-right .user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.2s;
}

.header-right .user-info:hover {
  background: #f5f7fa;
}

.user-name {
  margin-left: 8px;
  font-size: 14px;
  color: #303133;
}

.layout-main {
  background: #f0f2f5;
  overflow-y: auto;
  padding: 0;
}

.interviewer-layout .layout-aside {
  background: #102a43;
}

.interviewer-layout .aside-logo {
  border-bottom-color: rgba(45, 212, 191, 0.18);
}

.interviewer-layout .aside-menu .el-menu-item:hover {
  background: rgba(45, 212, 191, 0.08);
}

.interviewer-layout .aside-menu .el-menu-item.is-active {
  background: rgba(45, 212, 191, 0.14);
  border-right-color: #2dd4bf;
}

.interviewer-layout .aside-collapse {
  border-top-color: rgba(45, 212, 191, 0.18);
}

.interviewer-layout .aside-collapse:hover {
  color: #2dd4bf;
  background: rgba(45, 212, 191, 0.08);
}
</style>
