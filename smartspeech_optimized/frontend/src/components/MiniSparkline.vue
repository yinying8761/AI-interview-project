<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({
  values: { type: Array, default: () => [] },
  color: { type: String, default: '#0d7a72' },
  height: { type: Number, default: 54 },
  maxPoints: { type: Number, default: 60 },
})

const CHART_WIDTH = 180
const PLOT_LEFT = 4
const PLOT_RIGHT = 176
const renderedValues = ref([])

let animationFrame = 0
let animationStartedAt = 0
let animationFrom = []
let animationTo = []

const chartHeight = computed(() => Math.max(36, Number(props.height) || 54))
const pointLimit = computed(() => Math.max(2, Math.floor(Number(props.maxPoints) || 60)))
const points = computed(() => {
  const values = fillMissing(renderedValues.value)
  if (!values.length) return []

  const top = 5
  const bottom = chartHeight.value - 5
  const step = (PLOT_RIGHT - PLOT_LEFT) / Math.max(1, pointLimit.value - 1)

  return values.flatMap((value, index) => {
    if (!Number.isFinite(value)) return []
    return [{
      x: PLOT_LEFT + index * step,
      y: bottom - (clamp(value) / 100) * (bottom - top),
    }]
  })
})
const linePath = computed(() => buildSmoothPath(points.value))
const areaPath = computed(() => {
  if (points.value.length < 2) return ''
  const first = points.value[0]
  const last = points.value[points.value.length - 1]
  const bottom = chartHeight.value - 1
  return `${linePath.value} L ${format(last.x)} ${bottom} L ${format(first.x)} ${bottom} Z`
})
const endPoint = computed(() => points.value[points.value.length - 1] || null)

watch(
  () => props.values,
  (values) => animateTo(normalizeValues(values)),
  { immediate: true },
)

onBeforeUnmount(() => {
  if (animationFrame) window.cancelAnimationFrame(animationFrame)
})

function animateTo(nextValues) {
  const target = toFixedFrame(nextValues)
  if (!target.some((value) => value !== null)) {
    renderedValues.value = target
    return
  }
  if (!renderedValues.value.length || sameValues(renderedValues.value, target)) {
    renderedValues.value = target
    return
  }

  if (animationFrame) window.cancelAnimationFrame(animationFrame)
  animationFrom = renderedValues.value
  animationTo = target
  animationStartedAt = performance.now()

  const tick = (now) => {
    const progress = Math.min(1, (now - animationStartedAt) / 320)
    const eased = progress * progress * (3 - 2 * progress)
    renderedValues.value = animationTo.map((targetValue, index) => {
      const sourceValue = animationFrom[index]
      if (sourceValue === null) return targetValue
      if (targetValue === null) return sourceValue
      return sourceValue + (targetValue - sourceValue) * eased
    })

    if (progress < 1) {
      animationFrame = window.requestAnimationFrame(tick)
    } else {
      animationFrame = 0
      renderedValues.value = animationTo
    }
  }

  animationFrame = window.requestAnimationFrame(tick)
}

function normalizeValues(values) {
  return (Array.isArray(values) ? values : []).map((value) => {
    if (value === null || value === undefined || value === '') return null
    const number = Number(value)
    return Number.isFinite(number) ? clamp(number) : null
  })
}

function toFixedFrame(values) {
  const frame = Array(pointLimit.value).fill(null)
  const visibleValues = values.slice(-pointLimit.value)
  visibleValues.forEach((value, index) => {
    frame[index] = value
  })
  return frame
}

function fillMissing(values) {
  const filled = [...values]
  const firstValue = filled.findIndex((value) => Number.isFinite(value))
  const lastValue = filled.findLastIndex((value) => Number.isFinite(value))
  if (firstValue < 0) return filled

  let previous = null
  for (let index = firstValue; index <= lastValue; index += 1) {
    const value = filled[index]
    if (value === null) {
      if (previous !== null) filled[index] = previous
      continue
    }
    previous = value
  }
  return filled
}

function sameValues(left, right) {
  return left.length === right.length
    && left.every((value, index) => value === right[index])
}

function buildSmoothPath(entries) {
  if (!entries.length) return ''
  if (entries.length === 1) {
    return `M ${format(entries[0].x)} ${format(entries[0].y)}`
  }

  const top = 5
  const bottom = chartHeight.value - 5
  let path = `M ${format(entries[0].x)} ${format(entries[0].y)}`

  for (let index = 0; index < entries.length - 1; index += 1) {
    const previous = entries[index - 1] || entries[index]
    const current = entries[index]
    const next = entries[index + 1]
    const following = entries[index + 2] || next
    const control1X = current.x + (next.x - previous.x) / 6
    const control1Y = clamp(current.y + (next.y - previous.y) / 6, top, bottom)
    const control2X = next.x - (following.x - current.x) / 6
    const control2Y = clamp(next.y - (following.y - current.y) / 6, top, bottom)

    path += ` C ${format(control1X)} ${format(control1Y)},`
      + ` ${format(control2X)} ${format(control2Y)},`
      + ` ${format(next.x)} ${format(next.y)}`
  }

  return path
}

function clamp(value, minimum = 0, maximum = 100) {
  return Math.max(minimum, Math.min(maximum, value))
}

function format(value) {
  return value.toFixed(2)
}
</script>

<template>
  <svg
    class="sparkline"
    :viewBox="`0 0 ${CHART_WIDTH} ${chartHeight}`"
    preserveAspectRatio="none"
    role="img"
    aria-label="实时评分趋势"
    :style="{ height: `${chartHeight}px` }"
  >
    <line
      :x1="PLOT_LEFT"
      :y1="chartHeight - 5"
      :x2="PLOT_RIGHT"
      :y2="chartHeight - 5"
      class="baseline"
    />
    <path
      v-if="areaPath"
      :d="areaPath"
      class="trend-area"
      :fill="color"
    />
    <path
      v-if="linePath"
      :d="linePath"
      class="trend-line"
      :stroke="color"
    />
    <template v-if="endPoint">
      <circle
        class="endpoint-halo"
        :cx="endPoint.x"
        :cy="endPoint.y"
        r="5"
        :fill="color"
      />
      <circle
        class="endpoint"
        :cx="endPoint.x"
        :cy="endPoint.y"
        r="2.8"
        :fill="color"
      />
    </template>
  </svg>
</template>

<style scoped>
.sparkline {
  width: 100%;
  display: block;
  overflow: visible;
}

.trend-line {
  fill: none;
  stroke-width: 2.6;
  stroke-linecap: round;
  stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
}

.trend-area {
  opacity: 0.08;
}

.endpoint-halo {
  opacity: 0.16;
}

.endpoint {
  stroke: var(--surface);
  stroke-width: 1.2;
  vector-effect: non-scaling-stroke;
}

.baseline {
  stroke: var(--line);
  stroke-width: 1;
  stroke-dasharray: 3 5;
  vector-effect: non-scaling-stroke;
}
</style>
