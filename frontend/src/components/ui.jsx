import { Children, cloneElement, isValidElement, useId } from 'react'
import { CircleAlert, Inbox, LoaderCircle, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-react'
import { errorMessage } from '../services/api'
import { PRIORITIES, STATUSES } from '../lib/tasks'

export function Spinner({ label = 'Đang tải dữ liệu…' }) {
  return <div className="loading-state" role="status"><LoaderCircle className="animate-spin text-blue-600" size={26} /><span>{label}</span></div>
}
export function ErrorState({ error, retry }) {
  return <div className="error-state" role="alert"><CircleAlert size={28} /><h3>Chưa thể tải dữ liệu</h3><p>{errorMessage(error)}</p>
    {retry && <button className="btn btn-secondary" onClick={retry}><RefreshCw size={16} />Thử lại</button>}
  </div>
}
export function EmptyState({ title = 'Chưa có dữ liệu', description, action, actionLabel }) {
  return <div className="empty-state"><span className="empty-icon"><Inbox size={30} /></span><h3>{title}</h3><p>{description || 'Dữ liệu mới sẽ xuất hiện tại đây.'}</p>
    {action && <button className="btn btn-primary" onClick={action}><Plus size={17} />{actionLabel || 'Tạo mới'}</button>}
  </div>
}
export function PageHeader({ eyebrow = 'KHÔNG GIAN LÀM VIỆC', title, description, children }) {
  return <div className="page-heading"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p className="page-description">{description}</p></div><div className="page-actions">{children}</div></div>
}
export function Badge({ value, priority = false }) {
  return <span className={`badge ${priority ? 'priority' : 'status'}-${value}`}><span className="badge-dot" />{(priority ? PRIORITIES : STATUSES)[value] || value}</span>
}
export function Field({ label, error, hint, children, className = '' }) {
  const id = useId()
  const bindInput = (child) => {
    if (!isValidElement(child)) return child
    if (['input', 'select', 'textarea'].includes(child.type)) return cloneElement(child, {
      id, 'aria-invalid': Boolean(error), 'aria-describedby': [error ? `${id}-error` : '', hint ? `${id}-hint` : ''].filter(Boolean).join(' ') || undefined,
    })
    if (child.props.children) return cloneElement(child, {}, Children.map(child.props.children, bindInput))
    return child
  }
  return <div className={`field ${className}`}><label htmlFor={id} className="field-label">{label}</label>{Children.map(children, bindInput)}{error && <span id={`${id}-error`} className="field-error" role="alert">{error}</span>}{hint && <span id={`${id}-hint`} className="field-hint">{hint}</span>}</div>
}
export function FormError({ message }) {
  return message ? <div className="form-error" role="alert"><CircleAlert size={18} /><span>{message}</span></div> : null
}
export function EditActions({ onEdit, onDelete, label }) {
  return <div className="row-actions"><button className="icon-button" onClick={onEdit} title="Chỉnh sửa" aria-label={`Sửa ${label}`}><Pencil size={16} /></button><button className="icon-button danger" onClick={onDelete} title="Xóa" aria-label={`Xóa ${label}`}><Trash2 size={16} /></button></div>
}
