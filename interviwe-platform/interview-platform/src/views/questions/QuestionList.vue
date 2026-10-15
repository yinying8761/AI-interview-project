<template>
  <div class="page-container">
    <div class="page-header">
      <h2>题库管理</h2>
      <el-button type="primary" @click="$router.push('/questions/create')">
        <el-icon><Plus /></el-icon> 新建题目
      </el-button>
    </div>

    <!-- 筛选区域 -->
    <el-card class="filter-card">
      <div class="filter-row">
        <el-input
          v-model="filterForm.keyword"
          placeholder="搜索题目内容..."
          clearable
          style="width: 260px"
          @input="handleFilterChange"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <el-select
          v-model="filterForm.type"
          placeholder="题型"
          multiple
          collapse-tags
          clearable
          style="width: 180px"
          @change="handleFilterChange"
        >
          <el-option v-for="t in typeOpts" :key="t" :label="t" :value="t" />
        </el-select>

        <el-select
          v-model="filterForm.difficulty"
          placeholder="难度"
          multiple
          collapse-tags
          clearable
          style="width: 180px"
          @change="handleFilterChange"
        >
          <el-option v-for="d in difficultyOpts" :key="d.value" :label="d.label" :value="d.value" />
        </el-select>

        <el-select
          v-model="filterForm.direction"
          placeholder="方向"
          multiple
          collapse-tags
          clearable
          style="width: 180px"
          @change="handleFilterChange"
        >
          <el-option v-for="d in directionOpts" :key="d" :label="d" :value="d" />
        </el-select>

        <el-select
          v-model="filterForm.status"
          placeholder="状态"
          multiple
          collapse-tags
          clearable
          style="width: 160px"
          @change="handleFilterChange"
        >
          <el-option v-for="s in statusOpts" :key="s" :label="s" :value="s" />
        </el-select>

        <el-button @click="resetFilter">重置</el-button>
      </div>
    </el-card>

    <!-- 批量操作 -->
    <div class="batch-toolbar" v-if="selectedRows.length > 0">
      <span class="selected-info">已选 {{ selectedRows.length }} 条</span>
      <el-button size="small" type="success" @click="batchUpdateStatus('启用')">批量启用</el-button>
      <el-button size="small" type="warning" @click="batchUpdateStatus('停用')">批量停用</el-button>
      <el-button size="small" type="danger" @click="batchDelete">批量删除</el-button>
    </div>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <el-table
        :data="questionStore.pagedQuestions"
        v-loading="loading"
        border
        stripe
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
        style="width: 100%"
      >
        <el-table-column type="selection" width="50" fixed="left" />
        <el-table-column type="index" label="序号" width="60" align="center" />

        <el-table-column prop="content" label="题目内容" min-width="300" show-overflow-tooltip sortable="custom">
          <template #default="{ row }">
            <el-tooltip :content="row.content" placement="top" :show-after="500">
              <span class="content-text">{{ row.content }}</span>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column prop="type" label="题型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="typeColorMap[row.type]" size="small">{{ row.type }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="difficulty" label="难度" width="120" align="center">
          <template #default="{ row }">
            <span class="stars">
              <span v-for="i in 5" :key="i" :class="['star', i <= row.difficulty ? 'filled' : '']">★</span>
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="direction" label="方向" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="directionColorMap[row.direction]" size="small" effect="plain">{{ row.direction }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="usageCount" label="使用次数" width="100" align="center" sortable="custom" />

        <el-table-column prop="passRate" label="通过率" width="160" sortable="custom">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress
                :percentage="row.passRate"
                :status="getProgressStatus(row.passRate)"
                :stroke-width="8"
                style="flex: 1; min-width: 80px"
              />
              <span class="progress-val">{{ row.passRate }}%</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              :type="statusColorMap[row.status]"
              size="small"
              effect="plain"
            >{{ row.status }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <el-button size="small" type="primary" text @click="editQuestion(row.id)">
              <el-icon><Edit /></el-icon> 编辑
            </el-button>
            <el-button size="small" type="success" text @click="copyQuestion(row)">
              <el-icon><CopyDocument /></el-icon> 复制
            </el-button>
            <el-button size="small" type="danger" text @click="deleteQuestion(row.id)">
              <el-icon><Delete /></el-icon> 删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="questionStore.pagination.currentPage"
          v-model:page-size="questionStore.pagination.pageSize"
          :page-sizes="questionStore.pagination.pageSizes"
          :total="questionStore.pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="onPageSizeChange"
          @current-change="onPageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useQuestionStore } from '@/stores/question'
import { typeColorMap, directionColorMap, statusColorMap } from '@/utils/helpers'
import type { Question } from '@/types'

const router = useRouter()
const questionStore = useQuestionStore()
const loading = ref(false)
const selectedRows = ref<Question[]>([])

const filterForm = reactive({
  keyword: '',
  type: [] as string[],
  difficulty: [] as number[],
  direction: [] as string[],
  status: [] as string[]
})

const typeOpts = ['单选', '多选', '判断', '编程']
const difficultyOpts = [
  { label: '★ 简单', value: 1 },
  { label: '★★ 较易', value: 2 },
  { label: '★★★ 中等', value: 3 },
  { label: '★★★★ 较难', value: 4 },
  { label: '★★★★★ 困难', value: 5 }
]
const directionOpts = ['前端', '后端', '算法', '大数据']
const statusOpts = ['启用', '停用', '审核中']

function handleFilterChange() {
  questionStore.setFilter({
    keyword: filterForm.keyword,
    type: filterForm.type.length > 0 ? filterForm.type : undefined,
    difficulty: filterForm.difficulty.length > 0 ? filterForm.difficulty as any[] : undefined,
    direction: filterForm.direction.length > 0 ? filterForm.direction : undefined,
    status: filterForm.status.length > 0 ? filterForm.status : undefined
  })
}

function resetFilter() {
  filterForm.keyword = ''
  filterForm.type = []
  filterForm.difficulty = []
  filterForm.direction = []
  filterForm.status = []
  questionStore.setFilter({})
}

function handleSelectionChange(rows: Question[]) {
  selectedRows.value = rows
}

function handleSortChange({ prop, order }: { prop: string; order: any }) {
  questionStore.setSort(prop, order)
}

function onPageChange(page: number) {
  questionStore.pagination.currentPage = page
}

function onPageSizeChange(size: number) {
  questionStore.pagination.pageSize = size
  questionStore.pagination.currentPage = 1
}

function editQuestion(id: number) {
  router.push(`/questions/edit/${id}`)
}

function copyQuestion(row: Question) {
  const copy: Question = {
    ...JSON.parse(JSON.stringify(row)),
    id: Date.now(),
    content: `【复制】${row.content}`,
    usageCount: 0,
    status: '审核中',
    createTime: new Date().toISOString()
  }
  questionStore.saveQuestion(copy)
  ElMessage.success('题目已复制')
}

function deleteQuestion(id: number) {
  ElMessageBox.confirm('确认删除该题目？', '删除确认', { type: 'warning' }).then(() => {
    questionStore.deleteQuestion(id)
    ElMessage.success('已删除')
  }).catch(() => {})
}

function batchDelete() {
  const ids = selectedRows.value.map(r => r.id)
  ElMessageBox.confirm(`确认批量删除 ${ids.length} 条题目？`, '批量删除', { type: 'warning' }).then(() => {
    questionStore.batchDelete(ids)
    selectedRows.value = []
    ElMessage.success(`已删除 ${ids.length} 条`)
  }).catch(() => {})
}

function batchUpdateStatus(status: Question['status']) {
  const ids = selectedRows.value.map(r => r.id)
  questionStore.batchUpdateStatus(ids, status)
  selectedRows.value = []
  ElMessage.success(`已批量${status === '启用' ? '启用' : '停用'} ${ids.length} 条`)
}

function getProgressStatus(val: number): '' | 'success' | 'exception' | 'warning' {
  if (val >= 80) return 'success'
  if (val >= 50) return ''
  if (val >= 30) return 'warning'
  return 'exception'
}
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
}

.filter-card {
  :deep(.el-card__body) { padding: 16px; }
  .filter-row {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    align-items: center;
  }
}

.batch-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  background: #ecf5ff;
  border-radius: 6px;
  border: 1px solid #b3d8ff;
  .selected-info {
    color: #409EFF;
    font-weight: 600;
    font-size: 13px;
  }
}

.table-card {
  :deep(.el-card__body) { padding: 0; }
}

.content-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 280px;
}

.stars {
  .star {
    color: #ddd;
    font-size: 15px;
    &.filled { color: #f5a623; }
  }
}

.progress-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  .progress-val {
    font-size: 12px;
    color: #606266;
    white-space: nowrap;
  }
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px;
  border-top: 1px solid #EBEEF5;
}
</style>
