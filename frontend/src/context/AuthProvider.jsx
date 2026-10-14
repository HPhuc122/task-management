import { useCallback, useEffect, useRef, useState } from 'react'
import { AuthContext } from './auth-context'
import { useToast } from './toast-context'
import { authService } from '../services/resources'
import { clearSession, getToken, restoreSession, setSession, subscribeSession } from '../services/session'
export function AuthProvider({ children }) {
  const [auth, setAuth] = useState(() => ({ user: null, authLoading: Boolean(restoreSession()), authError: null }))
  const request = useRef(null)
  const notify = useToast()
  const refreshUser = useCallback(async () => {
    const token = getToken()
    if (!token) return
    request.current?.abort()
    const controller = new AbortController()
    request.current = controller
    setAuth((previous) => ({ ...previous, authLoading: !previous.user, authError: null }))
    try {
      const current = await authService.me(controller.signal)
      if (!controller.signal.aborted && token === getToken()) {
        setAuth({ user: current, authLoading: false, authError: null })
        return current
      }
    } catch (error) {
      // The Axios interceptor clears only the current session on 401.
      if (!controller.signal.aborted && token === getToken()) {
        setAuth((previous) => ({ ...previous, authLoading: false, authError: error }))
      }
    }
  }, [])
  useEffect(() => subscribeSession((reason) => {
    request.current?.abort()
    setAuth({ user: null, authLoading: false, authError: null })
    if (reason === 'expired') notify('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.', 'error')
  }), [notify])
  useEffect(() => {
    let active = true
    Promise.resolve().then(() => { if (active) refreshUser() })
    window.addEventListener('focus', refreshUser)
    window.addEventListener('auth:refresh-role', refreshUser)
    return () => {
      active = false
      request.current?.abort()
      window.removeEventListener('focus', refreshUser)
      window.removeEventListener('auth:refresh-role', refreshUser)
    }
  }, [refreshUser])
  const authenticate = async (mode, credentials) => {
    const result = await authService[mode](credentials)
    setSession(result.accessToken, result.expiresIn)
    const current = await refreshUser()
    if (current) notify(mode === 'register' ? 'Tạo tài khoản thành công. Chào mừng bạn!' : 'Đăng nhập thành công.')
  }
  const logout = () => { clearSession(); notify('Bạn đã đăng xuất.') }
  return <AuthContext.Provider value={{ ...auth, isAdmin: auth.user?.role === 'ADMIN', authenticate, logout, refreshUser }}>{children}</AuthContext.Provider>
}
