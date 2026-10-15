<script setup>
import { computed } from 'vue'

const props = defineProps({
  score: { type: [Number, String], default: null },
  label: { type: String, default: '得分' },
  size: { type: Number, default: 116 },
  tone: { type: String, default: 'teal' },
})

const radius = 44
const circumference = 2 * Math.PI * radius
const numericScore = computed(() => {
  if (props.score === null || props.score === undefined || props.score === '') return null
  const value = Number(props.score)
  return Number.isFinite(value) ? Math.max(0, Math.min(100, value)) : null
})
const dashOffset = computed(() => {
  if (numericScore.value === null) return circumference
  return circumference * (1 - numericScore.value / 100)
})
</script>

<template>
  <div class="score-ring" :style="{ '--ring-size': `${size}px` }" :class="`tone-${tone}`">
    <svg viewBox="0 0 104 104" aria-hidden="true">
      <circle class="ring-track" cx="52" cy="52" :r="radius" />
      <circle
        class="ring-value"
        cx="52"
        cy="52"
        :r="radius"
        :stroke-dasharray="circumference"
        :stroke-dashoffset="dashOffset"
      />
    </svg>
    <span class="ring-copy">
      <strong>{{ numericScore === null ? '--' : numericScore.toFixed(1) }}</strong>
      <small>{{ label }}</small>
    </span>
  </div>
</template>

<style scoped>
.score-ring {
  position: relative;
  width: var(--ring-size);
  height: var(--ring-size);
  flex: 0 0 auto;
}

.score-ring svg {
  width: 100%;
  height: 100%;
  transform: rotate(-90deg);
}

circle {
  fill: none;
  stroke-width: 7;
}

.ring-track {
  stroke: var(--line);
}

.ring-value {
  stroke: var(--teal);
  stroke-linecap: round;
  transition: stroke-dashoffset .6s cubic-bezier(.2, .7, .2, 1);
}

.tone-coral .ring-value {
  stroke: var(--coral);
}

.tone-amber .ring-value {
  stroke: var(--amber);
}

.ring-copy {
  position: absolute;
  inset: 0;
  display: grid;
  align-content: center;
  justify-items: center;
}

.ring-copy strong {
  color: var(--ink);
  font-family: var(--font-data);
  font-size: clamp(21px, 4vw, 29px);
  line-height: 1;
  letter-spacing: 0;
}

.ring-copy small {
  margin-top: 6px;
  color: var(--muted);
  font-size: 10px;
  font-weight: 700;
}
</style>
