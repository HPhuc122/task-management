export const STATUSES = { TODO: 'Chưa bắt đầu', IN_PROGRESS: 'Đang thực hiện', DONE: 'Hoàn thành' }
export const PRIORITIES = { LOW: 'Thấp', MEDIUM: 'Trung bình', HIGH: 'Cao' }
export const formatDate = (value, withTime = false) => value
  ? new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', ...(withTime ? { hour: '2-digit', minute: '2-digit' } : {}) }).format(new Date(value))
  : 'Chưa có hạn'
export const isOverdue = (task, now = Date.now()) => Boolean(task.dueDate && task.status !== 'DONE' && new Date(task.dueDate).getTime() < now)
export function summarize(tasks) {
  return {
    total: tasks.length, todo: tasks.filter((t) => t.status === 'TODO').length,
    inProgress: tasks.filter((t) => t.status === 'IN_PROGRESS').length,
    done: tasks.filter((t) => t.status === 'DONE').length,
    overdue: tasks.filter((t) => isOverdue(t)).length,
  }
}
export function toLocalInput(value) {
  if (!value) return ''
  const date = new Date(value)
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}
export function taskPayload(task) {
  return {
    title: task.title.trim(), description: task.description || null,
    status: task.status, priority: task.priority, dueDate: task.dueDate || null,
    userId: Number(task.userId), categoryId: task.categoryId ? Number(task.categoryId) : null,
    projectId: task.projectId ? Number(task.projectId) : null,
  }
}
export function filterTasks(tasks, filters) {
  const term = (filters.q || '').trim().toLocaleLowerCase('vi')
  return tasks.filter((task) =>
    (!term || `${task.title} ${task.description || ''}`.toLocaleLowerCase('vi').includes(term)) &&
    (!filters.status || task.status === filters.status) &&
    (!filters.priority || task.priority === filters.priority) &&
    (!filters.projectId || String(task.projectId) === filters.projectId) &&
    (!filters.categoryId || String(task.categoryId) === filters.categoryId) &&
    (!filters.overdue || isOverdue(task)),
  )
}
