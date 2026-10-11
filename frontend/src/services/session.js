// Memory-only: never persist JWTs in storage, cookies, URLs, or logs.
let token = null
let expiresAt = 0
let timer
const listeners = new Set()
export function subscribeSession(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
export function clearSession(reason = 'logout') {
  token = null
  expiresAt = 0
  clearTimeout(timer)
  listeners.forEach((listener) => listener(reason))
}
export function setSession(accessToken, expiresIn) {
  if (typeof accessToken !== 'string' || !accessToken || !Number.isFinite(expiresIn) || expiresIn <= 0) {
    throw new Error('Phản hồi đăng nhập không hợp lệ. Vui lòng thử lại.')
  }
  clearTimeout(timer)
  token = accessToken
  expiresAt = Date.now() + expiresIn * 1000
  timer = setTimeout(() => clearSession('expired'), Math.min(expiresIn * 1000, 2_147_483_647))
}
export function getToken() {
  if (token && Date.now() >= expiresAt) clearSession('expired')
  return token
}
