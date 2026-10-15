<template>
  <div class="page-container">
    <el-row :gutter="20">
      <!-- ==================== 个人信息 ==================== -->
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <span style="font-weight: 600">👤 个人信息</span>
          </template>

          <!-- 头像 -->
          <div style="text-align: center; margin-bottom: 24px">
            <el-upload :show-file-list="false" accept="image/*" :http-request="handleAvatarUpload">
              <el-avatar :size="80" :src="profileForm.avatar" style="cursor: pointer; background: #409EFF; font-size: 32px">
                {{ (store.userInfo?.nickname || store.userInfo?.username || '?').charAt(0) }}
              </el-avatar>
              <div style="font-size: 12px; color: #909399; margin-top: 8px">点击更换头像</div>
            </el-upload>
          </div>

          <el-form ref="profileRef" :model="profileForm" label-width="90px" style="max-width: 480px; margin: 0 auto">
            <el-form-item label="用户名">
              <el-input :model-value="store.userInfo?.username" disabled />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="profileForm.nickname" placeholder="设置昵称" />
            </el-form-item>
            <el-form-item label="邮箱">
              <el-input v-model="profileForm.email" placeholder="邮箱地址" />
            </el-form-item>
            <el-form-item label="手机号">
              <el-input v-model="profileForm.phone" placeholder="手机号" />
            </el-form-item>
            <el-form-item label="角色">
              <el-tag v-for="role in store.roles" :key="role" size="small" style="margin-right: 6px">
                {{ roleText(role) }}
              </el-tag>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="savingProfile" @click="handleUpdateProfile">
                保存修改
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- ==================== 修改密码 ==================== -->
      <el-col :span="10">
        <el-card shadow="never">
          <template #header>
            <span style="font-weight: 600">🔒 修改密码</span>
          </template>

          <el-form ref="passwordRef" :model="passwordForm" :rules="passwordRules"
            label-width="90px" style="max-width: 400px; margin: 0 auto">
            <el-form-item label="旧密码" prop="oldPassword">
              <el-input v-model="passwordForm.oldPassword" type="password" placeholder="请输入旧密码" show-password />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwordForm.newPassword" type="password" placeholder="6-30位，须含字母和数字" show-password />
            </el-form-item>
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="再次输入新密码" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="warning" :loading="savingPassword" @click="handleChangePassword">
                修改密码
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 临时隐藏：与登录页同因 —— Gitee 登录/绑定当前不可用。
             SecurityConfig 为 STATELESS，但授权请求仓库仍是 session 版，
             绑定流程同样会抛 authorization_request_not_found。
             改用 Cookie 版授权请求仓库后，删掉 v-if="false" 即可恢复。 -->
        <el-card v-if="false" shadow="never" style="margin-top: 20px">
          <template #header>
            <span style="font-weight: 600">🔗 第三方账号</span>
          </template>

          <div style="display: flex; align-items: center; justify-content: space-between; gap: 12px">
            <div>
              <div style="font-weight: 600; margin-bottom: 4px">Gitee</div>
              <template v-if="giteeBound">
                <el-tag type="success">已关联</el-tag>
                <span v-if="giteeProviderUsername" style="color: #909399; font-size: 12px; margin-left: 8px">
                  {{ giteeProviderUsername }}
                </span>
              </template>
              <div v-else style="color: #909399; font-size: 12px">
                绑定后可使用 Gitee 登录本平台
              </div>
            </div>
            <el-button
              v-if="!giteeBound"
              type="success"
              :loading="bindingGitee"
              @click="handleBindGitee"
            >
              关联 Gitee 账号
            </el-button>
          </div>
        </el-card>

        <!-- 我的权限 -->
        <el-card shadow="never" style="margin-top: 20px">
          <template #header>
            <span style="font-weight: 600">🔑 我的权限</span>
          </template>
          <div style="display: flex; flex-wrap: wrap; gap: 6px">
            <el-tag v-for="perm in store.permissions" :key="perm" size="small" type="info">
              {{ perm }}
            </el-tag>
            <span v-if="store.permissions.length === 0" style="color: #c0c4cc; font-size: 13px">暂无权限</span>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../store/user'
import { updateProfile, changePassword, startBindGitee, getGiteeBindStatus } from '../api/auth'
import { uploadFile } from '../api/interview'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const profileRef = ref()
const passwordRef = ref()
const savingProfile = ref(false)
const savingPassword = ref(false)
const bindingGitee = ref(false)
const giteeBound = ref(false)
const giteeProviderUsername = ref('')


const profileForm = reactive({
  nickname: '', email: '', phone: '', avatar: ''
})

const passwordForm = reactive({
  oldPassword: '', newPassword: '', confirmPassword: ''
})

const validateConfirm = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 30, message: '密码长度 6-30 个字符', trigger: 'blur' },
    { pattern: /^(?=.*[a-zA-Z])(?=.*\d).+$/, message: '密码必须包含字母和数字', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

const roleText = (r) => ({ admin: '管理员', interviewer: '面试官', candidate: '候选人' }[r] || r)

const handleBindGitee = async () => {
  bindingGitee.value = true

  try {
    const res = await startBindGitee()
    window.location.href = res.data.authorizeUrl
  } catch (e) {
    bindingGitee.value = false
  }
}
const handleAvatarUpload = async (options) => {
  try {
    const formData = new FormData()
    formData.append('file', options.file)
    const res = await uploadFile(formData, 'avatar')
    profileForm.avatar = res.data.url
    ElMessage.success('头像上传成功，请保存修改')
  } catch (e) { /* handled */ }
}

const handleUpdateProfile = async () => {
  savingProfile.value = true
  try {
    await updateProfile({ ...profileForm })
    ElMessage.success('个人信息已更新')
    await store.fetchUserInfo()
  } catch (e) { /* handled */ }
  savingProfile.value = false
}

const handleChangePassword = async () => {
  const valid = await passwordRef.value.validate().catch(() => false)
  if (!valid) return
  savingPassword.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    await store.doLogout()
    router.push('/login')
  } catch (e) { /* handled */ }
  savingPassword.value = false
}

const loadGiteeBindStatus = async () => {
  try {
    const res = await getGiteeBindStatus()
    giteeBound.value = res.data.bound
    giteeProviderUsername.value = res.data.providerUsername || ''
  } catch (e) {
    giteeBound.value = false
    giteeProviderUsername.value = ''
  }
}

onMounted(async () => {
  if (route.query.oauthBind === 'success') {
    ElMessage.success('Gitee 账号绑定成功')
  } else if (route.query.oauthBind === 'expired') {
    ElMessage.error('绑定会话已过期，请重新绑定')
  } else if (route.query.oauthBind === 'conflict') {
    ElMessage.error('该 Gitee 账号已绑定其他用户，或当前账号已有关联')
  }

  if (route.query.oauthBind) {
    router.replace({ path: '/profile' })
  }

  await loadGiteeBindStatus()

  const info = store.userInfo
  if (info) {
    profileForm.nickname = info.nickname || ''
    profileForm.email = info.email || ''
    profileForm.phone = info.phone || ''
    profileForm.avatar = info.avatar || ''
  }
})
</script>
