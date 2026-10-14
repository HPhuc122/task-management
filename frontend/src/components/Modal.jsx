import { useEffect, useId, useRef } from 'react'
import { createPortal } from 'react-dom'
import { X } from 'lucide-react'

let openDialogs = 0
let bodyOverflow = ''

export default function Modal({ title, description, children, onClose, busy = false, wide = false, className = '', headerActions }) {
  const dialog = useRef(null)
  const titleId = useId()
  const descriptionId = useId()
  useEffect(() => {
    const previous = document.activeElement
    const node = dialog.current
    node.showModal()
    if (openDialogs++ === 0) bodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      node.close()
      if (--openDialogs === 0) document.body.style.overflow = bodyOverflow
      if (previous?.isConnected) previous.focus()
    }
  }, [])
  return createPortal(<dialog ref={dialog} className={`modal ${wide ? 'modal-wide' : ''} ${className}`} aria-labelledby={titleId} aria-describedby={description ? descriptionId : undefined}
    onCancel={(event) => { event.preventDefault(); if (!busy) onClose() }}>
    <div className="modal-heading"><div className="modal-title-group"><h2 id={titleId}>{title}</h2>{description && <p id={descriptionId}>{description}</p>}</div><div className="modal-header-actions">{headerActions}<button type="button" className="icon-button" disabled={busy} aria-label="Đóng cửa sổ" onClick={onClose}><X size={21} /></button></div></div>
    {children}
  </dialog>, document.body)
}
