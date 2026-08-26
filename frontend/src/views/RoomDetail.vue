<template>
  <div class="room-detail-page">
    <div v-if="room" class="room-main">
      <!-- 顶部信息栏 -->
      <div class="room-info card">
        <div class="info-header">
          <div v-if="!editingTitle" class="title-row">
            <h2>{{ room.title }}</h2>
            <button v-if="canEdit && isPublisherMode" class="btn btn-sm" @click="startEditTitle">编辑</button>
          </div>
          <div v-else class="title-edit-row">
            <input v-model="editTitleValue" class="title-input" />
            <button class="btn btn-sm btn-primary" @click="saveTitle">保存</button>
            <button class="btn btn-sm" @click="cancelEditTitle">取消</button>
          </div>
          <span class="status-badge" :class="room.status">{{ statusMap[room.status] }}</span>
          <span v-if="currentMode === 'viewer'" class="mode-badge">观众端</span>
          <span v-if="currentMode === 'publisher'" class="mode-badge publisher">主播端</span>
          <!-- 录播状态 -->
          <span v-if="room.status === 'live'" class="record-status">
            <template v-if="localRecording && serverRecording">
              <span class="record-dot"></span>本地+服务器录播中
            </template>
            <template v-else-if="localRecording">
              <span class="record-dot"></span>本地录播中
            </template>
            <template v-else-if="serverRecording">
              <span class="record-dot"></span>服务器录播中
            </template>
          </span>
        </div>
        <p class="info-meta">
          房间ID: {{ room.roomId }} | 主播: {{ room.publisherDisplayName || room.publisherUid }}
        </p>
        <!-- 操作按钮区 -->
        <div class="info-actions">
          <template v-if="isPublisherMode">
            <template v-if="room.status === 'waiting'">
              <button class="btn btn-success" @click="handleStartLive" :disabled="liveLoading">
                {{ liveLoading ? '开播中...' : '开始直播' }}
              </button>
              <!-- 录播选项 -->
              <div class="record-options">
                <label class="record-toggle">
                  <input type="checkbox" v-model="localRecordEnabled" />
                  本地录播
                </label>
                <label class="record-toggle">
                  <input type="checkbox" v-model="serverRecordEnabled" />
                  服务器录播
                </label>
              </div>
              <!-- 画质选择 -->
              <div class="quality-select">
                <label>画质:</label>
                <select v-model="selectedQuality" :disabled="room.status === 'live'">
                  <option v-for="q in VIDEO_QUALITIES" :key="q.label" :value="q">{{ q.label }}</option>
                </select>
              </div>
            </template>
            <button v-if="room.status === 'live'" class="btn btn-danger" @click="handleStopLive">结束直播</button>
          </template>
          <button class="btn" @click="showShare = true">分享链接</button>
          <button class="btn" @click="confirmLeave">返回列表</button>
        </div>
      </div>

      <!-- 主播端：设备异常提示 -->
      <div v-if="isPublisherMode && deviceError" class="device-error card">
        <div class="error-icon">⚠️</div>
        <div class="error-content">
          <strong>{{ deviceError.title }}</strong>
          <p>{{ deviceError.message }}</p>
          <button class="btn btn-sm" @click="retryDevice">重新检测</button>
        </div>
      </div>

      <!-- 视频区域 -->
      <div class="room-content">
        <!-- 主播端：摄像头预览 + 推流 -->
        <div v-if="isPublisherMode" class="video-section">
          <div class="video-container card" :class="{ live: room.status === 'live' }" ref="publisherContainer">
            <video ref="publisherVideo" class="main-video" autoplay playsinline muted></video>
            <div v-if="room.status === 'waiting'" class="video-overlay">
              <p>点击上方「开始直播」按钮开启摄像头并推流</p>
            </div>
            <div v-if="room.status === 'live'" class="live-indicator">
              <span class="dot"></span> 直播中
            </div>
            <!-- 本地录播状态 -->
            <div v-if="localRecording" class="local-record-indicator">
              <span class="record-dot"></span> 本地录制中
            </div>
            <!-- 全屏按钮 -->
            <button class="fullscreen-btn" @click="toggleFullscreen('publisher')" title="全屏">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"/>
              </svg>
            </button>
          </div>

          <!-- 主播控制栏 -->
          <div v-if="room.status === 'live' && publisher" class="media-controls card">
            <div class="control-group">
              <button class="btn btn-sm" :class="{ 'btn-primary': isScreenSharing }" @click="toggleScreenShare">
                <span v-if="isScreenSharing">🖥️ 屏幕共享中</span>
                <span v-else>🖥️ 屏幕共享</span>
              </button>
              <button class="btn btn-sm" :class="{ 'btn-primary': isMicOn }" @click="toggleMicrophone">
                <span v-if="isMicOn">🎤 麦克风开</span>
                <span v-else>🎤 麦克风关</span>
              </button>
              <button class="btn btn-sm" :class="{ 'btn-danger': localRecording }" @click="toggleLocalRecord">
                <span v-if="localRecording">⏹️ 停止本地录制</span>
                <span v-else>🔴 开始本地录制</span>
              </button>
              <span v-if="localRecordBlob && !converting && !mp4Blob" class="record-download">
                <a :href="localRecordUrl" download="recording.webm" class="btn btn-sm btn-success">⬇️ 下载 WebM</a>
              </span>
              <span v-if="localRecordBlob && !converting && !mp4Blob" class="record-download">
                <button class="btn btn-sm btn-primary" @click="handleConvertToMp4">🎬 转码为 MP4</button>
              </span>
              <span v-if="converting" class="convert-progress">
                <span class="progress-bar"><span class="progress-fill" :style="{ width: convertProgress + '%' }"></span></span>
                <span class="progress-text">转码中 {{ convertProgress }}%</span>
              </span>
              <span v-if="mp4Blob" class="record-download">
                <a :href="mp4Url" download="recording.mp4" class="btn btn-sm btn-success">⬇️ 下载 MP4</a>
              </span>
            </div>
          </div>
        </div>

        <!-- 观众端：视频播放器 -->
        <div v-if="isViewerMode" class="video-section">
          <div class="video-container card" ref="viewerContainer">
            <video ref="viewerVideo" class="main-video" autoplay playsinline></video>
            <div v-if="room.status === 'waiting'" class="video-overlay">
              <p>直播尚未开始，请稍候...</p>
            </div>
            <div v-if="room.status === 'live'" class="live-indicator">
              <span class="dot"></span> 直播中
            </div>
            <!-- 全屏按钮 -->
            <button class="fullscreen-btn" @click="toggleFullscreen('viewer')" title="全屏">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"/>
              </svg>
            </button>
          </div>
        </div>

        <!-- 右侧：在线观众 -->
        <div class="sidebar card">
          <h3>在线观众 ({{ onlineUsers.length }})</h3>
          <div v-if="onlineUsers.length === 0" class="empty-users">暂无观众</div>
          <ul v-else class="user-list">
            <li v-for="u in onlineUsers" :key="u.uid" class="user-item">
              <span>{{ u.username }} ({{ u.role }})</span>
              <button v-if="canKick(u.uid)" class="btn btn-sm btn-danger" @click="handleKick(u.uid)">踢出</button>
            </li>
          </ul>
        </div>
      </div>
    </div>
    <div v-else class="loading">加载中...</div>

    <!-- 分享弹窗 -->
    <div v-if="showShare" class="share-modal" @click.self="showShare = false">
      <div class="share-content card">
        <h3>分享直播间</h3>
        <div class="share-item">
          <label>主播链接（推流）</label>
          <div class="share-url-row">
            <input :value="publisherLink" readonly />
            <button class="btn btn-sm" @click="copyToClipboard(publisherLink)">复制</button>
          </div>
        </div>
        <div class="share-item">
          <label>观众链接（观看）</label>
          <div class="share-url-row">
            <input :value="viewerLink" readonly />
            <button class="btn btn-sm" @click="copyToClipboard(viewerLink)">复制</button>
          </div>
        </div>
        <button class="btn" @click="showShare = false">关闭</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getRoom, startLive, stopLive, getRoomUsers, kickUser, updateRoom, getPlayUrl } from '../api/room'
import { getUser } from '../api/user'
import { useAuthStore } from '../store'
import { getWsClient, connectWs } from '../utils/ws'
import { startWhipPublish, startWhepPlay, type WhipPublisher, VIDEO_QUALITIES, type VideoQuality } from '../utils/rtc'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const room = ref<any>(null)
const onlineUsers = ref<any[]>([])
const liveLoading = ref(false)
const publisherInfo = ref<any>(null)
const editingTitle = ref(false)
const editTitleValue = ref('')
const showShare = ref(false)
const publisherVideo = ref<HTMLVideoElement | null>(null)
const viewerVideo = ref<HTMLVideoElement | null>(null)
const publisherContainer = ref<HTMLDivElement | null>(null)
const viewerContainer = ref<HTMLDivElement | null>(null)

// 画质选择
const selectedQuality = ref<VideoQuality>(VIDEO_QUALITIES[1]) // 默认标清

// 当前模式
const currentMode = computed(() => {
  const m = route.query.mode as string
  return m === 'viewer' ? 'viewer' : 'publisher'
})
const isPublisherMode = computed(() => currentMode.value === 'publisher')
const isViewerMode = computed(() => currentMode.value === 'viewer')

const canEdit = computed(() => {
  if (!room.value) return false
  if (authStore.isAdmin) return true
  if (room.value.publisherUid === authStore.uid) return true
  return false
})

const statusMap: Record<string, string> = { waiting: '等待中', live: '直播中', closed: '已关闭' }

// 分享链接
const publisherLink = computed(() => {
  const base = window.location.origin + window.location.pathname
  return base + '?mode=publisher'
})
const viewerLink = computed(() => {
  const base = window.location.origin + window.location.pathname
  return base + '?mode=viewer'
})

const canKick = (targetUid: string) => {
  if (targetUid === authStore.uid) return false
  if (authStore.isAdmin) return true
  if (room.value?.publisherUid === authStore.uid) return true
  return false
}

async function loadRoom() {
  try {
    const res: any = await getRoom(route.params.roomId as string)
    room.value = res.data
    if (room.value?.publisherUid) {
      try {
        const userRes: any = await getUser(room.value.publisherUid)
        publisherInfo.value = userRes.data
      } catch (_) { /* ignore */ }
    }
  } catch (e) {
    console.error('load room error', e)
  }
}

async function loadUsers() {
  try {
    const res: any = await getRoomUsers(route.params.roomId as string)
    onlineUsers.value = res.data || []
  } catch (e) {
    console.error('load users error', e)
  }
}

// ============ 设备检测与异常提示 ============
interface DeviceErrorInfo {
  title: string
  message: string
}

const deviceError = ref<DeviceErrorInfo | null>(null)

async function checkDevices(): Promise<boolean> {
  deviceError.value = null
  try {
    // 检测摄像头
    const videoStream = await navigator.mediaDevices.getUserMedia({ video: true })
    videoStream.getTracks().forEach(t => t.stop())
  } catch (err: any) {
    deviceError.value = {
      title: '摄像头检测失败',
      message: err.name === 'NotAllowedError'
        ? '请允许浏览器访问摄像头权限'
        : err.name === 'NotFoundError'
          ? '未检测到可用摄像头设备'
          : `设备异常: ${err.message}`
    }
    return false
  }

  try {
    // 检测麦克风
    const audioStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    audioStream.getTracks().forEach(t => t.stop())
  } catch (err: any) {
    deviceError.value = {
      title: '麦克风检测失败',
      message: err.name === 'NotAllowedError'
        ? '请允许浏览器访问麦克风权限'
        : err.name === 'NotFoundError'
          ? '未检测到可用麦克风设备'
          : `设备异常: ${err.message}`
    }
    return false
  }

  return true
}

function retryDevice() {
  checkDevices()
}

// 全屏切换
function toggleFullscreen(type: 'publisher' | 'viewer') {
  const el = type === 'publisher' ? publisherContainer.value : viewerContainer.value
  if (!el) return
  if (document.fullscreenElement) {
    document.exitFullscreen()
  } else {
    el.requestFullscreen()
  }
}

// ============ WHIP 推流相关 ============
let publisher: WhipPublisher | null = null
let publisherCleanup: (() => void) | null = null

// 媒体控制状态
const isMicOn = ref(true)
const isScreenSharing = ref(false)
const localRecording = ref(false)
const localRecordBlob = ref<Blob | null>(null)
const localRecordUrl = ref('')
const serverRecording = ref(false)

// 录播选项（开播前选择）
const localRecordEnabled = ref(false)
const serverRecordEnabled = ref(false)

// 本地录播转码 MP4 相关
const converting = ref(false)
const convertProgress = ref(0)
const mp4Blob = ref<Blob | null>(null)
const mp4Url = ref('')

async function handleStartLive() {
  // 开播前检测设备
  const devicesOk = await checkDevices()
  if (!devicesOk) {
    return
  }

  liveLoading.value = true
  try {
    const res: any = await startLive(route.params.roomId as string, serverRecordEnabled.value)
    const { whipUrl } = res.data
    await loadRoom()

    // 开始 WHIP 推流
    if (publisherVideo.value && whipUrl) {
      publisher = await startWhipPublish(whipUrl, publisherVideo.value, {
        enableCamera: true,
        enableMicrophone: true,
        publisherName: authStore.username || '主播',
        quality: selectedQuality.value
      })
      publisherCleanup = publisher.stop

      // 如果选择了本地录播，自动开始录制
      if (localRecordEnabled.value && publisher) {
        publisher.startLocalRecord()
        localRecording.value = true
      }

      // 设置服务器录播状态
      serverRecording.value = serverRecordEnabled.value
    }
  } catch (e: any) {
    console.error('start live error', e)
    alert('开播失败: ' + (e?.message || '未知错误'))
  } finally {
    liveLoading.value = false
  }
}

async function handleStopLive() {
  try {
    // 停止本地录制
    if (localRecording.value && publisher) {
      const blob = publisher.stopLocalRecord()
      if (blob) {
        localRecordBlob.value = blob
        localRecordUrl.value = URL.createObjectURL(blob)
      }
      localRecording.value = false
    }

    // 清理 MP4 状态
    if (mp4Url.value) {
      URL.revokeObjectURL(mp4Url.value)
      mp4Url.value = ''
    }
    mp4Blob.value = null

    if (publisherCleanup) {
      publisherCleanup()
      publisherCleanup = null
    }
    publisher = null

    await stopLive(route.params.roomId as string)
    await loadRoom()
  } catch (e) {
    console.error('stop live error', e)
  }
}

// 媒体控制
async function toggleScreenShare() {
  if (!publisher) return
  try {
    if (isScreenSharing.value) {
      await publisher.switchToCamera()
      isScreenSharing.value = false
    } else {
      await publisher.switchToScreenShare()
      isScreenSharing.value = true
    }
  } catch (e: any) {
    alert('屏幕共享失败: ' + (e?.message || '未知错误'))
  }
}

function toggleMicrophone() {
  isMicOn.value = !isMicOn.value
  if (publisher) {
    publisher.setAudioEnabled(isMicOn.value)
  }
}

// 本地录播控制
function toggleLocalRecord() {
  if (!publisher) return
  if (localRecording.value) {
    // 停止录制
    const blob = publisher.stopLocalRecord()
    if (blob) {
      localRecordBlob.value = blob
      localRecordUrl.value = URL.createObjectURL(blob)
    }
    localRecording.value = false
  } else {
    // 开始录制
    publisher.startLocalRecord()
    localRecording.value = true
    localRecordBlob.value = null
    localRecordUrl.value = ''
    // 清理之前的 MP4
    if (mp4Url.value) {
      URL.revokeObjectURL(mp4Url.value)
      mp4Url.value = ''
    }
    mp4Blob.value = null
  }
}

// MP4 转码
async function handleConvertToMp4() {
  if (!localRecordBlob.value) return
  converting.value = true
  convertProgress.value = 0
  try {
    const { convertWebmToMp4 } = await import('../utils/ffmpeg-mp4')
    mp4Blob.value = await convertWebmToMp4(localRecordBlob.value, (progress) => {
      convertProgress.value = Math.round(progress * 100)
    })
    mp4Url.value = URL.createObjectURL(mp4Blob.value)
  } catch (e: any) {
    alert('转码失败: ' + (e?.message || '未知错误'))
    console.error('convert to mp4 error:', e)
  } finally {
    converting.value = false
  }
}

// ============ WHEP 拉流相关 ============
let viewerCleanup: (() => void) | null = null

async function startViewerPlay() {
  if (!viewerVideo.value) return
  try {
    const res: any = await getPlayUrl(route.params.roomId as string)
    const whepUrl = res.data
    if (whepUrl) {
      viewerCleanup = await startWhepPlay(whepUrl, viewerVideo.value)
    }
  } catch (e) {
    console.error('start viewer play error', e)
  }
}

async function handleKick(targetUid: string) {
  try {
    await kickUser(route.params.roomId as string, targetUid)
    await loadUsers()
  } catch (e) {
    console.error('kick user error', e)
  }
}

function startEditTitle() {
  editTitleValue.value = room.value?.title || ''
  editingTitle.value = true
}

function cancelEditTitle() {
  editingTitle.value = false
}

async function saveTitle() {
  if (!editTitleValue.value || !room.value) return
  try {
    await updateRoom(route.params.roomId as string, { title: editTitleValue.value })
    room.value.title = editTitleValue.value
    editingTitle.value = false
  } catch (e) {
    console.error('update title error', e)
  }
}

function copyToClipboard(text: string) {
  navigator.clipboard.writeText(text).then(() => alert('已复制到剪贴板'))
}

// 退出直播间确认
function confirmLeave() {
  if (room.value?.status === 'live' && (localRecording.value || serverRecording.value)) {
    const msg = localRecording.value && serverRecording.value
      ? '当前正在本地录播和服务器录播中，退出后录制将停止/保存，是否确认退出？'
      : localRecording.value
        ? '当前正在本地录播中，退出后录制将停止/保存，是否确认退出？'
        : '当前正在服务器录播中，退出后录制将停止/保存，是否确认退出？'
    if (!confirm(msg)) return
  }
  router.push('/rooms')
}

let wsClient: any = null
onMounted(() => {
  loadRoom()
  loadUsers()
  connectWs()
  wsClient = getWsClient()
  wsClient.on('room_user_list', (msg: any) => {
    if (msg.data?.roomId === route.params.roomId) {
      onlineUsers.value = msg.data.users || []
    }
  })
  wsClient.on('room_status_change', (msg: any) => {
    if (msg.data?.roomId === route.params.roomId) {
      loadRoom()
    }
  })
  setTimeout(() => {
    wsClient.joinRoom(route.params.roomId as string)
  }, 500)

  // 观众模式：自动拉流
  if (isViewerMode.value) {
    const checkInterval = setInterval(() => {
      if (room.value?.status === 'live') {
        clearInterval(checkInterval)
        startViewerPlay()
      }
    }, 1000)
    setTimeout(() => clearInterval(checkInterval), 30000)
  }
})

onUnmounted(() => {
  if (wsClient) {
    wsClient.leaveRoom(route.params.roomId as string)
  }
  if (publisherCleanup) {
    publisherCleanup()
    publisherCleanup = null
  }
  if (viewerCleanup) {
    viewerCleanup()
    viewerCleanup = null
  }
  // 清理本地录制 URL
  if (localRecordUrl.value) {
    URL.revokeObjectURL(localRecordUrl.value)
  }
  if (mp4Url.value) {
    URL.revokeObjectURL(mp4Url.value)
  }
})
</script>

<style scoped>
/* === 全局直播布局：铺满视口 === */
.room-detail-page {
  width: 100%;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: #0d0d1a;
}

/* 主要内容区 */
.room-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.card {
  background: rgba(255,255,255,0.05);
  border-radius: 8px;
  padding: 16px;
  border: 1px solid rgba(255,255,255,0.08);
}

/* 顶部信息栏 */
.room-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: rgba(13, 13, 26, 0.95);
  border-bottom: 1px solid rgba(255,255,255,0.08);
  color: #fff;
  flex-shrink: 0;
  gap: 12px;
  flex-wrap: wrap;
}
.info-header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.info-header h2 { font-size: 18px; margin: 0; color: #fff; }
.title-row { display: flex; align-items: center; gap: 6px; }
.title-edit-row { display: flex; align-items: center; gap: 6px; }
.title-input { width: 200px; padding: 4px 8px; font-size: 13px; }
.status-badge { padding: 2px 10px; border-radius: 10px; font-size: 11px; font-weight: 500; }
.status-badge.live { background: #27ae60; color: #fff; }
.status-badge.waiting { background: #f39c12; color: #fff; }
.status-badge.closed { background: #95a5a6; color: #fff; }
.mode-badge { padding: 2px 8px; border-radius: 6px; font-size: 11px; background: #e74c3c; color: #fff; }
.mode-badge.publisher { background: #3498db; }
.info-meta { font-size: 12px; color: #888; margin: 0; }
.info-actions { display: flex; gap: 6px; flex-wrap: wrap; align-items: center; }
.quality-select { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #aaa; }
.quality-select select { padding: 3px 6px; font-size: 12px; background: #1a1a2e; color: #fff; border: 1px solid #333; border-radius: 4px; }

/* 设备异常提示 */
.device-error {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff3cd;
  border: 1px solid #ffc107;
  color: #856404;
  padding: 10px 16px;
  flex-shrink: 0;
}
.error-icon { font-size: 20px; }
.error-content { flex: 1; }
.error-content strong { display: block; margin-bottom: 2px; font-size: 14px; }
.error-content p { margin: 0 0 6px 0; font-size: 12px; }

/* 主内容区：视频 + 侧边栏 */
.room-content {
  flex: 1;
  display: flex;
  min-height: 0;
  gap: 0;
  overflow: hidden;
}

/* 视频区域 */
.video-section {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  position: relative;
}

.video-container {
  position: relative;
  flex: 1;
  min-height: 0;
  background: #000;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.main-video {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.video-overlay {
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  background: rgba(0,0,0,0.6);
  text-align: center;
  padding: 20px;
  font-size: 14px;
}

.live-indicator {
  position: absolute;
  top: 12px;
  left: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #fff;
  background: rgba(231, 76, 60, 0.85);
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
}

.local-record-indicator {
  position: absolute;
  top: 12px;
  right: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  color: #fff;
  background: rgba(231, 76, 60, 0.85);
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
}

.record-dot { width: 8px; height: 8px; background: #ff0000; border-radius: 50%; animation: blink 1s infinite; }
.record-status { display: inline-flex; align-items: center; gap: 6px; padding: 2px 10px; border-radius: 6px; font-size: 11px; background: #e74c3c; color: #fff; }
.dot { width: 8px; height: 8px; background: #fff; border-radius: 50%; animation: blink 1s infinite; }
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }

/* 媒体控制栏 */
.media-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 10px 16px;
  background: rgba(13, 13, 26, 0.95);
  border-top: 1px solid rgba(255,255,255,0.08);
  flex-shrink: 0;
}
.media-controls .control-group {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.record-download { margin-left: 8px; }

/* 转码进度条 */
.convert-progress {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #666;
}
.progress-bar {
  width: 100px;
  height: 6px;
  background: #e0e0e0;
  border-radius: 3px;
  overflow: hidden;
}
.progress-fill {
  display: block;
  height: 100%;
  background: #3498db;
  border-radius: 3px;
  transition: width 0.3s;
}
.progress-text { white-space: nowrap; }

/* 服务器端录播开关 */
.record-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #aaa;
  cursor: pointer;
}
.record-toggle input { margin: 0; }

/* 录播选项组 */
.record-options { display: flex; gap: 12px; align-items: center; }

/* 侧边栏 */
.sidebar {
  width: 260px;
  min-width: 260px;
  background: rgba(13, 13, 26, 0.95);
  border-left: 1px solid rgba(255,255,255,0.08);
  color: #fff;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}
.sidebar h3 { margin: 0; padding: 12px 16px; font-size: 14px; border-bottom: 1px solid rgba(255,255,255,0.08); }
.empty-users { color: #888; padding: 20px; text-align: center; font-size: 13px; }
.user-list { list-style: none; padding: 0; margin: 0; }
.user-item { display: flex; justify-content: space-between; align-items: center; padding: 8px 16px; border-bottom: 1px solid rgba(255,255,255,0.06); font-size: 13px; color: #ddd; }
.user-item:last-child { border-bottom: none; }

/* 按钮样式 */
.btn { padding: 5px 12px; border: 1px solid #444; border-radius: 4px; background: #1a1a2e; color: #ddd; cursor: pointer; font-size: 13px; text-decoration: none; display: inline-flex; align-items: center; gap: 4px; }
.btn:hover { background: #2a2a3e; }
.btn-primary { background: #3498db; color: #fff; border-color: #3498db; }
.btn-primary:hover { background: #2980b9; }
.btn-success { background: #27ae60; color: #fff; border-color: #27ae60; }
.btn-success:hover { background: #219a52; }
.btn-danger { background: #e74c3c; color: #fff; border-color: #e74c3c; }
.btn-danger:hover { background: #c0392b; }
.btn-sm { padding: 3px 8px; font-size: 12px; }
.btn:disabled { opacity: 0.6; cursor: not-allowed; }

.loading { text-align: center; padding: 60px; color: #999; }

/* 全屏按钮 */
.fullscreen-btn {
  position: absolute;
  bottom: 16px;
  right: 16px;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 8px;
  background: rgba(0,0,0,0.5);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s;
  z-index: 10;
}
.fullscreen-btn:hover { background: rgba(0,0,0,0.8); }
.video-container:hover .fullscreen-btn { opacity: 1; }
.fullscreen-btn svg { width: 20px; height: 20px; }

/* 分享弹窗 */
.share-modal { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 1000; }
.share-content { width: 500px; max-width: 90%; background: #fff; border-radius: 8px; padding: 24px; }
.share-content h3 { margin: 0 0 16px 0; }
.share-item { margin-bottom: 16px; }
.share-item label { display: block; font-size: 13px; color: #666; margin-bottom: 6px; }
.share-url-row { display: flex; gap: 8px; }
.share-url-row input { flex: 1; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 13px; }
</style>
