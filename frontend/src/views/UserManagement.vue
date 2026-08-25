<template>
  <div class="management-page">
    <div class="page-header">
      <h2>用户管理</h2>
      <button class="btn btn-primary" @click="openAddModal">新增用户</button>
    </div>

    <div class="filter-bar">
      <input v-model="searchKeyword" placeholder="搜索用户名/手机号" @keyup.enter="handleSearch" />
      <select v-model="filterRole" @change="handleSearch">
        <option value="">全部角色</option>
        <option value="admin">管理员</option>
        <option value="user">普通用户</option>
      </select>
      <button class="btn btn-primary" @click="handleSearch">搜索</button>
      <button class="btn" @click="loadUsers">刷新</button>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="filteredUsers.length === 0" class="empty">暂无用户数据</div>
    <div v-else class="table-container card">
      <table class="data-table">
        <thead>
          <tr>
            <th>UID</th>
            <th>用户名</th>
            <th>手机号</th>
            <th>角色</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="user in filteredUsers" :key="user.uid">
            <td class="mono-cell">{{ user.uid }}</td>
            <td>{{ user.username || '-' }}</td>
            <td>{{ user.phone }}</td>
            <td>
              <span class="role-tag" :class="user.role">{{ roleMap[user.role] || user.role }}</span>
            </td>
            <td>
              <span class="status-tag" :class="user.status === 1 ? 'active' : 'disabled'">
                {{ user.status === 1 ? '正常' : '禁用' }}
              </span>
            </td>
            <td>{{ formatTime(user.createdAt) }}</td>
            <td>
              <button class="btn btn-sm btn-primary" @click="openEditModal(user)">编辑</button>
              <button class="btn btn-sm btn-danger" @click="handleDelete(user.uid)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div v-if="totalPages > 1" class="pagination">
      <button :disabled="currentPage <= 1" class="btn btn-sm" @click="goPage(currentPage - 1)">上一页</button>
      <span class="page-info">第 {{ currentPage }} / {{ totalPages }} 页，共 {{ total }} 条</span>
      <button :disabled="currentPage >= totalPages" class="btn btn-sm" @click="goPage(currentPage + 1)">下一页</button>
    </div>

    <!-- 新增/编辑用户弹窗 -->
    <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
      <div class="modal card">
        <h3>{{ isEdit ? '编辑用户' : '新增用户' }}</h3>
        <div class="form-group">
          <label>用户名</label>
          <input v-model="form.username" placeholder="用户名" />
        </div>
        <div class="form-group">
          <label>手机号 <span v-if="!isEdit" class="required">*</span></label>
          <input v-model="form.phone" placeholder="手机号" :disabled="isEdit" />
        </div>
        <div class="form-group" v-if="!isEdit">
          <label>密码 <span class="required">*</span></label>
          <input v-model="form.password" type="password" placeholder="默认 123456" />
        </div>
        <div class="form-group">
          <label>角色</label>
          <select v-model="form.role">
            <option value="user">普通用户</option>
            <option value="admin">管理员</option>
          </select>
        </div>
        <div class="form-group">
          <label>状态</label>
          <select v-model="form.status">
            <option :value="1">正常</option>
            <option :value="0">禁用</option>
          </select>
        </div>
        <div class="form-group">
          <label>公司</label>
          <input v-model="form.company" placeholder="公司（选填）" />
        </div>
        <div class="form-group">
          <label>部门</label>
          <input v-model="form.department" placeholder="部门（选填）" />
        </div>
        <div class="modal-actions">
          <button class="btn" @click="closeModal">取消</button>
          <button class="btn btn-primary" @click="saveUser">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { listUsers, createUser, updateUser, deleteUser } from '../api/user'

const users = ref<any[]>([])
const loading = ref(false)
const searchKeyword = ref('')
const filterRole = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const totalPages = ref(0)

const showModal = ref(false)
const isEdit = ref(false)
const editUid = ref('')
const form = ref({
  username: '',
  phone: '',
  password: '',
  role: 'user',
  status: 1,
  company: '',
  department: ''
})

const roleMap: Record<string, string> = {
  admin: '管理员',
  user: '普通用户'
}

const filteredUsers = computed(() => {
  let result = users.value
  if (filterRole.value) {
    result = result.filter(u => u.role === filterRole.value)
  }
  return result
})

async function loadUsers() {
  loading.value = true
  try {
    const res: any = await listUsers({
      page: currentPage.value,
      size: pageSize.value,
      keyword: searchKeyword.value || undefined
    })
    const data = res.data || {}
    users.value = data.records || []
    total.value = data.total || 0
    totalPages.value = data.pages || 1
    currentPage.value = Number(data.current) || 1
  } catch (e) {
    console.error('load users error', e)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadUsers()
}

function goPage(page: number) {
  currentPage.value = page
  loadUsers()
}

function openAddModal() {
  isEdit.value = false
  editUid.value = ''
  form.value = {
    username: '',
    phone: '',
    password: '',
    role: 'user',
    status: 1,
    company: '',
    department: ''
  }
  showModal.value = true
}

function openEditModal(user: any) {
  isEdit.value = true
  editUid.value = user.uid
  form.value = {
    username: user.username || '',
    phone: user.phone || '',
    password: '',
    role: user.role || 'user',
    status: user.status ?? 1,
    company: user.company || '',
    department: user.department || ''
  }
  showModal.value = true
}

function closeModal() {
  showModal.value = false
}

async function saveUser() {
  try {
    if (isEdit.value) {
      await updateUser(editUid.value, {
        username: form.value.username,
        role: form.value.role,
        status: form.value.status,
        company: form.value.company,
        department: form.value.department
      })
    } else {
      await createUser({
        username: form.value.username,
        phone: form.value.phone,
        passwordHash: form.value.password || '123456',
        role: form.value.role,
        status: form.value.status,
        company: form.value.company,
        department: form.value.department
      })
    }
    closeModal()
    await loadUsers()
  } catch (e: any) {
    console.error('save user error', e)
    alert('保存失败: ' + (e.message || '未知错误'))
  }
}

async function handleDelete(uid: string) {
  if (!confirm('确定删除该用户？此操作不可恢复。')) return
  try {
    await deleteUser(uid)
    await loadUsers()
  } catch (e: any) {
    console.error('delete user error', e)
    alert('删除失败: ' + (e.message || '未知错误'))
  }
}

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString()
}

onMounted(loadUsers)
</script>

<style scoped>
.management-page { max-width: 1200px; margin: 0 auto; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-header h2 { font-size: 22px; }
.filter-bar { display: flex; gap: 12px; margin-bottom: 20px; flex-wrap: wrap; align-items: center; }
.filter-bar input { width: 240px; }
.loading, .empty { text-align: center; padding: 60px; color: #999; }
.table-container { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; }
.data-table th { padding: 12px 16px; text-align: left; font-size: 14px; font-weight: 600; color: #666; background: #f8f9fa; border-bottom: 1px solid #eee; }
.data-table td { padding: 12px 16px; font-size: 14px; color: #333; border-bottom: 1px solid #f0f0f0; }
.data-table tr:hover { background: #f8f9ff; }
.mono-cell { font-family: monospace; font-size: 12px; color: #666; }
.role-tag { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }
.role-tag.admin { background: #e8f5e9; color: #2e7d32; }
.role-tag.user { background: #e3f2fd; color: #1565c0; }
.status-tag { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; font-weight: 500; }
.status-tag.active { background: #d4edda; color: #155724; }
.status-tag.disabled { background: #f8d7da; color: #721c24; }

.pagination { display: flex; justify-content: center; align-items: center; gap: 16px; margin-top: 24px; }
.page-info { font-size: 14px; color: #666; }

.modal-overlay { position: fixed; top: 0; left: 0; right: 0; bottom: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { width: 480px; max-height: 80vh; overflow-y: auto; }
.modal h3 { margin-bottom: 16px; }
.form-group { margin-bottom: 12px; }
.form-group label { display: block; font-size: 13px; color: #666; margin-bottom: 4px; }
.form-group .required { color: #e74c3c; }
.form-group input, .form-group select { width: 100%; padding: 8px 12px; border: 1px solid #ddd; border-radius: 4px; font-size: 14px; }
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
</style>
