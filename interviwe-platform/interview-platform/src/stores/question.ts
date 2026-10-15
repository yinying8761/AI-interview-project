import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Question, QuestionFilter, Pagination } from '@/types'
import questionsData from '@/mock/questions.json'

export const useQuestionStore = defineStore('question', () => {
  const allQuestions = ref<Question[]>(questionsData as Question[])
  const filter = ref<QuestionFilter>({})
  const sortField = ref<string>('')
  const sortOrder = ref<'ascending' | 'descending' | null>(null)
  const pagination = ref<Pagination>({
    currentPage: 1,
    pageSize: 10,
    total: 0,
    pageSizes: [10, 20, 50, 100]
  })

  // 筛选后的数据
  const filteredQuestions = computed(() => {
    let list = [...allQuestions.value]

    if (filter.value.keyword) {
      const kw = filter.value.keyword.toLowerCase()
      list = list.filter(q => q.content.toLowerCase().includes(kw))
    }
    if (filter.value.type && filter.value.type.length > 0) {
      list = list.filter(q => filter.value.type!.includes(q.type))
    }
    if (filter.value.difficulty && filter.value.difficulty.length > 0) {
      list = list.filter(q => filter.value.difficulty!.includes(q.difficulty))
    }
    if (filter.value.direction && filter.value.direction.length > 0) {
      list = list.filter(q => filter.value.direction!.includes(q.direction))
    }
    if (filter.value.status && filter.value.status.length > 0) {
      list = list.filter(q => filter.value.status!.includes(q.status))
    }

    // 排序
    if (sortField.value && sortOrder.value) {
      list.sort((a: any, b: any) => {
        const aVal = a[sortField.value]
        const bVal = b[sortField.value]
        if (sortOrder.value === 'ascending') return aVal > bVal ? 1 : -1
        return aVal < bVal ? 1 : -1
      })
    }

    return list
  })

  // 分页后的数据
  const pagedQuestions = computed(() => {
    const total = filteredQuestions.value.length
    pagination.value.total = total
    const start = (pagination.value.currentPage - 1) * pagination.value.pageSize
    const end = start + pagination.value.pageSize
    return filteredQuestions.value.slice(start, end)
  })

  // 删除题目
  function deleteQuestion(id: number) {
    const index = allQuestions.value.findIndex(q => q.id === id)
    if (index !== -1) allQuestions.value.splice(index, 1)
  }

  // 批量删除
  function batchDelete(ids: number[]) {
    allQuestions.value = allQuestions.value.filter(q => !ids.includes(q.id))
  }

  // 批量更新状态
  function batchUpdateStatus(ids: number[], status: Question['status']) {
    allQuestions.value.forEach(q => {
      if (ids.includes(q.id)) q.status = status
    })
  }

  // 新增/更新题目
  function saveQuestion(question: Question) {
    const index = allQuestions.value.findIndex(q => q.id === question.id)
    if (index !== -1) {
      allQuestions.value[index] = { ...question }
    } else {
      allQuestions.value.unshift({ ...question })
    }
  }

  // 根据ID获取题目
  function getById(id: number): Question | undefined {
    return allQuestions.value.find(q => q.id === id)
  }

  // 设置筛选条件
  function setFilter(f: QuestionFilter) {
    filter.value = { ...f }
    pagination.value.currentPage = 1
  }

  // 设置排序
  function setSort(field: string, order: 'ascending' | 'descending' | null) {
    sortField.value = field
    sortOrder.value = order
  }

  return {
    allQuestions,
    filter,
    sortField,
    sortOrder,
    pagination,
    filteredQuestions,
    pagedQuestions,
    deleteQuestion,
    batchDelete,
    batchUpdateStatus,
    saveQuestion,
    getById,
    setFilter,
    setSort
  }
})
