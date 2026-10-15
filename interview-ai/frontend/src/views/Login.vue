<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-title">🤖 多模态智能模拟面试</div>
      <div class="auth-subtitle">AI 驱动的面试评测平台</div>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" :prefix-icon="Lock"
            show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
        <!-- 临时隐藏：Gitee 登录当前不可用（阶段演示先走密码登录）。
             原因：SecurityConfig 使用 SessionCreationPolicy.STATELESS，
             而 OAuth2AuthorizationRequestRepository 仍是默认的 session 实现，
             回调时必然抛出 authorization_request_not_found。
             改用 Cookie 版授权请求仓库后，删掉下面的 v-if="false" 即可恢复。 -->
        <el-form-item v-if="false">
          <el-button type="success" style="width: 100%" @click="loginWithGitee">
            使用 Gitee 登录
          </el-button>
        </el-form-item>
      </el-form>

      <div style="text-align: center">
        <span style="color: #909399; font-size: 14px">还没有账号？</span>
        <router-link to="/register" style="color: #409EFF; font-size: 14px; text-decoration: none">
          立即注册
        </router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', password: '' })

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '用户名长度 4-20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 30, message: '密码长度 6-30 个字符', trigger: 'blur' }
  ]
}

const loginWithGitee = () => {
  window.location.href = '/oauth2/authorization/gitee'
}

onMounted(() => {
  const oauthStatus = route.query.oauth
  if (oauthStatus === 'missing_code') {
    ElMessage.error('第三方登录参数缺失')
  } else if (oauthStatus === 'exchange_failed') {
    ElMessage.error('第三方登录失败，请重试')
  }
})
const handleLogin = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await store.doLogin(form)
    ElMessage.success('登录成功')
    router.push(store.isAdmin ? '/admin/users' : '/dashboard')
  } catch (e) {
    // error already handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>
