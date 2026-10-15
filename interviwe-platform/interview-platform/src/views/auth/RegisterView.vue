<template>
  <div class="auth-page">
    <div class="auth-card">
      <!-- Logo区域 -->
      <div class="auth-logo">
        <el-icon size="36" color="#409EFF"><Monitor /></el-icon>
        <h1>面试平台管理系统</h1>
        <p>创建新账号</p>
      </div>

      <!-- 注册表单 -->
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="auth-form"
        @keyup.enter="handleRegister"
      >
        <!-- 用户名 + 手机号 同行 -->
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model="form.username"
                placeholder="4-20个字符"
                prefix-icon="User"
                clearable
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号（选填）" prop="phone">
              <el-input
                v-model="form.phone"
                placeholder="11位手机号"
                prefix-icon="Phone"
                clearable
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 邮箱 -->
        <el-form-item label="邮箱" prop="email">
          <el-input
            v-model="form.email"
            placeholder="请输入邮箱（登录无需邮箱，仅注册校验）"
            prefix-icon="Message"
            clearable
          />
        </el-form-item>

        <!-- 密码 + 确认密码 同行 -->
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                placeholder="6-30位，须含字母和数字"
                prefix-icon="Lock"
                show-password
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="确认密码" prop="confirmPassword">
              <el-input
                v-model="form.confirmPassword"
                type="password"
                placeholder="再次输入密码"
                prefix-icon="Lock"
                show-password
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 提交按钮 -->
        <el-form-item>
          <el-button
            type="primary"
            :loading="authStore.loading"
            class="submit-btn"
            @click="handleRegister"
          >
            {{ authStore.loading ? '注册中...' : '立即注册' }}
          </el-button>
        </el-form-item>

        <div class="auth-footer">
          已有账号？
          <el-link type="primary" @click="router.push('/login')">立即登录</el-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { validators } from '@/utils/validators'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: '',
  role: '候选人' as '管理员' | '面试官' | '候选人',
  phone: ''
})

const roleOptions = [
  { value: '管理员', label: '管理员', icon: 'UserFilled', color: '#F56C6C', desc: '系统管理' },
  { value: '面试官', label: '面试官', icon: 'Avatar',     color: '#409EFF', desc: '开展面试' },
  { value: '候选人', label: '候选人', icon: 'User',       color: '#67C23A', desc: '参加面试' }
] as const

// 确认密码自定义校验
function validateConfirm(rule: any, value: string, callback: Function) {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

// 密码强度校验（6-30位，须含字母和数字，与后端 LoginDTO/RegisterDTO 一致）
function validatePassword(rule: any, value: string, callback: Function) {
  const reg = /^(?=.*[a-zA-Z])(?=.*\d).{6,30}$/
  if (!reg.test(value)) {
    callback(new Error('密码需6-30位，且同时包含字母和数字'))
  } else {
    callback()
  }
}

// 用户名校验（4-20，与后端一致）
function validateUsername(rule: any, value: string, callback: Function) {
  if (!value) return callback(new Error('请输入用户名'))
  if (value.length < 4 || value.length > 20) return callback(new Error('用户名为4-20个字符'))
  callback()
}

const rules = {
  username: [{ required: true, validator: validateUsername, trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { validator: validators.email, trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { validator: validatePassword, trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ],
  phone: [
    { validator: validators.phone, trigger: 'blur' }
  ]
}

async function handleRegister() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const result = await authStore.register(form)
  if (result.success) {
    ElMessage.success(result.message)
    router.push('/login')
  } else {
    ElMessage.error(result.message)
  }
}
</script>

<style lang="scss" scoped>
.auth-page {
  min-height: 100vh;
  background: linear-gradient(135deg, #1a2038 0%, #2d3561 50%, #1a2038 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.auth-card {
  background: #fff;
  border-radius: 16px;
  padding: 40px 40px 32px;
  width: 100%;
  max-width: 560px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.auth-logo {
  text-align: center;
  margin-bottom: 32px;
  h1 {
    font-size: 20px;
    font-weight: 700;
    color: #303133;
    margin: 12px 0 6px;
  }
  p {
    font-size: 13px;
    color: #909399;
  }
}

.role-selector {
  display: flex;
  gap: 12px;
  width: 100%;

  .role-card {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    padding: 14px 8px;
    border: 2px solid #EBEEF5;
    border-radius: 10px;
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      border-color: #409EFF;
      background: #f0f7ff;
    }

    &.active {
      border-color: #409EFF;
      background: #409EFF;
      .role-name { color: #fff; }
      .role-desc { color: rgba(255,255,255,0.75); }
    }

    .role-name {
      font-size: 14px;
      font-weight: 600;
      color: #303133;
    }
    .role-desc {
      font-size: 11px;
      color: #909399;
    }
  }
}

.auth-form {
  :deep(.el-form-item__label) {
    font-weight: 500;
    color: #606266;
    padding-bottom: 4px;
  }
  :deep(.el-input__wrapper) {
    border-radius: 8px;
  }
}

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  border-radius: 8px;
  margin-top: 4px;
}

.auth-footer {
  text-align: center;
  font-size: 13px;
  color: #909399;
  margin-top: 12px;
}
</style>
