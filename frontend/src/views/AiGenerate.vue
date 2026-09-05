<template>
  <div>
    <div class="page-head">
      <div class="page-head-title">智能出题（按知识点自动出题）</div>
    </div>

    <!-- 出题源状态 -->
    <el-alert
      v-if="!provider"
      type="info"
      :closable="false"
      show-icon
      title="正在获取出题源配置..."
      class="mb14"
    />
    <el-alert
      v-else-if="!provider.apiKeyConfigured"
      type="warning"
      :closable="false"
      show-icon
      class="mb14"
    >
      <template #title>未配置大模型 API Key —— 当前使用{{ provider.label }}本地模拟出题（可完整体验全流程）</template>
      <div class="mode-tip">{{ provider.modeTip }}</div>
    </el-alert>
    <el-alert v-else type="success" :closable="false" show-icon class="mb14">
      <template #title>当前使用 {{ provider.label }}（{{ provider.model }}）</template>
      <div class="mode-tip">{{ provider.modeTip }}</div>
    </el-alert>

    <!-- 出题表单 -->
    <el-card shadow="never" class="form-card">
      <el-form label-width="90px">
        <el-form-item label="知识点" required>
          <el-select
            v-model="form.knowledgePointId"
            placeholder="请选择知识点"
            filterable
            clearable
            style="width: 340px"
          >
            <el-option
              v-for="kp in kpOptions"
              :key="kp.id"
              :label="kp.name"
              :value="kp.id"
            >
              <span>{{ kp.name }}</span>
              <span v-if="kp.category" class="kp-opt-cat">{{ kp.category }}</span>
            </el-option>
          </el-select>
        </el-form-item>

        <el-form-item label="题型">
          <el-radio-group v-model="form.type">
            <el-radio-button value="">全部题型（均衡配比）</el-radio-button>
            <el-radio-button v-for="t in QUESTION_TYPES" :key="t.value" :value="t.value">
              {{ t.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="数量">
          <el-input-number v-model="form.count" :min="1" :max="30" />
          <span class="tip">道 / 题型（选择"全部题型"时按四种题型均衡分配）</span>
        </el-form-item>

        <el-form-item label="难度">
          <div class="diff-line">
            <el-rate v-model="form.difficulty" :show-text="false" />
            <span class="diff-text">{{ diffText }}</span>
          </div>
        </el-form-item>

        <el-form-item label="附加要求">
          <el-input
            v-model="form.extraInstruction"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            placeholder="例如: 结合二十大精神 / 题目尽量贴近岗位实际 / 解析要详细...(选填)"
            style="width: 560px"
          />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" size="large" :loading="generating" @click="onGenerate">
            {{ generating ? '生成中, 请稍候...' : '开始生成' }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 生成结果 -->
    <el-card v-if="result" shadow="never" class="result-card">
      <template #header>
        <div class="result-head">
          <div class="result-summary">
            本次请求生成 <b>{{ result.requested }}</b> 道，通过格式校验
            <b class="valid-num">{{ result.validCount }}</b> 道
            <el-tag v-for="c in typeCounts" :key="c.label" size="small" class="type-chip" :type="c.tag">
              {{ c.label }} {{ c.count }}
            </el-tag>
          </div>
          <div class="result-actions">
            <el-button
              type="primary"
              size="large"
              :disabled="!result.validCount"
              :loading="saving"
              @click="onSaveValid"
            >
              保存通过校验的题目（{{ result.validCount }}）→ AI生成待处理
            </el-button>
            <el-button size="large" @click="$router.push('/questions')">去题库查看</el-button>
          </div>
        </div>
        <div class="result-sub">出题源: {{ result.providerLabel }}，题目经统一格式校验，未落库</div>
      </template>

      <div v-if="result.items && result.items.length" class="gen-list">
        <div
          v-for="item in result.items"
          :key="item.index"
          class="gen-card"
          :class="stateClass(item)"
        >
          <div class="gen-head">
            <el-icon :size="18" class="state-icon"><component :is="stateIcon(item)" /></el-icon>
            <span class="gen-no">第 {{ item.index + 1 }} 题</span>
            <el-tag :type="tagOf(typeMap, item.question && item.question.type)" size="small">
              {{ labelOf(typeMap, item.question && item.question.type) }}
            </el-tag>
            <el-tag v-if="item.duplicate" type="warning" size="small" effect="dark">与库中重复</el-tag>
            <el-tag v-if="!item.valid" type="danger" size="small" effect="dark">校验未通过</el-tag>
            <el-tag v-else type="success" size="small" effect="dark">校验通过</el-tag>
          </div>

          <el-alert v-if="!item.valid" type="error" :closable="false" class="reason-alert">
            <template #title>格式校验未通过原因：</template>
            <div class="reason-tags">
              <el-tag v-for="(r, i) in item.reasons" :key="i" type="danger" size="small" class="reason-tag">
                {{ r }}
              </el-tag>
            </div>
          </el-alert>

          <template v-if="item.question">
            <div class="gen-stem">{{ item.question.stem }}</div>
            <div
              v-if="item.question.options && item.question.options.length"
              class="gen-options"
            >
              <div v-for="(opt, i) in item.question.options" :key="i" class="gen-option">
                <span class="opt-letter">{{ letterOf(i) }}.</span> {{ opt }}
              </div>
            </div>
            <div class="gen-answer">
              <span class="gen-answer-label">参考答案:</span> {{ item.question.answer }}
            </div>
            <div v-if="item.question.analysis" class="gen-analysis">{{ item.question.analysis }}</div>
          </template>
        </div>
      </div>

      <el-result
        v-if="!result.validCount"
        icon="warning"
        title="没有通过校验的题目"
        sub-title="供验收演示：未配置 API Key 时使用本地模拟生成，会全部通过；真实千帆模型可能因返回格式不合规出现个别失败，属正常容错，可点击“开始生成”重试。"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { aiGenerate, aiSave, allKnowledgePoints, getAiProvider } from '@/api'
import { QUESTION_TYPES, labelOf, tagOf, typeMap } from '@/utils/dicts'
import { letterOf } from '@/utils/format'

const provider = ref(null)
const kpOptions = ref([])
const generating = ref(false)
const saving = ref(false)
const result = ref(null)

const form = reactive({
  knowledgePointId: null,
  type: '',
  count: 5,
  difficulty: 3,
  extraInstruction: ''
})

const diffText = computed(() => {
  const map = { 1: '较易', 2: '偏易', 3: '中等', 4: '偏难', 5: '较难' }
  return map[form.difficulty] || ''
})

onMounted(async () => {
  try {
    provider.value = await getAiProvider()
  } catch (e) {
    /* 拦截器已提示 */
  }
  try {
    kpOptions.value = (await allKnowledgePoints()) || []
  } catch (e) {
    /* 忽略 */
  }
})

async function onGenerate() {
  if (!form.knowledgePointId) {
    ElMessage.warning('请先选择知识点')
    return
  }
  generating.value = true
  result.value = null
  try {
    const res = await aiGenerate({
      knowledgePointId: form.knowledgePointId,
      type: form.type || null,
      count: form.count,
      difficulty: form.difficulty,
      extraInstruction: form.extraInstruction.trim() || undefined
    })
    result.value = res
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    generating.value = false
  }
}

const typeCounts = computed(() => {
  if (!result.value) return []
  const counts = {}
  for (const item of result.value.items || []) {
    if (item.valid && item.question && item.question.type) {
      counts[item.question.type] = (counts[item.question.type] || 0) + 1
    }
  }
  return QUESTION_TYPES.filter((t) => counts[t.value]).map((t) => ({
    label: t.label,
    count: counts[t.value],
    tag: t.tag
  }))
})

function stateIcon(item) {
  if (item.duplicate) return 'WarningFilled'
  return item.valid ? 'CircleCheckFilled' : 'CircleCloseFilled'
}

function stateClass(item) {
  if (item.duplicate) return 'state-dup'
  return item.valid ? 'state-ok' : 'state-bad'
}

async function onSaveValid() {
  const items = (result.value?.items || []).filter((i) => i.valid && !i.duplicate)
  if (!items.length) return
  saving.value = true
  try {
    const kp = kpOptions.value.find((k) => k.id === form.knowledgePointId)
    const kpName = kp ? kp.name : '未知知识点'
    const now = new Date()
    const pad = (n) => String(n).padStart(2, '0')
    const batchLabel = `AI-${kpName}-${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}`
    const res = await aiSave({ questions: items.map((i) => i.question), batchLabel })
    ElMessage.success(
      `保存成功 ${res.saved} 道（状态: AI生成待处理）；跳过重复 ${res.skippedDuplicate} 道，跳过校验未通过 ${res.skippedInvalid} 道`
    )
    result.value = null
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.page-head {
  margin-bottom: 14px;
}

.page-head-title {
  font-size: 16px;
  font-weight: 600;
}

.mb14 {
  margin-bottom: 14px;
}

.mode-tip {
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
}

.form-card {
  margin-bottom: 14px;
}

.form-card :deep(.el-form-item) {
  margin-bottom: 18px;
}

.tip {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}

.kp-opt-cat {
  float: right;
  color: #909399;
  font-size: 12px;
  margin-left: 16px;
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

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.result-summary {
  font-size: 14px;
  color: #303133;
}

.valid-num {
  color: #67c23a;
  font-size: 16px;
}

.type-chip {
  margin-left: 8px;
}

.result-actions {
  display: flex;
  gap: 10px;
}

.result-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
}

.gen-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 70vh;
  overflow: auto;
  padding-right: 4px;
}

.gen-card {
  border: 1px solid var(--el-border-color-light);
  border-left-width: 4px;
  border-radius: 8px;
  padding: 12px 14px;
}

.gen-card.state-ok {
  border-left-color: #67c23a;
}

.gen-card.state-bad {
  border-left-color: #f56c6c;
  background: #fef7f7;
}

.gen-card.state-dup {
  border-left-color: #e6a23c;
  background: #fdf6ec;
}

.gen-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.state-icon {
  flex-shrink: 0;
}

.state-ok .state-icon {
  color: #67c23a;
}

.state-bad .state-icon {
  color: #f56c6c;
}

.state-dup .state-icon {
  color: #e6a23c;
}

.gen-no {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
}

.reason-alert {
  margin-bottom: 8px;
}

.reason-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.gen-stem {
  font-size: 14px;
  line-height: 1.7;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.gen-options {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.gen-option {
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
}

.opt-letter {
  font-weight: 600;
}

.gen-answer {
  margin-top: 6px;
  font-size: 13px;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.gen-answer-label {
  color: #67c23a;
  font-weight: 600;
}

.gen-analysis {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.7;
  background: #fafafa;
  border-radius: 4px;
  padding: 6px 10px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
