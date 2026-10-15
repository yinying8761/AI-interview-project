import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { InterviewRecord, RecordFilter, ScoreItem, Pagination } from '@/types'
import { recordApi, type BackendRecord } from '@/api'

// 后端状态 → 页面中文状态
const STATUS_MAP: Record<string, InterviewRecord['status']> = {
  pending: '待面试',
  ongoing: '进行中',
  completed: '已完成',
  cancelled: '已取消'
}

/** 后端记录 → 页面模型（补齐页面需要的展示字段） */
function toPageModel(r: BackendRecord, index: number): InterviewRecord {
  return {
    id: r.id,
    configId: r.scenarioId,
    interviewName: `面试-场景${r.scenarioId}`,
    candidateId: r.userId,
    candidateName: `用户${r.userId}`,
    interviewerId: 0,
    interviewerName: '—',
    interviewTime: r.startedAt || r.createdAt || '',
    duration: r.duration ?? 0,
    status: STATUS_MAP[r.status] ?? '待面试',
    scores: [],
    totalScore: r.score ?? 0,
    overallComment: r.aiFeedback || '',
    recommendation: '待定',
    createTime: r.createdAt || '',
    updateTime: r.completedAt || r.createdAt || ''
  }
}

export const useRecordStore = defineStore('record', () => {
  // ===================== State =====================
  const allRecords = ref<InterviewRecord[]>([])
  const filter = ref<RecordFilter>({})
  const sortField = ref('')
  const sortOrder = ref<'ascending' | 'descending' | null>(null)
  const pagination = ref<Pagination>({
    currentPage: 1,
    pageSize: 10,
    total: 0,
    pageSizes: [10, 20, 50]
  })
  const loading = ref(false)

  // ===================== 拉取我的记录（真实接口） =====================
  async function fetchRecords() {
    loading.value = true
    try {
      const list = await recordApi.list()
      allRecords.value = list.map((r, i) => toPageModel(r, i))
    } catch (e: any) {
      allRecords.value = []
      throw e
    } finally {
      loading.value = false
    }
  }

  // ===================== Computed =====================
  const filteredRecords = computed(() => {
    let list = [...allRecords.value]

    if (filter.value.keyword) {
      const kw = filter.value.keyword.toLowerCase()
      list = list.filter(r =>
        r.candidateName.toLowerCase().includes(kw) ||
        r.interviewerName.toLowerCase().includes(kw) ||
        r.interviewName.toLowerCase().includes(kw)
      )
    }
    if (filter.value.status && filter.value.status.length > 0) {
      list = list.filter(r => filter.value.status!.includes(r.status))
    }
    if (filter.value.recommendation && filter.value.recommendation.length > 0) {
      list = list.filter(r => filter.value.recommendation!.includes(r.recommendation))
    }

    if (sortField.value && sortOrder.value) {
      list.sort((a: any, b: any) => {
        const av = a[sortField.value]
        const bv = b[sortField.value]
        if (sortOrder.value === 'ascending') return av > bv ? 1 : -1
        return av < bv ? 1 : -1
      })
    }

    return list
  })

  const pagedRecords = computed(() => {
    pagination.value.total = filteredRecords.value.length
    const start = (pagination.value.currentPage - 1) * pagination.value.pageSize
    return filteredRecords.value.slice(start, start + pagination.value.pageSize)
  })

  const statistics = computed(() => {
    const completed = allRecords.value.filter(r => r.status === '已完成')
    const avg = completed.length
      ? Math.round(completed.reduce((s, r) => s + r.totalScore, 0) / completed.length)
      : 0
    return {
      total: allRecords.value.length,
      completed: completed.length,
      pending: allRecords.value.filter(r => r.status === '待面试').length,
      inProgress: allRecords.value.filter(r => r.status === '进行中').length,
      cancelled: allRecords.value.filter(r => r.status === '已取消').length,
      avgScore: avg,
      recommendCount: 0,
      recommendRate: 0
    }
  })

  // ===================== 工具 =====================
  function getRecordById(id: number): InterviewRecord | undefined {
    return allRecords.value.find(r => r.id === id)
  }

  function setFilter(f: RecordFilter) {
    filter.value = { ...f }
    pagination.value.currentPage = 1
  }

  function setSort(field: string, order: 'ascending' | 'descending' | null) {
    sortField.value = field
    sortOrder.value = order
  }

  async function createRecord(data: Partial<Omit<InterviewRecord, 'id' | 'createTime' | 'updateTime'>>): Promise<InterviewRecord | null> {
    const scenarioId = data.configId ?? data.candidateId ?? 1
    const record = await recordApi.create(Number(scenarioId))
    const model = toPageModel(record, allRecords.value.length)
    allRecords.value.unshift(model)
    return model
  }

  async function submitScore(
    id: number,
    scores: ScoreItem[],
    overallComment: string,
    recommendation: InterviewRecord['recommendation']
  ): Promise<boolean> {
    // 后端评分接口为课后进阶（record:score 权限已就绪），本地模拟打分展示
    const record = allRecords.value.find(r => r.id === id)
    if (!record) return false
    const total = scores.length
      ? Math.round(scores.reduce((s, item) => s + item.score, 0) / scores.length * 10)
      : 0
    record.scores = scores
    record.totalScore = total
    record.overallComment = overallComment
    record.recommendation = recommendation
    record.status = '已完成'
    return true
  }

  async function updateStatus(id: number, status: InterviewRecord['status']): Promise<boolean> {
    const record = allRecords.value.find(r => r.id === id)
    if (!record) return false
    // 后端仅支持 pending 取消；其余由面试流程驱动
    if (status === '已取消') {
      try {
        await recordApi.cancel(id)
      } catch {
        return false
      }
    }
    record.status = status
    return true
  }

  async function deleteRecord(id: number): Promise<boolean> {
    return true
  }

  return {
    allRecords,
    filter,
    pagination,
    loading,
    filteredRecords,
    pagedRecords,
    statistics,
    getRecordById,
    setFilter,
    setSort,
    fetchRecords,
    createRecord,
    submitScore,
    updateStatus,
    deleteRecord
  }
})