/**
 * WebRTC WHIP/WHEP 工具封装 + 本地录播
 * WHIP: WebRTC-HTTP Ingestion Protocol (推流)
 * WHEP: WebRTC-HTTP Egress Protocol (拉流)
 */

export interface WhipPublisher {
  pc: RTCPeerConnection
  stream: MediaStream
  stop: () => void
  switchToScreenShare: () => Promise<void>
  switchToCamera: () => Promise<void>
  setAudioEnabled: (enabled: boolean) => void
  startLocalRecord: () => void
  stopLocalRecord: () => Blob | null
  isScreenSharing: () => boolean
}

export interface MediaDeviceError {
  type: 'camera' | 'microphone'
  message: string
}

/** 画质配置 */
export interface VideoQuality {
  label: string
  width: number
  height: number
}

export const VIDEO_QUALITIES: VideoQuality[] = [
  { label: '流畅', width: 640, height: 360 },
  { label: '标清', width: 1280, height: 720 },
  { label: '高清', width: 1920, height: 1080 }
]

/** 检测设备权限，返回可用的媒体流 */
export async function getMediaStream(
  options: { video?: boolean; audio?: boolean; screen?: boolean; quality?: VideoQuality }
): Promise<{ stream: MediaStream; error?: MediaDeviceError }> {
  let stream: MediaStream
  let error: MediaDeviceError | undefined

  const quality = options.quality || VIDEO_QUALITIES[1] // 默认标清

  try {
    if (options.screen) {
      stream = await navigator.mediaDevices.getDisplayMedia({
        video: { cursor: 'always' } as any,
        audio: options.audio
      })
    } else {
      const videoConstraint = options.video
        ? {
            width: { ideal: quality.width, max: quality.width },
            height: { ideal: quality.height, max: quality.height },
            facingMode: 'user'
          }
        : false
      stream = await navigator.mediaDevices.getUserMedia({
        video: videoConstraint as any,
        audio: options.audio ? { echoCancellation: true, noiseSuppression: true } : false
      })
    }
  } catch (err: any) {
    const errName = err?.name || ''
    if (errName === 'NotAllowedError' || errName === 'PermissionDeniedError') {
      error = {
        type: options.screen ? 'camera' : 'camera',
        message: '用户拒绝了媒体权限，请检查浏览器权限设置'
      }
    } else if (errName === 'NotFoundError' || errName === 'DevicesNotFoundError') {
      error = {
        type: options.screen ? 'camera' : 'camera',
        message: '未检测到可用的摄像头/麦克风设备'
      }
    } else if (errName === 'NotReadableError' || errName === 'TrackStartError') {
      error = {
        type: options.screen ? 'camera' : 'camera',
        message: '设备被其他应用占用，请关闭其他使用摄像头的应用后重试'
      }
    } else {
      error = {
        type: options.screen ? 'camera' : 'camera',
        message: `设备异常: ${err?.message || '未知错误'}`
      }
    }
    throw error
  }

  return { stream, error }
}

export interface CompositeLayoutOptions {
  publisherName?: string
}

/**
 * 创建画中画合成流（摄像头 + 屏幕共享）
 * 返回合成后的 MediaStream 和辅助函数
 */
export function createCompositeStream(
  cameraStream: MediaStream,
  layoutOptions: CompositeLayoutOptions = {}
): {
  compositeStream: MediaStream
  setScreenShareStream: (screenStream: MediaStream | null) => void
  destroy: () => void
} {
  const { publisherName = '主播' } = layoutOptions

  const canvas = document.createElement('canvas')
  canvas.width = 1920
  canvas.height = 1080
  const ctx = canvas.getContext('2d', { alpha: false })!

  // 画布布局配置（参考主流直播平台）
  const LAYOUT = {
    pipWidth: 320,
    pipHeight: 240,
    pipMarginX: 24,
    pipMarginY: 88, // 底部留空间给信息条
    pipRadius: 16,
    pipBorderWidth: 3,
    pipBorderColor: '#ffffff',
    pipShadowColor: 'rgba(0,0,0,0.5)',
    pipShadowBlur: 16,
    pipShadowOffsetX: 0,
    pipShadowOffsetY: 4
  }

  const cameraVideo = document.createElement('video')
  cameraVideo.autoplay = true
  cameraVideo.muted = true

  let screenVideo: HTMLVideoElement | null = null
  let animFrameId: number

  // 设置摄像头视频源
  cameraVideo.srcObject = cameraStream
  cameraVideo.play()

  // 辅助函数：填充圆角矩形
  function fillRoundRect(
    context: CanvasRenderingContext2D,
    x: number, y: number,
    w: number, h: number,
    r: number,
    fillStyle: string
  ) {
    context.save()
    context.fillStyle = fillStyle
    drawRoundRect(context, x, y, w, h, r)
    context.fill()
    context.restore()
  }
  function drawRoundRect(
    context: CanvasRenderingContext2D,
    x: number, y: number,
    w: number, h: number,
    r: number
  ) {
    context.beginPath()
    context.moveTo(x + r, y)
    context.lineTo(x + w - r, y)
    context.arcTo(x + w, y, x + w, y + r, r)
    context.lineTo(x + w, y + h - r)
    context.arcTo(x + w, y + h, x + w - r, y + h, r)
    context.lineTo(x + r, y + h)
    context.arcTo(x, y + h, x, y + h - r, r)
    context.lineTo(x, y + r)
    context.arcTo(x, y, x + r, y, r)
    context.closePath()
  }

  // 辅助函数：等比填充绘制
  function drawCover(
    context: CanvasRenderingContext2D,
    video: HTMLVideoElement,
    cx: number, cy: number, cw: number, ch: number
  ) {
    const vw = video.videoWidth || cw
    const vh = video.videoHeight || ch
    const vratio = vw / vh
    const cratio = cw / ch

    let sx = 0, sy = 0, sw = vw, sh = vh
    if (vratio > cratio) {
      // 视频更宽，截取左右
      sw = vh * cratio
      sx = (vw - sw) / 2
    } else if (vratio < cratio) {
      // 视频更高，截取上下
      sh = vw / cratio
      sy = (vh - sh) / 2
    }

    context.drawImage(video, sx, sy, sw, sh, cx, cy, cw, ch)
  }

  function draw() {
    const hasScreen = screenVideo && screenVideo.readyState >= 2
    const hasCamera = cameraVideo.readyState >= 2

    // 1. 背景
    ctx.fillStyle = '#0a0a1a'
    ctx.fillRect(0, 0, canvas.width, canvas.height)

    // 2. 主画面（屏幕共享或摄像头全屏）
    if (hasScreen) {
      drawCover(ctx, screenVideo!, 0, 0, canvas.width, canvas.height)
    } else if (hasCamera) {
      drawCover(ctx, cameraVideo, 0, 0, canvas.width, canvas.height)
    }

    // 3. 底部信息条（当有屏幕共享时显示）
    if (hasScreen) {
      // 底部渐变遮罩
      const gradient = ctx.createLinearGradient(0, canvas.height - 90, 0, canvas.height)
      gradient.addColorStop(0, 'rgba(0,0,0,0)')
      gradient.addColorStop(1, 'rgba(0,0,0,0.75)')
      ctx.fillStyle = gradient
      ctx.fillRect(0, canvas.height - 90, canvas.width, 90)

      // 分隔线
      ctx.strokeStyle = 'rgba(255,255,255,0.1)'
      ctx.lineWidth = 1
      ctx.beginPath()
      ctx.moveTo(0, canvas.height - 64)
      ctx.lineTo(canvas.width, canvas.height - 64)
      ctx.stroke()

      // 主播名称
      ctx.fillStyle = '#ffffff'
      ctx.font = 'bold 20px "Microsoft YaHei", "PingFang SC", sans-serif'
      ctx.textBaseline = 'middle'
      ctx.shadowColor = 'rgba(0,0,0,0.5)'
      ctx.shadowBlur = 4
      ctx.fillText(publisherName, 24, canvas.height - 32)
      ctx.shadowBlur = 0

      // LIVE 标签
      const liveX = canvas.width - 110
      const liveY = canvas.height - 44
      fillRoundRect(ctx, liveX, liveY, 86, 28, 4, 'rgba(255, 68, 68, 0.9)')
      ctx.fillStyle = '#ffffff'
      ctx.font = 'bold 12px "Microsoft YaHei", sans-serif'
      ctx.textAlign = 'center'
      ctx.textBaseline = 'middle'
      ctx.fillText('● LIVE', liveX + 43, liveY + 14)
      ctx.textAlign = 'left'
    }

    // 4. 画中画：摄像头小窗口（右下角，圆角边框）
    if (hasScreen && hasCamera) {
      const pipX = canvas.width - LAYOUT.pipWidth - LAYOUT.pipMarginX
      const pipY = canvas.height - LAYOUT.pipHeight - LAYOUT.pipMarginY

      // 阴影
      ctx.save()
      ctx.shadowColor = LAYOUT.pipShadowColor
      ctx.shadowBlur = LAYOUT.pipShadowBlur
      ctx.shadowOffsetX = LAYOUT.pipShadowOffsetX
      ctx.shadowOffsetY = LAYOUT.pipShadowOffsetY
      drawRoundRect(ctx, pipX, pipY, LAYOUT.pipWidth, LAYOUT.pipHeight, LAYOUT.pipRadius)
      ctx.clip()
      ctx.shadowColor = 'transparent'
      drawCover(ctx, cameraVideo, pipX, pipY, LAYOUT.pipWidth, LAYOUT.pipHeight)
      ctx.restore()

      // 边框
      ctx.save()
      ctx.strokeStyle = LAYOUT.pipBorderColor
      ctx.lineWidth = LAYOUT.pipBorderWidth
      drawRoundRect(ctx, pipX, pipY, LAYOUT.pipWidth, LAYOUT.pipHeight, LAYOUT.pipRadius)
      ctx.stroke()
      ctx.restore()

      // "摄像头" 标签
      fillRoundRect(ctx, pipX + 10, pipY + 10, 60, 22, 3, 'rgba(0,0,0,0.55)')
      ctx.fillStyle = '#ffffff'
      ctx.font = '11px "Microsoft YaHei", sans-serif'
      ctx.textBaseline = 'middle'
      ctx.textAlign = 'center'
      ctx.fillText('摄像头', pipX + 40, pipY + 21)
      ctx.textAlign = 'left'

      // 右上角在线绿点
      ctx.beginPath()
      ctx.arc(pipX + LAYOUT.pipWidth - 16, pipY + 16, 6, 0, Math.PI * 2)
      ctx.fillStyle = '#4ade80'
      ctx.fill()
      ctx.strokeStyle = '#ffffff'
      ctx.lineWidth = 2
      ctx.stroke()
    }

    animFrameId = requestAnimationFrame(draw)
  }

  draw()

  // 获取麦克风和屏幕共享的音频轨道
  const audioTracks: MediaStreamTrack[] = []
  cameraStream.getAudioTracks().forEach(t => audioTracks.push(t))

  // 从 Canvas 获取视频流
  const compositeStream = canvas.captureStream(30)

  // 添加音频轨道
  audioTracks.forEach(track => compositeStream.addTrack(track))

  return {
    compositeStream,
    setScreenShareStream: (screenStream: MediaStream | null) => {
      if (screenStream) {
        if (screenVideo) {
          screenVideo.srcObject = screenStream
        } else {
          screenVideo = document.createElement('video')
          screenVideo.autoplay = true
          screenVideo.muted = true
          screenVideo.srcObject = screenStream
          screenVideo.play()
        }
      } else {
        if (screenVideo) {
          screenVideo.srcObject = null
          screenVideo = null
        }
      }
    },
    destroy: () => {
      cancelAnimationFrame(animFrameId)
      cameraVideo.srcObject = null
      if (screenVideo) {
        screenVideo.srcObject = null
      }
    }
  }
}

/**
 * 本地录播管理器
 * 
 * 注意：浏览器 MediaRecorder API 原生主要支持 WebM 格式（VP8/VP9）。
 * MP4 (H.264) 支持非常有限：
 * - Chrome 在部分平台上可能支持 video/mp4，但兼容性差
 * - Firefox / Safari 基本不支持
 * - 即使支持，录制的 MP4 也可能缺少 moov atom 导致无法播放
 * 
 * 如果需要可靠的 MP4 输出，建议：
 * 1. 前端录制 WebM -> 上传到后端 -> ffmpeg 转码为 MP4
 * 2. 使用 ffmpeg.wasm 在浏览器端转码（体积大，约 25MB）
 */
export class LocalRecorder {
  private recorder: MediaRecorder | null = null
  private chunks: Blob[] = []
  private recording = false
  private mimeType = 'video/webm'

  start(stream: MediaStream) {
    if (this.recording) return
    this.chunks = []

    // 尝试 MP4 -> WebM VP9 -> WebM VP8 -> 默认 WebM
    if (MediaRecorder.isTypeSupported('video/mp4;codecs=avc1')) {
      this.mimeType = 'video/mp4;codecs=avc1'
    } else if (MediaRecorder.isTypeSupported('video/webm;codecs=vp9')) {
      this.mimeType = 'video/webm;codecs=vp9'
    } else if (MediaRecorder.isTypeSupported('video/webm;codecs=vp8')) {
      this.mimeType = 'video/webm;codecs=vp8'
    } else {
      this.mimeType = 'video/webm'
    }

    this.recorder = new MediaRecorder(stream, { mimeType: this.mimeType })
    this.recorder.ondataavailable = (e) => {
      if (e.data.size > 0) {
        this.chunks.push(e.data)
      }
    }
    this.recorder.start(1000) // 每秒一个 chunk
    this.recording = true
  }

  stop(): Blob | null {
    if (!this.recording || !this.recorder) return null
    this.recorder.stop()
    this.recording = false
    const blob = new Blob(this.chunks, { type: this.mimeType })
    this.chunks = []
    return blob
  }

  isRecording(): boolean {
    return this.recording
  }

  getMimeType(): string {
    return this.mimeType
  }

  /** 下载录制的视频 */
  download(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    // 根据实际 mime type 确定扩展名
    const ext = this.mimeType.includes('mp4') ? '.mp4' : '.webm'
    a.download = filename.endsWith(ext) ? filename : filename + ext
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }
}

/**
 * WHIP 推流：获取媒体并推流到 SRS
 * @param whipUrl 推流地址
 * @param videoElement 用于预览的视频元素
 * @param options 配置项
 */
export async function startWhipPublish(
  whipUrl: string,
  videoElement: HTMLVideoElement,
  options: {
    enableCamera?: boolean
    enableMicrophone?: boolean
    enableScreenShare?: boolean
    publisherName?: string
    quality?: import('./rtc').VideoQuality
  } = {}
): Promise<WhipPublisher> {
  const { enableCamera = true, enableMicrophone = true, enableScreenShare = false, publisherName = '主播', quality } = options

  // 1. 获取摄像头流
  let cameraStream: MediaStream
  try {
    const result = await getMediaStream({
      video: enableCamera,
      audio: enableMicrophone,
      quality
    })
    cameraStream = result.stream
  } catch (err: any) {
    throw err
  }

  // 2. 创建合成流
  const composite = createCompositeStream(cameraStream, { publisherName })

  // 3. 显示预览
  videoElement.srcObject = composite.compositeStream
  await videoElement.play()

  // 4. 创建 RTCPeerConnection
  const pc = new RTCPeerConnection()

  // 添加合成流的轨道
  composite.compositeStream.getTracks().forEach(track => {
    pc.addTrack(track, composite.compositeStream)
  })

  // 5. 创建 offer 并发送
  const offer = await pc.createOffer()
  await pc.setLocalDescription(offer)
  await waitForIceGathering(pc)

  const response = await fetch(whipUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/sdp' },
    body: pc.localDescription?.sdp
  })

  if (!response.ok) {
    throw new Error(`WHIP publish failed: ${response.status}`)
  }

  const answerSdp = await response.text()
  await pc.setRemoteDescription(new RTCSessionDescription({ type: 'answer', sdp: answerSdp }))

  // 6. 本地录播实例
  const localRecorder = new LocalRecorder()

  let isScreenSharing = false

  return {
    pc,
    stream: composite.compositeStream,
    stop: () => {
      composite.compositeStream.getTracks().forEach(t => t.stop())
      cameraStream.getTracks().forEach(t => t.stop())
      composite.destroy()
      pc.close()
    },
    switchToScreenShare: async () => {
      const screenResult = await getMediaStream({ screen: true, audio: enableMicrophone })
      composite.setScreenShareStream(screenResult.stream)
      isScreenSharing = true
    },
    switchToCamera: async () => {
      composite.setScreenShareStream(null)
      isScreenSharing = false
    },
    setAudioEnabled: (enabled: boolean) => {
      composite.compositeStream.getAudioTracks().forEach(track => {
        track.enabled = enabled
      })
    },
    startLocalRecord: () => {
      localRecorder.start(composite.compositeStream)
    },
    stopLocalRecord: () => {
      return localRecorder.stop()
    },
    isScreenSharing: () => isScreenSharing
  }
}

/**
 * WHEP 拉流：从 SRS 拉流并播放
 * @param whepUrl 拉流地址
 * @param videoElement 用于播放的视频元素
 */
export async function startWhepPlay(whepUrl: string, videoElement: HTMLVideoElement): Promise<() => void> {
  const pc = new RTCPeerConnection()

  pc.ontrack = (event) => {
    if (event.streams && event.streams[0]) {
      videoElement.srcObject = event.streams[0]
    }
  }

  // 1. 获取 offer
  const offerResponse = await fetch(whepUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/sdp' }
  })

  if (!offerResponse.ok) {
    throw new Error(`WHEP play failed: ${offerResponse.status}`)
  }

  const offerSdp = await offerResponse.text()
  await pc.setRemoteDescription(new RTCSessionDescription({ type: 'offer', sdp: offerSdp }))

  // 2. 创建 answer
  const answer = await pc.createAnswer()
  await pc.setLocalDescription(answer)
  await waitForIceGathering(pc)

  // 3. 发送 answer
  const patchResponse = await fetch(whepUrl, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/sdp' },
    body: pc.localDescription?.sdp
  })

  if (!patchResponse.ok) {
    throw new Error(`WHEP patch failed: ${patchResponse.status}`)
  }

  return () => {
    pc.close()
  }
}

/**
 * 等待 ICE gathering 完成
 */
function waitForIceGathering(pc: RTCPeerConnection): Promise<void> {
  return new Promise((resolve) => {
    if (pc.iceGatheringState === 'complete') {
      resolve()
      return
    }
    const check = setInterval(() => {
      if (pc.iceGatheringState === 'complete') {
        clearInterval(check)
        resolve()
      }
    }, 200)
    setTimeout(() => {
      clearInterval(check)
      resolve()
    }, 3000)
  })
}
