<template>
  <div class="resume-page">
    <!-- 顶部标题 + 主操作 -->
    <div class="page-header">
      <div>
        <h2 class="page-title">我的简历</h2>
        <p class="page-subtitle">上传一份简历，AI 自动解析出技能、教育、项目与关键词，用于模拟面试。</p>
      </div>
      <div class="header-actions">
        <el-tag v-if="resume" type="success" effect="light">已解析</el-tag>
        <el-button type="primary" :icon="UploadFilled" :loading="uploading" @click="reUpload">
          {{ resume ? '更换简历' : '上传简历' }}
        </el-button>
      </div>
    </div>

    <!-- 隐藏文件选择，供“上传/更换”共用 -->
    <input ref="fileInputRef" type="file" accept=".pdf,.doc,.docx" style="display:none" @change="onChooseFile" />

    <!-- 解析中：固定视口居中遮罩（长页面也始终可见） -->
    <div v-if="uploading" class="parse-mask">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>正在上传并解析，请稍候…</span>
    </div>

    <!-- 空态：先引导上传 -->
    <el-card v-if="!resume && !uploading" class="panel-card empty-card" shadow="never">
      <el-empty description="还没有简历，上传一份开始智能解析">
        <el-button type="primary" :icon="UploadFilled" :loading="uploading" @click="reUpload">上传简历</el-button>
      </el-empty>
    </el-card>

    <el-skeleton v-else-if="!resume" animated :rows="6" style="padding: 12px" />

    <template v-else>
      <el-row :gutter="16">
        <!-- 左列：当前文件 + 基本信息 -->
        <el-col :xs="24" :md="8">
          <el-card class="panel-card" shadow="never">
            <template #header>
              <div class="card-header"><el-icon><Document /></el-icon><span>当前文件</span></div>
            </template>
            <div class="file-meta">
              <el-icon class="file-icon"><Document /></el-icon>
              <div class="file-info">
                <div class="file-name">{{ resume.fileName || '未命名简历' }}</div>
                <div class="file-meta-text">{{ fileTypeText }} · 已解析</div>
                <div class="file-actions">
                  <el-button v-if="resume.fileUrl" link type="primary" @click="openCurrent">预览</el-button>
                  <el-button link type="primary" @click="reUpload">更换</el-button>
                </div>
              </div>
            </div>
          </el-card>

          <el-card class="panel-card" shadow="never">
            <template #header>
              <div class="card-header"><el-icon><User /></el-icon><span>基本信息</span></div>
            </template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="姓名">{{ resume.parseResult?.name || '-' }}</el-descriptions-item>
              <el-descriptions-item label="电话">{{ resume.parseResult?.phone || '-' }}</el-descriptions-item>
              <el-descriptions-item label="邮箱">{{ resume.parseResult?.email || '-' }}</el-descriptions-item>
              <el-descriptions-item label="当前版本">V{{ resume.version }}</el-descriptions-item>
              <el-descriptions-item label="解析状态"><el-tag size="small" type="success">完成</el-tag></el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>

        <!-- 右列：解析内容（视觉主体） -->
        <el-col :xs="24" :md="16">
          <el-card class="panel-card" shadow="never">
            <template #header><div class="card-header"><el-icon><DataAnalysis /></el-icon><span>解析概览</span></div></template>
            <div class="stat-row">
              <div class="stat-item"><div class="stat-num">{{ stats.skills }}</div><div class="stat-label">技能</div></div>
              <div class="stat-item"><div class="stat-num">{{ stats.education }}</div><div class="stat-label">教育</div></div>
              <div class="stat-item"><div class="stat-num">{{ stats.projects }}</div><div class="stat-label">项目</div></div>
              <div class="stat-item"><div class="stat-num">{{ stats.keywords }}</div><div class="stat-label">关键词</div></div>
            </div>
          </el-card>

          <el-card class="panel-card" shadow="never">
            <template #header>
              <div class="card-header"><el-icon><Collection /></el-icon><span>技能标签</span><el-tag size="small" effect="plain">{{ stats.skills }}</el-tag></div>
            </template>
            <div v-if="resume?.parseResult?.skills?.length" class="tag-wrap">
              <el-tag v-for="s in resume.parseResult.skills" :key="s" class="tag-item">{{ s }}</el-tag>
            </div>
            <el-empty v-else :image-size="50" description="未识别到技能" />
          </el-card>

          <el-card class="panel-card" shadow="never">
            <template #header>
              <div class="card-header"><el-icon><School /></el-icon><span>教育经历</span><el-tag size="small" effect="plain" type="info">{{ stats.education }}</el-tag></div>
            </template>
            <div v-if="resume?.parseResult?.educationList?.length" class="item-list">
              <div v-for="(e, i) in resume.parseResult.educationList" :key="i" class="item-row">
                <span class="item-index">{{ i + 1 }}</span><span class="item-text">{{ e }}</span>
              </div>
            </div>
            <el-empty v-else :image-size="50" description="未识别到教育经历" />
          </el-card>

          <el-card class="panel-card" shadow="never">
            <template #header>
              <div class="card-header"><el-icon><Tickets /></el-icon><span>项目经历</span><el-tag size="small" effect="plain" type="info">{{ stats.projects }}</el-tag></div>
            </template>
            <div v-if="resume?.parseResult?.projects?.length" class="item-list">
              <div v-for="(p, i) in resume.parseResult.projects" :key="i" class="item-row">
                <span class="item-index">{{ i + 1 }}</span><span class="item-text">{{ p }}</span>
              </div>
            </div>
            <el-empty v-else :image-size="50" description="未识别到项目经历" />
          </el-card>

          <el-card class="panel-card" shadow="never">
            <template #header>
              <div class="card-header"><el-icon><Star /></el-icon><span>关键词</span><el-tag size="small" effect="plain" type="warning">{{ stats.keywords }}</el-tag></div>
            </template>
            <div v-if="resume?.parseResult?.keywords?.length" class="tag-wrap">
              <el-tag v-for="k in resume.parseResult.keywords" :key="k" class="tag-item" type="warning" effect="plain">{{ k }}</el-tag>
            </div>
            <el-empty v-else :image-size="50" description="未识别到关键词" />
          </el-card>
        </el-col>
      </el-row>

      <!-- 版本历史（整行，默认折叠） -->
      <el-card class="panel-card version-card" shadow="never">
        <template #header>
          <div class="card-header version-header" @click="showVersions = !showVersions">
            <el-icon><Clock /></el-icon><span>版本历史</span>
            <el-tag size="small" effect="plain" type="info">{{ versions.length }}</el-tag>
            <el-icon class="version-arrow"><CaretRight v-if="!showVersions" /><CaretBottom v-else /></el-icon>
          </div>
        </template>
        <div v-show="showVersions" class="version-body">
          <el-table :data="versions" style="width: 100%">
            <el-table-column prop="version" label="版本" width="90" />
            <el-table-column prop="fileName" label="文件名" min-width="220" show-overflow-tooltip />
            <el-table-column prop="fileType" label="类型" width="100" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'PARSED' ? 'success' : 'info'">{{ row.status === 'PARSED' ? '已解析' : row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" min-width="170"><template #default="{ row }">{{ row.createdAt || '-' }}</template></el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }"><el-button link type="primary" @click="openPreview(row)">预览</el-button></template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import {
  UploadFilled, Document, User, Collection, School, Tickets, Star, Clock, DataAnalysis, Loading, CaretRight, CaretBottom
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import {
  uploadResume, getCurrentResume, listResumeVersions, getResumeFileUrl
} from '../api/resume'

const uploading = ref(false)
const resume = ref(null)
const versions = ref([])
const fileInputRef = ref(null)
const showVersions = ref(false)

const stats = computed(() => ({
  skills: resume.value?.parseResult?.skills?.length ?? 0,
  education: resume.value?.parseResult?.educationList?.length ?? 0,
  projects: resume.value?.parseResult?.projects?.length ?? 0,
  keywords: resume.value?.parseResult?.keywords?.length ?? 0
}))

const fileTypeText = computed(() => {
  const name = resume.value?.fileName || ''
  const ext = name.toLowerCase().split('.').pop()
  const map = { pdf: 'PDF', doc: 'Word', docx: 'Word' }
  return map[ext] || ext?.toUpperCase() || '文件'
})

const beforeUpload = (file) => {
  const allowed = ['.pdf', '.doc', '.docx']
  const isAllowed = allowed.some((ext) => file.name.toLowerCase().endsWith(ext))
  const isLt20M = file.size / 1024 / 1024 < 20
  if (!isAllowed) { ElMessage.error('只支持 PDF、DOC、DOCX 格式'); return false }
  if (!isLt20M) { ElMessage.error('文件不能超过 20MB'); return false }
  return true
}

const doUpload = async (file) => {
  uploading.value = true
  try {
    const res = await uploadResume(file)
    resume.value = res.data
    ElMessage.success('上传并解析成功')
    await loadVersions(res.data.resumeId)
  } catch (e) {
    ElMessage.error(e?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

const reUpload = () => fileInputRef.value?.click()

const onChooseFile = (e) => {
  const file = e.target.files && e.target.files[0]
  if (file) {
    if (!beforeUpload(file)) return
    doUpload(file)
  }
  e.target.value = ''
}

const loadVersions = async (resumeId) => {
  if (!resumeId) return
  const res = await listResumeVersions(resumeId)
  versions.value = res.data || []
}

const openPreview = async (row) => {
  const res = await getResumeFileUrl(row.versionId)
  window.open(res.data.url, '_blank')
}

const openCurrent = () => {
  if (resume.value?.fileUrl) {
    window.open(resume.value.fileUrl, '_blank')
  } else {
    ElMessage.info('当前文件暂无预览地址')
  }
}

onMounted(async () => {
  try {
    const res = await getCurrentResume()
    resume.value = res.data
    if (res.data) await loadVersions(res.data.resumeId)
  } catch (e) { /* 未上传时忽略 */ }
})
</script>

<style scoped>
.resume-page { max-width: 1180px; margin: 0 auto; padding: 4px 0 24px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 20px; font-weight: 600; }
.page-subtitle { margin: 4px 0 0; font-size: 13px; color: #909399; }
.header-actions { display: flex; align-items: center; gap: 12px; }
.panel-card { margin-bottom: 16px; }
.empty-card { display: flex; justify-content: center; padding: 40px 0; }
.card-header { display: flex; align-items: center; gap: 6px; font-weight: 600; }
.file-meta { display: flex; gap: 10px; padding: 12px; border: 1px solid #ebeef5; border-radius: 6px; background: #fafafa; }
.file-icon { font-size: 22px; color: #409eff; margin-top: 2px; }
.file-info { min-width: 0; }
.file-name { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-meta-text { font-size: 12px; color: #909399; margin-top: 2px; }
.file-actions { margin-top: 6px; }
.stat-row { display: flex; gap: 16px; }
.stat-item { flex: 1; text-align: center; padding: 8px 0; border: 1px solid #ebeef5; border-radius: 6px; background: #fafafa; }
.stat-num { font-size: 22px; font-weight: 600; color: #409eff; }
.stat-label { font-size: 12px; color: #909399; margin-top: 2px; }
.tag-wrap { display: flex; flex-wrap: wrap; }
.tag-item { margin: 0 6px 6px 0; }
.item-list { display: flex; flex-direction: column; gap: 8px; }
.item-row { display: flex; align-items: flex-start; gap: 10px; }
.item-index { flex: 0 0 auto; width: 22px; height: 22px; line-height: 22px; text-align: center; border-radius: 50%; background: #ecf5ff; color: #409eff; font-size: 12px; font-weight: 600; margin-top: 1px; }
.item-text { line-height: 1.7; }
.parse-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: rgba(255, 255, 255, 0.72);
  color: #409eff;
  font-size: 15px;
}
.parse-mask .is-loading {
  font-size: 34px;
  animation: rotate 1s linear infinite;
}
@keyframes rotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
