<template>
  <div class="page-container">
    <el-card v-if="record" class="score-card">
      <!-- 面试基本信息 -->
      <template #header>
        <div class="card-header">
          <span class="title">面试打分 - {{ record.interviewName }}</span>
          <el-tag :type="statusTypeMap[record.status]">{{ record.status }}</el-tag>
        </div>
      </template>

      <el-descriptions :column="3" border class="info-desc">
        <el-descriptions-item label="候选人">
          <el-tag type="success">{{ record.candidateName }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="面试官">
          <el-tag type="primary">{{ record.interviewerName }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="面试时间">
          {{ formatDateTime(record.interviewTime) }}
        </el-descriptions-item>
      </el-descriptions>

      <el-divider>评分维度</el-divider>

      <!-- 各维度打分 -->
      <div class="score-grid">
        <div v-for="(item, idx) in scoreItems" :key="idx" class="score-item">
          <div class="score-item-header">
            <span class="dimension-name">{{ item.dimension }}</span>
            <el-tag :type="getScoreTagType(item.score)" size="small">
              {{ item.score }} / 10
            </el-tag>
          </div>

          <el-slider
            v-model="item.score"
            :min="0"
            :max="10"
            :step="1"
            :marks="sliderMarks"
            show-stops
            :color="getSliderColor(item.score)"
            class="score-slider"
          />

          <el-input
            v-model="item.comment"
            type="textarea"
            :rows="2"
            :placeholder="`请输入「${item.dimension}」维度的评价（选填）`"
            resize="none"
          />
        </div>
      </div>

      <el-divider>综合评价</el-divider>

      <!-- 综合分展示（实时计算） -->
      <div class="total-score-display">
        <div class="score-circle" :style="{ background: getScoreGradient(calculatedScore) }">
          <span class="score-number">{{ calculatedScore }}</span>
          <span class="score-unit">分</span>
        </div>
        <div class="score-breakdown">
          <p class="breakdown-title">评分明细</p>
          <div v-for="item in scoreItems" :key="item.dimension" class="breakdown-item">
            <span class="bd-dim">{{ item.dimension }}</span>
            <el-progress
              :percentage="item.score * 10"
              :color="getSliderColor(item.score)"
              :stroke-width="8"
              style="flex:1;margin:0 12px"
            />
            <span class="bd-score">{{ item.score }}</span>
          </div>
        </div>
      </div>

      <!-- 综合评语 -->
      <el-form ref="formRef" :model="submitForm" :rules="formRules" label-position="top">
        <el-form-item label="综合评语" prop="overallComment">
          <el-input
            v-model="submitForm.overallComment"
            type="textarea"
            :rows="4"
            placeholder="请输入对候选人的综合评价，包括优势与不足..."
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="录用推荐" prop="recommendation">
          <div class="recommendation-selector">
            <div
              v-for="r in recommendOptions"
              :key="r.value"
              class="rec-card"
              :class="{ active: submitForm.recommendation === r.value }"
              :style="submitForm.recommendation === r.value ? { background: r.color, borderColor: r.color } : {}"
              @click="submitForm.recommendation = r.value"
            >
              <el-icon size="20" :color="submitForm.recommendation === r.value ? '#fff' : r.color">
                <component :is="r.icon" />
              </el-icon>
              <span>{{ r.value }}</span>
            </div>
          </div>
        </el-form-item>

        <!-- 操作按钮 -->
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="recordStore.loading"
            @click="handleSubmit"
          >
            提交评分
          </el-button>
          <el-button size="large" @click="router.push('/records')">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 记录不存在 -->
    <el-empty v-else description="面试记录不存在">
      <el-button type="primary" @click="router.push('/records')">返回列表</el-button>
    </el-empty>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useRecordStore } from '@/stores/record'
import { formatDateTime } from '@/utils/helpers'
import type { ScoreItem } from '@/types'

const route = useRoute()
const router = useRouter()
const recordStore = useRecordStore()
const formRef = ref<FormInstance>()

const recordId = Number(route.params.id)
const record = computed(() => recordStore.getRecordById(recordId))

const statusTypeMap: Record<string, string> = {
  '待面试': 'info', '进行中': 'warning', '已完成': 'success', '已取消': 'danger'
}

// 默认评分维度（如果已有评分则恢复）
const defaultDimensions = ['技术能力', '项目经验', '沟通表达', '学习能力', '团队协作']

const scoreItems = reactive<ScoreItem[]>([])

onMounted(() => {
  if (record.value && record.value.scores && record.value.scores.length > 0) {
    // 已有打分，回填
    scoreItems.push(...record.value.scores.map(s => ({ ...s })))
  } else {
    // 初始化默认维度
    defaultDimensions.forEach(d => scoreItems.push({ dimension: d, score: 5, comment: '' }))
  }
  // 同步综合评语
  if (record.value?.overallComment) {
    submitForm.overallComment = record.value.overallComment
  }
  if (record.value?.recommendation) {
    submitForm.recommendation = record.value.recommendation
  }
})

// 实时计算综合分
const calculatedScore = computed(() => {
  if (scoreItems.length === 0) return 0
  const avg = scoreItems.reduce((s, item) => s + item.score, 0) / scoreItems.length
  return Math.round(avg * 10)
})

const submitForm = reactive({
  overallComment: '',
  recommendation: '待定' as '强烈推荐' | '推荐' | '待定' | '不推荐'
})

const formRules = {
  overallComment: [{ required: true, message: '请填写综合评语', trigger: 'blur' }],
  recommendation: [{ required: true, message: '请选择推荐结论', trigger: 'change' }]
}

const recommendOptions = [
  { value: '强烈推荐', icon: 'StarFilled',  color: '#67C23A' },
  { value: '推荐',    icon: 'CircleCheck',  color: '#409EFF' },
  { value: '待定',    icon: 'QuestionFilled', color: '#E6A23C' },
  { value: '不推荐',  icon: 'CircleClose',  color: '#F56C6C' }
] as const

const sliderMarks = { 0: '0', 5: '5', 10: '10' }

function getScoreTagType(score: number) {
  if (score >= 8) return 'success'
  if (score >= 6) return 'primary'
  if (score >= 4) return 'warning'
  return 'danger'
}

function getSliderColor(score: number) {
  if (score >= 8) return '#67C23A'
  if (score >= 6) return '#409EFF'
  if (score >= 4) return '#E6A23C'
  return '#F56C6C'
}

function getScoreGradient(score: number) {
  if (score >= 85) return 'linear-gradient(135deg, #52c41a, #389e0d)'
  if (score >= 70) return 'linear-gradient(135deg, #1890ff, #096dd9)'
  if (score >= 60) return 'linear-gradient(135deg, #faad14, #d48806)'
  return 'linear-gradient(135deg, #ff4d4f, #cf1322)'
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  const success = await recordStore.submitScore(
    recordId,
    [...scoreItems],
    submitForm.overallComment,
    submitForm.recommendation
  )

  if (success) {
    ElMessage.success('评分已提交！')
    router.push(`/records/${recordId}/report`)
  } else {
    ElMessage.error('提交失败，请重试')
  }
}
</script>

<style lang="scss" scoped>
.page-container {
  padding: 20px;
}

.score-card {
  max-width: 900px;
  margin: 0 auto;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  .title { font-size: 16px; font-weight: 600; }
}

.info-desc { margin-bottom: 8px; }

.score-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 20px;
  margin-bottom: 8px;
}

.score-item {
  background: #f8f9fa;
  border-radius: 10px;
  padding: 16px;

  .score-item-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    .dimension-name {
      font-weight: 600;
      font-size: 14px;
      color: #303133;
    }
  }

  .score-slider {
    margin: 4px 8px 16px;
    :deep(.el-slider__runway) { margin: 8px 0; }
  }
}

.total-score-display {
  display: flex;
  align-items: flex-start;
  gap: 32px;
  margin: 16px 0 24px;

  .score-circle {
    min-width: 120px;
    height: 120px;
    border-radius: 50%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    box-shadow: 0 4px 16px rgba(0,0,0,0.2);
    .score-number {
      font-size: 36px;
      font-weight: 700;
      color: #fff;
      line-height: 1;
    }
    .score-unit {
      font-size: 14px;
      color: rgba(255,255,255,0.8);
    }
  }

  .score-breakdown {
    flex: 1;
    .breakdown-title {
      font-weight: 600;
      color: #606266;
      margin-bottom: 12px;
    }
    .breakdown-item {
      display: flex;
      align-items: center;
      margin-bottom: 8px;
      .bd-dim { width: 64px; font-size: 13px; color: #606266; }
      .bd-score { width: 24px; text-align: right; font-weight: 600; color: #303133; }
    }
  }
}

.recommendation-selector {
  display: flex;
  gap: 12px;
  .rec-card {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    padding: 16px 8px;
    border: 2px solid #EBEEF5;
    border-radius: 10px;
    cursor: pointer;
    font-size: 14px;
    font-weight: 600;
    color: #606266;
    transition: all 0.2s;
    &:hover { border-color: #409EFF; }
    &.active { color: #fff; }
  }
}
</style>
