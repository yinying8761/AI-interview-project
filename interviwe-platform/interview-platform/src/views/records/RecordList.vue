<template>
  <div class="page-container">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="4" v-for="s in statCards" :key="s.label">
        <el-card class="stat-card" :style="{ borderTop: `3px solid ${s.color}` }">
          <div class="stat-value" :style="{ color: s.color }">{{ s.value }}</div>
          <div class="stat-label">{{ s.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选区域 -->
    <el-card class="filter-card">
      <el-row :gutter="12" align="middle">
        <el-col :span="6">
          <el-input
            v-model="filterForm.keyword"
            placeholder="搜索候选人/面试官/面试名称"
            prefix-icon="Search"
            clearable
            @input="handleFilter"
          />
        </el-col>
        <el-col :span="4">
          <el-select
            v-model="filterForm.status"
            placeholder="面试状态"
            multiple
            collapse-tags
            clearable
            @change="handleFilter"
          >
            <el-option v-for="s in statusOptions" :key="s" :label="s" :value="s" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select
            v-model="filterForm.recommendation"
            placeholder="推荐结果"
            multiple
            collapse-tags
            clearable
            @change="handleFilter"
          >
            <el-option v-for="r in recommendOptions" :key="r" :label="r" :value="r" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width:100%"
            @change="handleFilter"
          />
        </el-col>
        <el-col :span="4">
          <el-button @click="resetFilter">重置筛选</el-button>
          <el-button type="primary" @click="handleCreate">+ 新建记录</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <el-table
        v-loading="recordStore.loading"
        :data="recordStore.pagedRecords"
        border
        stripe
        row-key="id"
        @sort-change="handleSortChange"
      >
        <el-table-column type="index" label="序号" width="60" align="center" />

        <el-table-column prop="interviewName" label="面试名称" min-width="180" show-overflow-tooltip />

        <el-table-column prop="candidateName" label="候选人" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="success">{{ row.candidateName }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="interviewerName" label="面试官" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="primary">{{ row.interviewerName }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="interviewTime" label="面试时间" width="160" sortable="custom">
          <template #default="{ row }">
            {{ formatDateTime(row.interviewTime) }}
          </template>
        </el-table-column>

        <el-table-column prop="duration" label="时长(分钟)" width="100" align="center">
          <template #default="{ row }">
            {{ row.duration > 0 ? row.duration : '-' }}
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTypeMap[row.status]" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="totalScore" label="综合分" width="90" sortable="custom" align="center">
          <template #default="{ row }">
            <span v-if="row.status === '已完成'" :style="{ color: getScoreColor(row.totalScore), fontWeight: 700 }">
              {{ row.totalScore }}
            </span>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>

        <el-table-column prop="recommendation" label="推荐结论" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.status === '已完成'" :type="recommendTypeMap[row.recommendation]" size="small">
              {{ row.recommendation }}
            </el-tag>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <!-- 查看报告（已完成才可以） -->
            <el-button
              v-if="row.status === '已完成'"
              link
              type="primary"
              size="small"
              @click="router.push(`/records/${row.id}/report`)"
            >
              <el-icon><Document /></el-icon>查看报告
            </el-button>

            <!-- 打分（待面试/进行中时可打分） -->
            <el-button
              v-if="['待面试','进行中'].includes(row.status)"
              link
              type="warning"
              size="small"
              @click="router.push(`/records/${row.id}/score`)"
            >
              <el-icon><EditPen /></el-icon>打分
            </el-button>

            <!-- 取消 -->
            <el-button
              v-if="['待面试','进行中'].includes(row.status)"
              link
              type="danger"
              size="small"
              @click="handleCancel(row)"
            >
              取消
            </el-button>

            <!-- 删除（已完成/已取消时可删除） -->
            <el-popconfirm
              v-if="['已完成','已取消'].includes(row.status)"
              title="确认删除该面试记录？"
              @confirm="handleDelete(row.id)"
            >
              <template #reference>
                <el-button link type="danger" size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="recordStore.pagination.currentPage"
          v-model:page-size="recordStore.pagination.pageSize"
          :total="recordStore.pagination.total"
          :page-sizes="recordStore.pagination.pageSizes"
          layout="total, sizes, prev, pager, next, jumper"
          background
        />
      </div>
    </el-card>

    <!-- 新建记录对话框 -->
    <el-dialog v-model="createDialogVisible" title="新建面试记录" width="500px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="面试名称" prop="interviewName">
          <el-input v-model="createForm.interviewName" placeholder="请输入面试名称" />
        </el-form-item>
        <el-form-item label="候选人" prop="candidateName">
          <el-input v-model="createForm.candidateName" placeholder="候选人姓名" />
        </el-form-item>
        <el-form-item label="面试官" prop="interviewerName">
          <el-input v-model="createForm.interviewerName" placeholder="面试官姓名" />
        </el-form-item>
        <el-form-item label="面试时间" prop="interviewTime">
          <el-date-picker
            v-model="createForm.interviewTime"
            type="datetime"
            placeholder="请选择面试时间"
            value-format="YYYY-MM-DDTHH:mm:ssZ"
            style="width:100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmCreate">确认创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useRecordStore } from '@/stores/record'
import { formatDateTime } from '@/utils/helpers'

const router = useRouter()
const recordStore = useRecordStore()

const statusOptions = ['待面试', '进行中', '已完成', '已取消']
const recommendOptions = ['强烈推荐', '推荐', '待定', '不推荐']

const statusTypeMap: Record<string, string> = {
  '待面试': 'info',
  '进行中': 'warning',
  '已完成': 'success',
  '已取消': 'danger'
}

const recommendTypeMap: Record<string, string> = {
  '强烈推荐': 'success',
  '推荐': 'primary',
  '待定': 'warning',
  '不推荐': 'danger'
}

function getScoreColor(score: number) {
  if (score >= 85) return '#67C23A'
  if (score >= 70) return '#409EFF'
  if (score >= 60) return '#E6A23C'
  return '#F56C6C'
}

// 统计卡片
const stats = computed(() => recordStore.statistics)
const statCards = computed(() => [
  { label: '全部记录', value: stats.value.total,        color: '#909399' },
  { label: '待面试',   value: stats.value.pending,      color: '#909399' },
  { label: '进行中',   value: stats.value.inProgress,   color: '#E6A23C' },
  { label: '已完成',   value: stats.value.completed,    color: '#67C23A' },
  { label: '已取消',   value: stats.value.cancelled,    color: '#F56C6C' },
  { label: '平均分',   value: stats.value.avgScore,     color: '#409EFF' }
])

// 筛选
const filterForm = reactive({
  keyword: '',
  status: [] as string[],
  recommendation: [] as string[],
  dateRange: null as [string, string] | null
})

function handleFilter() {
  recordStore.setFilter({
    keyword: filterForm.keyword,
    status: filterForm.status,
    recommendation: filterForm.recommendation,
    dateRange: filterForm.dateRange as [string, string] | undefined
  })
}

function resetFilter() {
  filterForm.keyword = ''
  filterForm.status = []
  filterForm.recommendation = []
  filterForm.dateRange = null
  recordStore.setFilter({})
}

function handleSortChange({ prop, order }: { prop: string; order: any }) {
  recordStore.setSort(prop, order)
}

// 取消面试
async function handleCancel(row: any) {
  await ElMessageBox.confirm(`确认将「${row.candidateName}」的面试记录设为已取消？`, '取消确认', { type: 'warning' })
  await recordStore.updateStatus(row.id, '已取消')
  ElMessage.success('已标记为取消')
}

// 删除
async function handleDelete(id: number) {
  await recordStore.deleteRecord(id)
  ElMessage.success('删除成功')
}

// 新建记录
const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  interviewName: '',
  candidateName: '',
  interviewerName: '',
  interviewTime: ''
})
const createRules = {
  interviewName: [{ required: true, message: '请输入面试名称', trigger: 'blur' }],
  candidateName: [{ required: true, message: '请输入候选人姓名', trigger: 'blur' }],
  interviewerName: [{ required: true, message: '请输入面试官姓名', trigger: 'blur' }],
  interviewTime: [{ required: true, message: '请选择面试时间', trigger: 'change' }]
}

function handleCreate() {
  createDialogVisible.value = true
}

async function confirmCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return

  await recordStore.createRecord({
    configId: 0,
    interviewName: createForm.interviewName,
    candidateId: 0,
    candidateName: createForm.candidateName,
    interviewerId: 0,
    interviewerName: createForm.interviewerName,
    interviewTime: createForm.interviewTime,
    duration: 0,
    status: '待面试',
    scores: [],
    totalScore: 0,
    overallComment: '',
    recommendation: '待定'
  })

  createDialogVisible.value = false
  ElMessage.success('面试记录已创建')
  Object.assign(createForm, { interviewName: '', candidateName: '', interviewerName: '', interviewTime: '' })
}

onMounted(() => {
  // 确保统计数据响应式更新
})
</script>

<style lang="scss" scoped>
.page-container {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.stat-row {
  .stat-card {
    text-align: center;
    padding: 4px;
    :deep(.el-card__body) { padding: 16px 12px; }
    .stat-value {
      font-size: 28px;
      font-weight: 700;
      line-height: 1.2;
    }
    .stat-label {
      font-size: 12px;
      color: #909399;
      margin-top: 4px;
    }
  }
}

.filter-card :deep(.el-card__body) { padding: 14px 16px; }

.table-card {
  :deep(.el-card__body) { padding: 0; }
}

.pagination-wrap {
  padding: 14px 16px;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #EBEEF5;
}
</style>
