import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { getCart } from '../api/cart'
import { getProduct } from '../api/catalog'
import { ApiError } from '../api/client'
import { createAddress, createOrder, getAddresses, type Address, type AddressInput } from '../api/orders'
import { formatPoints, getMyPoints } from '../api/points'
import AddressForm, { AddressBlock } from '../components/AddressForm'
import BottomPanel from '../components/BottomPanel'
import { ErrorBox, Loading } from '../components/Boxes'
import { Screen, TopBar } from '../components/Layout'
import OrderItems, { type LineView } from '../components/OrderItems'
import Toast from '../components/Toast'
import { formatPrice } from '../util/format'

interface Line extends LineView {
  cartItemId: number | null
  skuId: number
}

/**
 * 주문서. 진입 두 가지: 장바구니(`?cartItemIds=1,2`) 또는 바로 구매(`?productId=&skuId=&quantity=`).
 * 결제는 모의라 "결제하기" 가 곧 주문 생성이고, 끝나면 주문 상세로 간다(주문서는 히스토리에서 뺀다).
 */
export default function CheckoutPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const query = params.toString()

  const [lines, setLines] = useState<Line[] | null>(null)
  const [addresses, setAddresses] = useState<Address[]>([])
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [error, setError] = useState(false)
  const [picker, setPicker] = useState(false)
  const [form, setForm] = useState(false)
  const [saving, setSaving] = useState(false)
  const [paying, setPaying] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  /** 보유 포인트. null = 못 불러옴(포인트 줄을 숨긴다). */
  const [balance, setBalance] = useState<number | null>(null)
  const [pointText, setPointText] = useState('')

  const load = useCallback(async () => {
    setError(false)
    try {
      const [loaded, addrs] = await Promise.all([loadLines(new URLSearchParams(query)), getAddresses()])
      setLines(loaded)
      setAddresses(addrs)
      setSelectedId((cur) => cur ?? (addrs.find((a) => a.isDefault) ?? addrs[0])?.id ?? null)
    } catch {
      setError(true)
    }
  }, [query])
  useEffect(() => {
    load()
  }, [load])
  useEffect(() => {
    getMyPoints().then(setBalance).catch(() => setBalance(null))
  }, [])

  const selected = addresses.find((a) => a.id === selectedId) ?? null
  const total = (lines ?? []).reduce((s, l) => s + l.lineAmount, 0)
  const maxPoints = Math.min(balance ?? 0, total)
  const usePoints = clampPoints(pointText, maxPoints)
  const payment = total - usePoints
  const canPay = !paying && !!lines && lines.length > 0 && selected !== null

  const closePicker = useCallback(() => setPicker(false), [])
  const closeForm = useCallback(() => setForm(false), [])
  const clearMessage = useCallback(() => setMessage(null), [])

  const saveAddress = async (input: AddressInput) => {
    setSaving(true)
    try {
      const created = await createAddress(input)
      const next = await getAddresses()
      setAddresses(next)
      setSelectedId(created.id)
      setForm(false)
      setPicker(false)
    } catch (e) {
      setMessage(e instanceof ApiError && e.status === 400 ? e.message : '배송지를 저장하지 못했습니다')
    } finally {
      setSaving(false)
    }
  }

  const pay = async () => {
    if (!canPay || !lines || !selected) return
    setPaying(true)
    try {
      const order = await createOrder(
        selected.id,
        lines.map((l) => ({ skuId: l.skuId, quantity: l.quantity })),
        lines.map((l) => l.cartItemId).filter((id): id is number => id !== null),
        usePoints,
      )
      navigate(`/orders/${order.id}`, { replace: true, state: { justOrdered: true } })
    } catch (e) {
      setMessage(e instanceof ApiError && e.status === 400 ? e.message : '주문하지 못했습니다')
      setPaying(false)
    }
  }

  return (
    <Screen className="with-bottom-bar">
      <TopBar title="주문서" back />
      {error ? (
        <ErrorBox message="주문서를 불러오지 못했습니다." onRetry={load} />
      ) : lines === null ? (
        <Loading />
      ) : (
        <>
          <section className="block">
            <div className="block-head">
              <h2>배송지</h2>
              {addresses.length > 0 && (
                <button type="button" className="btn outline small" onClick={() => setPicker(true)}>
                  변경
                </button>
              )}
            </div>
            {selected ? (
              <AddressBlock address={selected} />
            ) : (
              <div>
                <div style={{ color: 'var(--muted)', marginBottom: 10 }}>배송지를 등록해 주세요</div>
                <button type="button" className="btn outline small" onClick={() => setForm(true)}>
                  배송지 추가
                </button>
              </div>
            )}
          </section>
          <section className="block">
            <div className="block-head">
              <h2>주문 상품</h2>
            </div>
            <OrderItems lines={lines} />
          </section>
          {balance !== null && (
            <section className="block">
              <div className="block-head">
                <h2>포인트</h2>
                <span className="point-have">보유 {formatPoints(balance)}</span>
              </div>
              <div className="point-use">
                <input
                  type="number"
                  inputMode="numeric"
                  aria-label="사용 포인트"
                  placeholder="0"
                  min={0}
                  max={maxPoints}
                  value={pointText}
                  disabled={maxPoints === 0}
                  onChange={(e) => setPointText(e.target.value)}
                  onBlur={() => setPointText(usePoints === 0 ? '' : String(usePoints))}
                />
                <span className="unit">P</span>
                <button type="button" className="btn outline small" disabled={maxPoints === 0} onClick={() => setPointText(String(maxPoints))}>
                  전액 사용
                </button>
              </div>
              <div className="point-hint">{maxPoints === 0 ? '사용할 수 있는 포인트가 없습니다.' : `최대 ${formatPoints(maxPoints)}까지 쓸 수 있어요. 1P = 1원`}</div>
            </section>
          )}
          <section className="block">
            <div className="block-head">
              <h2>결제 수단</h2>
            </div>
            <div>모의 결제 (실제 결제 없음)</div>
          </section>
          <section className="block">
            <div className="block-head">
              <h2>결제 금액</h2>
            </div>
            <div className="kv muted">
              <span>상품 금액</span>
              <span>{formatPrice(total)}</span>
            </div>
            {usePoints > 0 && (
              <div className="kv muted">
                <span>포인트 사용</span>
                <span>-{formatPrice(usePoints)}</span>
              </div>
            )}
            <div className="kv">
              <span>결제 금액</span>
              <span className="v">{formatPrice(payment)}</span>
            </div>
          </section>
          <div className="bottom-bar">
            <div className="summary">
              <div className="label">결제 금액</div>
              <div className="amount">{formatPrice(payment)}</div>
            </div>
            <button type="button" className="btn primary cta" disabled={!canPay} onClick={pay}>
              결제하기
            </button>
          </div>
        </>
      )}

      <BottomPanel open={picker} onClose={closePicker}>
        <h3>배송지 선택</h3>
        {addresses.map((a) => (
          <button
            key={a.id}
            type="button"
            className={`pick-row ${a.id === selectedId ? 'on' : ''}`}
            onClick={() => {
              setSelectedId(a.id)
              setPicker(false)
            }}
          >
            <span className="radio" />
            <AddressBlock address={a} />
          </button>
        ))}
        <button type="button" className="btn outline small" style={{ marginTop: 12 }} onClick={() => setForm(true)}>
          배송지 추가
        </button>
      </BottomPanel>
      <AddressForm open={form} saving={saving} onSubmit={saveAddress} onClose={closeForm} />
      <Toast message={message} onDone={clearMessage} />
    </Screen>
  )
}

async function loadLines(params: URLSearchParams): Promise<Line[]> {
  const cartItemIds = (params.get('cartItemIds') ?? '').split(',').map(Number).filter((n) => n > 0)
  const productId = Number(params.get('productId') ?? 0)
  const skuId = Number(params.get('skuId') ?? 0)
  const quantity = Math.max(1, Number(params.get('quantity') ?? 1))
  if (cartItemIds.length > 0) {
    const cart = await getCart()
    return cart.items
      .filter((i) => cartItemIds.includes(i.id))
      .map((i) => ({ key: i.id, cartItemId: i.id, skuId: i.skuId, productId: i.productId, productName: i.productName, optionLabel: i.optionLabel, imageUrl: i.imageUrl, unitPrice: i.unitPrice, quantity: i.quantity, lineAmount: i.lineAmount }))
  }
  if (productId > 0 && skuId > 0) {
    const p = await getProduct(productId)
    const sku = p.skus.find((s) => s.id === skuId)
    if (!sku) return []
    const unitPrice = p.price + sku.extraPrice
    return [{ key: skuId, cartItemId: null, skuId, productId, productName: p.name, optionLabel: sku.optionLabel, imageUrl: p.images[0] ?? null, unitPrice, quantity, lineAmount: unitPrice * quantity }]
  }
  return []
}

/** 입력한 포인트를 0~최대치의 정수로 맞춘다. 빈 값·글자는 0. */
export function clampPoints(text: string, max: number): number {
  const n = Math.floor(Number(text))
  if (!Number.isFinite(n) || n <= 0) return 0
  return Math.min(n, max)
}
