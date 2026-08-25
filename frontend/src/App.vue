<template>
  <div id="app">
    <header class="app-header">
      <div class="header-left">
        <h1 @click="goHome">SRS 直播平台</h1>
        <nav class="nav-links" v-if="authStore.isLoggedIn">
          <router-link to="/rooms">直播间</router-link>
          <template v-if="authStore.isAdmin">
            <router-link to="/admin">集群管理</router-link>
            <router-link to="/admin/users">用户管理</router-link>
            <router-link to="/admin/nodes">SRS节点</router-link>
            <router-link to="/admin/rooms">直播管理</router-link>
          </template>
        </nav>
      </div>
      <div class="header-right">
        <template v-if="authStore.token">
          <span class="user-info">{{ authStore.username }}</span>
          <button class="btn btn-sm" @click="handleLogout">退出登录</button>
        </template>
        <template v-else>
          <router-link to="/login" class="btn btn-sm">登录</router-link>
          <router-link to="/register" class="btn btn-sm">注册</router-link>
        </template>
      </div>
    </header>
    <main class="app-main">
      <router-view />
    </main>
    <DebugPanel />
  </div>
</template>

<script setup lang="ts">
import { useAuthStore } from './store'
import { useRouter } from 'vue-router'
import DebugPanel from './components/DebugPanel.vue'

const authStore = useAuthStore()
const router = useRouter()

function goHome() {
  router.push('/')
}

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #f5f5f5; color: #333; }
.app-header { display: flex; justify-content: space-between; align-items: center; padding: 0 24px; height: 56px; background: #1a1a2e; color: #fff; }
.app-header h1 { font-size: 20px; cursor: pointer; }
.header-left { display: flex; align-items: center; gap: 24px; }
.nav-links { display: flex; gap: 16px; align-items: center; }
.nav-links a { color: #aaa; font-size: 14px; text-decoration: none; transition: color 0.2s; }
.nav-links a:hover, .nav-links a.router-link-active { color: #fff; }
.header-right { display: flex; align-items: center; gap: 12px; }
.user-info { font-size: 14px; color: #aaa; }
.app-main { max-width: 1200px; margin: 0 auto; padding: 24px; }
.btn { display: inline-flex; align-items: center; justify-content: center; padding: 8px 16px; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; text-decoration: none; }
.btn-sm { padding: 4px 12px; font-size: 13px; }
.btn-primary { background: #4a90d9; color: #fff; }
.btn-primary:hover { background: #357abd; }
.btn-danger { background: #e74c3c; color: #fff; }
.btn-danger:hover { background: #c0392b; }
.btn-success { background: #27ae60; color: #fff; }
.btn-success:hover { background: #219a52; }
input, select { padding: 8px 12px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; outline: none; }
input:focus { border-color: #4a90d9; }
.card { background: #fff; border-radius: 8px; padding: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
</style>