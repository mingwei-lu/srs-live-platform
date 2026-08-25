import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const uid = ref(localStorage.getItem('uid') || '')
  const username = ref(localStorage.getItem('username') || '')
  const role = ref(localStorage.getItem('role') || '')

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'admin')

  function setLogin(data: { uid: string; username: string; role: string; token: string }) {
    token.value = data.token
    uid.value = data.uid
    username.value = data.username
    role.value = data.role
    localStorage.setItem('token', data.token)
    localStorage.setItem('uid', data.uid)
    localStorage.setItem('username', data.username)
    localStorage.setItem('role', data.role)
  }

  function logout() {
    token.value = ''
    uid.value = ''
    username.value = ''
    role.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('uid')
    localStorage.removeItem('username')
    localStorage.removeItem('role')
  }

  return { token, uid, username, role, isLoggedIn, isAdmin, setLogin, logout }
})