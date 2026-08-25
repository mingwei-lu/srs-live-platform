<template>
  <div class="management-page">
    <div class="page-header">
      <h2>SRS 节点管理</h2>
      <span class="subtitle">节点由 srs-proxy 自动注册，只读展示</span>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="nodes.length === 0" class="empty">
      <p>暂无节点数据</p>
      <p class="sub-text">请确保 srs-proxy 已启动并注册到 Redis</p>
    </div>
    <div v-else class="table-container card">
      <table class="data-table">
        <thead>
          <tr>
            <th>节点ID</th>
            <th>IP地址</th>
            <th>API端口</th>
            <th>RTC端口</th>
            <th>状态</th>
            <th>连接数</th>
            <th>CPU</th>
            <th>权重</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="node in nodes" :key="node.nodeId">
            <td class="mono-cell">{{ node.nodeId }}</td>
            <td>{{ node.ip }}</td>
            <td>{{ node.apiPort }}</td>
            <td>{{ node.rtcPort }}</td>
            <td>
              <span class="status-tag" :class="node.status">{{ statusMap[node.status] || node.status }}</span>
            </td>
            <td>{{ node.currentConnections || 0 }}/{{ node.maxConnections }}</td>
            <td>{{ node.cpuUsage != null ? node.cpuUsage + '%' : '-' }}</td>
            <td>{{ node.weight }}</td>
            <td>
              <button class="btn btn-sm btn-primary" @click="checkHealth(node.nodeId)">健康检查</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 健康检查结果弹窗 -->
    <div v-if="healthResult" class="modal-overlay" @click.self="healthResult = null">
      <div class="modal card">
        <h3>健康检查结果</h3>
        <pre class="json-display">{{ JSON.stringify(healthResult, null, 2) }}</pre>
        <div class="modal-actions">
          <button class="btn" @click="healthResult = null">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { getClusterNodes, checkClusterNodeHealth } from '../api/cluster'

const nodes = ref<any[]>([])
const loading = ref(false)
const healthResult = ref<any>(null)
let refreshTimer: number | null = null

const statusMap: Record<string, string> = {
  active: '正常',
  inactive: '离线',
  removing: '移除中'
}

async function loadNodes() {
  loading.value = true
  try {
    const res: any = await getClusterNodes()
    nodes.value = res.data || []
  } catch (e) {
    console.error('load nodes error', e)
  } finally {
    loading.value = false
  }
}

async function checkHealth(nodeId: string) {
  try {
    const res: any = await checkClusterNodeHealth(nodeId)
    healthResult.value = res.data
  } catch (e) {
    console.error('check health error', e)
    alert('健康检查失败')
  }
}

function startAutoRefresh() {
  refreshTimer = window.setInterval(() => {
    loadNodes()
  }, 10000)
}

onMounted(() => {
  loadNodes()
  startAutoRefresh()
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
  }
})
</script>

<style scoped>
.management-page { max-width: 1200px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 22px; }
.subtitle { font-size: 13px; color: #888; }
.loading, .empty { text-align: center; padding: 60px; color: #999; }
.empty .sub-text { font-size: 13px; color: #bbb; margin-top: 8px; }
.table-container { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; }
.data-table th { padding: 12px 16px; text-align: left; font-size: 14px; font-weight: 600; color: #666; background: #f8f9fa; border-bottom: 1px solid #eee; }
.data-table td { padding: 12px 16px; font-size: 14px; color: #333; border-bottom: 1px solid #f0f0f0; }
.data-table tr:hover { background: #f8f9ff; }
.mono-cell { font-family: monospace; font-size: 12px; color: #666; }
.status-tag { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }
.status-tag.active { background: #d4edda; color: #155724; }
.status-tag.inactive { background: #f8d7da; color: #721c24; }
.status-tag.removing { background: #fff3cd; color: #856404; }
.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { width: 560px; max-height: 80vh; overflow-y: auto; }
.modal h3 { margin-bottom: 16px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
.json-display { background: #f5f5f5; padding: 12px; border-radius: 4px; font-size: 13px; font-family: monospace; white-space: pre-wrap; word-break: break-all; max-height: 300px; overflow-y: auto; }
</style>
