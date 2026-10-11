import { Link } from 'react-router-dom'
export default function NotFoundPage() {
  return <div className="empty-state"><p className="eyebrow">404</p><h1>Không tìm thấy trang</h1><p>Đường dẫn này không tồn tại.</p><Link to="/dashboard" className="btn btn-primary">Về tổng quan</Link></div>
}
