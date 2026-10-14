// Same-tab persistence only. Never store passwords or put JWTs in URLs/logs.
export const SESSION_STORAGE_KEY = 'taskflow.auth.session'
let token = null
let expiresAt = 0
let timer
const listeners = new Set()

function removeStoredSession() {
  try { sessionStorage.removeItem(SESSION_STORAGE_KEY) } catch { /* Storage may be disabled. */ }
}

function scheduleExpiry() {
  clearTimeout(timer)
  timer = setTimeout(() => {
    if (Date.now() >= expiresAt) clearSession('expired')
    else scheduleExpiry()
  }, Math.min(expiresAt - Date.now(), 2_147_483_647))
}

export function restoreSession() {
  if (getToken()) return token
  try {
    const stored = JSON.parse(sessionStorage.getItem(SESSION_STORAGE_KEY))
    if (typeof stored?.accessToken !== 'string' || !stored.accessToken.trim()
      || !Number.isFinite(stored.expiresAt) || stored.expiresAt <= Date.now()) {
      removeStoredSession()
      return null
    }
    token = stored.accessToken
    expiresAt = stored.expiresAt
    scheduleExpiry()
    return token
  } catch {
    removeStoredSession()
    return null
  }
}
export function subscribeSession(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}
export function clearSession(reason = 'logout') {
  token = null
  expiresAt = 0
  clearTimeout(timer)
  removeStoredSession()
  listeners.forEach((listener) => listener(reason))
}
export function setSession(accessToken, expiresIn) {
  const expiration = Date.now() + expiresIn * 1000
  if (typeof accessToken !== 'string' || !accessToken.trim() || !Number.isFinite(expiresIn) || expiresIn <= 0 || !Number.isFinite(expiration)) {
    throw new Error('Phản hồi đăng nhập không hợp lệ. Vui lòng thử lại.')
  }
  token = accessToken
  expiresAt = expiration
  try { sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify({ accessToken, expiresAt })) }
  catch { removeStoredSession() /* Retain the in-memory session if storage is unavailable. */ }
  scheduleExpiry()
}
export function getToken() {
  if (token && Date.now() >= expiresAt) clearSession('expired')
  return token
}
