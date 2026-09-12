import axios from 'axios'
import { clearSession } from '../utils/session'

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/',
  timeout: 10000
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('inknexus_token')
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 网关鉴权不通过时返回 401，自动清理登录态并回到登录页
http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      clearSession()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default http
