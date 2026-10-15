<script setup>
import {
  Activity,
  AlertCircle,
  ArrowRight,
  BadgeCheck,
  CircleDot,
  FileCheck2,
  FileText,
  Gauge,
  Layers3,
  LoaderCircle,
  LogOut,
  Play,
  RefreshCw,
  RotateCcw,
  Settings2,
  Timer,
  Trash2,
  TriangleAlert,
  Upload,
  Video,
  Volume2,
  VolumeX,
} from 'lucide-vue-next'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue'
import { useRouter } from 'vue-router'
import { apiBlob, apiRequest, imageFormData } from '../api/client'
import CameraPanel from '../components/CameraPanel.vue'
import ConversationPanel from '../components/ConversationPanel.vue'
import FinishDialog from '../components/FinishDialog.vue'
import LiveMetricsPanel from '../components/LiveMetricsPanel.vue'
import QuestionLimitDialog from '../components/QuestionLimitDialog.vue'
import { useCamera } from '../composables/useCamera'
import { useAuthStore } from '../stores/auth'
import { captureVideoFrame, formatDuration } from '../utils/media'

const router = useRouter()
const auth = useAuthStore()
const camera = useCamera()

const JOB_ROLES = [
  'Java 后端开发工程师',
  'Python 后端开发工程师',
  '前端开发工程师',
  '算法工程师',
  '数据分析师',
  '产品经理',
  '软件测试工程师',
  '运维开发工程师',
  '自定义岗位',
]
const DIFFICULTIES = ['初级', '中级', '高级', '专家']
const FOLLOW_UP_MODES = [
  { value: 'random', label: '随机 1-3 次 · 推荐' },
  { value: '1', label: '固定追问 1 次' },
  { value: '2', label: '固定追问 2 次' },
  { value: '3', label: '固定追问 3 次' },
]
const FPS_OPTIONS = [
  { value: 2, label: '2 帧/秒 · 低负载' },
  { value: 3, label: '3 帧/秒 · 推荐' },
  { value: 4, label: '4 帧/秒 · 高精度' },
]

const config = reactive({
  job_role: JOB_ROLES[0],
  custom_role: '',
  difficulty: '中级',
  voice: 'zh-CN-YunxiNeural',
  auto_tts: true,
  analysis_fps: 3,
  follow_up_mode: 'random',
})

const voices = ref([])
const session = ref(null)
const metrics = ref({})
const visionStatus = ref({})
const messages = ref([])
const starting = ref(false)
const opening = ref(false)
const sending = ref(false)
const transcribing = ref(false)
const ttsBusyId = ref('')
const finishOpen = ref(false)
const finishing = ref(false)
const checkpointOpen = ref(false)
const continuing = ref(false)
const selectedResume = ref(null)
const resumeInput = ref(null)
const uploadingResume = ref(false)
const notice = ref('')
const error = ref('')
const elapsedSeconds = ref(0)
const autoSpeak = ref(true)
const cameraRetries = ref(0)

let metricsTimer = null
let frameTimer = null
let clockTimer = null
let frameBusy = false
let frameErrors = 0
let disposed = false
let audioCache = new Map()
let metricsBusy = false
let frameAbortController = null
let adaptiveFrameDelay = 0

const active = computed(() => session.value?.status === 'active')
const answerCount = computed(
  () => messages.value.filter((item) => item.role === 'user').length,
)
const effectiveRole = computed(() =>
  config.job_role === '自定义岗位'
    ? config.custom_role.trim() || '自定义岗位'
    : config.job_role,
)
const frameInterval = computed(() => 1000 / Math.max(1, Number(config.analysis_fps)))
const cameraHealthy = computed(() => camera.status.value === 'ready')
const faceDetected = computed(() => Boolean(metrics.value.face_detected))
const expressionLabel = computed(() => {
  if (!faceDetected.value) return '等待人脸'
  return metrics.value.expression || '分析中'
})
const questionLimit = computed(() => Number(session.value?.question_limit) || 12)
const currentQuestionNumber = computed(() => Math.min(
  Number(session.value?.question_count) || 0,
  questionLimit.value,
))
const questionProgress = computed(() => (
  Math.max(0, Math.min(100, (currentQuestionNumber.value / questionLimit.value) * 100))
))
const followUpSummary = computed(() => {
  const current = Number(session.value?.current_follow_up_count) || 0
  const target = Number(session.value?.follow_up_target) || 0
  if (!target) return '待配置'
  return `${current}/${target} 次`
})

onMounted(async () => {
  document.addEventListener('visibilitychange', handleVisibilityChange)
  await Promise.allSettled([loadVoices(), loadLatestResume()])
})

onBeforeUnmount(() => {
  disposed = true
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  frameAbortController?.abort()
  stopLoops()
  audioCache.forEach((url) => URL.revokeObjectURL(url))
  audioCache = new Map()
})

watch(
  () => camera.status.value,
  (status) => {
    if (status !== 'error' || !active.value || disposed) return
    if (cameraRetries.value >= 6) return
    cameraRetries.value += 1
    window.setTimeout(() => {
      if (!disposed && active.value && camera.status.value === 'error') camera.start()
    }, 2500)
  },
)

async function loadVoices() {
  try {
    const payload = await apiRequest('/api/media/voices', { timeout: 30000 })
    voices.value = payload.voices || []
    if (voices.value.length && !voices.value.some((item) => item.short_name === config.voice)) {
      config.voice = voices.value[0].short_name
    }
  } catch {
    voices.value = [
      { short_name: 'zh-CN-YunxiNeural', label: '云希 · 男声' },
      { short_name: 'zh-CN-XiaoxiaoNeural', label: '晓晓 · 女声' },
    ]
  }
}

async function loadLatestResume() {
  try {
    const payload = await apiRequest('/api/resumes/latest', { timeout: 20000 })
    selectedResume.value = payload.resume || null
  } catch {
    selectedResume.value = null
  }
}

function chooseResume() {
  if (starting.value || uploadingResume.value) return
  resumeInput.value?.click()
}

async function uploadResume(event) {
  const file = event.target?.files?.[0]
  event.target.value = ''
  if (!file || uploadingResume.value) return

  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!['pdf', 'doc', 'docx'].includes(extension)) {
    error.value = '仅支持 PDF、DOC 和 DOCX 格式的简历。'
    return
  }
  if (file.size > 8 * 1024 * 1024) {
    error.value = '简历不能超过 8 MB。'
    return
  }

  uploadingResume.value = true
  error.value = ''
  notice.value = ''
  try {
    const form = new FormData()
    form.append('resume', file, file.name)
    const payload = await apiRequest('/api/resumes', {
      method: 'POST',
      body: form,
      timeout: 180000,
    })
    selectedResume.value = payload.resume
    notice.value = `已解析简历“${payload.resume.filename}”，本轮面试会围绕其中的项目经历提问。`
  } catch (cause) {
    error.value = cause.message
  } finally {
    uploadingResume.value = false
  }
}

async function removeResume() {
  if (!selectedResume.value || uploadingResume.value) return
  const confirmed = window.confirm('确认移除上次保存的简历？后续面试将不再使用该简历。')
  if (!confirmed) return
  uploadingResume.value = true
  error.value = ''
  try {
    await apiRequest('/api/resumes/latest', {
      method: 'DELETE',
      timeout: 20000,
    })
    selectedResume.value = null
    notice.value = '简历已移除。'
  } catch (cause) {
    error.value = cause.message
  } finally {
    uploadingResume.value = false
  }
}

async function startInterview() {
  if (starting.value) return
  error.value = ''
  notice.value = ''
  starting.value = true
  cameraRetries.value = 0

  // 摄像头与后端会话同时启动，先出画面，首题随后异步生成。
  const cameraPromise = camera.start()

  try {
    const payload = await apiRequest('/api/interviews', {
      method: 'POST',
      body: {
        job_role: effectiveRole.value,
        difficulty: config.difficulty,
        voice: config.voice,
        auto_tts: config.auto_tts,
        analysis_fps: config.analysis_fps,
        snapshot_interval: 2,
        follow_up_mode: config.follow_up_mode,
        resume_id: selectedResume.value?.id || null,
      },
      timeout: 180000,
    })
    session.value = payload
    messages.value = payload.transcript || []
    autoSpeak.value = Boolean(payload.auto_tts)
    startClock()
    startMetricsLoop()
    const stream = await cameraPromise
    if (!stream) {
      notice.value = camera.error.value || '摄像头未连接成功。'
    }
    startFrameLoop()
    requestOpening()
  } catch (cause) {
    camera.stop()
    error.value = cause.message
    session.value = null
  } finally {
    starting.value = false
  }
}

async function requestOpening() {
  if (!session.value || opening.value) return
  opening.value = true
  try {
    const payload = await apiRequest(
      `/api/interviews/${session.value.id}/opening`,
      { method: 'POST', timeout: 180000 },
    )
    if (payload.message) {
      const message = payload.message
      messages.value = [
        ...messages.value.filter((item) => item.id !== message.id),
        message,
      ]
    }
    if (payload.session) session.value = { ...session.value, ...payload.session }
    if (payload.warning) notice.value = payload.warning
    if (autoSpeak.value && payload.message) speak(payload.message)
  } catch (cause) {
    error.value = `首题生成失败：${cause.message}`
  } finally {
    opening.value = false
  }
}

function startClock() {
  if (clockTimer) window.clearInterval(clockTimer)
  updateElapsed()
  clockTimer = window.setInterval(updateElapsed, 1000)
}

function updateElapsed() {
  const created = session.value?.created_at
  if (!created) {
    elapsedSeconds.value = 0
    return
  }
  const startedAt = new Date(created).getTime()
  if (Number.isNaN(startedAt)) return
  elapsedSeconds.value = Math.max(0, Math.floor((Date.now() - startedAt) / 1000))
}

function startMetricsLoop() {
  if (metricsTimer) window.clearInterval(metricsTimer)
  pullMetrics()
  metricsTimer = window.setInterval(pullMetrics, 800)
}

async function pullMetrics() {
  if (!session.value || disposed || metricsBusy || document.hidden) return
  metricsBusy = true
  try {
    const payload = await apiRequest(
      `/api/interviews/${session.value.id}/metrics`,
      { timeout: 8000 },
    )
    applyMetrics(payload.metrics)
    if (payload.vision) visionStatus.value = payload.vision
  } catch {
    /* 指标轮询失败不打断面试流程 */
  } finally {
    metricsBusy = false
  }
}

function applyMetrics(nextMetrics) {
  if (!nextMetrics || !Object.keys(nextMetrics).length) return
  const currentUpdatedAt = Number(metrics.value?.last_updated_at)
  const nextUpdatedAt = Number(nextMetrics.last_updated_at)
  if (
    Number.isFinite(currentUpdatedAt)
    && Number.isFinite(nextUpdatedAt)
    && nextUpdatedAt < currentUpdatedAt
  ) return
  metrics.value = nextMetrics
}

function startFrameLoop() {
  if (frameTimer) window.clearTimeout(frameTimer)
  frameErrors = 0
  adaptiveFrameDelay = frameInterval.value
  frameTimer = window.setTimeout(pushFrame, 260)
}

async function pushFrame() {
  if (disposed || !active.value) return
  if (document.hidden) {
    frameTimer = window.setTimeout(pushFrame, 2000)
    return
  }

  if (!frameBusy && cameraHealthy.value) {
    frameBusy = true
    frameAbortController = new AbortController()
    const requestStartedAt = performance.now()
    try {
      const blob = await captureVideoFrame(camera.videoElement.value, {
        maxWidth: 448,
        quality: 0.54,
      })
      if (blob) {
        const payload = await apiRequest(
          `/api/interviews/${session.value.id}/frames`,
          {
            method: 'POST',
            body: imageFormData(blob, {}, 'frame.jpg'),
            timeout: 8000,
            signal: frameAbortController.signal,
          },
        )
        if (payload?.metrics) applyMetrics(payload.metrics)
        if (payload?.vision) visionStatus.value = payload.vision
        frameErrors = 0

        const clientLatency = performance.now() - requestStartedAt
        const serverLatency = Number(payload?.vision?.last_latency_ms) || 0
        if (payload?.replaced_pending || serverLatency > 650 || clientLatency > 900) {
          adaptiveFrameDelay = Math.min(frameInterval.value * 2, 1800)
        } else if (serverLatency > 360 || clientLatency > 550) {
          adaptiveFrameDelay = Math.min(frameInterval.value * 1.4, 1200)
        } else {
          adaptiveFrameDelay = frameInterval.value
        }
      }
    } catch (cause) {
      if (disposed || !active.value) return
      frameErrors += 1
      if (frameErrors === 3) {
        notice.value = `视觉分析暂时不稳定：${cause.message}`
      }
      adaptiveFrameDelay = Math.min(2500, frameInterval.value * (frameErrors + 1))
    } finally {
      frameAbortController = null
      frameBusy = false
    }
  }

  const backoff = frameErrors >= 3
    ? Math.max(1800, adaptiveFrameDelay)
    : adaptiveFrameDelay
  if (!disposed && active.value) {
    frameTimer = window.setTimeout(pushFrame, backoff)
  }
}

function handleVisibilityChange() {
  if (document.hidden) {
    if (frameTimer) window.clearTimeout(frameTimer)
    frameTimer = null
    return
  }
  if (active.value && cameraHealthy.value) startFrameLoop()
}

async function sendAnswer(text) {
  if (!active.value || sending.value) return
  error.value = ''
  sending.value = true

  const optimistic = {
    id: `local-${Date.now()}`,
    role: 'user',
    content: text,
    created_at: new Date().toISOString(),
  }
  messages.value = [...messages.value, optimistic]

  try {
    const payload = await apiRequest(
      `/api/interviews/${session.value.id}/messages`,
      {
        method: 'POST',
        body: { message: text },
        timeout: 180000,
      },
    )
    messages.value = [
      ...messages.value.filter((item) => item.id !== optimistic.id),
      payload.user_message,
      ...(payload.assistant_message ? [payload.assistant_message] : []),
    ]
    if (payload.session) session.value = { ...session.value, ...payload.session }
    if (payload.checkpoint?.required) {
      checkpointOpen.value = true
    }
    if (autoSpeak.value && payload.assistant_message) speak(payload.assistant_message)
  } catch (cause) {
    messages.value = messages.value.filter((item) => item.id !== optimistic.id)
    error.value = cause.message
  } finally {
    sending.value = false
  }
}

async function handleAudio(blob) {
  if (!active.value || transcribing.value) return
  error.value = ''
  transcribing.value = true
  try {
    const form = new FormData()
    form.append('audio', blob, `answer_${Date.now()}.webm`)
    const payload = await apiRequest('/api/media/transcribe', {
      method: 'POST',
      body: form,
      timeout: 300000,
    })
    const text = (payload.text || '').trim()
    if (!text) {
      error.value = '没有识别到有效内容，请重新录制。'
      return
    }
    await sendAnswer(text)
  } catch (cause) {
    error.value = cause.message
  } finally {
    transcribing.value = false
  }
}

async function speak(message) {
  if (!message?.content || ttsBusyId.value) return
  ttsBusyId.value = message.id
  error.value = ''
  try {
    let url = audioCache.get(message.id)
    if (!url) {
      const blob = await apiBlob('/api/media/tts', {
        method: 'POST',
        body: {
          text: message.content,
          voice: session.value?.voice || config.voice,
        },
        timeout: 180000,
      })
      url = URL.createObjectURL(blob)
      audioCache.set(message.id, url)
    }
    await playUrl(url)
  } catch (cause) {
    error.value = `语音合成失败：${cause.message}`
  } finally {
    ttsBusyId.value = ''
  }
}

function playUrl(url) {
  return new Promise((resolve, reject) => {
    const audio = new Audio(url)
    audio.addEventListener('ended', resolve, { once: true })
    audio.addEventListener('error', () => reject(new Error('浏览器无法播放生成的音频。')), {
      once: true,
    })
    audio.play().catch(reject)
  })
}

function toggleAutoSpeak() {
  autoSpeak.value = !autoSpeak.value
}

async function finishInterview() {
  if (!session.value || finishing.value) return
  finishing.value = true
  error.value = ''
  try {
    const payload = await apiRequest(
      `/api/interviews/${session.value.id}/finish`,
      { method: 'POST', timeout: 300000 },
    )
    stopLoops()
    camera.stop()
    session.value = { ...session.value, status: 'finished' }
    finishOpen.value = false
    checkpointOpen.value = false
    router.push({ name: 'report-detail', params: { id: payload.report.session_id } })
  } catch (cause) {
    error.value = cause.message
    finishing.value = false
  }
}

async function continueInterview() {
  if (!session.value || continuing.value) return
  const previousSessionId = session.value.id
  continuing.value = true
  error.value = ''
  notice.value = ''
  stopLoops()

  try {
    const payload = await apiRequest('/api/interviews', {
      method: 'POST',
      body: {
        job_role: session.value.job_role,
        difficulty: session.value.difficulty,
        voice: session.value.voice,
        auto_tts: session.value.auto_tts,
        analysis_fps: session.value.analysis_fps,
        snapshot_interval: session.value.snapshot_interval,
        follow_up_mode: session.value.follow_up_mode,
        resume_id: session.value.resume?.id || null,
      },
      timeout: 180000,
    })
    session.value = payload
    messages.value = payload.transcript || []
    metrics.value = {}
    visionStatus.value = {}
    checkpointOpen.value = false
    autoSpeak.value = Boolean(payload.auto_tts)
    startClock()
    startMetricsLoop()
    startFrameLoop()
    requestOpening()
    notice.value = '已开启新一轮面试，题号与追问次数已重新计算。'

    try {
      await apiRequest(`/api/interviews/${previousSessionId}`, {
        method: 'DELETE',
        timeout: 20000,
      })
    } catch {
      /* 上一轮会话会由服务端定时清理。 */
    }
  } catch (cause) {
    error.value = cause.message
  } finally {
    continuing.value = false
  }
}

async function abandonInterview() {
  if (!session.value) return
  const confirmed = window.confirm(
    '确认放弃本次面试？本轮问答与表情注意力采样都会被丢弃。',
  )
  if (!confirmed) return
  const sessionId = session.value.id
  stopLoops()
  camera.stop()
  session.value = null
  messages.value = []
  metrics.value = {}
  checkpointOpen.value = false
  try {
    await apiRequest(`/api/interviews/${sessionId}`, {
      method: 'DELETE',
      timeout: 20000,
    })
  } catch {
    /* 会话可能已过期，忽略 */
  }
}

function stopLoops() {
  if (metricsTimer) window.clearInterval(metricsTimer)
  if (frameTimer) window.clearTimeout(frameTimer)
  if (clockTimer) window.clearInterval(clockTimer)
  frameAbortController?.abort()
  frameAbortController = null
  frameBusy = false
  metricsBusy = false
  metricsTimer = null
  frameTimer = null
  clockTimer = null
}
</script>

<template>
  <div class="page" :class="{ 'page-stage': Boolean(session) }">
    <!-- 未开始：面试配置 -->
    <section v-if="!session" class="setup">
      <header class="setup-head">
        <div>
          <span class="tag tag-positive"><BadgeCheck :size="13" /> 准备就绪</span>
          <h1>开始一场智能体模拟面试</h1>
          <p>
            面试开始后摄像头会全程开启并实时分析表情与注意力，请确认环境安静、光线充足。
          </p>
        </div>
        <div class="setup-account">
          <span>{{ auth.user?.display_name || auth.user?.username }}</span>
          <small>{{
            auth.user?.face_registered ? '人脸已绑定' : '未绑定人脸'
          }}</small>
        </div>
      </header>

      <div v-if="error" class="alert alert-danger">
        <AlertCircle :size="16" />
        <span>{{ error }}</span>
      </div>

      <div class="setup-grid">
        <section class="panel">
          <div class="panel-title">
            <span><Settings2 :size="17" /> 面试配置</span>
            <small>可随时重新开始一轮</small>
          </div>

          <label class="field">
            <span class="field-label">目标岗位</span>
            <select v-model="config.job_role" :disabled="starting">
              <option v-for="role in JOB_ROLES" :key="role" :value="role">{{ role }}</option>
            </select>
          </label>

          <label v-if="config.job_role === '自定义岗位'" class="field">
            <span class="field-label">自定义岗位名称 <small>最多 100 字</small></span>
            <input
              v-model="config.custom_role"
              type="text"
              maxlength="100"
              placeholder="例如：大模型应用开发工程师"
              :disabled="starting"
            />
          </label>

          <label class="field">
            <span class="field-label">面试难度</span>
            <select v-model="config.difficulty" :disabled="starting">
              <option v-for="level in DIFFICULTIES" :key="level" :value="level">
                {{ level }}
              </option>
            </select>
          </label>

          <label class="field">
            <span class="field-label">追问策略 <small>每题最多追问 3 次</small></span>
            <select v-model="config.follow_up_mode" :disabled="starting">
              <option
                v-for="item in FOLLOW_UP_MODES"
                :key="item.value"
                :value="item.value"
              >
                {{ item.label }}
              </option>
            </select>
          </label>

          <label class="field">
            <span class="field-label">面试官音色 <small>Edge-TTS</small></span>
            <select v-model="config.voice" :disabled="starting">
              <option v-for="voice in voices" :key="voice.short_name" :value="voice.short_name">
                {{ voice.label }}
              </option>
            </select>
          </label>

          <label class="field">
            <span class="field-label">画面采样频率 <small>影响流畅度与精度</small></span>
            <select v-model.number="config.analysis_fps" :disabled="starting">
              <option v-for="item in FPS_OPTIONS" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
          </label>

          <label class="checkbox-row">
            <input v-model="config.auto_tts" type="checkbox" :disabled="starting" />
            <span>自动朗读面试官问题</span>
            <small>可面试中关闭</small>
          </label>

          <button
            class="button button-primary button-block start-button"
            type="button"
            :disabled="starting"
            @click="startInterview"
          >
            <LoaderCircle v-if="starting" class="spin" :size="18" />
            <Play v-else :size="18" />
            {{ starting ? '正在打开摄像头' : '开始面试并打开摄像头' }}
          </button>
        </section>

        <section class="panel resume-panel">
          <div class="panel-title">
            <span><FileText :size="17" /> 简历来源</span>
            <small>
              {{ selectedResume ? '已载入上次保存的简历' : 'PDF / DOC / DOCX · 最大 8 MB' }}
            </small>
          </div>

          <input
            ref="resumeInput"
            class="visually-hidden"
            type="file"
            accept=".pdf,.doc,.docx"
            @change="uploadResume"
          />

          <div v-if="selectedResume" class="resume-current">
            <div class="resume-file-head">
              <span class="resume-file-icon"><FileCheck2 :size="20" /></span>
              <div>
                <strong>{{ selectedResume.filename }}</strong>
                <small>
                  {{ selectedResume.extension.toUpperCase() }} ·
                  {{ (selectedResume.size / 1024).toFixed(1) }} KB ·
                  已提取 {{ selectedResume.extracted_chars }} 字
                </small>
              </div>
            </div>
            <p class="resume-preview">{{ selectedResume.preview }}</p>
            <div class="resume-actions">
              <button
                class="button button-secondary button-small"
                type="button"
                :disabled="starting || uploadingResume"
                @click="chooseResume"
              >
                <LoaderCircle v-if="uploadingResume" class="spin" :size="15" />
                <RefreshCw v-else :size="15" />
                {{ uploadingResume ? '正在解析' : '重新上传' }}
              </button>
              <button
                class="button button-ghost button-small resume-remove"
                type="button"
                :disabled="starting || uploadingResume"
                @click="removeResume"
              >
                <Trash2 :size="15" />
                移除
              </button>
            </div>
          </div>

          <button
            v-else
            class="resume-upload"
            type="button"
            :disabled="starting || uploadingResume"
            @click="chooseResume"
          >
            <LoaderCircle v-if="uploadingResume" class="spin" :size="26" />
            <Upload v-else :size="26" />
            <strong>{{ uploadingResume ? '正在解析简历' : '上传一份简历' }}</strong>
            <span>系统会提取项目、职责、技术栈和结果，作为本轮面试依据。</span>
          </button>

          <div class="alert alert-info resume-note">
            <CircleDot :size="15" />
            <span>
              系统仅保存解析后的文本和文件名，不保留原始文件；下一次进入页面会自动恢复上次简历。
            </span>
          </div>
        </section>

        <section class="panel setup-brief">
          <div class="panel-title">
            <span><Gauge :size="17" /> 本轮评估方式</span>
          </div>
          <div class="brief-formula">
            <strong>最终得分</strong>
            <p>问答得分 × 80% +（表情得分 × 50% + 注意力得分 × 50%）× 20%</p>
          </div>
          <ul class="brief-list">
            <li>
              <span>问答得分</span>
              <strong>80%</strong>
              <small>DeepSeek 对每轮回答的逻辑、专业度、岗位匹配度综合评分</small>
            </li>
            <li>
              <span>表情得分</span>
              <strong>10%</strong>
              <small>微笑、专注、紧张等面部状态的变化与稳定性</small>
            </li>
            <li>
              <span>注意力得分</span>
              <strong>10%</strong>
              <small>视线居中比例、头部姿态、离帧次数与分散时长</small>
            </li>
          </ul>
          <div class="alert alert-info brief-note">
            <CircleDot :size="16" />
            <span>
              有效视频样本不足时，最终得分保持为空，系统不会重新分配权重，避免出现虚高成绩。
            </span>
          </div>
          <div class="alert alert-warning brief-note">
            <TriangleAlert :size="16" />
            <span>
              仅保存采样指标与状态变化次数，原始视频帧、人脸照片与录音都不会写入磁盘。
            </span>
          </div>
        </section>
      </div>
    </section>

    <!-- 进行中：面试工作台 -->
    <section v-else class="workspace-view">
      <header class="session-bar">
        <div class="session-ident">
          <span class="live-badge" :class="{ idle: !active }">
            <i />
            {{ active ? '面试进行中' : '面试已结束' }}
          </span>
          <div>
            <strong>{{ session.job_role }}</strong>
            <small>
              {{ session.difficulty }} · 画面采样 {{ session.analysis_fps }} 帧/秒
            </small>
          </div>
        </div>

        <div class="session-stats">
          <span class="question-stat">
            <CircleDot :size="15" />
            <strong>第 {{ currentQuestionNumber }}/{{ questionLimit }} 题</strong>
          </span>
          <span>
            <Layers3 :size="15" />
            <strong>{{ session.current_scene_label || '准备首题' }}</strong>
          </span>
          <span>
            <ArrowRight :size="15" />
            <strong>追问 {{ followUpSummary }}</strong>
          </span>
          <span>
            <Timer :size="15" />
            <strong class="data-value">{{ formatDuration(elapsedSeconds) }}</strong>
          </span>
          <span>
            <Video :size="15" />
            <strong>{{ cameraHealthy ? '摄像头在线' : '摄像头恢复中' }}</strong>
          </span>
          <span>
            <Activity :size="15" />
            <strong>
              {{
                visionStatus.processing
                  ? '视觉分析中'
                  : visionStatus.pending
                    ? '已接收最新帧'
                    : '视觉链路就绪'
              }}
            </strong>
          </span>
        </div>

        <div class="session-actions">
          <button
            class="button button-secondary button-small"
            type="button"
            :title="autoSpeak ? '关闭自动朗读' : '开启自动朗读'"
            @click="toggleAutoSpeak"
          >
            <Volume2 v-if="autoSpeak" :size="15" />
            <VolumeX v-else :size="15" />
            {{ autoSpeak ? '自动朗读开' : '自动朗读关' }}
          </button>
          <button
            class="button button-secondary button-small"
            type="button"
            :disabled="starting"
            @click="abandonInterview"
          >
            <RotateCcw :size="15" />
            重新开始
          </button>
          <button
            class="button button-primary button-small"
            type="button"
            :disabled="!active"
            @click="finishOpen = true"
          >
            <LogOut :size="15" />
            结束面试
          </button>
        </div>
      </header>

      <div
        class="round-progress"
        :aria-label="`本轮面试进度 ${currentQuestionNumber}/${questionLimit}`"
      >
        <span :style="{ width: `${questionProgress}%` }" />
      </div>

      <div v-if="error || notice" class="session-alerts">
        <div v-if="error" class="alert alert-danger">
          <AlertCircle :size="16" />
          <span>{{ error }}</span>
          <button type="button" class="alert-close" @click="error = ''">知道了</button>
        </div>
        <div v-if="notice" class="alert alert-warning">
          <TriangleAlert :size="16" />
          <span>{{ notice }}</span>
          <button type="button" class="alert-close" @click="notice = ''">知道了</button>
        </div>
      </div>

      <div class="workspace">
        <div class="column column-left">
          <div class="video-region">
            <CameraPanel
              :stream="camera.stream.value"
              :status="camera.status.value"
              :error="camera.error.value"
              :device-label="camera.deviceLabel.value"
              :face-detected="faceDetected"
              :expression="expressionLabel"
              :attention-state="metrics.attention_state || 'unknown'"
              :vision-status="visionStatus"
              :target-fps="session.analysis_fps"
              @retry="camera.retry"
              @video-ready="camera.setVideoElement"
            />
          </div>
        </div>

        <div class="column column-center">
          <ConversationPanel
            :messages="messages"
            :opening="opening"
            :sending="sending"
            :transcribing="transcribing"
            :tts-busy-id="ttsBusyId"
            :disabled="!active"
            @send="sendAnswer"
            @audio="handleAudio"
            @replay="speak"
          />
        </div>

        <div class="column column-right">
          <LiveMetricsPanel :metrics="metrics" :vision="visionStatus" />
        </div>
      </div>
    </section>

    <FinishDialog
      :open="finishOpen"
      :loading="finishing"
      :answer-count="answerCount"
      @close="finishOpen = false"
      @confirm="finishInterview"
    />

    <QuestionLimitDialog
      :open="checkpointOpen"
      :loading="continuing || finishing"
      :question-count="currentQuestionNumber"
      :question-limit="questionLimit"
      @continue="continueInterview"
      @evaluate="finishInterview"
    />
  </div>
</template>

<style scoped>
/* 配置阶段 -------------------------------------------------------------- */

.setup-head {
  margin-bottom: 18px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.setup-head h1 {
  margin-top: 12px;
  font-size: 27px;
}

.setup-head p {
  margin-top: 8px;
  max-width: 720px;
  color: var(--muted-strong);
  font-size: 13px;
  line-height: 1.7;
}

.setup-account {
  flex: 0 0 auto;
  padding: 10px 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  text-align: right;
}

.setup-account span,
.setup-account small {
  display: block;
}

.setup-account span {
  color: var(--ink);
  font-size: 13px;
  font-weight: 800;
}

.setup-account small {
  margin-top: 3px;
  color: var(--muted);
  font-size: 11px;
}

.setup-grid {
  display: grid;
  grid-template-columns: minmax(300px, 1.05fr) minmax(280px, .95fr) minmax(280px, .9fr);
  gap: 16px;
  align-items: start;
}

.resume-panel {
  min-height: 100%;
}

.resume-upload {
  width: 100%;
  min-height: 210px;
  padding: 24px 18px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 9px;
  border: 1px dashed #9bbfdf;
  border-radius: var(--radius);
  color: var(--teal-dark);
  background: linear-gradient(160deg, #f7fbff 0%, #edf6ff 100%);
  cursor: pointer;
  transition: border-color .16s ease, background .16s ease, transform .1s ease;
}

.resume-upload:hover:not(:disabled) {
  border-color: var(--teal);
  background: var(--teal-soft);
  transform: translateY(-1px);
}

.resume-upload:disabled {
  opacity: .6;
  cursor: not-allowed;
}

.resume-upload strong {
  color: var(--ink);
  font-size: 14px;
}

.resume-upload span {
  max-width: 260px;
  color: var(--muted-strong);
  font-size: 11px;
  line-height: 1.65;
}

.resume-current {
  padding: 14px;
  border: 1px solid #bdd7ed;
  border-radius: var(--radius);
  background: #f7fbff;
}

.resume-file-head {
  display: flex;
  align-items: center;
  gap: 10px;
}

.resume-file-icon {
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 7px;
  color: var(--teal-dark);
  background: var(--teal-soft);
}

.resume-file-head > div {
  min-width: 0;
}

.resume-file-head strong,
.resume-file-head small {
  display: block;
}

.resume-file-head strong {
  overflow: hidden;
  color: var(--ink);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.resume-file-head small {
  margin-top: 3px;
  color: var(--muted);
  font-size: 10px;
}

.resume-preview {
  margin-top: 12px;
  padding-top: 11px;
  border-top: 1px solid var(--line);
  color: var(--muted-strong);
  font-size: 11px;
  line-height: 1.7;
}

.resume-actions {
  margin-top: 13px;
  display: flex;
  align-items: center;
  gap: 7px;
}

.resume-remove {
  margin-left: auto;
  color: var(--danger);
}

.resume-remove:hover:not(:disabled) {
  color: var(--danger);
  background: var(--danger-soft);
}

.resume-note {
  margin-top: 12px;
}

.start-button {
  min-height: 46px;
  margin-top: 18px;
  font-size: 14px;
}

.brief-formula {
  padding: 14px;
  border: 1px solid #bfe0da;
  border-radius: var(--radius);
  background: var(--teal-soft);
}

.brief-formula strong {
  color: var(--teal-dark);
  font-size: 12px;
}

.brief-formula p {
  margin-top: 7px;
  color: var(--teal-dark);
  font-family: var(--font-data);
  font-size: 12px;
  line-height: 1.7;
}

.brief-list {
  margin: 14px 0 0;
  padding: 0;
  list-style: none;
}

.brief-list li {
  padding: 12px 0;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 3px 12px;
  border-bottom: 1px solid var(--line-soft);
}

.brief-list li:last-child {
  border-bottom: 0;
}

.brief-list span {
  color: var(--ink);
  font-size: 13px;
  font-weight: 800;
}

.brief-list strong {
  color: var(--coral);
  font-family: var(--font-data);
  font-size: 14px;
}

.brief-list small {
  grid-column: 1 / -1;
  color: var(--muted);
  font-size: 11px;
  line-height: 1.6;
}

.brief-note {
  margin-top: 12px;
}

/* 面试工作台 ------------------------------------------------------------ */

.session-bar {
  min-height: 64px;
  margin-bottom: 14px;
  padding: 10px 14px;
  display: flex;
  align-items: center;
  gap: 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-xs);
}

.round-progress {
  height: 3px;
  margin: -9px 2px 11px;
  overflow: hidden;
  border-radius: 3px;
  background: #dceaf6;
}

.round-progress span {
  height: 100%;
  display: block;
  border-radius: inherit;
  background: linear-gradient(90deg, #4b8bc7, #7eb1df);
  transition: width .35s ease;
}

.session-ident {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.session-ident strong,
.session-ident small {
  display: block;
}

.session-ident strong {
  color: var(--ink);
  font-size: 15px;
}

.session-ident small {
  margin-top: 3px;
  color: var(--muted);
  font-size: 11px;
}

.live-badge {
  min-height: 26px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 5px;
  color: #fff;
  background: var(--coral);
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}

.live-badge i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #fff;
  animation: blink 1.6s ease-in-out infinite;
}

.live-badge.idle {
  color: var(--muted-strong);
  background: var(--surface-soft);
}

.live-badge.idle i {
  background: var(--muted);
  animation: none;
}

@keyframes blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: .3;
  }
}

.session-stats {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.session-stats span {
  min-height: 34px;
  padding: 0 11px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border: 1px solid var(--line-soft);
  border-radius: 6px;
  color: var(--muted);
  background: var(--surface-soft);
  font-size: 11px;
  white-space: nowrap;
}

.session-stats strong {
  color: var(--ink);
  font-size: 12px;
}

.session-actions {
  display: flex;
  align-items: center;
  gap: 7px;
}

.session-alerts {
  margin-bottom: 14px;
  display: grid;
  gap: 8px;
}

.session-alerts .alert {
  align-items: center;
}

.alert-close {
  margin-left: auto;
  padding: 3px 8px;
  border: 0;
  border-radius: 5px;
  color: inherit;
  background: rgb(255 255 255 / 60%);
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}

.workspace-view {
  min-height: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.workspace {
  min-height: 0;
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.62fr) minmax(336px, .88fr);
  grid-template-rows: minmax(260px, 46%) minmax(250px, 54%);
  gap: 14px;
  align-items: stretch;
}

.column {
  min-width: 0;
  min-height: 0;
}

.column-right {
  display: grid;
  gap: 14px;
}

.column-left {
  grid-column: 1;
  grid-row: 1;
  min-height: 0;
  display: flex;
}

.column-right {
  grid-column: 2;
  grid-row: 1 / span 2;
  align-content: start;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.column-center {
  grid-column: 1;
  grid-row: 2;
  min-height: 0;
  display: flex;
}

.video-region {
  min-height: 0;
  display: flex;
}

.video-region :deep(.camera-panel) {
  width: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.video-region :deep(.camera-stage) {
  min-height: 0;
  flex: 1;
  aspect-ratio: auto;
}

@media (max-width: 1380px) {
  .workspace {
    grid-template-columns: minmax(0, 1.52fr) minmax(316px, .88fr);
  }
}

@media (max-width: 1180px) {
  .setup-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .resume-panel {
    grid-row: span 2;
  }

  .workspace {
    grid-template-columns: minmax(0, 1fr) minmax(292px, 320px);
  }
}

@media (max-width: 900px) {
  .setup-head {
    flex-direction: column;
    align-items: stretch;
  }

  .setup-account {
    text-align: left;
  }

  .setup-grid {
    grid-template-columns: 1fr;
  }

  .resume-panel {
    grid-row: auto;
  }

  .session-bar {
    flex-wrap: wrap;
  }

  .session-stats {
    order: 3;
    width: 100%;
    margin-left: 0;
    overflow-x: auto;
    padding-bottom: 2px;
  }

  .session-actions {
    margin-left: auto;
    flex-wrap: wrap;
  }

  .workspace {
    grid-template-columns: 1fr;
    grid-template-rows: auto;
    gap: 14px;
  }

  .video-region {
    width: 100%;
    min-height: 280px;
    grid-column: 1;
    grid-row: 1;
    order: 1;
  }

  .column-center {
    grid-column: 1;
    grid-row: 3;
    order: 3;
    min-height: 520px;
  }

  .column-right {
    grid-column: 1;
    grid-row: 2;
    order: 2;
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
    overflow: visible;
  }

  .column-right :deep(.metrics-panel) {
    grid-column: 1 / -1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
  }
}

@media (max-width: 640px) {
  .setup-head h1 {
    font-size: 22px;
  }

  .session-ident small {
    display: none;
  }

  .session-actions .button {
    padding: 0 10px;
  }

  .video-region {
    min-height: 230px;
  }

  .column-right :deep(.metrics-panel) {
    grid-template-columns: 1fr;
  }
}
</style>
