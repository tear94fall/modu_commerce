import { useCallback, useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { addCartItem } from '../api/cart'
import { getProduct, setWish, type ProductDetail } from '../api/catalog'
import { ApiError } from '../api/client'
import BottomPanel from '../components/BottomPanel'
import { ErrorBox, Loading } from '../components/Boxes'
import { HeartIcon, HeartOutlineIcon } from '../components/Icons'
import { Screen, TopBar } from '../components/Layout'
import Price from '../components/Price'
import Toast from '../components/Toast'
import { formatPrice } from '../util/format'
import { clampQuantity, isValueAvailable, selectSku, toggleValue, totalPrice, type Selection } from '../util/sku'

export default function ProductDetailPage() {
  const { id } = useParams()
  const productId = Number(id)
  const [detail, setDetail] = useState<ProductDetail | null>(null)
  const [status, setStatus] = useState<'loading' | 'ok' | 'notFound' | 'error'>('loading')
  const [sheetOpen, setSheetOpen] = useState(false)
  const [selected, setSelected] = useState<Selection>({})
  const [quantity, setQuantity] = useState(1)
  const [working, setWorking] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [slide, setSlide] = useState(0)

  const load = useCallback(() => {
    setStatus('loading')
    getProduct(productId)
      .then((d) => {
        setDetail(d)
        setSelected({})
        setQuantity(1)
        setStatus('ok')
      })
      .catch((e: unknown) => setStatus(e instanceof ApiError && e.status === 404 ? 'notFound' : 'error'))
  }, [productId])
  useEffect(load, [load])

  const closeSheet = useCallback(() => setSheetOpen(false), [])
  const clearMessage = useCallback(() => setMessage(null), [])

  if (status === 'loading' || !detail) {
    return (
      <Screen>
        <TopBar title="" back />
        {status === 'notFound' ? <ErrorBox message="상품이 없거나 판매가 끝났습니다." /> : status === 'error' ? <ErrorBox message="상품을 불러오지 못했습니다." onRetry={load} /> : <Loading />}
      </Screen>
    )
  }

  const sku = selectSku(detail, selected)
  const total = totalPrice(detail, sku, quantity)

  const toggleWish = () => {
    const next = !detail.wished
    const before = detail
    setDetail({ ...detail, wished: next, wishCount: Math.max(0, detail.wishCount + (next ? 1 : -1)) })
    setWish(detail.id, next).catch(() => {
      setDetail(before)
      setMessage('찜을 바꾸지 못했습니다.')
    })
  }

  /** 같은 값을 다시 누르면 해제. 값을 바꾸면 수량은 1로 돌아간다(재고가 달라지므로). */
  const pick = (groupId: number, valueId: number) => {
    setSelected((s) => toggleValue(s, groupId, valueId))
    setQuantity(1)
  }

  const addToCart = async () => {
    if (!sku || working) return
    setWorking(true)
    try {
      await addCartItem(sku.id, quantity)
      setSheetOpen(false)
      setMessage('장바구니에 담았습니다')
    } catch (e) {
      setMessage(e instanceof ApiError && e.status === 400 ? e.message : '장바구니에 담지 못했습니다.')
    } finally {
      setWorking(false)
    }
  }

  const onScroll = (e: React.UIEvent<HTMLDivElement>) => {
    const el = e.currentTarget
    setSlide(Math.round(el.scrollLeft / el.clientWidth))
  }

  return (
    <Screen className="with-bottom-bar">
      <TopBar title={detail.name} back />
      <div className="gallery">
        {detail.images.length > 0 && (
          <div className="track" onScroll={onScroll}>
            {detail.images.map((src, i) => (
              <img key={src} src={src} alt={i === 0 ? detail.name : ''} />
            ))}
          </div>
        )}
        {detail.images.length > 1 && (
          <div className="dots">
            {detail.images.map((src, i) => (
              <i key={src} className={i === slide ? 'on' : undefined} />
            ))}
          </div>
        )}
      </div>
      <div className="detail-body">
        {detail.categoryPath.length > 0 && <div className="crumb">{detail.categoryPath.join(' › ')}</div>}
        <h1>{detail.name}</h1>
        <Price price={detail.price} listPrice={detail.listPrice} discountRate={detail.discountRate} />
        {detail.description && <p className="desc">{detail.description}</p>}
      </div>
      {detail.detail && (
        <div className="detail-text">
          <h2>상품 설명</h2>
          {detail.detail}
        </div>
      )}
      <div className="bottom-bar">
        <button type="button" className={`wish-big ${detail.wished ? 'on' : ''}`} aria-label={detail.wished ? '찜 해제' : '찜'} aria-pressed={detail.wished} onClick={toggleWish}>
          {detail.wished ? <HeartIcon /> : <HeartOutlineIcon />}
          <span>{detail.wishCount}</span>
        </button>
        <button type="button" className="btn primary" disabled={detail.soldOut} onClick={() => setSheetOpen(true)}>
          {detail.soldOut ? '품절' : '옵션 선택'}
        </button>
      </div>

      <BottomPanel open={sheetOpen} onClose={closeSheet}>
        <h3>옵션 선택</h3>
        {detail.optionGroups.map((group) => (
          <div key={group.id} className="opt-group">
            <div className="label">{group.name}</div>
            <div className="opt-values">
              {group.values.map((v) => (
                <button
                  key={v.id}
                  type="button"
                  className={`opt ${selected[group.id] === v.id ? 'on' : ''}`}
                  disabled={!isValueAvailable(detail, selected, group.id, v.id)}
                  aria-pressed={selected[group.id] === v.id}
                  onClick={() => pick(group.id, v.id)}
                >
                  {v.name}
                </button>
              ))}
            </div>
          </div>
        ))}
        <hr />
        {sku ? (
          <div className="sku-row">
            <div className="info">
              <div className="label">{sku.optionLabel || detail.name}</div>
              <div className="sub">
                {sku.extraPrice !== 0 && <b>{sku.extraPrice > 0 ? '+' : ''}{formatPrice(sku.extraPrice)} </b>}
                남은 수량 {sku.stock}
              </div>
            </div>
            <div className="stepper">
              <button type="button" aria-label="수량 줄이기" disabled={quantity <= 1} onClick={() => setQuantity((q) => clampQuantity(q - 1, sku))}>
                −
              </button>
              <span aria-label="수량">{quantity}</span>
              <button type="button" aria-label="수량 늘리기" disabled={quantity >= sku.stock} onClick={() => setQuantity((q) => clampQuantity(q + 1, sku))}>
                +
              </button>
            </div>
          </div>
        ) : (
          <div className="sub" style={{ color: 'var(--muted)', fontSize: 14 }}>
            옵션을 모두 골라 주세요
          </div>
        )}
        <div className="total">
          <span className="label">총 금액</span>
          <span className="amount">{formatPrice(total)}</span>
        </div>
        <div className="actions">
          <button type="button" className="btn outline" disabled={!sku || working} onClick={addToCart}>
            장바구니 담기
          </button>
        </div>
      </BottomPanel>
      <Toast message={message} onDone={clearMessage} />
    </Screen>
  )
}
