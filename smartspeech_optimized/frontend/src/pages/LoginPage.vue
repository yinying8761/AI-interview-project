<script setup>
import {
  Activity,
  AlertCircle,
  AudioLines,
  Bot,
  BrainCircuit,
  Eye,
  EyeOff,
  KeyRound,
  LoaderCircle,
  LogIn,
  ScanFace,
  ShieldCheck,
  SmilePlus,
  UserPlus,
} from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiRequest } from '../api/client'
import FaceCaptureModal from '../components/FaceCaptureModal.vue'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const mode = ref('password')
const showPassword = ref(false)
const error = ref('')
const notice = ref('')
const faceModal = reactive({ open: false, loading: false })
const capabilities = ref(null)

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({
  username: '',
  display_name: '',
  password: '',
  confirm: '',
  enrollFace: true,
})

const busy = computed(() => auth.loading || faceModal.loading)

const capabilityList = computed(() => {
  const caps = capabilities.value
  if (!caps) return []
  return [
    { key: 'deepseek', label: 'DeepSeek 大模型', ready: caps.deepseek },
    { key: 'whisper', label: 'Whisper 语音识别', ready: caps.whisper },
    { key: 'tts', label: 'Edge-TTS 语音合成', ready: caps.edge_tts },
    { key: 'vision', label: '表情 / 注意力分析', ready: caps.mediapipe },
    { key: 'face', label: '人脸识别登录', ready: caps.face_recognition },
  ]
})

onMounted(async () => {
  try {
    const payload = await apiRequest('/api/health', { timeout: 12000 })
    capabilities.value = payload.capabilities || null
  } catch {
    capabilities.value = null
  }
})

function switchMode(next) {
  mode.value = next
  error.value = ''
  notice.value = ''
}

function redirectAfterLogin() {
  const target = typeof route.query.redirect === 'string' ? route.query.redirect : ''
  router.replace(target && target.startsWith('/') ? target : { name: 'interview' })
}

async function submitPasswordLogin() {
  error.value = ''
  notice.value = ''
  if (!loginForm.username.trim() || !loginForm.password) {
    error.value = '请输入用户名和密码。'
    return
  }
  try {
    await auth.login(loginForm.username.trim(), loginForm.password)
    redirectAfterLogin()
  } catch (cause) {
    error.value = cause.message
  }
}

async function submitRegister() {
  error.value = ''
  notice.value = ''
  const username = registerForm.username.trim()
  const displayName = registerForm.display_name.trim() || username

  if (username.length < 3) {
    error.value = '用户名长度至少 3 个字符。'
    return
  }
  if (registerForm.password.length < 6) {
    error.value = '密码长度至少 6 个字符。'
    return
  }
  if (registerForm.password !== registerForm.confirm) {
    error.value = '两次输入的密码不一致。'
    return
  }

  try {
    await auth.register({
      username,
      password: registerForm.password,
      display_name: displayName,
    })
  } catch (cause) {
    error.value = cause.message
    return
  }

  if (registerForm.enrollFace) {
    faceModal.open = true
    notice.value = '账号已创建。请拍摄一张清晰的人脸照片完成绑定，也可以跳过。'
    return
  }
  redirectAfterLogin()
}

async function confirmFaceEnroll(blob) {
  faceModal.loading = true
  error.value = ''
  try {
    await auth.registerFace(blob)
    faceModal.open = false
    redirectAfterLogin()
  } catch (cause) {
    error.value = cause.message
  } finally {
    faceModal.loading = false
  }
}

async function confirmFaceLogin(blob) {
  faceModal.loading = true
  error.value = ''
  try {
    const payload = await auth.faceLogin(blob)
    faceModal.open = false
    notice.value = payload?.match
      ? `人脸匹配成功，相似度 ${(payload.match.similarity * 100).toFixed(1)}%。`
      : '人脸匹配成功。'
    redirectAfterLogin()
  } catch (cause) {
    error.value = cause.message
  } finally {
    faceModal.loading = false
  }
}

function onFaceModalConfirm(blob) {
  if (mode.value === 'face') confirmFaceLogin(blob)
  else confirmFaceEnroll(blob)
}

function skipFaceEnroll() {
  faceModal.open = false
  redirectAfterLogin()
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-intro">
      <div class="intro-inner">
        <div class="intro-brand">
          <span class="intro-mark"><Bot :size="22" /></span>
          <span>
            <strong>智能体模拟面试系统</strong>
            <small>AGENT INTERVIEW WORKSPACE</small>
          </span>
        </div>

        <h1>把一次面试，拆解成可量化的训练数据</h1>
        <p class="intro-copy">
          大模型连续追问、语音识别与合成、表情识别与注意力监测在同一条链路中运行，
          面试结束后输出问答得分、表情得分、注意力得分与最终成绩。
        </p>

        <ul class="intro-list">
          <li>
            <span><BrainCircuit :size="17" /></span>
            <div>
              <strong>大模型连续追问</strong>
              <small>基于上一轮回答自适应提问并生成评估报告</small>
            </div>
          </li>
          <li>
            <span><AudioLines :size="17" /></span>
            <div>
              <strong>语音问答闭环</strong>
              <small>Whisper 转写回答，Edge-TTS 朗读面试官问题</small>
            </div>
          </li>
          <li>
            <span><SmilePlus :size="17" /></span>
            <div>
              <strong>表情与注意力采样</strong>
              <small>浏览器实时画面 + 本地 MediaPipe 分析，不保存原始视频</small>
            </div>
          </li>
          <li>
            <span><Activity :size="17" /></span>
            <div>
              <strong>综合评分</strong>
              <small>问答占 80%，表情与注意力合并占 20%</small>
            </div>
          </li>
        </ul>

        <div v-if="capabilityList.length" class="capability-strip">
          <span
            v-for="item in capabilityList"
            :key="item.key"
            class="capability"
            :class="{ ready: item.ready }"
            :title="item.ready ? '服务可用' : '未检测到依赖或未配置'"
          >
            <i />
            {{ item.label }}
          </span>
        </div>
      </div>
    </section>

    <section class="auth-form-side">
      <div class="auth-card">
        <header class="auth-card-header">
          <span class="auth-icon">
            <ShieldCheck v-if="mode !== 'register'" :size="20" />
            <UserPlus v-else :size="20" />
          </span>
          <div>
            <h2>
              {{
                mode === 'password'
                  ? '账号登录'
                  : mode === 'face'
                    ? '人脸登录'
                    : '注册新账号'
              }}
            </h2>
            <p>
              {{
                mode === 'password'
                  ? '使用用户名和密码进入面试工作台'
                  : mode === 'face'
                    ? '调用摄像头比对人脸特征向量'
                    : '账号信息保存在本地，密码经 PBKDF2 加密'
              }}
            </p>
          </div>
        </header>

        <nav class="auth-tabs" aria-label="登录方式">
          <button
            type="button"
            :class="{ active: mode === 'password' }"
            @click="switchMode('password')"
          >
            <KeyRound :size="15" />
            密码登录
          </button>
          <button
            type="button"
            :class="{ active: mode === 'face' }"
            @click="switchMode('face')"
          >
            <ScanFace :size="15" />
            人脸登录
          </button>
          <button
            type="button"
            :class="{ active: mode === 'register' }"
            @click="switchMode('register')"
          >
            <UserPlus :size="15" />
            注册
          </button>
        </nav>

        <div v-if="error" class="alert alert-danger auth-alert">
          <AlertCircle :size="16" />
          <span>{{ error }}</span>
        </div>
        <div v-else-if="notice" class="alert alert-info auth-alert">
          <ScanFace :size="16" />
          <span>{{ notice }}</span>
        </div>

        <form v-if="mode === 'password'" class="auth-form" @submit.prevent="submitPasswordLogin">
          <label class="field">
            <span class="field-label">用户名</span>
            <input
              v-model="loginForm.username"
              type="text"
              autocomplete="username"
              placeholder="请输入用户名"
              :disabled="busy"
            />
          </label>
          <label class="field">
            <span class="field-label">密码</span>
            <span class="password-input">
              <input
                v-model="loginForm.password"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="current-password"
                placeholder="请输入密码"
                :disabled="busy"
              />
              <button
                class="password-toggle"
                type="button"
                :title="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                <EyeOff v-if="showPassword" :size="16" />
                <Eye v-else :size="16" />
              </button>
            </span>
          </label>
          <button class="button button-primary button-block" type="submit" :disabled="busy">
            <LoaderCircle v-if="busy" class="spin" :size="17" />
            <LogIn v-else :size="17" />
            进入面试工作台
          </button>
        </form>

        <div v-else-if="mode === 'face'" class="auth-face">
          <div class="face-visual">
            <ScanFace :size="46" />
            <span class="scan-line" />
          </div>
          <p>
            点击下方按钮打开摄像头，系统会提取 512 维人脸特征向量并与本地已注册人脸比对。
            原始人脸图片不会写入磁盘。
          </p>
          <button
            class="button button-primary button-block"
            type="button"
            :disabled="busy"
            @click="faceModal.open = true"
          >
            <LoaderCircle v-if="faceModal.loading" class="spin" :size="17" />
            <ScanFace v-else :size="17" />
            开启摄像头并识别
          </button>
        </div>

        <form v-else class="auth-form" @submit.prevent="submitRegister">
          <label class="field">
            <span class="field-label">用户名 <small>3-32 个字符</small></span>
            <input
              v-model="registerForm.username"
              type="text"
              autocomplete="username"
              placeholder="用于登录，不允许空格"
              :disabled="busy"
            />
          </label>
          <label class="field">
            <span class="field-label">显示名称 <small>选填</small></span>
            <input
              v-model="registerForm.display_name"
              type="text"
              placeholder="面试报告中显示的名字"
              :disabled="busy"
            />
          </label>
          <div class="field-grid">
            <label class="field">
              <span class="field-label">密码</span>
              <input
                v-model="registerForm.password"
                type="password"
                autocomplete="new-password"
                placeholder="至少 6 位"
                :disabled="busy"
              />
            </label>
            <label class="field">
              <span class="field-label">确认密码</span>
              <input
                v-model="registerForm.confirm"
                type="password"
                autocomplete="new-password"
                placeholder="再次输入"
                :disabled="busy"
              />
            </label>
          </div>
          <label class="checkbox-row">
            <input v-model="registerForm.enrollFace" type="checkbox" :disabled="busy" />
            <span>注册后录入人脸</span>
            <small>可用于刷脸登录</small>
          </label>
          <button class="button button-primary button-block" type="submit" :disabled="busy">
            <LoaderCircle v-if="busy" class="spin" :size="17" />
            <UserPlus v-else :size="17" />
            创建账号并继续
          </button>
        </form>
      </div>
    </section>

    <FaceCaptureModal
      :open="faceModal.open"
      :title="mode === 'face' ? '人脸登录' : '录入人脸'"
      :description="
        mode === 'face'
          ? '请正对摄像头，保持光线充足后拍摄照片。'
          : '拍摄一张正面清晰照片，用于后续刷脸登录。'
      "
      :loading="faceModal.loading"
      :confirm-text="mode === 'face' ? '开始识别' : '绑定人脸'"
      @close="faceModal.open = false"
      @confirm="onFaceModalConfirm"
    />

    <div v-if="mode === 'register' && auth.user && !auth.user.face_registered" class="skip-strip">
      <button type="button" @click="skipFaceEnroll">暂不录入人脸，直接进入系统</button>
    </div>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(420px, .95fr);
}

/* 左侧介绍 -------------------------------------------------------------- */

.auth-intro {
  position: relative;
  padding: 46px 52px;
  display: flex;
  align-items: center;
  overflow: hidden;
  color: #d6e6e6;
  background:
    radial-gradient(1200px 520px at 8% 4%, #16434a 0%, transparent 62%),
    #0d262e;
}

.intro-inner {
  position: relative;
  width: min(560px, 100%);
  margin: 0 auto;
}

.intro-brand {
  display: flex;
  align-items: center;
  gap: 11px;
}

.intro-mark {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: var(--radius);
  color: #0d262e;
  background: var(--teal-light);
}

.intro-brand strong,
.intro-brand small {
  display: block;
}

.intro-brand strong {
  color: #f2fbfa;
  font-size: 16px;
}

.intro-brand small {
  margin-top: 2px;
  color: #7fa3a6;
  font-family: var(--font-data);
  font-size: 9px;
  letter-spacing: .16em;
}

.auth-intro h1 {
  margin-top: 34px;
  color: #ffffff;
  font-size: clamp(26px, 2.6vw, 36px);
  line-height: 1.32;
}

.intro-copy {
  margin-top: 14px;
  color: #a9c2c4;
  font-size: 14px;
  line-height: 1.85;
}

.intro-list {
  margin: 30px 0 0;
  padding: 0;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  list-style: none;
}

.intro-list li {
  min-height: 92px;
  padding: 14px;
  display: flex;
  align-items: flex-start;
  gap: 11px;
  border: 1px solid rgb(255 255 255 / 9%);
  border-radius: var(--radius);
  background: rgb(255 255 255 / 4%);
}

.intro-list li > span {
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 6px;
  color: var(--teal-light);
  background: rgb(88 201 176 / 12%);
}

.intro-list strong {
  display: block;
  color: #eaf6f6;
  font-size: 13px;
}

.intro-list small {
  display: block;
  margin-top: 5px;
  color: #8fabae;
  font-size: 11px;
  line-height: 1.6;
}

.capability-strip {
  margin-top: 26px;
  padding-top: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 7px 14px;
  border-top: 1px solid rgb(255 255 255 / 9%);
}

.capability {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #82989b;
  font-size: 11px;
}

.capability i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #6d7c7f;
}

.capability.ready {
  color: #b9d6d4;
}

.capability.ready i {
  background: var(--teal-light);
  box-shadow: 0 0 0 3px rgb(88 201 176 / 18%);
}

/* 右侧表单 -------------------------------------------------------------- */

.auth-form-side {
  padding: 40px 32px;
  display: grid;
  place-items: center;
  background: var(--surface);
}

.auth-card {
  width: min(440px, 100%);
}

.auth-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.auth-icon {
  width: 44px;
  height: 44px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: var(--radius);
  color: var(--teal-dark);
  background: var(--teal-soft);
}

.auth-card-header h2 {
  font-size: 21px;
}

.auth-card-header p {
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
}

.auth-tabs {
  margin: 24px 0 20px;
  padding: 4px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 4px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface-soft);
}

.auth-tabs button {
  min-height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 0;
  border-radius: 6px;
  color: var(--muted-strong);
  background: transparent;
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
  transition: background .15s ease, color .15s ease, box-shadow .15s ease;
}

.auth-tabs button.active {
  color: var(--ink);
  background: var(--surface);
  box-shadow: var(--shadow-xs);
}

.auth-alert {
  margin-bottom: 16px;
}

.auth-form {
  display: block;
}

.auth-form .button {
  margin-top: 20px;
}

.password-input {
  position: relative;
  display: block;
}

.password-input input {
  padding-right: 44px;
}

.password-toggle {
  position: absolute;
  top: 50%;
  right: 8px;
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 5px;
  color: var(--muted);
  background: transparent;
  cursor: pointer;
}

.password-toggle:hover {
  color: var(--ink);
  background: var(--surface-sunken);
}

.field-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.field-grid .field + .field {
  margin-top: 0;
}

.field-grid + .checkbox-row {
  margin-top: 13px;
}

.auth-face {
  text-align: center;
}

.face-visual {
  position: relative;
  height: 168px;
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  color: var(--teal);
  background:
    repeating-linear-gradient(0deg, var(--line-soft) 0 1px, transparent 1px 26px),
    repeating-linear-gradient(90deg, var(--line-soft) 0 1px, transparent 1px 26px),
    var(--surface-soft);
}

.scan-line {
  position: absolute;
  right: 12%;
  left: 12%;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--teal), transparent);
  animation: scan 2.6s ease-in-out infinite;
}

@keyframes scan {
  0%,
  100% {
    top: 24%;
    opacity: .25;
  }
  50% {
    top: 76%;
    opacity: .9;
  }
}

.auth-face p {
  margin: 16px 0;
  color: var(--muted-strong);
  font-size: 12px;
  line-height: 1.75;
  text-align: left;
}

.skip-strip {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 90;
}

.skip-strip button {
  padding: 10px 16px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  color: var(--ink);
  background: var(--surface);
  box-shadow: var(--shadow-md);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

@media (max-width: 1080px) {
  .auth-page {
    grid-template-columns: 1fr;
  }

  .auth-intro {
    padding: 34px 28px 38px;
  }

  .intro-list {
    grid-template-columns: 1fr;
  }

  .auth-form-side {
    padding: 32px 24px 48px;
  }
}

@media (max-width: 520px) {
  .auth-intro {
    padding: 26px 20px 30px;
  }

  .auth-intro h1 {
    margin-top: 24px;
    font-size: 23px;
  }

  .intro-list {
    margin-top: 22px;
  }

  .auth-tabs button {
    font-size: 11px;
  }

  .field-grid {
    grid-template-columns: 1fr;
  }

  .field-grid .field + .field {
    margin-top: 13px;
  }

  .skip-strip {
    right: 12px;
    bottom: 78px;
    left: 12px;
  }
}
</style>
