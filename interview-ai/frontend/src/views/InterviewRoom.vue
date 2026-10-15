<template>
  <div class="page-container">
    <!-- 顶部信息栏 -->
    <div class="interview-header">
      <div style="display: flex; align-items: center; gap: 16px">
        <el-button text @click="goBack">
          <el-icon><ArrowLeft /></el-icon>返回
        </el-button>
        <span style="font-weight: 600; font-size: 16px">{{ scenarioName || '模拟面试' }}</span>
        <el-tag v-if="recordStatus" :type="statusType(recordStatus)" size="small">
          {{ statusText(recordStatus) }}
        </el-tag>
      </div>

      <div style="display: flex; align-items: center; gap: 16px">
        <div v-if="timerRunning" style="display: flex; align-items: center; gap: 6px">
          <el-icon style="color: #E6A23C"><Timer /></el-icon>
          <span style="font-size: 18px; font-weight: 600; color: #E6A23C; font-variant-numeric: tabular-nums">
            {{ formatTimer(elapsedSeconds) }}
          </span>
        </div>
        <el-progress :percentage="progress" :stroke-width="8" style="width: 160px" />
      </div>
    </div>

    <!-- 已完成 -->
    <div v-if="recordStatus === 'completed'" class="flex-center" style="padding: 60px 0">
      <el-result icon="success" title="面试已提交" sub-title="AI 正在生成评测报告，请稍后在面试记录中查看结果">
        <template #extra>
          <el-button type="primary" @click="$router.push('/records')">查看面试记录</el-button>
          <el-button @click="$router.push('/scenarios')">再考一次</el-button>
        </template>
      </el-result>
    </div>

    <!-- 已取消 -->
    <div v-else-if="recordStatus === 'cancelled'" class="flex-center" style="padding: 60px 0">
      <el-result icon="info" title="面试已取消" sub-title="你可以返回场景列表重新开始一场面试">
        <template #extra>
          <el-button type="primary" @click="$router.push('/scenarios')">返回场景列表</el-button>
        </template>
      </el-result>
    </div>

    <!-- 答题区 -->
    <div v-else-if="questions.length > 0">
      <el-card class="question-card" shadow="never">
        <!-- 题目信息 -->
        <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
          <div style="display: flex; align-items: center; gap: 10px">
            <el-tag type="primary" effect="dark" round>
              第 {{ currentIndex + 1 }} / {{ questions.length }} 题
            </el-tag>
            <el-tag size="small" :type="typeTag(questions[currentIndex].type)">
              {{ typeText(questions[currentIndex].type) }}
            </el-tag>
            <el-tag size="small" :type="difficultyType(questions[currentIndex].difficulty)">
              {{ difficultyText(questions[currentIndex].difficulty) }}
            </el-tag>
          </div>
        </div>

        <!-- 题目内容 -->
        <div class="question-text">{{ questions[currentIndex].content }}</div>

        <!-- 答题区域 -->
        <el-input v-model="answers[currentIndex]" type="textarea" :rows="8"
          :placeholder="`请输入你对第 ${currentIndex + 1} 题的回答...`"
          style="font-size: 15px" />

        <!-- 导航按钮 -->
        <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 20px">
          <el-button :disabled="currentIndex === 0" @click="prevQuestion">
            <el-icon><ArrowLeft /></el-icon>上一题
          </el-button>

          <div style="display: flex; gap: 8px">
            <el-tag v-for="(q, i) in questions" :key="i" :type="i === currentIndex ? 'primary' : (answers[i] ? 'success' : 'info')"
              style="cursor: pointer" @click="currentIndex = i" size="small" effect="plain">
              {{ i + 1 }}
            </el-tag>
          </div>

          <template v-if="currentIndex < questions.length - 1">
            <el-button type="primary" @click="nextQuestion">
              下一题<el-icon><ArrowRight /></el-icon>
            </el-button>
          </template>
          <template v-else>
            <el-button type="success" :loading="submitting" @click="handleSubmit">
              <el-icon><Check /></el-icon>提交面试
            </el-button>
          </template>
        </div>
      </el-card>
    </div>

    <!-- 加载中 -->
    <div v-else-if="loading" class="flex-center" style="padding: 80px 0">
      <el-skeleton style="width: 600px" :rows="5" animated />
    </div>

    <!-- 无题目 -->
    <div v-else class="flex-center" style="padding: 80px 0">
      <el-empty description="该场景暂无可用题目" />
    </div>

    <!-- 取消按钮 -->
    <div v-if="recordStatus === 'ongoing' || recordStatus === 'pending'" style="text-align: center; margin-top: 20px">
      <el-button type="danger" plain @click="handleCancel">取消面试</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowRight, Check, Timer } from '@element-plus/icons-vue'
import {
  startInterview, submitInterview, cancelInterview,
  getRecordQuestions, getScenario, listMyRecords
} from '../api/interview'

const route = useRoute()
const router = useRouter()

const recordId = route.params.recordId
const scenarioId = route.query.scenarioId

const loading = ref(true)
const submitting = ref(false)
const questions = ref([])
const answers = ref([])
const currentIndex = ref(0)
const recordStatus = ref('')
const scenarioName = ref('')
const answerData = ref('')

// 计时器
const timerRunning = ref(false)
const elapsedSeconds = ref(0)
let timerInterval = null

const progress = computed(() => {
  if (questions.value.length === 0) return 0
  return Math.round((currentIndex.value + 1) / questions.value.length * 100)
})

const statusType = (s) => ({ pending: 'info', ongoing: 'primary', completed: 'success', cancelled: 'danger' }[s] || 'info')
const statusText = (s) => ({ pending: '待开始', ongoing: '进行中', completed: '已完成', cancelled: '已取消' }[s] || s)
const typeTag = (t) => ({ technical: 'primary', behavioral: 'warning', hr: 'success' }[t] || 'info')
const typeText = (t) => ({ technical: '技术题', behavioral: '行为题', hr: 'HR 面' }[t] || t)
const difficultyType = (d) => ({ 1: 'success', 2: 'warning', 3: 'danger' }[d] || 'info')
const difficultyText = (d) => ({ 1: '简单', 2: '中等', 3: '困难' }[d] || '未知')
const formatTimer = (s) => {
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}`
}

const startTimer = () => {
  timerRunning.value = true
  timerInterval = setInterval(() => { elapsedSeconds.value++ }, 1000)
}

const stopTimer = () => {
  timerRunning.value = false
  if (timerInterval) clearInterval(timerInterval)
}

const goBack = () => {
  if (recordStatus.value === 'ongoing') {
    ElMessageBox.confirm('面试正在进行中，离开后计时将暂停，你可以稍后在面试记录中继续。确定离开吗？',
      '提示', { confirmButtonText: '确定离开', cancelButtonText: '继续答题', type: 'warning' }
    ).then(() => { stopTimer(); router.push('/scenarios') }).catch(() => {})
  } else {
    router.back()
  }
}

const prevQuestion = () => { if (currentIndex.value > 0) currentIndex.value-- }
const nextQuestion = () => { if (currentIndex.value < questions.value.length - 1) currentIndex.value++ }

const loadData = async () => {
  loading.value = true
  try {
    // 获取场景名
    if (scenarioId) {
      const sRes = await getScenario(scenarioId)
      scenarioName.value = sRes.data?.name || ''
    }

    // 获取记录列表，找到当前记录的状态
    const rRes = await listMyRecords()
    const record = (rRes.data || []).find(r => String(r.id) === String(recordId))
    recordStatus.value = record?.status || 'pending'
    answerData.value = record?.answerData || ''

    // 获取随机题目
    if (scenarioId && recordStatus.value !== 'completed' && recordStatus.value !== 'cancelled') {
      const qRes = await getRecordQuestions(recordId)
      questions.value = qRes.data || []
      answers.value = questions.value.map(() => '')

      // 如果还没有开始，自动开始面试
      if (recordStatus.value === 'pending') {
        await startInterview(recordId)
        recordStatus.value = 'ongoing'
      }
      startTimer()
    }
  } catch (e) { /* handled */ }
  loading.value = false
}

const handleSubmit = async () => {
  const answered = answers.value.filter(a => a.trim()).length
  if (answered === 0) {
    ElMessage.warning('请至少回答一道题目')
    return
  }

  try {
    await ElMessageBox.confirm(
      `你已回答 ${answered}/${questions.value.length} 题，确定要提交面试吗？提交后不可修改。`,
      '提交面试',
      { confirmButtonText: '确定提交', cancelButtonText: '再检查一下', type: 'info' }
    )
    submitting.value = true
    stopTimer()

    const payload = questions.value.map((q, i) => ({
      questionId: q.id,
      content: q.content,
      answer: answers.value[i] || ''
    }))

    await submitInterview(recordId, {
      answerData: JSON.stringify({ duration: elapsedSeconds.value, answers: payload })
    })
    recordStatus.value = 'completed'
    ElMessage.success('面试提交成功！')
  } catch (e) { /* cancelled or error */ }
  submitting.value = false
}

const handleCancel = async () => {
  try {
    await ElMessageBox.confirm('确定要取消这场面试吗？取消后无法恢复。',
      '取消面试', { confirmButtonText: '确定取消', cancelButtonText: '继续答题', type: 'warning' })
    stopTimer()
    await cancelInterview(recordId)
    recordStatus.value = 'cancelled'
    ElMessage.info('面试已取消')
  } catch (e) { /* cancelled or error */ }
}

onMounted(loadData)
onUnmounted(stopTimer)
</script>
