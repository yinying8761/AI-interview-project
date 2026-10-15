// src/composables/useInterviewRoom.js
import { onBeforeUnmount, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'

/**
 * 视频面试间组合式函数。
 * 负责：拉取会话详情 -> 建立 WebSocket 信令 -> 申请本地媒体 ->
 *       创建 RTCPeerConnection -> 交换 Offer/Answer 与 ICE -> 媒体控制与断线重连。
 * @param recordId 面试会话 ID（来自路由参数）
 */
export function useInterviewRoom(recordId) {
  const localVideoRef = ref(null)
  const remoteVideoRef = ref(null)

  // 统一状态对象，供页面绑定
  const status = reactive({
    socket: 'closed',    // WebSocket 状态：closed/connecting/open/error
    peer: 'new',         // PeerConnection 状态：new/connecting/connected/disconnected/failed/closed
    media: 'idle',       // 本地媒体：idle/ready/closed
    network: 'unknown',  // 网络：unknown/good/normal/poor/failed
    camera: false,       // 摄像头是否开启
    microphone: false,   // 麦克风是否开启
    screen: false        // 是否在屏幕共享
  })

  const localStream = ref(null)
  const remoteStream = ref(null)

  let socket = null
  let peer = null
  let room = null
  let currentRole = 'CANDIDATE'
  let tracksAttached = false
  let leaving = false
  let reconnectTimer = null

  // 课堂默认使用公共 Google STUN；校园网 / 企业网无法连通时再补充 TURN
  const iceServers = [
    { urls: 'stun:stun.l.google.com:19302' }
  ]

  /**
   * 拉取会话详情，得到 roomCode 并确定当前用户角色（HOST / CANDIDATE）。
   */
  const loadSession = async () => {
    // getSession 复用第05讲 InterviewSessionController 的 GET /api/interview-sessions/{id}
    const res = await request.get(`/interview-sessions/${recordId}`)
    room = res.data

    // 依据当前用户角色识别主持人：候选人为 CANDIDATE，面试官/管理员为 HOST
    const roles = JSON.parse(localStorage.getItem('roles') || '[]')
    currentRole = roles.includes('interviewer') || roles.includes('admin')
      ? 'HOST'
      : 'CANDIDATE'

    // 缓存房间编号与角色，供页面展示与调试
    localStorage.setItem(`roomCode:${recordId}`, room.roomCode)
    localStorage.setItem('interviewRole', currentRole)
    return room
  }

  /**
   * 发送一条信令消息；roomCode / role 使用当前会话上下文。
   */
  const send = (type, payload = {}) => {
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({
        type,
        recordId: Number(recordId),
        roomCode: room ? room.roomCode : null,
        role: currentRole,
        payload: JSON.stringify(payload)
      }))
    }
  }

  /**
   * 创建 RTCPeerConnection：注册 ICE 候选、远端轨道与连接状态回调。
   */
  const createPeer = async () => {
    peer = new RTCPeerConnection({ iceServers })

    // 本地收集到候选地址时，通过信令发给对端
    peer.onicecandidate = (event) => {
      if (event.candidate) {
        send('ICE_CANDIDATE', { candidate: event.candidate })
      }
    }

    // 对端媒体轨道到达时，绑定到远端 <video>
    peer.ontrack = (event) => {
      remoteStream.value = event.streams[0]

      if (remoteVideoRef.value) {
        remoteVideoRef.value.srcObject = event.streams[0]
      }
    }

    // 连接状态变化：反映到 UI，并在 failed 时触发重连
    peer.onconnectionstatechange = () => {
      status.peer = peer.connectionState

      if (peer.connectionState === 'connected') {
        status.network = 'good'
      } else if (peer.connectionState === 'disconnected') {
        status.network = 'poor'
      } else if (peer.connectionState === 'failed') {
        status.network = 'failed'
        scheduleReconnect()
      }
    }

    return peer
  }

  /**
   * 仅获取并展示本地媒体流；轨道加入 PeerConnection 统一由 ensureLocalTracks 负责。
   */
  const bindLocalStream = async () => {
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        echoCancellation: true,   // 回声消除
        noiseSuppression: true,   // 降噪
        autoGainControl: true     // 自动增益
      },
      video: {
        width: { ideal: 1280 },
        height: { ideal: 720 },
        frameRate: { ideal: 24 }
      }
    })

    localStream.value = stream

    if (localVideoRef.value) {
      localVideoRef.value.srcObject = stream
    }

    status.camera = stream.getVideoTracks().some(item => item.enabled)
    status.microphone = stream.getAudioTracks().some(item => item.enabled)
    status.media = 'ready'

    return stream
  }

  /**
   * 本地轨道只加入 PeerConnection 一次，避免重复 addTrack。
   */
  const ensureLocalTracks = () => {
    if (!peer || !localStream.value || tracksAttached) {
      return
    }

    localStream.value.getTracks().forEach(track => peer.addTrack(track, localStream.value))
    tracksAttached = true
  }

  /** 释放旧 PeerConnection。 */
  const resetPeer = () => {
    if (peer) {
      try {
        peer.close()
      } catch (e) {
        // 忽略关闭异常
      }
    }

    peer = null
    tracksAttached = false
  }

  /** 确保存在一个可用的 PeerConnection；已关闭 / 失败则重建。 */
  const ensurePeerForSession = async () => {
    if (!peer || ['closed', 'failed'].includes(peer.connectionState)) {
      resetPeer()
      await createPeer()
    }

    return peer
  }

  /**
   * 统一定时调度重连：信令断开重新连 WebSocket，媒体断开重建 PeerConnection。
   */
  const scheduleReconnect = () => {
    if (leaving || reconnectTimer) {
      return
    }

    status.network = 'reconnecting'

    reconnectTimer = setTimeout(async () => {
      reconnectTimer = null

      if (!socket || socket.readyState !== WebSocket.OPEN) {
        await reconnectSocket()
      } else if (peer && ['failed', 'closed'].includes(peer.connectionState)) {
        await reconnectPeer()
      }
    }, 2000)
  }

  /**
   * 建立连接：拉取会话详情后创建 WebSocket，并绑定各回调。
   */
  const connect = async () => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }

    await loadSession()

    // 关闭旧的 socket，避免重复事件
    if (socket) {
      socket.onclose = null
      socket.onerror = null

      try {
        socket.close()
      } catch (e) {
        // 忽略关闭异常
      }

      socket = null
    }

    // 浏览器无法在 WebSocket 请求头里带 token，因此用查询参数携带
    const token = localStorage.getItem('token')
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const host = window.location.host

    socket = new WebSocket(`${protocol}://${host}/ws/interview?token=${token}`)
    status.socket = 'connecting'

    socket.onopen = () => {
      status.socket = 'open'
      send('JOIN')
    }

    socket.onmessage = async (event) => {
      const message = JSON.parse(event.data)
      const payload = message.payload ? JSON.parse(message.payload) : {}

      switch (message.type) {
        case 'ROOM_STATE':
          // 拿到房间状态后，同步 roomCode 并准备好 PeerConnection 与本地媒体
          await handleRoomState(payload)
          break
        case 'PEER_JOINED':
          // 对端加入：由最新加入方发起 Offer
          await createOffer()
          break
        case 'OFFER':
          await handleOffer(payload)
          break
        case 'ANSWER':
          await handleAnswer(payload)
          break
        case 'ICE_CANDIDATE':
          await handleCandidate(payload)
          break
        case 'MEDIA_STATE':
          // 对端媒体状态，本讲仅记录，不强制应用
          break
        case 'PEER_LEFT':
          resetPeer()
          status.peer = 'closed'
          status.network = 'unknown'
          break
        case 'ERROR':
          console.error('信令错误:', payload)
          ElMessage.error('信令错误：' + (payload && payload.message ? payload.message : '未知'))
          break
      }
    }

    socket.onclose = () => {
      status.socket = 'closed'

      // 非主动离开才触发重连
      if (!leaving) {
        scheduleReconnect()
      }
    }

    socket.onerror = () => {
      status.socket = 'error'
    }
  }

  /**
   * 收到 ROOM_STATE：确保 PeerConnection 与本地媒体就绪。
   */
  const handleRoomState = async (payload = {}) => {
    // 服务端可能补发房间编号（旧记录无 roomCode），此处同步，后续消息使用新编号
    if (room) {
      if (payload.roomCode) room.roomCode = payload.roomCode
      if (payload.status) room.status = payload.status
    }
    await ensurePeerForSession()

    if (!localStream.value) {
      await bindLocalStream()
    }

    ensureLocalTracks()
    status.peer = peer.connectionState
  }

  /**
   * 创建并发送 Offer（对端加入时由接收方调用）。
   */
  const createOffer = async () => {
    await ensurePeerForSession()

    if (!localStream.value) {
      await bindLocalStream()
    }

    ensureLocalTracks()

    const offer = await peer.createOffer()
    await peer.setLocalDescription(offer)
    send('OFFER', { sdp: offer.sdp, type: offer.type })
  }

  /**
   * 收到 Offer：设置远端描述、确保本地媒体后回送 Answer。
   */
  const handleOffer = async (payload) => {
    await ensurePeerForSession()
    await peer.setRemoteDescription(payload)

    if (!localStream.value) {
      await bindLocalStream()
    }

    ensureLocalTracks()

    const answer = await peer.createAnswer()
    await peer.setLocalDescription(answer)
    send('ANSWER', { sdp: answer.sdp, type: answer.type })
  }

  /**
   * 收到 Answer：设置远端描述，完成媒体协商。
   */
  const handleAnswer = async (payload) => {
    if (peer && peer.signalingState !== 'stable') {
      await peer.setRemoteDescription(payload)
    }
  }

  /**
   * 收到 ICE 候选：加入对端候选地址，辅助 NAT 穿透。
   */
  const handleCandidate = async (payload) => {
    if (peer && payload.candidate) {
      await peer.addIceCandidate(payload.candidate)
    }
  }

  /**
   * 静音开关：切换本地音频轨道 enabled，并把状态广播给对端。
   */
  const toggleMicrophone = () => {
    if (!localStream.value) {
      return
    }

    localStream.value.getAudioTracks().forEach(track => {
      track.enabled = !track.enabled
    })

    status.microphone = localStream.value
      .getAudioTracks()
      .some(track => track.enabled)

    send('MEDIA_STATE', { microphone: status.microphone })
  }

  /**
   * 摄像头开关：切换本地视频轨道 enabled，并把状态广播给对端。
   */
  const toggleCamera = () => {
    if (!localStream.value) {
      return
    }

    localStream.value.getVideoTracks().forEach(track => {
      track.enabled = !track.enabled
    })

    status.camera = localStream.value
      .getVideoTracks()
      .some(track => track.enabled)

    send('MEDIA_STATE', { camera: status.camera })
  }

  /**
   * 用新轨道替换视频发送器轨道（用于屏幕共享 / 切换摄像头）。
   */
  const replaceMediaTrack = async (track) => {
    const sender = peer.getSenders().find(item =>
      item.track && item.track.kind === 'video'
    )

    if (sender) {
      await sender.replaceTrack(track)
    }
  }

  /**
   * 屏幕共享：getDisplayMedia 取屏幕轨道并替换视频发送器；结束共享时恢复摄像头。
   */
  const startScreenShare = async () => {
    if (!peer || !localStream.value) {
      return
    }

    const screenStream = await navigator.mediaDevices.getDisplayMedia({
      video: { frameRate: { ideal: 15 } },
      audio: false
    })

    const screenTrack = screenStream.getVideoTracks()[0]
    await replaceMediaTrack(screenTrack)

    if (localVideoRef.value) {
      localVideoRef.value.srcObject = screenStream
    }

    status.screen = true
    send('MEDIA_STATE', { screen: true })

    // 用户点浏览器“停止共享”时，由 onended 兜底恢复摄像头
    screenTrack.onended = async () => {
      const cameraTrack = localStream.value.getVideoTracks()[0]

      if (cameraTrack) {
        await replaceMediaTrack(cameraTrack)
      }

      if (localVideoRef.value) {
        localVideoRef.value.srcObject = localStream.value
      }

      status.screen = false
      send('MEDIA_STATE', { screen: false })
    }
  }

  /**
   * 结束屏幕共享：把视频发送器恢复为摄像头轨道。
   */
  const stopScreenShare = async () => {
    const cameraTrack = localStream.value.getVideoTracks()[0]

    if (cameraTrack) {
      await replaceMediaTrack(cameraTrack)
    }

    if (localVideoRef.value) {
      localVideoRef.value.srcObject = localStream.value
    }

    status.screen = false
    send('MEDIA_STATE', { screen: false })
  }

  /**
   * 信令断线重连：重新建立 WebSocket 并发送 JOIN。
   */
  const reconnectSocket = async () => {
    await connect()
  }

  /**
   * 媒体断线重连：释放并重建 PeerConnection，重新协商 Offer/Answer。
   */
  const reconnectPeer = async () => {
    resetPeer()
    await createPeer()

    if (!localStream.value) {
      await bindLocalStream()
    }

    ensureLocalTracks()
    await createOffer()
  }

  /**
   * 页面可手动触发的完整重连。
   */
  const reconnect = async () => {
    status.network = 'reconnecting'
    await reconnectSocket()
    await reconnectPeer()
  }

  /**
   * 离开房间：发送 LEAVE、停止本地轨道、关闭 PeerConnection 与 WebSocket。
   */
  const leave = () => {
    leaving = true

    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }

    send('LEAVE')

    // 停止本地媒体，释放摄像头 / 麦克风
    if (localStream.value) {
      localStream.value.getTracks().forEach(track => track.stop())
    }

    resetPeer()

    if (socket) {
      socket.close()
      socket = null
    }

    status.socket = 'closed'
    status.peer = 'closed'
    status.media = 'closed'
    status.network = 'unknown'
  }

  // 组件卸载时自动清理
  onBeforeUnmount(leave)

  // ===== 设备选择 =====
  const devices = reactive({ cameras: [], microphones: [], speakers: [] })
  const selectedDevice = reactive({ camera: '', microphone: '', speaker: '' })

  // 枚举可用设备（需在授权后调用才能拿到名称）
  const enumDevices = async () => {
    const list = await navigator.mediaDevices.enumerateDevices()
    devices.cameras = list.filter((d) => d.kind === 'videoinput')
    devices.microphones = list.filter((d) => d.kind === 'audioinput')
    devices.speakers = list.filter((d) => d.kind === 'audiooutput')
    if (!selectedDevice.camera && devices.cameras[0]) {
      selectedDevice.camera = devices.cameras[0].deviceId
    }
    if (!selectedDevice.microphone && devices.microphones[0]) {
      selectedDevice.microphone = devices.microphones[0].deviceId
    }
  }

  // 按指定摄像头/麦克风重新获取并在本地替换
  const switchAVStream = async (videoId, audioId) => {
    const params = {
      audio: audioId
        ? { deviceId: { exact: audioId }, echoCancellation: true, noiseSuppression: true, autoGainControl: true }
        : true,
      video: videoId
        ? { deviceId: { exact: videoId }, width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 24 } }
        : true
    }
    const newStream = await navigator.mediaDevices.getUserMedia(params)
    localStream.value?.getTracks().forEach((t) => t.stop())
    localStream.value = newStream
    if (localVideoRef.value) localVideoRef.value.srcObject = newStream
    status.camera = newStream.getVideoTracks().some((t) => t.enabled)
    status.microphone = newStream.getAudioTracks().some((t) => t.enabled)
  }

  const applyCamera = async (deviceId) => {
    selectedDevice.camera = deviceId
    await switchAVStream(deviceId, selectedDevice.microphone)
  }

  const applyMicrophone = async (deviceId) => {
    selectedDevice.microphone = deviceId
    await switchAVStream(selectedDevice.camera, deviceId)
  }

  // 切换音响（输出设备）
  const applySpeaker = async (deviceId) => {
    selectedDevice.speaker = deviceId
    const el = localVideoRef.value
    if (el && el.setSinkId) {
      try {
        await el.setSinkId(deviceId)
      } catch (e) {
        // 不支持的浏览器直接忽略
      }
    }
  }

  return {
    localVideoRef,
    remoteVideoRef,
    localStream,
    remoteStream,
    status,
    connect,
    reconnect,
    toggleMicrophone,
    toggleCamera,
    startScreenShare,
    stopScreenShare,
    leave,
    devices,
    selectedDevice,
    enumDevices,
    applyCamera,
    applyMicrophone,
    applySpeaker
  }
}
