import { useCallback, useEffect, useRef, useState } from 'react'
import { CheckCircle2, CircleAlert, X } from 'lucide-react'
import { ToastContext } from './toast-context'
export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([])
  const timers = useRef(new Set())
  const dismiss = useCallback((id) => setToasts((items) => items.filter((item) => item.id !== id)), [])
  const notify = useCallback((message, type = 'success') => {
    const id = crypto.randomUUID()
    setToasts((items) => [...items.slice(-3), { id, message, type }])
    const timer = setTimeout(() => { dismiss(id); timers.current.delete(timer) }, 6000)
    timers.current.add(timer)
  }, [dismiss])
  useEffect(() => {
    const current = timers.current
    return () => current.forEach(clearTimeout)
  }, [])
  return <ToastContext.Provider value={notify}>
    {children}
    <div className="toast-container" aria-live="polite" aria-atomic="false">
      {toasts.map(({ id, message, type }) => <div key={id} className={`toast toast-${type}`} role={type === 'error' ? 'alert' : 'status'}>
        {type === 'error' ? <CircleAlert size={20} /> : <CheckCircle2 size={20} />}
        <p>{message}</p><button className="icon-button" onClick={() => dismiss(id)} aria-label="Đóng thông báo"><X size={16} /></button>
      </div>)}
    </div>
  </ToastContext.Provider>
}
