/**
 * WebRTC WHIP/WHEP 工具封装 + 本地录播 + 画中画合成
 * WHIP: WebRTC-HTTP Ingestion Protocol (推流)
 * WHEP: WebRTC-HTTP Egress Protocol (拉流)
 */

export interface WhipPublisher {
  pc: RTCPeerConnection
  stream: MediaStream
  stop: () => void
  /** 切换到屏幕共享（设置屏幕流到合成器） */
  switchToScreenShare: (screenStream: MediaStream) => Promise<void>
  /** 切换回仅摄像头 */
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

/** 尝试获取摄像头流（不抛异常，失败返回 null） */
export async function tryGetCameraStream(quality?: VideoQuality): Promise<MediaStream | null> {
  try {
    const q = quality || VIDEO_QUALITIES[1]
    return await navigator.mediaDevices.getUserMedia({
      video: {
        width: { ideal: q.width, max: q.width },
        height: { ideal: q.height, max: q.height },
        facingMode: 'user'
      },
      audio: false
    })
  } catch {
    return null
  }
}

/** 尝试获取麦克风流（不抛异常，失败返回 null） */
export async function tryGetMicStream(): Promise<MediaStream | null> {
  try {
    return await navigator.mediaDevices.getUserMedia({
      video: false,
      audio: { echoCancellation: true, noiseSuppression: true }
    })
  } catch {
    return null
  }
}

/** 获取屏幕共享流（需要用户手势触发） */
export async function getScreenStream(): Promise<MediaStream> {
  return navigator.mediaDevices.getDisplayMedia({
    video: { cursor: 'always' } as any,
    audio: false
  })
}

/**
 * 创建画中画合成流（摄像头 + 屏幕共享）
 * 支持仅摄像头、仅屏幕、摄像头+屏幕(画中画)三种模式
 */
export function createCompositeStream(
  options: {
    cameraStream?: MediaStream | null
    publisherName?: string
    quality?: VideoQuality
  } = {}
): {
  compositeStream: MediaStream
  setScreenShareStream: (screenStream: MediaStream | null) => void
  destroy: () => void
} {
  const { publisherName = '主播', quality } = options

  const w = quality?.width || 1920
  const h = quality?.height || 1080

  const canvas = document.createElement('canvas')
  canvas.width = w
  canvas.height = h
  const ctx = canvas.getContext('2d', { alpha: false })!

  // 画中画布局配置
  const LAYOUT = {
    pipWidth: Math.round(w * 0.167),   // 约320@1920
    pipHeight: Math.round(h * 0.222),  // 约240@1080
    pipMarginX: Math.round(w * 0.0125),
    pipMarginY: Math.round(h * 0.081),
    pipRadius: 16,
    pipBorderWidth: 3,
    pipBorderColor: '#ffffff'
  }

  // 摄像头视频元素
  let cameraVideo: HTMLVideoElement | null = null
  if (options.cameraStream) {
    cameraVideo = document.createElement('video')
    cameraVideo.autoplay = true
    cameraVideo.muted = true
    cameraVideo.srcObject = options.cameraStream
    cameraVideo.play()
  }

  // 屏幕共享视频元素
  let screenVideo: HTMLVideoElement | null = null
  let animFrameId: number

  function fillRoundRect(
    context: CanvasRenderingContext2D,
    x: number, y: number,
    cw: number, ch: number, r: number, fillStyle: string
  ) {
    context.save()
    context.fillStyle = fillStyle
    drawRoundRect(context, x, y, cw, ch, r)
    context.fill()
    context.restore()
  }

  function drawRoundRect(
    context: CanvasRenderingContext2D,
    x: number, y: number,
    cw: number, ch: number, r: number
  ) {
    context.beginPath()
    context.moveTo(x + r, y)
    context.lineTo(x + cw - r, y)
    context.arcTo(x + cw, y, x + cw, y + r, r)
    context.lineTo(x + cw, y + ch - r)
    context.arcTo(x + cw, y + ch, x + cw - r, y + ch, r)
    context.lineTo(x + r, y + ch)
    context.arcTo(x, y + ch, x, y + ch - r, r)
    context.lineTo(x, y + r)
    context.arcTo(x, y, x + r, y, r)
    context.closePath()
  }

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
      sw = vh * cratio
      sx = (vw - sw) / 2
    } else if (vratio < cratio) {
      sh = vw / cratio
      sy = (vh - sh) / 2
    }
    context.drawImage(video, sx, sy, sw, sh, cx, cy, cw, ch)
  }

  function draw() {
    const hasScreen = screenVideo && screenVideo.readyState >= 2
    const hasCamera = cameraVideo && cameraVideo.readyState >= 2

    // 背景
    ctx.fillStyle = '#0a0a1a'
    ctx.fillRect(0, 0, canvas.width, canvas.height)

    // 主画面：屏幕共享优先，否则摄像头
    if (hasScreen) {
      drawCover(ctx, screenVideo!, 0, 0, canvas.width, canvas.height)
    } else if (hasCamera) {
      drawCover(ctx, cameraVideo!, 0, 0, canvas.width, canvas.height)
    }

    // 底部信息条（屏幕共享时显示）
    if (hasScreen) {
      const gradH = Math.round(h * 0.083)
      const sepY = canvas.height - Math.round(h * 0.059)
      ctx.fillStyle = createGradient(ctx, canvas.height, gradH)
      ctx.fillRect(0, canvas.height - gradH, canvas.width, gradH)
      ctx.strokeStyle = 'rgba(255,255,255,0.1)'
      ctx.lineWidth = 1
      ctx.beginPath()
      ctx.moveTo(0, sepY)
      ctx.lineTo(canvas.width, sepY)
      ctx.stroke()
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
      ctx.fillText('• LIVE', liveX + 43, liveY + 14)
      ctx.textAlign = 'left'
    }

    // 画中画：摄像头小窗（右下角）
    if (hasScreen && hasCamera) {
      const pipX = canvas.width - LAYOUT.pipWidth - LAYOUT.pipMarginX
      const pipY = canvas.height - LAYOUT.pipHeight - LAYOUT.pipMarginY
      // 阴影
      ctx.save()
      ctx.shadowColor = 'rgba(0,0,0,0.5)'
      ctx.shadowBlur = 16
      ctx.shadowOffsetX = 0
      ctx.shadowOffsetY = 4
      drawRoundRect(ctx, pipX, pipY, LAYOUT.pipWidth, LAYOUT.pipHeight, LAYOUT.pipRadius)
      ctx.clip()
      ctx.shadowColor = 'transparent'
      drawCover(ctx, cameraVideo!, pipX, pipY, LAYOUT.pipWidth, LAYOUT.pipHeight)
      ctx.restore()
      // 边框
      ctx.save()
      ctx.strokeStyle = LAYOUT.pipBorderColor
      ctx.lineWidth = LAYOUT.pipBorderWidth
      drawRoundRect(ctx, pipX, pipY, LAYOUT.pipWidth, LAYOUT.pipHeight, LAYOUT.pipRadius)
      ctx.stroke()
      ctx.restore()
      // 标签
      fillRoundRect(ctx, pipX + 10, pipY + 10, 60, 22, 3, 'rgba(0,0,0,0.55)')
      ctx.fillStyle = '#ffffff'
      ctx.font = '11px "Microsoft YaHei", sans-serif'
      ctx.textBaseline = 'middle'
      ctx.textAlign = 'center'
      ctx.fillText('摄像头', pipX + 40, pipY + 21)
      ctx.textAlign = 'left'
      // 在线绿点
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

  function createGradient(context: CanvasRenderingContext2D, ch: number, gradH: number) {
    const gradient = context.createLinearGradient(0, ch - gradH, 0, ch)
    gradient.addColorStop(0, 'rgba(0,0,0,0)')
    gradient.addColorStop(1, 'rgba(0,0,0,0.75)')
    return gradient
  }

  draw()

  // 从 Canvas 获取视频流
  const compositeStream = canvas.captureStream(30)

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
      if (cameraVideo) {
        cameraVideo.srcObject = null
      }
      if (screenVideo) {
        screenVideo.srcObject = null
      }
    }
  }
}

/**
 * 本地录播管理器
 */
export class LocalRecorder {
  private recorder: MediaRecorder | null = null
  private chunks: Blob[] = []
  private recording = false
  private mimeType = 'video/webm'

  start(stream: MediaStream) {
    if (this.recording) return
    this.chunks = []
    if (MediaRecorder.isTypeSupported('video/mp4;codecs=avc1')) {
      this.mimeType = 'video/mp4;codecs=avc1'
    } else if (MediaRecorder.isTypeSupported('video/webm;codecs=vp9')) {
      this.mimeType = 'video/webm;codecs=vp9'
    } else if (MediaRecorder.isTypeSupported('video/webm;codecs=vp8')) {
      this.mimeType = 'video/webm;codecs=vp8'
    }
    this.recorder = new MediaRecorder(stream, { mimeType: this.mimeType })
    this.recorder.ondataavailable = (e) => {
      if (e.data.size > 0) this.chunks.push(e.data)
    }
    this.recorder.start(1000)
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

  download(blob: Blob, filename: string) {
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const ext = this.mimeType.includes('mp4') ? '.mp4' : '.webm'
    a.download = filename.endsWith(ext) ? filename : filename + ext
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }
}

/**
 * WHIP 推流
 * @param whipUrl 推流地址
 * @param videoElement 预览用的 video 元素
 * @param composite 合成流管理器（由 createCompositeStream 返回）
 * @param audioTrack 麦克风音频轨道（可选，无音频可不传）
 */
export async function startWhipPublish(
  whipUrl: string,
  videoElement: HTMLVideoElement,
  composite: ReturnType<typeof createCompositeStream>,
  audioTrack?: MediaStreamTrack | null
): Promise<WhipPublisher> {
  const compositeStream = composite.compositeStream

  // 添加音频轨道到合成流
  if (audioTrack) {
    compositeStream.addTrack(audioTrack)
  }

  // 预览
  videoElement.srcObject = compositeStream
  await videoElement.play()

  // 创建 PeerConnection 并推流
  const pc = new RTCPeerConnection()
  compositeStream.getTracks().forEach(track => {
    pc.addTrack(track, compositeStream)
  })

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

  const localRecorder = new LocalRecorder()
  let isScreenSharing = false

  return {
    pc,
    stream: compositeStream,
    stop: () => {
      compositeStream.getTracks().forEach(t => t.stop())
      composite.destroy()
      pc.close()
    },
    switchToScreenShare: async (screenStream: MediaStream) => {
      composite.setScreenShareStream(screenStream)
      isScreenSharing = true
    },
    switchToCamera: async () => {
      composite.setScreenShareStream(null)
      isScreenSharing = false
    },
    setAudioEnabled: (enabled: boolean) => {
      compositeStream.getAudioTracks().forEach(track => {
        track.enabled = enabled
      })
    },
    startLocalRecord: () => {
      localRecorder.start(compositeStream)
    },
    stopLocalRecord: () => {
      return localRecorder.stop()
    },
    isScreenSharing: () => isScreenSharing
  }
}

/**
 * WHEP 拉流：从 SRS 拉流并播放
 */
export async function startWhepPlay(whepUrl: string, videoElement: HTMLVideoElement): Promise<() => void> {
  const pc = new RTCPeerConnection()

  pc.ontrack = (event) => {
    if (event.streams && event.streams[0]) {
      videoElement.srcObject = event.streams[0]
    }
  }

  const offerResponse = await fetch(whepUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/sdp' }
  })
  if (!offerResponse.ok) {
    throw new Error(`WHEP play failed: ${offerResponse.status}`)
  }
  const offerSdp = await offerResponse.text()
  await pc.setRemoteDescription(new RTCSessionDescription({ type: 'offer', sdp: offerSdp }))

  const answer = await pc.createAnswer()
  await pc.setLocalDescription(answer)
  await waitForIceGathering(pc)

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