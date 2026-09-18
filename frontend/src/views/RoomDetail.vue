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
            <!-- 等待中：开始直播 -->
            <template v-if="room.status === 'waiting'">
              <button class="btn btn-success" @click="handleStartLive" :disabled="liveLoading">
                {{ liveLoading ? '开播中...' : '开始直播' }}
              </button>
              <div class="record-options">
                <label class="record-toggle">
                  <input type="checkbox" v-model="localRecordEnabled" />本地录播
                </label>
                <label class="record-toggle">
                  <input type="checkbox" v-model="serverRecordEnabled" />服务器录播
                </label>
              </div>
              <div class="quality-select">
                <label>画质:</label>
                <select v-model="selectedQuality" :disabled="room.status === 'live'">
                  <option v-for="q in VIDEO_QUALITIES" :key="q.label" :value="q">{{ q.label }}</option>
                </select>
              </div>
            </template>
            <!-- 直播中：主播操作 -->
            <template v-if="room.status === 'live'">
              <!-- 重新推流（主播刷新页面后必须重新建立推流连接） -->
              <button v-if="!publisherStreamReady" class="btn btn-success" @click="handleRePublish" :disabled="liveLoading">
                {{ liveLoading ? '推流中...' : '重新推流' }}
              </button>
              <button v-if="publisherStreamReady" class="btn btn-danger" @click="handleStopLive">结束直播</button>
            </template>
          </template>
          <button class="btn" @click="showShare = true">分享链接</button>
          <button class="btn" @click="confirmLeave">返回列表</button>
        </div>
        <!-- 设备状态提示 -->
        <div v-if="isPublisherMode && (room.status === 'waiting' || !publisherStreamReady)" class="device-status-row">
          <span class="device-tag" :class="cameraStatus">{{ cameraStatusText }}</span>
          <span class="device-tag" :class="micStatus">{{ micStatusText }}</span>
        </div>
        <!-- 屏幕共享状态 -->
        <div v-if="publisherStreamReady && shareMode !== 'camera'" class="device-status-row">
          <span class="device-tag active">{{ shareMode === 'pip' ? '屏幕共享+画中画' : '屏幕共享中' }}</span>
        </div>
      </div>

      <!-- 视频区域 -->
      <div class="room-content">
        <!-- 主播端 -->
        <div v-if="isPublisherMode" class="video-section">
          <div class="video-container card" :class="{ live: room.status === 'live' }" ref="publisherContainer">
            <video ref="publisherVideo" class="main-video" autoplay playsinline muted></video>
            <div v-if="room.status === 'waiting' && !publisherStreamReady" class="video-overlay">
              <p>点击「开始直播」开启推流</p>
            </div>
            <div v-if="room.status === 'live' && !publisherStreamReady" class="video-overlay">
              <p>点击「重新推流」建立连接</p>
            </div>
            <div v-if="room.status === 'live' && publisherStreamReady" class="live-indicator">
              <span class="dot"></span> 直播中
            </div>
            <div v-if="localRecording" class="local-record-indicator">
              <span class="record-dot"></span> 本地录制中
            </div>
            <button class="fullscreen-btn" @click="toggleFullscreen('publisher')" title="全屏">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"/>
              </svg>
            </button>
          </div>

          <!-- 主播控制栏：仅推流成功后显示 -->
          <div v-if="publisherStreamReady" class="media-controls card">
            <div class="control-group">
              <!-- 屏幕共享选择按钮 -->
              <button class="btn btn-sm btn-primary" @click="screenShareDialogMode = 'switch'; showScreenShareDialog = true">
                🖥️ 屏幕共享
              </button>
              <!-- 麦克风开关 -->
              <button class="btn btn-sm" :class="{ 'btn-primary': isMicOn }" @click="toggleMicrophone">
                <span v-if="isMicOn">🎤 麦克风开</span>
                <span v-else>🎤 麦克风关</span>
              </button>
              <!-- 本地录制 -->
              <button class="btn btn-sm" :class="{ 'btn-danger': localRecording }" @click="toggleLocalRecord">
                <span v-if="localRecording">⏹️ 停止本地录制</span>
                <span v-else>🔴 开始本地录制</span>
              </button>
              <span v-if="localRecordBlob && !converting && !mp4Blob" class="record-download">
                <a :href="localRecordUrl" :download="`${room?.id || 'live'}_${new Date().toISOString().slice(0,10)}.webm`" class="btn btn-sm btn-success">⬇️ 下载 WebM</a>
              </span>
              <span v-if="localRecordBlob && !converting && !mp4Blob" class="record-download">
                <button class="btn btn-sm btn-primary" @click="handleConvertToMp4">🎬 转码为 MP4</button>
              </span>
              <span v-if="converting" class="convert-progress">
                <span class="progress-bar"><span class="progress-fill" :style="{ width: convertProgress + '%' }"></span></span>
                <span class="progress-text">转码中 {{ convertProgress }}%</span>
              </span>
              <span v-if="mp4Blob" class="record-download">
                <a :href="mp4Url" :download="`${room?.id || 'live'}_${new Date().toISOString().slice(0,10)}.mp4`" class="btn btn-sm btn-success">⬇️ 下载 MP4</a>
              </span>
            </div>
          </div>
        </div>

        <!-- 观众端 -->
        <div v-if="isViewerMode" class="video-section">
          <div class="video-container card" ref="viewerContainer">
            <video ref="viewerVideo" class="main-video" autoplay playsinline></video>
            <div v-if="room.status === 'waiting'" class="video-overlay">
              <p>直播尚未开始，请稍候...</p>
            </div>
            <div v-if="room.status === 'live'" class="live-indicator">
              <span class="dot"></span> 直播中
            </div>
            <button class="fullscreen-btn" @click="toggleFullscreen('viewer')" title="全屏">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"/>
              </svg>
            </button>
          </div>
        </div>

        <!-- 右侧：在线观众 -->
        <div class="sidebar card">
          <!-- 统计概览 -->
          <div class="user-stats">
            <div class="stat-item host">
              <span class="stat-num">{{ categorizedUsers.hostCount }}</span>
              <span class="stat-label">主持人</span>
            </div>
            <div class="stat-item participant">
              <span class="stat-num">{{ categorizedUsers.participantCount }}</span>
              <span class="stat-label">参与人员</span>
            </div>
            <div class="stat-item total">
              <span class="stat-num">{{ categorizedUsers.totalCount }}</span>
              <span class="stat-label">总计</span>
            </div>
          </div>

          <!-- 主持人/主播区域 -->
          <div class="user-section">
            <div class="section-header host-header">
              <span class="section-icon">🎙️</span>
              <span>主持人 / 主播</span>
              <span class="section-count">({{ categorizedUsers.hostCount }})</span>
            </div>
            <div v-if="!categorizedUsers.hosts || categorizedUsers.hosts.length === 0" class="empty-users">暂无</div>
            <ul v-else class="user-list">
              <li v-for="u in categorizedUsers.hosts" :key="u.uid" class="user-item host-item">
                <span class="user-name">{{ u.username }}</span>
                <span class="user-role-tag host-tag">主播</span>
                <button v-if="canKick(u.uid)" class="btn btn-sm btn-danger" @click="handleKick(u.uid)">踢出</button>
              </li>
            </ul>
          </div>

          <!-- 参与人员区域 -->
          <div class="user-section">
            <div class="section-header participant-header">
              <span class="section-icon">👥</span>
              <span>参与人员</span>
              <span class="section-count">({{ categorizedUsers.participantCount }})</span>
            </div>
            <div v-if="!categorizedUsers.participants || categorizedUsers.participants.length === 0" class="empty-users">暂无</div>
            <ul v-else class="user-list">
              <li v-for="u in categorizedUsers.participants" :key="u.uid" class="user-item">
                <span class="user-name">{{ u.username }}</span>
                <span class="user-role-tag">{{ roleTag(u.role) }}</span>
                <button v-if="canKick(u.uid)" class="btn btn-sm btn-danger" @click="handleKick(u.uid)">踢出</button>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="loading">加载中...</div>

    <!-- 屏幕共享选择弹窗 -->
    <div v-if="showScreenShareDialog" class="share-modal" @click.self="showScreenShareDialog = false">
      <div class="share-content card dialog-card">
        <h3>{{ screenShareDialogMode === 'initial' ? '推流前选择画面模式' : '屏幕共享模式选择' }}</h3>
        <div class="screen-share-options">
          <label class="share-option" :class="{ selected: screenShareChoice === 'camera' }">
            <input type="radio" v-model="screenShareChoice" value="camera" />
            <div class="option-info">
              <strong>仅摄像头</strong>
              <span>仅展示摄像头画面</span>
            </div>
          </label>
          <label class="share-option" :class="{ selected: screenShareChoice === 'screen' }">
            <input type="radio" v-model="screenShareChoice" value="screen" />
            <div class="option-info">
              <strong>仅屏幕共享</strong>
              <span>仅共享屏幕内容（如已有摄像头则隐藏）</span>
            </div>
          </label>
          <label class="share-option" :class="{ selected: screenShareChoice === 'pip' }">
            <input type="radio" v-model="screenShareChoice" value="pip" />
            <div class="option-info">
              <strong>摄像头 + 屏幕共享（画中画）</strong>
              <span>屏幕为主画面，摄像头小窗叠加</span>
            </div>
          </label>
        </div>
        <div class="dialog-actions">
          <button class="btn btn-sm" @click="showScreenShareDialog = false">取消</button>
          <button class="btn btn-sm btn-primary" @click="applyScreenShare" :disabled="screenShareLoading">
            {{ screenShareLoading ? '处理中...' : '确认' }}
          </button>
        </div>
      </div>
    </div>

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
import { connectWsForRoom } from '../utils/ws'
import {
  startWhipPublish, startWhepPlay, createCompositeStream,
  tryGetCameraStream, tryGetMicStream, getScreenStream,
  VIDEO_QUALITIES, type VideoQuality, type WhipPublisher
} from '../utils/rtc'

type ShareMode = 'camera' | 'screen' | 'pip'

// ========== 路由 & 基础状态 ==========
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const room = ref<any>(null)
const categorizedUsers = ref<{ hosts: any[], participants: any[], hostCount: number, participantCount: number, totalCount: number }>({
  hosts: [], participants: [], hostCount: 0, participantCount: 0, totalCount: 0
})
const liveLoading = ref(false)
const publisherInfo = ref<any>(null)
const editingTitle = ref(false)
const editTitleValue = ref('')
const showShare = ref(false)
const publisherVideo = ref<HTMLVideoElement | null>(null)
const viewerVideo = ref<HTMLVideoElement | null>(null)
const publisherContainer = ref<HTMLDivElement | null>(null)
const viewerContainer = ref<HTMLDivElement | null>(null)

const selectedQuality = ref<VideoQuality>(VIDEO_QUALITIES[1])
const currentMode = computed(() => (route.query.mode as string) === 'viewer' ? 'viewer' : 'publisher')
const isPublisherMode = computed(() => currentMode.value === 'publisher')
const isViewerMode = computed(() => currentMode.value === 'viewer')
const canEdit = computed(() => {
  if (!room.value) return false
  if (authStore.isAdmin) return true
  return room.value.publisherUid === authStore.uid
})
const statusMap: Record<string, string> = { waiting: '等待中', live: '直播中', closed: '已关闭' }
const publisherLink = computed(() => (window.location.origin + window.location.pathname + '?mode=publisher'))
const viewerLink = computed(() => (window.location.origin + window.location.pathname + '?mode=viewer'))
const canKick = (targetUid: string) => {
  if (targetUid === authStore.uid) return false
  if (authStore.isAdmin) return true
  return room.value?.publisherUid === authStore.uid
}

// ========== 设备状态 ==========
const cameraStatus = ref<'idle' | 'loading' | 'ok' | 'fail'>('idle')
const micStatus = ref<'idle' | 'loading' | 'ok' | 'fail'>('idle')
const cameraStatusText = computed(() => {
  switch (cameraStatus.value) {
    case 'loading': return '摄像头检测中...'
    case 'ok': return '摄像头已就绪'
    case 'fail': return '无摄像头'
    default: return '摄像头未检测'
  }
})
const micStatusText = computed(() => {
  switch (micStatus.value) {
    case 'loading': return '麦克风检测中...'
    case 'ok': return '麦克风已就绪'
    case 'fail': return '无麦克风'
    default: return '麦克风未检测'
  }
})

// ========== 推流相关状态 ==========
let publisher: WhipPublisher | null = null
let publisherCleanup: (() => void) | null = null
const publisherStreamReady = ref(false)
const isMicOn = ref(true)
const localRecording = ref(false)
const localRecordBlob = ref<Blob | null>(null)
const localRecordUrl = ref('')
const serverRecording = ref(false)
const localRecordEnabled = ref(false)
const serverRecordEnabled = ref(false)
const converting = ref(false)
const convertProgress = ref(0)
const mp4Blob = ref<Blob | null>(null)
const mp4Url = ref('')

// 屏幕共享
const showScreenShareDialog = ref(false)
const screenShareChoice = ref<ShareMode>('pip')
const screenShareLoading = ref(false)
const shareMode = ref<ShareMode>('camera')
// 弹窗用途：'initial'=初始推流前选择，'switch'=运行中切换模式
const screenShareDialogMode = ref<'initial' | 'switch' | null>(null)
let pendingWhipUrl = '' // 初始推流时暂存 whipUrl，弹窗确认后使用
let cameraStream: MediaStream | null = null
let micStream: MediaStream | null = null
let screenStream: MediaStream | null = null
let compositeObj: ReturnType<typeof createCompositeStream> | null = null

// ========== 核心：开始直播 ==========
async function handleStartLive() {
  liveLoading.value = true

  // 1. 非阻塞检测摄像头
  cameraStatus.value = 'loading'
  if (cameraStream) {
    cameraStream.getTracks().forEach(t => t.stop())
    cameraStream = null
  }
  cameraStream = await tryGetCameraStream(selectedQuality.value)
  cameraStatus.value = cameraStream ? 'ok' : 'fail'

  // 2. 非阻塞检测麦克风
  micStatus.value = 'loading'
  if (micStream) {
    micStream.getTracks().forEach(t => t.stop())
    micStream = null
  }
  micStream = await tryGetMicStream()
  micStatus.value = micStream ? 'ok' : 'fail'

  // 3. 后端开播
  try {
    const res: any = await startLive(route.params.roomId as string, serverRecordEnabled.value)
    pendingWhipUrl = res.data.whipUrl
    await loadRoom()

    // 4. 弹出屏幕共享选择弹窗，用户确认后再推流
    screenShareDialogMode.value = 'initial'
    screenShareChoice.value = cameraStream ? 'pip' : 'screen' // 无摄像头默认仅屏幕
    showScreenShareDialog.value = true
  } catch (e: any) {
    console.error('start live error', e)
    alert('开播失败: ' + (e?.message || '未知错误'))
    liveLoading.value = false
  }
}

/** 重新推流（主播刷新页面后） */
async function handleRePublish() {
  liveLoading.value = true

  cameraStatus.value = 'loading'
  if (cameraStream) {
    cameraStream.getTracks().forEach(t => t.stop())
    cameraStream = null
  }
  cameraStream = await tryGetCameraStream(selectedQuality.value)
  cameraStatus.value = cameraStream ? 'ok' : 'fail'

  micStatus.value = 'loading'
  if (micStream) {
    micStream.getTracks().forEach(t => t.stop())
    micStream = null
  }
  micStream = await tryGetMicStream()
  micStatus.value = micStream ? 'ok' : 'fail'

  try {
    const res: any = await startLive(route.params.roomId as string, serverRecordEnabled.value)
    pendingWhipUrl = res.data.whipUrl

    // 清理旧合成器
    if (compositeObj) {
      compositeObj.destroy()
      compositeObj = null
    }
    screenStream = null
    shareMode.value = 'camera'

    // 弹出弹窗，用户确认后再推流
    screenShareDialogMode.value = 'initial'
    screenShareChoice.value = cameraStream ? 'pip' : 'screen'
    showScreenShareDialog.value = true
  } catch (e: any) {
    console.error('re-publish error', e)
    alert('重新推流失败: ' + (e?.message || '未知错误'))
    liveLoading.value = false
  }
}

/** 创建合成流并推流 */
async function publishStream(whipUrl: string) {
  if (publisherCleanup) {
    publisherCleanup()
    publisherCleanup = null
  }
  if (compositeObj) {
    compositeObj.destroy()
    compositeObj = null
  }

  // 创建合成器
  compositeObj = createCompositeStream({
    cameraStream: cameraStream,
    publisherName: authStore.username || '主播',
    quality: selectedQuality.value
  })

  // 如果有初始屏幕流，直接设置到合成器
  if (screenStream) {
    compositeObj.setScreenShareStream(screenStream)
  }

  // 提取麦克风音频轨道
  const audioTrack = micStream ? micStream.getAudioTracks()[0] || null : null

  publisher = await startWhipPublish(whipUrl, publisherVideo.value!, compositeObj, audioTrack)
  publisherCleanup = publisher.stop
  publisherStreamReady.value = true
  isMicOn.value = !!micStream
}

// ========== 屏幕共享 ==========
async function applyScreenShare() {
  showScreenShareDialog.value = false
  const choice = screenShareChoice.value
  const isInitial = screenShareDialogMode.value === 'initial'
  screenShareLoading.value = true

  try {
    // 获取屏幕流（如果需要）
    if (choice === 'screen' || choice === 'pip') {
      // 先清理旧屏幕流
      if (screenStream) {
        screenStream.getTracks().forEach(t => t.stop())
        screenStream = null
      }
      screenStream = await getScreenStream()
    } else {
      // 仅摄像头：清理屏幕流
      if (screenStream) {
        screenStream.getTracks().forEach(t => t.stop())
        screenStream = null
      }
    }

    if (isInitial) {
      // ====== 初始推流 ======
      await publishStream(pendingWhipUrl)
      shareMode.value = choice
      serverRecording.value = serverRecordEnabled.value

      if (localRecordEnabled.value && publisher) {
        publisher.startLocalRecord()
        localRecording.value = true
      }
    } else {
      // ====== 运行中切换 ======
      if (publisher) {
        if (screenStream) {
          await publisher.switchToScreenShare(screenStream)
        } else {
          await publisher.switchToCamera()
        }
      }
      shareMode.value = choice
    }
  } catch (e: any) {
    if (e?.name === 'AbortError') {
      // 用户取消了屏幕选择 → 回退
      if (isInitial) {
        // 初始推流时取消屏幕选择 → 用仅摄像头或重新弹窗
        screenShareChoice.value = 'camera'
        await retryInitialPublish()
        return
      }
    } else {
      alert('操作失败: ' + (e?.message || '未知错误'))
    }
  } finally {
    screenShareLoading.value = false
    screenShareDialogMode.value = null
    liveLoading.value = false
  }
}

/** 初始推流取消屏幕选择后的回退 */
async function retryInitialPublish() {
  // 关闭屏幕流
  if (screenStream) {
    screenStream.getTracks().forEach(t => t.stop())
    screenStream = null
  }
  shareMode.value = 'camera'
  try {
    await publishStream(pendingWhipUrl)
    serverRecording.value = serverRecordEnabled.value
    if (localRecordEnabled.value && publisher) {
      publisher.startLocalRecord()
      localRecording.value = true
    }
  } catch (e: any) {
    alert('推流失败: ' + (e?.message || '未知错误'))
  } finally {
    liveLoading.value = false
    screenShareDialogMode.value = null
  }
}

// ========== 结束直播 ==========
async function handleStopLive() {
  try {
    if (localRecording.value && publisher) {
      const blob = publisher.stopLocalRecord()
      if (blob) {
        localRecordBlob.value = blob
        localRecordUrl.value = URL.createObjectURL(blob)
      }
      localRecording.value = false
    }
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
    publisherStreamReady.value = false

    // 清理所有媒体流
    if (cameraStream) { cameraStream.getTracks().forEach(t => t.stop()); cameraStream = null }
    if (micStream) { micStream.getTracks().forEach(t => t.stop()); micStream = null }
    if (screenStream) { screenStream.getTracks().forEach(t => t.stop()); screenStream = null }
    if (compositeObj) { compositeObj.destroy(); compositeObj = null }

    cameraStatus.value = 'idle'
    micStatus.value = 'idle'

    await stopLive(route.params.roomId as string)
    await loadRoom()
  } catch (e) {
    console.error('stop live error', e)
  }
}

// ========== 麦克风控制 ==========
function toggleMicrophone() {
  isMicOn.value = !isMicOn.value
  if (publisher) {
    publisher.setAudioEnabled(isMicOn.value)
  }
}

// ========== 本地录制 ==========
function toggleLocalRecord() {
  if (!publisher) return
  if (localRecording.value) {
    const blob = publisher.stopLocalRecord()
    if (blob) {
      localRecordBlob.value = blob
      localRecordUrl.value = URL.createObjectURL(blob)
    }
    localRecording.value = false
  } else {
    publisher.startLocalRecord()
    localRecording.value = true
    localRecordBlob.value = null
    localRecordUrl.value = ''
    if (mp4Url.value) {
      URL.revokeObjectURL(mp4Url.value)
      mp4Url.value = ''
    }
    mp4Blob.value = null
  }
}

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

// ========== 通用功能 ==========
function toggleFullscreen(type: 'publisher' | 'viewer') {
  const el = type === 'publisher' ? publisherContainer.value : viewerContainer.value
  if (!el) return
  if (document.fullscreenElement) {
    document.exitFullscreen()
  } else {
    el.requestFullscreen()
  }
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
    if (res.data) {
      categorizedUsers.value = res.data
    }
  } catch (e) {
    console.error('load users error', e)
  }
}

/** 角色标签中文映射 */
function roleTag(role: string) {
  return role === 'admin' ? '管理员' : '观众'
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

// ========== 观众拉流 ==========
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

// ========== 生命周期 ==========
let wsClient: any = null

onMounted(() => {
  loadRoom()
  loadUsers()

  // 房间级 WebSocket：进入直播间建连，离开时销毁
  const token = authStore.token
  if (token) {
    wsClient = connectWsForRoom(
      token,
      route.params.roomId as string,
      (msg: any) => {
        // WS 推送的 room_user_list 已包含分类数据
        if (msg.type === 'room_user_list' && msg.data) {
          const { hosts, participants, hostCount, participantCount, totalCount } = msg.data
          categorizedUsers.value = {
            hosts: hosts || [],
            participants: participants || [],
            hostCount: hostCount || 0,
            participantCount: participantCount || 0,
            totalCount: totalCount || 0
          }
        }
      },
      () => {
        loadRoom()
      }
    )
  }

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

  // 主播模式 + 房间已直播：提示重新推流（不做自动推流，需要用户点击按钮）
  // 状态由 room.status === 'live' && !publisherStreamReady 驱动 UI 显示「重新推流」
})

onUnmounted(() => {
  // 销毁房间级 WebSocket 连接
  if (wsClient) {
    wsClient.destroy()
    wsClient = null
  }
  if (publisherCleanup) {
    publisherCleanup()
    publisherCleanup = null
  }
  if (viewerCleanup) {
    viewerCleanup()
    viewerCleanup = null
  }
  // 清理媒体流
  if (cameraStream) { cameraStream.getTracks().forEach(t => t.stop()); cameraStream = null }
  if (micStream) { micStream.getTracks().forEach(t => t.stop()); micStream = null }
  if (screenStream) { screenStream.getTracks().forEach(t => t.stop()); screenStream = null }
  if (compositeObj) { compositeObj.destroy(); compositeObj = null }
  // URL 清理
  if (localRecordUrl.value) URL.revokeObjectURL(localRecordUrl.value)
  if (mp4Url.value) URL.revokeObjectURL(mp4Url.value)
})
</script>

<style scoped>
/* === 全局直播布局 === */
.room-detail-page {
  width: 100%;
  height: 100%;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: #0d0d1a;
}
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

/* 设备状态提示行 */
.device-status-row {
  width: 100%;
  display: flex;
  gap: 8px;
  padding-top: 4px;
}
.device-tag {
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 11px;
  background: rgba(255,255,255,0.05);
  color: #888;
  border: 1px solid rgba(255,255,255,0.1);
  transition: all 0.3s;
}
.device-tag.ok { color: #27ae60; border-color: #27ae60; }
.device-tag.fail { color: #e74c3c; border-color: #e74c3c; }
.device-tag.loading { color: #f39c12; border-color: #f39c12; animation: pulse 1s infinite; }
.device-tag.active { color: #3498db; border-color: #3498db; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.5; } }

/* 主内容区 */
.room-content {
  flex: 1;
  display: flex;
  min-height: 0;
  gap: 0;
  overflow: hidden;
}
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
.convert-progress { display: inline-flex; align-items: center; gap: 8px; font-size: 13px; color: #666; }
.progress-bar { width: 100px; height: 6px; background: #e0e0e0; border-radius: 3px; overflow: hidden; }
.progress-fill { display: block; height: 100%; background: #3498db; border-radius: 3px; transition: width 0.3s; }
.progress-text { white-space: nowrap; }
.record-options { display: flex; gap: 12px; align-items: center; }
.record-toggle { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: #aaa; cursor: pointer; }
.record-toggle input { margin: 0; }

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
/* 统计概览 */
.user-stats {
  display: flex;
  gap: 0;
  border-bottom: 1px solid rgba(255,255,255,0.08);
}
.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 4px;
  border-right: 1px solid rgba(255,255,255,0.06);
}
.stat-item:last-child { border-right: none; }
.stat-num {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.2;
}
.stat-item.host .stat-num { color: #f39c12; }
.stat-item.participant .stat-num { color: #3498db; }
.stat-item.total .stat-num { color: #27ae60; }
.stat-label {
  font-size: 10px;
  color: #888;
  margin-top: 2px;
}
/* 用户分区 */
.user-section {
  border-bottom: 1px solid rgba(255,255,255,0.06);
}
.user-section:last-child { border-bottom: none; }
.section-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  font-size: 12px;
  font-weight: 500;
  background: rgba(255,255,255,0.03);
}
.section-icon { font-size: 14px; }
.section-count { color: #888; font-weight: 400; }
.host-header { color: #f39c12; }
.participant-header { color: #3498db; }
.empty-users { color: #888; padding: 12px; text-align: center; font-size: 12px; }
.user-list { list-style: none; padding: 0; margin: 0; }
.user-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-bottom: 1px solid rgba(255,255,255,0.04);
  font-size: 12px;
  color: #ddd;
}
.user-item:last-child { border-bottom: none; }
.user-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-role-tag {
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 10px;
  background: rgba(255,255,255,0.1);
  color: #aaa;
  flex-shrink: 0;
}
.host-tag { background: rgba(243, 156, 18, 0.2); color: #f39c12; }
.host-item { background: rgba(243, 156, 18, 0.04); }

/* 按钮 */
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
  bottom: 16px; right: 16px;
  width: 40px; height: 40px;
  border: none; border-radius: 8px;
  background: rgba(0,0,0,0.5); color: #fff;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  opacity: 0; transition: opacity 0.3s; z-index: 10;
}
.fullscreen-btn:hover { background: rgba(0,0,0,0.8); }
.video-container:hover .fullscreen-btn { opacity: 1; }
.fullscreen-btn svg { width: 20px; height: 20px; }

/* 弹窗 & 遮罩 */
.share-modal {
  position: fixed; top: 0; left: 0; right: 0; bottom: 0;
  background: rgba(0,0,0,0.5);
  display: flex; align-items: center; justify-content: center; z-index: 1000;
}
.share-content {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  max-width: 90%;
}
.share-content h3 { margin: 0 0 16px 0; }
.share-item { margin-bottom: 16px; }
.share-item label { display: block; font-size: 13px; color: #666; margin-bottom: 6px; }
.share-url-row { display: flex; gap: 8px; }
.share-url-row input { flex: 1; padding: 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 13px; }

/* 屏幕共享弹窗 */
.dialog-card { width: 420px; }
.screen-share-options {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 16px;
}
.share-option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
  border: 2px solid #e0e0e0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.share-option:hover { border-color: #3498db; background: #f0f8ff; }
.share-option.selected { border-color: #3498db; background: #e8f4fd; }
.share-option input[type="radio"] { margin-top: 2px; flex-shrink: 0; }
.option-info { display: flex; flex-direction: column; gap: 2px; }
.option-info strong { font-size: 14px; color: #333; }
.option-info span { font-size: 12px; color: #888; }
.dialog-actions { display: flex; justify-content: flex-end; gap: 8px; }
</style>