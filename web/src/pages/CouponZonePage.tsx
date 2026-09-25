import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getDownloadableCoupons, type CouponOffer } from '../api/coupons'
import { EmptyBox, ErrorBox, Loading } from '../components/Boxes'
import CouponOfferList from '../components/CouponOfferList'
import { Screen, TopBar } from '../components/Layout'
import Toast from '../components/Toast'

/** 쿠폰존. 지금 받을 수 있는 쿠폰을 한 장씩 받는다. */
export default function CouponZonePage() {
  const [offers, setOffers] = useState<CouponOffer[] | null>(null)
  const [error, setError] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const load = useCallback(() => {
    setError(false)
    getDownloadableCoupons()
      .then(setOffers)
      .catch(() => setError(true))
  }, [])
  useEffect(load, [load])
  const clearMessage = useCallback(() => setMessage(null), [])

  return (
    <Screen>
      <TopBar
        title="쿠폰존"
        back
        actions={
          <Link to="/my/coupons" className="top-link">
            쿠폰함
          </Link>
        }
      />
      {error ? (
        <ErrorBox message="쿠폰을 불러오지 못했습니다." onRetry={load} />
      ) : offers === null ? (
        <Loading />
      ) : offers.length === 0 ? (
        <EmptyBox message="지금 받을 수 있는 쿠폰이 없습니다." />
      ) : (
        <CouponOfferList offers={offers} onChange={setOffers} onMessage={setMessage} />
      )}
      <Toast message={message} onDone={clearMessage} />
    </Screen>
  )
}
