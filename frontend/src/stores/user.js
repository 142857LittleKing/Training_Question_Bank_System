import { defineStore } from 'pinia'
import { login as loginApi, getMe } from '@/api'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('tqb_token') || '',
    user: JSON.parse(localStorage.getItem('tqb_user') || 'null')
  }),
  getters: {
    role: (s) => s.user?.role || '',
    roleLabel: (s) => s.user?.roleLabel || '',
    displayName: (s) => s.user?.displayName || s.user?.username || ''
  },
  actions: {
    async login(username, password) {
      const data = await loginApi({ username, password })
      this.token = data.token
      this.user = {
        id: data.id,
        username: data.username,
        displayName: data.displayName,
        role: data.role,
        roleLabel: data.roleLabel
      }
      localStorage.setItem('tqb_token', this.token)
      localStorage.setItem('tqb_user', JSON.stringify(this.user))
      return data
    },
    async refreshMe() {
      try {
        this.user = await getMe()
        localStorage.setItem('tqb_user', JSON.stringify(this.user))
      } catch (e) {
        /* 忽略 */
      }
    },
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('tqb_token')
      localStorage.removeItem('tqb_user')
    }
  }
})
