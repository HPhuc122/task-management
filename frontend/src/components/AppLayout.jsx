import { useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { ArrowUpRight, CheckCheck, ChevronRight, FolderKanban, LayoutDashboard, ListTodo, LogOut, Menu, Search, ShieldCheck, Tags, X } from 'lucide-react'
import { useAuth } from '../context/auth-context'

const links = [
  { to: '/dashboard', label: 'Tổng quan', icon: LayoutDashboard },
  { to: '/tasks', label: 'Công việc', icon: ListTodo },
  { to: '/projects', label: 'Dự án', icon: FolderKanban },
]
export function Brand() {
  return <span className="brand"><span className="brand-symbol"><CheckCheck size={25} strokeWidth={2.8} /></span><span>Task<span className="text-blue-600">Flow</span><small>QUẢN LÝ CÔNG VIỆC</small></span></span>
}
export default function AppLayout() {
  const { user, isAdmin, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const navigate = useNavigate()
  const location = useLocation()
  const navigation = isAdmin ? [...links, { to: '/categories', label: 'Danh mục', icon: Tags }] : links
  const title = navigation.find((link) => link.to === location.pathname)?.label || 'Trang không tồn tại'
  return <div className="app-shell">
    <a className="skip-link" href="#main-content">Đến nội dung chính</a>
    {open && <button className="sidebar-backdrop" aria-label="Đóng menu" onClick={() => setOpen(false)} />}
    <aside className={`sidebar ${open ? 'sidebar-open' : ''}`} aria-label="Điều hướng chính">
      <div className="sidebar-brand"><Link to="/dashboard" onClick={() => setOpen(false)} aria-label="TaskFlow - Tổng quan"><Brand /></Link><button className="icon-button mobile-only" onClick={() => setOpen(false)} aria-label="Đóng menu"><X size={22} /></button></div>
      <div className="workspace-card"><span className="workspace-avatar">TM</span><div><strong>Task Management</strong><span>Không gian làm việc</span></div><ChevronRight size={16} /></div>
      <p className="nav-caption">QUẢN LÝ</p>
      <nav>{navigation.map(({ to, label, icon: Icon }) => <NavLink key={to} to={to} onClick={() => setOpen(false)} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><Icon size={20} /><span>{label}</span></NavLink>)}</nav>
      <div className="sidebar-bottom"><div className="sidebar-tip"><span className="tip-icon"><ShieldCheck size={22} /></span><strong>{isAdmin ? 'Không gian quản trị' : 'Làm việc có tổ chức'}</strong><p>{isAdmin ? 'Quản lý công việc, dự án và danh mục của hệ thống.' : 'Chia nhỏ công việc. Theo dõi tiến độ. Hoàn thành mục tiêu.'}</p><Link to="/tasks" onClick={() => setOpen(false)}>Đến công việc <ArrowUpRight size={15} /></Link></div>
        <button className="nav-item logout-button" onClick={logout}><LogOut size={19} />Đăng xuất</button>
        <div className="sidebar-user"><span className="avatar">{user.email.slice(0, 2).toUpperCase()}</span><div><strong title={user.email}>{user.email}</strong><span>{isAdmin ? 'Quản trị viên' : 'Thành viên'}</span></div><span className="online-dot" /></div>
      </div>
    </aside>
    <div className="app-main"><header className="topbar">
      <div className="breadcrumb"><button className="icon-button mobile-only" aria-label="Mở menu" aria-expanded={open} onClick={() => setOpen(true)}><Menu size={22} /></button><span className="breadcrumb-root">Không gian làm việc</span><ChevronRight size={15} /><strong>{title}</strong></div>
      <form className="global-search" role="search" onSubmit={(event) => { event.preventDefault(); navigate(`/tasks?q=${encodeURIComponent(query)}`); setQuery('') }}><Search size={17} /><input aria-label="Tìm kiếm công việc" placeholder="Tìm kiếm công việc…" value={query} onChange={(event) => setQuery(event.target.value)} /><kbd>↵</kbd></form>
      <span className="role-label">{isAdmin ? <ShieldCheck size={15} /> : <span className="online-dot" />}{user.role}</span><span className="avatar topbar-avatar" title={user.email}>{user.email.slice(0, 2).toUpperCase()}</span>
    </header><main id="main-content" className="page-content" key={`${user.id}-${user.role}`}><Outlet /></main><footer className="app-footer"><span>TaskFlow · Không gian cho những việc quan trọng</span><span>Đồ án Task Management</span></footer></div>
  </div>
}
