<template>
  <div>
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- ============ 随机抽题 ============ -->
      <el-tab-pane label="随机抽题" name="quiz">
        <el-card shadow="never">
          <div class="quiz-settings">
            <div class="setting-item">
              <span class="setting-label">知识点:</span>
              <el-select v-model="quizForm.kpId" placeholder="全部知识点" clearable filterable style="width: 200px">
                <el-option v-for="kp in kpOptions" :key="kp.id" :label="kp.name" :value="kp.id" />
              </el-select>
            </div>
            <div class="setting-item">
              <span class="setting-label">题型:</span>
              <el-select v-model="quizForm.type" style="width: 150px">
                <el-option label="全部题型" value="" />
                <el-option v-for="t in QUESTION_TYPES" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </div>
            <div class="setting-item count-item">
              <span class="setting-label">数量:</span>
              <el-slider v-model="quizForm.count" :min="1" :max="50" show-input style="width: 220px" />
            </div>
            <div class="quiz-actions">
              <el-button type="primary" :loading="quizLoading" @click="doRandom">开始抽题</el-button>
              <el-button :icon="CopyDocument" :disabled="!quizList.length" @click="copyJson">复制JSON</el-button>
              <el-button :icon="Download" :disabled="!quizList.length" @click="exportJson">导出JSON文件</el-button>
            </div>
          </div>

          <el-divider />
          <div class="quiz-result-tip" v-if="quizList.length">
            本次共抽取 <b>{{ quizList.length }}</b> 道已上架题目（供考试/组卷等下游直接集成）
          </div>

          <div v-if="quizList.length" class="quiz-list">
            <div v-for="(q, idx) in quizList" :key="q.id" class="quiz-card">
              <div class="quiz-head">
                <span class="quiz-no">{{ idx + 1 }}.</span>
                <el-tag :type="tagOf(typeMap, q.type)" size="small">{{ labelOf(typeMap, q.type) }}</el-tag>
                <span class="quiz-kp" v-if="q.knowledgePointName">{{ q.knowledgePointName }}</span>
                <div class="flex-spacer" />
                <el-tag size="small" type="info" effect="plain">#{{ q.id }}</el-tag>
                <el-button
                  link
                  type="primary"
                  @click="toggleAnswer(idx)"
                >
                  {{ shownAnswer === idx ? '隐藏答案/解析' : '显示答案/解析' }}
                </el-button>
              </div>
              <div class="quiz-stem">{{ q.stem }}</div>
              <div v-if="q.options && q.options.length" class="quiz-options">
                <div
                  v-for="(opt, i) in q.options"
                  :key="i"
                  class="quiz-option"
                  :class="{ 'is-correct': shownAnswer === idx && isCorrect(q, i) }"
                >
                  <span class="opt-letter">{{ letterOf(i) }}.</span>
                  <span class="opt-text">{{ opt }}</span>
                  <el-tag v-if="shownAnswer === idx && isCorrect(q, i)" type="success" size="small" effect="light">正确答案</el-tag>
                </div>
              </div>

              <template v-if="shownAnswer === idx">
                <div class="quiz-answer">
                  <span class="qa-label">参考答案:</span>
                  <span class="qa-value">{{ answerText(q) }}</span>
                </div>
                <div v-if="q.analysis" class="quiz-analysis">解析: {{ q.analysis }}</div>
              </template>
            </div>
          </div>

          <el-empty v-else-if="!quizLoading" description="点击「开始抽题」抽取已上架题目" :image-size="90" />
        </el-card>
      </el-tab-pane>

      <!-- ============ 检索已上架题目 ============ -->
      <el-tab-pane label="检索已上架题目" name="search">
        <div class="page-card">
          <div class="filter-bar">
            <el-input
              v-model="searchQuery.keyword"
              placeholder="题干关键词"
              clearable
              style="width: 220px"
              @keyup.enter="onSearch"
              @clear="onSearch"
            />
            <el-select v-model="searchQuery.kpId" placeholder="知识点" clearable filterable style="width: 180px">
              <el-option v-for="kp in kpOptions" :key="kp.id" :label="kp.name" :value="kp.id" />
            </el-select>
            <el-select v-model="searchQuery.type" placeholder="题型" clearable style="width: 130px">
              <el-option v-for="t in QUESTION_TYPES" :key="t.value" :label="t.label" :value="t.value" />
            </el-select>
            <el-button type="primary" @click="onSearch">查 询</el-button>
            <el-button @click="onReset">重 置</el-button>
            <el-tag type="success" effect="plain" class="status-hint">仅检索已上架题目</el-tag>
          </div>
        </div>

        <el-card shadow="never">
          <el-table v-loading="searchLoading" :data="searchList" stripe>
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="expand-wrap">
                  <div class="expand-row">
                    <span class="expand-label">题干</span>
                    <div class="expand-text">{{ row.stem }}</div>
                  </div>
                  <div v-if="row.options && row.options.length" class="expand-row">
                    <span class="expand-label">选项</span>
                    <div class="expand-options">
                      <div
                        v-for="(opt, i) in row.options"
                        :key="i"
                        class="expand-option"
                        :class="{ 'is-correct': isCorrect(row, i) }"
                      >
                        {{ letterOf(i) }}. {{ opt }}
                        <el-tag v-if="isCorrect(row, i)" type="success" size="small">正确答案</el-tag>
                      </div>
                    </div>
                  </div>
                  <div class="expand-row">
                    <span class="expand-label">参考答案</span>
                    <div class="expand-text strong">{{ answerText(row) }}</div>
                  </div>
                  <div v-if="row.analysis" class="expand-row">
                    <span class="expand-label">解析</span>
                    <div class="expand-text">{{ row.analysis }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="knowledgePointName" label="知识点" width="140" show-overflow-tooltip />
            <el-table-column label="题型" width="95">
              <template #default="{ row }">
                <el-tag :type="tagOf(typeMap, row.type)" size="small">{{ labelOf(typeMap, row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="stem" label="题干" min-width="300" show-overflow-tooltip />
            <el-table-column label="难度" width="130" align="center">
              <template #default="{ row }">
                <el-rate :model-value="row.difficulty" disabled size="small" />
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="tagOf(statusMap, row.status)" size="small">{{ labelOf(statusMap, row.status) }}</el-tag>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无已上架题目" :image-size="80" />
            </template>
          </el-table>

          <div class="pagination-bar">
            <el-pagination
              v-model:current-page="searchQuery.page"
              v-model:page-size="searchQuery.size"
              :page-sizes="[10, 20, 50]"
              :total="searchTotal"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="onSizeChange"
              @current-change="loadSearch"
            />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { CopyDocument, Download } from '@element-plus/icons-vue'
import { allKnowledgePoints, pageQuestions, quizRandom, saveBlob } from '@/api'
import { QUESTION_TYPES, labelOf, sourceMap, statusMap, tagOf, typeMap } from '@/utils/dicts'
import { letterOf, splitLetters } from '@/utils/format'

const activeTab = ref('quiz')
const kpOptions = ref([])

onMounted(async () => {
  try {
    kpOptions.value = (await allKnowledgePoints()) || []
  } catch (e) {
    /* 忽略 */
  }
})

// ---------- Tab1: 随机抽题 ----------
const quizForm = reactive({ kpId: null, type: '', count: 10 })
const quizLoading = ref(false)
const quizList = ref([])
const shownAnswer = ref(-1)

async function doRandom() {
  quizLoading.value = true
  shownAnswer.value = -1
  try {
    quizList.value = (await quizRandom({
      kpId: quizForm.kpId || null,
      type: quizForm.type || null,
      count: quizForm.count
    })) || []
    if (!quizList.value.length) {
      ElMessage.info('当前条件下没有已上架题目, 请调整筛选条件')
    }
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    quizLoading.value = false
  }
}

function toggleAnswer(idx) {
  shownAnswer.value = shownAnswer.value === idx ? -1 : idx
}

function exportFileName() {
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  return `随机抽题_${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}_${pad(now.getHours())}${pad(now.getMinutes())}.json`
}

async function copyJson() {
  const text = JSON.stringify(quizList.value, null, 2)
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('JSON 已复制到剪贴板')
  } catch (e) {
    // 剪贴板不可用时退化为选中提示
    ElMessage.warning('复制失败, 请使用「导出JSON文件」')
  }
}

function exportJson() {
  const blob = new Blob([JSON.stringify(quizList.value, null, 2)], {
    type: 'application/json;charset=utf-8'
  })
  saveBlob(blob, exportFileName())
  ElMessage.success('JSON 文件已导出')
}

// ---------- Tab2: 检索已上架题目 ----------
const searchQuery = reactive({ keyword: '', kpId: null, type: '', page: 1, size: 10 })
const searchList = ref([])
const searchTotal = ref(0)
const searchLoading = ref(false)

async function loadSearch() {
  searchLoading.value = true
  try {
    const res = await pageQuestions({
      status: 'PUBLISHED',
      keyword: searchQuery.keyword.trim() || undefined,
      kpId: searchQuery.kpId || undefined,
      type: searchQuery.type || undefined,
      page: searchQuery.page - 1,
      size: searchQuery.size
    })
    searchList.value = res.list || []
    searchTotal.value = res.total || 0
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    searchLoading.value = false
  }
}

function onSearch() {
  searchQuery.page = 1
  loadSearch()
}

function onReset() {
  Object.assign(searchQuery, { keyword: '', kpId: null, type: '', page: 1 })
  loadSearch()
}

function onSizeChange() {
  searchQuery.page = 1
  loadSearch()
}

function onTabChange() {
  if (activeTab.value === 'search' && !searchList.value.length) loadSearch()
}

// ---------- 通用渲染辅助 ----------
function isCorrect(q, i) {
  if (!q || !(q.type === 'SINGLE' || q.type === 'MULTIPLE') || !Array.isArray(q.options)) return false
  return splitLetters(q.answer).includes(letterOf(i))
}

function answerText(q) {
  if (!q) return '-'
  if (q.type === 'JUDGE') return q.answer === '对' ? '对（正确）' : q.answer === '错' ? '错（错误）' : q.answer || '-'
  return q.answer || '-'
}
</script>

<style scoped>
.quiz-settings {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 18px;
}

.setting-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.setting-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}

.count-item {
  flex: 1;
  min-width: 320px;
}

.quiz-actions {
  display: flex;
  gap: 10px;
  margin-left: auto;
}

.quiz-result-tip {
  font-size: 13px;
  color: #909399;
  margin-bottom: 12px;
}

.quiz-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 72vh;
  overflow: auto;
  padding-right: 4px;
}

.quiz-card {
  border: 1px solid var(--el-border-color-light);
  border-left: 4px solid #409eff;
  border-radius: 8px;
  padding: 12px 14px;
}

.quiz-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.quiz-no {
  font-weight: 700;
  color: #409eff;
  flex-shrink: 0;
}

.quiz-kp {
  font-size: 12px;
  color: #909399;
}

.flex-spacer {
  flex: 1;
}

.quiz-stem {
  font-size: 14px;
  line-height: 1.7;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.quiz-options {
  margin-top: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.quiz-option {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #606266;
  line-height: 1.7;
  border-radius: 4px;
  padding: 0 6px;
}

.quiz-option.is-correct {
  background: #f0f9eb;
  color: #303133;
}

.opt-letter {
  font-weight: 600;
  flex-shrink: 0;
}

.opt-text {
  flex: 1;
  word-break: break-word;
}

.quiz-answer {
  margin-top: 8px;
  font-size: 13px;
}

.qa-label {
  color: #67c23a;
  font-weight: 600;
}

.qa-value {
  color: #303133;
  font-weight: 600;
  word-break: break-word;
}

.quiz-analysis {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  line-height: 1.7;
  background: #fafafa;
  border-left: 3px solid #e6a23c;
  padding: 6px 10px;
  border-radius: 4px;
}

.status-hint {
  margin-left: 6px;
}

.expand-wrap {
  padding: 4px 8px 4px 50px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 860px;
}

.expand-row {
  display: flex;
  gap: 12px;
}

.expand-label {
  flex-shrink: 0;
  width: 70px;
  font-size: 13px;
  color: #909399;
  font-weight: 600;
}

.expand-text {
  font-size: 13px;
  color: #303133;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  flex: 1;
}

.expand-text.strong {
  color: #67c23a;
  font-weight: 600;
}

.expand-options {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.expand-option {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #606266;
  border-radius: 4px;
  padding: 1px 6px;
}

.expand-option.is-correct {
  background: #f0f9eb;
  color: #303133;
}
</style>
