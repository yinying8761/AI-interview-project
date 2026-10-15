<script setup>
import {
  AlertCircle,
  ArrowRight,
  BarChart3,
  CalendarClock,
  FileText,
  LoaderCircle,
  RefreshCw,
  Sparkles,
  Trophy,
} from 'lucide-vue-next'
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { apiRequest } from '../api/client'
import ScoreRing from '../components/ScoreRing.vue'
import { formatDateTime, formatDuration } from '../utils/media'

const reports = ref([])
const loading = ref(true)
const error = ref('')

const scoreStats = computed(() => {
  const finals = reports.value
    .map((item) => Number(item.final_score))
    .filter((value) => Number.isFinite(value))
  if (!finals.length) return null
  const average = finals.reduce((sum, value) => sum + value, 0) / finals.length
  return {
    count: finals.length,
    average: average.toFixed(1),
    best: Math.max(...finals).toFixed(1),
  }
})

onMounted(load)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const payload = await apiRequest('/api/reports', { timeout: 30000 })
    reports.value = payload.reports || []
  } catch (cause) {
    error.value = cause.message
  } finally {
    loading.value = false
  }
}

function scoreText(value) {
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(1) : '--'
}

function levelTone(level) {
  if (level === '优秀' || level === '良好') return 'tag-positive'
  if (level === '中等' || level === '及格') return 'tag'
  return 'tag-warning'
}
</script>

<template>
  <div class="page">
    <header class="reports-head">
      <div>
        <span class="tag"><BarChart3 :size="13" /> 历史报告</span>
        <h1>我的面试记录</h1>
        <p>每份报告都完整保留问答评估、表情与注意力采样统计，可随时回看复盘。</p>
      </div>
      <button class="button button-secondary" type="button" :disabled="loading" @click="load">
        <LoaderCircle v-if="loading" class="spin" :size="16" />
        <RefreshCw v-else :size="16" />
        刷新列表
      </button>
    </header>

    <div v-if="error" class="alert alert-danger reports-alert">
      <AlertCircle :size="16" />
      <span>{{ error }}</span>
    </div>

    <section v-if="scoreStats" class="stats-row">
      <article class="panel stat-card">
        <span class="stat-icon"><FileText :size="18" /></span>
        <div>
          <small>已完成面试</small>
          <strong class="data-value">{{ reports.length }}</strong>
        </div>
      </article>
      <article class="panel stat-card">
        <span class="stat-icon teal"><BarChart3 :size="18" /></span>
        <div>
          <small>最终得分平均</small>
          <strong class="data-value">{{ scoreStats.average }}</strong>
        </div>
      </article>
      <article class="panel stat-card">
        <span class="stat-icon amber"><Trophy :size="18" /></span>
        <div>
          <small>最终得分最高</small>
          <strong class="data-value">{{ scoreStats.best }}</strong>
        </div>
      </article>
    </section>

    <div v-if="loading" class="report-loading">
      <LoaderCircle class="spin" :size="26" />
      <span>正在读取报告列表</span>
    </div>

    <section v-else-if="!reports.length" class="panel empty-state">
      <Sparkles :size="28" />
      <h2>还没有面试报告</h2>
      <p>完成一次模拟面试并结束评估后，报告会自动出现在这里。</p>
      <RouterLink class="button button-primary" :to="{ name: 'interview' }">
        去开始第一场面试
        <ArrowRight :size="16" />
      </RouterLink>
    </section>

    <section v-else class="report-list">
      <article v-for="item in reports" :key="item.session_id" class="panel report-card">
        <div class="report-score">
          <ScoreRing
            :score="item.final_score"
            :size="92"
            :tone="item.final_score == null ? 'teal' : 'teal'"
            label="最终得分"
          />
        </div>

        <div class="report-body">
          <div class="report-title">
            <h2>{{ item.job_role || '未命名岗位' }}</h2>
            <span class="tag" :class="levelTone(item.level)">{{ item.level || '待评估' }}</span>
            <span class="tag">{{ item.difficulty || '--' }}</span>
          </div>
          <p class="report-summary">{{ item.summary || '暂无评估总结。' }}</p>
          <div class="report-meta">
            <span><CalendarClock :size="14" />{{ formatDateTime(item.created_at) }}</span>
            <span>时长 {{ formatDuration(item.duration_seconds) }}</span>
          </div>
        </div>

        <div class="report-metrics">
          <div class="metric">
            <span>问答</span>
            <strong class="data-value">{{ scoreText(item.question_score) }}</strong>
          </div>
          <div class="metric">
            <span>表情</span>
            <strong class="data-value">{{ scoreText(item.expression_score) }}</strong>
          </div>
          <div class="metric">
            <span>注意力</span>
            <strong class="data-value">{{ scoreText(item.attention_score) }}</strong>
          </div>
        </div>

        <RouterLink
          class="button button-secondary report-open"
          :to="{ name: 'report-detail', params: { id: item.session_id } }"
        >
          查看报告
          <ArrowRight :size="15" />
        </RouterLink>
      </article>
    </section>
  </div>
</template>

<style scoped>
.reports-head {
  margin-bottom: 18px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}

.reports-head h1 {
  margin-top: 12px;
  font-size: 27px;
}

.reports-head p {
  margin-top: 8px;
  color: var(--muted-strong);
  font-size: 13px;
  line-height: 1.7;
}

.reports-alert {
  margin-bottom: 16px;
}

.stats-row {
  margin-bottom: 16px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 13px;
}

.stat-icon {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: var(--radius);
  color: var(--muted-strong);
  background: var(--surface-soft);
}

.stat-icon.teal {
  color: var(--teal-dark);
  background: var(--teal-soft);
}

.stat-icon.amber {
  color: #8a5c14;
  background: var(--amber-soft);
}

.stat-card small {
  display: block;
  color: var(--muted);
  font-size: 11px;
}

.stat-card strong {
  display: block;
  margin-top: 3px;
  color: var(--ink);
  font-size: 22px;
}

.report-loading {
  min-height: 260px;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 10px;
  color: var(--muted);
  font-size: 13px;
}

.empty-state {
  min-height: 320px;
  display: grid;
  align-content: center;
  justify-items: center;
  gap: 9px;
  text-align: center;
}

.empty-state svg {
  color: var(--teal);
}

.empty-state h2 {
  margin-top: 6px;
  font-size: 18px;
}

.empty-state p {
  max-width: 380px;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.7;
}

.empty-state .button {
  margin-top: 10px;
  text-decoration: none;
}

.report-list {
  display: grid;
  gap: 12px;
}

.report-card {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 18px;
}

.report-body {
  min-width: 0;
}

.report-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.report-title h2 {
  font-size: 16px;
}

.report-summary {
  margin-top: 7px;
  display: -webkit-box;
  overflow: hidden;
  color: var(--muted-strong);
  font-size: 12px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.report-meta {
  margin-top: 9px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  color: var(--muted);
  font-size: 11px;
}

.report-meta span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.report-metrics {
  display: flex;
  gap: 8px;
}

.metric {
  min-width: 72px;
  padding: 9px 11px;
  border: 1px solid var(--line-soft);
  border-radius: 6px;
  background: var(--surface-soft);
  text-align: center;
}

.metric span {
  display: block;
  color: var(--muted);
  font-size: 10px;
}

.metric strong {
  display: block;
  margin-top: 4px;
  color: var(--ink);
  font-size: 17px;
}

.report-open {
  text-decoration: none;
}

@media (max-width: 1180px) {
  .report-card {
    grid-template-columns: auto minmax(0, 1fr) auto;
  }

  .report-metrics {
    grid-column: 2 / -1;
  }
}

@media (max-width: 900px) {
  .reports-head {
    flex-direction: column;
    align-items: stretch;
  }

  .stats-row {
    grid-template-columns: 1fr;
  }

  .report-card {
    grid-template-columns: 1fr;
    justify-items: stretch;
  }

  .report-score {
    justify-self: start;
  }

  .report-metrics {
    grid-column: auto;
    flex-wrap: wrap;
  }
}
</style>
