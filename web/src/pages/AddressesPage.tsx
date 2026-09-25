import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import { createAddress, deleteAddress, getAddresses, setDefaultAddress, updateAddress, type Address, type AddressInput } from '../api/orders'
import AddressForm, { AddressBlock } from '../components/AddressForm'
import { EmptyBox, ErrorBox, Loading } from '../components/Boxes'
import ConfirmDialog from '../components/ConfirmDialog'
import { Screen, TopBar } from '../components/Layout'
import Toast from '../components/Toast'

export default function AddressesPage() {
  const [addresses, setAddresses] = useState<Address[] | null>(null)
  const [error, setError] = useState(false)
  const [editing, setEditing] = useState<Address | null | undefined>(undefined) // undefined = 닫힘, null = 새로 추가
  const [removing, setRemoving] = useState<Address | null>(null)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState<string | null>(null)

  const load = useCallback(() => {
    setError(false)
    getAddresses()
      .then(setAddresses)
      .catch(() => setError(true))
  }, [])
  useEffect(load, [load])

  const closeForm = useCallback(() => setEditing(undefined), [])
  const clearMessage = useCallback(() => setMessage(null), [])

  const save = async (input: AddressInput) => {
    setSaving(true)
    try {
      if (editing) await updateAddress(editing.id, input)
      else await createAddress(input)
      setEditing(undefined)
      load()
    } catch (e) {
      setMessage(e instanceof ApiError && e.status === 400 ? e.message : '배송지를 저장하지 못했습니다')
    } finally {
      setSaving(false)
    }
  }

  const makeDefault = async (a: Address) => {
    try {
      await setDefaultAddress(a.id)
      load()
    } catch {
      setMessage('기본 배송지를 바꾸지 못했습니다')
    }
  }

  const remove = async () => {
    const target = removing
    setRemoving(null)
    if (!target) return
    try {
      await deleteAddress(target.id)
      load()
    } catch {
      setMessage('삭제하지 못했습니다')
    }
  }

  return (
    <Screen className="with-bottom-bar">
      <TopBar title="배송지 관리" back />
      {error ? (
        <ErrorBox message="배송지를 불러오지 못했습니다." onRetry={load} />
      ) : addresses === null ? (
        <Loading />
      ) : addresses.length === 0 ? (
        <EmptyBox message="등록한 배송지가 없습니다." />
      ) : (
        addresses.map((a) => (
          <div key={a.id} className="addr-card">
            <AddressBlock address={a} />
            <div className="links">
              <button type="button" onClick={() => setEditing(a)}>
                수정
              </button>
              <button type="button" onClick={() => setRemoving(a)}>
                삭제
              </button>
              {!a.isDefault && (
                <button type="button" className="brand" onClick={() => makeDefault(a)}>
                  기본으로 설정
                </button>
              )}
            </div>
          </div>
        ))
      )}
      <div className="fab-bar">
        <button type="button" className="btn primary block" onClick={() => setEditing(null)}>
          배송지 추가
        </button>
      </div>
      <AddressForm open={editing !== undefined} initial={editing ?? null} saving={saving} onSubmit={save} onClose={closeForm} />
      <ConfirmDialog open={removing !== null} title="배송지 삭제" message={`${removing?.recipient ?? ''} 배송지를 삭제할까요?`} confirmLabel="삭제" onConfirm={remove} onClose={() => setRemoving(null)} />
      <Toast message={message} onDone={clearMessage} />
    </Screen>
  )
}
