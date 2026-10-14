import { StrictMode } from 'react'
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
import TaskAttachments from '../components/TaskAttachments'
import TaskForm from '../components/TaskForm'
import { ToastContext } from '../context/toast-context'
import { AuthContext } from '../context/auth-context'
import { attachmentService, projectService, taskService } from '../services/resources'
import { attachmentError, MAX_ATTACHMENT_SIZE, saveAttachment, validateAttachment } from '../lib/attachments'

vi.mock('docx-preview', () => ({ renderAsync: vi.fn() }))
import { renderAsync } from 'docx-preview'

const image = { id: 1, fileName: 'Ảnh công việc.PNG', contentType: 'image/png', sizeBytes: 2048, createdAt: '2026-10-14T03:00:00Z' }
const pdf = { id: 2, fileName: 'Báo cáo.pdf', contentType: 'application/pdf', sizeBytes: 3000 }
const docx = { id: 3, fileName: 'Tài liệu.docx', contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document', sizeBytes: 4000 }
const zip = { id: 4, fileName: 'Tệp nén.zip', contentType: 'application/zip', sizeBytes: 5000 }
const notify = vi.fn()
const failure = (status) => new AxiosError('private token and storage details', 'ERR_BAD_RESPONSE', {}, null, { status, data: new Blob(['private']), headers: {} })
function setup(files = [image, pdf, docx, zip]) {
  attachmentService.list.mockResolvedValue(files)
  return render(<ToastContext.Provider value={notify}><TaskAttachments taskId={15} /></ToastContext.Provider>)
}
async function loaded() { await waitFor(() => expect(screen.getByLabelText('Chọn tệp đính kèm')).toBeEnabled()) }
async function preview(file) {
  await loaded()
  await userEvent.click(screen.getByRole('button', { name: `Xem ${file.fileName}` }))
  return screen.findByRole('dialog')
}

beforeEach(() => {
  notify.mockReset()
  vi.spyOn(attachmentService, 'list').mockResolvedValue([])
  vi.spyOn(attachmentService, 'upload').mockResolvedValue(zip)
  vi.spyOn(attachmentService, 'download').mockImplementation(async (_taskId, id) => new Blob(['test'], { type: [image, pdf, docx, zip].find((file) => file.id === id)?.contentType }))
  vi.spyOn(attachmentService, 'remove').mockResolvedValue({})
  vi.stubGlobal('URL', class extends URL {
    static createObjectURL = vi.fn(() => 'blob:attachment-test')
    static revokeObjectURL = vi.fn()
  })
  renderAsync.mockReset().mockImplementation(async (_blob, body, styles) => {
    body.innerHTML = '<section class="docx"><p>Nội dung Word</p><table><tbody><tr><td>Bảng</td></tr></tbody></table></section>'
    styles.innerHTML = '<style>.docx { color: black; }</style>'
  })
})
afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers() })

describe('Task attachment management', () => {
  it('uploads a valid file and updates the count without saving the Task', async () => {
    setup([])
    await loaded()
    const file = new File(['archive'], zip.fileName, { type: zip.contentType })
    await userEvent.upload(screen.getByLabelText('Chọn tệp đính kèm'), file)
    await screen.findByRole('button', { name: zip.fileName, exact: true })
    expect(attachmentService.upload).toHaveBeenCalledWith(15, file, expect.any(AbortSignal))
    expect(screen.getByLabelText('1 tệp đính kèm')).toBeInTheDocument()
    expect(notify).toHaveBeenCalledWith('Tải tệp đính kèm thành công.')
  })

  it.each([
    ['empty.pdf', 0, 'tệp rỗng'],
    ['large.pdf', MAX_ATTACHMENT_SIZE + 1, 'giới hạn 10 MB'],
    ['unsafe.html', 10, 'Định dạng không được hỗ trợ'],
    ['image.svg', 10, 'Định dạng không được hỗ trợ'],
    ['document', 10, 'Định dạng không được hỗ trợ'],
  ])('rejects invalid upload %s before calling the API', async (name, size, message) => {
    setup([])
    await loaded()
    const file = new File(['test'], name)
    Object.defineProperty(file, 'size', { value: size })
    fireEvent.change(screen.getByLabelText('Chọn tệp đính kèm'), { target: { files: [file] } })
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(attachmentService.upload).not.toHaveBeenCalled()
  })

  it('accepts exactly 10 MB and every supported extension, case-insensitively', () => {
    for (const extension of ['JPG', 'jpeg', 'png', 'webp', 'gif', 'pdf', 'docx', 'zip']) {
      expect(validateAttachment({ name: `Tài liệu.${extension}`, size: MAX_ATTACHMENT_SIZE })).toBe('')
    }
  })

  it('shows image preview in a modal and revokes its URL when closed', async () => {
    setup()
    const dialog = await preview(image)
    expect(await within(dialog).findByRole('img', { name: image.fileName })).toHaveAttribute('src', 'blob:attachment-test')
    expect(dialog.tagName).toBe('DIALOG')
    const signal = attachmentService.download.mock.calls[0][2]
    await userEvent.click(within(dialog).getByRole('button', { name: 'Đóng cửa sổ' }))
    expect(signal.aborted).toBe(true)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:attachment-test')
  })

  it('renders PDF in a document iframe', async () => {
    setup()
    const dialog = await preview(pdf)
    expect(await within(dialog).findByTitle(`Xem PDF: ${pdf.fileName}`)).toHaveAttribute('src', 'blob:attachment-test')
    expect(within(dialog).getByRole('button', { name: 'Tải xuống', exact: true })).toBeEnabled()
  })

  it('offers a download if the browser cannot show PDF', async () => {
    vi.spyOn(navigator, 'pdfViewerEnabled', 'get').mockReturnValue(false)
    setup()
    const dialog = await preview(pdf)
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('không hỗ trợ xem PDF')
    expect(within(dialog).queryByTitle(`Xem PDF: ${pdf.fileName}`)).not.toBeInTheDocument()
  })

  it('renders DOCX locally in a sandbox with HTML chunks and remote resources disabled', async () => {
    setup()
    const dialog = await preview(docx)
    const frame = await within(dialog).findByTitle(`Xem DOCX: ${docx.fileName}`)
    expect(frame).toHaveAttribute('sandbox', '')
    expect(frame.getAttribute('srcdoc')).toContain('Nội dung Word')
    expect(frame.getAttribute('srcdoc')).toContain("default-src 'none'")
    expect(renderAsync).toHaveBeenCalledWith(expect.any(Blob), expect.any(HTMLElement), expect.any(HTMLElement), expect.objectContaining({ renderAltChunks: false, useBase64URL: true }))
  })

  it('handles failed DOCX rendering without exposing private exception details', async () => {
    renderAsync.mockRejectedValue(new Error('private storage token'))
    setup()
    const dialog = await preview(docx)
    const alert = await within(dialog).findByRole('alert')
    expect(alert).toHaveTextContent('Không thể xem trước')
    expect(alert).not.toHaveTextContent('private')
    expect(within(alert).getByRole('button', { name: 'Tải xuống' })).toBeEnabled()
  })

  it('rejects active content returned for a supported filename', async () => {
    attachmentService.download.mockResolvedValue(new Blob(['<script>test</script>'], { type: 'text/html' }))
    setup()
    const dialog = await preview(pdf)
    expect(await within(dialog).findByRole('alert')).toHaveTextContent('Không thể xem trước')
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })

  it('downloads ZIP by filename using authenticated service without opening a preview', async () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function () {
      expect(this.download).toBe(zip.fileName)
      expect(this.href).toBe('blob:attachment-test')
    })
    setup()
    await loaded()
    await userEvent.click(screen.getByRole('button', { name: zip.fileName, exact: true }))
    await waitFor(() => expect(click).toHaveBeenCalledOnce())
    expect(attachmentService.download).toHaveBeenCalledWith(15, zip.id, expect.any(AbortSignal))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('eventually revokes download URLs', () => {
    vi.useFakeTimers()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    saveAttachment(new Blob(['zip']), zip.fileName)
    expect(URL.revokeObjectURL).not.toHaveBeenCalled()
    vi.runAllTimers()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:attachment-test')
    expect(document.querySelector('a[download]')).toBeNull()
  })

  it('only deletes after confirmation and updates the list and count', async () => {
    setup([zip])
    await loaded()
    await userEvent.click(screen.getByRole('button', { name: `Xóa ${zip.fileName}` }))
    expect(attachmentService.remove).not.toHaveBeenCalled()
    await userEvent.click(screen.getByRole('button', { name: 'Xóa dữ liệu' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(attachmentService.remove).toHaveBeenCalledWith(15, zip.id, expect.any(AbortSignal))
    expect(screen.getByLabelText('0 tệp đính kèm')).toBeInTheDocument()
  })

  it('does not delete when confirmation is canceled', async () => {
    setup([zip])
    await loaded()
    await userEvent.click(screen.getByRole('button', { name: `Xóa ${zip.fileName}` }))
    await userEvent.click(screen.getByRole('button', { name: 'Hủy', exact: true }))
    expect(attachmentService.remove).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: zip.fileName, exact: true })).toBeInTheDocument()
  })

  it('closes preview when the previewed file is deleted and restores body scrolling', async () => {
    setup([image])
    const dialog = await preview(image)
    await within(dialog).findByRole('img')
    await userEvent.click(within(dialog).getByRole('button', { name: `Xóa ${image.fileName}` }))
    expect(screen.getAllByRole('dialog')).toHaveLength(2)
    await userEvent.click(screen.getByRole('button', { name: 'Xóa dữ liệu' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(URL.revokeObjectURL).toHaveBeenCalled()
    expect(document.body.style.overflow).toBe('')
  })

  it('retains the file and confirmation on deletion failure and sends an error toast', async () => {
    attachmentService.remove.mockRejectedValue(failure(403))
    setup([zip])
    await loaded()
    await userEvent.click(screen.getByRole('button', { name: `Xóa ${zip.fileName}` }))
    await userEvent.click(screen.getByRole('button', { name: 'Xóa dữ liệu' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('không có quyền')
    expect(notify).toHaveBeenCalledWith(expect.stringContaining('không có quyền'), 'error')
    expect(screen.getByLabelText('1 tệp đính kèm')).toBeInTheDocument()
  })

  it('keeps same-name uploads separate by ID', async () => {
    setup([zip, { ...zip, id: 5 }])
    await loaded()
    const buttons = screen.getAllByRole('button', { name: `Xóa ${zip.fileName}` })
    await userEvent.click(buttons[1])
    await userEvent.click(screen.getByRole('button', { name: 'Xóa dữ liệu' }))
    await waitFor(() => expect(screen.getByLabelText('1 tệp đính kèm')).toBeInTheDocument())
    expect(attachmentService.remove).toHaveBeenCalledWith(15, 5, expect.any(AbortSignal))
  })

  it('does not report a preview failure as an upload failure', async () => {
    attachmentService.upload.mockResolvedValue(image)
    attachmentService.download.mockRejectedValue(failure(502))
    setup([])
    await loaded()
    await userEvent.upload(screen.getByLabelText('Chọn tệp đính kèm'), new File(['image'], image.fileName, { type: image.contentType }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể xem trước')
    expect(notify).toHaveBeenCalledWith('Tải tệp đính kèm thành công.')
    expect(notify).not.toHaveBeenCalledWith(expect.any(String), 'error')
    expect(screen.getByLabelText('1 tệp đính kèm')).toBeInTheDocument()
  })

  it('aborts a pending preview on unmount and ignores its late response', async () => {
    let resolve
    attachmentService.download.mockImplementation(() => new Promise((done) => { resolve = done }))
    const view = setup([image])
    await preview(image)
    const signal = attachmentService.download.mock.calls[0][2]
    view.unmount()
    expect(signal.aborted).toBe(true)
    await act(async () => resolve(new Blob(['image'], { type: image.contentType })))
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })

  it('prevents duplicate downloads and aborts pending requests on unmount', async () => {
    attachmentService.download.mockReturnValue(new Promise(() => {}))
    const view = setup([zip])
    await loaded()
    const button = screen.getByRole('button', { name: `Tải xuống ${zip.fileName}` })
    fireEvent.click(button)
    fireEvent.click(button)
    expect(attachmentService.download).toHaveBeenCalledOnce()
    expect(button).toBeDisabled()
    const signal = attachmentService.download.mock.calls[0][2]
    view.unmount()
    expect(signal.aborted).toBe(true)
  })

  it('prevents duplicate uploads and ignores a late upload response after unmount', async () => {
    let resolve
    attachmentService.upload.mockImplementation(() => new Promise((done) => { resolve = done }))
    const view = setup([])
    await loaded()
    const input = screen.getByLabelText('Chọn tệp đính kèm')
    const file = new File(['zip'], zip.fileName)
    fireEvent.change(input, { target: { files: [file] } })
    fireEvent.change(input, { target: { files: [file] } })
    expect(attachmentService.upload).toHaveBeenCalledOnce()
    expect(input).toBeDisabled()
    const signal = attachmentService.upload.mock.calls[0][2]
    view.unmount()
    expect(signal.aborted).toBe(true)
    await act(async () => resolve(zip))
    expect(notify).not.toHaveBeenCalled()
  })

  it('shows an upload error inline and in the existing toast system', async () => {
    attachmentService.upload.mockRejectedValue(failure(413))
    setup([])
    await loaded()
    await userEvent.upload(screen.getByLabelText('Chọn tệp đính kèm'), new File(['zip'], zip.fileName))
    expect(await screen.findByRole('alert')).toHaveTextContent('giới hạn dung lượng')
    expect(notify).toHaveBeenCalledWith(expect.stringContaining('giới hạn dung lượng'), 'error')
    expect(screen.getByLabelText('0 tệp đính kèm')).toBeInTheDocument()
    expect(screen.getByLabelText('Chọn tệp đính kèm')).toBeEnabled()
  })

  it('shows a safe toast on download failure and re-enables retry', async () => {
    attachmentService.download.mockRejectedValue(failure(404))
    setup([zip])
    await loaded()
    const button = screen.getByRole('button', { name: `Tải xuống ${zip.fileName}` })
    await userEvent.click(button)
    await waitFor(() => expect(notify).toHaveBeenCalledWith(expect.stringContaining('không còn tồn tại'), 'error'))
    expect(screen.getByRole('alert')).toHaveTextContent('không còn tồn tại')
    expect(button).toBeEnabled()
    expect(URL.createObjectURL).not.toHaveBeenCalled()
  })

  it.each([400, 401, 403, 404, 413, 502])('shows safe API errors for status %s', async (status) => {
    attachmentService.list.mockRejectedValue(failure(status))
    render(<ToastContext.Provider value={notify}><TaskAttachments taskId={15} /></ToastContext.Provider>)
    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(attachmentError(failure(status)))
    expect(alert).not.toHaveTextContent('private')
  })

  it('keeps TaskForm open through nested preview and confirmation, without submitting Task', async () => {
    vi.spyOn(taskService, 'get').mockResolvedValue({ id: 15, title: 'Task', userId: 4 })
    vi.spyOn(taskService, 'update').mockResolvedValue({})
    vi.spyOn(projectService, 'list').mockResolvedValue([])
    attachmentService.list.mockResolvedValue([image])
    const onClose = vi.fn()
    const errors = vi.spyOn(console, 'error')
    const view = render(<StrictMode><ToastContext.Provider value={notify}><AuthContext.Provider value={{ user: { id: 4 }, isAdmin: false }}><TaskForm taskId={15} onClose={onClose} onSuccess={vi.fn()} /></AuthContext.Provider></ToastContext.Provider></StrictMode>)
    await loaded()
    await userEvent.click(screen.getByRole('button', { name: `Xem ${image.fileName}` }))
    const dialogs = screen.getAllByRole('dialog')
    expect(dialogs).toHaveLength(2)
    expect(new Set(dialogs.map((dialog) => dialog.getAttribute('aria-labelledby'))).size).toBe(2)
    await within(dialogs[1]).findByRole('img')
    await userEvent.click(within(dialogs[1]).getByRole('button', { name: 'Đóng cửa sổ' }))
    expect(document.body.style.overflow).toBe('hidden')
    await userEvent.click(screen.getByRole('button', { name: `Xóa ${image.fileName}` }))
    await userEvent.click(screen.getByRole('button', { name: 'Xóa dữ liệu' }))
    await waitFor(() => expect(screen.getAllByRole('dialog')).toHaveLength(1))
    expect(screen.getByRole('dialog')).toHaveAccessibleName('Chỉnh sửa công việc')
    expect(taskService.update).not.toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
    view.unmount()
    expect(document.body.style.overflow).toBe('')
    expect(errors).not.toHaveBeenCalled()
  })
})
