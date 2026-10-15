// 工具函数

/**
 * 格式化日期时间
 */
export function formatDateTime(dateStr: string, format = 'YYYY-MM-DD HH:mm'): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  if (isNaN(date.getTime())) return '-'

  const pad = (n: number) => String(n).padStart(2, '0')
  const year = date.getFullYear()
  const month = pad(date.getMonth() + 1)
  const day = pad(date.getDate())
  const hours = pad(date.getHours())
  const minutes = pad(date.getMinutes())
  const seconds = pad(date.getSeconds())

  return format
    .replace('YYYY', String(year))
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

/**
 * 相对时间 (x分钟前/x小时前/x天前)
 */
export function relativeTime(dateStr: string): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  if (isNaN(date.getTime())) return '-'

  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (seconds < 60) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 30) return `${days}天前`
  return formatDateTime(dateStr, 'YYYY-MM-DD')
}

/**
 * 深拷贝
 */
export function deepClone<T>(obj: T): T {
  return JSON.parse(JSON.stringify(obj))
}

/**
 * 导出JSON文件
 */
export function exportJson(data: any, filename: string) {
  const json = JSON.stringify(data, null, 2)
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

/**
 * 读取JSON文件
 */
export function readJsonFile(file: File): Promise<any> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      try {
        const data = JSON.parse(e.target?.result as string)
        resolve(data)
      } catch {
        reject(new Error('文件格式错误，请上传有效的JSON文件'))
      }
    }
    reader.onerror = () => reject(new Error('文件读取失败'))
    reader.readAsText(file)
  })
}

/**
 * 生成唯一ID
 */
export function generateId(): number {
  return Date.now() + Math.floor(Math.random() * 1000)
}

/**
 * 防抖
 */
export function debounce<T extends (...args: any[]) => any>(fn: T, delay = 300) {
  let timer: ReturnType<typeof setTimeout>
  return function (this: any, ...args: Parameters<T>) {
    clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), delay)
  }
}

/**
 * 状态颜色映射
 */
export const statusColorMap: Record<string, string> = {
  '启用': 'success',
  '停用': 'danger',
  '审核中': 'warning',
  '正常': 'success',
  '冻结': 'danger',
  '已注销': 'info',
  '草稿': 'info',
  '已提交': 'success'
}

/**
 * 题型颜色映射
 */
export const typeColorMap: Record<string, string> = {
  '单选': 'primary',
  '多选': 'success',
  '判断': 'warning',
  '编程': 'danger'
}

/**
 * 方向颜色映射
 */
export const directionColorMap: Record<string, string> = {
  '前端': 'primary',
  '后端': 'success',
  '算法': 'warning',
  '大数据': 'danger'
}

/**
 * 角色颜色映射
 */
export const roleColorMap: Record<string, string> = {
  '管理员': 'danger',
  '面试官': 'primary',
  '候选人': 'success'
}
