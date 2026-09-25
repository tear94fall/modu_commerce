import type { Page } from './catalog'
import { api } from './client'

export interface Address {
  id: number
  recipient: string
  phone: string
  zipCode: string
  address1: string
  address2: string | null
  isDefault: boolean
}

export interface AddressInput {
  recipient: string
  phone: string
  zipCode: string
  address1: string
  address2: string | null
  isDefault: boolean
}

export type OrderStatus = 'PAID' | 'SHIPPING' | 'DELIVERED' | 'CANCELLED'

export const ORDER_STATUS_LABELS: Record<string, string> = { PAID: '결제완료', SHIPPING: '배송중', DELIVERED: '배송완료', CANCELLED: '취소' }

export const statusLabel = (status: string) => ORDER_STATUS_LABELS[status] ?? '알 수 없음'

/** 서버 결제 수단 코드. 지금은 모의 결제뿐이다. */
export const paymentLabel = (method: string) => (method === 'MOCK' || !method ? '모의 결제 (실제 결제 없음)' : method)

export interface OrderItem {
  id: number
  productId: number
  productName: string
  optionLabel: string
  imageUrl: string | null
  unitPrice: number
  quantity: number
  lineAmount: number
  /** 이 줄에 쓴 리뷰 id. 없으면 null. */
  reviewId: number | null
  /** 지금 리뷰를 쓸 수 있는가(취소 주문이 아니고 아직 안 썼음). */
  reviewable: boolean
}

export interface OrderSummary {
  id: number
  orderNo: string
  status: OrderStatus
  /** 상품 금액 합. */
  totalAmount: number
  /** 쿠폰 할인 금액. 쿠폰을 안 썼으면 0. */
  couponDiscount: number
  couponName: string | null
  /** 결제에 쓴 포인트(1P = 1원). */
  pointAmount: number
  /** 실제 결제 금액 = 상품 금액 − 쿠폰 할인 − 포인트. */
  paymentAmount: number
  itemCount: number
  firstItemName: string
  firstImageUrl: string | null
  createdAt: string | null
}

export interface OrderDetail {
  id: number
  orderNo: string
  status: OrderStatus
  totalAmount: number
  couponDiscount: number
  couponName: string | null
  pointAmount: number
  paymentAmount: number
  paymentMethod: string
  recipient: string
  phone: string
  zipCode: string
  address1: string
  address2: string | null
  paidAt: string
  cancelledAt: string | null
  items: OrderItem[]
}

export interface OrderLine {
  skuId: number
  quantity: number
}

export const fullAddress = (a: { address1: string; address2: string | null }) => [a.address1, a.address2?.trim()].filter(Boolean).join(' ')

export const getAddresses = () => api<Address[]>('/api/v1/addresses')
export const createAddress = (input: AddressInput) => api<Address>('/api/v1/addresses', { method: 'POST', body: JSON.stringify(input) })
export const updateAddress = (id: number, input: AddressInput) => api<Address>(`/api/v1/addresses/${id}`, { method: 'PUT', body: JSON.stringify(input) })
export const setDefaultAddress = (id: number) => api<Address>(`/api/v1/addresses/${id}/default`, { method: 'PUT' })
export const deleteAddress = (id: number) => api<void>(`/api/v1/addresses/${id}`, { method: 'DELETE' })

/** userCouponId 는 내 사용 가능 쿠폰(없으면 null). 쿠폰이 안 맞으면 400 과 까닭. */
export const createOrder = (addressId: number, items: OrderLine[], cartItemIds: number[], usePoints = 0, userCouponId: number | null = null) =>
  api<OrderDetail>('/api/v1/orders', {
    method: 'POST',
    body: JSON.stringify({ addressId, items, cartItemIds, usePoints, ...(userCouponId !== null ? { userCouponId } : {}) }),
  })
export const getOrders = (page = 0, size = 20) => api<Page<OrderSummary>>(`/api/v1/orders?page=${page}&size=${size}`)
export const getOrder = (id: number) => api<OrderDetail>(`/api/v1/orders/${id}`)
export const cancelOrder = (id: number) => api<OrderDetail>(`/api/v1/orders/${id}/cancel`, { method: 'POST' })
