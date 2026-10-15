<script setup>
import {
  Activity,
  BrainCircuit,
  Eye,
  Focus,
  Gauge,
  ScanFace,
  Smile,
  TimerReset,
} from 'lucide-vue-next'
import { computed } from 'vue'
import MiniSparkline from './MiniSparkline.vue'
import ScoreRing from './ScoreRing.vue'

const props = defineProps({
  metrics: { type: Object, default: () => ({}) },
  vision: { type: Object, default: () => ({}) },
})

const expressionScore = computed(() => numberOrNull(props.metrics.expression_score))
const attentionScore = computed(() => numberOrNull(props.metrics.attention_score))
const videoScore = computed(() => {
  if (expressionScore.value === null || attentionScore.value === null) return null
  return Number(((expressionScore.value + attentionScore.value) / 2).toFixed(1))
})

const expressionTrend = computed(() =>
  (props.metrics.trend || [])
    .map((item) => numberOrNull(item.expression_score))
)
const attentionTrend = computed(() =>
  (props.metrics.trend || [])
    .map((item) => numberOrNull(item.attention_score))
)

const expressionLabel = computed(() => {
  if (!props.metrics.face_detected) return '等待人脸'
  return props.metrics.expression || '分析中'
})

const attentionLabel = computed(() => {
  if (props.metrics.attention_state === 'attentive') return '集中'
  if (props.metrics.attention_state === 'distracted') return '分散'
  return '校准中'
})

const issues = computed(() => props.metrics.issues || [])
const visionLabel = computed(() => {
  if (props.vision.state === 'degraded') return '分析降级'
  if (props.vision.processing) return '分析中'
  if (props.vision.pending) return '已接收'
  if (Number(props.vision.processed_frames) > 0) return '稳定运行'
  return '准备中'
})
const visionLatency = computed(() => {
  const latency = Number(props.vision.last_latency_ms)
  return Number.isFinite(latency) && latency > 0 ? `${Math.round(latency)} ms` : '--'
})
const visionFps = computed(() => {
  const fps = Number(props.vision.effective_fps)
  return Number.isFinite(fps) && fps > 0 ? fps.toFixed(1) : '--'
})

function numberOrNull(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number : null
}

function scoreText(value) {
  return value === null ? '--' : value.toFixed(1)
}

function signed(value, suffix = '°') {
  const number = Number(value)
  if (!Number.isFinite(number)) return '--'
  return `${number > 0 ? '+' : ''}${number.toFixed(1)}${suffix}`
}
</script>

<template>
  <aside class="metrics-panel">
    <section class="panel-section">
      <div class="section-heading">
        <span>
          <BrainCircuit :size="17" />
          当前状态
        </span>
        <span
          class="state-pill"
          :class="metrics.attention_state === 'attentive' ? 'positive' : 'neutral'"
        >
          {{ attentionLabel }}
        </span>
      </div>

      <div class="state-grid">
        <div class="state-item">
          <span><ScanFace :size="16" />人脸检测</span>
          <strong>{{ metrics.face_detected ? '正常' : '未检测到' }}</strong>
        </div>
        <div class="state-item">
          <span><Smile :size="16" />表情识别</span>
          <strong>{{ expressionLabel }}</strong>
        </div>
        <div class="state-item">
          <span><Eye :size="16" />视线中心</span>
          <strong>{{ metrics.looking_center ? '是' : '否' }}</strong>
        </div>
        <div class="state-item">
          <span><Activity :size="16" />画面覆盖</span>
          <strong>{{ Number(metrics.face_detection_rate || 0).toFixed(0) }}%</strong>
        </div>
        <div class="state-item">
          <span><Activity :size="16" />分析链路</span>
          <strong>{{ visionLabel }}</strong>
        </div>
        <div class="state-item">
          <span><Activity :size="16" />采样 / 延迟</span>
          <strong>{{ visionFps }} FPS · {{ visionLatency }}</strong>
        </div>
      </div>

      <div v-if="issues.length" class="issue-list">
        <span v-for="issue in issues" :key="issue">{{ issue }}</span>
      </div>
      <p v-else class="quiet-note">当前未检测到明显注意力异常。</p>
    </section>

    <section class="panel-section video-score">
      <div class="section-heading">
        <span>
          <Gauge :size="17" />
          视频状态得分
        </span>
        <small>实时</small>
      </div>
      <div class="score-layout">
        <ScoreRing :score="videoScore" label="视频综合" :size="112" />
        <div class="score-details">
          <div class="score-line">
            <span><Smile :size="15" />表情</span>
            <strong>{{ scoreText(expressionScore) }}</strong>
          </div>
          <div class="score-line">
            <span><Focus :size="15" />注意力</span>
            <strong>{{ scoreText(attentionScore) }}</strong>
          </div>
          <p>最终报告中，视频得分占总成绩 20%。</p>
        </div>
      </div>
    </section>

    <section class="panel-section">
      <div class="section-heading">
        <span>
          <Activity :size="17" />
          评分趋势
        </span>
        <small>实时更新</small>
      </div>

      <div class="trend-block">
        <div class="trend-copy">
          <span>表情得分</span>
          <strong>{{ scoreText(expressionScore) }}</strong>
        </div>
        <MiniSparkline :values="expressionTrend" color="#d26d4d" />
      </div>

      <div class="trend-block">
        <div class="trend-copy">
          <span>注意力得分</span>
          <strong>{{ scoreText(attentionScore) }}</strong>
        </div>
        <MiniSparkline :values="attentionTrend" color="#0d7a72" />
      </div>
    </section>

    <section class="panel-section">
      <div class="section-heading">
        <span>
          <TimerReset :size="17" />
          动态变化
        </span>
      </div>
      <div class="counter-grid">
        <div>
          <strong>{{ metrics.expression_changes ?? 0 }}</strong>
          <span>表情变化</span>
        </div>
        <div>
          <strong>{{ metrics.attention_state_changes ?? 0 }}</strong>
          <span>注意力变化</span>
        </div>
        <div>
          <strong>{{ metrics.distraction_count ?? 0 }}</strong>
          <span>分散次数</span>
        </div>
        <div>
          <strong>{{ metrics.blink_count ?? 0 }}</strong>
          <span>眨眼次数</span>
        </div>
      </div>

      <div class="pose-strip">
        <span>俯仰 {{ signed(metrics.pitch) }}</span>
        <span>偏转 {{ signed(metrics.yaw) }}</span>
        <span>倾斜 {{ signed(metrics.roll) }}</span>
      </div>
    </section>
  </aside>
</template>

<style scoped>
.metrics-panel {
  display: grid;
  gap: 12px;
}

.panel-section {
  padding: 15px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--surface);
  box-shadow: var(--shadow-xs);
}

.section-heading {
  min-height: 25px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.section-heading > span:first-child {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--ink);
  font-size: 13px;
  font-weight: 800;
}

.section-heading > span:first-child svg {
  color: var(--teal);
}

.section-heading small {
  color: var(--muted);
  font-size: 10px;
}

.score-layout {
  margin-top: 14px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.score-details {
  min-width: 0;
  flex: 1;
}

.score-line {
  min-height: 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  border-bottom: 1px solid var(--line-soft);
}

.score-line span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--muted-strong);
  font-size: 12px;
}

.score-line strong {
  color: var(--ink);
  font-family: var(--font-data);
  font-size: 17px;
}

.score-details p {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.5;
}

.state-pill {
  padding: 4px 8px;
  border-radius: 5px;
  color: var(--muted-strong);
  background: var(--surface-soft);
  font-size: 10px;
  font-weight: 800;
}

.state-pill.positive {
  color: var(--teal-dark);
  background: var(--teal-soft);
}

.state-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 7px;
}

.state-item {
  min-height: 60px;
  padding: 9px;
  border: 1px solid var(--line-soft);
  border-radius: 6px;
  background: var(--surface-soft);
}

.state-item span {
  display: flex;
  align-items: center;
  gap: 5px;
  color: var(--muted);
  font-size: 10px;
}

.state-item strong {
  display: block;
  margin-top: 7px;
  overflow: hidden;
  color: var(--ink);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.issue-list {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 5px;
}

.issue-list span {
  padding: 4px 7px;
  border-radius: 5px;
  color: #9b4c36;
  background: var(--coral-soft);
  font-size: 10px;
}

.quiet-note {
  margin: 10px 0 0;
  color: var(--muted);
  font-size: 10px;
}

.trend-block + .trend-block {
  margin-top: 8px;
  padding-top: 11px;
  border-top: 1px solid var(--line-soft);
}

.trend-copy {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.trend-copy span {
  color: var(--muted-strong);
  font-size: 11px;
}

.trend-copy strong {
  color: var(--ink);
  font-family: var(--font-data);
  font-size: 14px;
}

.counter-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 7px;
}

.counter-grid div {
  min-height: 58px;
  padding: 9px;
  border-radius: 6px;
  background: var(--surface-soft);
}

.counter-grid strong,
.counter-grid span {
  display: block;
}

.counter-grid strong {
  color: var(--ink);
  font-family: var(--font-data);
  font-size: 20px;
}

.counter-grid span {
  margin-top: 4px;
  color: var(--muted);
  font-size: 10px;
}

.pose-strip {
  margin-top: 9px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 5px;
}

.pose-strip span {
  padding: 6px 3px;
  overflow: hidden;
  border: 1px solid var(--line-soft);
  border-radius: 5px;
  color: var(--muted-strong);
  font-family: var(--font-data);
  font-size: 9px;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1280px) {
  .score-layout {
    align-items: flex-start;
  }
}

@media (max-width: 640px) {
  .state-grid,
  .counter-grid {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
