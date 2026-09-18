import { defineConfig, loadEnv, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

/**
 * 注入跨域隔离响应头到所有响应（含 Worker、代理），确保 SharedArrayBuffer 可用
 */
function crossOriginIsolationPlugin(): Plugin {
  return {
    name: 'cross-origin-isolation',
    configureServer(server) {
      server.middlewares.use((_req, res, next) => {
        res.setHeader('Cross-Origin-Opener-Policy', 'same-origin')
        res.setHeader('Cross-Origin-Embedder-Policy', 'credentialless')
        next()
      })
    }
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, resolve(__dirname), '')

  return {
    plugins: [vue(), crossOriginIsolationPlugin()],
    // ffmpeg.wasm 内部动态创建 Worker（new URL('./worker.js')），
    // Vite 预构建会解析失败，需要排除
    optimizeDeps: {
      exclude: ['@ffmpeg/ffmpeg', '@ffmpeg/util']
    },
    server: {
      port: 3000,
      proxy: {
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8180',
          changeOrigin: true
        },
        '/ws': {
          target: env.VITE_PROXY_WS_TARGET || 'http://localhost:8180',
          ws: true,
          changeOrigin: true
        },
        // ffmpeg.wasm core 代理到 CDN，变同源请求避免跨域隔离问题
        '/ffmpeg-core': {
          target: 'https://cdn.jsdelivr.net/npm/@ffmpeg/core@0.12.9/dist',
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/ffmpeg-core/, '')
        }
      }
    }
  }
})