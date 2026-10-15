<script setup>
import { computed } from 'vue'

/**
 * 面部表情与注意力指标面板。
 * 信息结构与 smartspeech_optimized/frontend/src/components/LiveMetricsPanel.vue 保持一致：
 * 当前状态、注意力 issues、视频状态得分、评分趋势、动态变化与头部姿态；
 * 额外展示视觉线程错误信息，便于联调阶段定位本地模型故障。
 */
const props = defineProps({
  metrics: { type: Object, default: () => ({}) },
  vision: { type: Object, default: () => ({}) }
})

const expressionScore = computed(() => numberOrNull(props.metrics.expression_score))
const attentionScore = computed(() => numberOrNull(props.metrics.attention_score))
const videoScore = computed(() => {
  if (expressionScore.value === null || attentionScore.value === null) return null
  return Number(((expressionScore.value + attentionScore.value) / 2).toFixed(1))
})

const expressionLabel = computed(() => {
  if (!props.metrics.face_detected) return '等待人脸'
  return props.metrics.expression || '分析中'
})

const attentionLabel = computed(() => {
  if (props.metrics.attention_state === 'attentive') return '集中'
  if (props.metrics.attention_state === 'distracted') return '分散'
  return '校准中'
})

const attentionPositive = computed(() => props.metrics.attention_state === 'attentive')
const issues = computed(() => props.metrics.issues || [])

const expressionTrend = computed(() =>
  (props.metrics.trend || []).map((item) => numberOrNull(item.expression_score)))
const attentionTrend = computed(() =>
  (props.metrics.trend || []).map((item) => numberOrNull(item.attention_score)))

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
const visionError = computed(() => String(props.vision.last_error || '').trim())

function numberOrNull(value) {
  if (value === null || value === undefined || value === '') return null
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

function sparkPoints(values) {
  const points = values
    .map((value, index) => ({ value, index }))
    .filter((point) => point.value !== null)
  if (points.length < 2) return ''
  const maxIndex = Math.max(values.length - 1, 1)
  return points
    .map((point) => {
      const x = (point.index / maxIndex) * 100
      const y = 28 - (Math.max(0, Math.min(100, point.value)) / 100) * 26
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
}
</script>

<template>
  <div class="vision-metrics">
    <div class="vm-section">
      <div class="vm-heading">
        <span>当前状态</span>
        <span class="state-pill" :class="{ positive: attentionPositive }">{{ attentionLabel }}</span>
      </div>
      <div class="state-grid">
        <div class="state-item">
          <span>人脸检测</span>
          <strong>{{ metrics.face_detected ? '正常' : '未检测到' }}</strong>
        </div>
        <div class="state-item">
          <span>表情识别</span>
          <strong>{{ expressionLabel }}</strong>
        </div>
        <div class="state-item">
          <span>视线中心</span>
          <strong>{{ metrics.looking_center ? '是' : '否' }}</strong>
        </div>
        <div class="state-item">
          <span>画面覆盖</span>
          <strong>{{ Number(metrics.face_detection_rate || 0).toFixed(0) }}%</strong>
        </div>
        <div class="state-item">
          <span>分析链路</span>
          <strong>{{ visionLabel }}</strong>
        </div>
        <div class="state-item">
          <span>采样 / 延迟</span>
          <strong>{{ visionFps }} FPS · {{ visionLatency }}</strong>
        </div>
      </div>
      <div v-if="issues.length" class="issue-list">
        <span v-for="issue in issues" :key="issue">{{ issue }}</span>
      </div>
      <p v-else class="quiet-note">当前未检测到明显注意力异常。</p>
      <p v-if="visionError" class="vision-error">视觉线程异常：{{ visionError }}</p>
    </div>

    <div class="vm-section">
      <div class="vm-heading">
        <span>视频状态得分</span>
        <small>实时</small>
      </div>
      <div class="score-row">
        <div class="score-cell">
          <span>表情</span>
          <strong>{{ scoreText(expressionScore) }}</strong>
        </div>
        <div class="score-cell">
          <span>注意力</span>
          <strong>{{ scoreText(attentionScore) }}</strong>
        </div>
        <div class="score-cell accent">
          <span>视频综合</span>
          <strong>{{ scoreText(videoScore) }}</strong>
        </div>
      </div>
    </div>

    <div class="vm-section">
      <div class="vm-heading">
        <span>评分趋势</span>
        <small>实时更新</small>
      </div>
      <div class="trend-row">
        <div class="trend-copy">
          <span>表情得分</span>
          <strong>{{ scoreText(expressionScore) }}</strong>
        </div>
        <svg class="sparkline" viewBox="0 0 100 30" preserveAspectRatio="none" aria-hidden="true">
          <polyline
            v-if="sparkPoints(expressionTrend)"
            :points="sparkPoints(expressionTrend)"
            fill="none"
            stroke="#d26d4d"
            stroke-width="1.5"
            vector-effect="non-scaling-stroke"
          />
        </svg>
      </div>
      <div class="trend-row">
        <div class="trend-copy">
          <span>注意力得分</span>
          <strong>{{ scoreText(attentionScore) }}</strong>
        </div>
        <svg class="sparkline" viewBox="0 0 100 30" preserveAspectRatio="none" aria-hidden="true">
          <polyline
            v-if="sparkPoints(attentionTrend)"
            :points="sparkPoints(attentionTrend)"
            fill="none"
            stroke="#0d7a72"
            stroke-width="1.5"
            vector-effect="non-scaling-stroke"
          />
        </svg>
      </div>
    </div>

    <div class="vm-section">
      <div class="vm-heading"><span>动态变化</span></div>
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
    </div>
  </div>
</template>

<style scoped>
.vision-metrics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.vm-section {
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafbfc;
}

.vm-heading {
  min-height: 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.vm-heading small {
  color: #909399;
  font-size: 11px;
  font-weight: 400;
}

.state-pill {
  padding: 2px 8px;
  border-radius: 5px;
  background: #ebeef5;
  color: #606266;
  font-size: 11px;
  font-weight: 600;
}

.state-pill.positive {
  background: #e6f6f1;
  color: #0d7a72;
}

.state-grid {
  margin-top: 10px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.state-item {
  padding: 8px 10px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
}

.state-item span {
  display: block;
  color: #909399;
  font-size: 11px;
}

.state-item strong {
  display: block;
  margin-top: 5px;
  overflow: hidden;
  color: #303133;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.issue-list {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.issue-list span {
  padding: 3px 8px;
  border-radius: 5px;
  background: #fdeee8;
  color: #9b4c36;
  font-size: 11px;
}

.quiet-note {
  margin: 10px 0 0;
  color: #909399;
  font-size: 11px;
}

.vision-error {
  margin: 8px 0 0;
  color: #c0392b;
  font-size: 11px;
  word-break: break-all;
}

.score-row {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.score-cell {
  padding: 10px 8px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
  text-align: center;
}

.score-cell span {
  display: block;
  color: #909399;
  font-size: 11px;
}

.score-cell strong {
  display: block;
  margin-top: 5px;
  color: #303133;
  font-size: 18px;
}

.score-cell.accent strong {
  color: #0d7a72;
}

.trend-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.trend-copy {
  flex: 0 0 96px;
  display: flex;
  flex-direction: column;
}

.trend-copy span {
  color: #909399;
  font-size: 11px;
}

.trend-copy strong {
  margin-top: 3px;
  color: #303133;
  font-size: 14px;
}

.sparkline {
  flex: 1;
  height: 34px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 5px;
}

.counter-grid {
  margin-top: 10px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

.counter-grid div {
  padding: 9px 10px;
  border-radius: 6px;
  background: #fff;
  border: 1px solid #ebeef5;
}

.counter-grid strong,
.counter-grid span {
  display: block;
}

.counter-grid strong {
  color: #303133;
  font-size: 18px;
}

.counter-grid span {
  margin-top: 3px;
  color: #909399;
  font-size: 11px;
}

.pose-strip {
  margin-top: 9px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px;
}

.pose-strip span {
  padding: 6px 4px;
  border: 1px solid #ebeef5;
  border-radius: 5px;
  background: #fff;
  color: #606266;
  font-size: 11px;
  text-align: center;
}

@media (max-width: 820px) {
  .vision-metrics {
    grid-template-columns: 1fr;
  }
}
</style>