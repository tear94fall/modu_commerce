import { useLocation, useNavigate } from 'react-router-dom'
import type { Category, Page, ProductSummary } from '../api/catalog'

/** 테스트용 카테고리 트리: 생활(주방·욕실) + 아이콘·색이 없는 문구(소분류 없음). */
export const CATEGORY_TREE: Category[] = [
  {
    id: 1,
    name: '생활',
    icon: '🏠',
    color: '#DCFCE7',
    children: [
      { id: 11, name: '주방', icon: '🍳', color: '#FFEDD5', children: [] },
      { id: 12, name: '욕실', icon: '🛁', color: '#CFFAFE', children: [] },
    ],
  },
  { id: 2, name: '문구', icon: null, color: null, children: [] },
]

export const product = (over: Partial<ProductSummary> = {}): ProductSummary => ({
  id: 1,
  name: '모두 베이직 티셔츠',
  imageUrl: null,
  price: 12000,
  listPrice: null,
  discountRate: 0,
  soldOut: false,
  wished: false,
  reviewCount: 0,
  ratingAverage: 0,
  ...over,
})

export const page = <T,>(content: T[], totalElements = content.length): Page<T> => ({ content, totalElements, totalPages: 1, number: 0 })

/** 지금 주소를 글자로 보여 주고, "이전" 으로 한 칸 뒤로 간다(replace 확인용). */
export function LocationProbe() {
  const location = useLocation()
  const navigate = useNavigate()
  return (
    <>
      <output data-testid="location">{location.pathname + location.search}</output>
      <button type="button" onClick={() => navigate(-1)}>
        이전
      </button>
    </>
  )
}
