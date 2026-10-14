import { useEffect, useState } from 'react'
import { Download, Trash2 } from 'lucide-react'
import Modal from './Modal'
import AttachmentIcon from './AttachmentIcon'
import { Spinner } from './ui'
import { attachmentService } from '../services/resources'
import { attachmentError, attachmentFormat, formatAttachmentSize } from '../lib/attachments'

// Generated document markup stays in a scriptless, network-isolated iframe.
async function renderDocx(blob) {
  const { renderAsync } = await import('docx-preview')
  const document = window.document.implementation.createHTMLDocument('')
  await renderAsync(blob, document.body, document.head, {
    useBase64URL: true, renderAltChunks: false, breakPages: true,
    ignoreLastRenderedPageBreak: false,
  })
  const policy = "default-src 'none'; img-src data:; font-src data:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'"
  return `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="Content-Security-Policy" content="${policy}"><style>body{margin:0;background:#eef2f7} .docx-wrapper{padding:20px!important;min-width:fit-content}</style>${document.head.innerHTML}</head><body>${document.body.innerHTML}</body></html>`
}

export default function AttachmentPreview({ taskId, file, onClose, onDownload, downloading, downloadError, onDelete, deleteDisabled }) {
  const [state, setState] = useState({ loading: true, error: '', url: '', html: '' })
  const { kind, mime, label } = attachmentFormat(file)
  useEffect(() => {
    const controller = new AbortController()
    let url
    const load = async () => {
      try {
        if (!['image', 'pdf', 'docx'].includes(kind)) throw new Error('unsupported')
        const blob = await attachmentService.download(taskId, file.id, controller.signal)
        if (controller.signal.aborted) return
        // Never display HTML/SVG or another active format as a document preview.
        const type = blob.type?.split(';')[0].toLowerCase()
        if (type && type !== mime && type !== 'application/octet-stream') throw new Error('invalid content type')
        if (kind === 'docx') {
          const html = await renderDocx(blob)
          if (!controller.signal.aborted) setState({ loading: false, error: '', html, url: '' })
        } else if (kind === 'pdf' && navigator.pdfViewerEnabled === false) {
          setState({ loading: false, error: 'Trình duyệt này không hỗ trợ xem PDF. Vui lòng tải tệp xuống để đọc.', url: '', html: '' })
        } else {
          url = URL.createObjectURL(new Blob([blob], { type: mime }))
          setState({ loading: false, error: '', url, html: '' })
        }
      } catch (error) {
        if (!controller.signal.aborted) setState({ loading: false, error: `Không thể xem trước tệp. ${attachmentError(error)}`, url: '', html: '' })
      }
    }
    load()
    return () => { controller.abort(); if (url) URL.revokeObjectURL(url) }
  }, [taskId, file.id, kind, mime])

  const failed = () => setState((previous) => ({ ...previous, loading: false, error: 'Không thể hiển thị tệp này. Bạn có thể tải xuống để mở bằng ứng dụng phù hợp.' }))
  const download = <button type="button" className="btn btn-primary" disabled={downloading} onClick={() => onDownload(file)}><Download size={17} />{downloading ? 'Đang tải…' : 'Tải xuống'}</button>
  return <Modal className="attachment-preview" title={<span className="attachment-preview-title"><AttachmentIcon file={file} /><span>{file.fileName}</span></span>}
    description={`${label} · ${formatAttachmentSize(file.sizeBytes)}`} onClose={onClose}
    headerActions={<>{download}<button type="button" className="icon-button danger" disabled={deleteDisabled} aria-label={`Xóa ${file.fileName}`} onClick={() => onDelete(file)}><Trash2 size={18} /></button></>}>
    {downloadError && <p className="attachment-preview-note field-error" role="alert">{downloadError}</p>}
    <div className="attachment-preview-body">
      {state.loading && <Spinner label="Đang tải bản xem trước…" />}
      {state.error && <div className="attachment-preview-fallback" role="alert"><p>{state.error}</p>{download}</div>}
      {!state.error && state.url && kind === 'image' && <div className="attachment-image-canvas"><img src={state.url} alt={file.fileName} onError={failed} /></div>}
      {!state.error && state.url && kind === 'pdf' && <><iframe className="attachment-document" src={state.url} title={`Xem PDF: ${file.fileName}`} onError={failed} /><p className="attachment-preview-note">Nếu PDF không hiển thị, hãy dùng nút Tải xuống để mở trên thiết bị.</p></>}
      {!state.error && state.html && <iframe className="attachment-document" sandbox="" srcDoc={state.html} title={`Xem DOCX: ${file.fileName}`} onError={failed} />}
    </div>
  </Modal>
}
