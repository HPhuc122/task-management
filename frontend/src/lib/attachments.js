export const MAX_ATTACHMENT_SIZE = 10 * 1024 * 1024
export const ATTACHMENT_ACCEPT = '.jpg,.jpeg,.png,.webp,.gif,.pdf,.docx,.zip'

const formats = {
  jpg: ['image', 'image/jpeg'], jpeg: ['image', 'image/jpeg'],
  png: ['image', 'image/png'], webp: ['image', 'image/webp'], gif: ['image', 'image/gif'],
  pdf: ['pdf', 'application/pdf'],
  docx: ['docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'],
  zip: ['zip', 'application/zip'],
}

export function attachmentFormat(file) {
  const extension = file.fileName?.lastIndexOf('.') > 0 ? file.fileName.split('.').pop().toLowerCase() : ''
  const format = formats[extension]
  return { kind: format?.[0] || 'other', mime: format?.[1], label: extension?.toUpperCase() || 'Tệp' }
}

export function validateAttachment(file) {
  if (!file.size) return 'Không thể tải lên tệp rỗng.'
  if (file.size > MAX_ATTACHMENT_SIZE) return 'Tệp vượt quá giới hạn 10 MB.'
  if (attachmentFormat({ fileName: file.name }).kind === 'other') return 'Định dạng không được hỗ trợ. Chọn ảnh, PDF, DOCX hoặc ZIP.'
  return ''
}

export function formatAttachmentSize(bytes = 0) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function attachmentError(error) {
  const messages = {
    400: 'Tệp không hợp lệ. Vui lòng kiểm tra định dạng và thử lại.',
    401: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
    403: 'Bạn không có quyền truy cập tệp của công việc này.',
    404: 'Tệp hoặc công việc không còn tồn tại. Vui lòng tải lại danh sách.',
    413: 'Tệp vượt quá giới hạn dung lượng của máy chủ (mặc định 10 MB).',
    502: 'Dịch vụ lưu trữ tạm thời không khả dụng. Vui lòng thử lại sau.',
  }
  if (error?.response) return messages[error.response.status] || 'Không thể xử lý tệp. Vui lòng thử lại sau.'
  if (['ECONNABORTED', 'ETIMEDOUT'].includes(error?.code)) return 'Máy chủ phản hồi quá lâu. Vui lòng thử lại.'
  return 'Không thể tải hoặc xử lý tệp. Kiểm tra kết nối và thử lại.'
}

// Delay revocation so the browser can start consuming the download URL.
export function saveAttachment(blob, fileName) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.append(link)
  try { link.click() }
  finally { link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000) }
}
