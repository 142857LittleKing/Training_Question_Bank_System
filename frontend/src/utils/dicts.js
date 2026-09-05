/** 枚举字典 + 标签/颜色映射(与后端保持一致) */

export const QUESTION_TYPES = [
  { value: 'SINGLE', label: '单选题', tag: 'primary' },
  { value: 'MULTIPLE', label: '多选题', tag: 'warning' },
  { value: 'JUDGE', label: '判断题', tag: 'success' },
  { value: 'SHORT', label: '简答题', tag: 'danger' }
]

export const QUESTION_STATUS = [
  { value: 'DRAFT', label: '草稿', tag: 'info' },
  { value: 'GENERATED', label: 'AI生成待处理', tag: 'warning' },
  { value: 'PENDING', label: '待审核', tag: 'danger' },
  { value: 'PUBLISHED', label: '已上架', tag: 'success' },
  { value: 'REJECTED', label: '已驳回', tag: 'danger' },
  { value: 'OFFLINE', label: '已下架', tag: 'info' }
]

export const QUESTION_SOURCES = [
  { value: 'MANUAL', label: '手工录入', tag: 'primary' },
  { value: 'AI', label: '大模型生成', tag: 'warning' },
  { value: 'IMPORT', label: '批量导入', tag: 'success' }
]

export const REVIEW_ACTIONS = [
  { value: 'PASS', label: '审核通过', tag: 'success' },
  { value: 'REJECT', label: '审核驳回', tag: 'danger' },
  { value: 'IMPORT_PASS', label: '导入自动通过', tag: 'info' }
]

export const ROLES = [
  { value: 'ADMIN', label: '系统管理员' },
  { value: 'GENERATOR', label: '出题录入员' },
  { value: 'REVIEWER', label: '审核员' }
]

export function dictMap(dict) {
  const map = {}
  dict.forEach((d) => (map[d.value] = d))
  return map
}

export const typeMap = dictMap(QUESTION_TYPES)
export const statusMap = dictMap(QUESTION_STATUS)
export const sourceMap = dictMap(QUESTION_SOURCES)
export const actionMap = dictMap(REVIEW_ACTIONS)
export const roleMap = dictMap(ROLES)

export function labelOf(map, value, fallback = value ?? '-') {
  return map[value] ? map[value].label : fallback
}

export function tagOf(map, value) {
  return map[value] ? map[value].tag : 'info'
}
