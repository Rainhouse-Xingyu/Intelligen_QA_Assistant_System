const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

export function getToken() {
  return localStorage.getItem('token') || sessionStorage.getItem('token') || localStorage.getItem('jwt') || ''
}

export function authHeaders(extra = {}) {
  const token = getToken()
  return {
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...extra
  }
}

export async function parseResponse(response) {
  const contentType = response.headers.get('content-type') || ''
  const text = await response.text()
  let payload

  if (contentType.includes('application/json') && text) {
    payload = JSON.parse(text)
  } else {
    payload = {
      code: response.ok ? 200 : response.status,
      message: text || response.statusText || `请求失败: ${response.status}`,
      data: null
    }
  }

  if (!response.ok || payload.code !== 200) {
    throw new Error(payload.message || `请求失败: ${response.status}`)
  }
  return payload.data
}

export async function apiGet(path, params = {}) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') query.append(key, value)
  })
  const url = `${API_BASE}${path}${query.toString() ? `?${query}` : ''}`
  const response = await fetch(url, { headers: authHeaders() })
  return parseResponse(response)
}

export async function apiJson(path, body = {}, method = 'POST') {
  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body)
  })
  return parseResponse(response)
}

export async function apiForm(path, params = {}, method = 'POST') {
  const body = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') body.append(key, value)
  })
  const response = await fetch(`${API_BASE}${path}`, {
    method,
    headers: authHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }),
    body
  })
  return parseResponse(response)
}

export async function apiUpload(path, formData) {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: authHeaders(),
    body: formData
  })
  return parseResponse(response)
}

export async function apiDelete(path) {
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'DELETE',
    headers: authHeaders()
  })
  return parseResponse(response)
}

export function wsUrl(path) {
  const base = API_BASE || window.location.origin
  const url = new URL(path, base)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  return url.toString()
}
