<template>
  <div class="management-page">
    <div class="page-header">
      <h2>直播间管理</h2>
      <div class="header-actions">
        <select v-model="filterStatus" @change="loadRooms">
          <option value="">全部状态</option>
          <option value="waiting">未开始</option>
          <option value="live">进行中</option>
          <option value="closed">已结束</option>
        </select>
      </div>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="rooms.length === 0" class="empty">暂无直播间</div>
    <div v-else class="table-container card">
      <table class="data-table">
        <thead>
          <tr>
            <th>直播间ID</th>
            <th>标题</th>
            <th>主播</th>
            <th>状态</th>
            <th>SRS节点</th>
            <th>创建时间</th>
            <th>开播时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="room in rooms" :key="room.roomId">
            <td class="mono-cell">{{ room.roomId }}</td>
            <td class="title-cell">{{ room.title }}</td>
            <td>{{ room.publisherDisplayName || room.publisherUid }}</td>
            <td>
              <span class="status-tag" :class="room.status">{{ statusMap[room.status] || room.status }}</span>
            </td>
            <td>{{ room.srsNode || '-' }}</td>
            <td>{{ formatTime(room.createdAt) }}</td>
            <td>{{ formatTime(room.startedAt) }}</td>
            <td>
              <button class="btn btn-sm btn-primary" @click="goRoom(room.roomId)">进入</button>
              <button v-if="room.status !== 'closed'" class="btn btn-sm btn-danger" @click="handleCloseRoom(room.roomId)">关闭</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { listRooms, closeRoom as apiCloseRoom } from '../api/room'

const router = useRouter()
const rooms = ref<any[]>([])
const loading = ref(false)
const filterStatus = ref('')

const statusMap: Record<string, string> = {
  waiting: '未开始',
  live: '进行中',
  closed: '已结束'
}

async function loadRooms() {
  loading.value = true
  try {
    const res: any = await listRooms(filterStatus.value || undefined)
    rooms.value = res.data || []
  } catch (e) {
    console.error('load rooms error', e)
  } finally {
    loading.value = false
  }
}

async function handleCloseRoom(roomId: string) {
  if (!confirm('确定关闭该直播间？')) return
  try {
    await apiCloseRoom(roomId)
    await loadRooms()
  } catch (e) {
    console.error('close room error', e)
    alert('关闭失败')
  }
}

function goRoom(roomId: string) {
  router.push(`/rooms/${roomId}`)
}

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString()
}

onMounted(loadRooms)
</script>

<style scoped>
.management-page { max-width: 1200px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 22px; }
.header-actions { display: flex; gap: 12px; align-items: center; }
.loading, .empty { text-align: center; padding: 60px; color: #999; }
.table-container { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; }
.data-table th { padding: 12px 16px; text-align: left; font-size: 14px; font-weight: 600; color: #666; background: #f8f9fa; border-bottom: 1px solid #eee; }
.data-table td { padding: 12px 16px; font-size: 14px; color: #333; border-bottom: 1px solid #f0f0f0; }
.data-table tr:hover { background: #f8f9ff; }
.mono-cell { font-family: monospace; font-size: 12px; color: #666; }
.title-cell { font-weight: 500; color: #1a1a2e; }
.status-tag { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }
.status-tag.live { background: #d4edda; color: #155724; }
.status-tag.waiting { background: #fff3cd; color: #856404; }
.status-tag.closed { background: #e2e3e5; color: #383d41; }
</style>
