import { defineStore } from 'pinia'
import { login, logout, getProfile, getMyPermissions, exchangeOAuthLogin } from '../api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || 'null'),
    permissions: JSON.parse(localStorage.getItem('permissions') || '[]'),
    roles: JSON.parse(localStorage.getItem('roles') || '[]')
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.roles.includes('admin'),
    isCandidate: (state) => state.roles.includes('candidate'),
    isCandidatePortal: (state) => state.roles.includes('candidate') && !state.roles.includes('admin'),
    isInterviewerPortal: (state) => state.roles.includes('interviewer') && !state.roles.includes('admin'),
    hasPermission: (state) => (perm) => state.permissions.includes(perm),
    hasAnyPermission: (state) => (perms) => perms.some(p => state.permissions.includes(p))
  },

  actions: {
    async doLogin(data) {
      const res = await login(data)
      this.token = res.data.token
      this.roles = res.data.roles || []
      localStorage.setItem('token', this.token)
      localStorage.setItem('roles', JSON.stringify(this.roles))
      // 登录后获取用户信息和权限
      await this.fetchUserInfo()
      await this.fetchPermissions()
      return res
    },

    async doOAuthLogin(loginCode) {
      const res = await exchangeOAuthLogin(loginCode)
      this.token = res.data.token
      this.roles = res.data.roles || []
      localStorage.setItem('token', this.token)
      localStorage.setItem('roles', JSON.stringify(this.roles))
      await this.fetchUserInfo()
      await this.fetchPermissions()
      return res
    },

    async fetchUserInfo() {
      const res = await getProfile()
      this.userInfo = res.data
      localStorage.setItem('userInfo', JSON.stringify(res.data))
    },

    async fetchPermissions() {
      const res = await getMyPermissions()
      this.permissions = res.data || []
      localStorage.setItem('permissions', JSON.stringify(this.permissions))
    },

    async doLogout() {
      try {
        await logout()
      } catch (e) { /* ignore */ }
      this.token = ''
      this.userInfo = null
      this.permissions = []
      this.roles = []
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      localStorage.removeItem('permissions')
      localStorage.removeItem('roles')
    },

    resetAuth() {
      this.token = ''
      this.userInfo = null
      this.permissions = []
      this.roles = []
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      localStorage.removeItem('permissions')
      localStorage.removeItem('roles')
    }
  }
})
