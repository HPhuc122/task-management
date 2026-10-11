import { useState } from 'react'
import { LoaderCircle, Trash2 } from 'lucide-react'
import Modal from './Modal'
import { FormError } from './ui'
import { errorMessage } from '../services/api'
import { useToast } from '../context/toast-context'
export default function DeleteDialog({ name, onDelete, onClose, onSuccess, description }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const notify = useToast()
  const submit = async (event) => {
    event.preventDefault()
    if (busy) return
    setBusy(true); setError('')
    try { await onDelete(); notify('Đã xóa thành công.'); onSuccess(); onClose() }
    catch (err) { setError(errorMessage(err)); setBusy(false) }
  }
  return <Modal title="Xác nhận xóa" onClose={onClose} busy={busy}>
    <form onSubmit={submit}><div className="modal-body"><div className="delete-icon"><Trash2 size={24} /></div><p>Bạn muốn xóa <strong className="wrap-anywhere">{name}</strong>?</p><p className="muted mt-2">{description || 'Thao tác này không thể hoàn tác.'}</p><FormError message={error} /></div>
      <div className="modal-footer"><button type="button" className="btn btn-secondary" disabled={busy} onClick={onClose}>Hủy</button><button className="btn btn-danger" disabled={busy}>{busy ? <LoaderCircle size={16} className="animate-spin" /> : <Trash2 size={16} />}Xóa dữ liệu</button></div>
    </form>
  </Modal>
}
