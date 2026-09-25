import { getWishlist } from '../api/catalog'
import { EmptyBox, ErrorBox, Loading } from '../components/Boxes'
import { Screen, TopBar } from '../components/Layout'
import { ProductGrid } from '../components/ProductCard'
import Toast from '../components/Toast'
import { useProductPager } from '../hooks/useProductPager'

const load = (page: number) => getWishlist(page)

export default function WishlistPage() {
  const pager = useProductPager(load)
  return (
    <Screen tabs>
      <TopBar title="찜" />
      {pager.error ? (
        <ErrorBox message="찜 목록을 불러오지 못했습니다." onRetry={pager.retry} />
      ) : pager.loading && pager.items.length === 0 ? (
        <Loading />
      ) : pager.items.length === 0 ? (
        <EmptyBox message="찜한 상품이 없습니다." />
      ) : (
        <>
          <ProductGrid products={pager.items} onToggleWish={pager.toggleWish} />
          {!pager.loading && pager.hasNext && (
            <button type="button" className="btn outline small load-more" onClick={pager.loadMore}>
              더 보기
            </button>
          )}
        </>
      )}
      <Toast message={pager.message} onDone={pager.clearMessage} />
    </Screen>
  )
}
