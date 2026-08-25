<template>
  <div v-if="showDebug" class="debug-panel">
    <div class="debug-header" @click="collapsed = !collapsed">
      <span>🔧 调试信息</span>
      <span>{{ collapsed ? '▼' : '▲' }}</span>
    </div>
    <div v-if="!collapsed" class="debug-content">
      <div class="debug-item">
        <span class="debug-label">模式:</span>
        <span class="debug-value">{{ envMode }}</span>
      </div>
      <div class="debug-item">
        <span class="debug-label">API地址:</span>
        <span class="debug-value">{{ apiUrl }}</span>
      </div>
      <div class="debug-item">
        <span class="debug-label">WS地址:</span>
        <span class="debug-value">{{ wsUrl }}</span>
      </div>
      <div class="debug-item">
        <span class="debug-label">当前页面:</span>
        <span class="debug-value">{{ currentUrl }}</span>
      </div>
      <div class="debug-actions">
        <button class="debug-btn" @click="copyDebugInfo">复制调试信息</button>
        <button class="debug-btn" @click="testApi">测试API</button>
      </div>
      <div v-if="apiTestResult" class="debug-result">
        <pre>{{ apiTestResult }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import api from '../api'

const collapsed = ref(true)
const showDebug = import.meta.env.VITE_DEBUG === 'true' || import.meta.env.DEV
const currentUrl = window.location.href
const envMode = import.meta.env.MODE

const apiUrl = computed(() => {
  const base = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  if (base.startsWith('http')) {
    return base
  }
  return window.location.origin + base
})

const wsUrl = computed(() => {
  const base = import.meta.env.VITE_WS_BASE_URL || '/ws'
  if (base.startsWith('ws://') || base.startsWith('wss://')) {
    return base
  }
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}${base}`
})

const apiTestResult = ref('')

async function testApi() {
  apiTestResult.value = '测试中...'
  try {
    const start = performance.now()
    const res: any = await api.get('/rooms')
    const duration = Math.round(performance.now() - start)
    apiTestResult.value = `✅ 成功 (${duration}ms)\n${JSON.stringify(res, null, 2).substring(0, 200)}`
  } catch (e: any) {
    apiTestResult.value = `❌ 失败: ${e?.message || '未知错误'}`
  }
}

function copyDebugInfo() {
  const info = [
    `模式: ${import.meta.env.MODE}`,
    `API地址: ${apiUrl.value}`,
    `WS地址: ${wsUrl.value}`,
    `当前页面: ${currentUrl}`,
    `UserAgent: ${navigator.userAgent}`,
  ].join('\n')
  navigator.clipboard.writeText(info).then(() => {
    alert('调试信息已复制到剪贴板')
  })
}
</script>

<style scoped>
.debug-panel {
  position: fixed;
  bottom: 10px;
  right: 10px;
  z-index: 9999;
  background: rgba(0, 0, 0, 0.85);
  color: #00ff00;
  border-radius: 8px;
  font-family: 'Courier New', monospace;
  font-size: 12px;
  max-width: 400px;
  min-width: 200px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.debug-header {
  padding: 8px 12px;
  cursor: pointer;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid rgba(0, 255, 0, 0.2);
  font-weight: bold;
}

.debug-content {
  padding: 8px 12px;
}

.debug-item {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  border-bottom: 1px solid rgba(0, 255, 0, 0.1);
}

.debug-label {
  color: #aaa;
  margin-right: 8px;
}

.debug-value {
  color: #0ff;
  word-break: break-all;
  text-align: right;
  flex: 1;
}

.debug-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid rgba(0, 255, 0, 0.2);
}

.debug-btn {
  background: rgba(0, 255, 0, 0.15);
  border: 1px solid #00ff00;
  color: #00ff00;
  padding: 4px 10px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 11px;
  flex: 1;
}

.debug-btn:hover {
  background: rgba(0, 255, 0, 0.3);
}

.debug-result {
  margin-top: 8px;
  padding: 6px;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 4px;
  max-height: 150px;
  overflow: auto;
}

.debug-result pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 11px;
}
</style>
