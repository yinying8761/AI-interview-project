// ==================== 表单相关类型 ====================
export interface FormField {
  key: string
  type: 'input' | 'select' | 'number' | 'switch' | 'datetime' | 'textarea'
  label: string
  value: any
  rules?: Array<{ required?: boolean; message: string; trigger: string; validator?: any }>
  props?: Record<string, any>
  visible?: boolean
  options?: Array<{ label: string; value: any }>
  step?: number
}

export interface LinkageRule {
  trigger: string
  condition: (value: any) => boolean
  action: 'show' | 'hide'
  target: string
  rules?: any[]
}

// ==================== 表格相关类型 ====================
export interface TableColumn {
  prop: string
  label: string
  width?: string | number
  minWidth?: string | number
  sortable?: boolean
  render?: 'ellipsis' | 'tags' | 'stars' | 'progress' | 'status' | 'actions' | 'index' | 'datetime' | 'relativeTime'
  renderOptions?: any
  fixed?: 'left' | 'right'
  showOverflowTooltip?: boolean
}

export interface FilterOption {
  key: string
  label: string
  options: Array<{ label: string; value: any }>
  multiple?: boolean
}

export interface Pagination {
  currentPage: number
  pageSize: number
  total: number
  pageSizes: number[]
}

// ==================== 题库相关类型 ====================
export interface Question {
  id: number
  content: string
  type: '单选' | '多选' | '判断' | '编程'
  difficulty: 1 | 2 | 3 | 4 | 5
  direction: '前端' | '后端' | '算法' | '大数据'
  usageCount: number
  passRate: number
  status: '启用' | '停用' | '审核中'
  createTime: string
}

export interface QuestionFilter {
  keyword?: string
  type?: string[]
  difficulty?: number[]
  direction?: string[]
  status?: string[]
}

// ==================== 用户相关类型 ====================
export interface User {
  id: number
  username: string
  role: '管理员' | '面试官' | '候选人'
  email: string
  createdAt: string
  lastLogin: string
  status: '正常' | '冻结' | '已注销'
}

export interface UserFilter {
  keyword?: string
  role?: string[]
  status?: string[]
}

// ==================== 面试配置类型 ====================
export interface InterviewConfig {
  id: number
  interviewName: string
  interviewType: '技术面' | 'HR面' | '综合面'
  techDirection?: '前端' | '后端' | '算法' | '大数据'
  duration: number
  needBreak: boolean
  interviewers: string[]
  interviewTime: string
  remark?: string
  status: '草稿' | '已提交'
  createTime: string
}

// ==================== 通用响应类型 ====================
export interface PageResult<T> {
  list: T[]
  total: number
  currentPage: number
  pageSize: number
}

// ==================== 认证相关类型 ====================
export interface RegisterForm {
  username: string
  email: string
  password: string
  confirmPassword: string
  role: '管理员' | '面试官' | '候选人'
  phone?: string
}

export interface LoginForm {
  email: string
  password: string
  role: '管理员' | '面试官' | '候选人'
  remember: boolean
}

export interface AuthUser {
  id: number
  username: string
  email: string
  role: '管理员' | '面试官' | '候选人'
  avatar?: string
  token: string
}

// ==================== 面试记录相关类型 ====================
export interface ScoreItem {
  dimension: string   // 评分维度，如"技术能力"
  score: number       // 0-10
  comment: string     // 备注
}

export interface InterviewRecord {
  id: number
  configId: number           // 关联的面试配置 id
  interviewName: string      // 冗余方便展示
  candidateId: number        // 候选人 id
  candidateName: string
  interviewerId: number      // 面试官 id
  interviewerName: string
  interviewTime: string      // 实际面试时间
  duration: number           // 实际时长（分钟）
  status: '待面试' | '进行中' | '已完成' | '已取消'
  scores: ScoreItem[]        // 各维度评分
  totalScore: number         // 综合分（0-100）
  overallComment: string     // 综合评语
  recommendation: '强烈推荐' | '推荐' | '待定' | '不推荐'
  createTime: string
  updateTime: string
}

export interface RecordFilter {
  keyword?: string
  status?: string[]
  recommendation?: string[]
  interviewerName?: string
  candidateName?: string
  dateRange?: [string, string]
}

// ==================== 仪表盘相关类型 ====================
export interface StatCard {
  key: string
  label: string
  value: number | string
  icon: string
  color: string        // 主色（用于图标背景/进度条）
  bgColor: string      // 卡片背景渐变色
  trend?: number       // 环比变化（正数上升，负数下降），可选
  unit?: string        // 单位，如 '%'、'人'
}

export interface TodoItem {
  id: number
  title: string
  description: string
  priority: '紧急' | '普通' | '低优先级'
  type: '面试安排' | '打分待办' | '系统通知' | '数据审核'
  status: '待处理' | '进行中' | '已完成'
  relatedId?: number     // 关联的面试记录 id
  dueDate?: string       // 截止日期
  createTime: string
  updateTime: string
}

export interface DashboardStats {
  totalInterviews: number
  completedToday: number
  pendingCount: number
  inProgressCount: number
  avgScore: number
  recommendRate: number
  totalCandidates: number
  activeInterviewers: number
}
