import { useState, type FormEvent } from 'react'
import type { Address, AddressInput } from '../api/orders'
import { fullAddress } from '../api/orders'
import BottomPanel from './BottomPanel'

interface AddressFormProps {
  open: boolean
  initial?: Address | null
  saving: boolean
  onSubmit: (input: AddressInput) => void
  onClose: () => void
}

/** 배송지 입력 패널. 받는 사람·연락처·주소가 있고 우편번호가 5자리여야 저장할 수 있다(앱과 같은 규칙). */
export default function AddressForm({ open, initial, saving, onSubmit, onClose }: AddressFormProps) {
  return (
    <BottomPanel open={open} onClose={onClose}>
      {open && <Fields key={initial?.id ?? 'new'} initial={initial} saving={saving} onSubmit={onSubmit} onClose={onClose} />}
    </BottomPanel>
  )
}

function Fields({ initial, saving, onSubmit, onClose }: Omit<AddressFormProps, 'open'>) {
  const [recipient, setRecipient] = useState(initial?.recipient ?? '')
  const [phone, setPhone] = useState(initial?.phone ?? '')
  const [zipCode, setZipCode] = useState(initial?.zipCode ?? '')
  const [address1, setAddress1] = useState(initial?.address1 ?? '')
  const [address2, setAddress2] = useState(initial?.address2 ?? '')
  const [isDefault, setIsDefault] = useState(initial?.isDefault ?? false)
  const valid = recipient.trim() !== '' && phone.trim() !== '' && zipCode.length === 5 && address1.trim() !== ''

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!valid || saving) return
    onSubmit({ recipient: recipient.trim(), phone: phone.trim(), zipCode, address1: address1.trim(), address2: address2.trim() || null, isDefault })
  }

  return (
    <form className="address-form" onSubmit={submit}>
      <h3>배송지</h3>
      <label>
        받는 사람
        <input value={recipient} onChange={(e) => setRecipient(e.target.value)} autoComplete="name" />
      </label>
      <label>
        연락처
        <input value={phone} onChange={(e) => setPhone(e.target.value)} type="tel" autoComplete="tel" />
      </label>
      <label>
        우편번호
        <input value={zipCode} onChange={(e) => setZipCode(e.target.value.replace(/\D/g, '').slice(0, 5))} inputMode="numeric" autoComplete="postal-code" />
      </label>
      <label>
        주소
        <input value={address1} onChange={(e) => setAddress1(e.target.value)} autoComplete="street-address" />
      </label>
      <label>
        상세 주소
        <input value={address2} onChange={(e) => setAddress2(e.target.value)} />
      </label>
      <label className="check">
        <input type="checkbox" checked={isDefault} onChange={(e) => setIsDefault(e.target.checked)} />
        기본 배송지로 설정
      </label>
      <div className="actions">
        <button type="button" className="btn outline" onClick={onClose}>
          취소
        </button>
        <button type="submit" className="btn primary" disabled={!valid || saving}>
          저장
        </button>
      </div>
    </form>
  )
}

/** 이름 + 기본 뱃지, 연락처, 주소 한 덩어리. 주문서·배송지 관리·주문 상세가 같이 쓴다. */
export function AddressBlock({ address }: { address: Address }) {
  return (
    <div className="addr">
      <div className="addr-name">
        {address.recipient}
        {address.isDefault && <span className="tag">기본</span>}
      </div>
      <div className="addr-sub">{address.phone}</div>
      <div className="addr-line">
        ({address.zipCode}) {fullAddress(address)}
      </div>
    </div>
  )
}
