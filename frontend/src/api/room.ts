import api from './index'

export function createRoom(data: { title: string }) {
  return api.post('/rooms', data)
}

export function getRoom(roomId: string) {
  return api.get(`/rooms/${roomId}`)
}

export function listRooms(status?: string) {
  return api.get('/rooms', { params: { status } })
}

export function updateRoom(roomId: string, data: { title?: string }) {
  return api.put(`/rooms/${roomId}`, data)
}

export function closeRoom(roomId: string) {
  return api.delete(`/rooms/${roomId}`)
}

export function startLive(roomId: string, serverRecording: boolean = false) {
  return api.post(`/rooms/${roomId}/start?serverRecording=${serverRecording}`)
}

export function stopLive(roomId: string) {
  return api.post(`/rooms/${roomId}/stop`)
}

export function getRoomUsers(roomId: string) {
  return api.get(`/rooms/${roomId}/users`)
}

export function kickUser(roomId: string, targetUid: string) {
  return api.post(`/rooms/${roomId}/kick/${targetUid}`)
}

export function getPlayUrl(roomId: string) {
  return api.get(`/rooms/${roomId}/play-url`)
}