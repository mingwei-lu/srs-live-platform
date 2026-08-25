<template>
  <div class="auth-page">
    <div class="auth-card card">
      <h2>注册</h2>
      <form @submit.prevent="handleRegister">
        <div class="form-group">
          <label>手机号</label>
          <div class="input-row">
            <input v-model="form.phone" type="tel" required placeholder="请输入手机号" maxlength="11" @blur="checkPhoneExists" />
            <span v-if="phoneRegistered" class="hint-warn">已注册</span>
            <span v-else-if="phoneChecked && !phoneRegistered" class="hint-ok">可用</span>
          </div>
        </div>

        <div class="form-group">
          <label>密码</label>
          <input v-model="form.password" type="password" required placeholder="6-32位字符" />
        </div>

        <div class="form-group">
          <label>昵称（可选）</label>
          <input v-model="form.username" type="text" placeholder="如：张三" />
        </div>

        <div class="form-group">
          <label>医院 / 公司</label>
          <input v-model="form.company" type="text" placeholder="如：北京协和医院" />
        </div>
        <div class="form-group">
          <label>部门 / 科室</label>
          <input v-model="form.department" type="text" placeholder="如：心内科" />
        </div>
        <div class="form-group">
          <label>手机尾号</label>
          <input v-model="form.phoneTail" type="text" placeholder="如：1234" maxlength="4" />
        </div>
        <p class="hint-text">填写公司/科室/尾号可与其他同名用户区分</p>

        <p v-if="error" class="error-text">{{ error }}</p>
        <button type="submit" class="btn btn-primary" style="width:100%">注册</button>
      </form>
      <p class="auth-link">已有账号？<router-link to="/login">立即登录</router-link></p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { register, checkPhone } from '../api/user'
import { useAuthStore } from '../store'
import { connectWs } from '../utils/ws'

const router = useRouter()
const authStore = useAuthStore()
const error = ref('')
const phoneRegistered = ref(false)
const phoneChecked = ref(false)
const form = reactive({ phone: '', password: '', username: '', company: '', department: '', phoneTail: '' })

async function checkPhoneExists() {
  if (!form.phone || form.phone.length < 11) return
  phoneChecked.value = false
  try {
    const res: any = await checkPhone(form.phone)
    phoneRegistered.value = res.data === true
  } catch {
    phoneRegistered.value = false
  } finally {
    phoneChecked.value = true
  }
}

async function handleRegister() {
  try {
    error.value = ''
    const res: any = await register(form)
    authStore.setLogin(res.data)
    connectWs()
    router.push('/rooms')
  } catch (e: any) {
    if (e?.code === 40011) {
      error.value = '该手机号已注册，请直接登录'
    } else if (e?.code === 40010) {
      error.value = '该昵称+辨识信息组合已存在，请修改'
    } else {
      error.value = e?.message || '注册失败'
    }
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
.input-row { display: flex; align-items: center; gap: 8px; }
.input-row input { flex: 1; }
.hint-warn { font-size: 12px; color: #e74c3c; white-space: nowrap; }
.hint-ok { font-size: 12px; color: #27ae60; white-space: nowrap; }
.hint-text { font-size: 12px; color: #888; margin-top: -8px; margin-bottom: 12px; }
.error-text { color: #e74c3c; font-size: 13px; margin-bottom: 12px; }
.auth-link { text-align: center; margin-top: 16px; font-size: 14px; color: #666; }
.auth-link a { color: #4a90d9; text-decoration: none; }
</style>