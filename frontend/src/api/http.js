import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import router from '@/router'

const http = axios.create({
  baseURL: '/api',
  timeout: 300000 // AI 出题可能较慢
})

http.interceptors.request.use((config) => {
  const store = useUserStore()
  if (store.token) {
    config.headers.Authorization = `Bearer ${store.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res.code !== 'undefined') {
      if (res.code === 0) {
        return res.data
      }
      ElMessage.error(res.message || '请求失败')
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message
    if (status === 401) {
      const store = useUserStore()
      store.logout()
      if (router.currentRoute.value.path !== '/login') {
        ElMessage.warning(message || '登录已过期, 请重新登录')
        router.push('/login')
      }
    } else if (status === 403) {
      ElMessage.error(message || '没有操作权限')
    } else if (status === 404) {
      ElMessage.error(message || '资源不存在')
    } else {
      ElMessage.error(message || (error.message === 'Network Error' ? '无法连接服务器' : '请求失败'))
    }
    return Promise.reject(error)
  }
)

export default http
