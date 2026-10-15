<template>
  <main class="page-container ai-room">
    <header><div><h2>大模型面试官</h2><p>文字作答与视觉行为分析 · 面试 #{{ id }}</p></div><el-button @click="router.push('/records')">返回面试记录</el-button></header>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <el-alert v-if="warning" :title="warning" type="warning" :closable="false" show-icon />
    <div class="room-grid">
      <el-card class="conversation" v-loading="loading">
        <template #header><strong>第 {{ state.question_count || 0 }} / 12 题</strong></template>
        <el-empty v-if="!messages.length" description="点击生成首题，开始面试" />
        <article v-for="m in messages" :key="m.id" :class="['bubble', m.role]">
          <b>{{ m.role === 'assistant' ? '面试官' : '我的回答' }}</b><p>{{ m.content }}</p>
        </article>
        <el-button v-if="!messages.length" type="primary" :loading="busy" @click="opening">生成首题</el-button>
        <el-input v-model="answer" type="textarea" :rows="5" maxlength="5000" show-word-limit placeholder="请输入你的回答，可结合具体项目、技术选择和结果说明。" :disabled="busy || checkpoint" />
        <div class="actions"><el-button type="primary" :loading="busy" :disabled="!answer.trim() || !messages.length || checkpoint" @click="send">提交回答并继续</el-button><el-button :loading="finishing" :disabled="busy || !hasAnswers" @click="finish">结束并生成报告</el-button></div>
        <p v-if="checkpoint">本轮已达到12题，请生成报告。</p>
      </el-card>
      <el-card><template #header><strong>视觉行为分析</strong></template>
        <video ref="video" autoplay muted playsinline />
        <p>{{ cameraNote }}</p>
        <el-button :disabled="cameraStarting || !!stream" @click="enableCamera">开启摄像头并分析</el-button>
        <el-button v-if="stream" @click="stopCamera">停止采集</el-button>
        <dl><dt>表情指标</dt><dd>{{ vision.metrics.value.expression_score ?? '未评估' }}</dd><dt>注意力指标</dt><dd>{{ vision.metrics.value.attention_score ?? '未评估' }}</dd></dl>
        <p>{{ vision.message.value }}</p><p class="muted">缺少有效人脸样本时，视觉维度显示“未评估”，不生成综合分。该指标仅用于本次练习反馈。</p>
      </el-card>
    </div>
  </main>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../api/request'
import { getSession, startSession, completeSession } from '../api/interview'
import { useVisionCapture } from '../composables/useVisionCapture'
const id = Number(useRoute().params.recordId)
const router = useRouter()
const state = ref({}), answer = ref(''), error = ref(''), warning = ref('')
const busy = ref(false), loading = ref(false), finishing = ref(false), video = ref(null)
const cameraNote = ref('尚未采集'), cameraStarting = ref(false), stream = ref(null)
const messages = computed(() => state.value.transcript || [])
const hasAnswers = computed(() => messages.value.some(m => m.role === 'user'))
const checkpoint = computed(() => state.value.status === 'checkpoint')
const vision = useVisionCapture({ recordId: id, getVideoElement: () => video.value, getQuestionIndex: () => Math.max(1, state.value.question_count || 1) })
const endpoint = `/interview-sessions/${id}/conversation`
let pendingSubmitId = null
async function load() {
  loading.value = true
  try {
    const record = (await getSession(id)).data
    if (record.status === 'pending') await startSession(id)
    state.value = (await request.get(endpoint)).data
  } catch (e) { error.value = e.response?.data?.message || e.message }
  finally { loading.value = false }
}
async function opening() {
  busy.value = true; error.value = ''
  try { const r = (await request.post(`${endpoint}/opening`, {}, { timeout: 180000 })).data; state.value = r.session; warning.value = r.warning || '' }
  catch (e) { error.value = e.response?.data?.message || e.message }
  finally { busy.value = false }
}
async function send() {
  busy.value = true; error.value = ''; pendingSubmitId ||= crypto.randomUUID()
  try { const r = (await request.post(endpoint, { message: answer.value, submitId: pendingSubmitId }, { timeout: 180000 })).data; state.value = r.session; answer.value = ''; pendingSubmitId = null }
  catch (e) { error.value = e.response?.data?.message || e.message }
  finally { busy.value = false }
}
async function enableCamera() {
  cameraStarting.value = true
  try { stream.value = await navigator.mediaDevices.getUserMedia({ video: true, audio: false }); video.value.srcObject = stream.value; await video.value.play(); await vision.start(); cameraNote.value = '正在采集本次面试图像' }
  catch (e) { cameraNote.value = `摄像头未开启：${e.message}`; stopCamera() }
  finally { cameraStarting.value = false }
}
function stopCamera() { vision.stop(); stream.value?.getTracks().forEach(t => t.stop()); stream.value = null }
async function finish() {
  finishing.value = true; error.value = ''; stopCamera()
  try { await completeSession(id, {}); router.push('/records') }
  catch (e) { error.value = e.response?.data?.message || e.message }
  finally { finishing.value = false }
}
onMounted(load)
onBeforeUnmount(stopCamera)
</script>

<style scoped>
header{display:flex;justify-content:space-between;align-items:center;margin-bottom:24px}header p,.muted{color:#667085}.room-grid{display:grid;grid-template-columns:minmax(0,2fr) minmax(260px,1fr);gap:24px;margin-top:20px}.bubble{padding:16px;background:#f1f5fa;border-radius:12px;margin-bottom:16px}.bubble.user{background:#eef8f3}.bubble p{white-space:pre-wrap;line-height:1.8;overflow-wrap:anywhere}video{width:100%;aspect-ratio:4/3;background:#17243a;border-radius:12px}.actions{display:flex;gap:10px;margin-top:16px}dt{color:#667085}dd{margin:8px 0 16px;font-size:24px}@media(max-width:850px){.room-grid{grid-template-columns:1fr}}
</style>
