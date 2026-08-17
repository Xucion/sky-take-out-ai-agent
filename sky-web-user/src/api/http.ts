import axios from 'axios'

export interface ApiResult<T> {
  code: number
  msg?: string
  data: T
}

const http = axios.create({ baseURL: '/api', timeout: 12000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('sky_user_token')
  if (token) config.headers.authentication = token
  return config
})

http.interceptors.response.use(
  (response) => {
    const result = response.data as ApiResult<unknown>
    if (result?.code !== 1) {
      if (result?.msg?.includes('未登录')) {
        localStorage.removeItem('sky_user_token')
        localStorage.removeItem('sky_user_profile')
        window.location.assign('/login')
      }
      return Promise.reject(new Error(result?.msg || '请求失败'))
    }
    return response
  },
  (error) => Promise.reject(new Error(error.response?.data?.msg || error.message || '网络连接失败')),
)

export async function request<T>(config: Parameters<typeof http.request>[0]): Promise<T> {
  const response = await http.request<ApiResult<T>>(config)
  return response.data.data
}
