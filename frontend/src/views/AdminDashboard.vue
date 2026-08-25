<template>
  <div class="admin-page">
    <h2>管理后台</h2>
    <p class="subtitle">SRS 集群状态大盘</p>

    <div class="stats-grid">
      <div class="stat-card card">
        <div class="stat-value">{{ overview.totalNodes || 0 }}</div>
        <div class="stat-label">总节点</div>
      </div>
      <div class="stat-card card">
        <div class="stat-value" style="color:#27ae60">{{ overview.onlineNodes || 0 }}</div>
        <div class="stat-label">在线节点</div>
      </div>
      <div class="stat-card card">
        <div class="stat-value" style="color:#e74c3c">{{ overview.offlineNodes || 0 }}</div>
        <div class="stat-label">异常节点</div>
      </div>
      <div class="stat-card card">
        <div class="stat-value">{{ overview.totalConnections || 0 }}</div>
        <div class="stat-label">总连接数</div>
      </div>
      <div class="stat-card card">
        <div class="stat-value">{{ overview.onlineUsers || 0 }}</div>
        <div class="stat-label">在线用户</div>
      </div>
    </div>

    <div class="card" style="margin-top:16px">
      <div class="section-header">
        <h3>节点列表（点击行查看详情）</h3>
        <button class="btn btn-sm" @click="loadData">刷新</button>
      </div>
      <table v-if="nodes.length > 0" class="node-table">
        <thead>
          <tr>
            <th>节点ID</th>
            <th>IP</th>
            <th>状态</th>
            <th>连接数</th>
            <th>CPU</th>
            <th>内存</th>
            <th>最后心跳</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="node in nodes" :key="node.nodeId" class="node-row" @click="showNodeDetail(node.nodeId)">
            <td>{{ node.nodeId }}</td>
            <td>{{ node.ip }}:{{ node.apiPort }}</td>
            <td><span class="status-dot" :class="node.status"></span>{{ node.status }}</td>
            <td>{{ node.currentConnections }}/{{ node.maxConnections }}</td>
            <td>{{ node.cpuUsage != null ? node.cpuUsage + '%' : '-' }}</td>
            <td>{{ node.memUsage != null ? node.memUsage + '%' : '-' }}</td>
            <td>{{ formatTime(node.lastHeartbeat) }}</td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">暂无节点数据</div>
    </div>

    <!-- 节点详情弹窗 -->
    <div v-if="selectedNode" class="modal-overlay" @click.self="selectedNode = null">
      <div class="modal card">
        <h3>节点详情 - {{ selectedNode.nodeId }}</h3>
        <div class="detail-grid">
          <div class="detail-item"><label>节点ID</label><span>{{ selectedNode.nodeId }}</span></div>
          <div class="detail-item"><label>IP地址</label><span>{{ selectedNode.ip }}</span></div>
          <div class="detail-item"><label>API端口</label><span>{{ selectedNode.apiPort }}</span></div>
          <div class="detail-item"><label>RTC端口</label><span>{{ selectedNode.rtcPort }}</span></div>
          <div class="detail-item"><label>状态</label><span class="status-dot" :class="selectedNode.status"></span>{{ selectedNode.status }}</div>
          <div class="detail-item"><label>权重</label><span>{{ selectedNode.weight }}</span></div>
          <div class="detail-item"><label>连接数</label><span>{{ selectedNode.currentConnections }} / {{ selectedNode.maxConnections }}</span></div>
          <div class="detail-item"><label>CPU使用率</label><span>{{ selectedNode.cpuUsage != null ? selectedNode.cpuUsage + '%' : '-' }}</span></div>
          <div class="detail-item"><label>内存使用率</label><span>{{ selectedNode.memUsage != null ? selectedNode.memUsage + '%' : '-' }}</span></div>
          <div class="detail-item"><label>注册时间</label><span>{{ formatTime(selectedNode.registeredAt) }}</span></div>
          <div class="detail-item"><label>最后心跳</label><span>{{ formatTime(selectedNode.lastHeartbeat) }}</span></div>
        </div>
        <div class="modal-actions">
          <button class="btn" @click="selectedNode = null">关闭</button>
        </div>
      </div>
    </div>

    <div class="card" style="margin-top:16px">
      <h3>集群事件</h3>
      <table v-if="events.length > 0" class="node-table">
        <thead>
          <tr><th>时间</th><th>节点</th><th>类型</th><th>严重程度</th><th>描述</th></tr>
        </thead>
        <tbody>
          <tr v-for="ev in events" :key="ev.id">
            <td>{{ formatTime(ev.createdAt) }}</td>
            <td>{{ ev.nodeId }}</td>
            <td>{{ ev.eventType }}</td>
            <td><span class="severity" :class="ev.severity">{{ ev.severity }}</span></td>
            <td>{{ ev.message }}</td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">暂无事件</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getClusterOverview, getClusterNodes, getClusterNode, getClusterEvents } from '../api/cluster'

const overview = ref<any>({})
const nodes = ref<any[]>([])
const events = ref<any[]>([])
const selectedNode = ref<any>(null)

async function loadData() {
  try {
    const [overviewRes, nodesRes, eventsRes] = await Promise.all([
      getClusterOverview(),
      getClusterNodes(),
      getClusterEvents()
    ])
    overview.value = (overviewRes as any).data || {}
    nodes.value = (nodesRes as any).data || []
    events.value = (eventsRes as any).data || []
  } catch (e) {
    console.error('load cluster data error', e)
  }
}

async function showNodeDetail(nodeId: string) {
  try {
    const res: any = await getClusterNode(nodeId)
    selectedNode.value = res.data
  } catch (e) {
    console.error('load node detail error', e)
  }
}

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString()
}

onMounted(loadData)
</script>

<style scoped>
.subtitle { color: #888; margin-bottom: 24px; }
.stats-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 16px; }
.stat-card { text-align: center; }
.stat-value { font-size: 32px; font-weight: bold; color: #1a1a2e; }
.stat-label { font-size: 14px; color: #888; margin-top: 4px; }
.section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.section-header h3 { font-size: 18px; }
.node-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.node-table th, .node-table td { padding: 10px 12px; text-align: left; border-bottom: 1px solid #f0f0f0; }
.node-table th { background: #fafafa; font-weight: 600; color: #666; }
.status-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; }
.status-dot.active { background: #27ae60; }
.status-dot.inactive { background: #e74c3c; }
.status-dot.removing { background: #f39c12; }
.severity { padding: 1px 8px; border-radius: 8px; font-size: 12px; }
.severity.critical { background: #fde8e8; color: #e74c3c; }
.severity.warning { background: #fef3cd; color: #f39c12; }
.severity.info { background: #e8f4fd; color: #4a90d9; }
.node-row { cursor: pointer; }
.node-row:hover { background: #f5f8ff; }
.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { width: 560px; max-height: 80vh; overflow-y: auto; }
.modal h3 { margin-bottom: 16px; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.detail-item { display: flex; flex-direction: column; gap: 2px; }
.detail-item label { font-size: 12px; color: #888; }
.detail-item span { font-size: 14px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
.empty { text-align: center; padding: 40px; color: #999; }
</style>