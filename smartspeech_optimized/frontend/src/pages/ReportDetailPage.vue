<script setup>
import {
  Activity,
  AlertCircle,
  ArrowLeft,
  BadgeCheck,
  BrainCircuit,
  Clock3,
  Eye,
  Focus,
  Gauge,
  Lightbulb,
  LoaderCircle,
  MessageSquareText,
  Printer,
  ShieldCheck,
  Smile,
  Target,
  TrendingUp,
  Video,
} from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { apiRequest } from '../api/client'
import ScoreRing from '../components/ScoreRing.vue'
import { formatDateTime, formatDuration } from '../utils/media'

const route = useRoute()
const report = ref(null)
const loading = ref(true)
const error = ref('')

const evaluation = computed(() => report.value?.question_evaluation || {})
const fusion = computed(() => report.value?.score_fusion || {})
const video = computed(() => report.value?.video_summary || null)
const transcript = computed(() => report.value?.transcript || [])
const dimensions = computed(() => {
  const source = evaluation.value.dimensions || {}
  return Object.entries(source).map(([name, score]) => ({
    name,
    score: Number(score) || 0,
  }))
})
const events = computed(() => (video.value?.events || []).slice(0, 40))
const expressionDetail = computed(() => video.value?.expression || {})
const attentionDetail = computed(() => video.value?.attention || {})

const formulaText = computed(() => {
  const data = fusion.value
  if (!data.available || data.final_score == null) {
    return '有效视频样本不足，未生成最终得分。'
  }
  return `${score(data.question_score)} × 0.8 + (${score(data.expression_score)} × 0.5 + ${score(data.attention_score)} × 0.5) × 0.2 = ${score(data.final_score)}`
})

onMounted(load)

function printReport() {
  window.print()
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const payload = await apiRequest(`/api/reports/${route.params.id}`, {
      timeout: 30000,
    })
    report.value = payload.report
  } catch (cause) {
    error.value = cause.message
  } finally {
    loading.value = false
  }
}

function score(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(1) : '--'
}

function count(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

function eventLabel(type) {
  if (type === 'attention') return '注意力'
  if (type === 'expression') return '表情'
  return '人脸'
}

function eventTone(item) {
  const state = String(item.state || '')
  if (state === 'lost' || state === 'distracted') return 'warn'
  if (state === 'recovered' || state === 'attentive') return 'good'
  return 'neutral'
}

function alternativeText(item) {
  const raw = item.expression ?? item.state ?? ''
  const names = {
    neutral: '平静',
    happy: '微笑',
    smile: '微笑',
    laugh: '大笑',
    surprise: '惊讶',
    confused: '困惑',
    nervous: '紧张',
    attentive: '集中',
    distracted: '分散',
    lost: '离开画面',
    recovered: '回到画面',
  }
  return names[raw] || raw || '状态变化'
}
</script>

<template>
  <div class="page">
    <div v-if="loading" class="detail-loading">
      <LoaderCircle class="spin" :size="26" />
      <span>正在读取评估报告</span>
    </div>

    <div v-else-if="error" class="panel detail-error">
      <AlertCircle :size="24" />
      <h2>报告读取失败</h2>
      <p>{{ error }}</p>
      <RouterLink class="button button-secondary" :to="{ name: 'reports' }">
        <ArrowLeft :size="16" />
        返回报告列表
      </RouterLink>
    </div>

    <template v-else-if="report">
      <header class="detail-head">
        <div>
          <RouterLink class="back-link" :to="{ name: 'reports' }">
            <ArrowLeft :size="15" />
            返回报告列表
          </RouterLink>
          <h1>{{ report.job_role }}</h1>
          <div class="detail-tags">
            <span class="tag tag-positive">{{ evaluation.level || '已评估' }}</span>
            <span class="tag">{{ report.difficulty }}</span>
            <span class="tag">
              <Clock3 :size="13" />
              {{ formatDateTime(report.finished_at || report.created_at) }}
            </span>
            <span class="tag">
              <Video :size="13" />
              时长 {{ formatDuration(report.duration_seconds) }}
            </span>
          </div>
        </div>
        <button class="button button-secondary" type="button" @click="printReport">
          <Printer :size="16" />
          打印 / 导出 PDF
        </button>
      </header>

      <!-- 分数总览 -->
      <section class="score-overview panel">
        <div class="overview-main">
          <ScoreRing
            :score="fusion.final_score"
            :size="150"
            label="最终得分"
            :tone="fusion.available ? 'teal' : 'amber'"
          />
          <div class="overview-formula">
            <span class="overview-label">评分计算过程</span>
            <p class="data-value">{{ formulaText }}</p>
            <div
              class="overview-note"
              :class="fusion.available ? 'available' : 'unavailable'"
            >
              <ShieldCheck v-if="fusion.available" :size="15" />
              <AlertCircle v-else :size="15" />
              <span>{{ fusion.note }}</span>
            </div>
          </div>
        </div>

        <div class="overview-grid">
          <article class="score-tile primary">
            <span><MessageSquareText :size="16" />问答得分</span>
            <strong class="data-value">{{ score(fusion.question_score) }}</strong>
            <small>权重 80%</small>
          </article>
          <article class="score-tile">
            <span><Smile :size="16" />表情得分</span>
            <strong class="data-value">{{ score(fusion.expression_score) }}</strong>
            <small>占视频得分 50%</small>
          </article>
          <article class="score-tile">
            <span><Focus :size="16" />注意力得分</span>
            <strong class="data-value">{{ score(fusion.attention_score) }}</strong>
            <small>占视频得分 50%</small>
          </article>
          <article class="score-tile accent">
            <span><Gauge :size="16" />视频综合</span>
            <strong class="data-value">
              {{ score(fusion.expression_attention_score) }}
            </strong>
            <small>权重 20%</small>
          </article>
        </div>
      </section>

      <div class="detail-grid">
        <div class="detail-main">
          <!-- 评估总结 -->
          <section class="panel">
            <div class="panel-title">
              <span><BrainCircuit :size="17" /> 大模型综合评估</span>
              <small>基于问答文本</small>
            </div>
            <p class="summary-text">{{ evaluation.summary }}</p>

            <div v-if="dimensions.length" class="dimension-list">
              <div v-for="item in dimensions" :key="item.name" class="dimension">
                <div class="dimension-head">
                  <span>{{ item.name }}</span>
                  <strong class="data-value">{{ item.score.toFixed(0) }}</strong>
                </div>
                <div class="dimension-track">
                  <i :style="{ width: `${Math.max(0, Math.min(100, item.score))}%` }" />
                </div>
              </div>
            </div>

            <div class="feedback-grid">
              <div class="feedback-column positive">
                <span><BadgeCheck :size="15" />表现亮点</span>
                <ul>
                  <li v-for="item in evaluation.strengths || []" :key="item">{{ item }}</li>
                  <li v-if="!(evaluation.strengths || []).length">暂无记录</li>
                </ul>
              </div>
              <div class="feedback-column warning">
                <span><TrendingUp :size="15" />待改进项</span>
                <ul>
                  <li v-for="item in evaluation.improvements || []" :key="item">{{ item }}</li>
                  <li v-if="!(evaluation.improvements || []).length">暂无记录</li>
                </ul>
              </div>
            </div>

            <div v-if="(evaluation.next_steps || []).length" class="next-steps">
              <span><Lightbulb :size="15" />后续训练建议</span>
              <ol>
                <li v-for="item in evaluation.next_steps" :key="item">{{ item }}</li>
              </ol>
            </div>
          </section>

          <!-- 逐题复盘 -->
          <section v-if="(evaluation.question_reviews || []).length" class="panel">
            <div class="panel-title">
              <span><Target :size="17" /> 逐题复盘</span>
              <small>{{ evaluation.question_reviews.length }} 个问题</small>
            </div>
            <article
              v-for="(item, index) in evaluation.question_reviews"
              :key="index"
              class="review-item"
            >
              <span class="review-index">{{ String(index + 1).padStart(2, '0') }}</span>
              <div>
                <h3>{{ item.question || '问题待补充' }}</h3>
                <p><strong>回答表现：</strong>{{ item.assessment || '--' }}</p>
                <p><strong>改进建议：</strong>{{ item.suggestion || '--' }}</p>
              </div>
            </article>
          </section>

          <!-- 面试记录 -->
          <section class="panel">
            <div class="panel-title">
              <span><MessageSquareText :size="17" /> 完整面试记录</span>
              <small>{{ transcript.length }} 条消息</small>
            </div>
            <div class="transcript">
              <article
                v-for="item in transcript"
                :key="item.id"
                class="transcript-item"
                :class="item.role"
              >
                <span>{{ item.role === 'assistant' ? '面试官' : '候选人' }}</span>
                <p>{{ item.content }}</p>
              </article>
            </div>
          </section>
        </div>

        <aside class="detail-side">
          <!-- 视频指标 -->
          <section class="panel">
            <div class="panel-title">
              <span><Video :size="17" /> 表情与注意力采样</span>
            </div>
            <div v-if="video" class="video-counters">
              <div>
                <strong class="data-value">{{ count(video.processed_frames) }}</strong>
                <span>分析帧数</span>
              </div>
              <div>
                <strong class="data-value">{{ count(video.face_frames) }}</strong>
                <span>含人脸帧</span>
              </div>
              <div>
                <strong class="data-value">
                  {{ Number(video.face_detection_rate || 0).toFixed(1) }}%
                </strong>
                <span>人脸覆盖</span>
              </div>
              <div>
                <strong class="data-value">{{ count(video.event_count) }}</strong>
                <span>状态变化</span>
              </div>
              <div>
                <strong class="data-value">
                  {{ count(video.missing_face_episodes) }}
                </strong>
                <span>离开画面次数</span>
              </div>
              <div>
                <strong class="data-value">
                  {{ Number(video.missing_face_duration_seconds || 0).toFixed(1) }}s
                </strong>
                <span>离帧总时长</span>
              </div>
            </div>
            <p v-else class="quiet-note">
              本轮未采集到有效视频样本，可能是摄像头未开启或作答时间过短。
            </p>

            <div v-if="video" class="detail-pairs">
              <div>
                <span>表情变化次数</span>
                <strong class="data-value">
                  {{ count(expressionDetail.change_count) }}
                </strong>
              </div>
              <div>
                <span>眨眼次数</span>
                <strong class="data-value">{{ count(expressionDetail.blink_count) }}</strong>
              </div>
              <div>
                <span>注意力变化</span>
                <strong class="data-value">
                  {{ count(attentionDetail.state_change_count) }}
                </strong>
              </div>
              <div>
                <span>分散次数</span>
                <strong class="data-value">
                  {{ count(attentionDetail.distraction_count) }}
                </strong>
              </div>
              <div>
                <span>视线居中比例</span>
                <strong class="data-value">
                  {{ Number(attentionDetail.attention_rate || 0).toFixed(0) }}%
                </strong>
              </div>
              <div>
                <span>有效分析帧</span>
                <strong class="data-value">
                  {{ count(expressionDetail.analyzed_frames) }}
                </strong>
              </div>
            </div>
          </section>

          <!-- 状态变化时间线 -->
          <section v-if="events.length" class="panel">
            <div class="panel-title">
              <span><Activity :size="17" /> 状态变化时间线</span>
              <small>最近 {{ events.length }} 条</small>
            </div>
            <ol class="timeline">
              <li v-for="(item, index) in events" :key="index">
                <span class="timeline-time data-value">
                  {{ Number(item.elapsed_seconds || 0).toFixed(1) }}s
                </span>
                <span class="timeline-dot" :class="eventTone(item)" />
                <div>
                  <strong>{{ eventLabel(item.source) }} · {{ alternativeText(item) }}</strong>
                  <small>{{ item.detail || '状态发生变化' }}</small>
                </div>
              </li>
            </ol>
          </section>

          <!-- 隐私说明 -->
          <section class="panel privacy-panel">
            <div class="panel-title">
              <span><Eye :size="17" /> 隐私与数据</span>
            </div>
            <ul>
              <li>原始视频帧：不保存</li>
              <li>人脸照片：不保存</li>
              <li>回答录音：转写后立即删除</li>
              <li>报告内容：仅保存指标、状态变化与评分</li>
            </ul>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail-loading {
  min-height: 400px;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 10px;
  color: var(--muted);
  font-size: 13px;
}

.detail-error {
  min-height: 320px;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 10px;
  text-align: center;
}

.detail-error svg {
  color: var(--danger);
}

.detail-error p {
  color: var(--muted);
  font-size: 13px;
}

.detail-error .button {
  margin-top: 8px;
  text-decoration: none;
}

.detail-head {
  margin-bottom: 16px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--muted-strong);
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}

.back-link:hover {
  color: var(--teal-dark);
}

.detail-head h1 {
  margin-top: 10px;
  font-size: 26px;
}

.detail-tags {
  margin-top: 11px;
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.score-overview {
  margin-bottom: 16px;
}

.overview-main {
  display: flex;
  align-items: center;
  gap: 24px;
}

.overview-formula {
  min-width: 0;
  flex: 1;
}

.overview-label {
  color: var(--muted);
  font-size: 11px;
  font-weight: 700;
}

.overview-formula p {
  margin-top: 7px;
  padding: 12px 14px;
  border: 1px solid #bfe0da;
  border-radius: var(--radius);
  color: var(--teal-dark);
  background: var(--teal-soft);
  font-size: 13px;
  line-height: 1.7;
  word-break: break-word;
}

.overview-note {
  margin-top: 9px;
  display: flex;
  align-items: flex-start;
  gap: 7px;
  font-size: 11px;
  line-height: 1.6;
}

.overview-note.available {
  color: var(--teal-dark);
}

.overview-note.unavailable {
  color: #8a5c14;
}

.overview-grid {
  margin-top: 18px;
  padding-top: 16px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  border-top: 1px solid var(--line-soft);
}

.score-tile {
  padding: 12px 13px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius);
  background: var(--surface-soft);
}

.score-tile span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--muted);
  font-size: 11px;
}

.score-tile strong {
  display: block;
  margin-top: 6px;
  color: var(--ink);
  font-size: 24px;
}

.score-tile small {
  display: block;
  margin-top: 3px;
  color: var(--muted);
  font-size: 10px;
}

.score-tile.primary {
  border-color: #bfe0da;
  background: var(--teal-soft);
}

.score-tile.primary strong {
  color: var(--teal-dark);
}

.score-tile.accent {
  border-color: #eccfbf;
  background: var(--coral-soft);
}

.score-tile.accent strong {
  color: #95523d;
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(300px, 360px);
  gap: 16px;
  align-items: start;
}

.detail-main,
.detail-side {
  display: grid;
  gap: 16px;
}

.summary-text {
  color: var(--ink-soft);
  font-size: 13px;
  line-height: 1.85;
}

.dimension-list {
  margin-top: 16px;
  display: grid;
  gap: 11px;
}

.dimension-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.dimension-head span {
  color: var(--muted-strong);
  font-size: 12px;
}

.dimension-head strong {
  color: var(--ink);
  font-size: 14px;
}

.dimension-track {
  height: 7px;
  margin-top: 6px;
  overflow: hidden;
  border-radius: 4px;
  background: var(--surface-sunken);
}

.dimension-track i {
  display: block;
  height: 100%;
  border-radius: 4px;
  background: var(--teal);
  transition: width .5s ease;
}

.feedback-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.feedback-column {
  padding: 13px;
  border: 1px solid var(--line-soft);
  border-radius: var(--radius);
}

.feedback-column > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 800;
}

.feedback-column.positive {
  background: var(--teal-soft);
}

.feedback-column.positive > span {
  color: var(--teal-dark);
}

.feedback-column.warning {
  background: var(--amber-soft);
}

.feedback-column.warning > span {
  color: #8a5c14;
}

.feedback-column ul {
  margin: 9px 0 0;
  padding-left: 16px;
}

.feedback-column li {
  margin-bottom: 6px;
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.65;
}

.next-steps {
  margin-top: 12px;
  padding: 13px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface-soft);
}

.next-steps > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--ink);
  font-size: 12px;
  font-weight: 800;
}

.next-steps ol {
  margin: 9px 0 0;
  padding-left: 18px;
}

.next-steps li {
  margin-bottom: 5px;
  color: var(--muted-strong);
  font-size: 12px;
  line-height: 1.65;
}

.review-item {
  padding: 13px 0;
  display: flex;
  gap: 13px;
  border-bottom: 1px solid var(--line-soft);
}

.review-item:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}

.review-index {
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  border-radius: 6px;
  color: var(--teal-dark);
  background: var(--teal-soft);
  font-family: var(--font-data);
  font-size: 12px;
  font-weight: 800;
}

.review-item h3 {
  font-size: 13px;
  line-height: 1.6;
}

.review-item p {
  margin-top: 6px;
  color: var(--muted-strong);
  font-size: 12px;
  line-height: 1.7;
}

.review-item strong {
  color: var(--ink);
}

.transcript {
  max-height: 520px;
  padding-right: 4px;
  overflow-y: auto;
}

.transcript-item {
  padding: 11px 0;
  border-bottom: 1px solid var(--line-soft);
}

.transcript-item:last-child {
  border-bottom: 0;
}

.transcript-item span {
  display: inline-block;
  margin-bottom: 5px;
  padding: 2px 8px;
  border-radius: 5px;
  font-size: 10px;
  font-weight: 800;
}

.transcript-item.assistant span {
  color: var(--teal-dark);
  background: var(--teal-soft);
}

.transcript-item.user span {
  color: #95523d;
  background: var(--coral-soft);
}

.transcript-item p {
  color: var(--ink-soft);
  font-size: 12px;
  line-height: 1.75;
  white-space: pre-wrap;
}

.video-counters {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.video-counters div {
  min-height: 62px;
  padding: 10px;
  border: 1px solid var(--line-soft);
  border-radius: 6px;
  background: var(--surface-soft);
}

.video-counters strong {
  display: block;
  color: var(--ink);
  font-size: 19px;
}

.video-counters span {
  display: block;
  margin-top: 4px;
  color: var(--muted);
  font-size: 10px;
}

.detail-pairs {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--line-soft);
}

.detail-pairs div {
  min-height: 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-bottom: 1px solid var(--line-soft);
}

.detail-pairs div:last-child {
  border-bottom: 0;
}

.detail-pairs span {
  color: var(--muted-strong);
  font-size: 11px;
}

.detail-pairs strong {
  color: var(--ink);
  font-size: 13px;
}

.quiet-note {
  color: var(--muted);
  font-size: 11px;
  line-height: 1.7;
}

.timeline {
  margin: 0;
  padding: 0;
  max-height: 340px;
  overflow-y: auto;
  list-style: none;
}

.timeline li {
  position: relative;
  padding: 0 0 13px 20px;
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.timeline li::before {
  position: absolute;
  top: 12px;
  bottom: 0;
  left: 5px;
  width: 1px;
  content: '';
  background: var(--line);
}

.timeline li:last-child::before {
  display: none;
}

.timeline-time {
  min-width: 46px;
  color: var(--muted);
  font-size: 10px;
}

.timeline-dot {
  position: absolute;
  top: 5px;
  left: 1px;
  width: 9px;
  height: 9px;
  border: 2px solid var(--surface);
  border-radius: 50%;
  background: var(--muted);
}

.timeline-dot.good {
  background: var(--teal);
}

.timeline-dot.warn {
  background: var(--coral);
}

.timeline strong {
  display: block;
  color: var(--ink);
  font-size: 11px;
}

.timeline small {
  display: block;
  margin-top: 3px;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.55;
}

.privacy-panel ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.privacy-panel li {
  padding: 7px 0;
  border-bottom: 1px solid var(--line-soft);
  color: var(--muted-strong);
  font-size: 11px;
}

.privacy-panel li:last-child {
  border-bottom: 0;
}

@media (max-width: 1080px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .overview-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .detail-head {
    flex-direction: column;
    align-items: stretch;
  }

  .overview-main {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .feedback-grid {
    grid-template-columns: 1fr;
  }

  .detail-head h1 {
    font-size: 22px;
  }
}

@media print {
  .detail-head .button,
  .back-link {
    display: none;
  }
}
</style>
