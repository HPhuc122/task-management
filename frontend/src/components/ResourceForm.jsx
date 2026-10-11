import { useState } from 'react'
import { LoaderCircle, Save } from 'lucide-react'
import { useAuth } from '../context/auth-context'
import { useToast } from '../context/toast-context'
import { categoryService, projectService } from '../services/resources'
import { errorMessage, fieldErrors } from '../services/api'
import Modal from './Modal'
import { Field, FormError } from './ui'

export default function ResourceForm({ kind, item, onClose, onSuccess }) {
  const isCategory = kind === 'category'
  const { user, isAdmin } = useAuth()
  const name = isCategory ? 'danh mục' : 'dự án'
  const service = isCategory ? categoryService : projectService
  const [values, setValues] = useState({ name: item?.name || '', description: item?.description || '', userId: item?.userId || user.id })
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [errors, setErrors] = useState({})
  const notify = useToast()
  const change = (event) => setValues((prev) => ({ ...prev, [event.target.name]: event.target.value }))
  const submit = async (event) => {
    event.preventDefault()
    if (busy) return
    setError(''); setErrors({})
    if (!values.name.trim()) { setErrors({ name: 'Vui lòng nhập tên.' }); return }
    setBusy(true)
    try {
      const body = { name: values.name.trim(), description: values.description || null, ...(!isCategory ? { userId: Number(values.userId) } : {}) }
      if (item) await service.update(item.id, body)
      else await service.create(body)
      notify(`Đã ${item ? 'cập nhật' : 'tạo'} ${name}.`); onSuccess(); onClose()
    } catch (err) { setError(errorMessage(err)); setErrors(fieldErrors(err)); setBusy(false) }
  }
  return <Modal title={`${item ? 'Chỉnh sửa' : 'Tạo'} ${name}`} onClose={onClose} busy={busy}><form onSubmit={submit}>
    <fieldset className="modal-body form-stack" disabled={busy}><FormError message={error} />
      <Field label={`Tên ${name} *`} error={errors.name}><input name="name" required autoFocus maxLength={isCategory ? 100 : 255} placeholder={`Nhập tên ${name}`} value={values.name} onChange={change} /></Field>
      <Field label="Mô tả" error={errors.description}><textarea name="description" rows={4} maxLength={isCategory ? 2000 : undefined} placeholder={`Mô tả ngắn về ${name}…`} value={values.description} onChange={change} /></Field>
      {!isCategory && isAdmin && <Field label="ID người sở hữu *" error={errors.userId} hint="Nhập ID tài khoản đã tồn tại trong hệ thống."><input name="userId" type="number" required min={1} step={1} value={values.userId} onChange={change} /></Field>}
    </fieldset><div className="modal-footer"><button type="button" className="btn btn-secondary" disabled={busy} onClick={onClose}>Hủy</button><button className="btn btn-primary" disabled={busy}>{busy ? <LoaderCircle size={17} className="animate-spin" /> : <Save size={17} />}{item ? 'Lưu thay đổi' : `Tạo ${name}`}</button></div>
  </form></Modal>
}
