<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '64px' : '210px'" class="layout-aside">
      <div class="logo">
        <el-icon :size="24"><Reading /></el-icon>
        <span v-if="!collapsed" class="logo-text">培训题库系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        background-color="#001529"
        text-color="#a6adb4"
        active-text-color="#ffffff"
      >
        <el-menu-item
          v-for="item in menus"
          :key="item.path"
          :index="item.path"
        >
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="collapsed = !collapsed">
            <Expand v-if="collapsed" />
            <Fold v-else />
          </el-icon>
          <span class="page-title">{{ currentTitle }}</span>
        </div>
        <div class="header-right">
          <el-badge v-if="userStore.role !== 'GENERATOR' && pendingCount > 0" :value="pendingCount" :max="99">
            <el-button text @click="$router.push('/review-center')">
              <el-icon><Bell /></el-icon> 待审核
            </el-button>
          </el-badge>
          <el-dropdown @command="onCommand">
            <span class="user-entry">
              <el-avatar :size="30" style="background:#1677ff">
                {{ (userStore.displayName || '?').slice(0, 1) }}
              </el-avatar>
              <span class="user-name">{{ userStore.displayName }} ({{ userStore.roleLabel }})</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心 / 修改密码</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getDashboardSummary } from '@/api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collapsed = ref(false)
const pendingCount = ref(0)

const allMenus = [
  { path: '/dashboard', title: '数据看板', icon: 'DataAnalysis', roles: ['ADMIN', 'GENERATOR', 'REVIEWER'] },
  { path: '/questions', title: '题库管理', icon: 'Collection', roles: ['ADMIN', 'GENERATOR', 'REVIEWER'] },
  { path: '/questions/edit', title: '题目录入', icon: 'EditPen', roles: ['ADMIN', 'GENERATOR'] },
  { path: '/ai-generate', title: '智能出题', icon: 'MagicStick', roles: ['ADMIN', 'GENERATOR'] },
  { path: '/review-center', title: '审核中心', icon: 'Checked', roles: ['ADMIN', 'REVIEWER'] },
  { path: '/review-records', title: '审核记录', icon: 'Tickets', roles: ['ADMIN', 'GENERATOR', 'REVIEWER'] },
  { path: '/knowledge-points', title: '知识点管理', icon: 'Files', roles: ['ADMIN', 'GENERATOR', 'REVIEWER'] },
  { path: '/import', title: '批量导入', icon: 'Upload', roles: ['ADMIN', 'GENERATOR'] },
  { path: '/quiz', title: '抽题·检索使用', icon: 'Search', roles: ['ADMIN', 'GENERATOR', 'REVIEWER'] }
]

const menus = computed(() => allMenus.filter((m) => m.roles.includes(userStore.role)))

const activeMenu = computed(() => {
  if (route.path.startsWith('/questions/edit')) return '/questions/edit'
  return route.path
})

const currentTitle = computed(() => route.meta.title || '')

async function loadPending() {
  try {
    const s = await getDashboardSummary()
    pendingCount.value = s.pendingReview || 0
  } catch (e) {
    /* 忽略 */
  }
}

function onCommand(cmd) {
  if (cmd === 'logout') {
    userStore.logout()
    router.push('/login')
  } else if (cmd === 'profile') {
    router.push('/profile')
  }
}

onMounted(loadPending)
</script>

<style scoped>
.layout {
  height: 100%;
}

.layout-aside {
  background: #001529;
  transition: width 0.2s;
  overflow-x: hidden;
}

.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #fff;
  font-weight: 600;
  font-size: 16px;
  white-space: nowrap;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.layout-aside :deep(.el-menu) {
  border-right: none;
}

.layout-header {
  height: 56px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
  padding: 0 16px;
  z-index: 5;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: #595959;
}

.page-title {
  font-size: 16px;
  font-weight: 600;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #333;
  outline: none;
}

.user-name {
  font-size: 14px;
}

.layout-main {
  padding: 16px;
  overflow: auto;
}
</style>
