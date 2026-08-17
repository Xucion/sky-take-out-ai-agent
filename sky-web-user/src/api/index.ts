import { request } from './http'

export interface LoginResult { id: number; name: string; phone: string; token: string }
export interface Category { id: number; name: string; type: number; sort: number; status: number }
export interface Flavor { id: number; name: string; value: string }
export interface Product {
  id: number
  name: string
  categoryId: number
  price: number
  image: string
  description?: string
  flavors?: Flavor[]
}
export interface CartItem {
  id: number
  name: string
  dishId?: number
  setmealId?: number
  dishFlavor?: string
  number: number
  amount: number
  image: string
}
export interface Address {
  id?: number
  consignee: string
  phone: string
  sex: string
  provinceCode?: string
  provinceName: string
  cityCode?: string
  cityName: string
  districtCode?: string
  districtName: string
  detail: string
  label?: string
  isDefault: number
}
export interface OrderDetail { id: number; name: string; image: string; number: number; amount: number; dishFlavor?: string }
export interface Order {
  id: number
  number: string
  status: number
  payStatus: number
  amount: number
  orderTime: string
  checkoutTime?: string
  estimatedDeliveryTime?: string
  consignee: string
  phone: string
  address: string
  remark?: string
  cancelReason?: string
  orderDetailList: OrderDetail[]
}
export interface PageData<T> { total: number; records: T[] }

export const login = (data: { phone: string; password: string }) =>
  request<LoginResult>({ url: '/user/user/login', method: 'post', data })
export const register = (data: { name: string; phone: string; password: string }) =>
  request<LoginResult>({ url: '/user/user/register', method: 'post', data })

export const getShopStatus = () => request<number>({ url: '/user/shop/status' })
export const getCategories = () => request<Category[]>({ url: '/user/category/list' })
export const getDishes = (categoryId: number) => request<Product[]>({ url: '/user/dish/list', params: { categoryId } })
export const getSetmeals = (categoryId: number) => request<Product[]>({ url: '/user/setmeal/list', params: { categoryId } })

export const getCart = () => request<CartItem[]>({ url: '/user/shoppingCart/list' })
export const addCart = (data: { dishId?: number; setmealId?: number; dishFlavor?: string }) =>
  request<void>({ url: '/user/shoppingCart/add', method: 'post', data })
export const subCart = (data: { dishId?: number; setmealId?: number; dishFlavor?: string }) =>
  request<void>({ url: '/user/shoppingCart/sub', method: 'post', data })
export const cleanCart = () => request<void>({ url: '/user/shoppingCart/clean', method: 'delete' })

export const getAddresses = () => request<Address[]>({ url: '/user/addressBook/list' })
export const getDefaultAddress = () => request<Address>({ url: '/user/addressBook/default' })
export const getAddress = (id: number) => request<Address>({ url: `/user/addressBook/${id}` })
export const createAddress = (data: Address) => request<void>({ url: '/user/addressBook', method: 'post', data })
export const updateAddress = (data: Address) => request<void>({ url: '/user/addressBook', method: 'put', data })
export const deleteAddress = (id: number) => request<void>({ url: '/user/addressBook', method: 'delete', params: { id } })
export const setDefaultAddress = (id: number) => request<void>({ url: '/user/addressBook/default', method: 'put', data: { id } })

export interface SubmitOrderData {
  addressBookId: number
  payMethod: number
  remark: string
  estimatedDeliveryTime: string
  deliveryStatus: number
  tablewareNumber: number
  tablewareStatus: number
  packAmount: number
  amount: number
}
export interface SubmitOrderResult { id: number; orderNumber: string; orderAmount: number; orderTime: string }
export const submitOrder = (data: SubmitOrderData) => request<SubmitOrderResult>({ url: '/user/order/submit', method: 'post', data })
export const payOrder = (orderNumber: string, payMethod = 2) =>
  request<void>({ url: '/user/order/payment', method: 'put', data: { orderNumber, payMethod } })
export const getOrders = (page = 1, pageSize = 20, status?: number) =>
  request<PageData<Order>>({ url: '/user/order/historyOrders', params: { page, pageSize, status } })
export const getOrder = (id: number) => request<Order>({ url: `/user/order/orderDetail/${id}` })
export const cancelOrder = (id: number) => request<void>({ url: `/user/order/cancel/${id}`, method: 'put' })
export const repeatOrder = (id: number) => request<void>({ url: `/user/order/repetition/${id}`, method: 'post' })
export const remindOrder = (id: number) => request<void>({ url: `/user/order/reminder/${id}` })
