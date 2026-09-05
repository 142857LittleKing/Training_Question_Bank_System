<template>
  <div>
    <div class="page-card">
      <div class="filter-bar">
        <el-select v-model="query.action" placeholder="审核动作" clearable style="width: 160px">
          <el-option v-for="a in REVIEW_ACTIONS" :key="a.value" :label="a.label" :value="a.value" />
        </el-select>
        <el-input
          v-model="query.reviewerName"
          placeholder="审核人"
          clearable
          style="width: 180px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button type="primary" @click="onSearch">查 询</el-button>
        <el-button @click="onReset">重 置</el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column prop="id" label="记录ID" width="90" />
        <el-table-column prop="stemSnapshot" label="题干快照" min-width="320" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="snap-text">#{{ row.questionId }}&nbsp; {{ row.stemSnapshot }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reviewerName" label="审核人" width="130" show-overflow-tooltip />
        <el-table-column label="动作" width="120">
          <template #default="{ row }">
            <el-tag :type="tagOf(actionMap, row.action)" size="small">{{ labelOf(actionMap, row.action) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核意见" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'comment-empty': !row.comment }">{{ row.comment || '（无意见）' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="168">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无审核记录" :image-size="80" />
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { pageReviewRecords } from '@/api'
import { REVIEW_ACTIONS, actionMap, labelOf, tagOf } from '@/utils/dicts'
import { fmtTime } from '@/utils/format'

const query = reactive({ action: '', reviewerName: '', page: 1, size: 10 })
const list = ref([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await pageReviewRecords({
      action: query.action || undefined,
      reviewerName: query.reviewerName.trim() || undefined,
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
  Object.assign(query, { action: '', reviewerName: '', page: 1 })
  load()
}

function onSizeChange() {
  query.page = 1
  load()
}

onMounted(load)
</script>

<style scoped>
.snap-text {
  color: #303133;
}

.comment-empty {
  color: #c0c4cc;
}
</style>
