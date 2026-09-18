import { FFmpeg } from '@ffmpeg/ffmpeg'

// 通过 Vite proxy 代理到 CDN，变同源请求，规避跨域隔离/CSP 问题
const CORE_URL = '/ffmpeg-core/esm/ffmpeg-core.js'
const WASM_URL = '/ffmpeg-core/umd/ffmpeg-core.wasm'

let ffmpegInstance: FFmpeg | null = null
let loadPromise: Promise<FFmpeg> | null = null

/**
 * 获取 ffmpeg.wasm 实例（单例）
 */
export async function getFFmpeg(): Promise<FFmpeg> {
  if (ffmpegInstance?.loaded) return ffmpegInstance
  if (loadPromise) return loadPromise

  loadPromise = (async () => {
    const ffmpeg = new FFmpeg()

    ffmpeg.on('log', ({ message }: { message: string }) => {
      console.log('[ffmpeg]', message)
    })

    await ffmpeg.load({ coreURL: CORE_URL, wasmURL: WASM_URL })

    return ffmpeg
  })()

  ffmpegInstance = await loadPromise
  loadPromise = null
  return ffmpegInstance
}

/**
 * 将 WebM Blob 转码为 MP4 Blob
 * @param webmBlob 输入的 WebM 文件
 * @param onProgress 进度回调 (0-1)
 */
export async function convertWebmToMp4(
  webmBlob: Blob,
  onProgress?: (progress: number) => void
): Promise<Blob> {
  const ffmpeg = await getFFmpeg()

  // 写入输入文件
  const inputData = new Uint8Array(await webmBlob.arrayBuffer())
  await ffmpeg.writeFile('input.webm', inputData)

  // 监听进度
  let lastProgress = -1
  ffmpeg.on('progress', ({ progress }: { progress: number }) => {
    const p = Math.min(Math.max(progress, 0), 1)
    if (p > lastProgress) {
      lastProgress = p
      onProgress?.(p)
    }
  })

  // 执行转码
  // -preset ultrafast: 最快编码速度，降低CPU占用
  // -crf 23: 质量与文件大小平衡
  // -movflags faststart: 支持流式播放
  // -pix_fmt yuv420p: 确保兼容性
  await ffmpeg.exec([
    '-i', 'input.webm',
    '-c:v', 'libx264',
    '-preset', 'ultrafast',
    '-crf', '23',
    '-c:a', 'aac',
    '-b:a', '128k',
    '-pix_fmt', 'yuv420p',
    '-movflags', 'faststart',
    '-y',
    'output.mp4'
  ])

  // 读取输出文件
  const mp4Data = await ffmpeg.readFile('output.mp4') as Uint8Array
  const mp4Blob = new Blob([new Uint8Array(Array.from(mp4Data))], { type: 'video/mp4' })

  // 清理虚拟文件系统
  await ffmpeg.deleteFile('input.webm')
  await ffmpeg.deleteFile('output.mp4')

  return mp4Blob
}
