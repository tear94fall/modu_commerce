import { useCallback, useEffect, useRef, useState } from 'react'
import { setWish, type Page, type ProductSummary } from '../api/catalog'

export interface PagerState {
  items: ProductSummary[]
  loading: boolean
  error: boolean
  hasNext: boolean
  message: string | null
}

/**
 * 첫 페이지 → 더 보기 페이징과 찜 토글(낙관적 반영, 실패하면 되돌림). 앱의 ProductPager 와 같은 규칙.
 * `load` 가 바뀌면(정렬·검색어) 첫 페이지부터 다시 받는다.
 */
export function useProductPager(load: (page: number) => Promise<Page<ProductSummary>>) {
  const [state, setState] = useState<PagerState>({ items: [], loading: true, error: false, hasNext: false, message: null })
  const pageRef = useRef(0)
  const requestRef = useRef(0)

  const fetchPage = useCallback(
    async (page: number) => {
      const requestId = ++requestRef.current
      setState((s) => ({ ...s, loading: true, error: false }))
      try {
        const result = await load(page)
        if (requestId !== requestRef.current) return
        pageRef.current = page
        setState((s) => ({
          items: page === 0 ? result.content : [...s.items, ...result.content],
          loading: false,
          error: false,
          hasNext: result.number + 1 < result.totalPages,
          message: s.message,
        }))
      } catch {
        if (requestId !== requestRef.current) return
        setState((s) => ({ ...s, loading: false, error: page === 0 }))
      }
    },
    [load],
  )

  useEffect(() => {
    fetchPage(0)
  }, [fetchPage])

  const loadMore = useCallback(() => {
    if (!state.loading && state.hasNext) fetchPage(pageRef.current + 1)
  }, [state.loading, state.hasNext, fetchPage])

  const toggleWish = useCallback(
    async (productId: number) => {
      const current = state.items.find((p) => p.id === productId)
      if (!current) return
      const next = !current.wished
      const patch = (wished: boolean) => setState((s) => ({ ...s, items: s.items.map((p) => (p.id === productId ? { ...p, wished } : p)) }))
      patch(next)
      try {
        await setWish(productId, next)
      } catch {
        patch(!next)
        setState((s) => ({ ...s, message: '찜을 바꾸지 못했습니다.' }))
      }
    },
    [state.items],
  )

  const clearMessage = useCallback(() => setState((s) => ({ ...s, message: null })), [])

  return { ...state, retry: () => fetchPage(0), loadMore, toggleWish, clearMessage }
}
