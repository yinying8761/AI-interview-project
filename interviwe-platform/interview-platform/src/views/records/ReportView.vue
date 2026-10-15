<template>
  <div class="page-container">
    <!-- 不存在 -->
    <el-empty v-if="!record" description="面试报告不存在或尚未完成打分">
      <el-button type="primary" @click="router.push('/records')">返回列表</el-button>
    </el-empty>

    <template v-else>
      <!-- 操作栏 -->
      <div class="action-bar">
        <el-button @click="router.push('/records')">
          <el-icon><ArrowLeft /></el-icon> 返回列表
        </el-button>
        <div class="right-actions">
          <el-button type="primary" @click="printReport">
            <el-icon><Printer /></el-icon> 打印报告
          </el-button>
          <el-button type="success" @click="exportReport">
            <el-icon><Download /></el-icon> 导出JSON
          </el-button>
        </div>
      </div>

      <!-- 报告主体 -->
      <div class="report-body" id="report-print">
        <!-- 报告标题 -->
        <div class="report-header">
          <div class="report-title">
            <el-icon size="32" color="#409EFF"><Document /></el-icon>
            <div>
              <h1>面试评估报告</h1>
              <p>Interview Assessment Report</p>
            </div>
          </div>
          <div class="report-meta">
            <span>报告编号：RPT-{{ String(record.id).padStart(6, '0') }}</span>
            <span>生成时间：{{ formatDateTime(record.updateTime) }}</span>
          </div>
        </div>

        <!-- 综合结论 Banner -->
        <div class="conclusion-banner" :style="{ background: getBannerGradient(record.recommendation) }">
          <div class="conclusion-left">
            <el-icon size="40" color="rgba(255,255,255,0.8)">
              <component :is="recommendIconMap[record.recommendation]" />
            </el-icon>
            <div>
              <div class="rec-label">录用推荐</div>
              <div class="rec-value">{{ record.recommendation }}</div>
            </div>
          </div>
          <div class="conclusion-right">
            <div class="big-score">{{ record.totalScore }}</div>
            <div class="score-label">综合评分（满分100）</div>
          </div>
        </div>

        <!-- 基本信息 -->
        <el-card class="section-card">
          <template #header><span class="section-title">📋 基本信息</span></template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="面试名称">{{ record.interviewName }}</el-descriptions-item>
            <el-descriptions-item label="面试状态">
              <el-tag :type="statusTypeMap[record.status]">{{ record.status }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="候选人">
              <el-tag type="success">{{ record.candidateName }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="面试官">
              <el-tag type="primary">{{ record.interviewerName }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="面试时间">{{ formatDateTime(record.interviewTime) }}</el-descriptions-item>
            <el-descriptions-item label="实际时长">{{ record.duration > 0 ? `${record.duration} 分钟` : '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 维度评分 -->
        <el-card class="section-card">
          <template #header><span class="section-title">📊 维度评分</span></template>

          <div class="score-dimensions">
            <div v-for="item in record.scores" :key="item.dimension" class="dim-row">
              <div class="dim-header">
                <span class="dim-name">{{ item.dimension }}</span>
                <div class="dim-score-wrap">
                  <span class="dim-score-text" :style="{ color: getScoreColor(item.score) }">
                    {{ item.score }}<small>/10</small>
                  </span>
                </div>
              </div>
              <el-progress
                :percentage="item.score * 10"
                :color="getProgressColor(item.score)"
                :stroke-width="12"
                :show-text="false"
                style="margin:8px 0"
              />
              <p v-if="item.comment" class="dim-comment">
                <el-icon size="12"><ChatLineRound /></el-icon>
                {{ item.comment }}
              </p>
            </div>
          </div>

          <!-- 雷达图替代方案：各维度分数可视化卡片 -->
          <div class="score-visual">
            <div
              v-for="item in record.scores"
              :key="item.dimension"
              class="score-badge"
              :style="{ background: getScoreBadgeBg(item.score) }"
            >
              <span class="badge-score">{{ item.score }}</span>
              <span class="badge-dim">{{ item.dimension }}</span>
            </div>
          </div>
        </el-card>

        <!-- 综合评语 -->
        <el-card class="section-card">
          <template #header><span class="section-title">💬 综合评语</span></template>
          <div class="overall-comment">
            <el-icon size="20" color="#909399"><ChatDotRound /></el-icon>
            <p>{{ record.overallComment || '（暂无评语）' }}</p>
          </div>
        </el-card>

        <!-- 报告底部 -->
        <div class="report-footer">
          <el-divider>— 报告结束 —</el-divider>
          <p class="footer-note">
            本报告由面试平台管理系统自动生成 · 面试官：{{ record.interviewerName }} · 
            报告时间：{{ formatDateTime(record.updateTime) }}
          </p>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useRecordStore } from '@/stores/record'
import { formatDateTime, exportJson } from '@/utils/helpers'

const route = useRoute()
const router = useRouter()
const recordStore = useRecordStore()

const recordId = Number(route.params.id)
const record = computed(() => recordStore.getRecordById(recordId))

const statusTypeMap: Record<string, string> = {
  '待面试': 'info', '进行中': 'warning', '已完成': 'success', '已取消': 'danger'
}

const recommendIconMap: Record<string, string> = {
  '强烈推荐': 'StarFilled',
  '推荐':    'CircleCheck',
  '待定':    'QuestionFilled',
  '不推荐':  'CircleClose'
}

function getBannerGradient(recommendation: string) {
  const map: Record<string, string> = {
    '强烈推荐': 'linear-gradient(135deg, #52c41a, #389e0d)',
    '推荐':    'linear-gradient(135deg, #1890ff, #096dd9)',
    '待定':    'linear-gradient(135deg, #faad14, #d48806)',
    '不推荐':  'linear-gradient(135deg, #ff4d4f, #cf1322)'
  }
  return map[recommendation] ?? 'linear-gradient(135deg, #909399, #606266)'
}

function getScoreColor(score: number) {
  if (score >= 8) return '#67C23A'
  if (score >= 6) return '#409EFF'
  if (score >= 4) return '#E6A23C'
  return '#F56C6C'
}

function getProgressColor(score: number) {
  if (score >= 8) return '#67C23A'
  if (score >= 6) return '#409EFF'
  if (score >= 4) return '#E6A23C'
  return '#F56C6C'
}

function getScoreBadgeBg(score: number) {
  if (score >= 8) return 'rgba(103,194,58,0.15)'
  if (score >= 6) return 'rgba(64,158,255,0.15)'
  if (score >= 4) return 'rgba(230,162,60,0.15)'
  return 'rgba(245,108,108,0.15)'
}

function printReport() {
  window.print()
}

function exportReport() {
  if (!record.value) return
  exportJson(record.value, `面试报告_${record.value.candidateName}_${record.value.id}.json`)
  ElMessage.success('报告已导出')
}
</script>

<style lang="scss" scoped>
.page-container {
  padding: 20px;
  max-width: 900px;
  margin: 0 auto;
}

.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  .right-actions { display: flex; gap: 8px; }
}

.report-body {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08);
}

.report-header {
  padding: 28px 32px 20px;
  border-bottom: 1px solid #EBEEF5;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;

  .report-title {
    display: flex;
    align-items: center;
    gap: 14px;
    h1 { font-size: 22px; font-weight: 700; color: #303133; margin: 0 0 4px; }
    p { font-size: 12px; color: #909399; margin: 0; letter-spacing: 1px; }
  }

  .report-meta {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
    font-size: 12px;
    color: #909399;
  }
}

.conclusion-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 28px 40px;
  color: #fff;

  .conclusion-left {
    display: flex;
    align-items: center;
    gap: 16px;
    .rec-label { font-size: 13px; opacity: 0.8; margin-bottom: 4px; }
    .rec-value { font-size: 28px; font-weight: 700; }
  }

  .conclusion-right {
    text-align: right;
    .big-score {
      font-size: 56px;
      font-weight: 700;
      line-height: 1;
    }
    .score-label { font-size: 13px; opacity: 0.8; margin-top: 4px; }
  }
}

.section-card {
  border-radius: 0;
  border: none;
  border-bottom: 1px solid #EBEEF5;
  box-shadow: none !important;
  :deep(.el-card__header) {
    padding: 14px 28px;
    background: #fafafa;
  }
  :deep(.el-card__body) { padding: 20px 28px; }

  .section-title {
    font-size: 15px;
    font-weight: 600;
    color: #303133;
  }
}

.score-dimensions {
  display: flex;
  flex-direction: column;
  gap: 16px;

  .dim-row {
    .dim-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 4px;
      .dim-name { font-weight: 600; font-size: 14px; color: #303133; }
      .dim-score-text {
        font-size: 20px;
        font-weight: 700;
        small { font-size: 13px; opacity: 0.7; }
      }
    }
    .dim-comment {
      display: flex;
      align-items: center;
      gap: 4px;
      font-size: 13px;
      color: #606266;
      background: #f8f9fa;
      border-radius: 6px;
      padding: 6px 10px;
      margin: 4px 0 0;
    }
  }
}

.score-visual {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px dashed #EBEEF5;

  .score-badge {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 12px 20px;
    border-radius: 10px;
    gap: 4px;

    .badge-score {
      font-size: 26px;
      font-weight: 700;
      color: #303133;
    }
    .badge-dim {
      font-size: 12px;
      color: #606266;
    }
  }
}

.overall-comment {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: #f8f9fa;
  border-radius: 8px;
  padding: 16px;
  p {
    margin: 0;
    line-height: 1.8;
    color: #303133;
    font-size: 14px;
    white-space: pre-wrap;
  }
}

.report-footer {
  padding: 12px 28px 20px;
  .footer-note {
    text-align: center;
    font-size: 12px;
    color: #c0c4cc;
  }
}

/* 打印样式 */
@media print {
  .action-bar { display: none !important; }
  .page-container { max-width: 100%; padding: 0; }
  .report-body { box-shadow: none; }
}
</style>
