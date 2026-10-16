<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>候选人简历</h2>
        <p>查看候选人发送过来的简历解析结果,辅助面试评审。</p>
      </div>
    </div>

    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" style="width: 100%" empty-text="暂无收到的简历">
        <el-table-column prop="candidateName" label="候选人" min-width="120" />
        <el-table-column prop="fileName" label="简历文件" min-width="220" show-overflow-tooltip />
        <el-table-column prop="message" label="留言" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'READ' ? 'success' : 'warning'">
              {{ row.status === 'READ' ? '已读' : '待查看' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发送时间" min-width="170">
          <template #default="{ row }">{{ row.sentAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailVisible" title="简历详情" width="760px" top="6vh">
      <div v-if="detail">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="候选人">{{ detail.candidateName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ detail.resume?.parseResult?.name || '-' }}</el-descriptions-item>
          <el-descriptions-item label="电话">{{ detail.resume?.parseResult?.phone || '-' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ detail.resume?.parseResult?.email || '-' }}</el-descriptions-item>
          <el-descriptions-item label="文件">{{ detail.fileName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="留言">{{ detail.message || '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-actions">
          <el-button v-if="detail.resume?.fileUrl" type="primary" @click="openFile(detail.resume.fileUrl)">预览原件</el-button>
        </div>

        <section class="detail-section">
          <h4>技能标签</h4>
          <div v-if="detail.resume?.parseResult?.skills?.length" class="tag-wrap">
            <el-tag v-for="s in detail.resume.parseResult.skills" :key="s" class="tag-item">{{ s }}</el-tag>
          </div>
          <el-empty v-else :image-size="40" description="未识别到技能" />
        </section>

        <section class="detail-section">
          <h4>教育经历</h4>
          <div v-if="detail.resume?.parseResult?.educationList?.length" class="item-list">
            <div v-for="(e, i) in detail.resume.parseResult.educationList" :key="i" class="item-row">
              <span class="item-index">{{ i + 1 }}</span><span>{{ e }}</span>
            </div>
          </div>
          <el-empty v-else :image-size="40" description="未识别到教育经历" />
        </section>

        <section class="detail-section">
          <h4>项目经历</h4>
          <div v-if="detail.resume?.parseResult?.projects?.length" class="item-list">
            <div v-for="(p, i) in detail.resume.parseResult.projects" :key="i" class="item-row">
              <span class="item-index">{{ i + 1 }}</span><span>{{ p }}</span>
            </div>
          </div>
          <el-empty v-else :image-size="40" description="未识别到项目经历" />
        </section>

        <section class="detail-section">
          <h4>关键词</h4>
          <div v-if="detail.resume?.parseResult?.keywords?.length" class="tag-wrap">
            <el-tag v-for="k in detail.resume.parseResult.keywords" :key="k" class="tag-item" type="warning" effect="plain">{{ k }}</el-tag>
          </div>
          <el-empty v-else :image-size="40" description="未识别到关键词" />
        </section>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { listReceivedResumes, getReceivedResumeDetail } from '../api/resume'

const loading = ref(false)
const list = ref([])
const detailVisible = ref(false)
const detail = ref(null)

const load = async () => {
  loading.value = true
  try {
    const res = await listReceivedResumes()
    list.value = res.data || []
  } catch (e) { /* 请求错误由拦截器提示 */ }
  loading.value = false
}

const openDetail = async (row) => {
  try {
    const res = await getReceivedResumeDetail(row.deliveryId)
    detail.value = res.data
    detailVisible.value = true
    await load()
  } catch (e) { /* 请求错误由拦截器提示 */ }
}

const openFile = (url) => window.open(url, '_blank')

onMounted(load)
</script>

<style scoped>
.page-container { padding: 24px; }
.page-header { margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 20px; font-weight: 600; }
.page-header p { margin: 4px 0 0; font-size: 13px; color: #909399; }
.detail-actions { margin: 12px 0; }
.detail-section { margin-top: 16px; }
.detail-section h4 { margin: 0 0 8px; font-size: 14px; font-weight: 600; }
.tag-wrap { display: flex; flex-wrap: wrap; }
.tag-item { margin: 0 6px 6px 0; }
.item-list { display: flex; flex-direction: column; gap: 8px; }
.item-row { display: flex; align-items: flex-start; gap: 10px; }
.item-index { flex: 0 0 auto; width: 22px; height: 22px; line-height: 22px; text-align: center; border-radius: 50%; background: #ecf5ff; color: #409eff; font-size: 12px; font-weight: 600; margin-top: 1px; }
</style>
