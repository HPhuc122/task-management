import { api } from './api'
export const authService = {
  login: (body) => api.post('/auth/login', body, { publicRequest: true }).then((r) => r.data),
  register: (body) => api.post('/auth/register', body, { publicRequest: true }).then((r) => r.data),
  me: (signal) => api.get('/auth/me', { signal, skipRoleRefresh: true }).then((r) => r.data),
}
function resource(path) {
  return {
    list: (signal) => api.get(path, { signal }).then((r) => r.data),
    get: (id, signal) => api.get(`${path}/${id}`, { signal }).then((r) => r.data),
    create: (body, config) => api.post(path, body, config).then((r) => r.data),
    update: (id, body) => api.put(`${path}/${id}`, body).then((r) => r.data),
    remove: (id) => api.delete(`${path}/${id}`),
  }
}
export const taskService = {
  ...resource('/tasks'),
  page: (userId, cursor, size, signal) => api.get('/tasks', {
    params: { userId, cursor: cursor || undefined, size }, signal,
  }).then((r) => r.data),
}
export const projectService = resource('/projects')
export const categoryService = resource('/categories')
export const attachmentService = {
  // Lấy danh sách file đính kèm của Task
  list: (taskId, signal) =>
    api.get(`/tasks/${taskId}/attachments`, { signal })
      .then((response) => response.data),

  // Upload file lên MinIO thông qua Backend
  upload: (taskId, file, signal) => {
    const formData = new FormData()
    formData.append('file', file)

    return api.post(`/tasks/${taskId}/attachments`, formData, {
      timeout: 60000,
      signal,
    }).then((response) => response.data)
  },

  // Tải file về dưới dạng Blob
  download: (taskId, attachmentId, signal) =>
    api.get(
      `/tasks/${taskId}/attachments/${attachmentId}/content`,
      {
        responseType: 'blob',
        headers: { Accept: '*/*' },
        timeout: 60000,
        signal,
      }
    ).then((response) => response.data),

  // Xóa file đính kèm
  remove: (taskId, attachmentId, signal) =>
    api.delete(`/tasks/${taskId}/attachments/${attachmentId}`, { signal, timeout: 60000 }),
}
