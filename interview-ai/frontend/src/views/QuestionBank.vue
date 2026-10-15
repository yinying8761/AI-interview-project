<template>
  <div class="page-container">
    <!-- 页头 -->
    <div class="flex-between" style="margin-bottom: 20px">
      <div>
        <div style="display: flex; align-items: center; gap: 10px">
          <h2 style="font-size: 20px; font-weight: 600">
            {{ store.isInterviewerPortal ? '📚 题库参考' : '📝 题库管理' }}
          </h2>
          <el-tag v-if="store.isInterviewerPortal" type="info" effect="plain" size="small">只读</el-tag>
        </div>
        <p v-if="store.isInterviewerPortal" style="margin-top: 7px; color: #909399; font-size: 13px">
          可查看题目、参考答案和评分关键词，题目新增、编辑和删除由管理员维护。
        </p>
      </div>
      <div style="display: flex; gap: 12px; flex-wrap: wrap">
        <el-select v-model="query.scenarioId" placeholder="选择场景" clearable style="width: 180px"
          @change="fetchQuestions">
          <el-option v-for="s in scenarioOptions" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-select v-model="query.difficulty" placeholder="难度" clearable style="width: 120px"
          @change="fetchQuestions">
          <el-option label="简单" :value="1" />
          <el-option label="中等" :value="2" />
          <el-option label="困难" :value="3" />
        </el-select>
        <el-input v-model="query.keyword" placeholder="搜索题目内容" clearable style="width: 180px"
          @keyup.enter="fetchQuestions" />
        <el-button type="primary" plain @click="fetchQuestions">
          <el-icon style="margin-right: 4px"><Search /></el-icon>查询
        </el-button>

        <template v-if="store.hasPermission('question:create')">
          <el-button type="primary" @click="openCreateDialog">
            <el-icon style="margin-right: 4px"><Plus /></el-icon>新增题目
          </el-button>
          <el-button @click="openImportDialog">
            <el-icon style="margin-right: 4px"><Upload /></el-icon>批量导入
          </el-button>
          <el-button type="success" @click="openGroupDialog">
            <el-icon style="margin-right: 4px"><MagicStick /></el-icon>智能组卷
          </el-button>
        </template>
        <el-button v-if="store.hasPermission('question:view')" @click="handleExport">
          <el-icon style="margin-right: 4px"><Download /></el-icon>导出
        </el-button>
      </div>
    </div>

    <!-- 题目列表 -->
    <el-card shadow="never">
      <el-table :data="questions" style="width: 100%" v-loading="loading" empty-text="暂无题目">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="content" label="题目内容" min-width="240" show-overflow-tooltip />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.type)">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="难度" width="90" align="center">
          <template #default="{ row }">
            <el-rate :model-value="row.difficulty" disabled size="small" style="display: inline-flex" />
          </template>
        </el-table-column>
        <el-table-column prop="keywords" label="关键词" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-for="kw in splitKeywords(row.keywords)" :key="kw" size="small" type="info"
              style="margin-right: 4px; margin-bottom: 2px">{{ kw }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button text type="primary" size="small" @click="viewDetail(row)">查看</el-button>
              <el-button v-if="store.hasPermission('question:update')"
                text type="warning" size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-button v-if="store.hasPermission('question:delete')"
                text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div style="display: flex; justify-content: flex-end; margin-top: 14px">
        <el-pagination
          v-model:current-page="page.current"
          v-model:page-size="page.size"
          :page-sizes="[10, 20, 50]"
          :total="page.total"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchQuestions"
          @size-change="fetchQuestions"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑题目' : '新增题目'" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="questionRules" label-width="90px">
        <el-form-item label="所属场景" prop="scenarioId">
          <el-select v-model="form.scenarioId" placeholder="请选择场景" style="width: 100%">
            <el-option v-for="s in scenarioOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题目内容" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="3" placeholder="请输入题目内容" />
        </el-form-item>
        <el-form-item label="题目类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio value="technical">技术题</el-radio>
            <el-radio value="behavioral">行为题</el-radio>
            <el-radio value="hr">HR 面</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="难度" prop="difficulty">
          <el-radio-group v-model="form.difficulty">
            <el-radio :value="1">简单</el-radio>
            <el-radio :value="2">中等</el-radio>
            <el-radio :value="3">困难</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="参考答案" prop="expectedAnswer">
          <el-input v-model="form.expectedAnswer" type="textarea" :rows="2" placeholder="请输入参考答案" />
        </el-form-item>
        <el-form-item label="评分关键词" prop="keywords">
          <el-input v-model="form.keywords" placeholder="使用英文逗号分隔" />
        </el-form-item>
        <el-form-item label="标签" prop="tags">
          <el-select v-model="form.tags" multiple filterable allow-create default-first-option
            placeholder="选择或输入标签编码" style="width: 100%">
            <el-option v-for="t in tagOptions" :key="t.code" :label="`${t.name} (${t.code})`" :value="t.code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="题目详情" width="600px">
      <el-descriptions v-if="detailData" :column="1" border>
        <el-descriptions-item label="ID">{{ detailData.id }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ typeText(detailData.type) }}</el-descriptions-item>
        <el-descriptions-item label="难度">{{ difficultyText(detailData.difficulty) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detailData.status === 1 ? '启用' : '禁用' }}</el-descriptions-item>
        <el-descriptions-item label="题目内容" :span="2">{{ detailData.content }}</el-descriptions-item>
        <el-descriptions-item label="参考答案" :span="2">{{ detailData.expectedAnswer || '—' }}</el-descriptions-item>
        <el-descriptions-item label="关键词" :span="2">
          <el-tag v-for="kw in splitKeywords(detailData.keywords)" :key="kw" size="small"
            type="info" style="margin-right: 4px">{{ kw }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="标签" :span="2">
          <el-tag v-for="code in detailTags" :key="code" size="small"
            type="success" style="margin-right: 4px">{{ code }}</el-tag>
          <span v-if="!detailTags.length">—</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入弹窗 -->
    <el-dialog v-model="importVisible" title="批量导入题目" width="560px">
      <div style="margin-bottom: 12px; color: #606266; font-size: 13px">
        请上传 Excel 模板，列名：场景ID、题目内容、题型、难度、参考答案、评分关键词、标签编码。
      </div>
      <el-upload
        ref="importUploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :on-change="onImportFileChange"
        :on-remove="() => (importFile = null)"
      >
        <el-button type="primary" plain>
          <el-icon style="margin-right: 4px"><Upload /></el-icon>选择文件
        </el-button>
      </el-upload>

      <el-alert v-if="importResult" style="margin-top: 16px" :type="importResult.failCount > 0 ? 'warning' : 'success'"
        :title="`共 ${importResult.total} 行，成功 ${importResult.successCount}，失败 ${importResult.failCount}`" :closable="false">
        <div v-for="(e, i) in importResult.errors" :key="i" style="font-size: 13px">
          第 {{ e.row }} 行：{{ e.reason }}
        </div>
      </el-alert>

      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="handleImport">
          开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 智能组卷弹窗 -->
    <el-dialog v-model="groupVisible" title="智能组卷" width="680px">
      <el-form :model="groupForm" label-width="110px">
        <el-form-item label="面试场景">
          <el-select v-model="groupForm.scenarioId" placeholder="请选择场景" style="width: 100%">
            <el-option v-for="s in scenarioOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="面试轮次">
          <el-select v-model="groupForm.round" style="width: 100%">
            <el-option label="技术一面" :value="1" />
            <el-option label="技术二面" :value="2" />
            <el-option label="HR 面" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="试卷名称">
          <el-input v-model="groupForm.title" placeholder="例如 Java 后端一面试卷" />
        </el-form-item>
        <el-form-item label="题目数量">
          <el-input-number v-model="groupForm.totalCount" :min="1" :max="50" />
        </el-form-item>
        <el-form-item label="知识标签">
          <el-select v-model="groupForm.knowledgeTags" multiple clearable placeholder="选择知识标签" style="width: 100%">
            <el-option v-for="t in tagsByType('KNOWLEDGE')" :key="t.code" :label="`${t.name} (${t.code})`" :value="t.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位标签">
          <el-select v-model="groupForm.jobTags" multiple clearable placeholder="选择岗位标签" style="width: 100%">
            <el-option v-for="t in tagsByType('JOB')" :key="t.code" :label="`${t.name} (${t.code})`" :value="t.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="轮次标签">
          <el-select v-model="groupForm.roundTags" multiple clearable placeholder="选择轮次标签" style="width: 100%">
            <el-option v-for="t in tagsByType('ROUND')" :key="t.code" :label="`${t.name} (${t.code})`" :value="t.code" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度计划">
          <div>
            <div style="display: flex; gap: 12px">
              <span>简单</span><el-input-number v-model="groupForm.easy" :min="0" :max="50" size="small" />
              <span>中等</span><el-input-number v-model="groupForm.medium" :min="0" :max="50" size="small" />
              <span>困难</span><el-input-number v-model="groupForm.hard" :min="0" :max="50" size="small" />
            </div>
            <p v-if="difficultyPlanSum !== groupForm.totalCount"
               style="margin-top: 6px; font-size: 12px; color: #e6a23c">
              难度计划合计 {{ difficultyPlanSum }} 与题目数量 {{ groupForm.totalCount }} 不一致，将按默认比例（约 30%/50%/20%）分配。
            </p>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupVisible = false">取消</el-button>
        <el-button type="primary" :loading="grouping" @click="handleGroup">生成试卷</el-button>
      </template>
    </el-dialog>

    <!-- 试卷详情弹窗 -->
    <el-dialog v-model="paperVisible" title="试卷详情" width="720px">
      <template v-if="paperDetail">
        <el-descriptions :column="2" border style="margin-bottom: 12px">
          <el-descriptions-item label="试卷ID">{{ paperDetail.paper.id }}</el-descriptions-item>
          <el-descriptions-item label="试卷名称">{{ paperDetail.paper.title }}</el-descriptions-item>
          <el-descriptions-item label="轮次">{{ paperDetail.paper.round }}</el-descriptions-item>
          <el-descriptions-item label="题目数量">{{ paperDetail.paper.totalCount }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="paperDetail.questions" style="width: 100%">
          <el-table-column prop="sortOrder" label="序号" width="70" />
          <el-table-column prop="content" label="题目内容" min-width="300" show-overflow-tooltip />
          <el-table-column label="难度" width="80" align="center">
            <template #default="{ row }">{{ difficultyText(row.difficulty) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="90" align="center">
            <template #default="{ row }">{{ typeText(row.type) }}</template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="paperVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload, Download, Search, MagicStick } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import {
  pageQuestions, getQuestion, createQuestion, updateQuestion, deleteQuestion,
  getQuestionTags, importQuestions, exportQuestions,
  listQuestionTags, generatePaper, getPaperDetail,
  listScenarios
} from '../api/interview'

const store = useUserStore()

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const grouping = ref(false)
const questions = ref([])
const scenarioOptions = ref([])
const tagOptions = ref([])
const detailVisible = ref(false)
const detailData = ref(null)
const detailTags = ref([])
const dialogVisible = ref(false)
const importVisible = ref(false)
const groupVisible = ref(false)
const paperVisible = ref(false)
const importUploadRef = ref()
const importFile = ref(null)
const importResult = ref(null)
const paperDetail = ref(null)
const editingId = ref(null)
const formRef = ref()

const query = reactive({ scenarioId: null, difficulty: null, keyword: '' })
const page = reactive({ current: 1, size: 10, total: 0 })

const form = reactive({
  scenarioId: null, content: '', type: 'technical',
  difficulty: 1, expectedAnswer: '', keywords: '', tags: []
})

const groupForm = reactive({
  scenarioId: null, round: 1, totalCount: 10, title: '',
  knowledgeTags: [], jobTags: [], roundTags: [],
  easy: 3, medium: 5, hard: 2
})

const questionRules = {
  scenarioId: [{ required: true, message: '请选择场景', trigger: 'change' }],
  content: [{ required: true, message: '请输入题目内容', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }]
}

const typeTag = (t) => ({ technical: 'primary', behavioral: 'warning', hr: 'success' }[t] || 'info')
const typeText = (t) => ({ technical: '技术题', behavioral: '行为题', hr: 'HR 面' }[t] || t)
const difficultyText = (d) => ({ 1: '简单', 2: '中等', 3: '困难' }[d] || '未知')
const splitKeywords = (kw) => kw ? kw.split(/[,，、]/).filter(Boolean) : []
const tagsByType = (type) => tagOptions.value.filter((t) => t.type === type)
const difficultyPlanSum = computed(() => (groupForm.easy || 0) + (groupForm.medium || 0) + (groupForm.hard || 0))

const fetchScenarios = async () => {
  try {
    const res = await listScenarios()
    scenarioOptions.value = res.data || []
  } catch (e) { /* handled */ }
}

const fetchTags = async () => {
  try {
    const res = await listQuestionTags()
    tagOptions.value = res.data || []
  } catch (e) { /* handled */ }
}

const fetchQuestions = async () => {
  loading.value = true
  try {
    const params = { current: page.current, size: page.size }
    if (query.scenarioId) params.scenarioId = query.scenarioId
    if (query.difficulty) params.difficulty = query.difficulty
    if (query.keyword) params.keyword = query.keyword
    const res = await pageQuestions(params)
    questions.value = res.data.records || []
    page.total = res.data.total || 0
  } catch (e) { /* handled */ }
  loading.value = false
}

const viewDetail = async (row) => {
  try {
    const res = await getQuestion(row.id)
    detailData.value = res.data
    const tagRes = await getQuestionTags(row.id)
    detailTags.value = tagRes.data || []
    detailVisible.value = true
  } catch (e) { /* handled */ }
}

const openCreateDialog = () => {
  editingId.value = null
  Object.assign(form, {
    scenarioId: query.scenarioId || null, content: '',
    type: 'technical', difficulty: 1, expectedAnswer: '', keywords: '', tags: []
  })
  dialogVisible.value = true
}

const openEditDialog = async (row) => {
  editingId.value = row.id
  Object.assign(form, {
    scenarioId: row.scenarioId, content: row.content,
    type: row.type, difficulty: row.difficulty,
    expectedAnswer: row.expectedAnswer || '', keywords: row.keywords || '', tags: []
  })
  try {
    const tagRes = await getQuestionTags(row.id)
    form.tags = tagRes.data || []
  } catch (e) { /* handled */ }
  dialogVisible.value = true
}

const handleSave = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) {
      await updateQuestion(editingId.value, { ...form })
      ElMessage.success('题目已更新')
    } else {
      await createQuestion({ ...form })
      ElMessage.success('题目已创建')
    }
    dialogVisible.value = false
    fetchQuestions()
  } catch (e) { /* handled */ }
  saving.value = false
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除该题目吗？\n"${row.content?.substring(0, 50)}..."`,
      '删除题目',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    await deleteQuestion(row.id)
    ElMessage.success('题目已删除')
    fetchQuestions()
  } catch (e) { /* cancelled or error */ }
}

const openImportDialog = () => {
  importVisible.value = true
  importFile.value = null
  importResult.value = null
}

const onImportFileChange = (file) => {
  importFile.value = file.raw
}

const handleImport = async () => {
  if (!importFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  importing.value = true
  try {
    const res = await importQuestions(importFile.value)
    importResult.value = res.data
    ElMessage.success('导入完成')
    fetchQuestions()
  } catch (e) { /* handled */ }
  importing.value = false
}

const handleExport = async () => {
  try {
    const blob = await exportQuestions({ scenarioId: query.scenarioId || undefined })
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '题库导出.xlsx'
    a.click()
    window.URL.revokeObjectURL(url)
  } catch (e) { /* handled */ }
}

const openGroupDialog = () => {
  Object.assign(groupForm, {
    scenarioId: query.scenarioId || null, round: 1, totalCount: 10, title: '',
    knowledgeTags: [], jobTags: [], roundTags: [], easy: 3, medium: 5, hard: 2
  })
  groupVisible.value = true
}

const handleGroup = async () => {
  if (!groupForm.scenarioId) {
    ElMessage.warning('请选择场景')
    return
  }
  grouping.value = true
  try {
    const difficultyPlan = {}
    if (groupForm.easy > 0) difficultyPlan[1] = groupForm.easy
    if (groupForm.medium > 0) difficultyPlan[2] = groupForm.medium
    if (groupForm.hard > 0) difficultyPlan[3] = groupForm.hard
    const planSum = groupForm.easy + groupForm.medium + groupForm.hard

    const payload = {
      scenarioId: groupForm.scenarioId,
      round: groupForm.round,
      totalCount: groupForm.totalCount,
      title: groupForm.title,
      knowledgeTags: groupForm.knowledgeTags,
      jobTags: groupForm.jobTags,
      roundTags: groupForm.roundTags
    }
    // 仅当难度计划数量与总题数一致时下发，否则使用后端默认比例
    if (planSum === groupForm.totalCount && Object.keys(difficultyPlan).length) {
      payload.difficultyPlan = difficultyPlan
    }

    const res = await generatePaper(payload)
    ElMessage.success(`试卷已生成，ID=${res.data}`)
    groupVisible.value = false
    await openPaperDetail(res.data)
  } catch (e) { /* handled */ }
  grouping.value = false
}

const openPaperDetail = async (paperId) => {
  try {
    const res = await getPaperDetail(paperId)
    paperDetail.value = res.data
    paperVisible.value = true
  } catch (e) { /* handled */ }
}

onMounted(() => {
  fetchScenarios()
  fetchTags()
  fetchQuestions()
})
</script>

<style scoped>
.table-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-wrap: nowrap;
  gap: 4px;
  white-space: nowrap;
}

.table-actions :deep(.el-button) {
  margin-left: 0;
  padding: 4px 2px;
}
</style>