<template>
  <div class="auth-page">
    <div class="auth-card">
      <!-- Logo区域 -->
      <div class="auth-logo">
        <el-icon size="36" color="#409EFF"><Monitor /></el-icon>
        <h1>面试平台管理系统</h1>
        <p>欢迎回来，请登录</p>
      </div>

      <!-- 登录表单 -->
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="auth-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名（4-20个字符）"
            prefix-icon="User"
            clearable
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            show-password
          />
        </el-form-item>

        <!-- 记住登录 + 快捷提示 -->
        <div class="form-options">
          <el-checkbox v-model="form.remember">记住登录状态</el-checkbox>
          <el-dropdown trigger="click" @command="fillQuickAccount">
            <el-link type="primary" :underline="false" style="font-size:12px">
              快速填入示例账号
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-link>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="acc in quickAccounts"
                  :key="acc.username"
                  :command="acc"
                >
                  {{ acc.label }}（{{ acc.username }}）
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <!-- 登录按钮 -->
        <el-form-item>
          <el-button
            type="primary"
            :loading="authStore.loading"
            class="submit-btn"
            @click="handleLogin"
          >
            {{ authStore.loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>

        <div class="auth-footer">
          还没有账号？
          <el-link type="primary" @click="router.push('/register')">立即注册（默认候选人）</el-link>
        </div>
      </el-form>

      <!-- 密码提示 -->
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="hint-alert"
      >
        <template #default>
          测试账号：管理员 <code>admin/admin123</code>；面试官 <code>interviewer01/iv123456</code>；候选人 <code>test01/test12345</code>
        </template>
      </el-alert>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  password: '',
  remember: false
})

// 快速填入示例账号（后端真实测试账号）
const quickAccounts = [
  { label: '管理员', username: 'admin', password: 'admin123' },
  { label: '面试官', username: 'interviewer01', password: 'iv123456' },
  { label: '候选人', username: 'test01', password: 'test12345' }
] as const

function fillQuickAccount(acc: { username: string; password: string }) {
  form.username = acc.username
  form.password = acc.password
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度4-20个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ]
}

async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const result = await authStore.login(form)
  if (result.success) {
    ElMessage.success(result.message)
    router.push('/')
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
  padding: 40px 40px 28px;
  width: 100%;
  max-width: 440px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.auth-logo {
  text-align: center;
  margin-bottom: 28px;
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

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: -8px 0 16px;
}

.submit-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
  border-radius: 8px;
}

.auth-footer {
  text-align: center;
  font-size: 13px;
  color: #909399;
  margin-top: 12px;
}

.hint-alert {
  margin-top: 16px;
  border-radius: 8px;
  font-size: 12px;
  code {
    background: rgba(64,158,255,0.12);
    padding: 1px 4px;
    border-radius: 3px;
    color: #409EFF;
    font-family: monospace;
  }
}
</style>