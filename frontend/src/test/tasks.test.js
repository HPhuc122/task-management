import { describe, expect, it } from 'vitest'
import { filterTasks, isOverdue, summarize, taskPayload, toLocalInput } from '../lib/tasks'
describe('Task DTO mapping and filtering', () => {
  it('preserves every writable field when changing status', () => {
    const full = { id: 7, title: '  Kiểm tra  ', description: 'Mô tả quan trọng', status: 'TODO', priority: 'HIGH', dueDate: '2030-10-12T02:30:00Z', userId: 4, categoryId: 2, projectId: 9, createdAt: '2026-01-01T00:00:00Z' }
    expect(taskPayload({ ...full, status: 'DONE' })).toEqual({ title: 'Kiểm tra', description: 'Mô tả quan trọng', status: 'DONE', priority: 'HIGH', dueDate: full.dueDate, userId: 4, categoryId: 2, projectId: 9 })
  })
  it('does not count finished tasks as overdue', () => {
    expect(isOverdue({ dueDate: '2000-01-01T00:00:00Z', status: 'DONE' })).toBe(false)
    expect(isOverdue({ dueDate: '2000-01-01T00:00:00Z', status: 'TODO' })).toBe(true)
    expect(isOverdue({ dueDate: null, status: 'TODO' })).toBe(false)
  })
  it('round-trips an Instant through datetime-local without changing the time', () => {
    const instant = '2030-10-12T02:30:00.000Z'
    expect(new Date(toLocalInput(instant)).toISOString()).toBe(instant)
  })
  it('combines case-insensitive text, project, status, and priority filters', () => {
    const tasks = [
      { title: 'Thiết kế', description: 'Giao diện REACT', projectId: 2, status: 'TODO', priority: 'HIGH' },
      { title: 'React API', projectId: 3, status: 'DONE', priority: 'LOW' },
    ]
    expect(filterTasks(tasks, { q: ' react ', projectId: '2', status: 'TODO', priority: 'HIGH' })).toEqual([tasks[0]])
    expect(summarize(tasks)).toMatchObject({ total: 2, done: 1, todo: 1, inProgress: 0 })
  })
})
