import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { ShieldX } from 'lucide-react'
import { useAuth } from '../context/auth-context'
import { Link } from 'react-router-dom'
import AuthSessionStatus from './AuthSessionStatus'
export default function ProtectedRoute({ adminOnly = false }) {
  const { user, isAdmin, authLoading, authError } = useAuth()
  const location = useLocation()
  if (authLoading || (!user && authError)) return <AuthSessionStatus />
  if (!user) return <Navigate to="/login" state={{ from: location.pathname + location.search + location.hash }} replace />
  if (adminOnly && !isAdmin) return <div className="empty-state"><ShieldX size={40} /><h1>Không có quyền truy cập</h1><p>Chức năng này dành riêng cho quản trị viên.</p><Link className="btn btn-primary" to="/dashboard">Về tổng quan</Link></div>
  return <Outlet />
}
