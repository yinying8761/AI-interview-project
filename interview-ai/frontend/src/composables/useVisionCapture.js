import { onBeforeUnmount, ref } from 'vue'
import {
  getVisionContext, getVisionMetrics, submitVisionFrame
} from '../api/interview'
import { captureVideoFrame } from '../utils/media'

/**
 * 负责从本地视频元素抽帧上报并轮询实时视觉指标。
 * 采样与轮询节奏与 smartspeech_optimized 前端 InterviewPage 保持一致：
 * - 帧采集缩到 448 宽、JPEG 0.54 质量；
 * - 抽帧间隔按服务端处理延迟与客户端往返自适应退避；
 * - 指标每 800ms 轮询一次，按 last_updated_at 单调更新，避免旧响应覆盖新数据；
 * - 页面隐藏时暂停采样；连续错误按帧数退避，3 次后进入 degraded 状态。
 */
export function useVisionCapture(options) {
  const {
    recordId,
    getVideoElement,
    getQuestionIndex,
    frameIntervalMs = 1000 / 3,
    pollIntervalMs = 800
  } = options

  // metrics 为 Python 实时指标原对象，vision 为视觉工作线程状态。
  const metrics = ref({})
  const vision = ref({})
  const status = ref('idle')
  const message = ref('正在准备视觉分析')

  let sessionId = ''
  let frameTimer = null
  let pollTimer = null
  let frameBusy = false
  let pollBusy = false
  let frameErrors = 0
  let pollErrors = 0
  let adaptiveDelay = frameIntervalMs
  let disposed = false

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
    status.value = 'active'
    message.value = '视觉指标持续更新'
  }

  function applyVision(nextVision) {
    if (!nextVision || !Object.keys(nextVision).length) return
    vision.value = nextVision
  }

  // 后端可能在 Python 会话失效后自动重建，响应携带的 sessionId 供前端自动切换。
  function adoptSessionId(payload) {
    const sid = payload?.sessionId
    if (typeof sid === 'string' && sid && sid !== sessionId) sessionId = sid
  }

  async function refreshSession() {
    // 会话号持续失效时重新获取上下文，配合后端的会话重建恢复采样。
    try {
      const context = await getVisionContext(Number(recordId), { silent: true })
      const data = context?.data ?? context
      if (data?.sessionId) sessionId = data.sessionId
    } catch {
      // 忽略：下一轮错误退避后再次尝试
    }
  }

  async function pullMetrics() {
    if (disposed || pollBusy || !sessionId || document.hidden) return
    pollBusy = true
    try {
      const result = await getVisionMetrics(sessionId, { silent: true })
      const payload = result?.data ?? result ?? {}
      applyMetrics(payload.metrics ?? payload.live_metrics)
      applyVision(payload.vision)
      adoptSessionId(payload)
      pollErrors = 0
    } catch {
      // 指标轮询失败不打断面试流程。
      pollErrors += 1
      if (pollErrors === 5) refreshSession()
    } finally {
      pollBusy = false
    }
  }

  async function pushFrame() {
    if (disposed || !sessionId) return
    if (document.hidden) {
      // 页面隐藏期间暂停抽帧，恢复可见后继续。
      frameTimer = window.setTimeout(pushFrame, 2000)
      return
    }

    const video = getVideoElement()
    if (!frameBusy && video && video.readyState >= 2 && video.videoWidth) {
      frameBusy = true
      const requestStartedAt = performance.now()
      try {
        const blob = await captureVideoFrame(video, { maxWidth: 448, quality: 0.54 })
        if (blob) {
          const result = await submitVisionFrame(
            sessionId,
            Number(recordId),
            getQuestionIndex(),
            blob,
            { silent: true }
          )
          const payload = result?.data ?? result ?? {}
          applyMetrics(payload.metrics)
          applyVision(payload.vision)
          adoptSessionId(payload)
          frameErrors = 0

          // 服务端处理延迟或客户端往返变慢时自动拉长抽帧间隔。
          const clientLatency = performance.now() - requestStartedAt
          const serverLatency = Number(payload?.vision?.last_latency_ms) || 0
          if (payload?.replaced_pending || serverLatency > 650 || clientLatency > 900) {
            adaptiveDelay = Math.min(frameIntervalMs * 2, 1800)
          } else if (serverLatency > 360 || clientLatency > 550) {
            adaptiveDelay = Math.min(frameIntervalMs * 1.4, 1200)
          } else {
            adaptiveDelay = frameIntervalMs
          }
        }
      } catch (error) {
        frameErrors += 1
        if (frameErrors === 3) {
          status.value = 'degraded'
          message.value = `视觉分析暂时不稳定：${error?.message || '请求失败'}`
          refreshSession()
        }
        adaptiveDelay = Math.min(2500, frameIntervalMs * (frameErrors + 1))
      } finally {
        frameBusy = false
      }
    }

    const backoff = frameErrors >= 3
      ? Math.max(1800, adaptiveDelay)
      : adaptiveDelay
    if (!disposed && sessionId) {
      frameTimer = window.setTimeout(pushFrame, backoff)
    }
  }

  async function start() {
    if (frameTimer || disposed) return
    status.value = 'loading'
    message.value = '正在连接视觉分析服务'
    try {
      const context = await getVisionContext(Number(recordId), { silent: true })
      const data = context?.data ?? context
      sessionId = data?.sessionId
      if (!sessionId) {
        status.value = 'unavailable'
        message.value = '视觉分析会话尚未创建'
        return
      }
      await pullMetrics()
      frameErrors = 0
      adaptiveDelay = frameIntervalMs
      frameTimer = window.setTimeout(pushFrame, 260)
      pollTimer = window.setInterval(pullMetrics, pollIntervalMs)
    } catch {
      status.value = 'unavailable'
      message.value = '视觉分析会话尚未创建'
    }
  }

  function stop() {
    if (frameTimer) window.clearTimeout(frameTimer)
    if (pollTimer) window.clearInterval(pollTimer)
    frameTimer = null
    pollTimer = null
    frameBusy = false
  }

  onBeforeUnmount(() => {
    disposed = true
    stop()
  })

  return {
    metrics,
    vision,
    status,
    message,
    start,
    stop
  }
}