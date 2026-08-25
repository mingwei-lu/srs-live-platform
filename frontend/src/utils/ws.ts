import { useAuthStore } from '../store'

type MessageHandler = (data: any) => void

class WebSocketClient {
  private ws: WebSocket | null = null
  private url: string = ''
  private reconnectAttempts: number = 0
  private maxReconnectAttempts: number = 10
  private handlers: Map<string, MessageHandler[]> = new Map()
  private pingTimer: ReturnType<typeof setInterval> | null = null
  private isDestroyed: boolean = false

  connect(token: string) {
    this.isDestroyed = false
    const wsBaseUrl = import.meta.env.VITE_WS_BASE_URL || '/ws'
    if (wsBaseUrl.startsWith('ws://') || wsBaseUrl.startsWith('wss://')) {
      // 生产环境：完整地址
      this.url = `${wsBaseUrl}?token=${token}`
    } else {
      // 开发环境：相对路径，通过 Vite 代理
      const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
      const host = window.location.host
      this.url = `${protocol}//${host}${wsBaseUrl}?token=${token}`
    }
    this.doConnect()
  }

  private doConnect() {
    if (this.ws) {
      this.ws.close()
    }
    this.ws = new WebSocket(this.url)

    this.ws.onopen = () => {
      console.log('WS connected')
      this.reconnectAttempts = 0
      this.startPing()
    }

    this.ws.onmessage = (event) => {
      try {
        const msg = JSON.parse(event.data)
        const handlers = this.handlers.get(msg.type) || []
        handlers.forEach(fn => fn(msg))
        // 全局消息处理
        const globalHandlers = this.handlers.get('*') || []
        globalHandlers.forEach(fn => fn(msg))
      } catch (e) {
        console.error('WS parse error', e)
      }
    }

    this.ws.onclose = () => {
      console.log('WS disconnected')
      this.stopPing()
      if (!this.isDestroyed) {
        this.reconnect()
      }
    }

    this.ws.onerror = (err) => {
      console.error('WS error', err)
    }
  }

  private reconnect() {
    const delays = [1000, 2000, 4000, 8000, 8000]
    const delay = delays[Math.min(this.reconnectAttempts, delays.length - 1)]
    this.reconnectAttempts++
    if (this.reconnectAttempts > this.maxReconnectAttempts) {
      console.error('WS max reconnect attempts reached')
      return
    }
    console.log(`WS reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`)
    setTimeout(() => this.doConnect(), delay)
  }

  private startPing() {
    this.pingTimer = setInterval(() => {
      this.send({ type: 'ping' })
    }, 15000)
  }

  private stopPing() {
    if (this.pingTimer) {
      clearInterval(this.pingTimer)
      this.pingTimer = null
    }
  }

  send(data: any) {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(data))
    }
  }

  on(type: string, handler: MessageHandler) {
    if (!this.handlers.has(type)) {
      this.handlers.set(type, [])
    }
    this.handlers.get(type)!.push(handler)
  }

  off(type: string, handler: MessageHandler) {
    const handlers = this.handlers.get(type)
    if (handlers) {
      const idx = handlers.indexOf(handler)
      if (idx >= 0) handlers.splice(idx, 1)
    }
  }

  joinRoom(roomId: string) {
    this.send({
      type: 'join_room',
      data: { roomId }
    })
  }

  leaveRoom(roomId: string) {
    this.send({
      type: 'leave_room',
      data: { roomId }
    })
  }

  destroy() {
    this.isDestroyed = true
    this.stopPing()
    if (this.ws) {
      this.ws.close()
      this.ws = null
    }
    this.handlers.clear()
  }
}

let wsClient: WebSocketClient | null = null

export function getWsClient(): WebSocketClient {
  if (!wsClient) {
    wsClient = new WebSocketClient()
  }
  return wsClient
}

export function connectWs() {
  const auth = useAuthStore()
  if (auth.token) {
    getWsClient().connect(auth.token)
  }
}

export function disconnectWs() {
  if (wsClient) {
    wsClient.destroy()
    wsClient = null
  }
}