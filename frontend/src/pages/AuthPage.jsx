import { useState } from 'react'
import { Link, Navigate, useLocation } from 'react-router-dom'
import { ArrowRight, Check, Eye, EyeOff, FolderKanban, ListTodo, LoaderCircle, LockKeyhole, Mail } from 'lucide-react'
import { Brand } from '../components/AppLayout'
import { Field, FormError } from '../components/ui'
import { useAuth } from '../context/auth-context'
import { errorMessage, fieldErrors } from '../services/api'

export default function AuthPage({ register = false }) {
  const { user, authenticate } = useAuth()
  const location = useLocation()
  const [values, setValues] = useState({ email: '', password: '', confirmation: '' })
  const [show, setShow] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [errors, setErrors] = useState({})
  const from = location.state?.from
  const destination = typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') && !/^\/(login|register)/.test(from) ? from : '/dashboard'
  if (user) return <Navigate to={destination} replace />
  const change = (event) => setValues((prev) => ({ ...prev, [event.target.name]: event.target.value }))
  const submit = async (event) => {
    event.preventDefault()
    if (busy) return
    setError(''); setErrors({})
    if (new TextEncoder().encode(values.password).length > 72) { setErrors({ password: 'Mật khẩu tối đa 72 byte UTF-8 (ký tự tiếng Việt có thể dùng nhiều byte).' }); return }
    if (register && values.password !== values.confirmation) { setErrors({ confirmation: 'Mật khẩu xác nhận chưa trùng khớp.' }); return }
    setBusy(true)
    try {
      await authenticate(register ? 'register' : 'login', { email: values.email.trim(), password: values.password })
      setValues({ email: '', password: '', confirmation: '' })
    } catch (err) { setError(errorMessage(err)); setErrors(fieldErrors(err)) }
    finally { setBusy(false) }
  }
  return <main className="auth-shell">
    <section className="auth-story"><Link to="/login"><Brand /></Link><div className="auth-story-content"><span className="auth-pill"><span />LÀM VIỆC THÔNG MINH HƠN</span><h1>Mọi công việc.<br />Một không gian.<br /><span>Nhiều tiến bộ hơn.</span></h1><p>Từ ý tưởng đầu tiên đến nhiệm vụ cuối cùng, TaskFlow giúp bạn giữ mọi thứ đúng hướng.</p>
      <div className="auth-features">{['Tổ chức công việc theo từng dự án', 'Theo dõi tiến độ và hạn hoàn thành', 'Tập trung vào những điều quan trọng'].map((text) => <div key={text}><span><Check size={15} /></span>{text}</div>)}</div>
      <div className="auth-art" aria-hidden="true"><div className="art-card art-card-back"><FolderKanban size={20} /><span>Dự án của bạn</span><div className="art-line" /><div className="art-line short" /></div><div className="art-card art-card-front"><span className="art-check"><Check size={22} /></span><div><strong>Sẵn sàng cho ngày mới</strong><small>Cùng hoàn thành mục tiêu hôm nay</small></div><ListTodo size={21} /></div></div>
    </div><p className="auth-copyright">TaskFlow · Task Management</p></section>
    <section className="auth-form-side"><div className="auth-mobile-brand"><Brand /></div><div className="auth-form-card" key={register ? 'register' : 'login'}><span className="auth-form-icon"><LockKeyhole size={25} /></span><h2>{register ? 'Tạo tài khoản mới' : 'Chào mừng trở lại'}</h2><p className="muted">{register ? 'Bắt đầu tổ chức công việc của bạn ngay hôm nay.' : 'Đăng nhập để tiếp tục công việc của bạn.'}</p>
      <form onSubmit={submit} className="auth-form"><FormError message={error} />
        <Field label="Địa chỉ email" error={errors.email}><div className="input-icon"><Mail size={18} /><input name="email" type="email" autoComplete="email" placeholder="ban@example.com" maxLength={254} required value={values.email} onChange={change} disabled={busy} /></div></Field>
        <Field label="Mật khẩu" error={errors.password} hint={register ? 'Từ 8 ký tự, tối đa 72 byte UTF-8.' : undefined}><div className="input-icon"><LockKeyhole size={18} /><input name="password" type={show ? 'text' : 'password'} autoComplete={register ? 'new-password' : 'current-password'} placeholder="Nhập mật khẩu của bạn" minLength={register ? 8 : 1} maxLength={72} required value={values.password} onChange={change} disabled={busy} /><button type="button" className="icon-button" onClick={() => setShow((v) => !v)} aria-label={show ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}>{show ? <EyeOff size={18} /> : <Eye size={18} />}</button></div></Field>
        {register && <Field label="Xác nhận mật khẩu" error={errors.confirmation}><input name="confirmation" type={show ? 'text' : 'password'} autoComplete="new-password" placeholder="Nhập lại mật khẩu" minLength={8} maxLength={72} required value={values.confirmation} onChange={change} disabled={busy} /></Field>}
        <button className="btn btn-primary auth-submit" disabled={busy}>{busy ? <LoaderCircle size={18} className="animate-spin" /> : null}{busy ? 'Đang xử lý…' : register ? 'Tạo tài khoản' : 'Đăng nhập'}{!busy && <ArrowRight size={18} />}</button>
      </form><p className="auth-switch">{register ? 'Đã có tài khoản?' : 'Chưa có tài khoản?'} <Link to={register ? '/login' : '/register'} onClick={() => { setError(''); setErrors({}); setValues({ email: '', password: '', confirmation: '' }) }}>{register ? 'Đăng nhập' : 'Đăng ký ngay'}</Link></p><p className="session-note"><LockKeyhole size={13} />Phiên đăng nhập kết thúc khi bạn tải lại hoặc đóng trang.</p>
    </div><p className="auth-form-footer">Gọn gàng hơn. Tập trung hơn. Hiệu quả hơn.</p></section>
  </main>
}
