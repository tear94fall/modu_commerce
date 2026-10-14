import type { Page } from './catalog'
import { api } from './client'
import { forgetCouponsOfOrder, rememberCouponUsed } from './coupons'
import { withRecentReviewState } from './reviews'

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

/** 구매 적립 처리 상태. NONE = 적립 없음(비고객 · 0P), PENDING = 포인트 서비스 재시도 대기. */
export type EarnStatus = 'NONE' | 'PENDING' | 'DONE' | 'FAILED'

/** 배송 완료 때 정해진 구매 적립. */
export interface OrderEarn {
  status: EarnStatus
  points: number | null
  rate: number | null
}

/** 결제완료·배송중 주문의 예상 적립(지금 등급 기준). */
export interface ExpectedEarn {
  points: number
  rate: number
}

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
  /** 구매 적립. 아직 정해지지 않았으면 null. */
  earn?: OrderEarn | null
  /** 결제완료·배송중일 때 예상 적립. 그 밖에는 null. */
  expectedEarn?: ExpectedEarn | null
}

/** 취소 주문의 포인트 환불 상태. PENDING = 포인트 서비스로 보내는 중(재시도 포함), FAILED = 운영 확인 중. */
export type PointRefundStatus = 'NONE' | 'PENDING' | 'DONE' | 'FAILED'

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
  /** 구매 적립. 아직 정해지지 않았으면 null. */
  earn?: OrderEarn | null
  /** 결제완료·배송중일 때 예상 적립. 그 밖에는 null. */
  expectedEarn?: ExpectedEarn | null
  /** 취소 주문의 포인트 환불 상태. 포인트를 안 썼거나 취소가 아니면 NONE(옛 서버는 없음). */
  pointRefundStatus?: PointRefundStatus
}

export interface OrderLine {
  skuId: number
  quantity: number
}

export const fullAddress = (a: { address1: string; address2: string | null }) => [a.address1, a.address2?.trim()].filter(Boolean).join(' ')

export const getAddresses = () => api<Address[]>('/api-public/v1/addresses')
export const createAddress = (input: AddressInput) => api<Address>('/api-public/v1/addresses', { method: 'POST', body: JSON.stringify(input) })
export const updateAddress = (id: number, input: AddressInput) => api<Address>(`/api-public/v1/addresses/${id}`, { method: 'PUT', body: JSON.stringify(input) })
export const setDefaultAddress = (id: number) => api<Address>(`/api-public/v1/addresses/${id}/default`, { method: 'PUT' })
export const deleteAddress = (id: number) => api<void>(`/api-public/v1/addresses/${id}`, { method: 'DELETE' })

/**
 * 주문 요청의 덧붙임. idempotencyKey 는 주문서 한 번(같은 입력)마다 하나 — 네트워크 오류로 다시 보낼 때 같은 키를 쓰면
 * 서버는 주문을 새로 만들지 않고 처음 주문을 돌려준다. expectedPaymentAmount 는 화면에 보인 결제 금액(다르면 409 PRICE_CHANGED).
 */
export interface CreateOrderOptions {
  idempotencyKey?: string
  expectedPaymentAmount?: number
}

/** userCouponId 는 내 사용 가능 쿠폰(없으면 null). 쿠폰이 안 맞으면 400 과 까닭. */
export const createOrder = (addressId: number, items: OrderLine[], cartItemIds: number[], usePoints = 0, userCouponId: number | null = null, options: CreateOrderOptions = {}) =>
  api<OrderDetail>('/api-public/v1/orders', {
    method: 'POST',
    headers: options.idempotencyKey ? { 'Idempotency-Key': options.idempotencyKey } : undefined,
    body: JSON.stringify({
      addressId,
      items,
      cartItemIds,
      usePoints,
      ...(userCouponId !== null ? { userCouponId } : {}),
      ...(options.expectedPaymentAmount !== undefined ? { expectedPaymentAmount: options.expectedPaymentAmount } : {}),
    }),
  }).then((o) => {
    if (userCouponId !== null) rememberCouponUsed(userCouponId, o.id)
    return o
  })
export const getOrders = (page = 0, size = 20) => api<Page<OrderSummary>>(`/api-public/v1/orders?page=${page}&size=${size}`)
/** 줄마다 방금 쓴·지운 리뷰를 반영한다(레플리카가 늦어도 '리뷰 쓰기'가 다시 뜨지 않는다). */
export const getOrder = (id: number) => api<OrderDetail>(`/api-public/v1/orders/${id}`).then(withRecentReviews)
export const cancelOrder = (id: number) =>
  api<OrderDetail>(`/api-public/v1/orders/${id}/cancel`, { method: 'POST' }).then((o) => {
    forgetCouponsOfOrder(id)
    return withRecentReviews(o)
  })

const withRecentReviews = (o: OrderDetail): OrderDetail => ({ ...o, items: o.items.map((i) => withRecentReviewState(i, o.status === 'CANCELLED')) })
