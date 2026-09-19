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

export const getCart = () => api<Cart>('/api/v1/cart')

export const addCartItem = (skuId: number, quantity: number) =>
  api<CartItem>('/api/v1/cart/items', { method: 'POST', body: JSON.stringify({ skuId, quantity }) })
