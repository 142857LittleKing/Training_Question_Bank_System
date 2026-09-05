<template>
  <div>
    <el-alert type="warning" :closable="false" show-icon class="mb14">
      <template #title>当前待审核 {{ total }} 道题</template>
      对题目进行审核后, 题目将进入"已上架"或"已驳回"状态; 通过审核的题目会出现在公开抽题接口中。
    </el-alert>

    <!-- 筛选 -->
    <div class="page-card">
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="题干关键词"
          clearable
          style="width: 220px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-select v-model="query.kpId" placeholder="知识点" clearable filterable style="width: 180px">
          <el-option v-for="kp in kpOptions" :key="kp.id" :label="kp.name" :value="kp.id" />
        </el-select>
        <el-select v-model="query.type" placeholder="题型" clearable style="width: 130px">
          <el-option v-for="t in QUESTION_TYPES" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-button type="primary" @click="onSearch">查 询</el-button>
        <el-button @click="onReset">重 置</el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="knowledgePointName" label="知识点" width="140" show-overflow-tooltip />
        <el-table-column label="题型" width="95">
          <template #default="{ row }">
            <el-tag :type="tagOf(typeMap, row.type)" size="small">{{ labelOf(typeMap, row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="stem" label="题干" min-width="260" show-overflow-tooltip />
        <el-table-column label="难度" width="130" align="center">
          <template #default="{ row }">
            <el-rate :model-value="row.difficulty" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column label="来源" width="110">
          <template #default="{ row }">
            <el-tag :type="tagOf(sourceMap, row.source)" size="small" effect="plain">{{ labelOf(sourceMap, row.source) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="创建人" width="110" show-overflow-tooltip />
        <el-table-column label="提交时间" width="168">
          <template #default="{ row }">{{ fmtTime(row.submittedAt || row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">查看</el-button>
            <el-button link type="success" @click="openAction('approve', row)">通过审核</el-button>
            <el-button link type="danger" @click="openAction('reject', row)">驳回</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无待审核题目 🎉" :image-size="80" />
        </template>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="onSizeChange"
          @current-change="load"
        />
      </div>
    </el-card>

    <!-- 审核操作弹窗 -->
    <el-dialog v-model="action.visible" :title="action.mode === 'approve' ? '审核通过' : '审核驳回'" width="480px">
      <div class="action-stem" v-if="action.row">
        <el-tag :type="tagOf(typeMap, action.row.type)" size="small">{{ labelOf(typeMap, action.row.type) }}</el-tag>
        <span class="action-stem-text">#{{ action.row.id }} {{ action.row.stem }}</span>
      </div>
      <el-form label-width="70px" class="action-form">
        <el-form-item :label="action.mode === 'approve' ? '审核意见' : '驳回原因'" required>
          <el-input
            v-model="action.comment"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            :placeholder="action.mode === 'approve' ? '审核通过意见(选填, 默认: 审核通过)' : '请填写驳回原因(必填), 便于录入员修改后重新提交'"
          />
        </el-form-item>
      </el-form>
      <div v-if="action.mode === 'reject'" class="reject-tip">
        驳回后题目回到录入员手中, 修改后可再次提交审核。
      </div>
      <template #footer>
        <el-button @click="action.visible = false">取 消</el-button>
        <el-button :type="action.mode === 'approve' ? 'success' : 'danger'" :loading="action.submitting" @click="confirmAction">
          {{ action.mode === 'approve' ? '确认通过并上架' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 题目详情抽屉(与题库管理共用结构) -->
    <el-drawer v-model="drawer.visible" size="700px" :destroy-on-close="true">
      <template #header>
        <div class="drawer-title">
          <span>题目详情</span>
          <el-tag v-if="drawer.question" :type="tagOf(statusMap, drawer.question.status)" size="small">
            {{ labelOf(statusMap, drawer.question.status) }}
          </el-tag>
        </div>
      </template>

      <div v-loading="drawer.loading" class="detail-wrap">
        <template v-if="drawer.question">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="题目ID">{{ drawer.question.id }}</el-descriptions-item>
            <el-descriptions-item label="题型/来源">
              <el-tag :type="tagOf(typeMap, drawer.question.type)" size="small">{{ labelOf(typeMap, drawer.question.type) }}</el-tag>
              <span class="sep" />
              <el-tag :type="tagOf(sourceMap, drawer.question.source)" size="small" effect="plain">{{ labelOf(sourceMap, drawer.question.source) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="知识点">{{ drawer.question.knowledgePointName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="难度">
              <el-rate :model-value="drawer.question.difficulty" disabled size="small" />
            </el-descriptions-item>
            <el-descriptions-item label="创建人">{{ drawer.question.createdBy }}</el-descriptions-item>
            <el-descriptions-item label="提交审核时间">{{ fmtTime(drawer.question.submittedAt) }}</el-descriptions-item>
          </el-descriptions>

          <div class="detail-block">
            <div class="detail-label">题干</div>
            <div class="detail-stem">{{ drawer.question.stem }}</div>
          </div>

          <div v-if="hasOptions(drawer.question)" class="detail-block">
            <div class="detail-label">选项</div>
            <div
              v-for="(opt, i) in drawer.question.options"
              :key="i"
              class="detail-option"
              :class="{ 'is-correct': isCorrect(drawer.question, i) }"
            >
              <span class="opt-letter">{{ letterOf(i) }}.</span>
              <span class="opt-text">{{ opt || '（空）' }}</span>
              <el-tag v-if="isCorrect(drawer.question, i)" type="success" size="small" effect="light">正确答案</el-tag>
            </div>
          </div>

          <div class="detail-block">
            <div class="detail-label">答案</div>
            <div class="detail-answer">{{ answerText(drawer.question) }}</div>
          </div>

          <div v-if="drawer.question.analysis" class="detail-block">
            <div class="detail-label">解析</div>
            <div class="detail-analysis">{{ drawer.question.analysis }}</div>
          </div>

          <el-divider content-position="left">审核历史</el-divider>
          <el-timeline v-if="history.length">
            <el-timeline-item
              v-for="h in history"
              :key="h.id"
              :timestamp="fmtTime(h.createdAt)"
              placement="top"
              :type="tagOf(actionMap, h.action)"
              :hollow="true"
            >
              <div class="history-row">
                <el-tag :type="tagOf(actionMap, h.action)" size="small">{{ labelOf(actionMap, h.action) }}</el-tag>
                <span class="history-reviewer">{{ h.reviewerName }}</span>
              </div>
              <div v-if="h.comment" class="history-comment">{{ h.comment }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else-if="!drawer.loading" description="暂无审核记录" :image-size="60" />
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { allKnowledgePoints, approveQuestion, pageQuestions, rejectQuestion, reviewRecordsOfQuestion } from '@/api'
import {
  QUESTION_TYPES,
  actionMap,
  labelOf,
  sourceMap,
  statusMap,
  tagOf,
  typeMap
} from '@/utils/dicts'
import { fmtTime, letterOf, splitLetters } from '@/utils/format'

const query = reactive({ keyword: '', kpId: null, type: '', page: 1, size: 10 })
const list = ref([])
const total = ref(0)
const loading = ref(false)
const kpOptions = ref([])

async function loadKps() {
  try {
    kpOptions.value = (await allKnowledgePoints()) || []
  } catch (e) {
    /* 忽略 */
  }
}

async function load() {
  loading.value = true
  try {
    const res = await pageQuestions({
      status: 'PENDING',
      keyword: query.keyword.trim() || undefined,
      kpId: query.kpId || undefined,
      type: query.type || undefined,
      page: query.page - 1,
      size: query.size
    })
    list.value = res.list || []
    total.value = res.total || 0
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  load()
}

function onReset() {
  Object.assign(query, { keyword: '', kpId: null, type: '', page: 1 })
  load()
}

function onSizeChange() {
  query.page = 1
  load()
}

onMounted(() => {
  loadKps()
  load()
})

// ---------- 审核操作 ----------
const action = reactive({ visible: false, mode: 'approve', row: null, comment: '', submitting: false })

function openAction(mode, row) {
  action.mode = mode
  action.row = row
  action.comment = mode === 'approve' ? '审核通过' : ''
  action.visible = true
}

async function confirmAction() {
  if (action.mode === 'reject' && !action.comment.trim()) {
    ElMessage.warning('驳回时必须填写原因')
    return
  }
  action.submitting = true
  try {
    const id = action.row.id
    if (action.mode === 'approve') {
      await approveQuestion(id, { comment: action.comment.trim() || '审核通过' })
      ElMessage.success(`题目 #${id} 审核通过并已上架`)
    } else {
      await rejectQuestion(id, { comment: action.comment.trim() })
      ElMessage.success(`题目 #${id} 已驳回`)
    }
    action.visible = false
    if (list.value.length === 1 && query.page > 1) query.page -= 1
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    action.submitting = false
  }
}

// ---------- 详情抽屉 ----------
const drawer = reactive({ visible: false, question: null, loading: false })
const history = ref([])

async function openDetail(row) {
  drawer.question = row
  history.value = []
  drawer.visible = true
  drawer.loading = true
  try {
    history.value = (await reviewRecordsOfQuestion(row.id)) || []
  } catch (e) {
    history.value = []
  } finally {
    drawer.loading = false
  }
}

function hasOptions(q) {
  return !!q && (q.type === 'SINGLE' || q.type === 'MULTIPLE') && Array.isArray(q.options)
}
function isCorrect(q, i) {
  if (!hasOptions(q)) return false
  return splitLetters(q.answer).includes(letterOf(i))
}
function answerText(q) {
  if (!q) return '-'
  if (q.type === 'JUDGE') return q.answer === '对' ? '对（正确）' : q.answer === '错' ? '错（错误）' : q.answer || '-'
  return q.answer || '-'
}
</script>

<style scoped>
.mb14 {
  margin-bottom: 14px;
}

.action-stem {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;
  background: #f7f9fc;
  border-radius: 6px;
  padding: 10px 12px;
}

.action-stem-text {
  font-size: 13px;
  color: #303133;
  line-height: 1.7;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  word-break: break-all;
}

.action-form :deep(.el-form-item__label) {
  font-weight: 600;
}

.reject-tip {
  font-size: 12px;
  color: #f56c6c;
}

.drawer-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
}

.detail-wrap {
  min-height: 200px;
}

.sep {
  display: inline-block;
  width: 8px;
}

.detail-block {
  margin-top: 16px;
}

.detail-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
  font-weight: 600;
}

.detail-stem {
  font-size: 15px;
  line-height: 1.8;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
  background: #f7f9fc;
  border-radius: 6px;
  padding: 10px 12px;
}

.detail-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-radius: 6px;
  font-size: 14px;
  line-height: 1.6;
}

.detail-option.is-correct {
  background: #f0f9eb;
}

.opt-letter {
  font-weight: 600;
  color: #606266;
  flex-shrink: 0;
}

.detail-option.is-correct .opt-letter {
  color: #67c23a;
}

.opt-text {
  flex: 1;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-answer {
  font-size: 14px;
  color: #303133;
  font-weight: 600;
  white-space: pre-wrap;
  word-break: break-word;
}

.detail-analysis {
  font-size: 13px;
  color: #606266;
  line-height: 1.8;
  background: #fafafa;
  border-left: 3px solid #e6a23c;
  padding: 8px 12px;
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-word;
}

.history-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.history-reviewer {
  font-size: 13px;
  color: #606266;
}

.history-comment {
  margin-top: 4px;
  font-size: 13px;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
