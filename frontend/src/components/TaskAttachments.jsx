import { useEffect, useRef, useState } from 'react'
import { Download, Eye, LoaderCircle, Paperclip, RefreshCw, Trash2, UploadCloud } from 'lucide-react'
import { useToast } from '../context/toast-context'
import { attachmentService } from '../services/resources'
import { ATTACHMENT_ACCEPT, attachmentError, attachmentFormat, formatAttachmentSize, saveAttachment, validateAttachment } from '../lib/attachments'
import AttachmentIcon from './AttachmentIcon'
import AttachmentPreview from './AttachmentPreview'
import DeleteDialog from './DeleteDialog'

export default function TaskAttachments({ taskId }) {
  const notify = useToast()
  const [list, setList] = useState({ files: [], loading: true, error: '' })
  const [version, setVersion] = useState(0)
  const [uploading, setUploading] = useState(false)
  const [uploadError, setUploadError] = useState('')
  const [downloading, setDownloading] = useState([])
  const [downloadError, setDownloadError] = useState(null)
  const [preview, setPreview] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const requests = useRef(new Set())
  const uploadLock = useRef(false)
  const downloadLocks = useRef(new Set())

  useEffect(() => {
    const pending = requests.current
    return () => { pending.forEach((controller) => controller.abort()); pending.clear() }
  }, [])

  useEffect(() => {
    const controller = new AbortController()
    Promise.resolve().then(async () => {
      if (controller.signal.aborted) return
      setList((previous) => ({ ...previous, loading: true, error: '' }))
      try {
        const files = await attachmentService.list(taskId, controller.signal)
        if (!controller.signal.aborted) setList({ files, loading: false, error: '' })
      } catch (error) {
        if (!controller.signal.aborted) setList((previous) => ({ ...previous, loading: false, error: attachmentError(error) }))
      }
    })
    return () => controller.abort()
  }, [taskId, version])

  const upload = async (event) => {
    const input = event.target
    const file = input.files?.[0]
    input.value = ''
    if (!file || uploadLock.current || list.loading) return
    const validation = validateAttachment(file)
    setUploadError(validation)
    if (validation) return
    uploadLock.current = true
    setUploading(true)
    const controller = new AbortController()
    requests.current.add(controller)
    try {
      const saved = await attachmentService.upload(taskId, file, controller.signal)
      if (controller.signal.aborted) return
      setList((previous) => ({ ...previous, files: [...previous.files, saved] }))
      notify('Tải tệp đính kèm thành công.')
      // Preview errors remain independent of a successful upload.
      if (attachmentFormat(saved).kind === 'image') setPreview(saved)
    } catch (error) {
      if (!controller.signal.aborted) { const message = attachmentError(error); setUploadError(message); notify(message, 'error') }
    } finally {
      requests.current.delete(controller)
      uploadLock.current = false
      if (!controller.signal.aborted) setUploading(false)
    }
  }

  const download = async (file) => {
    if (downloadLocks.current.has(file.id)) return
    downloadLocks.current.add(file.id)
    setDownloadError(null)
    setDownloading((ids) => [...ids, file.id])
    const controller = new AbortController()
    requests.current.add(controller)
    try {
      const blob = await attachmentService.download(taskId, file.id, controller.signal)
      if (!controller.signal.aborted) saveAttachment(blob, file.fileName)
    } catch (error) {
      if (!controller.signal.aborted) {
        const message = attachmentError(error)
        setDownloadError({ id: file.id, message })
        notify(message, 'error')
      }
    } finally {
      requests.current.delete(controller)
      downloadLocks.current.delete(file.id)
      if (!controller.signal.aborted) setDownloading((ids) => ids.filter((id) => id !== file.id))
    }
  }

  const remove = async () => {
    const controller = new AbortController()
    requests.current.add(controller)
    try { await attachmentService.remove(taskId, deleting.id, controller.signal) }
    catch (error) {
      if (!controller.signal.aborted) notify(attachmentError(error), 'error')
      throw error
    } finally { requests.current.delete(controller) }
  }

  return <section className="task-attachments col-span-full" aria-label="Tệp đính kèm">
    <div className="attachment-section-heading"><div><Paperclip size={18} /><h3>Tệp đính kèm</h3><span className="attachment-count" aria-label={`${list.files.length} tệp đính kèm`}>{list.files.length}</span></div>
      <button type="button" className="icon-button" aria-label="Tải lại danh sách tệp" disabled={list.loading || uploading || Boolean(deleting)} onClick={() => { setList((previous) => ({ ...previous, loading: true })); setVersion((value) => value + 1) }}><RefreshCw size={16} /></button></div>
    <label className="attachment-upload"><UploadCloud size={28} /><span>{uploading ? 'Đang tải tệp lên…' : 'Chọn tệp để tải lên'}</span><small>JPG, JPEG, PNG, WEBP, GIF, PDF, DOCX, ZIP · Tối đa 10 MB/tệp</small>
      <input type="file" className="sr-only" accept={ATTACHMENT_ACCEPT} disabled={uploading || list.loading || Boolean(deleting)} onChange={upload} aria-label="Chọn tệp đính kèm" />
    </label>
    {uploading && <p role="status" className="attachment-status"><LoaderCircle size={15} className="animate-spin" />Đang tải tệp lên…</p>}
    {uploadError && <p className="field-error" role="alert">{uploadError}</p>}
    {downloadError && preview?.id !== downloadError.id && <p className="field-error" role="alert">{downloadError.message}</p>}
    {list.loading && <p className="attachment-status" role="status">Đang tải danh sách tệp…</p>}
    {list.error && <p className="field-error" role="alert">{list.error}</p>}
    {!list.loading && !list.error && !list.files.length && <p className="attachment-empty">Chưa có tệp đính kèm. Thêm tài liệu hoặc hình ảnh để mọi thông tin ở cùng một nơi.</p>}
    <div className="attachment-list">{list.files.map((file) => {
      const { kind, label } = attachmentFormat(file)
      const canPreview = ['image', 'pdf', 'docx'].includes(kind)
      const isDownloading = downloading.includes(file.id)
      const date = file.createdAt ? new Date(file.createdAt) : null
      return <div key={file.id} className="attachment-row">
        <AttachmentIcon file={file} />
        <div className="attachment-details"><button type="button" className="attachment-name" title={file.fileName} disabled={!canPreview && isDownloading} onClick={() => canPreview ? setPreview(file) : download(file)}>{file.fileName}</button>
          <p>{label} · {formatAttachmentSize(file.sizeBytes)}{date && !Number.isNaN(date.getTime()) && <> · <time dateTime={file.createdAt}>{date.toLocaleDateString('vi-VN')}</time></>}</p></div>
        <div className="attachment-actions">
          {canPreview && <button type="button" className="icon-button" title="Xem" aria-label={`Xem ${file.fileName}`} onClick={() => setPreview(file)}><Eye size={17} /></button>}
          <button type="button" className="icon-button" title="Tải xuống" aria-label={`Tải xuống ${file.fileName}`} disabled={isDownloading} onClick={() => download(file)}>{isDownloading ? <LoaderCircle size={17} className="animate-spin" /> : <Download size={17} />}</button>
          <button type="button" className="icon-button danger" title="Xóa" aria-label={`Xóa ${file.fileName}`} disabled={uploading || list.loading || Boolean(deleting)} onClick={() => setDeleting(file)}><Trash2 size={17} /></button>
        </div>
      </div>
    })}</div>
    {preview && <AttachmentPreview key={preview.id} taskId={taskId} file={preview} onClose={() => setPreview(null)} onDownload={download} downloading={downloading.includes(preview.id)} downloadError={downloadError?.id === preview.id ? downloadError.message : ''} onDelete={setDeleting} deleteDisabled={uploading || list.loading || Boolean(deleting)} />}
    {deleting && <DeleteDialog name={deleting.fileName} onClose={() => setDeleting(null)} onDelete={remove} formatError={attachmentError} onSuccess={() => {
      setList((previous) => ({ ...previous, files: previous.files.filter((file) => file.id !== deleting.id) }))
      if (preview?.id === deleting.id) setPreview(null)
    }} />}
  </section>
}
