<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <el-icon :size="34" color="#1677ff"><Reading /></el-icon>
        <div class="brand-title">培训题库管理系统</div>
        <div class="brand-sub">Training Question Bank System</div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="onSubmit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="UserIcon" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="LockIcon"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="onSubmit">
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert type="info" :closable="false" class="hint">
        <template #title>演示账号</template>
        <div class="hint-body">
          <div><b>admin</b> / admin123 —— 系统管理员</div>
          <div><b>generator</b> / gen123456 —— 出题录入员</div>
          <div><b>reviewer</b> / rev123456 —— 审核员</div>
        </div>
      </el-alert>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock as LockIcon, User as UserIcon } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    await userStore.login(form.username.trim(), form.password)
    ElMessage.success(`欢迎回来, ${userStore.displayName}!`)
    router.push(typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard')
  } catch (e) {
    /* 错误提示已由 http 拦截器统一处理 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e8f1ff 0%, #f4f6fb 50%, #edf7ef 100%);
}

.login-card {
  width: 400px;
  background: #fff;
  border-radius: 10px;
  padding: 34px 34px 24px;
  box-shadow: 0 8px 30px rgba(22, 119, 255, 0.12);
}

.brand {
  text-align: center;
  margin-bottom: 22px;
}

.brand-title {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 700;
  color: #1f2d3d;
}

.brand-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  letter-spacing: 1px;
}

.login-btn {
  width: 100%;
}

.hint {
  margin-top: 4px;
}

.hint-body {
  line-height: 1.9;
  font-size: 13px;
}
</style>
