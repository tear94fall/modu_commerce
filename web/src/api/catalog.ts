import { api } from './client'

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

export const getCategories = () => api<Category[]>('/api/v1/categories')

export function getProducts({ categoryId, q, sort, page = 0, size = PAGE_SIZE }: ProductQuery) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (categoryId !== undefined) params.set('categoryId', String(categoryId))
  if (q) params.set('q', q)
  if (sort) params.set('sort', sort)
  return api<Page<ProductSummary>>(`/api/v1/products?${params}`)
}

export const getProduct = (id: number) => api<ProductDetail>(`/api/v1/products/${id}`)

export const getWishlist = (page = 0, size = PAGE_SIZE) => api<Page<ProductSummary>>(`/api/v1/wishlist?page=${page}&size=${size}`)

export const setWish = (productId: number, wished: boolean) =>
  api<void>(`/api/v1/wishlist/${productId}`, { method: wished ? 'POST' : 'DELETE' })
