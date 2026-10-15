<script setup>
import {
  Activity,
  Camera,
  CameraOff,
  CheckCircle2,
  LoaderCircle,
  RefreshCw,
  ScanFace,
} from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'

const props = defineProps({
  stream: { type: Object, default: null },
  status: { type: String, default: 'idle' },
  error: { type: String, default: '' },
  deviceLabel: { type: String, default: '' },
  faceDetected: { type: Boolean, default: false },
  expression: { type: String, default: '等待分析' },
  attentionState: { type: String, default: 'unknown' },
  visionStatus: { type: Object, default: () => ({}) },
  targetFps: { type: Number, default: 3 },
})

const emit = defineEmits(['retry', 'video-ready'])
const video = ref(null)

const statusCopy = computed(() => {
  if (props.status === 'ready') return '实时分析中'
  if (props.status === 'requesting') return '正在连接摄像头'
  if (props.status === 'reconnecting') return '正在恢复摄像头'
  if (props.status === 'error') return '摄像头不可用'
  return '摄像头未启动'
})

const attentionCopy = computed(() => {
  if (props.attentionState === 'attentive') return '注意力集中'
  if (props.attentionState === 'distracted') return '注意力分散'
  return '等待人脸'
})

const analysisCopy = computed(() => {
  const state = props.visionStatus?.state
  const fps = Number(props.visionStatus?.effective_fps)
  if (state === 'warming_up' || state === 'starting') return '正在预热视觉模型'
  if (state === 'degraded') return '视觉分析降级'
  if (props.visionStatus?.processing) {
    return fps > 0 ? `分析中 · ${fps.toFixed(1)} 帧/秒` : '正在分析最新帧'
  }
  if (props.visionStatus?.pending) return '已接收最新帧'
  if (Number(props.visionStatus?.processed_frames) > 0) {
    return `实时分析 · ${fps > 0 ? `${fps.toFixed(1)} 帧/秒` : `${props.targetFps} 帧/秒目标`}`
  }
  return '等待首个画面'
})

const latencyCopy = computed(() => {
  const latency = Number(props.visionStatus?.last_latency_ms)
  if (!Number.isFinite(latency) || latency <= 0) return '-- ms'
  return `${Math.round(latency)} ms`
})

function bindVideo() {
  const element = video.value
  if (!element) return
  element.srcObject = props.stream || null
  if (props.stream) element.play().catch(() => {})
  emit('video-ready', element)
}

watch(() => props.stream, bindVideo, { flush: 'post' })
onMounted(bindVideo)
</script>

<template>
  <section class="camera-panel">
    <div class="camera-stage" :class="{ ready: status === 'ready' }">
      <video
        ref="video"
        autoplay
        muted
        playsinline
        aria-label="面试摄像头实时画面"
      />

      <div v-if="status !== 'ready'" class="camera-state">
        <LoaderCircle
          v-if="status === 'requesting' || status === 'reconnecting'"
          class="spin"
          :size="30"
        />
        <CameraOff v-else :size="30" />
        <strong>{{ statusCopy }}</strong>
        <p>{{ error || '开始面试后，摄像头会保持开启并实时分析。' }}</p>
        <button
          v-if="status === 'error'"
          class="button button-secondary button-small"
          type="button"
          @click="emit('retry')"
        >
          <RefreshCw :size="15" />
          重新连接
        </button>
      </div>

      <div v-if="status === 'ready'" class="camera-overlay camera-overlay-top">
        <span class="live-dot" />
        <span>{{ statusCopy }}</span>
        <span class="camera-device">{{ deviceLabel }}</span>
      </div>

      <div v-if="status === 'ready'" class="camera-overlay camera-overlay-bottom">
        <span class="signal-chip" :class="{ detected: faceDetected }">
          <ScanFace :size="16" />
          {{ faceDetected ? '已检测到人脸' : '请正对摄像头' }}
        </span>
        <span class="signal-chip" :class="{ detected: attentionState === 'attentive' }">
          <CheckCircle2 :size="16" />
          {{ attentionCopy }}
        </span>
        <span
          class="signal-chip"
          :class="{
            detected: visionStatus?.processed_frames > 0 && !visionStatus?.processing,
            pending: visionStatus?.processing || visionStatus?.pending,
          }"
        >
          <Activity :size="16" />
          {{ analysisCopy }}
        </span>
      </div>
    </div>

    <div class="camera-caption">
      <span>
        <Camera :size="16" />
        本地画面仅用于实时分析
      </span>
      <div class="camera-caption-status">
        <span>分析延迟 {{ latencyCopy }}</span>
        <strong>{{ expression }}</strong>
      </div>
    </div>
  </section>
</template>

<style scoped>
.camera-panel {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: #0f1b20;
  box-shadow: var(--shadow-sm);
}

.camera-stage {
  position: relative;
  aspect-ratio: 16 / 10;
  min-height: 230px;
  overflow: hidden;
  background:
    linear-gradient(var(--surface) 1px, transparent 1px),
    linear-gradient(90deg, var(--surface) 1px, transparent 1px),
    #111f25;
  background-size: 28px 28px;
}

.camera-stage::after {
  position: absolute;
  inset: 0;
  pointer-events: none;
  content: '';
  box-shadow: inset 0 0 0 1px rgb(255 255 255 / 5%);
}

.camera-stage video {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  transform: scaleX(-1);
}

.camera-stage:not(.ready) video {
  opacity: 0;
}

.camera-state {
  position: absolute;
  inset: 0;
  padding: 28px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #d9e6e9;
  text-align: center;
}

.camera-state svg {
  margin-bottom: 13px;
  color: var(--teal-light);
}

.camera-state strong {
  font-size: 16px;
}

.camera-state p {
  max-width: 360px;
  margin: 7px 0 16px;
  color: #9eb0b6;
  font-size: 13px;
  line-height: 1.6;
}

.camera-overlay {
  position: absolute;
  right: 14px;
  left: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.camera-overlay-top {
  top: 14px;
  color: #eef8f8;
  font-size: 12px;
  font-weight: 700;
}

.camera-overlay-bottom {
  bottom: 14px;
  flex-wrap: wrap;
}

.camera-device {
  max-width: 44%;
  margin-left: auto;
  overflow: hidden;
  color: #a8bcc1;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #ef6a4d;
  box-shadow: 0 0 0 4px rgb(239 106 77 / 20%);
}

.signal-chip {
  min-height: 30px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: 1px solid rgb(255 255 255 / 14%);
  border-radius: 6px;
  color: #c2d0d4;
  background: rgb(12 26 31 / 72%);
  backdrop-filter: blur(8px);
  font-size: 11px;
  font-weight: 700;
}

.signal-chip.detected {
  color: #bdf0df;
  border-color: rgb(48 180 143 / 40%);
}

.camera-caption {
  min-height: 48px;
  padding: 0 15px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #aabac0;
  font-size: 12px;
}

.camera-caption span {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.camera-caption strong {
  color: #eef6f7;
  font-size: 12px;
}

.camera-caption-status {
  display: flex;
  align-items: center;
  gap: 10px;
}

.camera-caption-status > span {
  color: #738f96;
  font-family: var(--font-data);
  font-size: 10px;
}

.signal-chip.pending {
  color: #f2d29d;
  border-color: rgb(201 138 46 / 42%);
}

@media (max-width: 640px) {
  .camera-stage {
    min-height: 220px;
    aspect-ratio: 4 / 3;
  }

  .camera-device {
    display: none;
  }
}
</style>
