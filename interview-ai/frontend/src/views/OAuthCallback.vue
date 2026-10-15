<template>
  <div class="oauth-callback">
    <el-card shadow="never" class="oauth-card">
      <div v-if="processing" class="oauth-status">
        <el-icon class="is-loading"><Loading /></el-icon>
        <p>正在完成第三方登录...</p>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../store/user'

const route = useRoute()
const router = useRouter()
const store = useUserStore()
const processing = ref(true)

onMounted(async () => {
  const loginCode = route.query.loginCode

  if (!loginCode) {
    processing.value = false
    router.replace({
      path: '/login',
      query: { oauth: 'missing_code' }
    })
    return
  }

  try {
    await store.doOAuthLogin(String(loginCode))
    router.replace(store.isAdmin ? '/admin/users' : '/dashboard')
  } catch (e) {
    router.replace({
      path: '/login',
      query: { oauth: 'exchange_failed' }
    })
  } finally {
    processing.value = false
  }
})
</script>

<style scoped>
.oauth-callback {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
}

.oauth-card {
  width: 360px;
  text-align: center;
}

.oauth-status {
  color: #606266;
  padding: 24px 0;
}
</style>
