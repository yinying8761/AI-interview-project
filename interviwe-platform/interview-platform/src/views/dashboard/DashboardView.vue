<template>
  <div class="dashboard">
    <!-- 欢迎栏 -->
    <div class="welcome-bar">
      <div class="welcome-left">
        <div class="welcome-avatar">{{ authStore.username?.charAt(0) }}</div>
        <div>
          <div class="welcome-title">早上好，{{ authStore.username }}！<span class="wave">👋</span></div>
          <div class="welcome-sub">{{ roleGreeting }} · {{ today }}</div>
        </div>
      </div>
      <div class="welcome-right">
        <el-tag v-if="dashboardStore.urgentTodoCount > 0" type="danger" size="large" effect="dark">
          <el-icon style="margin-right:4px"><Bell /></el-icon>
          {{ dashboardStore.urgentTodoCount }} 项紧急待办
        </el-tag>
        <el-tag v-else type="success" size="large" effect="light">
          <el-icon style="margin-right:4px"><CircleCheck /></el-icon>
          暂无紧急事项
        </el-tag>
      </div>
    </div>

    <!-- 统计卡片行 -->
    <div class="stat-cards" v-loading="dashboardStore.statsLoading">
      <div
        v-for="card in statCards"
        :key="card.key"
        class="stat-card"
        :style="{ background: card.bgColor }"
      >
        <div class="card-icon" :style="{ background: card.color }">
          <el-icon size="22" color="#fff">
            <component :is="card.icon" />
          </el-icon>
        </div>
        <div class="card-content">
          <div class="card-value">
            <count-up :end-val="Number(card.value)" :duration="1.5" class="count-num" />
            <span v-if="card.unit" class="card-unit">{{ card.unit }}</span>
          </div>
          <div class="card-label">{{ card.label }}</div>
        </div>
        <div v-if="card.trend !== undefined" class="card-trend" :class="card.trend >= 0 ? 'up' : 'down'">
          <el-icon><component :is="card.trend >= 0 ? 'ArrowUp' : 'ArrowDown'" /></el-icon>
          {{ Math.abs(card.trend) }}%
        </div>
      </div>
    </div>

    <!-- 主内容区：左侧最近记录 + 右侧待办 -->
    <div class="dashboard-body">
      <!-- 左：最近面试记录 -->
      <el-card class="recent-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-header-title">
              <el-icon color="#409EFF"><VideoCamera /></el-icon>
              最近面试记录
            </span>
            <el-button link type="primary" @click="$router.push('/records')">
              查看全部 <el-icon><ArrowRight /></el-icon>
            </el-button>
          </div>
        </template>

        <div class="recent-list">
          <div
            v-for="record in dashboardStore.recentRecords"
            :key="record.id"
            class="recent-item"
            @click="goToRecord(record)"
          >
            <div class="recent-avatar" :style="{ background: statusColor(record.status) }">
              {{ record.candidateName.charAt(0) }}
            </div>
            <div class="recent-info">
              <div class="recent-name">{{ record.candidateName }}</div>
              <div class="recent-desc">{{ record.interviewName }}</div>
            </div>
            <div class="recent-meta">
              <el-tag :type="statusTagType(record.status)" size="small">
                {{ record.status }}
              </el-tag>
              <div v-if="record.totalScore > 0" class="recent-score">
                {{ record.totalScore }} 分
              </div>
              <div class="recent-time">{{ formatRelativeTime(record.updateTime) }}</div>
            </div>
          </div>

          <el-empty v-if="dashboardStore.recentRecords.length === 0" description="暂无面试记录" :image-size="80" />
        </div>
      </el-card>

      <!-- 右：待办事项 -->
      <div class="todo-panel">
        <!-- 待办头部 -->
        <el-card shadow="never" class="todo-card">
          <template #header>
            <div class="card-header">
              <span class="card-header-title">
                <el-icon color="#E6A23C"><List /></el-icon>
                待办事项
                <el-badge
                  v-if="dashboardStore.pendingTodos.length > 0"
                  :value="dashboardStore.pendingTodos.length"
                  class="todo-badge"
                />
              </span>
              <el-button link type="primary" size="small" @click="showAddTodo = true">
                <el-icon><Plus /></el-icon> 新增
              </el-button>
            </div>
          </template>

          <!-- 待办列表 -->
          <div class="todo-list" v-loading="dashboardStore.loading">
            <div
              v-for="todo in dashboardStore.pendingTodos"
              :key="todo.id"
              class="todo-item"
              :class="{ 'todo-urgent': todo.priority === '紧急', 'todo-in-progress': todo.status === '进行中' }"
            >
              <div class="todo-left">
                <el-checkbox
                  :model-value="todo.status === '已完成'"
                  @change="(val: boolean) => handleTodoCheck(todo.id, val)"
                />
                <div class="todo-content">
                  <div class="todo-title">{{ todo.title }}</div>
                  <div class="todo-desc">{{ todo.description }}</div>
                  <div class="todo-footer">
                    <el-tag
                      :type="priorityTagType(todo.priority)"
                      size="small"
                      effect="plain"
                    >{{ todo.priority }}</el-tag>
                    <el-tag :type="typeTagType(todo.type)" size="small" effect="plain">
                      {{ todo.type }}
                    </el-tag>
                    <span v-if="todo.dueDate" class="todo-due" :class="{ overdue: isOverdue(todo.dueDate) }">
                      <el-icon><Clock /></el-icon>
                      {{ todo.dueDate }}
                    </span>
                  </div>
                </div>
              </div>
              <div class="todo-actions">
                <el-tooltip v-if="todo.relatedId" content="查看关联记录" placement="top">
                  <el-button link size="small" @click="$router.push(`/records/${todo.relatedId}/report`)">
                    <el-icon><Document /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="删除" placement="top">
                  <el-button link size="small" type="danger" @click="handleDeleteTodo(todo.id)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </el-tooltip>
              </div>
            </div>

            <el-empty
              v-if="dashboardStore.pendingTodos.length === 0"
              description="所有待办已完成 🎉"
              :image-size="70"
            />
          </div>

          <!-- 已完成折叠 -->
          <el-collapse v-if="dashboardStore.completedTodos.length > 0" class="completed-collapse">
            <el-collapse-item :title="`已完成 (${dashboardStore.completedTodos.length})`" name="completed">
              <div
                v-for="todo in dashboardStore.completedTodos"
                :key="todo.id"
                class="todo-item todo-done"
              >
                <el-checkbox :model-value="true" @change="() => handleTodoCheck(todo.id, false)" />
                <span class="todo-done-title">{{ todo.title }}</span>
                <el-button link size="small" type="danger" @click="handleDeleteTodo(todo.id)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </div>
    </div>

    <!-- 新增待办对话框 -->
    <el-dialog
      v-model="showAddTodo"
      title="新增待办事项"
      width="480px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form :model="newTodo" :rules="todoRules" ref="todoFormRef" label-width="90px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="newTodo.title" placeholder="请输入待办标题" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="newTodo.description"
            type="textarea"
            :rows="3"
            placeholder="请输入详细描述"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="类型" prop="type">
          <el-select v-model="newTodo.type" placeholder="选择类型" style="width:100%">
            <el-option label="面试安排" value="面试安排" />
            <el-option label="打分待办" value="打分待办" />
            <el-option label="系统通知" value="系统通知" />
            <el-option label="数据审核" value="数据审核" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-radio-group v-model="newTodo.priority">
            <el-radio value="紧急">
              <el-tag type="danger" size="small">紧急</el-tag>
            </el-radio>
            <el-radio value="普通">
              <el-tag type="warning" size="small">普通</el-tag>
            </el-radio>
            <el-radio value="低优先级">
              <el-tag type="info" size="small">低优先级</el-tag>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker
            v-model="newTodo.dueDate"
            type="date"
            placeholder="选择截止日期（可选）"
            value-format="YYYY-MM-DD"
            style="width:100%"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="showAddTodo = false">取消</el-button>
        <el-button type="primary" :loading="dashboardStore.loading" @click="submitTodo">确认新增</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, defineComponent, h } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useDashboardStore } from '@/stores/dashboard'
import { useAuthStore } from '@/stores/auth'
import type { FormInstance } from 'element-plus'

// ── 手写 CountUp 组件（不依赖外部库） ──────────────────────────────
const CountUp = defineComponent({
  props: {
    endVal: { type: Number, required: true },
    duration: { type: Number, default: 1.5 }
  },
  setup(props) {
    const displayed = ref(0)
    onMounted(() => {
      const start = performance.now()
      const tick = (now: number) => {
        const progress = Math.min((now - start) / (props.duration * 1000), 1)
        // easeOutExpo
        const ease = progress === 1 ? 1 : 1 - Math.pow(2, -10 * progress)
        displayed.value = Math.round(props.endVal * ease)
        if (progress < 1) requestAnimationFrame(tick)
      }
      requestAnimationFrame(tick)
    })
    return () => h('span', displayed.value)
  }
})

const router = useRouter()
const dashboardStore = useDashboardStore()
const authStore = useAuthStore()

// ── 欢迎语 ────────────────────────────────────────────────────────
const today = new Date().toLocaleDateString('zh-CN', {
  year: 'numeric', month: 'long', day: 'numeric', weekday: 'long'
})

const roleGreeting = computed(() => {
  const map: Record<string, string> = {
    '管理员': '您好，管理员',
    '面试官': '今日面试安排请查收',
    '候选人': '祝您面试顺利'
  }
  return map[authStore.userRole] ?? '欢迎使用面试平台'
})

// ── 统计卡片配置 ──────────────────────────────────────────────────
const statCards = computed(() => {
  const s = dashboardStore.stats
  return [
    {
      key: 'total',
      label: '面试总场次',
      value: s.totalInterviews,
      icon: 'DataAnalysis',
      color: '#409EFF',
      bgColor: 'linear-gradient(135deg, #e8f4fd 0%, #f0f7ff 100%)',
      trend: 12,
      unit: '场'
    },
    {
      key: 'pending',
      label: '待面试',
      value: s.pendingCount,
      icon: 'Calendar',
      color: '#E6A23C',
      bgColor: 'linear-gradient(135deg, #fdf6ec 0%, #fffbf0 100%)',
      trend: undefined,
      unit: '场'
    },
    {
      key: 'inProgress',
      label: '进行中',
      value: s.inProgressCount,
      icon: 'Loading',
      color: '#67C23A',
      bgColor: 'linear-gradient(135deg, #f0f9eb 0%, #f6ffed 100%)',
      trend: undefined,
      unit: '场'
    },
    {
      key: 'avgScore',
      label: '平均得分',
      value: s.avgScore,
      icon: 'TrophyBase',
      color: '#F56C6C',
      bgColor: 'linear-gradient(135deg, #fef0f0 0%, #fff5f5 100%)',
      trend: 5,
      unit: '分'
    },
    {
      key: 'recommend',
      label: '推荐通过率',
      value: s.recommendRate,
      icon: 'Star',
      color: '#9c27b0',
      bgColor: 'linear-gradient(135deg, #f3e5f5 0%, #f9f0fb 100%)',
      trend: -3,
      unit: '%'
    },
    {
      key: 'candidates',
      label: '参与候选人',
      value: s.totalCandidates,
      icon: 'User',
      color: '#00bcd4',
      bgColor: 'linear-gradient(135deg, #e0f7fa 0%, #f0fbfd 100%)',
      trend: undefined,
      unit: '人'
    },
    {
      key: 'interviewers',
      label: '活跃面试官',
      value: s.activeInterviewers,
      icon: 'UserFilled',
      color: '#ff5722',
      bgColor: 'linear-gradient(135deg, #fbe9e7 0%, #fff3f0 100%)',
      trend: undefined,
      unit: '人'
    },
    {
      key: 'today',
      label: '今日完成',
      value: s.completedToday,
      icon: 'CircleCheck',
      color: '#3f51b5',
      bgColor: 'linear-gradient(135deg, #e8eaf6 0%, #f0f1fc 100%)',
      trend: undefined,
      unit: '场'
    }
  ]
})

// ── 最近记录辅助 ──────────────────────────────────────────────────
function statusColor(status: string) {
  const map: Record<string, string> = {
    '已完成': '#67C23A', '进行中': '#409EFF', '待面试': '#E6A23C', '已取消': '#909399'
  }
  return map[status] ?? '#409EFF'
}

function statusTagType(status: string) {
  const map: Record<string, string> = {
    '已完成': 'success', '进行中': 'primary', '待面试': 'warning', '已取消': 'info'
  }
  return map[status] ?? 'info'
}

function formatRelativeTime(iso: string) {
  const diff = Date.now() - new Date(iso).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins}分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days}天前`
  return new Date(iso).toLocaleDateString('zh-CN')
}

function goToRecord(record: any) {
  if (record.status === '已完成') {
    router.push(`/records/${record.id}/report`)
  } else {
    router.push('/records')
  }
}

// ── 待办辅助 ──────────────────────────────────────────────────────
function priorityTagType(priority: string) {
  const map: Record<string, string> = { '紧急': 'danger', '普通': 'warning', '低优先级': 'info' }
  return map[priority] ?? 'info'
}

function typeTagType(type: string) {
  const map: Record<string, string> = {
    '面试安排': 'primary', '打分待办': 'success', '系统通知': 'warning', '数据审核': 'info'
  }
  return map[type] ?? 'info'
}

function isOverdue(dueDate: string) {
  return new Date(dueDate) < new Date(new Date().toDateString())
}

async function handleTodoCheck(id: number, checked: boolean) {
  const status = checked ? '已完成' : '待处理'
  const ok = await dashboardStore.updateTodoStatus(id, status)
  if (ok) {
    ElMessage.success(checked ? '已标记为完成' : '已重置为待处理')
  }
}

async function handleDeleteTodo(id: number) {
  await ElMessageBox.confirm('确认删除该待办项？', '删除', { type: 'warning' })
  const ok = await dashboardStore.deleteTodo(id)
  if (ok) ElMessage.success('已删除')
}

// ── 新增待办 ─────────────────────────────────────────────────────
const showAddTodo = ref(false)
const todoFormRef = ref<FormInstance>()
const newTodo = ref({
  title: '',
  description: '',
  type: '面试安排' as '面试安排' | '打分待办' | '系统通知' | '数据审核',
  priority: '普通' as '紧急' | '普通' | '低优先级',
  status: '待处理' as const,
  dueDate: ''
})

const todoRules = {
  title: [{ required: true, message: '请输入待办标题', trigger: 'blur' },
          { min: 2, max: 50, message: '长度 2~50 个字符', trigger: 'blur' }],
  description: [{ required: true, message: '请输入描述', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }]
}

async function submitTodo() {
  await todoFormRef.value?.validate()
  await dashboardStore.addTodo({
    ...newTodo.value,
    dueDate: newTodo.value.dueDate || undefined
  })
  ElMessage.success('待办已新增')
  showAddTodo.value = false
  // 重置表单
  newTodo.value = {
    title: '', description: '', type: '面试安排',
    priority: '普通', status: '待处理', dueDate: ''
  }
}

// ── 初始化 ────────────────────────────────────────────────────────
onMounted(() => {
  dashboardStore.fetchStats()
  dashboardStore.fetchTodos()
})
</script>

<style lang="scss" scoped>
.dashboard {
  padding: 24px;
  min-height: 100%;

  // ── 欢迎栏 ──────────────────────────────────────────────
  .welcome-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: linear-gradient(135deg, #1a2038 0%, #283593 100%);
    border-radius: 12px;
    padding: 20px 28px;
    margin-bottom: 24px;
    color: #fff;

    .welcome-left {
      display: flex;
      align-items: center;
      gap: 16px;

      .welcome-avatar {
        width: 52px;
        height: 52px;
        border-radius: 50%;
        background: rgba(255,255,255,0.2);
        display: flex;
        align-items: center;
        justify-content: center;
        font-size: 22px;
        font-weight: 700;
        flex-shrink: 0;
        border: 2px solid rgba(255,255,255,0.3);
      }

      .welcome-title {
        font-size: 20px;
        font-weight: 700;
        .wave { font-style: normal; }
      }
      .welcome-sub {
        font-size: 13px;
        color: rgba(255,255,255,0.7);
        margin-top: 4px;
      }
    }
  }

  // ── 统计卡片行 ───────────────────────────────────────────
  .stat-cards {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;
    margin-bottom: 24px;

    @media (max-width: 1400px) { grid-template-columns: repeat(4, 1fr); }
    @media (max-width: 1100px) { grid-template-columns: repeat(2, 1fr); }

    .stat-card {
      border-radius: 12px;
      padding: 18px 20px;
      display: flex;
      align-items: center;
      gap: 16px;
      position: relative;
      overflow: hidden;
      border: 1px solid rgba(0,0,0,0.04);
      transition: transform 0.2s, box-shadow 0.2s;
      cursor: default;

      &:hover {
        transform: translateY(-2px);
        box-shadow: 0 6px 20px rgba(0,0,0,0.1);
      }

      .card-icon {
        width: 48px;
        height: 48px;
        border-radius: 10px;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        box-shadow: 0 4px 12px rgba(0,0,0,0.15);
      }

      .card-content {
        flex: 1;
        min-width: 0;

        .card-value {
          display: flex;
          align-items: baseline;
          gap: 2px;

          .count-num {
            font-size: 28px;
            font-weight: 700;
            color: #1a2038;
            line-height: 1;
          }
          .card-unit {
            font-size: 14px;
            color: #606266;
            margin-left: 2px;
          }
        }

        .card-label {
          font-size: 13px;
          color: #909399;
          margin-top: 4px;
        }
      }

      .card-trend {
        position: absolute;
        top: 10px;
        right: 12px;
        font-size: 12px;
        display: flex;
        align-items: center;
        gap: 2px;
        padding: 2px 6px;
        border-radius: 20px;

        &.up {
          color: #67C23A;
          background: rgba(103,194,58,0.12);
        }
        &.down {
          color: #F56C6C;
          background: rgba(245,108,108,0.12);
        }
      }
    }
  }

  // ── 主内容区 ─────────────────────────────────────────────
  .dashboard-body {
    display: grid;
    grid-template-columns: 1fr 400px;
    gap: 20px;
    align-items: start;

    @media (max-width: 1100px) {
      grid-template-columns: 1fr;
    }
  }

  // ── 卡片通用头部 ─────────────────────────────────────────
  .card-header {
    display: flex;
    align-items: center;
    justify-content: space-between;

    .card-header-title {
      display: flex;
      align-items: center;
      gap: 6px;
      font-weight: 600;
      font-size: 15px;
      color: #303133;
    }

    .todo-badge {
      margin-left: 4px;
    }
  }

  // ── 最近面试记录 ─────────────────────────────────────────
  .recent-card {
    :deep(.el-card__body) { padding: 0; }
  }

  .recent-list {
    padding: 4px 0;
  }

  .recent-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 14px 20px;
    cursor: pointer;
    transition: background 0.15s;
    border-bottom: 1px solid #f5f5f5;

    &:last-child { border-bottom: none; }
    &:hover { background: #f9fafc; }

    .recent-avatar {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 16px;
      font-weight: 700;
      color: #fff;
      flex-shrink: 0;
    }

    .recent-info {
      flex: 1;
      min-width: 0;

      .recent-name {
        font-size: 14px;
        font-weight: 600;
        color: #303133;
      }
      .recent-desc {
        font-size: 12px;
        color: #909399;
        margin-top: 2px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }

    .recent-meta {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      gap: 4px;
      flex-shrink: 0;

      .recent-score {
        font-size: 13px;
        font-weight: 700;
        color: #409EFF;
      }
      .recent-time {
        font-size: 11px;
        color: #c0c4cc;
      }
    }
  }

  // ── 待办区块 ──────────────────────────────────────────────
  .todo-panel {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .todo-card {
    :deep(.el-card__body) { padding: 0; }
  }

  .todo-list {
    padding: 4px 0;
    max-height: 420px;
    overflow-y: auto;
  }

  .todo-item {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 12px 16px;
    border-bottom: 1px solid #f5f5f5;
    transition: background 0.15s;

    &:last-child { border-bottom: none; }
    &:hover { background: #f9fafc; }

    &.todo-urgent {
      border-left: 3px solid #F56C6C;
      background: rgba(245,108,108,0.03);
    }
    &.todo-in-progress {
      border-left: 3px solid #409EFF;
      background: rgba(64,158,255,0.03);
    }

    .todo-left {
      display: flex;
      align-items: flex-start;
      gap: 10px;
      flex: 1;
      min-width: 0;
    }

    .todo-content {
      flex: 1;
      min-width: 0;

      .todo-title {
        font-size: 13px;
        font-weight: 600;
        color: #303133;
        line-height: 1.4;
      }
      .todo-desc {
        font-size: 12px;
        color: #909399;
        margin-top: 3px;
        line-height: 1.4;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }
      .todo-footer {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 4px;
        margin-top: 6px;

        .todo-due {
          font-size: 11px;
          color: #909399;
          display: flex;
          align-items: center;
          gap: 2px;
          &.overdue { color: #F56C6C; font-weight: 600; }
        }
      }
    }

    .todo-actions {
      display: flex;
      gap: 4px;
      flex-shrink: 0;
    }

    &.todo-done {
      display: flex;
      align-items: center;
      gap: 10px;
      opacity: 0.5;

      .todo-done-title {
        flex: 1;
        font-size: 13px;
        color: #909399;
        text-decoration: line-through;
      }
    }
  }

  .completed-collapse {
    padding: 0 12px 8px;
    :deep(.el-collapse-item__header) {
      font-size: 13px;
      color: #909399;
      border-top: 1px dashed #ebeef5;
    }
    :deep(.el-collapse-item__content) { padding-bottom: 0; }
    :deep(.el-collapse) { border: none; }
  }
}
</style>
