import { useCallback, useEffect, useState } from 'react'
import { AuthContext } from './auth-context'
import { useToast } from './toast-context'
import { authService } from '../services/resources'
import { clearSession, getToken, setSession, subscribeSession } from '../services/session'
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const notify = useToast()
  const refreshUser = useCallback(async () => {
    const token = getToken()
    if (!token) return
    try {
      const current = await authService.me()
      if (token === getToken()) setUser(current)
    } catch { /* 401 is handled centrally; transient errors preserve the session. */ }
  }, [])
  useEffect(() => subscribeSession((reason) => {
    setUser(null)
    if (reason === 'expired') notify('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.', 'error')
  }), [notify])
  useEffect(() => {
    window.addEventListener('focus', refreshUser)
    window.addEventListener('auth:refresh-role', refreshUser)
    return () => {
      window.removeEventListener('focus', refreshUser)
      window.removeEventListener('auth:refresh-role', refreshUser)
    }
  }, [refreshUser])
  const authenticate = async (mode, credentials) => {
    const result = await authService[mode](credentials)
    setSession(result.accessToken, result.expiresIn)
    try {
      const current = await authService.me()
      if (!getToken()) throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng thử lại.')
      setUser(current)
      notify(mode === 'register' ? 'Tạo tài khoản thành công. Chào mừng bạn!' : 'Đăng nhập thành công.')
    } catch (error) { clearSession('failed'); throw error }
  }
  const logout = () => { clearSession(); notify('Bạn đã đăng xuất.') }
  return <AuthContext.Provider value={{ user, isAdmin: user?.role === 'ADMIN', authenticate, logout }}>{children}</AuthContext.Provider>
}
