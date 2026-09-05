<template>
  <div>
    <div class="page-head">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item :to="{ path: '/questions' }">题库管理</el-breadcrumb-item>
        <el-breadcrumb-item>{{ isEdit ? `编辑题目 #${editingId}` : '题目录入' }}</el-breadcrumb-item>
      </el-breadcrumb>
      <el-button :icon="Back" @click="goBack">返回</el-button>
    </div>

    <el-card shadow="never" v-loading="loading">
      <el-form ref="formRef" :model="form" label-width="100px" class="edit-form">
        <el-form-item label="知识点" required>
          <div class="kp-line">
            <el-select v-model="form.knowledgePointId" placeholder="请选择知识点" filterable style="width: 320px">
              <el-option v-for="kp in kpOptions" :key="kp.id" :label="kp.name" :value="kp.id">
                <span>{{ kp.name }}</span>
                <span v-if="kp.category" class="kp-opt-cat">{{ kp.category }}</span>
              </el-option>
            </el-select>
            <el-button link type="primary" :icon="Plus" @click="kpDialog.visible = true">＋新增知识点</el-button>
          </div>
        </el-form-item>

        <el-form-item label="题型" required>
          <el-radio-group v-model="form.type" :disabled="isEdit" @change="onTypeChange">
            <el-radio-button v-for="t in QUESTION_TYPES" :key="t.value" :value="t.value">{{ t.label }}</el-radio-button>
          </el-radio-group>
          <div v-if="isEdit" class="form-tip">编辑状态下题型不可修改</div>
        </el-form-item>

        <el-form-item label="难度">
          <div class="diff-line">
            <el-rate v-model="form.difficulty" :show-text="false" />
            <span class="diff-text">{{ diffText }}</span>
          </div>
        </el-form-item>

        <el-form-item label="题干" required>
          <el-input
            v-model="form.stem"
            type="textarea"
            :rows="4"
            maxlength="3000"
            show-word-limit
            placeholder="请输入题目题干..."
          />
        </el-form-item>

        <el-form-item v-if="isChoice" label="选项" required>
          <div class="option-list">
            <div v-for="(opt, i) in form.options" :key="i" class="option-row">
              <span class="option-letter">{{ letterOf(i) }}.</span>
              <el-input v-model="form.options[i]" :placeholder="`选项 ${letterOf(i)}`" maxlength="500" />
              <el-button
                v-if="form.options.length > 2"
                link
                type="danger"
                :icon="Delete"
                @click="removeOption(i)"
              />
            </div>
            <el-button
              v-if="form.options.length < 8"
              size="small"
              :icon="Plus"
              plain
              @click="addOption"
            >
              添加选项({{ form.options.length }}/8)
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="答案" required>
          <template v-if="form.type === 'SINGLE'">
            <el-radio-group v-model="form.answer">
              <el-radio
                v-for="(opt, i) in form.options"
                :key="i"
                :value="letterOf(i)"
                :disabled="!String(opt || '').trim()"
              >
                {{ letterOf(i) }}
              </el-radio>
            </el-radio-group>
          </template>
          <template v-else-if="form.type === 'MULTIPLE'">
            <el-checkbox-group v-model="answerLetters">
              <el-checkbox
                v-for="(opt, i) in form.options"
                :key="i"
                :value="letterOf(i)"
                :disabled="!String(opt || '').trim()"
              >
                {{ letterOf(i) }}
              </el-checkbox>
            </el-checkbox-group>
            <div class="form-tip">多选题请选择至少 2 个正确选项</div>
          </template>
          <template v-else-if="form.type === 'JUDGE'">
            <el-radio-group v-model="form.answer">
              <el-radio value="对">对（正确）</el-radio>
              <el-radio value="错">错（错误）</el-radio>
            </el-radio-group>
          </template>
          <template v-else-if="form.type === 'SHORT'">
            <el-input
              v-model="form.answer"
              type="textarea"
              :rows="3"
              maxlength="2000"
              show-word-limit
              placeholder="请输入参考答案(评分要点)"
              style="width: 480px"
            />
          </template>
        </el-form-item>

        <el-form-item label="解析">
          <el-input
            v-model="form.analysis"
            type="textarea"
            :rows="3"
            maxlength="3000"
            show-word-limit
            placeholder="答案解析(选填)"
          />
        </el-form-item>

        <el-form-item>
          <div class="form-actions">
            <el-button type="primary" :loading="saving" @click="onSave(false)">保存（草稿）</el-button>
            <el-button type="success" :loading="savingSubmit" @click="onSave(true)">保存并提交审核</el-button>
            <el-button @click="goBack">返回</el-button>
          </div>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 新增知识点弹窗 -->
    <el-dialog v-model="kpDialog.visible" :title="kpDialog.title" width="480px">
      <el-form ref="kpFormRef" :model="kpDialog.form" :rules="kpRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="kpDialog.form.name" placeholder="知识点名称(必填)" maxlength="100" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-input v-model="kpDialog.form.category" placeholder="分类(选填)" maxlength="50" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="kpDialog.form.description"
            type="textarea"
            :rows="2"
            placeholder="描述(选填)"
            maxlength="500"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="kpDialog.visible = false">取 消</el-button>
        <el-button type="primary" :loading="kpDialog.saving" @click="onCreateKp">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back, Delete, Plus } from '@element-plus/icons-vue'
import {
  allKnowledgePoints,
  createKnowledgePoint,
  createQuestion,
  getQuestion,
  submitQuestion,
  updateQuestion
} from '@/api'
import { QUESTION_TYPES } from '@/utils/dicts'
import { letterOf, splitLetters } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const editingId = computed(() => {
  const id = route.params?.id
  return id ? Number(id) : null
})
const isEdit = computed(() => !!editingId.value)

const loading = ref(false)
const saving = ref(false)
const savingSubmit = ref(false)
const kpOptions = ref([])
const formRef = ref()

const form = reactive({
  type: 'SINGLE',
  knowledgePointId: null,
  difficulty: 3,
  stem: '',
  options: ['', ''],
  answer: '',
  analysis: ''
})
const answerLetters = ref([])

const isChoice = computed(() => ['SINGLE', 'MULTIPLE'].includes(form.type))
const diffText = computed(() => {
  const map = { 1: '较易', 2: '偏易', 3: '中等', 4: '偏难', 5: '较难' }
  return map[form.difficulty] || ''
})

// ---------- 数据加载 ----------
async function loadKps() {
  try {
    kpOptions.value = (await allKnowledgePoints()) || []
  } catch (e) {
    /* 忽略 */
  }
}

async function loadQuestion() {
  if (!editingId.value) return
  loading.value = true
  try {
    const q = await getQuestion(editingId.value)
    form.type = q.type || 'SINGLE'
    form.knowledgePointId = q.knowledgePointId || null
    form.difficulty = q.difficulty || 3
    form.stem = q.stem || ''
    form.analysis = q.analysis || ''
    form.answer = q.answer || ''
    form.options =
      q.type === 'SINGLE' || q.type === 'MULTIPLE'
        ? (q.options && q.options.length ? q.options.slice() : ['', ''])
        : []
    answerLetters.value = q.type === 'MULTIPLE' ? splitLetters(q.answer) : []
  } catch (e) {
    ElMessage.error('加载题目失败')
    router.replace('/questions')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadKps()
  loadQuestion()
})

// ---------- 选项编辑 ----------
function onTypeChange() {
  if (isEdit.value) return // 编辑时不可改题型
  form.answer = ''
  answerLetters.value = []
  if (form.type === 'SINGLE' || form.type === 'MULTIPLE') {
    if (!form.options || !form.options.length) form.options = ['', '']
  } else {
    form.options = []
    if (form.type === 'JUDGE') form.answer = '对'
  }
}

function addOption() {
  if (form.options.length >= 8) return
  form.options.push('')
}

function removeOption(i) {
  if (form.options.length <= 2) return
  form.options.splice(i, 1)
  // 清理失效的答案字母
  if (form.type === 'SINGLE' && !form.options.some((_, idx) => letterOf(idx) === form.answer)) {
    form.answer = ''
  }
  if (form.type === 'MULTIPLE') {
    answerLetters.value = answerLetters.value.filter((l) =>
      form.options.some((_, idx) => letterOf(idx) === l)
    )
  }
}

// ---------- 校验并提交 ----------
function validateForm() {
  if (!form.knowledgePointId) {
    ElMessage.warning('请选择知识点')
    return null
  }
  if (!form.stem.trim()) {
    ElMessage.warning('请输入题干')
    return null
  }
  if (!form.difficulty || form.difficulty < 1) {
    ElMessage.warning('请设置难度(1~5)')
    return null
  }
  let options
  let answer = form.answer
  if (isChoice.value) {
    options = form.options.map((o) => String(o || '').trim())
    const blanks = options.filter((o) => !o).length
    if (options.length < 2 || blanks > 0) {
      ElMessage.warning('选择题至少需要 2 个非空选项')
      return null
    }
    if (form.type === 'SINGLE') {
      if (!answer || !splitLetters(answer).length) {
        ElMessage.warning('请选择单选题答案')
        return null
      }
    } else {
      const letters = answerLetters.value.slice().sort()
      if (letters.length < 2) {
        ElMessage.warning('多选题请至少选择 2 个正确答案')
        return null
      }
      answer = letters.join(',')
    }
  } else {
    options = null
    if (form.type === 'JUDGE' && !['对', '错'].includes(answer)) {
      ElMessage.warning('请选择判断题答案(对/错)')
      return null
    }
    if (form.type === 'SHORT' && !String(answer || '').trim()) {
      ElMessage.warning('请填写简答题参考答案')
      return null
    }
  }
  return {
    type: form.type,
    stem: form.stem.trim(),
    options,
    answer,
    analysis: form.analysis.trim() || undefined,
    difficulty: form.difficulty,
    knowledgePointId: form.knowledgePointId
  }
}

async function onSave(submitAfter) {
  const payload = validateForm()
  if (!payload) return
  submitAfter ? (savingSubmit.value = true) : (saving.value = true)
  try {
    let id = editingId.value
    if (id) {
      await updateQuestion(id, payload)
    } else {
      const view = await createQuestion(payload)
      id = view.id
    }
    if (submitAfter) {
      await submitQuestion(id)
      ElMessage.success('已保存并提交审核')
    } else {
      ElMessage.success('已保存为草稿')
    }
    router.push('/questions')
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
    savingSubmit.value = false
  }
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/questions')
}

// ---------- 新增知识点 ----------
const kpFormRef = ref()
const kpRules = {
  name: [{ required: true, message: '请输入知识点名称', trigger: 'blur' }]
}
const kpDialog = reactive({
  visible: false,
  saving: false,
  title: '新增知识点',
  form: { name: '', category: '', description: '' }
})

async function onCreateKp() {
  try {
    await kpFormRef.value.validate()
  } catch (e) {
    return
  }
  kpDialog.saving = true
  try {
    const kp = await createKnowledgePoint({
      name: kpDialog.form.name.trim(),
      category: kpDialog.form.category.trim() || undefined,
      description: kpDialog.form.description.trim() || undefined
    })
    ElMessage.success('知识点创建成功')
    kpDialog.visible = false
    Object.assign(kpDialog.form, { name: '', category: '', description: '' })
    await loadKps()
    form.knowledgePointId = kp.id
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    kpDialog.saving = false
  }
}
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.edit-form {
  max-width: 860px;
}

.kp-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.kp-opt-cat {
  float: right;
  color: #909399;
  font-size: 12px;
  margin-left: 14px;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  width: 100%;
}

.diff-line {
  display: flex;
  align-items: center;
  gap: 12px;
}

.diff-text {
  font-size: 13px;
  color: #606266;
}

.option-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.option-row {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 640px;
}

.option-letter {
  font-weight: 600;
  color: #606266;
  flex-shrink: 0;
  width: 18px;
}

.form-actions {
  display: flex;
  gap: 10px;
}
</style>
