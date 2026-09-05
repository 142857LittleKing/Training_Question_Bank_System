<template>
  <div>
    <div class="page-card">
      <div class="filter-bar">
        <el-input
          v-model="query.keyword"
          placeholder="知识点名称/分类关键词"
          clearable
          style="width: 240px"
          @keyup.enter="onSearch"
          @clear="onSearch"
        />
        <el-button type="primary" @click="onSearch">查 询</el-button>
        <el-button @click="onReset">重 置</el-button>
        <div class="flex-spacer" />
        <el-button v-if="canManage" type="primary" :icon="Plus" @click="openDialog()">
          新增知识点
        </el-button>
      </div>
    </div>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="list" stripe>
        <el-table-column label="名称" width="200">
          <template #default="{ row }">
            <div class="name-cell">
              <el-icon class="name-icon"><Collection /></el-icon>
              <span class="name-text">{{ row.name }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="150">
          <template #default="{ row }">
            <el-tag v-if="row.category" size="small" type="info" effect="plain">{{ row.category }}</el-tag>
            <span v-else class="empty-text">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ 'empty-text': !row.description }">{{ row.description || '暂无描述' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="题目数" width="120" align="center">
          <template #default="{ row }">
            <el-badge :value="row.questionCount" :max="999" type="primary">
              <el-icon :size="20"><Document /></el-icon>
            </el-badge>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" label="创建人" width="120" show-overflow-tooltip />
        <el-table-column label="创建时间" width="168">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="canManage" label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无知识点" :image-size="80" />
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

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.isEdit ? `编辑知识点: ${dialog.form.name}` : '新增知识点'"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="dialog.form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="dialog.form.name" placeholder="请输入知识点名称" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-input v-model="dialog.form.category" placeholder="如: 党史党建 / 业务知识 / 安全法规(选填)" maxlength="50" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="dialog.form.description"
            type="textarea"
            :rows="3"
            placeholder="知识点描述(选填)"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取 消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="onSubmit">确 定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { createKnowledgePoint, deleteKnowledgePoint, pageKnowledgePoints, updateKnowledgePoint } from '@/api'
import { useUserStore } from '@/stores/user'
import { fmtTime } from '@/utils/format'

const userStore = useUserStore()
const canManage = computed(() => ['ADMIN', 'GENERATOR'].includes(userStore.role))

const query = reactive({ keyword: '', page: 1, size: 10 })
const list = ref([])
const total = ref(0)
const loading = ref(false)

const dialog = reactive({
  visible: false,
  isEdit: false,
  saving: false,
  form: { id: null, name: '', category: '', description: '' }
})
const formRef = ref()
const rules = {
  name: [{ required: true, message: '请输入知识点名称', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const res = await pageKnowledgePoints({
      keyword: query.keyword.trim() || undefined,
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
  Object.assign(query, { keyword: '', page: 1 })
  load()
}

function onSizeChange() {
  query.page = 1
  load()
}

onMounted(load)

// ---------- 新增/编辑 ----------
function openDialog(row) {
  dialog.isEdit = !!row
  Object.assign(dialog.form, row
    ? { id: row.id, name: row.name, category: row.category || '', description: row.description || '' }
    : { id: null, name: '', category: '', description: '' })
  dialog.visible = true
}

async function onSubmit() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  dialog.saving = true
  try {
    const data = {
      name: dialog.form.name.trim(),
      category: dialog.form.category.trim() || undefined,
      description: dialog.form.description.trim() || undefined
    }
    if (dialog.isEdit) {
      await updateKnowledgePoint(dialog.form.id, data)
      ElMessage.success('知识点已更新')
    } else {
      await createKnowledgePoint(data)
      ElMessage.success('知识点创建成功')
    }
    dialog.visible = false
    load()
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    dialog.saving = false
  }
}

// ---------- 删除 ----------
async function onDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除知识点「${row.name}」? ${row.questionCount > 0 ? `该知识点下还有 ${row.questionCount} 道题目, 删除会失败或被限制!` : ''}`,
      '删除确认',
      { type: 'warning' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteKnowledgePoint(row.id)
    ElMessage.success('删除成功')
    if (list.value.length === 1 && query.page > 1) query.page -= 1
    load()
  } catch (e) {
    // 服务端冲突(如知识点下仍有题目)的 message 已由拦截器弹出
  }
}
</script>

<style scoped>
.flex-spacer {
  flex: 1;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.name-icon {
  color: #409eff;
  flex-shrink: 0;
}

.name-text {
  font-weight: 500;
  color: #303133;
}

.empty-text {
  color: #c0c4cc;
}
</style>
