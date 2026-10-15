import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { User, UserFilter, Pagination } from '@/types'
import usersData from '@/mock/users.json'

export const useUserStore = defineStore('user', () => {
  const allUsers = ref<User[]>(usersData as User[])
  const filter = ref<UserFilter>({})
  const sortField = ref<string>('')
  const sortOrder = ref<'ascending' | 'descending' | null>(null)
  const pagination = ref<Pagination>({
    currentPage: 1,
    pageSize: 10,
    total: 0,
    pageSizes: [10, 20, 50, 100]
  })

  // 筛选后的数据
  const filteredUsers = computed(() => {
    let list = [...allUsers.value]

    if (filter.value.keyword) {
      const kw = filter.value.keyword.toLowerCase()
      list = list.filter(u =>
        u.username.toLowerCase().includes(kw) ||
        u.email.toLowerCase().includes(kw)
      )
    }
    if (filter.value.role && filter.value.role.length > 0) {
      list = list.filter(u => filter.value.role!.includes(u.role))
    }
    if (filter.value.status && filter.value.status.length > 0) {
      list = list.filter(u => filter.value.status!.includes(u.status))
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
  const pagedUsers = computed(() => {
    const total = filteredUsers.value.length
    pagination.value.total = total
    const start = (pagination.value.currentPage - 1) * pagination.value.pageSize
    const end = start + pagination.value.pageSize
    return filteredUsers.value.slice(start, end)
  })

  // 切换用户状态
  function toggleStatus(id: number) {
    const user = allUsers.value.find(u => u.id === id)
    if (user) {
      user.status = user.status === '正常' ? '冻结' : '正常'
    }
  }

  // 设置筛选条件
  function setFilter(f: UserFilter) {
    filter.value = { ...f }
    pagination.value.currentPage = 1
  }

  // 设置排序
  function setSort(field: string, order: 'ascending' | 'descending' | null) {
    sortField.value = field
    sortOrder.value = order
  }

  return {
    allUsers,
    filter,
    pagination,
    filteredUsers,
    pagedUsers,
    toggleStatus,
    setFilter,
    setSort
  }
})
