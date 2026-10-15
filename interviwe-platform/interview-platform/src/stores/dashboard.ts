import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { TodoItem, DashboardStats } from '@/types'
import todosData from '@/mock/todos.json'
import recordsData from '@/mock/records.json'
import usersData from '@/mock/users.json'

function mockDelay(ms = 400) {
  return new Promise(resolve => setTimeout(resolve, ms))
}

export const useDashboardStore = defineStore('dashboard', () => {
  // ===================== State =====================
  const todos = ref<TodoItem[]>(todosData as TodoItem[])
  const loading = ref(false)
  const statsLoading = ref(false)

  // ===================== 统计数据（从 records/users Mock 计算） =====================
  const stats = computed<DashboardStats>(() => {
    const records = recordsData as any[]
    const users = usersData as any[]

    const today = new Date().toISOString().slice(0, 10)
    const completed = records.filter(r => r.status === '已完成')
    const completedToday = completed.filter(r => r.updateTime?.slice(0, 10) === today)
    const pending = records.filter(r => r.status === '待面试')
    const inProgress = records.filter(r => r.status === '进行中')

    const avgScore = completed.length
      ? Math.round(completed.reduce((s: number, r: any) => s + r.totalScore, 0) / completed.length)
      : 0

    const recommended = completed.filter(
      (r: any) => r.recommendation === '强烈推荐' || r.recommendation === '推荐'
    )
    const recommendRate = completed.length
      ? Math.round((recommended.length / completed.length) * 100)
      : 0

    // 候选人人数
    const candidateIds = new Set(records.map((r: any) => r.candidateId))
    // 活跃面试官（参与过面试的）
    const interviewerIds = new Set(records.map((r: any) => r.interviewerId))

    return {
      totalInterviews: records.length,
      completedToday: completedToday.length,
      pendingCount: pending.length,
      inProgressCount: inProgress.length,
      avgScore,
      recommendRate,
      totalCandidates: candidateIds.size,
      activeInterviewers: interviewerIds.size
    }
  })

  // ===================== 最近面试记录（按时间倒序，取前5条） =====================
  const recentRecords = computed(() => {
    const records = recordsData as any[]
    return [...records]
      .sort((a, b) => new Date(b.updateTime).getTime() - new Date(a.updateTime).getTime())
      .slice(0, 5)
  })

  // ===================== 待办事项 computed =====================
  const pendingTodos = computed(() =>
    todos.value.filter(t => t.status !== '已完成')
      .sort((a, b) => {
        const priorityOrder: Record<string, number> = { '紧急': 0, '普通': 1, '低优先级': 2 }
        return priorityOrder[a.priority] - priorityOrder[b.priority]
      })
  )

  const completedTodos = computed(() =>
    todos.value.filter(t => t.status === '已完成')
  )

  const urgentTodoCount = computed(() =>
    todos.value.filter(t => t.priority === '紧急' && t.status !== '已完成').length
  )

  // ===================== 加载（模拟接口延迟） =====================
  async function fetchStats() {
    statsLoading.value = true
    await mockDelay(600)
    statsLoading.value = false
  }

  async function fetchTodos() {
    loading.value = true
    await mockDelay(400)
    loading.value = false
  }

  // ===================== 更新待办状态 =====================
  async function updateTodoStatus(id: number, status: TodoItem['status']): Promise<boolean> {
    await mockDelay(300)
    const todo = todos.value.find(t => t.id === id)
    if (!todo) return false
    todo.status = status
    todo.updateTime = new Date().toISOString()
    return true
  }

  // ===================== 新增待办 =====================
  async function addTodo(data: Omit<TodoItem, 'id' | 'createTime' | 'updateTime'>): Promise<TodoItem> {
    loading.value = true
    await mockDelay(400)
    const now = new Date().toISOString()
    const newTodo: TodoItem = {
      ...data,
      id: Date.now(),
      createTime: now,
      updateTime: now
    }
    todos.value.unshift(newTodo)
    loading.value = false
    return newTodo
  }

  // ===================== 删除待办 =====================
  async function deleteTodo(id: number): Promise<boolean> {
    await mockDelay(300)
    const idx = todos.value.findIndex(t => t.id === id)
    if (idx === -1) return false
    todos.value.splice(idx, 1)
    return true
  }

  return {
    todos,
    loading,
    statsLoading,
    stats,
    recentRecords,
    pendingTodos,
    completedTodos,
    urgentTodoCount,
    fetchStats,
    fetchTodos,
    updateTodoStatus,
    addTodo,
    deleteTodo
  }
})
