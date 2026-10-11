import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearSession, getToken, setSession, subscribeSession } from '../services/session'
afterEach(() => { clearSession(); vi.useRealTimers() })
describe('JWT session lifecycle', () => {
  it('expires on time, clears the token, and notifies subscribers', () => {
    vi.useFakeTimers()
    const listener = vi.fn()
    const unsubscribe = subscribeSession(listener)
    setSession('test-token', 2)
    expect(getToken()).toBe('test-token')
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
    vi.advanceTimersByTime(2000)
    expect(getToken()).toBeNull()
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
  })
})
