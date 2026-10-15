<template>
  <div class="page-container">
    <div class="page-header">
      <div class="breadcrumb">
        <el-button text @click="$router.push('/questions')">
          <el-icon><ArrowLeft /></el-icon> 返回题库
        </el-button>
        <span class="sep">/</span>
        <span>{{ isEdit ? '编辑题目' : '新建题目' }}</span>
      </div>
    </div>

    <el-card class="form-card">
      <template #header>
        <span>{{ isEdit ? '编辑题目' : '新建题目' }}</span>
      </template>

      <el-form
        ref="formRef"
        :model="formData"
        :rules="rules"
        label-width="110px"
        style="max-width: 680px"
      >
        <el-form-item label="题目内容" prop="content">
          <el-input
            v-model="formData.content"
            type="textarea"
            :rows="4"
            placeholder="请输入题目内容"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="题型" prop="type">
          <el-radio-group v-model="formData.type">
            <el-radio-button v-for="t in typeOpts" :key="t" :label="t">{{ t }}</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="难度" prop="difficulty">
          <el-rate
            v-model="formData.difficulty"
            :max="5"
            :texts="difficultyTexts"
            show-text
            :colors="['#99A9BF', '#F7BA2A', '#FF9900']"
          />
        </el-form-item>

        <el-form-item label="方向" prop="direction">
          <el-select v-model="formData.direction" placeholder="请选择技术方向" style="width: 200px">
            <el-option v-for="d in directionOpts" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>

        <el-form-item label="使用次数" prop="usageCount">
          <el-input-number v-model="formData.usageCount" :min="0" :max="9999" />
        </el-form-item>

        <el-form-item label="通过率(%)" prop="passRate">
          <el-slider
            v-model="formData.passRate"
            :min="0"
            :max="100"
            :step="0.5"
            show-input
            style="width: 400px"
          />
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="formData.status">
            <el-radio v-for="s in statusOpts" :key="s" :label="s">{{ s }}</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="submitting">
            {{ isEdit ? '保存修改' : '创建题目' }}
          </el-button>
          <el-button @click="$router.push('/questions')">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useQuestionStore } from '@/stores/question'
import type { Question } from '@/types'

const route = useRoute()
const router = useRouter()
const questionStore = useQuestionStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const isEdit = computed(() => !!route.params.id)

const formData = ref<Omit<Question, 'createTime'> & { createTime?: string }>({
  id: 0,
  content: '',
  type: '单选',
  difficulty: 3,
  direction: '前端',
  usageCount: 0,
  passRate: 60,
  status: '审核中'
})

const rules = {
  content: [
    { required: true, message: '请输入题目内容', trigger: 'blur' },
    { min: 5, max: 500, message: '题目内容应在5-500字符之间', trigger: 'blur' }
  ],
  type: [{ required: true, message: '请选择题型', trigger: 'change' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  direction: [{ required: true, message: '请选择技术方向', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const typeOpts = ['单选', '多选', '判断', '编程']
const difficultyTexts = ['简单', '较易', '中等', '较难', '困难']
const directionOpts = ['前端', '后端', '算法', '大数据']
const statusOpts = ['启用', '停用', '审核中']

onMounted(() => {
  if (isEdit.value) {
    const id = Number(route.params.id)
    const question = questionStore.getById(id)
    if (question) {
      Object.assign(formData.value, question)
    } else {
      ElMessage.error('题目不存在')
      router.push('/questions')
    }
  }
})

async function handleSubmit() {
  await formRef.value?.validate()
  submitting.value = true
  try {
    const now = new Date().toISOString()
    const question: Question = {
      ...formData.value as Question,
      id: isEdit.value ? formData.value.id : Date.now(),
      createTime: formData.value.createTime || now
    }
    questionStore.saveQuestion(question)
    ElMessage.success(isEdit.value ? '题目已更新' : '题目创建成功')
    router.push('/questions')
  } finally {
    submitting.value = false
  }
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
  .breadcrumb {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 14px;
    color: #606266;
    .sep { color: #C0C4CC; }
  }
}

.form-card {
  :deep(.el-rate) {
    height: 32px;
    line-height: 32px;
  }
}
</style>
