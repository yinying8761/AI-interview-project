// 视觉采样工具，与 smartspeech_optimized/frontend/src/utils/media.js 保持一致：
// 帧采集先按等比缩放再压缩为 JPEG，降低上传体积与服务端解码压力。

export function captureVideoFrame(video, options = {}) {
  const maxWidth = options.maxWidth || 640
  const quality = options.quality || 0.65
  const sourceWidth = video?.videoWidth || 0
  const sourceHeight = video?.videoHeight || 0

  if (!sourceWidth || !sourceHeight) return Promise.resolve(null)

  const scale = Math.min(1, maxWidth / sourceWidth)
  const width = Math.max(1, Math.round(sourceWidth * scale))
  const height = Math.max(1, Math.round(sourceHeight * scale))
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d', { alpha: false })
  context.drawImage(video, 0, 0, width, height)

  return new Promise((resolve) => {
    canvas.toBlob(resolve, 'image/jpeg', quality)
  })
}