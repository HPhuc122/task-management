import { useAuth } from '../context/auth-context'
import { ErrorState, Spinner } from './ui'

export default function AuthSessionStatus() {
  const { authLoading, authError, refreshUser } = useAuth()
  return <main aria-label="Kiểm tra phiên đăng nhập">
    {authLoading ? <Spinner label="Đang khôi phục phiên đăng nhập…" /> : <ErrorState error={authError} retry={refreshUser} />}
  </main>
}
