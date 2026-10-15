<template>
  <div class="page-container">
    <div class="flex-between" style="margin-bottom: 20px">
      <div>
        <h2 style="font-size: 20px; font-weight: 600">🏷️ 标签管理</h2>
        <p style="margin-top: 7px; color: #909399; font-size: 13px">
          维护知识、岗位、轮次、题型等标签，供题目绑定与智能组卷使用。
        </p>
      </div>
      <div style="display: flex; gap: 12px">
        <el-select v-model="filterType" placeholder="标签类型" clearable style="width: 160px" @change="fetchTags">
          <el-option v-for="(label, value) in typeOptions" :key="value" :label="label" :value="value" />
        </el-select>
        <el-button v-if="store.hasPermission('question:create')" type="primary" @click="openCreateDialog">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>新增标签
        </el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table :data="tags" style="width: 100%" v-loading="loading" empty-text="暂无标签">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="标签名称" min-width="140" />
        <el-table-column prop="code" label="标签编码" min-width="160" />
        <el-table-column label="类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="typeTag(row.type)">{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button v-if="store.hasPermission('question:update')"
                text type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
              <el-button v-if="store.hasPermission('question:update') && row.status === 1"
                text type="danger" size="small" @click="handleDisable(row)">停用</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑标签' : '新增标签'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="标签名称" prop="name">
          <el-input v-model="form.name" placeholder="例如 Spring Boot" />
        </el-form-item>
        <el-form-item label="标签编码" prop="code">
          <el-input v-model="form.code" placeholder="例如 SPRING_BOOT（大写蛇形）" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="标签类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="(label, value) in typeOptions" :key="value" :label="label" :value="value" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useUserStore } from '../store/user'
import { listQuestionTags, createQuestionTag, updateQuestionTag, disableQuestionTag } from '../api/interview'

const store = useUserStore()

const loading = ref(false)
const saving = ref(false)
const tags = ref([])
const editingId = ref(null)
const filterType = ref(null)
const dialogVisible = ref(false)
const formRef = ref()

const typeOptions = {
  KNOWLEDGE: '知识标签',
  JOB: '岗位标签',
  ROUND: '轮次标签',
  TYPE: '题型标签',
  DIFFICULTY: '难度标签'
}

const form = reactive({ name: '', code: '', type: 'KNOWLEDGE', sortOrder: 0, status: 1 })

const rules = {
  name: [{ required: true, message: '请输入标签名称', trigger: 'blur' }],
  code: [{ required: true, message: '请输入标签编码', trigger: 'blur' }],
  type: [{ required: true, message: '请选择标签类型', trigger: 'change' }]
}

const typeTag = (t) => ({ KNOWLEDGE: 'primary', JOB: 'warning', ROUND: 'success', TYPE: 'info', DIFFICULTY: 'danger' }[t] || 'info')
const typeLabel = (t) => typeOptions[t] || t

const fetchTags = async () => {
  loading.value = true
  try {
    const params = {}
    if (filterType.value) params.type = filterType.value
    const res = await listQuestionTags(params)
    tags.value = res.data || []
  } catch (e) { /* handled */ }
  loading.value = false
}

const openCreateDialog = () => {
  editingId.value = null
  Object.assign(form, { name: '', code: '', type: 'KNOWLEDGE', sortOrder: 0, status: 1 })
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name, code: row.code, type: row.type,
    sortOrder: row.sortOrder ?? 0, status: row.status ?? 1
  })
  dialogVisible.value = true
}

const handleSave = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) {
      await updateQuestionTag(editingId.value, { ...form })
      ElMessage.success('标签已更新')
    } else {
      await createQuestionTag({ ...form })
      ElMessage.success('标签已创建')
    }
    dialogVisible.value = false
    fetchTags()
  } catch (e) { /* handled */ }
  saving.value = false
}

const handleDisable = async (row) => {
  try {
    await ElMessageBox.confirm(`确定要停用标签“${row.name}”吗？`, '停用标签', {
      confirmButtonText: '停用', cancelButtonText: '取消', type: 'warning'
    })
    await disableQuestionTag(row.id)
    ElMessage.success('标签已停用')
    fetchTags()
  } catch (e) { /* cancelled or error */ }
}

onMounted(() => {
  fetchTags()
})
</script>

<style scoped>
.table-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-wrap: nowrap;
  gap: 4px;
  white-space: nowrap;
}

.table-actions :deep(.el-button) {
  margin-left: 0;
  padding: 4px 2px;
}
</style>