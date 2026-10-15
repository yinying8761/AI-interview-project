import { onBeforeUnmount, ref, shallowRef } from 'vue'

export function useCamera() {
  const stream = shallowRef(null)
  const videoElement = shallowRef(null)
  const status = ref('idle')
  const error = ref('')
  const deviceLabel = ref('')
  let intentionalStop = false
  let retryTimer = null

  async function attachStream(nextStream) {
    stream.value = nextStream
    if (videoElement.value) {
      videoElement.value.srcObject = nextStream
      await videoElement.value.play().catch(() => {})
    }
  }

  async function start() {
    intentionalStop = false
    if (retryTimer) window.clearTimeout(retryTimer)
    status.value = stream.value ? 'reconnecting' : 'requesting'
    error.value = ''

    try {
      stopTracks()
      const nextStream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: 'user',
          width: { ideal: 960 },
          height: { ideal: 720 },
          frameRate: { ideal: 24, max: 30 },
        },
        audio: false,
      })
      await attachStream(nextStream)
      monitorTracks(nextStream)
      const track = nextStream.getVideoTracks()[0]
      deviceLabel.value = track?.label || '摄像头'
      status.value = 'ready'
      return nextStream
    } catch (cause) {
      status.value = 'error'
      error.value = cameraErrorMessage(cause)
      return null
    }
  }

  function monitorTracks(nextStream) {
    nextStream.getVideoTracks().forEach((track) => {
      track.addEventListener('ended', handleTrackEnded, { once: true })
      track.addEventListener('mute', () => {
        if (!intentionalStop && status.value === 'ready') {
          status.value = 'reconnecting'
        }
      })
      track.addEventListener('unmute', () => {
        if (!intentionalStop) status.value = 'ready'
      })
    })
  }

  function handleTrackEnded() {
    if (intentionalStop) return
    status.value = 'reconnecting'
    error.value = '摄像头连接中断，正在自动恢复。'
    retryTimer = window.setTimeout(() => start(), 1200)
  }

  function setVideoElement(element) {
    videoElement.value = element
    if (element && stream.value) {
      element.srcObject = stream.value
      element.play().catch(() => {})
    }
  }

  function stopTracks() {
    stream.value?.getTracks().forEach((track) => {
      track.removeEventListener('ended', handleTrackEnded)
      track.stop()
    })
    stream.value = null
  }

  function stop() {
    intentionalStop = true
    if (retryTimer) window.clearTimeout(retryTimer)
    stopTracks()
    if (videoElement.value) videoElement.value.srcObject = null
    status.value = 'idle'
  }

  onBeforeUnmount(stop)

  return {
    stream,
    videoElement,
    status,
    error,
    deviceLabel,
    start,
    stop,
    retry: start,
    setVideoElement,
  }
}

function cameraErrorMessage(error) {
  const name = error?.name || ''
  if (name === 'NotAllowedError' || name === 'SecurityError') {
    return '浏览器未获得摄像头权限。请允许访问摄像头后重试。'
  }
  if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
    return '未检测到可用摄像头，请连接设备后重试。'
  }
  if (name === 'NotReadableError' || name === 'TrackStartError') {
    return '摄像头正在被其他程序占用，请关闭占用程序后重试。'
  }
  return error?.message || '摄像头启动失败，请检查设备和浏览器权限。'
}
