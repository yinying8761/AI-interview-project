// ===================== 后端 API 层（对接 interview-ai 契约） =====================
import axios from 'axios'
import type { AxiosInstance, AxiosRequestConfig } from 'axios'

// ---------- 后端契约类型 ----------
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

export interface BackendLoginVO {
  token: string
  userId: number
  username: string
  roles: string[]
}

export interface BackendUserVO {
  id: number
  username: string
  email: string
  nickname: string
  status: number
  createdAt: string
}

export interface BackendRole {
  id: number
  name: string
  code: string
  description: string
}

export interface BackendScenario {
  id: number
  name: string
  description: string
  techField: string
  difficulty: number
  coverImage: string
}

export interface BackendQuestion {
  id: number
  scenarioId: number
  content: string
  type: string
  difficulty: number
  sortOrder: number
  createdAt: string
}

export interface BackendRecord {
  id: number
  userId: number
  scenarioId: number
  status: 'pending' | 'ongoing' | 'completed' | 'cancelled'
  score: number | null
  duration: number | null
  answerData: string | null
  aiFeedback: string | null
  startedAt: string | null
  completedAt: string | null
  createdAt: string
}

// ---------- axios 实例 ----------
const http: AxiosInstance = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 15000
})

// 请求拦截：自动带 Bearer token
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截：401 → 清登录态
http.interceptors.response.use(
  (resp) => resp,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('auth_token')
      localStorage.removeItem('auth_user')
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

/** 统一解包：返回 data 或抛错 */
async function unwrap<T>(config: AxiosRequestConfig): Promise<T> {
  const resp = await http.request<ApiResult<T>>(config)
  const result = resp.data
  if (result.code !== 200) {
    throw new Error(result.message || `业务错误 ${result.code}`)
  }
  return result.data
}

// ===================== API 函数 =====================
export const authApi = {
  register(data: { username: string; password: string; email: string; phone?: string }) {
    return unwrap<BackendUserVO>({ method: 'POST', url: '/api/users/register', data })
  },
  login(data: { username: string; password: string }) {
    return unwrap<BackendLoginVO>({ method: 'POST', url: '/api/users/login', data })
  },
  logout() {
    return unwrap<null>({ method: 'POST', url: '/api/users/logout' })
  },
  profile() {
    return unwrap<BackendUserVO>({ method: 'GET', url: '/api/users/profile' })
  },
  updateProfile(data: { email?: string; phone?: string; nickname?: string; avatar?: string }) {
    return unwrap<BackendUserVO>({ method: 'PUT', url: '/api/users/profile', data })
  },
  changePassword(data: { oldPassword: string; newPassword: string }) {
    return unwrap<null>({ method: 'PUT', url: '/api/users/password', data })
  },
  myRecords() {
    return unwrap<BackendRecord[]>({ method: 'GET', url: '/api/users/my/records' })
  }
}

export const scenarioApi = {
  list() {
    return unwrap<BackendScenario[]>({ method: 'GET', url: '/api/scenarios' })
  },
  create(data: { name: string; description?: string; techField: string; difficulty?: number }) {
    return unwrap<null>({ method: 'POST', url: '/api/scenarios', data })
  },
  update(id: number, data: { name: string; description?: string; techField: string; difficulty?: number }) {
    return unwrap<null>({ method: 'PUT', url: `/api/scenarios/${id}`, data })
  },
  remove(id: number) {
    return unwrap<null>({ method: 'DELETE', url: `/api/scenarios/${id}` })
  }
}

export const questionApi = {
  list() {
    return unwrap<BackendQuestion[]>({ method: 'GET', url: '/api/questions' })
  },
  random(scenarioId: number, count = 5) {
    return unwrap<BackendQuestion[]>({ method: 'GET', url: '/api/questions/random', params: { scenarioId, count } })
  },
  create(data: { scenarioId: number; content: string; type?: string; difficulty?: number; expectedAnswer?: string; keywords?: string }) {
    return unwrap<null>({ method: 'POST', url: '/api/questions', data })
  },
  update(id: number, data: { scenarioId: number; content: string; type?: string; difficulty?: number; expectedAnswer?: string; keywords?: string }) {
    return unwrap<null>({ method: 'PUT', url: `/api/questions/${id}`, data })
  },
  remove(id: number) {
    return unwrap<null>({ method: 'DELETE', url: `/api/questions/${id}` })
  }
}

export const recordApi = {
  create(scenarioId: number) {
    return unwrap<BackendRecord>({ method: 'POST', url: '/api/records', data: { scenarioId } })
  },
  start(id: number) {
    return unwrap<BackendRecord>({ method: 'POST', url: `/api/records/${id}/start` })
  },
  submit(id: number, answerData: string) {
    return unwrap<null>({ method: 'POST', url: `/api/records/${id}/submit`, data: { answerData } })
  },
  cancel(id: number) {
    return unwrap<null>({ method: 'POST', url: `/api/records/${id}/cancel` })
  },
  list() {
    return unwrap<BackendRecord[]>({ method: 'GET', url: '/api/records' })
  }
}

export const adminApi = {
  users() {
    return unwrap<BackendUserVO[]>({ method: 'GET', url: '/api/admin/users' })
  },
  assignRoles(userId: number, roleIds: number[]) {
    return unwrap<null>({ method: 'PUT', url: `/api/admin/users/${userId}/role`, data: roleIds })
  },
  roles() {
    return unwrap<BackendRole[]>({ method: 'GET', url: '/api/admin/roles' })
  }
}

export const menuApi = {
  permissions() {
    return unwrap<string[]>({ method: 'GET', url: '/api/menus/permissions' })
  }
}

export const fileApi = {
  upload(file: File, module = 'resume') {
    const formData = new FormData()
    formData.append('file', file)
    return unwrap<{ id: number; fileName: string; url: string; fileSize: number }>({
      method: 'POST',
      url: '/api/files/upload',
      params: { module },
      data: formData,
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}

export default http