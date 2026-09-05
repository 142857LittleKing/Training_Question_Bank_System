import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

/** 角色值: ADMIN 系统管理员, GENERATOR 出题录入员, REVIEWER 审核员 */
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: () => import('@/layout/MainLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '数据看板', icon: 'DataAnalysis', roles: ['ADMIN', 'GENERATOR', 'REVIEWER'] }
      },
      {
        path: 'questions',
        name: 'QuestionList',
        component: () => import('@/views/QuestionList.vue'),
        meta: { title: '题库管理', icon: 'Collection', roles: ['ADMIN', 'GENERATOR', 'REVIEWER'] }
      },
      {
        path: 'questions/edit/:id?',
        name: 'QuestionEdit',
        component: () => import('@/views/QuestionEdit.vue'),
        meta: { title: '题目录入', icon: 'EditPen', roles: ['ADMIN', 'GENERATOR'], hidden: true }
      },
      {
        path: 'ai-generate',
        name: 'AiGenerate',
        component: () => import('@/views/AiGenerate.vue'),
        meta: { title: '智能出题', icon: 'MagicStick', roles: ['ADMIN', 'GENERATOR'] }
      },
      {
        path: 'review-center',
        name: 'ReviewCenter',
        component: () => import('@/views/ReviewCenter.vue'),
        meta: { title: '审核中心', icon: 'Checked', roles: ['ADMIN', 'REVIEWER'] }
      },
      {
        path: 'review-records',
        name: 'ReviewRecords',
        component: () => import('@/views/ReviewRecords.vue'),
        meta: { title: '审核记录', icon: 'Tickets', roles: ['ADMIN', 'GENERATOR', 'REVIEWER'] }
      },
      {
        path: 'knowledge-points',
        name: 'KnowledgePoints',
        component: () => import('@/views/KnowledgePoints.vue'),
        meta: { title: '知识点管理', icon: 'Files', roles: ['ADMIN', 'GENERATOR', 'REVIEWER'] }
      },
      {
        path: 'import',
        name: 'ImportQuestions',
        component: () => import('@/views/ImportQuestions.vue'),
        meta: { title: '批量导入', icon: 'Upload', roles: ['ADMIN', 'GENERATOR'] }
      },
      {
        path: 'quiz',
        name: 'QuizUsage',
        component: () => import('@/views/QuizUsage.vue'),
        meta: { title: '抽题检索使用', icon: 'Search', roles: ['ADMIN', 'GENERATOR', 'REVIEWER'] }
      },
      {
        path: 'profile',
        name: 'Profile',
        component: () => import('@/views/Profile.vue'),
        meta: { title: '个人中心', icon: 'User', roles: ['ADMIN', 'GENERATOR', 'REVIEWER'], hidden: true }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const store = useUserStore()
  if (to.meta.public) {
    return true
  }
  if (!store.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.roles && !to.meta.roles.includes(store.role)) {
    return { path: '/dashboard' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 培训题库管理系统` : '培训题库管理系统'
})

export default router
