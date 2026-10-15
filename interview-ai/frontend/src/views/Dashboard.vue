<template>
  <CandidateDashboard v-if="store.isCandidatePortal" />

  <div v-else class="page-container">
    <!-- 欢迎横幅 -->
    <div class="welcome-banner" :class="store.isInterviewerPortal ? 'interviewer-banner' : 'admin-banner'">
      <div>
        <h2>👋 欢迎回来，{{ store.userInfo?.nickname || store.userInfo?.username || '用户' }}！</h2>
        <p v-if="store.isAdmin">管理员无需参加面试，可在此查看平台面试数据和管理业务。</p>
        <p v-else-if="store.isInterviewerPortal">欢迎进入面试官工作台，可查看候选人记录并进行人工评分。</p>
        <p v-else>准备好开始今天的模拟面试了吗？</p>
      </div>
      <el-button v-if="store.isCandidatePortal" type="primary" size="large" @click="$router.push('/scenarios')">
        <el-icon style="margin-right: 6px"><VideoPlay /></el-icon>开始面试
      </el-button>
    </div>

    <!-- 统计卡片 -->
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="6" v-for="stat in statCards" :key="stat.label">
        <el-card shadow="hover">
          <div class="stat-card">
            <div class="stat-value" :style="{ color: stat.color }">{{ stat.value }}</div>
            <div class="stat-label">{{ stat.label }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 最近记录 -->
    <el-card shadow="never">
      <template #header>
        <div class="flex-between">
          <span style="font-weight: 600">📋 最近面试记录</span>
          <el-button text type="primary" @click="$router.push('/records')">查看全部 →</el-button>
        </div>
      </template>

      <el-table :data="recentRecords" style="width: 100%" v-loading="loadingRecords" empty-text="暂无面试记录">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="得分" width="90">
          <template #default="{ row }">
            <span v-if="row.score != null" style="color: #67C23A; font-weight: 600">{{ row.score }}</span>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="160">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button v-if="store.isCandidatePortal && (row.status === 'pending' || row.status === 'ongoing')"
              text type="primary" size="small" @click="goInterview(row)">
              继续
            </el-button>
            <el-button v-else-if="store.hasPermission('record:score') && row.status === 'completed'"
              text type="warning" size="small" @click="$router.push('/records')">
              评审
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { VideoPlay } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import { listMyRecords, listScenarios } from '../api/interview'
import CandidateDashboard from './CandidateDashboard.vue'

const router = useRouter()
const store = useUserStore()

const loadingRecords = ref(false)
const records = ref([])
const scenarioCount = ref(0)

const stats = reactive({
  total: 0,
  completed: 0,
  ongoing: 0
})

const statCards = computed(() => [
  { label: '面试总次数', value: stats.total, color: '#409EFF' },
  { label: '已完成', value: stats.completed, color: '#67C23A' },
  { label: '进行中', value: stats.ongoing, color: '#E6A23C' },
  { label: '可用场景', value: scenarioCount.value, color: '#764ba2' }
])

const recentRecords = computed(() => records.value.slice(0, 5))

const statusType = (s) => ({ pending: 'info', ongoing: 'primary', completed: 'success', cancelled: 'danger' }[s] || 'info')
const statusText = (s) => ({ pending: '待开始', ongoing: '进行中', completed: '已完成', cancelled: '已取消' }[s] || s)
const formatTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : '—'

const goInterview = (row) => {
  router.push(`/interview/${row.id}?scenarioId=${row.scenarioId}`)
}

onMounted(async () => {
  loadingRecords.value = true
  try {
    const [recordsRes, scenariosRes] = await Promise.all([
      listMyRecords(),
      listScenarios()
    ])
    records.value = recordsRes.data || []
    scenarioCount.value = (scenariosRes.data || []).length
    stats.total = records.value.length
    stats.completed = records.value.filter(r => r.status === 'completed').length
    stats.ongoing = records.value.filter(r => r.status === 'ongoing').length
  } catch (e) { /* handled */ }
  loadingRecords.value = false
})
</script>

<style scoped>
.welcome-banner {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
  padding: 28px 32px;
  color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.welcome-banner h2 {
  font-size: 22px;
  margin-bottom: 6px;
}

.welcome-banner p {
  font-size: 14px;
  opacity: 0.8;
}

.welcome-banner .el-button {
  background: rgba(255, 255, 255, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.4);
  font-size: 15px;
}

.welcome-banner .el-button:hover {
  background: rgba(255, 255, 255, 0.3);
}

.welcome-banner.admin-banner {
  background: linear-gradient(135deg, #1e3a8a 0%, #334155 100%);
}

.welcome-banner.interviewer-banner {
  background: linear-gradient(135deg, #0f766e 0%, #155e75 100%);
}
</style>
