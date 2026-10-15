<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="flex-between" style="margin-bottom: 20px">
      <h2 style="font-size: 20px; font-weight: 600">
        {{ store.isAdmin ? '🎯 场景管理' : store.isCandidatePortal ? '🎯 面试大厅' : '🎯 面试场景' }}
      </h2>
      <div style="display: flex; gap: 12px">
        <el-select v-model="techField" placeholder="技术领域" clearable style="width: 140px"
          @change="fetchScenarios">
          <el-option label="全部" value="" />
          <el-option label="AI" value="AI" />
          <el-option label="大数据" value="大数据" />
          <el-option label="物联网" value="物联网" />
        </el-select>
        <el-button v-if="store.hasPermission('scenario:create')" type="primary"
          @click="openCreateDialog">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>新增场景
        </el-button>
      </div>
    </div>

    <!-- 场景卡片 -->
    <el-row :gutter="20" v-loading="loading">
      <el-col :span="8" v-for="item in scenarios" :key="item.id" style="margin-bottom: 20px">
        <el-card class="scenario-card" shadow="hover">
          <div class="scenario-cover" :style="coverGradient(item.difficulty)">
            <span>{{ fieldIcon(item.techField) }}</span>
          </div>
          <div class="scenario-body">
            <div class="scenario-name">{{ item.name }}</div>
            <div class="scenario-desc">{{ item.description || '暂无描述' }}</div>
            <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 12px">
              <div style="display: flex; gap: 6px">
                <el-tag size="small" :type="difficultyType(item.difficulty)">
                  {{ difficultyText(item.difficulty) }}
                </el-tag>
                <el-tag size="small" type="info">{{ item.techField }}</el-tag>
              </div>
              <span style="font-size: 12px; color: #909399">
                <el-icon style="vertical-align: -2px"><Collection /></el-icon>
                {{ item.questionCount || 0 }} 题
              </span>
            </div>
            <div style="margin-top: 14px; display: flex; gap: 8px">
              <el-button v-if="store.isCandidatePortal" type="primary" size="small" style="flex: 1"
                :disabled="!store.hasPermission('record:create')" @click="startInterview(item)">
                开始面试
              </el-button>
              <template v-if="store.hasPermission('scenario:update')">
                <el-button size="small" @click="openEditDialog(item)">编辑</el-button>
                <el-button size="small" type="danger" plain
                  @click="handleDelete(item)">删除</el-button>
              </template>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-empty v-if="!loading && scenarios.length === 0" description="暂无面试场景" />

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑场景' : '新增场景'" width="520px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="scenarioRules" label-width="90px">
        <el-form-item label="场景名称" prop="name">
          <el-input v-model="form.name" placeholder="如：Java 后端工程师" />
        </el-form-item>
        <el-form-item label="技术领域" prop="techField">
          <el-select v-model="form.techField" placeholder="请选择" style="width: 100%">
            <el-option label="AI" value="AI" />
            <el-option label="大数据" value="大数据" />
            <el-option label="物联网" value="物联网" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度" prop="difficulty">
          <el-radio-group v-model="form.difficulty">
            <el-radio :value="1">初级</el-radio>
            <el-radio :value="2">中级</el-radio>
            <el-radio :value="3">高级</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="场景描述" />
        </el-form-item>
        <el-form-item label="封面图" prop="coverImage">
          <el-input v-model="form.coverImage" placeholder="图片 URL（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>
    <!-- 开始面试：可选择固定试卷 -->
    <el-dialog v-model="startDialogVisible" title="开始面试" width="520px" destroy-on-close>
      <el-form label-width="90px">
        <el-form-item label="面试场景">
          <span>{{ currentScenario?.name }}</span>
        </el-form-item>
        <el-form-item label="面试类型">
          <el-select v-model="interviewType" style="width: 100%">
            <el-option label="答题面试" value="quiz" />
            <el-option label="AI 视频面试" value="video" />
          </el-select>
        </el-form-item>
        <el-form-item label="预约时间">
          <el-date-picker v-model="scheduleRange" type="datetimerange"
            value-format="YYYY-MM-DDTHH:mm:ss" start-placeholder="开始时间" end-placeholder="结束时间"
            style="width: 100%" />
          <div style="font-size: 12px; color: #909399; margin-top: 4px">留空则立即开始；填写后进入预约（待开始）状态，可在“我的记录”中按时进入。</div>
        </el-form-item>
        <el-form-item label="使用试卷">
          <el-select v-model="selectedPaperId" placeholder="不选则随机出题" clearable style="width: 100%">
            <el-option v-for="p in availablePapers" :key="p.id"
              :label="`${p.title}（${p.totalCount}题）`" :value="p.id" />
          </el-select>
          <div style="font-size: 12px; color: #e6a23c; margin-top: 4px">
            <template v-if="!availablePapers.length">该场景暂无可用的固定试卷，将使用随机题目。</template>
            <template v-else>选择试卷后，面试将使用该试卷的固定题目；不选则随机抽取。</template>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="starting" @click="confirmStart">开始面试</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import {
  listScenarios, createScenario, updateScenario, deleteScenario, createSession, listAvailablePapers
} from '../api/interview'

const router = useRouter()
const store = useUserStore()

const loading = ref(false)
const saving = ref(false)
const scenarios = ref([])
const techField = ref('')
const dialogVisible = ref(false)
const startDialogVisible = ref(false)
const currentScenario = ref(null)
const selectedPaperId = ref(null)
const availablePapers = ref([])
const starting = ref(false)
const scheduleRange = ref([])
const interviewType = ref('quiz')
const editingId = ref(null)
const formRef = ref()

const form = reactive({
  name: '', description: '', techField: '', difficulty: 1, coverImage: ''
})

const scenarioRules = {
  name: [{ required: true, message: '请输入场景名称', trigger: 'blur' }],
  techField: [{ required: true, message: '请选择技术领域', trigger: 'change' }]
}

const difficultyType = (d) => ({ 1: 'success', 2: 'warning', 3: 'danger' }[d] || 'info')
const difficultyText = (d) => ({ 1: '初级', 2: '中级', 3: '高级' }[d] || '未知')
const fieldIcon = (f) => ({ AI: '🤖', '大数据': '📊', '物联网': '📡' }[f] || '💼')

const coverGradient = (d) => {
  const gradients = {
    1: 'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
    2: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    3: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)'
  }
  return { background: gradients[d] || gradients[2] }
}

const fetchScenarios = async () => {
  loading.value = true
  try {
    const params = techField.value ? { techField: techField.value } : {}
    const res = await listScenarios(params)
    scenarios.value = res.data || []
  } catch (e) { /* handled */ }
  loading.value = false
}

const startInterview = async (item) => {
  if (store.isAdmin || !store.isCandidate) {
    ElMessage.warning('仅候选人可以参加面试')
    return
  }
  currentScenario.value = item
  interviewType.value = 'quiz'
  scheduleRange.value = []
  selectedPaperId.value = null
  availablePapers.value = []
  startDialogVisible.value = true
  try {
    const res = await listAvailablePapers(item.id)
    availablePapers.value = res.data || []
  } catch (e) { /* handled */ }
}

const confirmStart = async () => {
  if (!currentScenario.value) return
  starting.value = true
  try {
    // 预约时间：填写则进入预约（待开始），留空则立即开始（进行中）
    const sched = scheduleRange.value && scheduleRange.value.length === 2 ? scheduleRange.value : null
    const payload = {
      scenarioId: currentScenario.value.id,
      paperId: selectedPaperId.value || null,
      scheduledAt: sched ? sched[0] : null,
      plannedEndAt: sched ? sched[1] : null,
      metadataJson: JSON.stringify({ device: 'web', type: interviewType.value })
    }
    // 答题与视频统一走会话接口创建（支持预约时间、生成 roomCode）
    const res = await createSession(payload)
    const record = res.data
    startDialogVisible.value = false
    if (sched) {
      ElMessage.success('面试已预约，请在预约时间进入面试')
      router.push('/records')
    } else {
      ElMessage.success('面试已开始')
      if (interviewType.value === 'video') {
        router.push(`/interview-room/${record.id}`)
      } else {
        router.push(`/interview/${record.id}?scenarioId=${currentScenario.value.id}`)
      }
    }
  } catch (e) { /* handled */ }
  starting.value = false
}

const openCreateDialog = () => {
  editingId.value = null
  Object.assign(form, { name: '', description: '', techField: '', difficulty: 1, coverImage: '' })
  dialogVisible.value = true
}

const openEditDialog = (item) => {
  editingId.value = item.id
  Object.assign(form, {
    name: item.name, description: item.description,
    techField: item.techField, difficulty: item.difficulty,
    coverImage: item.coverImage || ''
  })
  dialogVisible.value = true
}

const handleSave = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) {
      await updateScenario(editingId.value, { ...form })
      ElMessage.success('场景已更新')
    } else {
      await createScenario({ ...form })
      ElMessage.success('场景已创建')
    }
    dialogVisible.value = false
    fetchScenarios()
  } catch (e) { /* handled */ }
  saving.value = false
}

const handleDelete = async (item) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除场景「${item.name}」吗？此操作不可恢复。`,
      '删除场景',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteScenario(item.id)
    ElMessage.success('场景已删除')
    fetchScenarios()
  } catch (e) { /* cancelled or error */ }
}

onMounted(fetchScenarios)
</script>
