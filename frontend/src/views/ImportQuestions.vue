<template>
  <div>
    <el-tabs v-model="activeTab">
      <!-- 文件导入 -->
      <el-tab-pane label="文件导入" name="file">
        <el-alert type="info" :closable="false" show-icon class="mb14">
          <template #title>批量导入（支持任意更换题库数据）</template>
          <div class="alert-body">
            按模板整理题目后上传即可, 系统不存在的知识点会自动创建；判断题/简答题留空选项列。
            下载模板请点下方链接, 也可直接使用 <code>data/templates/样例导入.json</code>、
            <code>党建知识竞赛题库解析结果 data/seed/dangshi_150.json</code> 上传导入。
          </div>
        </el-alert>

        <div class="import-settings">
          <div class="setting-item">
            <span class="setting-label">导入后状态:</span>
            <el-radio-group v-model="fileForm.defaultStatus">
              <el-radio-button value="PUBLISHED">直接上架</el-radio-button>
              <el-radio-button value="PENDING">待审核</el-radio-button>
              <el-radio-button value="DRAFT">草稿</el-radio-button>
            </el-radio-group>
          </div>
          <div class="setting-item">
            <span class="setting-label">覆盖知识点(可选):</span>
            <el-input
              v-model="fileForm.kpName"
              placeholder="填写后将把所有行的知识点统一覆盖为该名称"
              clearable
              style="width: 320px"
            />
          </div>
        </div>

        <el-upload
          ref="uploadRef"
          drag
          :limit="1"
          accept=".xlsx,.xls,.json"
          :http-request="doUpload"
          :show-file-list="true"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或 <em>点击选择文件</em></div>
          <template #tip>
            <div class="el-upload__tip">仅支持 .xlsx / .xls / .json，单次最多 5000 行</div>
          </template>
        </el-upload>

        <div class="tpl-line">
          <el-button :icon="Download" @click="downloadTpl">下载导入模板（xlsx）</el-button>
          <span class="tpl-tip">模板内含填写说明与示例数据</span>
        </div>
      </el-tab-pane>

      <!-- JSON 粘贴导入 -->
      <el-tab-pane label="JSON 粘贴导入" name="json">
        <el-alert type="info" :closable="false" show-icon class="mb14">
          <template #title>直接粘贴 JSON 数组导入</template>
          <div class="alert-body">
            格式: [{&quot;knowledgePoint&quot;: &quot;知识点&quot;, &quot;type&quot;: &quot;SINGLE/MULTIPLE/JUDGE/SHORT&quot;,
            &quot;stem&quot;: &quot;题干&quot;, &quot;options&quot;: [&quot;A&quot;,&quot;B&quot;], &quot;answer&quot;: &quot;A&quot;,
            &quot;analysis&quot;: &quot;解析&quot;, &quot;difficulty&quot;: 3}] —— 题型同时兼容中文别名(单选/多选/判断/简答)。
          </div>
        </el-alert>

        <div class="import-settings">
          <div class="setting-item">
            <span class="setting-label">导入后状态:</span>
            <el-radio-group v-model="jsonForm.defaultStatus">
              <el-radio-button value="PUBLISHED">直接上架</el-radio-button>
              <el-radio-button value="PENDING">待审核</el-radio-button>
              <el-radio-button value="DRAFT">草稿</el-radio-button>
            </el-radio-group>
          </div>
        </div>

        <el-input
          v-model="jsonForm.content"
          type="textarea"
          :rows="12"
          placeholder='粘贴 JSON 数组, 例如: [{"knowledgePoint":"综合示例","type":"SINGLE","stem":"...","options":["..."],"answer":"A","analysis":"...","difficulty":3}]'
        />
        <div class="json-actions">
          <el-button :icon="MagicStick" @click="fillSample">填入样例</el-button>
          <el-button type="primary" :loading="jsonForm.loading" @click="doJsonImport">
            开始导入
          </el-button>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 导入结果 -->
    <el-card v-if="result" shadow="never" class="result-card">
      <template #header>
        <div class="result-head">
          <span class="result-title">导入结果: {{ result.fileName }}</span>
          <div class="result-nums">
            <span>共 <b>{{ result.totalRows }}</b> 行</span>
            <span class="ok-num">成功 <b>{{ result.success }}</b></span>
            <span class="bad-num">失败 <b>{{ result.failed }}</b></span>
            <el-tag size="small" effect="plain" class="status-tag">
              导入状态: {{ statusLabel(result.defaultStatus) }}
            </el-tag>
          </div>
        </div>
      </template>

      <el-table :data="result.rows || []" size="small" max-height="420" stripe>
        <el-table-column prop="rowNo" label="行号" width="80" />
        <el-table-column label="是否成功" width="90">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="原因/说明" min-width="260" show-overflow-tooltip />
        <el-table-column label="题目ID" width="100">
          <template #default="{ row }">
            <el-link v-if="row.questionId" type="primary" :underline="false" @click="openQuestion(row.questionId)">
              #{{ row.questionId }}
            </el-link>
            <span v-else class="empty-text">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download, MagicStick, UploadFilled } from '@element-plus/icons-vue'
import { downloadTemplate, importJsonText, saveBlob, uploadImport } from '@/api'
import { QUESTION_STATUS } from '@/utils/dicts'

const router = useRouter()
const activeTab = ref('file')
const uploadRef = ref()
const result = ref(null)

const fileForm = reactive({ defaultStatus: 'PUBLISHED', kpName: '' })
const jsonForm = reactive({ defaultStatus: 'PUBLISHED', content: '', loading: false })

const STATUS_LABELS = {}
QUESTION_STATUS.forEach((s) => (STATUS_LABELS[s.value] = s.label))
function statusLabel(v) {
  return STATUS_LABELS[v] || v || '-'
}

onMounted(() => {
  result.value = null
})

// ---------- 文件上传 ----------
async function doUpload(options) {
  const fd = new FormData()
  fd.append('file', options.file)
  fd.append('defaultStatus', fileForm.defaultStatus)
  if (fileForm.kpName.trim()) fd.append('kpName', fileForm.kpName.trim())
  try {
    const res = await uploadImport(fd)
    result.value = res
    ElMessage.success(`导入完成: 成功 ${res.success} 条, 失败 ${res.failed} 条`)
    options.onSuccess(res)
  } catch (e) {
    options.onError(e)
  } finally {
    uploadRef.value && uploadRef.value.clearFiles()
  }
}

async function downloadTpl() {
  try {
    const blob = await downloadTemplate()
    saveBlob(blob, '题库导入模板.xlsx')
    ElMessage.success('模板已开始下载')
  } catch (e) {
    /* 拦截器已提示 */
  }
}

// ---------- JSON 导入 ----------
const SAMPLE_JSON = [
  {
    knowledgePoint: '综合示例',
    type: 'SINGLE',
    stem: '党的根本宗旨是（  ）。',
    options: ['实现共产主义', '全心全意为人民服务', '发展生产力', '共同富裕'],
    answer: 'B',
    analysis: '全心全意为人民服务是党的根本宗旨。',
    difficulty: 1
  },
  {
    knowledgePoint: '综合示例',
    type: 'MULTIPLE',
    stem: '以下属于"四个全面"战略布局内容的有（  ）。',
    options: ['全面建成小康社会', '全面深化改革', '全面依法治国', '全面从严治党'],
    answer: 'A,B,C,D',
    analysis: '四个全面: 全面建设社会主义现代化国家、全面深化改革、全面依法治国、全面从严治党。',
    difficulty: 3
  },
  {
    knowledgePoint: '综合示例',
    type: 'JUDGE',
    stem: '中国共产党成立于 1921 年 7 月。',
    options: [],
    answer: '对',
    analysis: '1921 年 7 月中共一大召开, 标志中国共产党成立。',
    difficulty: 1
  },
  {
    knowledgePoint: '综合示例',
    type: 'SHORT',
    stem: '简述遵义会议的历史意义。',
    options: [],
    answer: '确立了毛泽东在党和红军中的领导地位, 是党的历史上生死攸关的转折点。',
    analysis: '评分要点: ①确立毛泽东领导地位; ②党的历史上生死攸关的转折点; ③标志着党在政治上走向成熟。',
    difficulty: 4
  }
]

function fillSample() {
  jsonForm.content = JSON.stringify(SAMPLE_JSON, null, 2)
  ElMessage.success('已填入四类题型(单选/多选/判断/简答)的样例数据')
}

async function doJsonImport() {
  if (!jsonForm.content.trim()) {
    ElMessage.warning('请先粘贴 JSON 内容')
    return
  }
  jsonForm.loading = true
  try {
    const res = await importJsonText({
      content: jsonForm.content,
      defaultStatus: jsonForm.defaultStatus
    })
    result.value = res
    ElMessage.success(`导入完成: 成功 ${res.success} 条, 失败 ${res.failed} 条`)
  } catch (e) {
    /* 拦截器已提示 */
  } finally {
    jsonForm.loading = false
  }
}

// ---------- 跳转到题目详情/列表 ----------
function openQuestion(id) {
  router.push('/questions')
}
</script>

<style scoped>
.mb14 {
  margin-bottom: 14px;
}

.alert-body {
  font-size: 13px;
  line-height: 1.9;
}

.alert-body code {
  background: #f0f2f5;
  padding: 1px 6px;
  border-radius: 4px;
  color: #476582;
}

.import-settings {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 18px;
  margin-bottom: 14px;
  background: #fafbfc;
  border-radius: 6px;
  padding: 10px 14px;
}

.setting-item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.setting-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}

.tpl-line {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.tpl-tip {
  font-size: 12px;
  color: #909399;
}

.json-actions {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}

.result-card {
  margin-top: 16px;
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 10px;
}

.result-title {
  font-weight: 600;
}

.result-nums {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 13px;
  color: #606266;
}

.ok-num b {
  color: #67c23a;
  font-size: 15px;
}

.bad-num b {
  color: #f56c6c;
  font-size: 15px;
}

.empty-text {
  color: #c0c4cc;
}
</style>
