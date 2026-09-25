import type { Category } from '../api/catalog'

/** 색이 없는 카테고리의 타일 바탕. */
export const NEUTRAL_TILE = '#F3F4F6'

/** 이모지(없으면 이름 첫 글자)를 파스텔 바탕 둥근 네모에 얹은 카테고리 아이콘. 크기는 CSS 클래스(sm/md/lg)로. */
export default function CategoryTile({ category, size = 'md' }: { category: Pick<Category, 'name' | 'icon' | 'color'>; size?: 'xs' | 'sm' | 'md' | 'lg' }) {
  const icon = category.icon?.trim()
  return (
    <span className={`cat-tile ${size} ${icon ? '' : 'letter'}`.trim()} style={{ background: category.color || NEUTRAL_TILE }} aria-hidden="true">
      {icon || Array.from(category.name.trim())[0] || '?'}
    </span>
  )
}
