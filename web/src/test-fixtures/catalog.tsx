import { useLocation, useNavigate } from 'react-router-dom'
import type { Category, Page, ProductSummary } from '../api/catalog'

/** 테스트용 카테고리 트리: 생활(주방·욕실) + 아이콘·색이 없는 문구(소분류 없음) + 3단계 패션(의류 › 하의·상의, 신발). 앞 둘은 옛 응답처럼 depth 가 없다. */
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
  {
    // 3단계 + 서버 순서(sortOrder)가 id·가나다 순과 다르다: 의류(32) → 신발(31), 하의(322) → 상의(321).
    id: 3,
    name: '패션',
    icon: '👗',
    color: '#FCE7F3',
    sortOrder: 3,
    depth: 1,
    children: [
      {
        id: 32,
        name: '의류',
        icon: '👕',
        color: '#E0E7FF',
        sortOrder: 1,
        depth: 2,
        children: [
          { id: 322, name: '하의', icon: '👖', color: null, sortOrder: 1, depth: 3, children: [] },
          { id: 321, name: '상의', icon: null, color: null, sortOrder: 2, depth: 3, children: [] },
        ],
      },
      { id: 31, name: '신발', icon: '👟', color: null, sortOrder: 2, depth: 2, children: [] },
    ],
  },
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
