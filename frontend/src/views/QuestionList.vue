<template>
  <div>
    <!-- 筛选栏 -->
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
        <el-select v-model="query.kpId" placeholder="知识点" clearable filterable style="width: 170px">
          <el-option v-for="kp in kpOptions" :key="kp.id" :label="kp.name" :value="kp.id" />
        </el-select>
        <el-select v-model="query.type" placeholder="题型" clearable style="width: 130px">
          <el-option v-for="t in QUESTION_TYPES" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 150px">
          <el-option v-for="s in QUESTION_STATUS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-select v-model="query.source" placeholder="来源" clearable style="width: 140px">
          <el-option v-for="s in QUESTION_SOURCES" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-checkbox v-if="canManage" v-model="query.mine" border>只看我录入的</el-checkbox>
        <el-button type="primary" @click="onSearch">查 询</el-button>
        <el-button @click="onReset">重 置</el-button>
        <div class="flex-spacer" />
        <el-button v-if="canManage" type="primary" :icon="Plus" @click="$router.push('/questions/edit')">
          ＋ 录入题目
        </el-button>
      </div>
    </div>

    <!-- 表格 -->
    <el-card shadow="never">
      <!-- 批量操作工具栏 -->
      <div v-if="canManage" class="table-toolbar">
        <el-button
          type="danger"
          plain
          :icon="Delete"
          :disabled="!selection.length"
          @click="onBatchDelete"
        >
          批量删除<template v-if="selection.length">（{{ selection.length }}）</template>
        </el-button>
        <span class="batch-tip">
          支持跨页勾选: 管理员可删任意题目; 出题员仅可删本人录入且非“已上架/待审核”的题目(逐条校验, 审核记录保留作审计)
        </span>
      </div>
      <el-table
        v-loading="loading"
        ref="tableRef"
        :data="list"
        stripe
        @selection-change="onSelectionChange"
      >
        <el-table-column v-if="canManage" type="selection" width="46" />
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="knowledgePointName" label="知识点" width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="kp-cell">
              <div class="kp-name">{{ row.knowledgePointName || '-' }}</div>
              <div v-if="row.knowledgePointCategory" class="kp-cat">{{ row.knowledgePointCategory }}</div>
            </div>
          </template>
        </el-table-column>
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
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="tagOf(statusMap, row.status)" size="small">{{ labelOf(statusMap, row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="创建人" width="110" show-overflow-tooltip />
        <el-table-column label="更新时间" width="168">
          <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" :width="opsWidth" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button v-if="canEdit(row)" link type="primary" @click="$router.push(`/questions/edit/${row.id}`)">编辑</el-button>
            <el-button v-if="canSubmit(row)" link type="warning" @click="onSubmitReview(row)">提交审核</el-button>
            <el-button v-if="canOffline(row)" link type="warning" @click="onOffline(row)">下架</el-button>
            <el-button v-if="canDelete(row)" link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无题目数据" :image-size="80" />
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

    <!-- 题目详情抽屉 -->
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
            <el-descriptions-item label="更新时间">{{ fmtTime(drawer.question.updatedAt) }}</el-descriptions-item>
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

          <div
            v-if="drawer.question.reviewerName || drawer.question.reviewComment"
            class="detail-block"
          >
            <div class="detail-label">最近审核记录</div>
            <div class="detail-meta">
              <span v-if="drawer.question.reviewerName">审核人: {{ drawer.question.reviewerName }}</span>
              <span v-if="drawer.question.reviewComment">意见: {{ drawer.question.reviewComment }}</span>
            </div>
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
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import {
  allKnowledgePoints,
  batchDeleteQuestions,
  deleteQuestion,
  offlineQuestion,
  pageQuestions,
  reviewRecordsOfQuestion,
  submitQuestion
} from '@/api'
import { useUserStore } from '@/stores/user'
import {
  QUESTION_SOURCES,
  QUESTION_STATUS,
  QUESTION_TYPES,
  actionMap,
  labelOf,
  sourceMap,
  statusMap,
  tagOf,
  typeMap
} from '@/utils/dicts'
import { fmtTime, letterOf, splitLetters } from '@/utils/format'

const userStore = useUserStore()

// ---------- 权限/状态判断 ----------
const role = computed(() => userStore.role)
const username = computed(() => userStore.user?.username || '')
const canManage = computed(() => ['ADMIN', 'GENERATOR'].includes(role.value))
const EDITABLE_STATUS = ['DRAFT', 'GENERATED', 'REJECTED', 'OFFLINE']

const opsWidth = computed(() => (canManage.value ? 320 : 90))

function canEdit(row) {
  return canManage.value && EDITABLE_STATUS.includes(row.status)
}
function canSubmit(row) {
  return canManage.value && EDITABLE_STATUS.includes(row.status)
}
function canOffline(row) {
  return ['ADMIN', 'REVIEWER'].includes(role.value) && row.status === 'PUBLISHED'
}
function canDelete(row) {
  if (role.value === 'ADMIN') return true
  if (role.value === 'GENERATOR' && row.createdBy === username.value) {
    return !['PUBLISHED', 'PENDING'].includes(row.status)
  }
  return false
}

// ---------- 列表 ----------
const query = reactive({ keyword: '', kpId: null, type: '', status: '', source: '', mine: false, page: 1, size: 10 })
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
    const params = {
      keyword: query.keyword.trim() || undefined,
      kpId: query.kpId || undefined,
      type: query.type || undefined,
      status: query.status || undefined,
      source: query.source || undefined,
      page: query.page - 1,
      size: query.size
    }
    if (canManage.value && query.mine) params.mine = true
    const res = await pageQuestions(params)
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
  Object.assign(query, { keyword: '', kpId: null, type: '', status: '', source: '', mine: false, page: 1 })
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

// ---------- 批量选择/批量删除 ----------
const selection = ref([])
const tableRef = ref()

function onSelectionChange(rows) {
  selection.value = rows
}

async function onBatchDelete() {
  const ids = selection.value.map((r) => r.id)
  if (!ids.length) return
  try {
    await ElMessageBox.confirm(
      `确认批量删除选中的 ${ids.length} 道题目?\n删除后不可恢复(审核记录仍保留作审计)。`,
      '批量删除确认',
      { type: 'error', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' }
    )
  } catch (e) {
    return
  }
  try {
    const res = await batchDeleteQuestions(ids)
    if (res.deleted > 0) {
      ElMessage.success(`已删除 ${res.deleted}/${res.requested} 道题目`)
    }
    const failed = (res.items || []).filter((i) => !i.deleted)
    if (failed.length) {
      const reasons = [...new Set(failed.map((i) => i.reason))]
      ElMessage.warning(
        `${failed.length} 道未能删除: ${reasons.slice(0, 3).join('；')}${reasons.length > 3 ? ' 等' : ''}`
      )
    }
    tableRef.value?.clearSelection()
    if (list.value.length === ids.length && query.page > 1) query.page -= 1
    load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

// ---------- 行操作 ----------
async function onSubmitReview(row) {
  try {
    await ElMessageBox.confirm(`确认将题目 #${row.id} 提交审核?`, '提交审核', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await submitQuestion(row.id)
    ElMessage.success('已提交审核, 等待审核员处理')
    load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function onOffline(row) {
  try {
    await ElMessageBox.confirm(`确认将题目 #${row.id} 下架? 下架后前端抽题不再包含该题。`, '下架确认', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await offlineQuestion(row.id)
    ElMessage.success('已下架')
    load()
  } catch (e) {
    /* 拦截器已提示 */
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除题目 #${row.id}? 删除后不可恢复(审核记录仍保留作审计)。`,
      '删除确认',
      { type: 'error', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteQuestion(row.id)
    ElMessage.success('删除成功')
    if (list.value.length === 1 && query.page > 1) query.page -= 1
    load()
  } catch (e) {
    /* 拦截器已提示(如无权限/状态不允许) */
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
.flex-spacer {
  flex: 1;
}

.table-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.batch-tip {
  font-size: 12px;
  color: #909399;
}

.kp-cell {
  display: flex;
  flex-direction: column;
  line-height: 1.3;
}

.kp-cat {
  font-size: 12px;
  color: #909399;
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
  color: #1f2d3d;
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

.detail-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: #606266;
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
