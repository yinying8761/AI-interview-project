import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { AuthUser, RegisterForm, LoginForm } from '@/types'
import { authApi } from '@/api'

// 角色码 → 中文（页面模型使用）
const ROLE_LABEL: Record<string, '管理员' | '面试官' | '候选人'> = {
  admin: '管理员',
  interviewer: '面试官',
  candidate: '候选人'
}

export const useAuthStore = defineStore('auth', () => {
  // ===================== State =====================
  const currentUser = ref<AuthUser | null>(null)
  const token = ref<string>('')
  const loading = ref(false)

  // ===================== Computed =====================
  const isLoggedIn = computed(() => !!token.value && !!currentUser.value)
  const userRole = computed(() => currentUser.value?.role ?? '')
  const username = computed(() => currentUser.value?.username ?? '')

  // ===================== 初始化（从本地存储恢复会话） =====================
  function restoreSession() {
    try {
      const saved = localStorage.getItem('auth_user')
      const savedToken = localStorage.getItem('auth_token')
      if (saved && savedToken) {
        currentUser.value = JSON.parse(saved)
        token.value = savedToken
      }
    } catch {
      logout()
    }
  }

  // ===================== 注册（后端：默认挂 candidate 角色） =====================
  async function register(form: RegisterForm): Promise<{ success: boolean; message: string }> {
    loading.value = true
    try {
      await authApi.register({
        username: form.username,
        password: form.password,
        email: form.email,
        phone: form.phone || ''
      })
      loading.value = false
      return { success: true, message: '注册成功，请登录' }
    } catch (e: any) {
      loading.value = false
      return { success: false, message: e?.message || '注册失败，请稍后重试' }
    }
  }

  // ===================== 登录（用户名 + 密码） =====================
  async function login(form: LoginForm): Promise<{ success: boolean; message: string }> {
    loading.value = true
    try {
      const data = await authApi.login({ username: form.username, password: form.password })
      const role = ROLE_LABEL[data.roles?.[0] ?? ''] ?? '候选人'
      const authUser: AuthUser = {
        id: data.userId,
        username: data.username,
        email: '',
        role,
        token: data.token
      }
      currentUser.value = authUser
      token.value = data.token
      localStorage.setItem('auth_user', JSON.stringify(authUser))
      localStorage.setItem('auth_token', data.token)
      loading.value = false
      return { success: true, message: `欢迎回来，${data.username}！` }
    } catch (e: any) {
      loading.value = false
      return { success: false, message: e?.message || '用户名或密码错误' }
    }
  }

  // ===================== 退出登录（调后端销会话） =====================
  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // 会话已失效等场景忽略
    }
    currentUser.value = null
    token.value = ''
    localStorage.removeItem('auth_user')
    localStorage.removeItem('auth_token')
  }

  return {
    currentUser,
    token,
    loading,
    isLoggedIn,
    userRole,
    username,
    restoreSession,
    register,
    login,
    logout
  }
})