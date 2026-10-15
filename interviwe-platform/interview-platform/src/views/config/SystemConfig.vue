<template>
  <div class="page-container">
    <div class="page-header">
      <h2>系统配置 - 面试安排</h2>
      <div class="header-actions">
        <el-button @click="handleExport">
          <el-icon><Download /></el-icon> 导出配置模板
        </el-button>
        <el-upload
          :show-file-list="false"
          accept=".json"
          :before-upload="handleImport"
        >
          <el-button type="primary" plain>
            <el-icon><Upload /></el-icon> 导入配置
          </el-button>
        </el-upload>
      </div>
    </div>

    <!-- 分步流程 -->
    <el-card class="steps-card">
      <el-steps :active="currentStep" finish-status="success" align-center>
        <el-step title="基础信息" description="名称·类型·时长" />
        <el-step title="详细安排" description="方向·面试官·时间" />
        <el-step title="确认提交" description="备注·最终确认" />
      </el-steps>
    </el-card>

    <!-- 表单区域 -->
    <el-card class="form-card">
      <el-form
        ref="formRef"
        :model="formData"
        label-width="120px"
        class="config-form"
      >
        <!-- Step 1: 基础信息 -->
        <template v-if="currentStep === 0">
          <div class="step-title">第一步：填写基础信息</div>

          <el-form-item label="面试名称" prop="interviewName" :rules="rules.interviewName">
            <el-input
              v-model="formData.interviewName"
              placeholder="请输入面试名称，2-30字符"
              maxlength="30"
              show-word-limit
            />
          </el-form-item>

          <el-form-item label="面试类型" prop="interviewType" :rules="rules.interviewType">
            <el-select v-model="formData.interviewType" placeholder="请选择面试类型" class="w-full" @change="onTypeChange">
              <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="面试时长(分钟)" prop="duration" :rules="rules.duration">
            <el-input-number
              v-model="formData.duration"
              :min="30"
              :max="180"
              :step="15"
              class="w-full"
              @change="onDurationChange"
            />
            <div class="field-hint">范围: 30-180分钟</div>
          </el-form-item>
        </template>

        <!-- Step 2: 详细安排 -->
        <template v-if="currentStep === 1">
          <div class="step-title">第二步：填写详细安排</div>

          <!-- 联动字段：技术方向（仅技术面时显示） -->
          <el-form-item
            v-if="showTechDirection"
            label="技术方向"
            prop="techDirection"
            :rules="rules.techDirection"
          >
            <el-select v-model="formData.techDirection" placeholder="请选择技术方向" class="w-full">
              <el-option v-for="t in techDirectionOptions" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
          </el-form-item>

          <!-- 联动字段：是否需要休息（仅时长>90分钟时显示） -->
          <el-form-item v-if="showNeedBreak" label="是否需要休息">
            <el-switch v-model="formData.needBreak" active-text="是" inactive-text="否" />
            <span class="field-hint ml-8">时长超过90分钟，建议安排休息时间</span>
          </el-form-item>

          <el-form-item label="面试官" prop="interviewers" :rules="rules.interviewers">
            <el-select
              v-model="formData.interviewers"
              multiple
              placeholder="请选择面试官（至少1人）"
              class="w-full"
            >
              <el-option v-for="i in interviewerOptions" :key="i.value" :label="i.label" :value="i.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="面试时间" prop="interviewTime" :rules="rules.interviewTime">
            <el-date-picker
              v-model="formData.interviewTime"
              type="datetime"
              placeholder="请选择面试时间"
              class="w-full"
              :disabled-date="disablePastDate"
              value-format="YYYY-MM-DDTHH:mm:ss[Z]"
            />
          </el-form-item>
        </template>

        <!-- Step 3: 确认提交 -->
        <template v-if="currentStep === 2">
          <div class="step-title">第三步：备注及确认</div>

          <el-form-item label="备注" prop="remark">
            <el-input
              v-model="formData.remark"
              type="textarea"
              :rows="4"
              placeholder="请填写备注信息（最多500字）"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>

          <!-- 信息摘要 -->
          <div class="summary-box">
            <div class="summary-title">信息确认</div>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="面试名称">{{ formData.interviewName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="面试类型">
                <el-tag size="small" type="primary">{{ formData.interviewType || '-' }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item v-if="showTechDirection" label="技术方向">
                <el-tag size="small" type="success">{{ formData.techDirection || '-' }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="面试时长">{{ formData.duration }}分钟</el-descriptions-item>
              <el-descriptions-item label="是否休息">{{ formData.needBreak ? '是' : '否' }}</el-descriptions-item>
              <el-descriptions-item label="面试官">{{ formData.interviewers?.join('、') || '-' }}</el-descriptions-item>
              <el-descriptions-item label="面试时间">{{ formatTime(formData.interviewTime) }}</el-descriptions-item>
            </el-descriptions>
          </div>
        </template>
      </el-form>

      <!-- 操作按钮 -->
      <div class="form-actions">
        <el-button v-if="currentStep > 0" @click="prevStep">上一步</el-button>
        <el-button @click="handleSaveDraft" :loading="saving">
          <el-icon><DocumentAdd /></el-icon> 保存草稿
        </el-button>
        <el-button v-if="currentStep < 2" type="primary" @click="nextStep">
          下一步 <el-icon><ArrowRight /></el-icon>
        </el-button>
        <el-button v-else type="success" @click="handleSubmit" :loading="submitting">
          <el-icon><Check /></el-icon> 正式提交
        </el-button>
      </div>
    </el-card>

    <!-- 历史配置列表 -->
    <el-card class="history-card">
      <template #header>
        <span>历史配置记录</span>
        <el-button size="small" type="danger" plain @click="clearHistory" class="ml-8">清空</el-button>
      </template>
      <el-table :data="configStore.configs" border stripe size="small" max-height="300">
        <el-table-column prop="interviewName" label="面试名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="interviewType" label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ row.interviewType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="时长(分)" width="80" align="center" />
        <el-table-column prop="interviewers" label="面试官" min-width="120">
          <template #default="{ row }">{{ row.interviewers?.join('、') }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === '已提交' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button type="danger" size="small" text @click="configStore.deleteConfig(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useConfigStore } from '@/stores/config'
import { exportJson, readJsonFile, formatDateTime } from '@/utils/helpers'
import type { InterviewConfig } from '@/types'

const router = useRouter()
const configStore = useConfigStore()
const formRef = ref<FormInstance>()
const currentStep = ref(0)
const saving = ref(false)
const submitting = ref(false)

interface FormData {
  interviewName: string
  interviewType: string
  techDirection: string
  duration: number
  needBreak: boolean
  interviewers: string[]
  interviewTime: string
  remark: string
}

const formData = ref<FormData>({
  interviewName: '',
  interviewType: '',
  techDirection: '',
  duration: 60,
  needBreak: false,
  interviewers: [],
  interviewTime: '',
  remark: ''
})

// 联动逻辑
const showTechDirection = computed(() => formData.value.interviewType === '技术面')
const showNeedBreak = computed(() => formData.value.duration > 90)

function onTypeChange() {
  if (formData.value.interviewType !== '技术面') {
    formData.value.techDirection = ''
  }
}

function onDurationChange() {
  if (formData.value.duration <= 90) {
    formData.value.needBreak = false
  }
}

// 选项数据
const typeOptions = [
  { label: '技术面', value: '技术面' },
  { label: 'HR面', value: 'HR面' },
  { label: '综合面', value: '综合面' }
]

const techDirectionOptions = [
  { label: '前端', value: '前端' },
  { label: '后端', value: '后端' },
  { label: '算法', value: '算法' },
  { label: '大数据', value: '大数据' }
]

const interviewerOptions = [
  { label: '张明', value: '张明' },
  { label: '李华', value: '李华' },
  { label: '王芳', value: '王芳' },
  { label: '刘洋', value: '刘洋' },
  { label: '吴刚', value: '吴刚' },
  { label: '林佳', value: '林佳' },
  { label: '罗丽', value: '罗丽' },
  { label: '徐鑫', value: '徐鑫' }
]

// 校验规则
const rules = {
  interviewName: [
    { required: true, message: '请输入面试名称', trigger: 'blur' },
    { min: 2, max: 30, message: '长度应在2到30个字符', trigger: 'blur' }
  ],
  interviewType: [
    { required: true, message: '请选择面试类型', trigger: 'change' }
  ],
  duration: [
    { required: true, message: '请输入面试时长', trigger: 'change' },
    {
      validator: (_: any, value: number, callback: Function) => {
        if (value < 30 || value > 180) callback(new Error('时长应在30-180分钟之间'))
        else callback()
      },
      trigger: 'change'
    }
  ],
  techDirection: computed(() =>
    showTechDirection.value
      ? [{ required: true, message: '请选择技术方向', trigger: 'change' }]
      : []
  ),
  interviewers: [
    {
      validator: (_: any, value: string[], callback: Function) => {
        if (!value || value.length === 0) callback(new Error('请至少选择一位面试官'))
        else callback()
      },
      trigger: 'change'
    }
  ],
  interviewTime: [
    { required: true, message: '请选择面试时间', trigger: 'change' },
    {
      validator: (_: any, value: string, callback: Function) => {
        if (value && new Date(value) < new Date()) {
          callback(new Error('不能选择过去时间'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

// 步骤字段映射
const stepFields: Record<number, string[]> = {
  0: ['interviewName', 'interviewType', 'duration'],
  1: ['interviewers', 'interviewTime', ...(showTechDirection.value ? ['techDirection'] : [])],
  2: []
}

async function nextStep() {
  const fields = stepFields[currentStep.value]
  if (fields.length > 0) {
    try {
      await formRef.value?.validateField(fields)
      if (currentStep.value === 1 && showTechDirection.value) {
        await formRef.value?.validateField(['techDirection'])
      }
      currentStep.value++
    } catch {
      ElMessage.warning('请完整填写当前步骤的必填项')
    }
  } else {
    currentStep.value++
  }
}

function prevStep() {
  currentStep.value--
}

function handleSaveDraft() {
  saving.value = true
  setTimeout(() => {
    configStore.saveDraft(formData.value as any)
    ElMessage.success('草稿已保存到本地')
    saving.value = false
  }, 500)
}

async function handleSubmit() {
  submitting.value = true
  try {
    configStore.submitConfig(formData.value as any)
    ElMessage.success('面试配置提交成功！')
    resetForm()
    router.push('/questions')
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  formData.value = {
    interviewName: '',
    interviewType: '',
    techDirection: '',
    duration: 60,
    needBreak: false,
    interviewers: [],
    interviewTime: '',
    remark: ''
  }
  currentStep.value = 0
  formRef.value?.clearValidate()
}

function handleExport() {
  exportJson(configStore.exportConfigs(), `configs_${Date.now()}.json`)
  ElMessage.success('配置模板已导出')
}

async function handleImport(file: File) {
  try {
    const data = await readJsonFile(file)
    if (Array.isArray(data)) {
      configStore.importConfigs(data as InterviewConfig[])
      ElMessage.success(`成功导入 ${data.length} 条配置`)
    } else {
      ElMessage.error('文件格式不正确，请上传配置数组JSON')
    }
  } catch (e: any) {
    ElMessage.error(e.message || '导入失败')
  }
  return false
}

function clearHistory() {
  ElMessageBox.confirm('确认清空所有历史配置？', '提示', { type: 'warning' }).then(() => {
    configStore.configs.splice(0)
    ElMessage.success('已清空')
  }).catch(() => {})
}

function disablePastDate(date: Date) {
  return date < new Date(new Date().setHours(0, 0, 0, 0))
}

function formatTime(str: string) {
  return formatDateTime(str)
}

// 加载草稿
onMounted(() => {
  const draft = configStore.loadDraft()
  if (draft) {
    ElMessageBox.confirm('检测到未完成的草稿，是否恢复？', '草稿恢复', { type: 'info' })
      .then(() => {
        Object.assign(formData.value, draft)
        ElMessage.info('草稿已恢复')
      })
      .catch(() => configStore.clearDraft())
  }
})
</script>

<style scoped lang="scss">
.page-container {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  h2 { margin: 0; color: #303133; }
  .header-actions {
    display: flex;
    gap: 8px;
    align-items: center;
  }
}

.steps-card {
  :deep(.el-card__body) { padding: 24px 40px; }
}

.form-card {
  :deep(.el-card__body) { padding: 24px; }
}

.step-title {
  font-size: 15px;
  font-weight: 600;
  color: #409EFF;
  margin-bottom: 20px;
  padding-bottom: 10px;
  border-bottom: 1px solid #EBEEF5;
}

.config-form {
  max-width: 600px;
}

.w-full { width: 100%; }
.ml-8 { margin-left: 8px; }

.field-hint {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.summary-box {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 16px;
  margin-top: 8px;
  .summary-title {
    font-weight: 600;
    margin-bottom: 12px;
    color: #303133;
  }
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #EBEEF5;
}

.history-card {
  :deep(.el-card__header) {
    display: flex;
    align-items: center;
    padding: 12px 20px;
  }
}
</style>
