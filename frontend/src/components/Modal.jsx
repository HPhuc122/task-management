import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'

export default function Modal({ title, description, children, onClose, busy = false, wide = false }) {
  const dialog = useRef(null)
  useEffect(() => {
    const previous = document.activeElement
    const node = dialog.current
    node.showModal()
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      node.close()
      document.body.style.overflow = overflow
      previous?.focus()
    }
  }, [])
  return createPortal(<dialog ref={dialog} className={`modal ${wide ? 'modal-wide' : ''}`} aria-labelledby="modal-title" aria-describedby={description ? 'modal-description' : undefined}
    onCancel={(event) => { event.preventDefault(); if (!busy) onClose() }}>
    <div className="modal-heading"><div><h2 id="modal-title">{title}</h2>{description && <p id="modal-description">{description}</p>}</div><button className="icon-button" disabled={busy} aria-label="Đóng cửa sổ" onClick={onClose}><X size={21} /></button></div>
    {children}
  </dialog>, document.body)
}
