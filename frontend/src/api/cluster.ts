import api from './index'

export function getClusterOverview() {
  return api.get('/v/overview')
}

export function getClusterNodes() {
  return api.get('/v/nodes')
}

export function getClusterNode(nodeId: string) {
  return api.get(`/v/nodes/${nodeId}`)
}

export function addClusterNode(data: any) {
  return api.post('/v/nodes', data)
}

export function updateClusterNode(nodeId: string, data: any) {
  return api.put(`/v/nodes/${nodeId}`, data)
}

export function removeClusterNode(nodeId: string) {
  return api.delete(`/v/nodes/${nodeId}`)
}

export function checkClusterNodeHealth(nodeId: string) {
  return api.get(`/v/nodes/${nodeId}/health`)
}

export function checkAllClusterNodesHealth() {
  return api.get('/v/nodes/health/all')
}

export function getClusterEvents(type?: string) {
  return api.get('/v/events', { params: { type } })
}
