import { AxiosError } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, errorMessage, fieldErrors } from '../services/api'
import { clearSession, getToken, setSession } from '../services/session'
const initialAdapter = api.defaults.adapter
afterEach(() => { api.defaults.adapter = initialAdapter; clearSession() })
const failure = (config, status, data = {}) => new AxiosError('Request failed', 'ERR_BAD_RESPONSE', config, null, { status, data, config, headers: {} })
describe('Axios authentication and ProblemDetail', () => {
  it('adds Bearer only to protected requests', async () => {
    setSession('test-token', 3600)
    const headers = []
    api.defaults.adapter = async (config) => {
      headers.push(config.headers.Authorization)
      return { data: {}, status: 200, config, headers: {} }
    }
    await api.get('/tasks')
    await api.post('/auth/login', {}, { publicRequest: true })
    expect(headers).toEqual(['Bearer test-token', undefined])
  })
  it('clears the current session on 401', async () => {
    setSession('test-token', 3600)
    api.defaults.adapter = async (config) => { throw failure(config, 401) }
    await expect(api.get('/tasks')).rejects.toMatchObject({ response: { status: 401 } })
    expect(getToken()).toBeNull()
  })
  it('ignores late 401 responses belonging to an older session', async () => {
    setSession('old-token', 3600)
    api.defaults.adapter = async (config) => {
      setSession('new-token', 3600)
      throw failure(config, 401)
    }
    await expect(api.get('/tasks')).rejects.toBeDefined()
    expect(getToken()).toBe('new-token')
  })
  it('retains JWT on 403 and requests role synchronization', async () => {
    setSession('test-token', 3600)
    const listener = vi.fn()
    window.addEventListener('auth:refresh-role', listener)
    api.defaults.adapter = async (config) => { throw failure(config, 403) }
    await expect(api.get('/categories')).rejects.toBeDefined()
    expect(getToken()).toBe('test-token')
    expect(listener).toHaveBeenCalledOnce()
    window.removeEventListener('auth:refresh-role', listener)
  })
  it('maps status errors without exposing raw backend detail or credentials', () => {
    const error = failure({}, 500, { detail: 'private server details' })
    expect(errorMessage(error)).not.toContain('private')
    expect(fieldErrors(failure({}, 400, { errors: { 'create.title': 'required' } }))).toEqual({ title: 'required' })
    expect(fieldErrors(failure({}, 400, { errors: { title: 'must not be blank', email: 'must be a well-formed email address' } }))).toEqual({ title: 'Không được để trống.', email: 'Địa chỉ email chưa hợp lệ.' })
  })
})
