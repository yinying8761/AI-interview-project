import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { InterviewConfig } from '@/types'
import configsData from '@/mock/configs.json'
import { generateId } from '@/utils/helpers'

const DRAFT_KEY = 'interview_config_draft'

export const useConfigStore = defineStore('config', () => {
  const configs = ref<InterviewConfig[]>(configsData as InterviewConfig[])

  // 保存草稿到本地存储
  function saveDraft(data: Partial<InterviewConfig>) {
    localStorage.setItem(DRAFT_KEY, JSON.stringify(data))
  }

  // 读取草稿
  function loadDraft(): Partial<InterviewConfig> | null {
    const raw = localStorage.getItem(DRAFT_KEY)
    if (!raw) return null
    try {
      return JSON.parse(raw)
    } catch {
      return null
    }
  }

  // 清除草稿
  function clearDraft() {
    localStorage.removeItem(DRAFT_KEY)
  }

  // 正式提交配置
  function submitConfig(data: Omit<InterviewConfig, 'id' | 'createTime' | 'status'>) {
    const config: InterviewConfig = {
      ...data,
      id: generateId(),
      status: '已提交',
      createTime: new Date().toISOString()
    }
    configs.value.unshift(config)
    clearDraft()
    return config
  }

  // 删除配置
  function deleteConfig(id: number) {
    const index = configs.value.findIndex(c => c.id === id)
    if (index !== -1) configs.value.splice(index, 1)
  }

  // 导出所有配置
  function exportConfigs() {
    return JSON.parse(JSON.stringify(configs.value))
  }

  // 导入配置
  function importConfigs(data: InterviewConfig[]) {
    data.forEach(item => {
      const exists = configs.value.find(c => c.id === item.id)
      if (!exists) {
        configs.value.push(item)
      }
    })
  }

  return {
    configs,
    saveDraft,
    loadDraft,
    clearDraft,
    submitConfig,
    deleteConfig,
    exportConfigs,
    importConfigs
  }
})
