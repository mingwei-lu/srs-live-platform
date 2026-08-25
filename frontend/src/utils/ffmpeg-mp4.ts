import { FFmpeg } from '@ffmpeg/ffmpeg'

const CACHE_NAME = 'ffmpeg-wasm-cache-v1'
const FFMPEG_VERSION = '0.12.15'
const BASE_URL = `https://cdn.jsdelivr.net/npm/@ffmpeg/ffmpeg@${FFMPEG_VERSION}/dist/umd`

let ffmpegInstance: FFmpeg | null = null
let loadPromise: Promise<FFmpeg> | null = null

/**
 * 获取 ffmpeg.wasm 实例（带缓存加速）
 */
export async function getFFmpeg(): Promise<FFmpeg> {
  if (ffmpegInstance?.loaded) return ffmpegInstance
  if (loadPromise) return loadPromise

  loadPromise = loadFFmpegWithCache()
  ffmpegInstance = await loadPromise
  loadPromise = null
  return ffmpegInstance
}

/**
 * 从缓存加载 ffmpeg.wasm，首次下载后缓存到 CacheStorage
 */
async function loadFFmpegWithCache(): Promise<FFmpeg> {
  let cache: Cache | null = null
  try {
    if ('caches' in self) {
      cache = await caches.open(CACHE_NAME)
    }
  } catch (e) {
    console.warn('[ffmpeg] CacheStorage unavailable:', e)
  }

  // 尝试从缓存获取核心文件
  let coreBlob = cache ? await getCacheBlob(cache, `${BASE_URL}/ffmpeg-core.js`) : null
  let wasmBlob = cache ? await getCacheBlob(cache, `${BASE_URL}/ffmpeg-core.wasm`) : null

  // 缓存未命中则从网络下载
  if (!coreBlob) {
    coreBlob = await fetchAndCache(cache, `${BASE_URL}/ffmpeg-core.js`)
  }
  if (!wasmBlob) {
    wasmBlob = await fetchAndCache(cache, `${BASE_URL}/ffmpeg-core.wasm`)
  }

  const ffmpeg = new FFmpeg()

  // 日志输出
  ffmpeg.on('log', ({ message }: { message: string }) => {
    console.log('[ffmpeg]', message)
  })

  await ffmpeg.load({
    coreURL: URL.createObjectURL(coreBlob),
    wasmURL: URL.createObjectURL(wasmBlob),
  })

  return ffmpeg
}

async function getCacheBlob(cache: Cache | null, url: string): Promise<Blob | null> {
  if (!cache) return null
  try {
    const response = await cache.match(url)
    if (response) {
      console.log('[ffmpeg-cache] hit:', url)
      return response.blob()
    }
  } catch (e) {
    console.warn('[ffmpeg-cache] read error:', e)
  }
  return null
}

async function fetchAndCache(cache: Cache | null, url: string): Promise<Blob> {
  console.log('[ffmpeg-cache] fetching:', url)
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(`Failed to fetch ${url}: ${response.status}`)
  }
  const blob = await response.blob()
  if (cache) {
    try {
      await cache.put(url, new Response(blob, { status: 200, statusText: 'OK' }))
    } catch (e) {
      console.warn('[ffmpeg-cache] write error:', e)
    }
  }
  return blob
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
