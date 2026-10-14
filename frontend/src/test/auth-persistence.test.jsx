import { StrictMode } from 'react'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
import App from '../App'
import { api } from '../services/api'
import { clearSession, getToken, SESSION_STORAGE_KEY } from '../services/session'

const initialAdapter = api.defaults.adapter
const owner = { id: 4, email: 'member@example.com', role: 'USER' }
let calls, role, meError, meGate

function saveSession(expiresAt = Date.now() + 3600000) {
  sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ accessToken: 'saved-token', expiresAt }))
}
function deferred() {
  let resolve
  const promise = new Promise((done) => { resolve = done })
  return { promise, resolve }
}
function remount(view) {
  const saved = sessionStorage.getItem(SESSION_STORAGE_KEY)
  view.unmount()
  // Simulate losing RAM on reload while preserving this tab's storage and URL.
  clearSession()
  if (saved) sessionStorage.setItem(SESSION_STORAGE_KEY, saved)
  return render(<App />)
}

beforeEach(() => {
  clearSession()
  sessionStorage.clear()
  window.history.replaceState({}, '', '/tasks')
  calls = []; role = 'USER'; meError = null; meGate = null
  api.defaults.adapter = async (config) => {
    calls.push(config)
    let data
    if (config.url === '/auth/me') {
      if (meGate) await meGate.promise
      if (meError === 'network') throw new AxiosError('Network error', 'ERR_NETWORK', config)
      if (meError) throw new AxiosError('Internal detail that must remain private', 'ERR_BAD_RESPONSE', config, null, { status: meError, data: {}, config, headers: {} })
      data = { ...owner, role }
    } else if (config.url === '/auth/login') {
      data = { accessToken: 'saved-token', expiresIn: 3600, user: { ...owner, role: 'ADMIN' } }
    } else if (config.url === '/tasks') {
      data = config.params?.userId ? { content: [], hasNext: false, nextCursor: null } : []
    } else if (config.url === '/projects' || config.url === '/categories') data = []
    else throw new Error(`Unexpected test endpoint: ${config.url}`)
    return { data, status: 200, config, headers: {} }
  }
})
afterEach(() => {
  cleanup()
  api.defaults.adapter = initialAdapter
  clearSession()
  sessionStorage.clear()
  vi.useRealTimers()
})

describe('Authentication restoration and protected navigation', () => {
  it.each([
    ['/dashboard', 'Tổng quan công việc'],
    ['/tasks?q=report#list', 'Công việc'],
    ['/projects', 'Dự án'],
  ])('restores the session at %s without changing the route', async (path, heading) => {
    window.history.replaceState({}, '', path)
    saveSession()
    const saved = sessionStorage.getItem(SESSION_STORAGE_KEY)
    render(<App />)
    expect(screen.getByRole('status')).toHaveTextContent('Đang khôi phục phiên đăng nhập')
    expect(screen.queryByLabelText('Mật khẩu', { exact: true })).not.toBeInTheDocument()
    await screen.findByRole('heading', { level: 1, name: heading, exact: true })
    expect(window.location.pathname + window.location.search + window.location.hash).toBe(path)
    expect(calls[0].url).toBe('/auth/me')
    expect(calls[0].headers.Authorization).toBe('Bearer saved-token')
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBe(saved)
    expect(calls.some((call) => call.url === '/categories')).toBe(false)
    expect(localStorage.length).toBe(0)
  })

  it('waits for /me without flashing login or fetching protected resources early', async () => {
    saveSession()
    meGate = deferred()
    render(<StrictMode><App /></StrictMode>)
    await waitFor(() => expect(calls).toHaveLength(1))
    expect(screen.getByRole('status')).toHaveTextContent('Đang khôi phục')
    expect(screen.queryByRole('heading', { name: 'Chào mừng trở lại' })).not.toBeInTheDocument()
    expect(window.location.pathname).toBe('/tasks')
    expect(calls.every((call) => call.url === '/auth/me')).toBe(true)
    await act(async () => meGate.resolve())
    await screen.findByRole('heading', { level: 1, name: 'Công việc', exact: true })
  })

  it('preserves the same absolute expiry across repeated reloads', async () => {
    saveSession()
    const saved = sessionStorage.getItem(SESSION_STORAGE_KEY)
    let view = render(<App />)
    for (let count = 0; count < 3; count++) {
      await screen.findByRole('heading', { level: 1, name: 'Công việc', exact: true })
      expect(window.location.pathname).toBe('/tasks')
      expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBe(saved)
      view = remount(view)
    }
    await screen.findByRole('heading', { level: 1, name: 'Công việc', exact: true })
    expect(calls.filter((call) => call.url === '/auth/me')).toHaveLength(4)
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBe(saved)
  })

  it('discards expired storage and redirects to login without sending the token', async () => {
    saveSession(Date.now() - 1)
    render(<App />)
    await screen.findByRole('heading', { name: 'Chào mừng trở lại' })
    expect(window.location.pathname).toBe('/login')
    expect(getToken()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
    expect(calls).toHaveLength(0)
  })

  it('clears both copies and redirects to login when /me returns 401', async () => {
    saveSession()
    meError = 401
    render(<App />)
    await screen.findByRole('heading', { name: 'Chào mừng trở lại' })
    expect(window.location.pathname).toBe('/login')
    expect(getToken()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
    expect(calls.map((call) => call.url)).toEqual(['/auth/me'])
  })

  it.each(['network', 500, 502, 503, 403])('preserves a valid JWT on /me failure %s and supports retry', async (failure) => {
    saveSession()
    const saved = sessionStorage.getItem(SESSION_STORAGE_KEY)
    meError = failure
    render(<App />)
    const alert = await screen.findByRole('alert')
    expect(alert).not.toHaveTextContent('Internal detail')
    expect(window.location.pathname).toBe('/tasks')
    expect(screen.queryByLabelText('Mật khẩu', { exact: true })).not.toBeInTheDocument()
    expect(getToken()).toBe('saved-token')
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBe(saved)
    expect(calls.map((call) => call.url)).toEqual(['/auth/me'])
    meError = null
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await screen.findByRole('heading', { level: 1, name: 'Công việc', exact: true })
    expect(window.location.pathname).toBe('/tasks')
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBe(saved)
  })

  it('keeps a newly issued token if /me fails immediately after login', async () => {
    render(<App />)
    await screen.findByLabelText('Địa chỉ email')
    fireEvent.change(screen.getByLabelText('Địa chỉ email'), { target: { value: owner.email } })
    fireEvent.change(screen.getByLabelText('Mật khẩu', { exact: true }), { target: { value: 'test-password' } })
    meError = 'network'
    fireEvent.submit(screen.getByRole('button', { name: 'Đăng nhập', exact: true }).closest('form'))
    await screen.findByRole('button', { name: 'Thử lại' })
    expect(getToken()).toBe('saved-token')
    expect(Object.keys(JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY))).sort()).toEqual(['accessToken', 'expiresAt'])
    meError = null
    await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }))
    await screen.findByRole('heading', { level: 1, name: 'Công việc', exact: true })
    expect(window.location.pathname).toBe('/tasks')
  })

  it('restores a session on the login page without displaying the login form first', async () => {
    window.history.replaceState({}, '', '/login')
    saveSession()
    render(<App />)
    expect(screen.queryByLabelText('Địa chỉ email')).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Đang khôi phục')
    await screen.findByRole('heading', { level: 1, name: 'Tổng quan công việc' })
    expect(window.location.pathname).toBe('/dashboard')
  })

  it('removes RAM and storage on logout, so refresh cannot restore the old session', async () => {
    saveSession()
    let view = render(<App />)
    await screen.findByRole('button', { name: 'Đăng xuất' })
    await userEvent.click(screen.getByRole('button', { name: 'Đăng xuất' }))
    await screen.findByRole('heading', { name: 'Chào mừng trở lại' })
    expect(getToken()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
    const before = calls.length
    view = remount(view)
    await screen.findByRole('heading', { name: 'Chào mừng trở lại' })
    expect(calls).toHaveLength(before)
    expect(window.location.pathname).toBe('/login')
    view.unmount()
  })

  it('enforces USER permissions after restoration even with a forged cached ADMIN role', async () => {
    window.history.replaceState({}, '', '/categories')
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ accessToken: 'saved-token', expiresAt: Date.now() + 3600000, user: { ...owner, role: 'ADMIN' } }))
    render(<App />)
    await screen.findByRole('heading', { name: 'Không có quyền truy cập' })
    expect(calls.some((call) => call.url === '/categories')).toBe(false)
    expect(getToken()).toBe('saved-token')
  })

  it('allows ADMIN routes only after /me confirms the role', async () => {
    window.history.replaceState({}, '', '/categories')
    saveSession()
    role = 'ADMIN'
    render(<App />)
    await screen.findByRole('heading', { level: 1, name: 'Danh mục công việc' })
    await waitFor(() => expect(calls.some((call) => call.url === '/categories')).toBe(true))
    expect(calls[0].url).toBe('/auth/me')
  })

  it('cannot resurrect a session from a late /me response after logout', async () => {
    saveSession()
    meGate = deferred()
    render(<App />)
    await waitFor(() => expect(calls).toHaveLength(1))
    const signal = calls[0].signal
    act(() => clearSession())
    await screen.findByRole('heading', { name: 'Chào mừng trở lại' })
    expect(signal.aborted).toBe(true)
    await act(async () => meGate.resolve())
    expect(window.location.pathname).toBe('/login')
    expect(getToken()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
  })

  it('expires an active restored session when the tab wakes up after its deadline', async () => {
    saveSession()
    render(<App />)
    await screen.findByRole('heading', { level: 1, name: 'Công việc', exact: true })
    vi.useFakeTimers()
    vi.setSystemTime(Date.now() + 3600001)
    fireEvent.focus(window)
    expect(getToken()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
    expect(window.location.pathname).toBe('/login')
    expect(screen.getByRole('heading', { name: 'Chào mừng trở lại' })).toBeInTheDocument()
  })
})
