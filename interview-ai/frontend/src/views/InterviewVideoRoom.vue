<template>
  <div class="interview-room">
    <!-- 顶部：标题 + 状态标签 -->
    <header class="room-header">
      <h2>AI 模拟面试</h2>
      <div class="status-bar">
        <el-tag v-if="phase !== 'idle'" :type="phaseTagType">{{ phaseText }}</el-tag>
        <el-tag :type="socketTag">服务：{{ socketText }}</el-tag>
        <el-tag :type="networkTag">网络：{{ networkText }}</el-tag>
      </div>
    </header>

    <!-- AI 面试官提问区 -->
    <div class="ai-interviewer">
      <div class="ai-avatar">AI</div>
      <div class="ai-question">
        <span class="ai-label">AI 面试官 · 第 {{ questionNumber }} 题</span>
        <p>{{ aiQuestion }}</p>
      </div>
    </div>

    <!-- 候选人画面 + 音量表 -->
    <div class="media-row">
      <div class="video-card">
        <video ref="localVideoRef" autoplay muted playsinline></video>
        <div class="video-title">我的画面</div>
      </div>
      <div class="volume-panel">
        <div class="volume-row">
          <el-icon class="vol-icon"><Microphone /></el-icon>
          <div class="volume-bar">
            <div class="volume-fill" :style="{ width: volumePercent + '%' }"></div>
          </div>
        </div>
        <div class="volume-row">
          <svg class="vol-icon-svg" viewBox="0 0 24 24" width="18" height="18" fill="currentColor">
            <path d="M4 9v6h4l5 5V4L8 9H4z" />
            <path d="M16.5 12a4.5 4.5 0 0 0-2.4-3.9v7.8a4.5 4.5 0 0 0 2.4-3.9z" fill="none" stroke="currentColor" stroke-width="2" />
            <path d="M14.5 5.3a8 8 0 0 1 0 13.4" fill="none" stroke="currentColor" stroke-width="2" />
          </svg>
          <div class="volume-bar">
            <div class="volume-fill play-fill" :style="{ width: playVolumePercent + '%' }"></div>
          </div>
        </div>
      </div>
    </div>

    <!-- 面部表情与注意力指标 -->
    <el-card class="vision-panel" shadow="never">
      <div class="vision-header">
        <div>
          <div class="vision-title">面部表情与注意力</div>
          <div class="vision-subtitle">{{ visionMessage }}</div>
        </div>
        <el-tag size="small" :type="visionStatusTag">{{ visionStatusText }}</el-tag>
      </div>
      <VisionMetricsPanel :metrics="visionMetrics" :vision="visionWorker" />
    </el-card>

    <!-- 实时字幕 -->
    <div class="live-subtitle">
      <div class="subtitle-label">实时字幕 <el-tag v-if="recognizedCount" size="small" type="success">{{ recognizedCount }} 句</el-tag></div>
      <div class="subtitle-body">
        <template v-if="subtitle">
          {{ subtitle }}<span class="subtitle-partial">{{ partial || (pendingAsr ? `（正在识别剩余语音…${pendingAsr} 段）` : '') }}</span>
        </template>
        <span v-else class="subtitle-placeholder">
          {{ phase === 'listening' ? '请开始作答，说话时将实时显示文字' : (pendingAsr ? `正在识别语音…${pendingAsr} 段` : '等待面试开始…') }}
        </span>
      </div>
    </div>

    <!-- 文字作答：安静环境、不便说话或语音识别不可用时可直接打字。
         提交时与语音识别文本合并，按题目归档，保证报告可追溯到输入。 -->
    <div class="live-subtitle" style="margin-top: 12px">
      <div class="subtitle-label">文字作答（可选，与语音识别结果一并提交）</div>
      <el-input
        v-model="typedAnswers[questionNumber]"
        type="textarea"
        :rows="4"
        :disabled="phase !== 'listening'"
        placeholder="安静环境或不方便说话时，可在此直接输入本题答案；提交时会与语音识别文本合并。"
      />
    </div>

    <!-- 控制栏 -->
    <div class="control-bar">
      <el-popover width="320" trigger="click" title="设备选择">
        <template #reference>
          <el-button><el-icon><Setting /></el-icon>设备</el-button>
        </template>
        <div class="device-select">
          <div class="device-row">
            <span class="device-label">摄像头</span>
            <el-select v-model="selectedDevice.camera" size="small" @change="applyCamera" placeholder="选择摄像头">
              <el-option v-for="d in devices.cameras" :key="d.deviceId" :label="d.label || d.deviceId" :value="d.deviceId" />
            </el-select>
          </div>
          <div class="device-row">
            <span class="device-label">麦克风</span>
            <el-select v-model="selectedDevice.microphone" size="small" @change="applyMicrophone" placeholder="选择麦克风">
              <el-option v-for="d in devices.microphones" :key="d.deviceId" :label="d.label || d.deviceId" :value="d.deviceId" />
            </el-select>
          </div>
          <div class="device-row">
            <span class="device-label">音响</span>
            <el-select v-model="selectedDevice.speaker" size="small" @change="applySpeaker" placeholder="选择音响">
              <el-option v-for="d in devices.speakers" :key="d.deviceId" :label="d.label || d.deviceId" :value="d.deviceId" />
            </el-select>
          </div>
        </div>
      </el-popover>

      <el-button @click="toggleMicrophone">{{ status.microphone ? '静音' : '取消静音' }}</el-button>
      <el-button @click="toggleCamera">{{ status.camera ? '关闭摄像头' : '开启摄像头' }}</el-button>

      <el-button v-if="phase === 'idle'" type="primary" @click="startInterview">开始面试</el-button>
      <el-button v-if="phase === 'listening'" type="warning" @click="answerDone">回答完毕</el-button>
      <el-button v-if="phase === 'submitted'" type="primary" @click="nextQuestion">{{ isLast ? '结束面试' : '下一题' }}</el-button>
      <el-button v-if="phase === 'end'" type="primary" @click="endInterview">结束面试</el-button>

      <el-button @click="reconnect">重新进入</el-button>
      <el-button type="danger" @click="handleLeave">离开</el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useInterviewRoom } from '../composables/useInterviewRoom'
import { useVisionCapture } from '../composables/useVisionCapture'
import VisionMetricsPanel from '../components/VisionMetricsPanel.vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
import { Setting, Microphone } from '@element-plus/icons-vue'
import { getRecordQuestions, submitInterview } from '../api/interview'

const route = useRoute()
const router = useRouter()
const recordId = route.params.recordId

const {
  localVideoRef, status, localStream, connect, reconnect,
  toggleMicrophone, toggleCamera, leave,
  devices, selectedDevice, enumDevices,
  applyCamera, applyMicrophone, applySpeaker
} = useInterviewRoom(recordId)

const handleLeave = () => { leave(); router.push('/records') }

const {
  metrics: visionMetrics,
  vision: visionWorker,
  status: visionStatus,
  message: visionMessage,
  start: startVisionCapture,
  stop: stopVisionCapture
} = useVisionCapture({
  recordId: Number(recordId),
  getVideoElement: () => localVideoRef.value,
  getQuestionIndex: () => questionNumber.value
})

const visionStatusText = computed(() => ({
  idle: '准备中',
  loading: '连接中',
  calibrating: '校准中',
  active: '分析中',
  degraded: '暂时不可用',
  unavailable: '不可用'
}[visionStatus.value] || '准备中'))
const visionStatusTag = computed(() => ({
  active: 'success',
  calibrating: 'warning',
  loading: 'info',
  degraded: 'warning',
  unavailable: 'info'
}[visionStatus.value] || 'info'))

// ===== 交互状态 =====
const phase = ref('idle')           // idle/asking/listening/submitted/end
const aiQuestion = ref('（点击【开始面试】开始）')
const subtitle = ref('')
const partial = ref('')
const history = ref([])
const volume = ref(0)
const playVolume = ref(0)
const recognizedCount = ref(0)
const pendingAsr = ref(0)          // 尚未返回的语音识别段数
const answersByQuestion = ref({})  // 按题目归档的识别文本
// 按题目归档的文字作答（键为 1-based 题号）；教室等不便说话的场合使用
const typedAnswers = reactive({})
const speaking = ref(false)
const questionIndex = ref(0)
const questions = ref([])
const isLast = computed(() => questionIndex.value >= questions.value.length - 1)
const questionNumber = computed(() => questionIndex.value + 1)

// 阶段文案与标签类型
const phaseText = computed(() => ({
  idle: '准备',
  asking: '朗读题目中…',
  listening: '作答中…',
  submitted: '已提交',
  end: '已结束'
}[phase.value] || phase.value))
const phaseTagType = computed(() => ({
  asking: 'warning',
  listening: 'success',
  submitted: 'info'
}[phase.value] || 'info'))

// 音量百分比与说话状态
const volumePercent = computed(() => Math.min(100, Math.round(volume.value * 100)))
const playVolumePercent = computed(() => Math.min(100, Math.round(playVolume.value * 100)))

// 静音分段参数：说话后停顿满 SILENCE_MS 即切段并立即送识别；
// MIN_SPEAK_MS 防止把一句话切碎，MIN_SEGMENT_BYTES 过滤空片段
const SILENCE_THRESHOLD = 0.008
const SILENCE_MS = 600
const MIN_SPEAK_MS = 300
const MIN_SEGMENT_BYTES = 512
// Whisper（CPU）单次转写可能超过 30s 默认超时，放宽到 120s
const TRANSCRIBE_TIMEOUT_MS = 120000
let spoke = false     // 是否已开始说话，未说话前不切段
let audioCtx = null
let frameTimer = null
let analyser = null
let sourceNode = null
let recorder = null
let chunks = []
let silenceMs = 0
let speakMs = 0
let lastTickAt = 0
let rafId = null

// ===== 开始面试 =====
const startInterview = async () => {
  ensureAudioCtx() // 在用户点击手势内解锁共用 AudioContext
  if (!localStream.value) await connect()
  const res = await getRecordQuestions(Number(recordId))
  questions.value = res.data || []
  if (!questions.value.length) { ElMessage.warning('暂无题目'); return }
  showQuestion(0)
}

const showQuestion = (idx) => {
  questionIndex.value = idx
  const q = questions.value[idx]
  if (!q) { phase.value = 'end'; return }
  aiQuestion.value = q.content
  history.value = []
  subtitle.value = ''
  phase.value = 'asking'
  // AI/试卷架构下统一先朗读题目（若服务不可用则直接开始答题）
  playTts(q.content)
}

/** 创建并解锁共用的 AudioContext（需在用户手势内调用），供麦克风分析与 TTS 播放。 */
const ensureAudioCtx = () => {
  if (!audioCtx) {
    audioCtx = new (window.AudioContext || window.webkitAudioContext)()
  }
  audioCtx.resume()
}

/** 用 Web Audio 播放题目 TTS，并通过 Analyser 驱动"题目朗读音量"条。 */
const playTts = async (text) => {
  try {
    ensureAudioCtx()
    const blob = await request.post('/ai/tts', { text }, { responseType: 'blob' })
    const arrayBuffer = await blob.arrayBuffer()
    const audioBuffer = await audioCtx.decodeAudioData(arrayBuffer)
    const source = audioCtx.createBufferSource()
    source.buffer = audioBuffer
    const analyser = audioCtx.createAnalyser()
    analyser.fftSize = 256
    // source -> analyser -> 扬声器：既能出声，又能读出音量
    source.connect(analyser)
    analyser.connect(audioCtx.destination)
    source.onended = () => { playVolume.value = 0; startAnswering() }
    playVolume.value = 0
    source.start(0)
    playLoop(analyser)
  } catch (e) {
    // TTS 服务不可用：直接进入答题
    setTimeout(startAnswering, 800)
  }
}
    /** 读播放 Analyser 的 RMS 驱动音量条，在题目朗读结束后停止。 */
/** 答题期间启动视觉指标采集。 */
const startFrameCapture = () => {
  stopFrameCapture()
  startVisionCapture()
}

const stopFrameCapture = () => {
  stopVisionCapture()
}

const playLoop = (analyser) => {
  if (phase.value !== 'asking') return
  const buf = new Float32Array(analyser.fftSize)
  analyser.getFloatTimeDomainData(buf)
  let sum = 0
  for (const v of buf) sum += v * v
  playVolume.value = Math.sqrt(sum / buf.length)
  requestAnimationFrame(() => playLoop(analyser))
}

// ===== 答题（自动录制 + 音量 + 静音分段） =====
const startAnswering = () => {
  phase.value = 'listening'
  lastTickAt = 0
  setupAnalyser()
  startNewRecorder()
  startFrameCapture() // 启动视觉指标采集与展示
  tick()
}

const setupAnalyser = () => {
  const track = localStream.value?.getAudioTracks()[0]
  if (!track) return
  ensureAudioCtx() // 复用已在手势中解锁的 AudioContext
  sourceNode = audioCtx.createMediaStreamSource(new MediaStream([track]))
  analyser = audioCtx.createAnalyser()
  analyser.fftSize = 256
  sourceNode.connect(analyser)
}

const startNewRecorder = () => {
  const mime = 'audio/webm;codecs=opus'
  const track = localStream.value?.getAudioTracks()[0]
  if (!track) return
  // 录制开始时固定归属题目：识别结果即使迟到，也只归档到本题，不串到下一题
  const segmentQuestion = questionNumber.value
  recorder = new MediaRecorder(new MediaStream([track]), { mimeType: mime, audioBitsPerSecond: 48000 })
  chunks = []
  recorder.ondataavailable = (e) => { if (e.data && e.data.size) chunks.push(e.data) }
  recorder.onstop = async () => {
    const blob = chunks.length ? new Blob(chunks, { type: mime }) : null
    // 立即开始录下一段，避免 ASR 转写期间漏录候选人的后续发言
    if (phase.value === 'listening') startNewRecorder()
    if (blob) {
      pendingAsr.value += 1
      try {
        await finalizeSegment(blob, segmentQuestion)
      } finally {
        pendingAsr.value -= 1
      }
    }
  }
  spoke = false
  speakMs = 0
  // timeslice=1000：每 1 秒触发一次 dataavailable，保证 chunks 始终有数据
  recorder.start(1000)
}

const tick = () => {
  if (phase.value !== 'listening') return
  if (!analyser) return
  // 按真实经过时间累计说话/停顿时长，避免帧率波动导致切段迟滞
  const now = performance.now()
  const dt = Math.max(0, Math.min(250, now - (lastTickAt || now)))
  lastTickAt = now
  const buf = new Float32Array(analyser.fftSize)
  analyser.getFloatTimeDomainData(buf)
  let sum = 0
  for (const v of buf) sum += v * v
  const rms = Math.sqrt(sum / buf.length)
  volume.value = rms
  speaking.value = rms >= SILENCE_THRESHOLD
  if (rms < SILENCE_THRESHOLD) {
    if (spoke) {
      silenceMs += dt
      // 连续说话超过最短时长后，停顿满 SILENCE_MS 立即切段送识别
      if (silenceMs >= SILENCE_MS && speakMs >= MIN_SPEAK_MS) {
        console.log('[split] silenceMs=', silenceMs, 'speakMs=', speakMs, '-> splitSegment')
        splitSegment()
      }
    }
  } else {
    spoke = true
    silenceMs = 0
    speakMs += dt
  }
  rafId = requestAnimationFrame(tick)
}

const splitSegment = () => {
  silenceMs = 0
  if (recorder && recorder.state === 'recording') {
    recorder.stop() // onstop 里转写并继续
  }
}

const finalizeSegment = async (blob, qNumber) => {
  // 空白或过短片段不送转写，避免异常
  if (!blob || blob.size < MIN_SEGMENT_BYTES) { console.log('[finalize] skip, size=', blob && blob.size); return }
  console.log('[finalize] transcribe size=', blob.size)
  try {
    const seg = await request.post('/interview-audio/segments', {
      recordId: Number(recordId),
      questionIndex: qNumber,
      mimeType: blob.type || 'audio/webm;codecs=opus',
      sampleRate: 48000,
      channels: 1
    }, { silent: true })
    const text = await transcribeAudio(seg.data.id, blob, qNumber)
    if (!text) return
    // 按题目归档：无论识别多晚返回都不丢；仍在当前题目时同步刷新字幕
    const list = answersByQuestion.value[qNumber] || (answersByQuestion.value[qNumber] = [])
    list.push(text)
    recognizedCount.value += 1
    if (qNumber === questionNumber.value) {
      history.value.push(text)
      subtitle.value = history.value.join('')
      partial.value = ''
    }
  } catch (e) {
    // 失败不阻断录音，在字幕区给出轻提示，避免全局“网络异常”弹窗打断作答
    console.warn('[finalize] 语音识别失败', e)
    if (qNumber === questionNumber.value) {
      partial.value = '（语音识别未成功，本段已跳过）'
    }
  }
}

const transcribeAudio = async (segmentId, blob, qNumber) => {
  // 直接上传原始 webm/opus：服务端 Whisper（PyAV/ffmpeg）原生解码，
  // 避免浏览器端转 WAV 造成的时长截断、音质损失与 AudioContext 泄漏
  const formData = new FormData()
  formData.append('audio', blob, `s-${Date.now()}.webm`)
  const res = await request.post(
    `/transcribe/${segmentId}?recordId=${Number(recordId)}&questionIndex=${qNumber ?? questionNumber.value}`,
    formData,
    // 长转写可能超过 30s 默认超时：放宽到 120s，并关闭全局错误弹窗
    { silent: true, timeout: TRANSCRIBE_TIMEOUT_MS }
  )
  const t = res.data?.text || ''
  console.log('[transcribe] textLen=', t.length, 'text=', t)
  return t
}

// ===== 回答完毕 / 下一题 / 结束 =====
const answerDone = async () => {
  if (recorder && recorder.state === 'recording') recorder.stop()
  stopFrameCapture()
  phase.value = 'submitted'
}

const nextQuestion = async () => {
  if (isLast.value) { endInterview(); return }
  showQuestion(questionIndex.value + 1)
}

/** 等待残余语音识别返回，超时即放行，避免收尾卡死。 */
const waitForPendingAsr = (timeoutMs) => new Promise((resolve) => {
  const startedAt = Date.now()
  const timer = setInterval(() => {
    if (pendingAsr.value <= 0 || Date.now() - startedAt >= timeoutMs) {
      clearInterval(timer)
      resolve()
    }
  }, 300)
})

const endInterview = async () => {
  try {
    // 提交前等待残余语音识别完成（最长 60s），避免“回答完毕”后的迟到文本丢失
    if (pendingAsr.value > 0) {
      partial.value = `（正在收尾识别剩余 ${pendingAsr.value} 段语音…）`
      await waitForPendingAsr(60000)
    }
    // 按题目汇总全部识别文本（含迟到结果），并合并文字作答后提交。
    // 统一成与答题面试一致的 answers:[{questionId,content,answer}] 结构，
    // 后端据此把真实的“题面 + 作答”送评，报告才能与输入对应。
    const answers = questions.value.map((q, i) => {
      const key = i + 1
      const asrText = (answersByQuestion.value[key] || []).join('')
      const typedText = (typedAnswers[key] || '').trim()
      return {
        questionId: q.id,
        content: q.content,
        answer: [asrText, typedText].filter(Boolean).join('\n')
      }
    })
    await submitInterview(Number(recordId), {
      answerData: JSON.stringify({ answers })
    })
    ElMessage.success('面试已提交')
    router.push('/records')
  } catch (e) {
    ElMessage.error('提交失败')
  }
}

// 服务/网络状态标签
const socketTag = computed(() => ({ open: 'success', connecting: 'warning', closed: 'info', error: 'danger' }[status.socket] || 'info'))
const socketText = computed(() => ({ open: '已连接', connecting: '连接中', closed: '已断开', error: '异常' }[status.socket] || status.socket))
const networkTag = computed(() => ({ good: 'success', normal: 'warning', poor: 'danger', failed: 'danger' }[status.network] || 'info'))
const networkText = computed(() => ({ good: '良好', normal: '一般', poor: '较差', failed: '连接失败', reconnecting: '重连中' }[status.network] || '检测中'))

const cleanup = () => {
  stopFrameCapture()
  if (rafId) cancelAnimationFrame(rafId)
  if (recorder && recorder.state !== 'inactive') recorder.stop()
  if (audioCtx) audioCtx.close()
}

onMounted(async () => {
  await connect()
  await enumDevices()
})

onBeforeUnmount(cleanup)
</script>

<style scoped>
.interview-room { max-width: 960px; margin: 0 auto; padding: 16px; }
.room-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.status-bar { display: flex; gap: 8px; }
.ai-interviewer { display: flex; gap: 14px; padding: 16px; border: 1px solid #ebeef5; border-radius: 8px; margin-bottom: 16px; background: #fafafa; }
.ai-avatar { width: 44px; height: 44px; border-radius: 50%; background: #409eff; color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; }
.ai-question { flex: 1; }
.ai-label { font-size: 12px; color: #909399; }
.ai-question p { margin: 6px 0 0; font-size: 16px; line-height: 1.6; }
.media-row { display: flex; gap: 16px; margin-bottom: 16px; }
.video-card { flex: 1; background: #000; border-radius: 8px; overflow: hidden; }
.video-card video { width: 100%; height: 320px; object-fit: cover; background: #111; }
.video-title { padding: 6px 10px; color: #fff; }
.volume-panel { width: 240px; }
.volume-row { display: flex; align-items: center; gap: 8px; }
.volume-row + .volume-row { margin-top: 12px; }
.vol-icon { font-size: 18px; color: #909399; flex: 0 0 18px; }
.vol-icon-svg { flex: 0 0 18px; color: #909399; }
.volume-bar { flex: 1; height: 10px; background: #ebeef5; border-radius: 5px; overflow: hidden; }
.volume-bar { height: 10px; background: #ebeef5; border-radius: 5px; overflow: hidden; }
.volume-fill { height: 100%; background: linear-gradient(90deg, #67c23a, #409eff); transition: width 0.08s; }
.live-subtitle { margin-bottom: 16px; }
.subtitle-label { font-size: 12px; color: #909399; margin-bottom: 6px; }
.subtitle-body { min-height: 48px; padding: 10px 12px; border: 1px solid #ebeef5; border-radius: 6px; line-height: 1.7; }
.subtitle-partial { color: #909399; }
.subtitle-placeholder { color: #c0c4cc; }
.control-bar { display: flex; flex-wrap: wrap; gap: 10px; }
.device-select { display: flex; flex-direction: column; gap: 10px; }
.device-row { display: flex; align-items: center; gap: 10px; }
.device-label { flex: 0 0 52px; font-size: 13px; color: #606266; }
.device-row .el-select { flex: 1; }
.volume-fill.play-fill { background: linear-gradient(90deg, #e6a23c, #f56c6c); }
.vision-panel { margin-bottom: 16px; }
.vision-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.vision-title { font-weight: 600; font-size: 15px; }
.vision-subtitle { margin-top: 4px; font-size: 12px; color: #909399; }
</style>