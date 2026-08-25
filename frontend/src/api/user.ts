import api from './index'

export function login(data: { phone: string; password: string }) {
  return api.post('/users/login', data)
}

export function register(data: { phone: string; password: string; role?: string; username?: string; company?: string; department?: string; phoneTail?: string }) {
  return api.post('/users/register', data)
}

export function getUser(uid: string) {
  return api.get(`/users/${uid}`)
}

export function listUsers(params?: { page?: number; size?: number; keyword?: string }) {
  return api.get('/users', { params })
}

export function createUser(data: any) {
  return api.post('/users', data)
}

export function updateUser(uid: string, data: any) {
  return api.put(`/users/${uid}`, data)
}

export function deleteUser(uid: string) {
  return api.delete(`/users/${uid}`)
}

export function checkPhone(phone: string) {
  return api.get('/users/check-phone', { params: { phone } })
}
