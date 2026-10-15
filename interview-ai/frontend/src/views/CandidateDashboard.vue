<template>
  <div class="candidate-dashboard" v-loading="loading">
    <section class="hero-panel">
      <div class="hero-copy">
        <span class="eyebrow">AI INTERVIEW PRACTICE</span>
        <h1>{{ greeting }}，{{ displayName }}</h1>
        <p>选择目标岗位场景，完成一场沉浸式模拟面试，让 AI 帮你发现优势与提升空间。</p>
        <div class="hero-actions">
          <el-button type="primary" size="large" round @click="router.push('/scenarios')">
            <el-icon><VideoPlay /></el-icon>
            <span>开始模拟面试</span>
          </el-button>
          <el-button text @click="router.push('/records')">
            查看面试报告<el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </div>

      <div class="hero-score">
        <div class="score-ring" :style="{ '--score': `${avgScore}%` }">
          <div class="score-ring-inner">
            <strong>{{ avgScoreText }}</strong>
            <span>平均得分</span>
          </div>
        </div>
        <p>已完成 {{ stats.completed }} 场模拟面试</p>
      </div>
    </section>

    <section class="metric-grid">
      <article v-for="metric in metrics" :key="metric.label" class="metric-card">
        <div class="metric-icon" :style="{ color: metric.color, background: metric.bg }">
          <el-icon><component :is="metric.icon" /></el-icon>
        </div>
        <div>
          <strong>{{ metric.value }}</strong>
          <span>{{ metric.label }}</span>
        </div>
      </article>
    </section>

    <section class="dashboard-grid">
      <div class="main-column">
        <div class="section-heading">
          <div>
            <h2>推荐面试场景</h2>
            <p>根据当前题库为你推荐的练习方向</p>
          </div>
          <el-button text type="primary" @click="router.push('/scenarios')">
            全部场景<el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>

        <div v-if="recommendedScenarios.length" class="scenario-list">
          <article v-for="scene in recommendedScenarios" :key="scene.id" class="scenario-row">
            <div class="scene-avatar">{{ fieldIcon(scene.techField) }}</div>
            <div class="scene-info">
              <h3>{{ scene.name }}</h3>
              <p>{{ scene.description || '暂无场景描述' }}</p>
              <div class="scene-tags">
                <el-tag size="small" effect="plain">{{ scene.techField }}</el-tag>
                <el-tag size="small" :type="difficultyType(scene.difficulty)" effect="light">
                  {{ difficultyText(scene.difficulty) }}
                </el-tag>
              </div>
            </div>
            <el-button type="primary" plain round @click="startInterview(scene)">开始</el-button>
          </article>
        </div>
        <el-empty v-else description="暂无可选面试场景" :image-size="80" />
      </div>

      <aside class="side-column">
        <el-card class="side-card" shadow="never">
          <div class="card-title">
            <span>面试进度</span>
            <strong>{{ progressPercent }}%</strong>
          </div>
          <el-progress :percentage="progressPercent" :stroke-width="8" :show-text="false" color="#6366f1" />
          <div class="progress-detail">
            <span><i class="completed-dot"></i>已完成 {{ stats.completed }}</span>
            <span><i class="ongoing-dot"></i>进行中 {{ stats.ongoing }}</span>
          </div>
        </el-card>

        <el-card class="side-card recent-card" shadow="never">
          <div class="card-title">
            <span>最近记录</span>
            <el-button text type="primary" size="small" @click="router.push('/records')">查看全部</el-button>
          </div>
          <div v-if="recentRecords.length" class="recent-list">
            <button v-for="record in recentRecords" :key="record.id" type="button"
              class="recent-item" @click="openRecord(record)">
              <span class="record-status" :class="record.status"></span>
              <span class="record-main">
                <strong>{{ scenarioNameMap[record.scenarioId] || `场景 #${record.scenarioId}` }}</strong>
                <small>{{ formatTime(record.createdAt) }}</small>
              </span>
              <span class="record-score">
                {{ record.score != null ? `${record.score}分` : statusText(record.status) }}
              </span>
            </button>
          </div>
          <div v-else class="empty-records">还没有面试记录，先从一个场景开始吧。</div>
        </el-card>
      </aside>
    </section>
    <!-- 开始面试：可选择固定试卷 -->
    <el-dialog v-model="startDialogVisible" title="开始面试" width="520px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="面试场景">
          <span>{{ currentScenario?.name }}</span>
        </el-form-item>
        <el-form-item label="面试类型">
          <el-select v-model="interviewType" style="width: 100%">
            <el-option label="答题面试" value="quiz" />
            <el-option label="AI 视频面试" value="video" />
          </el-select>
        </el-form-item>
        <el-form-item label="预约时间">
          <el-date-picker v-model="scheduleRange" type="datetimerange" start-placeholder="开始时间"
            end-placeholder="结束时间" style="width: 100%" />
          <div style="font-size: 12px; color: #909399; margin-top: 4px">留空则立即开始；填写后在预约时间内进入面试。</div>
        </el-form-item>
        <el-form-item label="使用试卷">
          <el-select v-model="selectedPaperId" placeholder="不选则随机出题" clearable style="width: 100%">
            <el-option v-for="p in availablePapers" :key="p.id"
              :label="`${p.title}（${p.totalCount}题）`" :value="p.id" />
          </el-select>
          <div style="font-size: 12px; color: #e6a23c; margin-top: 4px">
            <template v-if="!availablePapers.length">该场景暂无可用的固定试卷，将使用随机题目。</template>
            <template v-else>选择试卷后，面试将使用该试卷的固定题目；不选则随机抽取。</template>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="starting" @click="confirmStart">开始面试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Clock, DataAnalysis, Document, Trophy, VideoPlay } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import { createSession, listMyRecords, listScenarios, listAvailablePapers } from '../api/interview'

const router = useRouter()
const store = useUserStore()
const loading = ref(true)
const startDialogVisible = ref(false)
const currentScenario = ref(null)
const selectedPaperId = ref(null)
const availablePapers = ref([])
const starting = ref(false)
const interviewType = ref('quiz')
const scheduleRange = ref([])
const records = ref([])
const scenarios = ref([])
const stats = reactive({ total: 0, completed: 0, ongoing: 0 })

const displayName = computed(() => store.userInfo?.nickname || store.userInfo?.username || '候选人')
const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 12) return '上午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

const avgScore = computed(() => {
  const scores = records.value.filter(r => r.score != null).map(r => Number(r.score))
  if (!scores.length) return 0
  return Math.round(scores.reduce((sum, score) => sum + score, 0) / scores.length)
})
const avgScoreText = computed(() => records.value.some(r => r.score != null) ? `${avgScore.value}` : '--')
const progressPercent = computed(() => stats.total ? Math.round(stats.completed / stats.total * 100) : 0)
const recentRecords = computed(() => records.value.slice(0, 4))
const recommendedScenarios = computed(() => scenarios.value.slice(0, 3))
const scenarioNameMap = computed(() => Object.fromEntries(scenarios.value.map(s => [s.id, s.name])))

const metrics = computed(() => [
  { label: '累计面试', value: stats.total, icon: Document, color: '#4f46e5', bg: '#eef2ff' },
  { label: '已完成', value: stats.completed, icon: Trophy, color: '#16a34a', bg: '#ecfdf5' },
  { label: '进行中', value: stats.ongoing, icon: Clock, color: '#d97706', bg: '#fffbeb' },
  { label: '平均得分', value: avgScoreText.value, icon: DataAnalysis, color: '#7c3aed', bg: '#f5f3ff' }
])

const difficultyType = (value) => ({ 1: 'success', 2: 'warning', 3: 'danger' }[value] || 'info')
const difficultyText = (value) => ({ 1: '初级', 2: '中级', 3: '高级' }[value] || '未知')
const fieldIcon = (field) => ({ AI: '🤖', 大数据: '📊', 物联网: '📡', 后端: '⚙️', 前端: '🎨', 测试: '🧪' }[field] || '💼')
const statusText = (status) => ({ pending: '待开始', ongoing: '进行中', completed: '已完成', cancelled: '已取消' }[status] || status)
const formatTime = (value) => value ? new Date(value).toLocaleDateString('zh-CN') : '—'

const startInterview = async (scene) => {
  currentScenario.value = scene
  interviewType.value = 'quiz'
  scheduleRange.value = []
  selectedPaperId.value = null
  availablePapers.value = []
  startDialogVisible.value = true
  try {
    const res = await listAvailablePapers(scene.id)
    availablePapers.value = res.data || []
  } catch (e) { /* handled */ }
}

const confirmStart = async () => {
  if (!currentScenario.value) return
  starting.value = true
  try {
    const sched = scheduleRange.value && scheduleRange.value.length === 2 ? scheduleRange.value : null
    const payload = {
      scenarioId: currentScenario.value.id,
      paperId: selectedPaperId.value || null,
      scheduledAt: sched ? sched[0] : null,
      plannedEndAt: sched ? sched[1] : null,
      metadataJson: JSON.stringify({ device: 'web', type: interviewType.value })
    }
    const res = await createSession(payload)
    const record = res.data
    startDialogVisible.value = false
    if (sched) {
      ElMessage.success('面试已预约，请在预约时间进入面试')
      router.push('/records')
    } else {
      ElMessage.success('面试已开始')
      if (interviewType.value === 'video') {
        router.push(`/interview-room/${record.id}`)
      } else {
        router.push(`/interview/${record.id}?scenarioId=${currentScenario.value.id}`)
      }
    }
  } catch (e) { /* handled */ }
  starting.value = false
}

const openRecord = (record) => {
  if (record.status === 'pending' || record.status === 'ongoing') {
    const meta = record.metadataJson ? JSON.parse(record.metadataJson) : {}
    if (meta.type === 'video') {
      router.push(`/interview-room/${record.id}`)
    } else {
      router.push(`/interview/${record.id}?scenarioId=${record.scenarioId}`)
    }

  } else {
    router.push('/records')
  }
}
const loadRecords = async () => {
  const res = await listMyRecords()
  records.value = res.data || []
  stats.total = records.value.length
  stats.completed = records.value.filter(r => r.status === 'completed').length
  stats.ongoing = records.value.filter(r => r.status === 'ongoing').length
}

const loadScenarios = async () => {
  const res = await listScenarios()
  scenarios.value = res.data || []
}

const loadData = async () => {
  try {
    await Promise.all([loadRecords(), loadScenarios()])
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.candidate-dashboard {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.hero-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 28px 32px;
  border-radius: 18px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
}

.hero-copy { max-width: 620px; }
.eyebrow { display: block; font-size: 12px; letter-spacing: 2px; opacity: 0.85; }
.hero-copy h1 { margin: 10px 0 8px; font-size: 26px; }
.hero-copy p { margin: 0; color: rgba(255, 255, 255, 0.9); }
.hero-actions { margin-top: 20px; display: flex; gap: 12px; align-items: center; }
.hero-actions :deep(.el-button.is-text) {
  color: #fff;
}
.hero-actions :deep(.el-button.is-text:hover) {
  color: #fff;
  background: rgba(255, 255, 255, 0.14);
}
.hero-actions :deep(.el-button.is-text .el-icon) {
  color: #fff;
}

.hero-score { text-align: center; }
.score-ring {
  width: 110px; height: 110px; margin: 0 auto; border-radius: 50%;
  background: conic-gradient(#fff var(--score), rgba(255, 255, 255, 0.25) 0);
  display: grid; place-items: center;
}
.score-ring-inner {
  width: 86px; height: 86px; border-radius: 50%; background: #6d5ef0;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
}
.score-ring-inner strong { font-size: 24px; }
.score-ring-inner span { font-size: 11px; opacity: 0.85; }
.hero-score p { margin: 12px 0 0; font-size: 13px; opacity: 0.9; }

.metric-grid {
  display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px;
}
.metric-card {
  display: flex; align-items: center; gap: 12px; padding: 16px;
  border: 1px solid #eef0f6; border-radius: 14px; background: #fff;
}
.metric-icon {
  width: 42px; height: 42px; border-radius: 12px; display: grid; place-items: center; flex-shrink: 0;
}
.metric-card strong { font-size: 20px; display: block; }
.metric-card span { font-size: 12px; color: #64748b; }

.dashboard-grid {
  display: grid; grid-template-columns: 1fr 320px; gap: 18px; align-items: start;
}
.section-heading {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;
}
.section-heading h2 { margin: 0; font-size: 18px; }
.section-heading p { margin: 4px 0 0; font-size: 13px; color: #94a3b8; }

.scenario-list { display: flex; flex-direction: column; gap: 12px; }
.scenario-row {
  display: flex; align-items: center; gap: 14px; padding: 14px;
  border: 1px solid #eef0f6; border-radius: 14px; background: #fff;
}
.scene-avatar {
  width: 46px; height: 46px; border-radius: 12px; background: #f1f0ff;
  display: grid; place-items: center; font-size: 22px; flex-shrink: 0;
}
.scene-info { flex: 1; min-width: 0; }
.scene-info h3 { margin: 0 0 4px; font-size: 16px; }
.scene-info p { margin: 0 0 8px; font-size: 13px; color: #64748b; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.scene-tags { display: flex; gap: 6px; }

.side-column { display: flex; flex-direction: column; gap: 16px; }
.side-card { border-radius: 14px; }
.card-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px; font-weight: 600; }
.progress-detail { display: flex; gap: 18px; margin-top: 12px; font-size: 13px; color: #64748b; }
.progress-detail span { display: inline-flex; align-items: center; gap: 6px; }
.completed-dot, .ongoing-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.completed-dot { background: #16a34a; }
.ongoing-dot { background: #d97706; }

.recent-list { display: flex; flex-direction: column; gap: 8px; }
.recent-item {
  display: flex; align-items: center; gap: 10px; width: 100%; padding: 10px;
  border: 1px solid #eef0f6; border-radius: 10px; background: #fff; cursor: pointer; text-align: left;
}
.recent-item:hover { border-color: #c7d2fe; background: #fafbff; }
.record-status { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.record-status.completed { background: #16a34a; }
.record-status.ongoing { background: #d97706; }
.record-status.pending { background: #64748b; }
.record-status.cancelled { background: #ef4444; }
.record-main { flex: 1; min-width: 0; }
.record-main strong { display: block; font-size: 14px; }
.record-main small { font-size: 12px; color: #94a3b8; }
.record-score { font-size: 13px; color: #6366f1; font-weight: 600; }
.empty-records { padding: 18px; text-align: center; color: #94a3b8; font-size: 13px; }

@media (max-width: 980px) {
  .hero-panel { flex-direction: column; text-align: center; }
  .metric-grid { grid-template-columns: repeat(2, 1fr); }
  .dashboard-grid { grid-template-columns: 1fr; }
}
</style>
