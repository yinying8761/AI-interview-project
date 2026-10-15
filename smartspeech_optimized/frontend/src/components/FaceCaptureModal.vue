<script setup>
import {
  Camera,
  Check,
  LoaderCircle,
  RefreshCw,
  ScanFace,
  X,
} from 'lucide-vue-next'
import { onBeforeUnmount, ref, watch } from 'vue'
import { useCamera } from '../composables/useCamera'
import { captureVideoFrame } from '../utils/media'

const props = defineProps({
  open: { type: Boolean, default: false },
  title: { type: String, default: '人脸识别' },
  description: { type: String, default: '请正对摄像头，保持光线充足。' },
  loading: { type: Boolean, default: false },
  confirmText: { type: String, default: '确认' },
})

const emit = defineEmits(['close', 'confirm'])
const camera = useCamera()
const snapshot = ref(null)
const snapshotUrl = ref('')
const captureError = ref('')

watch(
  () => props.open,
  async (open) => {
    if (open) {
      snapshot.value = null
      snapshotUrl.value = ''
      captureError.value = ''
      await camera.start()
    } else {
      camera.stop()
    }
  },
)

async function capture() {
  captureError.value = ''
  const blob = await captureVideoFrame(camera.videoElement.value, {
    maxWidth: 720,
    quality: 0.8,
  })
  if (!blob) {
    captureError.value = '画面尚未准备好，请稍候重试。'
    return
  }
  if (snapshotUrl.value) URL.revokeObjectURL(snapshotUrl.value)
  snapshot.value = blob
  snapshotUrl.value = URL.createObjectURL(blob)
  camera.stop()
}

function retake() {
  snapshot.value = null
  if (snapshotUrl.value) URL.revokeObjectURL(snapshotUrl.value)
  snapshotUrl.value = ''
  camera.start()
}

function close() {
  if (props.loading) return
  camera.stop()
  emit('close')
}

onBeforeUnmount(() => {
  camera.stop()
  if (snapshotUrl.value) URL.revokeObjectURL(snapshotUrl.value)
})
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div v-if="open" class="modal-backdrop" @click.self="close">
        <section class="face-modal" role="dialog" aria-modal="true" :aria-label="title">
          <header class="modal-header">
            <div>
              <span class="modal-icon"><ScanFace :size="19" /></span>
              <span>
                <strong>{{ title }}</strong>
                <small>{{ description }}</small>
              </span>
            </div>
            <button
              class="icon-button"
              type="button"
              title="关闭"
              :disabled="loading"
              @click="close"
            >
              <X :size="18" />
            </button>
          </header>

          <div class="face-stage">
            <img v-if="snapshotUrl" :src="snapshotUrl" alt="待确认的人脸照片" />
            <video
              v-show="!snapshotUrl"
              :ref="camera.setVideoElement"
              autoplay
              muted
              playsinline
            />
            <div class="face-guide" :class="{ hidden: Boolean(snapshotUrl) }">
              <span class="guide-corner top-left" />
              <span class="guide-corner top-right" />
              <span class="guide-corner bottom-left" />
              <span class="guide-corner bottom-right" />
            </div>
            <div
              v-if="!snapshotUrl && ['idle', 'requesting', 'reconnecting'].includes(camera.status.value)"
              class="face-loading"
            >
              <LoaderCircle class="spin" :size="28" />
              <span>正在连接摄像头</span>
            </div>
            <div v-if="!snapshotUrl && camera.status.value === 'error'" class="face-loading error">
              <Camera :size="28" />
              <span>{{ camera.error.value }}</span>
              <button class="button button-secondary button-small" type="button" @click="camera.retry">
                <RefreshCw :size="15" />
                重试
              </button>
            </div>
          </div>

          <p v-if="captureError" class="capture-error">{{ captureError }}</p>
          <p v-else class="privacy-note">
            系统仅提取加密的人脸特征向量，不保存原始人脸照片。
          </p>

          <footer class="modal-actions">
            <button class="button button-secondary" type="button" :disabled="loading" @click="close">
              取消
            </button>
            <button
              v-if="!snapshot"
              class="button button-primary"
              type="button"
              :disabled="camera.status.value !== 'ready'"
              @click="capture"
            >
              <Camera :size="17" />
              拍摄照片
            </button>
            <template v-else>
              <button class="button button-secondary" type="button" :disabled="loading" @click="retake">
                <RefreshCw :size="16" />
                重新拍摄
              </button>
              <button
                class="button button-primary"
                type="button"
                :disabled="loading"
                @click="emit('confirm', snapshot)"
              >
                <LoaderCircle v-if="loading" class="spin" :size="17" />
                <Check v-else :size="17" />
                {{ confirmText }}
              </button>
            </template>
          </footer>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 100;
  padding: 20px;
  display: grid;
  place-items: center;
  background: rgb(10 24 29 / 62%);
  backdrop-filter: blur(5px);
}

.face-modal {
  width: min(560px, 100%);
  overflow: hidden;
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 10px;
  background: var(--surface);
  box-shadow: var(--shadow-lg);
}

.modal-header {
  min-height: 70px;
  padding: 13px 15px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  border-bottom: 1px solid var(--line);
}

.modal-header > div {
  display: flex;
  align-items: center;
  gap: 10px;
}

.modal-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  color: var(--teal-dark);
  background: var(--teal-soft);
}

.modal-header strong,
.modal-header small {
  display: block;
}

.modal-header strong {
  color: var(--ink);
  font-size: 14px;
}

.modal-header small {
  margin-top: 3px;
  color: var(--muted);
  font-size: 11px;
}

.face-stage {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: #101e24;
}

.face-stage video,
.face-stage img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  transform: scaleX(-1);
}

.face-guide {
  position: absolute;
  inset: 13%;
  pointer-events: none;
}

.face-guide.hidden {
  display: none;
}

.guide-corner {
  position: absolute;
  width: 34px;
  height: 34px;
  border-color: #70dcc3;
  border-style: solid;
}

.top-left {
  top: 0;
  left: 0;
  border-width: 2px 0 0 2px;
}

.top-right {
  top: 0;
  right: 0;
  border-width: 2px 2px 0 0;
}

.bottom-left {
  bottom: 0;
  left: 0;
  border-width: 0 0 2px 2px;
}

.bottom-right {
  right: 0;
  bottom: 0;
  border-width: 0 2px 2px 0;
}

.face-loading {
  position: absolute;
  inset: 0;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 9px;
  color: #dbe9eb;
  background: rgb(10 24 29 / 62%);
  font-size: 12px;
}

.face-loading.error {
  padding: 30px;
  color: #f1c4b7;
  text-align: center;
}

.capture-error,
.privacy-note {
  margin: 0;
  padding: 10px 15px;
  border-bottom: 1px solid var(--line);
  font-size: 11px;
}

.capture-error {
  color: var(--danger);
  background: var(--danger-soft);
}

.privacy-note {
  color: var(--muted);
  background: var(--surface-soft);
}

.modal-actions {
  padding: 14px 15px;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity .16s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
