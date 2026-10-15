const API_BASE = (import.meta.env.VITE_API_BASE || '').replace(/\/$/, '')

export class ApiError extends Error {
  constructor(message, status = 0, payload = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.payload = payload
  }
}

async function parseResponse(response) {
  const contentType = response.headers.get('content-type') || ''
  if (contentType.includes('application/json')) {
    return response.json()
  }
  return response.text()
}

export async function apiRequest(path, options = {}) {
  const {
    method = 'GET',
    body,
    headers = {},
    timeout = 120000,
    signal,
  } = options

  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), timeout)
  const abortFromCaller = () => controller.abort()
  signal?.addEventListener('abort', abortFromCaller, { once: true })

  const requestHeaders = { ...headers }
  let requestBody = body
  if (
    body !== undefined &&
    body !== null &&
    !(body instanceof FormData) &&
    !(body instanceof Blob)
  ) {
    requestHeaders['Content-Type'] = 'application/json'
    requestBody = JSON.stringify(body)
  }

  try {
    const response = await fetch(`${API_BASE}${path}`, {
      method,
      credentials: 'include',
      headers: requestHeaders,
      body: requestBody,
      signal: controller.signal,
    })
    const payload = await parseResponse(response)
    if (!response.ok) {
      const message =
        payload && typeof payload === 'object'
          ? payload.error || payload.message
          : typeof payload === 'string' && payload.trim()
      throw new ApiError(message || `请求失败（HTTP ${response.status}）`, response.status, payload)
    }
    return payload
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (error.name === 'AbortError') {
      throw new ApiError('请求超时，请检查网络或稍后重试。', 0)
    }
    throw new ApiError(error.message || '无法连接服务端。', 0)
  } finally {
    window.clearTimeout(timer)
    signal?.removeEventListener('abort', abortFromCaller)
  }
}

/**
 * 请求二进制内容（语音合成返回的音频流）。
 * 与 apiRequest 共享超时/错误处理逻辑，但不解析 JSON。
 */
export async function apiBlob(path, options = {}) {
  const {
    method = 'GET',
    body,
    headers = {},
    timeout = 120000,
    signal,
  } = options

  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), timeout)
  const abortFromCaller = () => controller.abort()
  signal?.addEventListener('abort', abortFromCaller, { once: true })

  const requestHeaders = { ...headers }
  let requestBody = body
  if (body !== undefined && body !== null && !(body instanceof FormData)) {
    requestHeaders['Content-Type'] = 'application/json'
    requestBody = JSON.stringify(body)
  }

  try {
    const response = await fetch(`${API_BASE}${path}`, {
      method,
      credentials: 'include',
      headers: requestHeaders,
      body: requestBody,
      signal: controller.signal,
    })

    if (!response.ok) {
      let message = `请求失败（HTTP ${response.status}）`
      try {
        const payload = await response.json()
        message = payload?.error || payload?.message || message
      } catch {
        /* 二进制接口出错时可能返回非 JSON 内容，保留默认提示 */
      }
      throw new ApiError(message, response.status)
    }

    const blob = await response.blob()
    if (!blob || blob.size === 0) {
      throw new ApiError('服务端未返回有效音频数据，请重试。', response.status)
    }
    return blob
  } catch (error) {
    if (error instanceof ApiError) throw error
    if (error.name === 'AbortError') {
      throw new ApiError('请求超时，请稍后重试。', 0)
    }
    throw new ApiError(error.message || '无法连接服务端。', 0)
  } finally {
    window.clearTimeout(timer)
    signal?.removeEventListener('abort', abortFromCaller)
  }
}

export function imageFormData(blob, fields = {}, filename = 'camera.jpg') {
  const form = new FormData()
  Object.entries(fields).forEach(([key, value]) => {
    if (value !== undefined && value !== null) form.append(key, String(value))
  })
  form.append('image', blob, filename)
  return form
}

export function jsonOrImageBody(blobOrDataUrl, fields = {}) {
  if (blobOrDataUrl instanceof Blob) {
    return imageFormData(blobOrDataUrl, fields)
  }
  return {
    ...fields,
    image_data: String(blobOrDataUrl || ''),
  }
}
