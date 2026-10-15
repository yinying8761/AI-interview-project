<template>
  <div class="page-container">
    <div class="page-header">
      <h2>用户管理</h2>
    </div>

    <!-- 搜索筛选 -->
    <el-card class="filter-card">
      <div class="filter-row">
        <el-input
          v-model="filterForm.keyword"
          placeholder="搜索用户名或邮箱..."
          clearable
          style="width: 280px"
          @input="handleFilterChange"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>

        <el-select
          v-model="filterForm.role"
          placeholder="角色"
          multiple
          collapse-tags
          clearable
          style="width: 200px"
          @change="handleFilterChange"
        >
          <el-option v-for="r in roleOpts" :key="r" :label="r" :value="r" />
        </el-select>

        <el-select
          v-model="filterForm.status"
          placeholder="状态"
          multiple
          collapse-tags
          clearable
          style="width: 200px"
          @change="handleFilterChange"
        >
          <el-option v-for="s in statusOpts" :key="s" :label="s" :value="s" />
        </el-select>

        <el-button @click="resetFilter">重置</el-button>
      </div>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <el-table
        :data="userStore.pagedUsers"
        border
        stripe
        @sort-change="handleSortChange"
        style="width: 100%"
      >
        <el-table-column prop="id" label="用户ID" width="80" align="center" />

        <el-table-column prop="username" label="用户名" width="120" sortable="custom">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="28" :style="{ background: avatarColor(row.role) }">
                {{ row.username.charAt(0) }}
              </el-avatar>
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="role" label="角色" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="roleColorMap[row.role]" size="small">{{ row.role }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip />

        <el-table-column prop="createdAt" label="注册时间" width="160" sortable="custom">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>

        <el-table-column prop="lastLogin" label="最后登录" width="130">
          <template #default="{ row }">
            <el-tooltip :content="formatDate(row.lastLogin)" placement="top">
              <span>{{ getRelativeTime(row.lastLogin) }}</span>
            </el-tooltip>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusColorMap[row.status]" size="small" effect="plain">{{ row.status }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== '已注销'"
              size="small"
              :type="row.status === '正常' ? 'warning' : 'success'"
              text
              @click="toggleStatus(row)"
            >
              {{ row.status === '正常' ? '冻结' : '解冻' }}
            </el-button>
            <el-tag v-else size="small" type="info">已注销</el-tag>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="userStore.pagination.currentPage"
          v-model:page-size="userStore.pagination.pageSize"
          :page-sizes="userStore.pagination.pageSizes"
          :total="userStore.pagination.total"
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
import { reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { roleColorMap, statusColorMap, formatDateTime, relativeTime } from '@/utils/helpers'
import type { User } from '@/types'

const userStore = useUserStore()

const filterForm = reactive({
  keyword: '',
  role: [] as string[],
  status: [] as string[]
})

const roleOpts = ['管理员', '面试官', '候选人']
const statusOpts = ['正常', '冻结', '已注销']

function handleFilterChange() {
  userStore.setFilter({
    keyword: filterForm.keyword || undefined,
    role: filterForm.role.length > 0 ? filterForm.role : undefined,
    status: filterForm.status.length > 0 ? filterForm.status : undefined
  })
}

function resetFilter() {
  filterForm.keyword = ''
  filterForm.role = []
  filterForm.status = []
  userStore.setFilter({})
}

function handleSortChange({ prop, order }: { prop: string; order: any }) {
  userStore.setSort(prop, order)
}

function onPageChange(page: number) {
  userStore.pagination.currentPage = page
}

function onPageSizeChange(size: number) {
  userStore.pagination.pageSize = size
  userStore.pagination.currentPage = 1
}

function toggleStatus(user: User) {
  const action = user.status === '正常' ? '冻结' : '解冻'
  ElMessageBox.confirm(`确认${action}用户「${user.username}」？`, '操作确认', { type: 'warning' })
    .then(() => {
      userStore.toggleStatus(user.id)
      ElMessage.success(`已${action}用户「${user.username}」`)
    })
    .catch(() => {})
}

function formatDate(str: string) {
  return formatDateTime(str)
}

function getRelativeTime(str: string) {
  return relativeTime(str)
}

const roleAvatarColorMap: Record<string, string> = {
  '管理员': '#F56C6C',
  '面试官': '#409EFF',
  '候选人': '#67C23A'
}

function avatarColor(role: string): string {
  return roleAvatarColorMap[role] || '#909399'
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

.table-card {
  :deep(.el-card__body) { padding: 0; }
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: 14px 16px;
  border-top: 1px solid #EBEEF5;
}
</style>
