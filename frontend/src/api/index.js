import http from './http'

// ---------------- 认证 ----------------
export const login = (data) => http.post('/auth/login', data)
export const getMe = () => http.get('/auth/me')
export const changePassword = (data) => http.put('/auth/password', data)
export const listUsers = () => http.get('/auth/users')

// ---------------- 知识点 ----------------
export const pageKnowledgePoints = (params) => http.get('/knowledge-points', { params })
export const allKnowledgePoints = () => http.get('/knowledge-points/all')
export const createKnowledgePoint = (data) => http.post('/knowledge-points', data)
export const updateKnowledgePoint = (id, data) => http.put(`/knowledge-points/${id}`, data)
export const deleteKnowledgePoint = (id) => http.delete(`/knowledge-points/${id}`)

// ---------------- 题目 ----------------
export const pageQuestions = (params) => http.get('/questions', { params })
export const getQuestion = (id) => http.get(`/questions/${id}`)
export const createQuestion = (data) => http.post('/questions', data)
export const updateQuestion = (id, data) => http.put(`/questions/${id}`, data)
export const deleteQuestion = (id) => http.delete(`/questions/${id}`)
export const submitQuestion = (id) => http.post(`/questions/${id}/submit`)
export const offlineQuestion = (id) => http.post(`/questions/${id}/offline`)

// ---------------- 审核 ----------------
export const approveQuestion = (id, data) => http.post(`/review/${id}/approve`, data)
export const rejectQuestion = (id, data) => http.post(`/review/${id}/reject`, data)
export const pageReviewRecords = (params) => http.get('/review-records', { params })
export const reviewRecordsOfQuestion = (id) => http.get(`/review-records/question/${id}`)

// ---------------- AI 出题 ----------------
export const getAiProvider = () => http.get('/ai/provider')
export const aiGenerate = (data) => http.post('/ai/generate', data)
export const aiSave = (data) => http.post('/ai/save', data)

// ---------------- 批量导入 ----------------
export const downloadTemplate = () =>
  http.get('/import/template', { responseType: 'blob' })
export const uploadImport = (formData) =>
  http.post('/import', formData, { headers: { 'Content-Type': 'multipart/form-data' } })
export const importJsonText = (data) => http.post('/import/json', data)

// ---------------- 检索使用(公开) ----------------
export const quizRandom = (params) => http.get('/quiz/random', { params })
export const quizSearch = (params) => http.get('/quiz/search', { params })

// ---------------- 首页统计 ----------------
export const getDashboardSummary = () => http.get('/dashboard/summary')

/** 文件下载助手(Blob) */
export function saveBlob(blob, filename) {
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.URL.revokeObjectURL(url)
}
