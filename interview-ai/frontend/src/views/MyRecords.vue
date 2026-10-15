<template>
  <div class="page-container">
    <div class="flex-between" style="margin-bottom: 20px">
      <h2 style="font-size: 20px; font-weight: 600">
        {{ store.isAdmin ? '📄 全部面试记录' : store.isCandidatePortal ? '📄 我的面试记录' : '📄 面试评审' }}
      </h2>
      <div class="toolbar">
        <el-button :loading="loading" @click="loadRecords">刷新记录</el-button>
        <el-select v-model="filterStatus" placeholder="筛选状态" clearable style="width: 140px"
          @change="applyFilter">
          <el-option label="全部" value="" />
          <el-option label="待开始" value="pending" />
          <el-option label="进行中" value="ongoing" />
          <el-option label="已完成" value="completed" />
          <el-option label="已取消" value="cancelled" />
        </el-select>
      </div>
    </div>

    <el-card shadow="never">
      <el-table :data="filteredRecords" style="width: 100%" v-loading="loading" empty-text="暂无面试记录">
        <el-table-column prop="id" label="记录ID" width="80" />
        <el-table-column v-if="!store.isCandidatePortal" label="候选人" min-width="130">
          <template #default="{ row }">
            {{ candidateName(row) }}
          </template>
        </el-table-column>
        <el-table-column label="场景" min-width="150">
          <template #default="{ row }">
            {{ scenarioNameMap[row.scenarioId] || `场景 #${row.scenarioId}` }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="面试类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getRecordType(row) === 'video' ? 'primary' : 'info'" size="small">
              {{ getRecordType(row) === 'llm' ? '大模型面试官' : getRecordType(row) === 'video' ? 'AI 模拟面试' : '文字答题' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="得分" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.score != null" style="font-weight: 700; color: #67C23A; font-size: 16px">
              {{ row.score }}
            </span>
            <span v-else style="color: #c0c4cc">—</span>
          </template>
        </el-table-column>
        <el-table-column label="预约" width="180">
          <template #default="{ row }">
            <div style="font-size: 12px; color: #606266">
              <div>{{ formatTime(row.scheduledAt) }}</div>
              <div style="color: #909399">{{ formatTime(row.plannedEndAt) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="360" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-if="canEnterInterview(row)"
                text type="primary" size="small" @click="enterInterview(row)">进入面试</el-button>
              <el-button v-if="store.hasPermission('interview:start') && row.status === 'pending'"
                text type="primary" size="small" @click="start(row)">开始</el-button>
              <el-button v-if="store.hasPermission('interview:submit') && row.status === 'ongoing'"
                text type="warning" size="small" @click="complete(row)">完成</el-button>
              <el-button v-if="store.hasPermission('interview:cancel') && (row.status === 'pending' || row.status === 'ongoing')"
                text type="danger" size="small" @click="cancel(row)">取消</el-button>
              <el-button v-if="!store.isCandidatePortal && store.hasPermission('record:view')"
                text type="info" size="small" @click="openEvents(row)">事件</el-button>
              <el-button v-if="!store.isCandidatePortal && row.status === 'completed' && store.hasPermission('record:score')"
                text type="warning" size="small" @click="openScoreDialog(row)">人工评分</el-button>
              <el-button v-if="row.status === 'completed'"
                text type="success" size="small" @click="viewReport(row)">查看报告</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 事件追踪 -->
    <el-dialog v-model="eventsVisible" :title="`会话事件 #${currentRecordId}`" width="700px">
      <el-table :data="events" border size="small" max-height="420">
        <el-table-column prop="eventType" label="事件类型" width="180" />
        <el-table-column prop="operatorId" label="操作人" width="90" />
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ formatTime(row.eventTime) }}</template>
        </el-table-column>
        <el-table-column prop="payloadJson" label="载荷" show-overflow-tooltip />
      </el-table>
    </el-dialog>

    <!-- 报告弹窗 -->
    <el-dialog v-model="reportVisible" title="面试评测报告" width="640px">
      <template v-if="reportData">
        <div style="text-align: center; margin-bottom: 24px">
          <div style="font-size: 48px; font-weight: 700; color: #409EFF">{{ reportData.score ?? '未评估' }}</div>
          <div style="font-size: 14px; color: #909399">综合评分（满分 100）</div>
        </div>

        <el-descriptions :column="2" border>
          <el-descriptions-item v-if="!store.isCandidatePortal" label="候选人" :span="2">
            {{ candidateName(reportData) }}
          </el-descriptions-item>
          <el-descriptions-item label="场景">
            {{ scenarioNameMap[reportData.scenarioId] || `场景 #${reportData.scenarioId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="用时">
            {{ reportData.duration ? `${Math.floor(reportData.duration / 60)}分${reportData.duration % 60}秒` : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(reportData.startedAt) }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ formatTime(reportData.completedAt) }}</el-descriptions-item>
        </el-descriptions>

        <div style="margin-top: 16px">
          <div style="font-weight: 600; margin-bottom: 8px">🤖 AI 评价反馈</div>
          <div style="background: #f5f7fa; border-radius: 8px; padding: 16px; line-height: 1.8; color: #606266; white-space: pre-wrap">
            {{ reportData.aiFeedback || '报告正在生成中，请稍后刷新查看...' }}
          </div>
        </div>
        <el-descriptions v-if="reportTrace" :column="1" border style="margin-top:16px">
          <el-descriptions-item label="文本理解与评价">{{ reportTrace.textStatus }} · {{ reportTrace.questionScore ?? '未评估' }}</el-descriptions-item>
          <el-descriptions-item label="视觉行为分析">{{ reportTrace.visionStatus }}</el-descriptions-item>
          <el-descriptions-item label="评分规则">{{ reportTrace.formula }}</el-descriptions-item>
          <el-descriptions-item label="输入追溯"><pre style="white-space:pre-wrap;overflow-wrap:anywhere">{{ reportTrace.input || '未提交文本' }}</pre></el-descriptions-item>
        </el-descriptions>
      </template>
      <template #footer>
        <el-button v-if="reportTrace" @click="downloadReport">下载完整报告 JSON</el-button>
        <el-button @click="reportVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 人工评分弹窗 -->
    <el-dialog v-model="scoreVisible" title="人工评分与评语" width="620px" destroy-on-close>
      <template v-if="scoreData">
        <el-descriptions :column="2" border style="margin-bottom: 20px">
          <el-descriptions-item label="记录ID">{{ scoreData.id }}</el-descriptions-item>
          <el-descriptions-item label="候选人">{{ candidateName(scoreData) }}</el-descriptions-item>
          <el-descriptions-item label="面试场景" :span="2">
            {{ scenarioNameMap[scoreData.scenarioId] || `场景 #${scoreData.scenarioId}` }}
          </el-descriptions-item>
        </el-descriptions>

        <el-form label-width="80px">
          <el-form-item label="综合评分">
            <el-input-number v-model="scoreForm.score" :min="0" :max="100" :precision="1" :step="1" />
            <span style="margin-left: 10px; color: #909399; font-size: 12px">满分 100 分</span>
          </el-form-item>
          <el-form-item label="评审意见">
            <el-input v-model="scoreForm.aiFeedback" type="textarea" :rows="5"
              maxlength="2000" show-word-limit placeholder="填写候选人的优势、不足和改进建议" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="scoreVisible = false">取消</el-button>
        <el-button type="primary" :loading="scoreSaving" @click="handleScore">保存评分</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../store/user'
import {
  listMyRecords, listScenarios, scoreInterview,
  joinSession, startSession, completeSession, cancelSession,
  listSessionEvents
} from '../api/interview'

const router = useRouter()
const store = useUserStore()

const loading = ref(false)
const records = ref([])
const scenarios = ref([])
const filterStatus = ref('')

const reportVisible = ref(false)
const reportData = ref(null)
const reportTrace = computed(() => {
  try { return JSON.parse(reportData.value?.reportJson || 'null') } catch { return null }
})
const downloadReport = () => {
  const url = URL.createObjectURL(new Blob([JSON.stringify(reportTrace.value, null, 2)], { type: 'application/json' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `面试报告-${reportData.value.id}.json`
  link.click()
  URL.revokeObjectURL(url)
}
const scoreVisible = ref(false)
const scoreSaving = ref(false)
const scoreData = ref(null)
const scoreForm = reactive({ score: 80, aiFeedback: '' })

const eventsVisible = ref(false)
const currentRecordId = ref(null)
const events = ref([])

const scenarioNameMap = computed(() => {
  const map = {}
  scenarios.value.forEach(s => { map[s.id] = s.name })
  return map
})

const filteredRecords = computed(() => {
  if (!filterStatus.value) return records.value
  return records.value.filter(r => r.status === filterStatus.value)
})

const statusType = (s) => ({ pending: 'info', ongoing: 'primary', completed: 'success', cancelled: 'danger' }[s] || 'info')
const statusText = (s) => ({ pending: '待开始', ongoing: '进行中', completed: '已完成', cancelled: '已取消' }[s] || s)
const formatTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : '—'
const candidateName = (row) => row?.nickname || row?.username || `用户 #${row?.userId ?? '—'}`

const applyFilter = () => {}

// ===== 会话生命周期 =====

// 解析记录类型：video -> 视频面试间，quiz -> 文字答题房
const getRecordType = (row) => {
  try {
    const meta = row.metadataJson ? JSON.parse(row.metadataJson) : {}
    return meta.type || 'quiz'
  } catch (e) {
    return 'quiz'
  }
}

// “进入面试”是否可用：视频面试候选人与面试官均可进；答题面试仅候选人本人进
const canEnterInterview = (row) => {
  if (row.status !== 'pending' && row.status !== 'ongoing') return false
  if (store.isAdmin) return false
  if (getRecordType(row) === 'video') return true
  return store.isCandidatePortal && store.hasPermission('interview:start')
}

// 进入面试：按类型跳转，视频走 WebRTC 面试间，答题走文字答题房
const enterInterview = async (row) => {
  if (getRecordType(row) === 'llm') { router.push(`/ai-interview/${row.id}`); return }
  if (getRecordType(row) === 'video') {
    router.push(`/interview-room/${row.id}`)
    return
  }
  // 答题：先写入加入事件，再进入文字答题房
  const role = store.isInterviewerPortal || store.isAdmin ? 'HOST' : 'CANDIDATE'
  try {
    await joinSession(row.id, role)
  } catch (e) { /* 忽略，进入答题房后由房间自身逻辑处理 */ }
  router.push(`/interview/${row.id}?scenarioId=${row.scenarioId}`)
}

const join = async (row) => {
  const role = store.isInterviewerPortal || store.isAdmin ? 'HOST' : 'CANDIDATE'
  // 视频面试：直接进入视频面试间，由 WebSocket JOIN 完成加入与事件写入
  if (role === 'CANDIDATE' && getRecordType(row) === 'video') {
    router.push(`/interview-room/${row.id}`)
    return
  }
  await joinSession(row.id, role)
  ElMessage.success('已加入')
  if (role === 'CANDIDATE') {
    router.push(`/interview/${row.id}?scenarioId=${row.scenarioId}`)
  } else {
    await loadRecords()
  }
}
const start = async (row) => {
  await startSession(row.id)
  ElMessage.success('面试已开始')
  await loadRecords()
}

const complete = async (row) => {
  await completeSession(row.id, {})
  ElMessage.success('面试已完成')
  await loadRecords()
}

const cancel = async (row) => {
  const { value } = await ElMessageBox.prompt('请输入取消原因', '取消会话')
  await cancelSession(row.id, { reason: value })
  ElMessage.success('会话已取消')
  await loadRecords()
}

const openEvents = async (row) => {
  currentRecordId.value = row.id
  const res = await listSessionEvents(row.id)
  events.value = res.data || []
  eventsVisible.value = true
}

// ===== 记录与评测 =====

const enterVideoRoom = (row) => {
  router.push(`/interview-room/${row.id}`)
}

const continueInterview = (row) => {
  if (getRecordType(row) === 'video') {
    router.push(`/interview-room/${row.id}`)
  } else {
    router.push(`/interview/${row.id}?scenarioId=${row.scenarioId}`)
  }
}

const viewReport = (row) => {
  reportData.value = row
  reportVisible.value = true
}

const openScoreDialog = (row) => {
  scoreData.value = row
  scoreForm.score = row.score != null ? Number(row.score) : 80
  scoreForm.aiFeedback = row.aiFeedback || ''
  scoreVisible.value = true
}

const handleScore = async () => {
  if (!scoreData.value || scoreForm.score == null) {
    ElMessage.warning('请填写评分')
    return
  }
  scoreSaving.value = true
  try {
    const res = await scoreInterview(scoreData.value.id, {
      score: scoreForm.score,
      aiFeedback: scoreForm.aiFeedback
    })
    const index = records.value.findIndex(r => r.id === res.data.id)
    if (index >= 0) {
      records.value[index] = {
        ...records.value[index],
        ...res.data,
        username: records.value[index].username,
        nickname: records.value[index].nickname
      }
    }
    ElMessage.success('评分已保存')
    scoreVisible.value = false
  } catch (e) { /* handled */ }
  scoreSaving.value = false
}

const loadRecords = async () => {
  loading.value = true
  try {
    const [rRes, sRes] = await Promise.all([listMyRecords(), listScenarios()])
    records.value = rRes.data || []
    scenarios.value = sRes.data || []
  } catch (e) { /* handled */ }
  loading.value = false
}

onMounted(loadRecords)
</script>

<style scoped>
.flex-between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
}
.table-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
}
</style>
