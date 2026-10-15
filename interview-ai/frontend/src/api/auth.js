import request from './request'

// ==================== 用户认证 ====================
export function login(data) {
  return request.post('/users/login', data)
}

export function register(data) {
  return request.post('/users/register', data)
}

export function logout() {
  return request.post('/users/logout')
}

// ==================== 用户信息 ====================
export function getProfile() {
  return request.get('/users/profile')
}

export function updateProfile(data) {
  return request.put('/users/profile', data)
}

export function changePassword(data) {
  return request.put('/users/password', data)
}

export function getUserById(id) {
  return request.get(`/users/${id}`)
}

// ==================== OAuth 登录 ====================
export function exchangeOAuthLogin(loginCode) {
  return request.post('/oauth/login/exchange', { loginCode })
}

// ==================== Gitee 绑定 ====================
export function startBindGitee() {
  return request.post('/oauth/gitee/bind/start')
}

export function getGiteeBindStatus() {
  return request.get('/oauth/gitee/bind/status')
}

// ==================== 权限 ====================
export function getMyPermissions() {
  return request.get('/menus/permissions')
}

// ==================== 管理模块 ====================
export function listUsers(params) {
  return request.get('/admin/users', { params })
}

export function listRoles() {
  return request.get('/admin/roles')
}

export function assignRoles(userId, roleIds) {
  return request.put(`/admin/users/${userId}/role`, roleIds)
}

export function resetUserPassword(userId, data) {
  return request.put(`/admin/users/${userId}/password`, data)
}

export function updateUser(userId, data) {
  return request.put(`/admin/users/${userId}`, data)
}

export function updateUserStatus(userId, status) {
  return request.put(`/admin/users/${userId}/status`, { status })
}
