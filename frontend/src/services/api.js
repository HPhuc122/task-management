import axios from 'axios'
import { clearSession, getToken } from './session'

export const api = axios.create({
  baseURL: (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, ''),
  timeout: 15000,
  headers: { Accept: 'application/json' },
})
api.interceptors.request.use((config) => {
  const token = getToken()
  if (token && !config.publicRequest) config.headers.Authorization = `Bearer ${token}`
  return config
})
api.interceptors.response.use((response) => response, (error) => {
  const sentToken = error.config?.headers?.Authorization
  if (error.response?.status === 401 && sentToken && sentToken === `Bearer ${getToken()}`) clearSession('expired')
  if (error.response?.status === 403 && !error.config?.skipRoleRefresh) window.dispatchEvent(new Event('auth:refresh-role'))
  return Promise.reject(error)
})
export const isCanceled = (error) => axios.isCancel(error)
const messages = {
  400: 'Dữ liệu chưa hợp lệ. Vui lòng kiểm tra lại các trường.',
  401: 'Email hoặc mật khẩu không đúng, hoặc phiên đăng nhập đã hết hạn.',
  403: 'Bạn không có quyền thực hiện thao tác này.',
  404: 'Dữ liệu không còn tồn tại. Vui lòng tải lại danh sách.',
  409: 'Dữ liệu bị trùng hoặc đang được sử dụng. Vui lòng kiểm tra lại.',
  429: 'Có quá nhiều yêu cầu. Vui lòng thử lại sau.',
}
export function errorMessage(error) {
  if (!axios.isAxiosError(error)) return error?.message || 'Có lỗi xảy ra. Vui lòng thử lại.'
  if (!error.response) return error.code === 'ECONNABORTED'
    ? 'Máy chủ phản hồi quá lâu. Vui lòng thử lại.'
    : 'Không kết nối được máy chủ. Kiểm tra backend và kết nối mạng.'
  const status = error.response.status
  if (status === 409 && error.config?.url === '/auth/register') return 'Email này đã được đăng ký.'
  return messages[status] || 'Máy chủ gặp sự cố. Vui lòng thử lại sau.'
}
export function fieldErrors(error) {
  const fields = error.response?.data?.errors
  if (!fields || typeof fields !== 'object') return {}
  return Object.fromEntries(Object.entries(fields).map(([key, value]) => [
    key.split('.').pop(), validationMessage(value),
  ]))
}

function validationMessage(value) {
  if (typeof value !== 'string') return 'Giá trị chưa hợp lệ.'
  if (/must not be blank|must not be empty/i.test(value)) return 'Không được để trống.'
  if (/must not be null/i.test(value)) return 'Vui lòng nhập hoặc chọn giá trị.'
  if (/well-formed email/i.test(value)) return 'Địa chỉ email chưa hợp lệ.'
  if (/must be greater than 0|must be a positive/i.test(value)) return 'Giá trị phải là số lớn hơn 0.'
  const size = value.match(/size must be between (\d+) and (\d+)/i)
  if (size) return `Độ dài phải từ ${size[1]} đến ${size[2]} ký tự.`
  return value
}
