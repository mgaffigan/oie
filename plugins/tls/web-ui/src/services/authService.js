import api from './api'

function parseLoginXml(xmlString) {
  try {
    const parser = new DOMParser()
    const doc = parser.parseFromString(xmlString, 'application/xml')
    const status = doc.querySelector('status')?.textContent || ''
    const message = doc.querySelector('message')?.textContent || ''
    return { status, message }
  } catch (e) {
    return { status: '', message: 'Failed to parse login response' }
  }
}

export async function loginWithCredentials({ username, password }) {
  // Use application/x-www-form-urlencoded (safelisted for CORS; many Mirth setups expect it)
  const body = new URLSearchParams()
  body.set('username', username)
  body.set('password', password)

  // The endpoint returns XML and sets JSESSIONID cookie; withCredentials ensures the browser stores it
  const response = await api.post('/api/users/_login', body, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'Accept': 'application/xml, text/xml' },
    responseType: 'text',
    transformResponse: [(data) => data],
  })

  const { status, message } = parseLoginXml(response.data || '')
  const success = String(status).toUpperCase() === 'SUCCESS'

  // Useful debug info in dev tools
  // eslint-disable-next-line no-console
  console.debug('[Auth] login response', { status, message, setCookie: response.headers?.['set-cookie'] })

  if (!success) {
    const error = new Error(message || 'Login failed')
    error.code = 'LOGIN_FAILED'
    throw error
  }

  return { success: true }
}


