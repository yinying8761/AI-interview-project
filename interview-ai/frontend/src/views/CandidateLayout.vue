<template>
  <div class="candidate-shell">
    <header class="candidate-header" :class="{ immersive: isInterviewRoom }">
      <div class="header-inner">
        <button class="candidate-brand" type="button" @click="navigate('/dashboard')">
          <span class="brand-mark">AI</span>
          <span class="brand-copy">
            <strong>智能面试</strong>
            <small>候选人中心</small>
          </span>
        </button>

        <nav v-if="!isInterviewRoom" class="candidate-nav">
          <button v-for="item in navItems" :key="item.path" type="button"
            :class="{ active: activeNav === item.path }" @click="navigate(item.path)">
            {{ item.label }}
          </button>
        </nav>

        <div class="header-actions">
          <span v-if="isInterviewRoom" class="interview-status">
            <i></i>面试进行中
          </span>
          <el-dropdown v-else trigger="click" @command="handleCommand">
            <button class="candidate-user" type="button">
              <el-avatar :size="34" :src="store.userInfo?.avatar">
                {{ (store.userInfo?.nickname || store.userInfo?.username || '?').charAt(0) }}
              </el-avatar>
              <span>{{ store.userInfo?.nickname || store.userInfo?.username || '候选人' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </button>
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
      </div>
    </header>

    <main class="candidate-main" :class="{ immersive: isInterviewRoom }">
      <router-view v-slot="{ Component }">
        <transition name="portal-fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>

    <footer v-if="!isInterviewRoom" class="candidate-footer">
      <span>AI 模拟面试 · 每一次练习都更接近理想 offer</span>
    </footer>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, SwitchButton, User } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const navItems = [
  { path: '/dashboard', label: '首页' },
  { path: '/scenarios', label: '面试大厅' },
  { path: '/records', label: '我的记录' },  { path: '/resume', label: '我的简历' }
]

const isInterviewRoom = computed(() => route.path.startsWith('/interview'))
const activeNav = computed(() => isInterviewRoom.value ? '/scenarios' : route.path)

const navigate = (path) => {
  if (route.path !== path) router.push(path)
}

const handleCommand = async (command) => {
  if (command === 'profile') {
    navigate('/profile')
    return
  }
  if (command !== 'logout') return

  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出登录', {
      confirmButtonText: '确定退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await store.doLogout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch (e) { /* cancelled */ }
}
</script>

<style scoped>
.candidate-shell {
  min-height: 100vh;
  background: #f6f7fb;
  color: #1e293b;
}

.candidate-header {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(255, 255, 255, 0.92);
  border-bottom: 1px solid #e8ebf3;
  backdrop-filter: blur(18px);
}

.candidate-header.immersive {
  background: #0f172a;
  border-bottom-color: #1e293b;
}

.header-inner {
  width: min(1180px, calc(100% - 40px));
  height: 68px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 28px;
}

.candidate-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  flex-shrink: 0;
}

.brand-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  color: #fff;
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.5px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 8px 18px rgba(99, 102, 241, 0.24);
}

.brand-copy {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.2;
}

.brand-copy strong {
  color: #1e293b;
  font-size: 16px;
}

.brand-copy small {
  margin-top: 3px;
  color: #94a3b8;
  font-size: 11px;
}

.candidate-nav {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border-radius: 999px;
  background: #f1f5f9;
}

.candidate-nav button {
  padding: 8px 18px;
  border: 0;
  border-radius: 999px;
  color: #64748b;
  background: transparent;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.candidate-nav button:hover {
  color: #4338ca;
}

.candidate-nav button.active {
  color: #4338ca;
  font-weight: 600;
  background: #fff;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
}

.header-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
}

.candidate-user {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px 4px 4px;
  border: 0;
  border-radius: 999px;
  color: #334155;
  background: transparent;
  cursor: pointer;
  transition: background 0.2s ease;
}

.candidate-user:hover {
  background: #f1f5f9;
}

.candidate-user span {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.candidate-user .el-avatar {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
}

.interview-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #cbd5e1;
  font-size: 13px;
}

.interview-status i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 4px rgba(34, 197, 94, 0.15);
}

.candidate-main {
  width: min(1180px, calc(100% - 40px));
  min-height: calc(100vh - 122px);
  margin: 0 auto;
}

.candidate-main.immersive {
  width: min(960px, calc(100% - 32px));
  min-height: calc(100vh - 68px);
  padding: 24px 0;
}

.candidate-main :deep(.page-container) {
  padding: 28px 0;
}

.candidate-footer {
  padding: 22px 20px 30px;
  color: #94a3b8;
  font-size: 12px;
  text-align: center;
}

.portal-fade-enter-active,
.portal-fade-leave-active {
  transition: opacity 0.18s ease, transform 0.18s ease;
}

.portal-fade-enter-from {
  opacity: 0;
  transform: translateY(6px);
}

.portal-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

@media (max-width: 760px) {
  .header-inner {
    width: calc(100% - 24px);
    height: auto;
    min-height: 64px;
    padding: 10px 0;
    flex-wrap: wrap;
    gap: 10px;
  }

  .candidate-nav {
    order: 3;
    width: 100%;
    justify-content: space-between;
  }

  .candidate-nav button {
    flex: 1;
    padding: 8px 6px;
  }

  .brand-copy small,
  .candidate-user span {
    display: none;
  }

  .candidate-main {
    width: calc(100% - 24px);
  }
}
</style>
