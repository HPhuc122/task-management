import { api } from './api'
export const authService = {
  login: (body) => api.post('/auth/login', body, { publicRequest: true }).then((r) => r.data),
  register: (body) => api.post('/auth/register', body, { publicRequest: true }).then((r) => r.data),
  me: () => api.get('/auth/me', { skipRoleRefresh: true }).then((r) => r.data),
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
