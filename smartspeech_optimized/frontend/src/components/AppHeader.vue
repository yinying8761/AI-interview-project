<script setup>
import {
  BarChart3,
  Bot,
  LogOut,
  MessageSquareText,
  UserRound,
} from 'lucide-vue-next'
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const menuOpen = ref(false)

const initials = computed(() => {
  const name = auth.user?.display_name || auth.user?.username || '用户'
  return name.slice(0, 1).toUpperCase()
})

async function logout() {
  menuOpen.value = false
  await auth.logout()
  router.replace({ name: 'login' })
}
</script>

<template>
  <header class="app-header">
    <div class="header-inner">
      <RouterLink class="brand" :to="{ name: 'interview' }">
        <span class="brand-mark"><Bot :size="20" /></span>
        <span>
          <strong>智能体模拟面试系统</strong>
          <small>AI INTERVIEW WORKSPACE</small>
        </span>
      </RouterLink>

      <nav class="main-nav" aria-label="主导航">
        <RouterLink
          :class="{ active: route.name === 'interview' }"
          :to="{ name: 'interview' }"
        >
          <MessageSquareText :size="17" />
          面试工作台
        </RouterLink>
        <RouterLink
          :class="{ active: route.name === 'reports' || route.name === 'report-detail' }"
          :to="{ name: 'reports' }"
        >
          <BarChart3 :size="17" />
          历史报告
        </RouterLink>
      </nav>

      <div class="account">
        <button
          class="account-trigger"
          type="button"
          :aria-expanded="menuOpen"
          @click="menuOpen = !menuOpen"
        >
          <span class="avatar">{{ initials }}</span>
          <span class="account-copy">
            <strong>{{ auth.user?.display_name || auth.user?.username }}</strong>
            <small>{{ auth.user?.face_registered ? '人脸已绑定' : '仅密码登录' }}</small>
          </span>
        </button>

        <div v-if="menuOpen" class="account-menu">
          <div class="account-summary">
            <UserRound :size="18" />
            <span>
              <strong>{{ auth.user?.display_name }}</strong>
              <small>@{{ auth.user?.username }}</small>
            </span>
          </div>
          <button type="button" @click="logout">
            <LogOut :size="17" />
            退出登录
          </button>
        </div>
      </div>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: 50;
  height: var(--header-height);
  border-bottom: 1px solid var(--line);
  background: color-mix(in srgb, var(--surface) 94%, transparent);
  backdrop-filter: blur(14px);
}

.header-inner {
  width: min(1540px, calc(100% - 40px));
  height: 100%;
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(260px, 1fr) auto minmax(260px, 1fr);
  align-items: center;
  gap: 24px;
}

.brand {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 11px;
  color: var(--ink);
  text-decoration: none;
}

.brand-mark {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  color: #fff;
  background: var(--ink);
  box-shadow: 0 5px 12px rgb(18 42 51 / 18%);
}

.brand strong,
.brand small {
  display: block;
}

.brand strong {
  font-size: 15px;
  letter-spacing: .01em;
}

.brand small {
  margin-top: 2px;
  color: var(--muted);
  font-family: var(--font-data);
  font-size: 9px;
  letter-spacing: .16em;
}

.main-nav {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 4px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface-soft);
}

.main-nav a {
  min-height: 36px;
  padding: 0 14px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  border-radius: 6px;
  color: var(--muted-strong);
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
  transition: background .16s ease, color .16s ease;
}

.main-nav a:hover,
.main-nav a.active {
  color: var(--ink);
  background: var(--surface);
  box-shadow: var(--shadow-xs);
}

.account {
  position: relative;
  justify-self: end;
}

.account-trigger {
  min-width: 0;
  padding: 3px 4px;
  display: flex;
  align-items: center;
  gap: 9px;
  border: 0;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.avatar {
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: var(--teal-dark);
  background: var(--teal-soft);
  font-family: var(--font-data);
  font-weight: 800;
}

.account-copy strong,
.account-copy small {
  display: block;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-copy strong {
  color: var(--ink);
  font-size: 13px;
}

.account-copy small {
  margin-top: 2px;
  color: var(--muted);
  font-size: 11px;
}

.account-menu {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 220px;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  box-shadow: var(--shadow-md);
}

.account-summary {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px;
  border-bottom: 1px solid var(--line);
  color: var(--muted-strong);
}

.account-summary strong,
.account-summary small {
  display: block;
  max-width: 155px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.account-summary strong {
  color: var(--ink);
  font-size: 13px;
}

.account-summary small {
  margin-top: 2px;
  font-size: 11px;
}

.account-menu button {
  width: 100%;
  min-height: 38px;
  margin-top: 5px;
  padding: 0 9px;
  display: flex;
  align-items: center;
  gap: 9px;
  border: 0;
  border-radius: 6px;
  color: var(--danger);
  background: transparent;
  font: inherit;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}

.account-menu button:hover {
  background: var(--danger-soft);
}

@media (max-width: 820px) {
  .header-inner {
    width: min(100% - 24px, 1540px);
    grid-template-columns: 1fr auto;
  }

  .brand small,
  .account-copy {
    display: none;
  }

  .main-nav {
    position: fixed;
    right: 12px;
    bottom: 12px;
    left: 12px;
    z-index: 60;
    justify-content: center;
    box-shadow: var(--shadow-lg);
  }

  .main-nav a {
    flex: 1;
    justify-content: center;
  }
}
</style>
