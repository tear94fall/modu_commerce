import { api } from './client'
import { CountHint, insertMissing, RecentMap } from './recent'

export interface Category {
  id: number
  name: string
  children: Category[]
  sortOrder?: number
  productCount?: number | null
  /** 이모지 하나("🍳"). 없으면 이름 첫 글자를 쓴다. */
  icon?: string | null
  /** 타일 바탕 파스텔 색("#FFEDD5"). 없으면 중립 회색. */
  color?: string | null
}

export interface ProductSummary {
  id: number
  name: string
  imageUrl: string | null
  price: number
  listPrice: number | null
  discountRate: number
  soldOut: boolean
  wished: boolean
  reviewCount: number
  /** 소수 첫째 자리(4.5). 리뷰가 없으면 0. */
  ratingAverage: number
}

export interface OptionValue {
  id: number
  name: string
}

export interface OptionGroup {
  id: number
  name: string
  values: OptionValue[]
}

export interface Sku {
  id: number
  optionValueIds: number[]
  optionLabel: string
  extraPrice: number
  stock: number
}

export interface ProductDetail {
  id: number
  name: string
  description: string
  detail: string | null
  images: string[]
  price: number
  listPrice: number | null
  discountRate: number
  soldOut: boolean
  wished: boolean
  wishCount: number
  reviewCount: number
  ratingAverage: number
  categoryPath: string[]
  optionGroups: OptionGroup[]
  skus: Sku[]
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
}

/** 서버 `ProductSort.param` 과 같은 값. */
export type ProductSort = 'latest' | 'popular' | 'priceAsc' | 'priceDesc'

export const SORT_LABELS: Record<ProductSort, string> = { latest: '최신순', popular: '인기순', priceAsc: '낮은 가격순', priceDesc: '높은 가격순' }

export const PAGE_SIZE = 20

export interface ProductQuery {
  categoryId?: number
  q?: string
  sort?: ProductSort
  page?: number
  size?: number
}

/** 카테고리 상품 목록 주소. title 은 카테고리를 못 불러왔을 때의 제목. sort 를 주면 정렬도 잇는다. */
export function categoryLink(category: Pick<Category, 'id' | 'name'>, sort?: ProductSort | null) {
  const params = new URLSearchParams({ categoryId: String(category.id), title: category.name })
  if (sort) params.set('sort', sort)
  return `/products?${params}`
}

/** 트리에서 id 까지의 경로(대분류부터). 없으면 빈 배열. */
export function categoryPath(roots: Category[], id: number): Category[] {
  for (const c of roots) {
    if (c.id === id) return [c]
    const sub = categoryPath(c.children ?? [], id)
    if (sub.length > 0) return [c, ...sub]
  }
  return []
}

export const getCategories = () => api<Category[]>('/api-public/v1/categories')

// ---- 방금 누른 찜(복제 지연 덮개, recent.ts) ----

/** 상품 id → 찜 여부와(찜했으면) 그때 본 카드. 요청을 보낼 때 기억하고, 실패하면 지운다. */
const wishes = new RecentMap<number, { wished: boolean; product: ProductSummary | null }>()
/** 마이 탭 '찜 N'. */
const wishTotal = new CountHint()

/** 목록·상세의 찜 표시를 방금 누른 값으로. */
export function withRecentWish<T extends { id: number; wished: boolean }>(p: T): T {
  const w = wishes.get(p.id)
  return w === undefined || w.wished === p.wished ? p : { ...p, wished: w.wished }
}

const withRecentWishes = <T extends { id: number; wished: boolean }>(page: Page<T>): Page<T> => ({ ...page, content: page.content.map(withRecentWish) })

export function getProducts({ categoryId, q, sort, page = 0, size = PAGE_SIZE }: ProductQuery) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (categoryId !== undefined) params.set('categoryId', String(categoryId))
  if (q) params.set('q', q)
  if (sort) params.set('sort', sort)
  return api<Page<ProductSummary>>(`/api-public/v1/products?${params}`).then(withRecentWishes)
}

export const getProduct = (id: number) =>
  api<ProductDetail>(`/api-public/v1/products/${id}`).then((d) => {
    const next = withRecentWish(d)
    return next === d ? d : { ...next, wishCount: Math.max(0, d.wishCount + (next.wished ? 1 : -1)) }
  })

/** 방금 뺀 찜은 빼고, 방금 한 찜이 첫 장에 없으면 맨 앞에 끼운다(찜한 순서 최신 순). */
export const getWishlist = (page = 0, size = PAGE_SIZE) =>
  api<Page<ProductSummary>>(`/api-public/v1/wishlist?page=${page}&size=${size}`).then((res) => {
    const kept = res.content.filter((p) => wishes.get(p.id)?.wished !== false)
    const added = wishes
      .entries()
      .reverse()
      .flatMap(([, w]) => (w.wished && w.product ? [{ ...w.product, wished: true }] : []))
    const merged = insertMissing({ ...res, content: kept }, added, () => true, (p) => p.id)
    const total = res.totalElements - (res.content.length - kept.length) + merged.inserted
    return { ...res, content: merged.content.map((p) => ({ ...p, wished: true })), totalElements: wishTotal.observe(Math.max(0, total)) }
  })

/** [product] 는 찜 목록에 바로 보일 카드(레플리카가 늦어도 찜 목록 첫 장에 끼운다). */
export function setWish(productId: number, wished: boolean, product: ProductSummary | null = null) {
  const before = wishes.get(productId)
  wishes.set(productId, { wished, product })
  wishTotal.write(wished ? 1 : -1)
  return api<void>(`/api-public/v1/wishlist/${productId}`, { method: wished ? 'POST' : 'DELETE' }).catch((e: unknown) => {
    if (before) wishes.set(productId, before)
    else wishes.delete(productId)
    wishTotal.write(wished ? -1 : 1)
    throw e
  })
}
