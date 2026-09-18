<template>
  <div class="auth-page">
    <div class="auth-card card">
      <h2>登录</h2>
      <form @submit.prevent="handleLogin">
        <div class="form-group">
          <label>手机号</label>
          <input v-model="form.phone" type="tel" required placeholder="请输入手机号" maxlength="11" />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="form.password" type="password" required placeholder="请输入密码" />
        </div>
        <p v-if="error" class="error-text">{{ error }}</p>
        <button type="submit" class="btn btn-primary" style="width:100%">登录</button>
      </form>
      <p class="auth-link">没有账号？<router-link to="/register">立即注册</router-link></p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { login } from '../api/user'
import { useAuthStore } from '../store'
const router = useRouter()
const authStore = useAuthStore()
const error = ref('')
const form = reactive({ phone: '', password: '' })

async function handleLogin() {
  try {
    error.value = ''
    const res: any = await login(form)
    authStore.setLogin(res.data)
    router.push('/rooms')
  } catch (e: any) {
    error.value = e?.message || '登录失败'
  }
}
</script>

<style scoped>
.auth-page { display: flex; justify-content: center; padding-top: 80px; }
.auth-card { width: 400px; }
.auth-card h2 { margin-bottom: 24px; text-align: center; }
.form-group { margin-bottom: 16px; }
.form-group label { display: block; margin-bottom: 4px; font-size: 14px; color: #666; }
.form-group input { width: 100%; }
.error-text { color: #e74c3c; font-size: 13px; margin-bottom: 12px; }
.auth-link { text-align: center; margin-top: 16px; font-size: 14px; color: #666; }
.auth-link a { color: #4a90d9; text-decoration: none; }
</style>