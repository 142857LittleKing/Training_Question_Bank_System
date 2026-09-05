<template>
  <div v-loading="loading" class="dashboard">
    <!-- 统计卡片 -->
    <div class="stat-row">
      <el-card v-for="card in statCards" :key="card.label" shadow="hover" class="stat-card">
        <div class="stat-inner">
          <div class="stat-icon" :style="{ background: card.color + '1a', color: card.color }">
            <el-icon :size="26"><component :is="card.icon" /></el-icon>
          </div>
          <div class="stat-meta">
            <div class="stat-value">{{ card.value }}</div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 三列占比 -->
    <div class="chart-row" v-if="summary">
      <el-card v-for="grp in chartGroups" :key="grp.title" class="chart-card">
        <template #header>
          <div class="chart-title">{{ grp.title }}</div>
        </template>
        <div v-if="grp.list.length" class="bar-list">
          <div v-for="row in grp.list" :key="row.name" class="bar-row">
            <div class="bar-head">
              <span class="bar-label">{{ row.label }}</span>
              <span class="bar-count">{{ row.count }} 道</span>
            </div>
            <el-progress
              :percentage="pct(row.count)"
              :show-text="false"
              :stroke-width="9"
              :color="grp.color"
            />
          </div>
        </div>
        <el-empty v-else description="暂无数据" :image-size="60" />
      </el-card>
    </div>

    <!-- 热门知识点 + 最新题目 -->
    <div class="bottom-row" v-if="summary">
      <el-card class="kp-card">
        <template #header>
          <div class="chart-title">热门知识点 TOP{{ (summary.topKnowledgePoints || []).length }}</div>
        </template>
        <div v-if="summary.topKnowledgePoints && summary.topKnowledgePoints.length" class="kp-tags">
          <div v-for="(kp, i) in summary.topKnowledgePoints" :key="kp.id" class="kp-item">
            <span class="kp-rank" :class="'rank-' + (i + 1)">{{ i + 1 }}</span>
            <span class="kp-name">{{ kp.name }}</span>
            <el-tag size="small" type="primary" effect="plain">{{ kp.count }} 题</el-tag>
          </div>
        </div>
        <el-empty v-else description="暂无题目, 快去录题吧" :image-size="70" />
      </el-card>

      <el-card class="recent-card">
        <template #header>
          <div class="chart-title">最新录入题目</div>
        </template>
        <el-table :data="summary.recent || []" stripe size="default">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column prop="knowledgePointName" label="知识点" width="130" show-overflow-tooltip />
          <el-table-column label="题型" width="100">
            <template #default="{ row }">
              <el-tag :type="tagOf(typeMap, row.type)" size="small">{{ labelOf(typeMap, row.type) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="stem" label="题干" min-width="260" show-overflow-tooltip />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="tagOf(statusMap, row.status)" size="small">{{ labelOf(statusMap, row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="110">
            <template #default="{ row }">
              <el-tag :type="tagOf(sourceMap, row.source)" size="small" effect="plain">{{ labelOf(sourceMap, row.source) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdBy" label="创建人" width="110" show-overflow-tooltip />
          <el-table-column label="时间" width="165">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { getDashboardSummary } from '@/api'
import { sourceMap, statusMap, tagOf, typeMap, labelOf } from '@/utils/dicts'
import { fmtTime } from '@/utils/format'

const loading = ref(false)
const summary = ref(null)

/** el-tag type -> hex, 用于进度条颜色 */
const TAG_COLORS = {
  primary: '#409eff',
  success: '#67c23a',
  warning: '#e6a23c',
  danger: '#f56c6c',
  info: '#909399'
}

async function load() {
  loading.value = true
  try {
    summary.value = await getDashboardSummary()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    loading.value = false
  }
}
onMounted(load)

const statCards = computed(() => {
  const s = summary.value
  if (!s) return []
  const byName = {}
  ;(s.byStatus || []).forEach((x) => (byName[x.name] = x))
  const find = (name) => byName[name]
  const statusCard = (name, color, icon) => {
    const it = find(name)
    return { label: it ? it.label : name, value: it ? it.count : 0, color, icon }
  }
  return [
    { label: '题库总数', value: s.total, color: '#1677ff', icon: 'DataAnalysis' },
    { label: '已上架', value: s.totalPublished, color: '#52c41a', icon: 'CircleCheck' },
    { label: '待审核', value: s.pendingReview, color: '#fa541c', icon: 'AlarmClock' },
    statusCard('DRAFT', '#8c8c8c', 'Document'),
    statusCard('GENERATED', '#faad14', 'MagicStick'),
    statusCard('OFFLINE', '#bfbfbf', 'CircleClose')
  ]
})

const chartGroups = computed(() => {
  const s = summary.value
  if (!s) return []
  const withColor = (list, tagName) =>
    (list || []).map((x) => ({ ...x, color: TAG_COLORS[tagName] || '#409eff' }))
  return [
    { title: '按状态分布', list: withColor(s.byStatus, 'status'), color: '#409eff' },
    { title: '按题型分布', list: withColor(s.byType, 'type'), color: '#67c23a' },
    { title: '按来源分布', list: withColor(s.bySource, 'source'), color: '#e6a23c' }
  ]
})

function pct(count) {
  const total = summary.value?.total || 0
  if (!total) return 0
  return Math.round((count / total) * 100)
}
</script>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 100%;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 12px;
}

.stat-card :deep(.el-card__body) {
  padding: 14px 16px;
}

.stat-inner {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  color: #1f2d3d;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

.chart-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 12px;
}

.chart-title {
  font-weight: 600;
  font-size: 15px;
}

.bar-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.bar-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 3px;
}

.bar-label {
  font-size: 13px;
  color: #303133;
}

.bar-count {
  font-size: 12px;
  color: #909399;
}

.bottom-row {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 12px;
  align-items: start;
}

.kp-tags {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.kp-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.kp-rank {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #f0f2f5;
  color: #606266;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.kp-rank.rank-1 {
  background: #ffd666;
  color: #ad6800;
}

.kp-rank.rank-2 {
  background: #d9d9d9;
  color: #595959;
}

.kp-rank.rank-3 {
  background: #ffb38a;
  color: #873800;
}

.kp-name {
  flex: 1;
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 1100px) {
  .bottom-row {
    grid-template-columns: 1fr;
  }
}
</style>
