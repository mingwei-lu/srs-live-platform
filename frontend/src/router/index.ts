import { createRouter, createWebHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import RoomList from '../views/RoomList.vue'
import RoomDetail from '../views/RoomDetail.vue'
import AdminDashboard from '../views/AdminDashboard.vue'
import UserManagement from '../views/UserManagement.vue'
import SrsNodeManagement from '../views/SrsNodeManagement.vue'
import RoomManagement from '../views/RoomManagement.vue'

const routes = [
  { path: '/', redirect: '/rooms' },
  { path: '/login', component: Login },
  { path: '/register', component: Register },
  { path: '/rooms', component: RoomList },
  { path: '/rooms/:roomId', component: RoomDetail },
  { path: '/admin', component: AdminDashboard },
  { path: '/admin/users', component: UserManagement },
  { path: '/admin/nodes', component: SrsNodeManagement },
  { path: '/admin/rooms', component: RoomManagement }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
