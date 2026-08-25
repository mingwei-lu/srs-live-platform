<template>
  <div class="room-list-page">
    <div class="page-header">
      <h2>直播间列表</h2>
      <div class="header-actions">
        <button v-if="authStore.isLoggedIn" class="btn btn-primary" @click="showCreate = true">创建直播间</button>
      </div>
    </div>

    <!-- 创建直播间弹窗 -->
    <div v-if="showCreate" class="modal-overlay" @click.self="showCreate = false">
      <div class="modal card">
        <h3>创建直播间</h3>
        <input v-model="newRoomTitle" placeholder="直播间标题" style="width:100%;margin:12px 0" />
        <div class="modal-actions">
          <button class="btn" @click="showCreate = false">取消</button>
          <button class="btn btn-primary" @click="handleCreate">创建</button>
        </div>
      </div>
    </div>

    <div class="status-filter-bar">
      <div
        class="status-filter-item"
        :class="{ active: filterStatus === '' }"
        @click="filterStatus = ''; loadRooms()"
      >
        <span class="status-dot all"></span>
        全部 <span class="status-count">{{ statusCounts.total }}</span>
      </div>
      <div
        class="status-filter-item"
        :class="{ active: filterStatus === 'waiting' }"
        @click="filterStatus = 'waiting'; loadRooms()"
      >
        <span class="status-dot waiting"></span>
        未开始 <span class="status-count">{{ statusCounts.waiting }}</span>
      </div>
      <div
        class="status-filter-item"
        :class="{ active: filterStatus === 'live' }"
        @click="filterStatus = 'live'; loadRooms()"
      >
        <span class="status-dot live"></span>
        进行中 <span class="status-count">{{ statusCounts.live }}</span>
      </div>
      <div
        class="status-filter-item"
        :class="{ active: filterStatus === 'closed' }"
        @click="filterStatus = 'closed'; loadRooms()"
      >
        <span class="status-dot closed"></span>
        已结束 <span class="status-count">{{ statusCounts.closed }}</span>
      </div>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="rooms.length === 0" class="empty">暂无直播间</div>
    <div v-else class="table-container">
      <table class="room-table">
        <thead>
          <tr>
            <th>直播间标题</th>
            <th>主播</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>开播时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="room in rooms" :key="room.roomId" @click="goRoom(room.roomId)" class="room-row">
            <td class="room-title-cell">{{ room.title }}</td>
            <td>{{ room.publisherDisplayName || room.publisherUid }}</td>
            <td>
              <span class="room-status" :class="room.status">
                <span class="status-indicator"></span>
                {{ statusMap[room.status] || room.status }}
              </span>
            </td>
            <td>{{ formatTime(room.createdAt) }}</td>
            <td>{{ formatTime(room.startedAt) }}</td>
            <td @click.stop>
              <button v-if="canClose(room)" class="btn btn-sm btn-danger" @click="handleCloseRoom(room.roomId)">关闭</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { listRooms, createRoom, closeRoom } from '../api/room'
import { useAuthStore } from '../store'

const router = useRouter()
const authStore = useAuthStore()
const rooms = ref<any[]>([])
const loading = ref(false)
const filterStatus = ref('')
const showCreate = ref(false)
const newRoomTitle = ref('')

const statusCounts = computed(() => {
  const counts = { total: rooms.value.length, waiting: 0, live: 0, closed: 0 }
  for (const room of rooms.value) {
    if (counts[room.status as keyof typeof counts] !== undefined) {
      counts[room.status as keyof typeof counts]++
    }
  }
  return counts
})

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

async function handleCreate() {
  if (!newRoomTitle.value) return
  try {
    await createRoom({ title: newRoomTitle.value })
    newRoomTitle.value = ''
    showCreate.value = false
    await loadRooms()
  } catch (e) {
    console.error('create room error', e)
  }
}

function canClose(room: any) {
  if (room.status === 'closed') return false
  if (authStore.isAdmin) return true
  if (room.publisherUid === authStore.uid) return true
  return false
}

async function handleCloseRoom(roomId: string) {
  if (!confirm('确定关闭该直播间？')) return
  try {
    await closeRoom(roomId)
    await loadRooms()
  } catch (e) {
    console.error('close room error', e)
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
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 22px; }
.header-actions { display: flex; gap: 12px; align-items: center; }
.loading, .empty { text-align: center; padding: 60px; color: #999; }

.table-container { overflow-x: auto; }
.room-table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.08); }
.room-table thead { background: #f8f9fa; }
.room-table th { padding: 14px 16px; text-align: left; font-size: 14px; font-weight: 600; color: #666; border-bottom: 1px solid #eee; }
.room-table td { padding: 14px 16px; font-size: 14px; color: #333; border-bottom: 1px solid #f0f0f0; }
.room-row { cursor: pointer; transition: background 0.15s; }
.room-row:hover { background: #f8f9ff; }
.room-title-cell { font-weight: 500; color: #1a1a2e; }

.room-status { display: inline-flex; align-items: center; gap: 4px; padding: 3px 10px; border-radius: 10px; font-size: 12px; font-weight: 500; }
.room-status .status-indicator { display: inline-block; width: 6px; height: 6px; border-radius: 50%; }
.room-status.live { background: #d4edda; color: #155724; }
.room-status.live .status-indicator { background: #28a745; }
.room-status.waiting { background: #fff3cd; color: #856404; }
.room-status.waiting .status-indicator { background: #ffc107; }
.room-status.closed { background: #e2e3e5; color: #383d41; }
.room-status.closed .status-indicator { background: #6c757d; }

.status-filter-bar { display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; }
.status-filter-item { display: flex; align-items: center; gap: 6px; padding: 8px 16px; border-radius: 20px; background: #fff; border: 1px solid #e0e0e0; cursor: pointer; font-size: 14px; color: #666; transition: all 0.2s; user-select: none; }
.status-filter-item:hover { border-color: #1890ff; color: #1890ff; }
.status-filter-item.active { background: #e6f7ff; border-color: #1890ff; color: #1890ff; font-weight: 500; }
.status-filter-item .status-dot { width: 8px; height: 8px; border-radius: 50%; }
.status-filter-item .status-dot.all { background: #1890ff; }
.status-filter-item .status-dot.waiting { background: #ffc107; }
.status-filter-item .status-dot.live { background: #28a745; }
.status-filter-item .status-dot.closed { background: #6c757d; }
.status-filter-item .status-count { font-size: 12px; color: #999; margin-left: 2px; }
.status-filter-item.active .status-count { color: #1890ff; }

.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { width: 400px; }
.modal h3 { margin-bottom: 8px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }
</style>
