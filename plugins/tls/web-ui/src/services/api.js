import axios from 'axios'

export const api = axios.create({
  // Use same-origin '/api' in dev with Vite proxy; fallback to absolute BASE_URL when building
  baseURL: '/',
  withCredentials: true,
  headers: {
    'X-Requested-With': 'XMLHttpRequest',
  },
  timeout: 15000,
})

// Dev-friendly request logging (masks password)
api.interceptors.request.use((config) => {
  try {
    let dataPreview = null
    const isFormData = typeof FormData !== 'undefined' && config.data instanceof FormData
    const isUrlParams = typeof URLSearchParams !== 'undefined' && config.data instanceof URLSearchParams
    if (isUrlParams) {
      const safe = {}
      for (const [k, v] of config.data.entries()) {
        safe[k] = String(k).toLowerCase() === 'password' ? '***' : v
      }
      dataPreview = safe
    } else if (isFormData) {
      const safe = {}
      for (const [k, v] of config.data.entries()) {
        safe[k] = String(k).toLowerCase() === 'password' ? '***' : (typeof v === 'string' ? v : '[file]')
      }
      dataPreview = safe
    } else if (config.data && typeof config.data === 'object') {
      const safe = { ...config.data }
      if ('password' in safe) safe.password = '***'
      dataPreview = safe
    }
    // eslint-disable-next-line no-console
    console.debug('[API] request', { method: config.method, url: config.url, data: dataPreview })
  } catch (_) {}
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    // eslint-disable-next-line no-console
    console.debug('[API] error', {
      url: error?.config?.url,
      status: error?.response?.status,
      data: error?.response?.data,
    })
    
    // Handle 401 Unauthorized errors - clear auth and redirect to login
    if (error?.response?.status === 401) {
      const STORAGE_KEY = 'auth:isAuthenticated'
      
      // Clear authentication state from localStorage
      try {
        localStorage.removeItem(STORAGE_KEY)
      } catch (_) {
        // Ignore localStorage errors
      }
      
      // Redirect to login page if not already there
      const currentPath = window.location.pathname
      if (!currentPath.includes('/login')) {
        window.location.href = '/tls-manager/login'
      }
    }
    
    return Promise.reject(error)
  }
)

export default api


