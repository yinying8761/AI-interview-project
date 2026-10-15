<script setup>
import {
  Bot,
  CircleStop,
  FileAudio,
  LoaderCircle,
  Mic,
  Send,
  Sparkles,
  UserRound,
  Volume2,
} from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { formatDateTime } from '../utils/media'

const props = defineProps({
  messages: { type: Array, default: () => [] },
  opening: { type: Boolean, default: false },
  sending: { type: Boolean, default: false },
  transcribing: { type: Boolean, default: false },
  ttsBusyId: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['send', 'audio', 'replay'])
const draft = ref('')
const recording = ref(false)
const recordingSeconds = ref(0)
const recorderError = ref('')
const messageList = ref(null)

let mediaRecorder = null
let recordingStream = null
let chunks = []
let recordingTimer = null

const canSend = computed(
  () =>
    draft.value.trim().length > 0 &&
    !props.sending &&
    !props.opening &&
    !props.disabled,
)

watch(
  () => [props.messages.length, props.opening, props.sending],
  async () => {
    await nextTick()
    if (messageList.value) {
      messageList.value.scrollTop = messageList.value.scrollHeight
    }
  },
)

function submitText() {
  if (!canSend.value) return
  const text = draft.value.trim()
  draft.value = ''
  emit('send', text)
}

function handleKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    submitText()
  }
}

async function startRecording() {
  recorderError.value = ''
  if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) {
    recorderError.value = '当前浏览器不支持录音，请使用最新版 Chrome 或 Edge。'
    return
  }

  try {
    recordingStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
    })
    const mimeType = selectMimeType()
    mediaRecorder = new MediaRecorder(
      recordingStream,
      mimeType ? { mimeType } : undefined,
    )
    chunks = []
    mediaRecorder.addEventListener('dataavailable', (event) => {
      if (event.data?.size) chunks.push(event.data)
    })
    mediaRecorder.addEventListener('stop', finishRecording, { once: true })
    mediaRecorder.start(200)
    recording.value = true
    recordingSeconds.value = 0
    recordingTimer = window.setInterval(() => {
      recordingSeconds.value += 1
      if (recordingSeconds.value >= 90) stopRecording()
    }, 1000)
  } catch (error) {
    recorderError.value = microphoneErrorMessage(error)
    stopRecordingTracks()
  }
}

function stopRecording() {
  if (mediaRecorder?.state === 'recording') {
    mediaRecorder.stop()
  }
  if (recordingTimer) window.clearInterval(recordingTimer)
  recordingTimer = null
  recording.value = false
}

function finishRecording() {
  const mimeType = mediaRecorder?.mimeType || chunks[0]?.type || 'audio/webm'
  const blob = new Blob(chunks, { type: mimeType })
  chunks = []
  mediaRecorder = null
  stopRecordingTracks()
  if (blob.size > 0) emit('audio', blob)
}

function stopRecordingTracks() {
  recordingStream?.getTracks().forEach((track) => track.stop())
  recordingStream = null
}

function selectMimeType() {
  const candidates = [
    'audio/webm;codecs=opus',
    'audio/webm',
    'audio/ogg;codecs=opus',
    'audio/mp4',
  ]
  return candidates.find((item) => MediaRecorder.isTypeSupported(item)) || ''
}

function microphoneErrorMessage(error) {
  if (error?.name === 'NotAllowedError') return '未获得麦克风权限，请允许访问后重试。'
  if (error?.name === 'NotFoundError') return '未检测到可用麦克风。'
  return error?.message || '录音启动失败。'
}

onBeforeUnmount(() => {
  if (recordingTimer) window.clearInterval(recordingTimer)
  if (mediaRecorder?.state === 'recording') mediaRecorder.stop()
  stopRecordingTracks()
})
</script>

<template>
  <section class="conversation-panel">
    <div ref="messageList" class="message-list" aria-live="polite">
      <div v-if="!messages.length && !opening" class="conversation-empty">
        <Sparkles :size="24" />
        <strong>面试已就绪</strong>
        <p>请等待面试官提问，或直接在下方输入回答。</p>
      </div>

      <article
        v-for="message in messages"
        :key="message.id"
        class="message"
        :class="message.role"
      >
        <div class="message-avatar">
          <Bot v-if="message.role === 'assistant'" :size="17" />
          <UserRound v-else :size="17" />
        </div>
        <div class="message-body">
          <div class="message-meta">
            <strong>{{ message.role === 'assistant' ? '面试官' : '我的回答' }}</strong>
            <span>{{ formatDateTime(message.created_at) }}</span>
            <button
              v-if="message.role === 'assistant'"
              class="icon-button icon-button-small"
              type="button"
              title="播放语音"
              :disabled="ttsBusyId === message.id"
              @click="emit('replay', message)"
            >
              <LoaderCircle v-if="ttsBusyId === message.id" class="spin" :size="14" />
              <Volume2 v-else :size="14" />
            </button>
          </div>
          <p>{{ message.content }}</p>
        </div>
      </article>

      <article
        v-if="sending || opening"
        class="message assistant"
        :class="{ centered: !messages.length }"
      >
        <div class="message-avatar"><Bot :size="17" /></div>
        <div class="message-body typing">
          <span /><span /><span />
        </div>
      </article>
    </div>

    <div class="composer">
      <div v-if="recorderError" class="recorder-error">{{ recorderError }}</div>

      <div v-if="recording" class="recording-strip">
        <span class="recording-dot" />
        <strong>正在录音</strong>
        <span>{{ String(Math.floor(recordingSeconds / 60)).padStart(2, '0') }}:{{ String(recordingSeconds % 60).padStart(2, '0') }}</span>
        <small>再次点击停止并提交，最长 90 秒</small>
        <button
          class="button button-danger button-small"
          type="button"
          @click="stopRecording"
        >
          <CircleStop :size="15" />
          停止并提交
        </button>
      </div>

      <template v-else>
        <textarea
          v-model="draft"
          rows="3"
          maxlength="5000"
          placeholder="输入回答，或点击麦克风录制语音..."
          :disabled="disabled || sending"
          @keydown="handleKeydown"
        />
        <div class="composer-actions">
          <div class="input-hints">
            <span><FileAudio :size="14" />语音会自动转写</span>
            <span>Enter 发送 · Shift + Enter 换行</span>
          </div>
          <button
            class="button button-secondary"
            type="button"
            :disabled="disabled || sending || transcribing"
            @click="startRecording"
          >
            <LoaderCircle v-if="transcribing" class="spin" :size="17" />
            <Mic v-else :size="17" />
            {{ transcribing ? '正在识别' : '语音回答' }}
          </button>
          <button
            class="button button-primary"
            type="button"
            :disabled="!canSend"
            @click="submitText"
          >
            <LoaderCircle v-if="sending" class="spin" :size="17" />
            <Send v-else :size="17" />
            发送回答
          </button>
        </div>
      </template>
    </div>
  </section>
</template>

<style scoped>
.conversation-panel {
  min-height: 460px;
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-sm);
}

.message-list {
  min-height: 220px;
  flex: 1;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 18px;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.conversation-empty {
  flex: 1;
  display: grid;
  align-content: center;
  justify-items: center;
  padding: 40px 20px;
  color: var(--muted);
  text-align: center;
}

.conversation-empty svg {
  margin-bottom: 10px;
  color: var(--teal);
}

.conversation-empty strong {
  color: var(--ink);
  font-size: 14px;
}

.conversation-empty p {
  margin: 5px 0 0;
  font-size: 12px;
}

.message {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.message.centered {
  margin: auto 0;
  justify-content: center;
}

.message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 8px;
  color: var(--teal-dark);
  background: var(--teal-soft);
}

.message.user .message-avatar {
  color: #95523d;
  background: var(--coral-soft);
}

.message-body {
  max-width: min(78%, 720px);
  padding: 11px 13px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface-soft);
}

.message.user .message-body {
  color: #fff;
  border-color: var(--teal-dark);
  background: var(--teal-dark);
}

.message-meta {
  min-height: 20px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.message-meta strong {
  color: var(--ink);
  font-size: 11px;
}

.message.user .message-meta strong {
  color: #ecfafa;
}

.message-meta > span {
  margin-left: auto;
  color: var(--muted);
  font-family: var(--font-data);
  font-size: 9px;
}

.message.user .message-meta > span {
  color: #b8dedb;
}

.message-body > p {
  margin: 7px 0 0;
  color: var(--ink-soft);
  font-size: 14px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}

.message.user .message-body > p {
  color: #fff;
}

.typing {
  min-width: 66px;
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 5px;
}

.typing span {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--teal);
  animation: pulse 1.1s infinite ease-in-out;
}

.typing span:nth-child(2) {
  animation-delay: .15s;
}

.typing span:nth-child(3) {
  animation-delay: .3s;
}

.composer {
  padding: 14px;
  border-top: 1px solid var(--line);
  background: var(--surface);
}

.composer textarea {
  width: 100%;
  min-height: 86px;
  padding: 12px;
  resize: vertical;
  border: 1px solid var(--line-strong);
  border-radius: 7px;
  outline: none;
  color: var(--ink);
  background: var(--surface-soft);
  font: inherit;
  font-size: 14px;
  line-height: 1.6;
  transition: border-color .15s ease, box-shadow .15s ease;
}

.composer textarea:focus {
  border-color: var(--teal);
  box-shadow: 0 0 0 3px var(--teal-soft);
}

.composer-actions {
  margin-top: 10px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.input-hints {
  min-width: 0;
  margin-right: auto;
  display: flex;
  flex-wrap: wrap;
  gap: 5px 14px;
  color: var(--muted);
  font-size: 10px;
}

.input-hints span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.recording-strip {
  min-height: 86px;
  padding: 13px;
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid #efb2a3;
  border-radius: 7px;
  background: var(--coral-soft);
}

.recording-strip strong {
  color: #8f422f;
  font-size: 13px;
}

.recording-strip > span:not(.recording-dot) {
  color: #8f422f;
  font-family: var(--font-data);
  font-size: 14px;
}

.recording-strip small {
  margin-right: auto;
  color: #9e6557;
  font-size: 10px;
}

.recording-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: var(--coral);
  box-shadow: 0 0 0 5px rgb(210 109 77 / 14%);
  animation: pulse 1s infinite ease-in-out;
}

.recorder-error {
  margin-bottom: 9px;
  padding: 8px 10px;
  border-radius: 6px;
  color: var(--danger);
  background: var(--danger-soft);
  font-size: 11px;
}

@keyframes pulse {
  0%,
  100% {
    opacity: .35;
    transform: translateY(0);
  }
  50% {
    opacity: 1;
    transform: translateY(-2px);
  }
}

@media (max-width: 720px) {
  .message-list {
    padding: 14px;
  }

  .message-body {
    max-width: 88%;
  }

  .composer-actions {
    flex-wrap: wrap;
  }

  .input-hints {
    width: 100%;
    margin-bottom: 3px;
  }

  .recording-strip {
    flex-wrap: wrap;
  }

  .recording-strip small {
    width: 100%;
    margin: 0;
  }
}
</style>
