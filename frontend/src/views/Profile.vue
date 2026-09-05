<template>
  <div class="profile-wrap">
    <!-- 用户信息 -->
    <el-card shadow="never" class="info-card">
      <template #header>
        <div class="card-title">个人信息</div>
      </template>
      <div class="user-info">
        <el-avatar :size="72" class="avatar">{{ (userStore.displayName || '?').slice(0, 1) }}</el-avatar>
        <div class="info-main">
          <div class="name-line">
            <span class="display-name">{{ userStore.displayName }}</span>
            <el-tag :type="roleTag" size="small">{{ userStore.roleLabel }}</el-tag>
          </div>
          <div class="meta-line">
            <span class="meta-item">用户名: <b>{{ userStore.user?.username }}</b></span>
            <el-divider direction="vertical" />
            <span class="meta-item">角色: <b>{{ userStore.role }}</b></span>
          </div>
        </div>
      </div>
    </el-card>

    <!-- 修改密码 -->
    <el-card shadow="never" class="pwd-card">
      <template #header>
        <div class="card-title">修改密码</div>
      </template>
      <el-form
        ref="pwdFormRef"
        :model="pwdForm"
        :rules="pwdRules"
        label-width="90px"
        class="pwd-form"
      >
        <el-form-item label="原密码" prop="oldPassword">
          <el-input
            v-model="pwdForm.oldPassword"
            type="password"
            show-password
            placeholder="请输入原密码"
            style="width: 340px"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            show-password
            placeholder="请输入新密码(至少6位)"
            style="width: 340px"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="pwdForm.confirmPassword"
            type="password"
            show-password
            placeholder="请再次输入新密码"
            style="width: 340px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="onChangePwd">确认修改</el-button>
          <el-button @click="onResetPwd">重 置</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/api'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const roleTag = computed(() => {
  const map = { ADMIN: 'danger', GENERATOR: 'primary', REVIEWER: 'warning' }
  return map[userStore.role] || 'info'
})

const pwdFormRef = ref()
const submitting = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const validateConfirm = (rule, value, callback) => {
  if (value !== pwdForm.newPassword) callback(new Error('两次输入的新密码不一致'))
  else callback()
}

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '新密码至少 6 位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}

async function onChangePwd() {
  try {
    await pwdFormRef.value.validate()
  } catch (e) {
    return
  }
  submitting.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码修改成功, 下次登录请使用新密码')
    pwdFormRef.value.resetFields()
  } catch (e) {
    /* 拦截器已提示(如原密码错误) */
  } finally {
    submitting.value = false
  }
}

function onResetPwd() {
  pwdFormRef.value.resetFields()
}
</script>

<style scoped>
.profile-wrap {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 760px;
}

.card-title {
  font-weight: 600;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 20px;
}

.avatar {
  background: #1677ff;
  font-size: 28px;
  flex-shrink: 0;
}

.name-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.display-name {
  font-size: 20px;
  font-weight: 700;
  color: #1f2d3d;
}

.meta-line {
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #606266;
}

.meta-item b {
  color: #303133;
}
</style>
