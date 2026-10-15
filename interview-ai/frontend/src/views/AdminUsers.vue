<template>
  <div class="page-container">
    <div class="page-heading">
      <div>
        <h2>👥 用户管理</h2>
        <p>管理系统用户资料、角色和账号状态</p>
      </div>
    </div>

    <el-card class="filter-card" shadow="never">
      <div class="filter-bar">
        <el-input v-model="query.keyword" placeholder="用户名 / 昵称 / 邮箱" clearable
          :prefix-icon="Search" style="width: 240px" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-select v-model="query.roleCode" placeholder="全部角色" clearable style="width: 150px"
          @change="handleSearch">
          <el-option v-for="role in roles" :key="role.id" :label="role.name" :value="role.code" />
        </el-select>
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px"
          @change="handleSearch">
          <el-option label="正常" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
    </el-card>

    <el-card shadow="never">
      <el-table :data="users" style="width: 100%" v-loading="loading" empty-text="暂无用户">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" width="130" />
        <el-table-column label="昵称" width="130">
          <template #default="{ row }">{{ row.nickname || '—' }}</template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column label="手机号" width="130">
          <template #default="{ row }">{{ row.phone || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="role in userRoleMap[row.id] || []" :key="role" size="small"
              :type="roleTagType(role)" style="margin-right: 4px; margin-bottom: 2px">
              {{ roleText(role) }}
            </el-tag>
            <span v-if="!userRoleMap[row.id]?.length" style="color: #c0c4cc; font-size: 12px">未分配</span>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" width="170">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="store.hasPermission('user:update')" label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text type="primary" size="small" @click="openDetailDialog(row)">查看</el-button>
              <el-button text type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-button text type="primary" size="small" @click="openRoleDialog(row)">角色</el-button>
              <el-dropdown @command="command => handleMoreCommand(command, row)">
                <el-button text type="primary" size="small">
                  更多<el-icon><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="password">重置密码</el-dropdown-item>
                    <el-dropdown-item command="toggle" divided
                      :disabled="row.id === store.userInfo?.id && row.status === 1">
                      {{ row.status === 1 ? '禁用账号' : '启用账号' }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination v-model:current-page="query.page" v-model:page-size="query.size"
          :page-sizes="[10, 20, 50]" :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange" @current-change="fetchUsers" />
      </div>
    </el-card>

    <!-- 用户详情 -->
    <el-dialog v-model="detailVisible" title="用户详情" width="620px">
      <el-descriptions v-if="detailUser" :column="2" border>
        <el-descriptions-item label="用户ID">{{ detailUser.id }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ detailUser.username }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ detailUser.nickname || '—' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailUser.status === 1 ? 'success' : 'danger'" size="small">
            {{ detailUser.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detailUser.email || '—' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ detailUser.phone || '—' }}</el-descriptions-item>
        <el-descriptions-item label="角色" :span="2">
          <el-tag v-for="role in detailUser.roles || []" :key="role" size="small"
            :type="roleTagType(role)" style="margin-right: 6px">{{ roleText(role) }}</el-tag>
          <span v-if="!detailUser.roles?.length" style="color: #c0c4cc">未分配</span>
        </el-descriptions-item>
        <el-descriptions-item label="注册时间" :span="2">{{ formatTime(detailUser.createdAt) }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 编辑资料 -->
    <el-dialog v-model="editVisible" title="编辑用户资料" width="520px" destroy-on-close>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="80px">
        <el-form-item label="用户名">
          <el-input :model-value="editUser?.username" disabled />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="editForm.nickname" maxlength="50" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="editForm.email" maxlength="100" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editForm.phone" maxlength="20" placeholder="请输入手机号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSaving" @click="handleUpdateUser">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色 -->
    <el-dialog v-model="roleDialogVisible" title="分配角色" width="480px" destroy-on-close>
      <p style="margin-bottom: 16px; color: #606266">
        为用户 <strong>{{ editingUser?.username }}</strong> 分配角色：
      </p>
      <el-checkbox-group v-model="selectedRoleIds">
        <div v-for="role in roles" :key="role.id"
          style="display: flex; align-items: center; gap: 8px; padding: 8px 0; border-bottom: 1px solid #f0f0f0">
          <el-checkbox :value="role.id">{{ role.name }}</el-checkbox>
          <span style="font-size: 12px; color: #909399">{{ role.description }}</span>
        </div>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSaving" @click="handleAssignRoles">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="passwordDialogVisible" title="重置用户密码" width="460px" destroy-on-close>
      <p style="margin-bottom: 16px; color: #606266">
        为用户 <strong>{{ passwordUser?.username }}</strong> 设置新密码：
      </p>
      <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="90px">
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password"
            placeholder="6-30位，须包含字母和数字" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password"
            placeholder="请再次输入新密码" show-password autocomplete="new-password" />
        </el-form-item>
      </el-form>
      <el-alert title="重置后，用户需要使用新密码登录。" type="warning" :closable="false" show-icon />
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="resettingPassword" @click="handleResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Search } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import {
  listUsers, listRoles, assignRoles, resetUserPassword, updateUser, updateUserStatus
} from '../api/auth'

const store = useUserStore()

const loading = ref(false)
const users = ref([])
const roles = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '', roleCode: '', status: null })

const detailVisible = ref(false)
const detailUser = ref(null)

const editVisible = ref(false)
const editSaving = ref(false)
const editUser = ref(null)
const editFormRef = ref()
const editForm = reactive({ nickname: '', email: '', phone: '' })
const editRules = {
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

const roleDialogVisible = ref(false)
const roleSaving = ref(false)
const editingUser = ref(null)
const selectedRoleIds = ref([])

const passwordDialogVisible = ref(false)
const resettingPassword = ref(false)
const passwordUser = ref(null)
const passwordFormRef = ref()
const passwordForm = reactive({ newPassword: '', confirmPassword: '' })

const validatePasswordConfirm = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) callback(new Error('两次输入的密码不一致'))
  else callback()
}

const passwordRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 30, message: '密码长度6-30个字符', trigger: 'blur' },
    { pattern: /^(?=.*[a-zA-Z])(?=.*\d).+$/, message: '密码必须包含字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validatePasswordConfirm, trigger: 'blur' }
  ]
}

const userRoleMap = computed(() => Object.fromEntries(users.value.map(user => [user.id, user.roles || []])))
const roleText = (code) => roles.value.find(role => role.code === code)?.name || code
const roleTagType = (code) => ({ admin: 'danger', interviewer: 'warning', candidate: 'primary' }[code] || 'info')
const formatTime = (time) => time ? new Date(time).toLocaleString('zh-CN') : '—'

const fetchRoles = async () => {
  const res = await listRoles()
  roles.value = res.data || []
}

const fetchUsers = async () => {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.keyword.trim()) params.keyword = query.keyword.trim()
    if (query.roleCode) params.roleCode = query.roleCode
    if (query.status !== null && query.status !== '') params.status = query.status
    const res = await listUsers(params)
    users.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) { /* handled */ }
  loading.value = false
}

const handleSearch = () => {
  query.page = 1
  fetchUsers()
}

const resetQuery = () => {
  Object.assign(query, { page: 1, size: 10, keyword: '', roleCode: '', status: null })
  fetchUsers()
}

const handleSizeChange = () => {
  query.page = 1
  fetchUsers()
}

const openDetailDialog = (user) => {
  detailUser.value = user
  detailVisible.value = true
}

const openEditDialog = (user) => {
  editUser.value = user
  Object.assign(editForm, {
    nickname: user.nickname || '',
    email: user.email || '',
    phone: user.phone || ''
  })
  editVisible.value = true
}

const handleUpdateUser = async () => {
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid || !editUser.value) return
  editSaving.value = true
  try {
    await updateUser(editUser.value.id, { ...editForm })
    ElMessage.success('用户资料已更新')
    editVisible.value = false
    fetchUsers()
  } catch (e) { /* handled */ }
  editSaving.value = false
}

const openRoleDialog = (user) => {
  editingUser.value = user
  selectedRoleIds.value = (user.roles || [])
    .map(code => roles.value.find(role => role.code === code)?.id)
    .filter(id => id !== undefined)
  roleDialogVisible.value = true
}

const handleAssignRoles = async () => {
  if (!editingUser.value) return
  roleSaving.value = true
  try {
    await assignRoles(editingUser.value.id, selectedRoleIds.value)
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
    fetchUsers()
  } catch (e) { /* handled */ }
  roleSaving.value = false
}

const openPasswordDialog = (user) => {
  passwordUser.value = user
  Object.assign(passwordForm, { newPassword: '', confirmPassword: '' })
  passwordDialogVisible.value = true
}

const handleResetPassword = async () => {
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid || !passwordUser.value) return
  resettingPassword.value = true
  try {
    await resetUserPassword(passwordUser.value.id, { newPassword: passwordForm.newPassword })
    ElMessage.success(`用户 ${passwordUser.value.username} 的密码已重置`)
    passwordDialogVisible.value = false
  } catch (e) { /* handled */ }
  resettingPassword.value = false
}

const handleMoreCommand = (command, user) => {
  if (command === 'password') openPasswordDialog(user)
  else if (command === 'toggle') toggleStatus(user)
}

const toggleStatus = async (user) => {
  const nextStatus = user.status === 1 ? 0 : 1
  const actionText = nextStatus === 0 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(
      `确定要${actionText}用户「${user.nickname || user.username}」吗？`,
      `${actionText}账号`,
      { confirmButtonText: `确定${actionText}`, cancelButtonText: '取消', type: 'warning' }
    )
    await updateUserStatus(user.id, nextStatus)
    ElMessage.success(`账号已${actionText}`)
    fetchUsers()
  } catch (e) { /* cancelled or handled */ }
}

onMounted(async () => {
  try {
    await fetchRoles()
    await fetchUsers()
  } catch (e) { /* handled */ }
})
</script>

<style scoped>
.page-heading h2 {
  color: #303133;
  font-size: 20px;
}

.page-heading p {
  margin-top: 7px;
  color: #909399;
  font-size: 13px;
}

.filter-card {
  margin: 20px 0;
}

.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.table-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-wrap: nowrap;
  gap: 4px;
  white-space: nowrap;
}

.table-actions :deep(.el-button) {
  margin-left: 0;
  padding: 4px 2px;
}
</style>
