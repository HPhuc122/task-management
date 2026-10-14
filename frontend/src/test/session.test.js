import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearSession, getToken, restoreSession, SESSION_STORAGE_KEY, setSession, subscribeSession } from '../services/session'
afterEach(() => { clearSession(); sessionStorage.clear(); vi.useRealTimers() })
describe('JWT session lifecycle', () => {
  it('expires on time, clears the token, and notifies subscribers', () => {
    vi.useFakeTimers()
    const listener = vi.fn()
    const unsubscribe = subscribeSession(listener)
    setSession('test-token', 2)
    expect(getToken()).toBe('test-token')
    expect(localStorage.length).toBe(0)
    expect(JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY))).toEqual({ accessToken: 'test-token', expiresAt: Date.now() + 2000 })
    vi.advanceTimersByTime(2000)
    expect(getToken()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
    expect(listener).toHaveBeenCalledWith('expired')
    unsubscribe()
  })
  it('does not let the previous session timer terminate a new session', () => {
    vi.useFakeTimers()
    setSession('old-token', 2)
    vi.advanceTimersByTime(1000)
    setSession('new-token', 10)
    vi.advanceTimersByTime(2000)
    expect(getToken()).toBe('new-token')
    clearSession()
    expect(getToken()).toBeNull()
  })
  it('rejects malformed login responses', () => {
    expect(() => setSession('', 3600)).toThrow()
    expect(() => setSession('token', -1)).toThrow()
    expect(() => setSession('token', Infinity)).toThrow()
    expect(() => setSession('token', Number.MAX_VALUE)).toThrow()
  })

  it('restores the saved token without extending its original expiration', () => {
    vi.useFakeTimers()
    const expiresAt = Date.now() + 2000
    sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ accessToken: 'saved-token', expiresAt }))
    expect(getToken()).toBeNull()
    expect(restoreSession()).toBe('saved-token')
    vi.advanceTimersByTime(1000)
    expect(restoreSession()).toBe('saved-token')
    expect(JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY)).expiresAt).toBe(expiresAt)
    vi.advanceTimersByTime(1000)
    expect(getToken()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
  })

  it.each([
    'not-json', 'null', '[]', '{}',
    JSON.stringify({ accessToken: '', expiresAt: 9999999999999 }),
    JSON.stringify({ accessToken: '   ', expiresAt: 9999999999999 }),
    JSON.stringify({ accessToken: 42, expiresAt: 9999999999999 }),
    JSON.stringify({ accessToken: 'token', expiresAt: '9999999999999' }),
    JSON.stringify({ accessToken: 'token', expiresAt: 1 }),
  ])('discards malformed or expired persisted data: %s', (value) => {
    sessionStorage.setItem(SESSION_STORAGE_KEY, value)
    expect(restoreSession()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
  })

  it('logout removes both copies without clearing unrelated sessionStorage data', () => {
    sessionStorage.setItem('unrelated-setting', 'keep')
    setSession('test-token', 3600)
    clearSession()
    expect(getToken()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
    expect(restoreSession()).toBeNull()
    expect(sessionStorage.getItem('unrelated-setting')).toBe('keep')
  })

  it('does not crash when storage is unavailable and still allows in-memory login/logout', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new DOMException('Disabled', 'SecurityError') })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new DOMException('Full', 'QuotaExceededError') })
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => { throw new DOMException('Disabled', 'SecurityError') })
    expect(restoreSession()).toBeNull()
    expect(() => setSession('test-token', 3600)).not.toThrow()
    expect(getToken()).toBe('test-token')
    expect(() => clearSession()).not.toThrow()
    expect(getToken()).toBeNull()
  })

  it('rechecks expiry on access after a suspended tab wakes up', () => {
    vi.useFakeTimers()
    setSession('test-token', 2)
    vi.setSystemTime(Date.now() + 3000)
    expect(getToken()).toBeNull()
    expect(sessionStorage.getItem(SESSION_STORAGE_KEY)).toBeNull()
  })

  it('does not expire long-lived sessions at the maximum JavaScript timeout', () => {
    vi.useFakeTimers()
    setSession('test-token', 3_000_000)
    vi.advanceTimersByTime(2_147_483_647)
    expect(getToken()).toBe('test-token')
    vi.advanceTimersByTime(3_000_000_000 - 2_147_483_647)
    expect(getToken()).toBeNull()
  })
})
