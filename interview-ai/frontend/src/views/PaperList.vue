<template>
  <div class="page-container">
    <div class="flex-between" style="margin-bottom: 20px">
      <div>
        <h2 style="font-size: 20px; font-weight: 600">📄 试卷管理</h2>
        <p style="margin-top: 7px; color: #909399; font-size: 13px">
          查看智能组卷生成的试卷，可作为固定题单用于面试。
        </p>
      </div>
      <div style="display: flex; gap: 12px">
        <el-select v-model="filterScenario" placeholder="选择场景" clearable style="width: 180px" @change="fetchPapers">
          <el-option v-for="s in scenarioOptions" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-button type="primary" plain @click="fetchPapers" :icon="Refresh">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table :data="papers" style="width: 100%" v-loading="loading" empty-text="暂无试卷">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="title" label="试卷名称" min-width="200" />
        <el-table-column label="场景" min-width="140">
          <template #default="{ row }">{{ scenarioName(row.scenarioId) }}</template>
        </el-table-column>
        <el-table-column label="轮次" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ roundText(row.round) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalCount" label="题目数" width="90" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="生成时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="openDetail(row.id)">查看题目</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailVisible" title="试卷题目" width="720px">
      <template v-if="detail">
        <el-descriptions :column="2" border style="margin-bottom: 12px">
          <el-descriptions-item label="试卷ID">{{ detail.paper.id }}</el-descriptions-item>
          <el-descriptions-item label="试卷名称">{{ detail.paper.title }}</el-descriptions-item>
          <el-descriptions-item label="轮次">{{ roundText(detail.paper.round) }}</el-descriptions-item>
          <el-descriptions-item label="题目数量">{{ detail.paper.totalCount }}</el-descriptions-item>
        </el-descriptions>
        <el-table :data="detail.questions" style="width: 100%">
          <el-table-column prop="sortOrder" label="序号" width="70" />
          <el-table-column prop="content" label="题目内容" min-width="320" show-overflow-tooltip />
          <el-table-column label="难度" width="80" align="center">
            <template #default="{ row }">{{ difficultyText(row.difficulty) }}</template>
          </el-table-column>
          <el-table-column label="类型" width="90" align="center">
            <template #default="{ row }">{{ typeText(row.type) }}</template>
          </el-table-column>
        </el-table>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { listPapers, getPaperDetail, listScenarios } from '../api/interview'

const loading = ref(false)
const papers = ref([])
const scenarioOptions = ref([])
const filterScenario = ref(null)
const detailVisible = ref(false)
const detail = ref(null)

const roundText = (r) => ({ TECH_ROUND_1: '技术一面', TECH_ROUND_2: '技术二面', HR_ROUND: 'HR 面' }[r] || r || '—')
const statusText = (s) => ({ GENERATED: '已生成', ENABLED: '启用', DISABLED: '停用' }[s] || s)
const statusType = (s) => ({ GENERATED: 'info', ENABLED: 'success', DISABLED: 'danger' }[s] || 'info')
const difficultyText = (d) => ({ 1: '简单', 2: '中等', 3: '困难' }[d] || '未知')
const typeText = (t) => ({ technical: '技术题', behavioral: '行为题', hr: 'HR 面' }[t] || t)
const scenarioName = (id) => scenarioOptions.value.find((s) => s.id === id)?.name || `场景#${id}`
const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

const fetchScenarios = async () => {
  try {
    const res = await listScenarios()
    scenarioOptions.value = res.data || []
  } catch (e) { /* handled */ }
}

const fetchPapers = async () => {
  loading.value = true
  try {
    const params = {}
    if (filterScenario.value) params.scenarioId = filterScenario.value
    const res = await listPapers(params)
    papers.value = res.data || []
  } catch (e) { /* handled */ }
  loading.value = false
}

const openDetail = async (paperId) => {
  try {
    const res = await getPaperDetail(paperId)
    detail.value = res.data
    detailVisible.value = true
  } catch (e) { /* handled */ }
}

onMounted(() => {
  fetchScenarios()
  fetchPapers()
})
</script>