import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { AxiosError } from 'axios'
import App from '../App'
import { api } from '../services/api'
import { clearSession } from '../services/session'

// These responses exist only inside tests. The shipped application always calls the real API.
const initialAdapter = api.defaults.adapter
const owner = { id: 4, email: 'member@example.com', role: 'USER' }
const task = { id: 15, title: 'Hoàn thiện React', description: 'Mô tả cần giữ', status: 'TODO', priority: 'HIGH', userId: 4, projectId: 9, categoryId: 2, dueDate: '2030-10-12T02:30:00Z' }
let calls
let role
let currentTask
let forbidden
beforeEach(() => {
  window.history.replaceState({}, '', '/login')
  calls = []; role = 'USER'; currentTask = { ...task }; forbidden = false
  api.defaults.adapter = async (config) => {
    calls.push({ url: config.url, method: config.method, params: config.params, body: typeof config.data === 'string' ? JSON.parse(config.data) : config.data, headers: config.headers })
    let data
    if (config.url === '/auth/login' || config.url === '/auth/register') data = { accessToken: 'test-token', expiresIn: 3600, tokenType: 'Bearer', user: owner }
    else if (config.url === '/auth/me') data = { ...owner, role }
    else if (config.url === '/tasks/15' && config.method === 'put') { currentTask = { ...currentTask, ...JSON.parse(config.data) }; data = currentTask }
    else if (config.url === '/tasks/15') data = currentTask
    else if (config.url === '/tasks' && config.params?.userId) data = config.params.cursor ? { content: [{ ...currentTask, id: 2, title: 'Trang thứ hai' }], nextCursor: null, hasNext: false } : { content: [{ id: currentTask.id, title: currentTask.title, status: currentTask.status, priority: currentTask.priority, dueDate: currentTask.dueDate, categoryId: currentTask.categoryId }], nextCursor: 15, hasNext: true }
    else if (config.url === '/tasks') data = [currentTask]
    else if (config.url === '/projects') data = [{ id: 9, name: 'Đồ án', description: 'Dự án thật', userId: 4, createdAt: '2026-01-01T00:00:00Z' }]
    else if (config.url === '/categories' && forbidden) throw new AxiosError('Forbidden', 'ERR_BAD_RESPONSE', config, null, { status: 403, data: {}, config, headers: {} })
    else if (config.url === '/categories') data = [{ id: 2, name: 'Học tập', description: 'Bài tập nhóm' }]
    else throw new Error(`Unexpected test request: ${config.method} ${config.url}`)
    return { data, status: 200, statusText: 'OK', config, headers: {} }
  }
})
afterEach(() => { api.defaults.adapter = initialAdapter; clearSession() })
async function login() {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Địa chỉ email'), owner.email)
  await user.type(screen.getByLabelText('Mật khẩu', { exact: true }), 'password-123')
  await user.click(screen.getByRole('button', { name: 'Đăng nhập', exact: true }))
  return user
}
describe('Application auth, authorization and Task integration', () => {
  it('redirects unauthenticated deep links to login', async () => {
    window.history.replaceState({}, '', '/categories')
    render(<App />)
    expect(await screen.findByRole('heading', { name: 'Chào mừng trở lại' })).toBeInTheDocument()
    expect(window.location.pathname).toBe('/login')
    expect(calls).toHaveLength(0)
  })
  it('uses backend role from /me and never requests categories for USER', async () => {
    render(<App />)
    const user = await login()
    await screen.findByRole('heading', { name: 'Tổng quan công việc' })
    await screen.findByText('Công việc gần đây')
    expect(calls.some((c) => c.url === '/auth/me')).toBe(true)
    expect(calls.some((c) => c.url === '/categories')).toBe(false)
    expect(screen.queryByRole('link', { name: 'Danh mục', exact: true })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Đăng xuất' }))
    await screen.findByRole('heading', { name: 'Chào mừng trở lại' })
    expect(window.location.pathname).toBe('/login')
  })
  it('preserves all fields when changing status from a summary page', async () => {
    window.history.replaceState({}, '', '/tasks')
    render(<App />)
    const user = await login()
    const select = await screen.findByRole('combobox', { name: `Trạng thái ${task.title}` })
    await user.selectOptions(select, 'DONE')
    await waitFor(() => expect(calls.some((c) => c.url === '/tasks/15' && c.method === 'put')).toBe(true))
    const request = calls.find((c) => c.url === '/tasks/15' && c.method === 'put')
    expect(request.body).toMatchObject({ status: 'DONE', description: task.description, projectId: 9, categoryId: 2, userId: 4, priority: 'HIGH', dueDate: task.dueDate })
  })
  it('passes nextCursor to the API and navigates backwards', async () => {
    window.history.replaceState({}, '', '/tasks')
    render(<App />)
    const user = await login()
    await screen.findByRole('combobox', { name: `Trạng thái ${task.title}` })
    await user.click(screen.getByRole('button', { name: 'Trang sau' }))
    await screen.findByText('Trang thứ hai')
    expect(calls.find((c) => c.params?.cursor === 15)?.params).toMatchObject({ userId: 4, size: 10 })
    await user.click(screen.getByRole('button', { name: 'Trang trước' }))
    await screen.findByRole('combobox', { name: `Trạng thái ${task.title}` })
  })
  it('fetches the complete allowed list for search instead of filtering just a cursor page', async () => {
    window.history.replaceState({}, '', '/tasks?q=React')
    render(<App />)
    await login()
    await screen.findByRole('combobox', { name: `Trạng thái ${task.title}` })
    expect(calls.find((c) => c.url === '/tasks')?.params).toBeUndefined()
    expect(screen.getByText('Tìm kiếm và lọc trên toàn bộ dữ liệu được phép xem')).toBeInTheDocument()
  })
  it('blocks USER from the admin page even with a direct URL', async () => {
    window.history.replaceState({}, '', '/categories')
    render(<App />)
    await login()
    await screen.findByRole('heading', { name: 'Không có quyền truy cập' })
    expect(calls.some((c) => c.url === '/categories')).toBe(false)
  })
  it('allows ADMIN according to /me even if the login response says USER', async () => {
    role = 'ADMIN'
    window.history.replaceState({}, '', '/categories')
    render(<App />)
    await login()
    await screen.findByRole('heading', { name: 'Danh mục công việc' })
    await screen.findByText('Học tập')
    expect(screen.getByRole('link', { name: 'Danh mục', exact: true })).toBeInTheDocument()
  })
  it('opens a task editor with complete values and without a USER category request', async () => {
    window.history.replaceState({}, '', '/tasks')
    render(<App />)
    const user = await login()
    await screen.findByText(task.title)
    await user.click(screen.getByRole('button', { name: `Sửa ${task.title}` }))
    const dialog = await screen.findByRole('dialog')
    await waitFor(() => expect(within(dialog).getByLabelText('Mô tả')).toHaveValue(task.description))
    expect(within(dialog).getByLabelText('Dự án')).toHaveValue('9')
    expect(within(dialog).queryByLabelText('Danh mục')).not.toBeInTheDocument()
    expect(calls.some((c) => c.url === '/categories')).toBe(false)
  })
  it('validates UTF-8 password bytes before submitting registration', async () => {
    window.history.replaceState({}, '', '/register')
    render(<App />)
    const password = 'ệ'.repeat(25)
    fireEvent.change(screen.getByLabelText('Địa chỉ email'), { target: { value: owner.email } })
    fireEvent.change(screen.getByLabelText('Mật khẩu', { exact: true }), { target: { value: password } })
    fireEvent.change(screen.getByLabelText('Xác nhận mật khẩu'), { target: { value: password } })
    fireEvent.submit(screen.getByRole('button', { name: 'Tạo tài khoản', exact: true }).closest('form'))
    expect(await screen.findByRole('alert')).toHaveTextContent('72 byte')
    expect(calls).toHaveLength(0)
  })
  it('refreshes role after 403 and removes access when ADMIN is demoted', async () => {
    role = 'ADMIN'
    window.history.replaceState({}, '', '/categories')
    render(<App />)
    const user = await login()
    await screen.findByText('Học tập')
    role = 'USER'; forbidden = true
    await user.click(screen.getByRole('button', { name: 'Tải lại', exact: true }))
    await screen.findByRole('heading', { name: 'Không có quyền truy cập' })
    expect(screen.queryByRole('link', { name: 'Danh mục', exact: true })).not.toBeInTheDocument()
  })
})
