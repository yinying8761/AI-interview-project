import { defineStore } from 'pinia'
import { apiRequest, jsonOrImageBody } from '../api/client'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    user: null,
    ready: false,
    loading: false,
  }),

  actions: {
    async checkSession() {
      try {
        const payload = await apiRequest('/api/auth/me', { timeout: 15000 })
        this.user = payload.user
      } catch (error) {
        if (error.status !== 401) {
          console.warn('Session check failed:', error.message)
        }
        this.user = null
      } finally {
        this.ready = true
      }
    },

    async login(username, password) {
      this.loading = true
      try {
        const payload = await apiRequest('/api/auth/login', {
          method: 'POST',
          body: { username, password },
        })
        this.user = payload.user
        this.ready = true
        return payload.user
      } finally {
        this.loading = false
      }
    },

    async register(fields, faceImage = null) {
      this.loading = true
      try {
        const payload = await apiRequest('/api/auth/register', {
          method: 'POST',
          body: faceImage
            ? jsonOrImageBody(faceImage, fields)
            : fields,
          timeout: 180000,
        })
        this.user = payload.user
        this.ready = true
        return payload.user
      } finally {
        this.loading = false
      }
    },

    async faceLogin(faceImage) {
      this.loading = true
      try {
        const payload = await apiRequest('/api/auth/face-login', {
          method: 'POST',
          body: jsonOrImageBody(faceImage),
          timeout: 180000,
        })
        this.user = payload.user
        this.ready = true
        return payload
      } finally {
        this.loading = false
      }
    },

    async registerFace(faceImage) {
      this.loading = true
      try {
        const payload = await apiRequest('/api/auth/face-register', {
          method: 'POST',
          body: jsonOrImageBody(faceImage),
          timeout: 180000,
        })
        this.user = payload.user
        return payload.user
      } finally {
        this.loading = false
      }
    },

    async logout() {
      try {
        await apiRequest('/api/auth/logout', { method: 'POST' })
      } finally {
        this.user = null
      }
    },
  },
})
