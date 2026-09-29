import { api } from './client'

export interface CartItem {
  id: number
  productId: number
  skuId: number
  productName: string
  optionLabel: string
  imageUrl: string | null
  unitPrice: number
  quantity: number
  stock: number
  available: boolean
  lineAmount: number
}

export interface Cart {
  items: CartItem[]
  totalAmount: number
  itemCount: number
}

/** quiet: 상단바 뱃지처럼 가입 전이어도 가입 화면으로 보내지 않을 때. */
export const getCart = (quiet = false) => api<Cart>('/api-public/v1/cart', { quiet })

export const addCartItem = (skuId: number, quantity: number) =>
  api<CartItem>('/api-public/v1/cart/items', { method: 'POST', body: JSON.stringify({ skuId, quantity }) })

export const changeCartQuantity = (id: number, quantity: number) =>
  api<CartItem>(`/api-public/v1/cart/items/${id}`, { method: 'PATCH', body: JSON.stringify({ quantity }) })

export const removeCartItem = (id: number) => api<void>(`/api-public/v1/cart/items/${id}`, { method: 'DELETE' })

/** 주문할 수 있는 줄: 판매중이고 재고가 수량 이상. */
export const orderable = (item: CartItem) => item.available && item.stock >= item.quantity
