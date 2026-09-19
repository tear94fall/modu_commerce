import { describe, expect, it } from 'vitest'
import type { ProductDetail } from '../api/catalog'
import { clampQuantity, isValueAvailable, selectSku, toggleValue, totalPrice } from './sku'

const detail = (over: Partial<ProductDetail> = {}): ProductDetail => ({
  id: 1,
  name: '티셔츠',
  description: '',
  detail: null,
  images: [],
  price: 10000,
  listPrice: null,
  discountRate: 0,
  soldOut: false,
  wished: false,
  wishCount: 0,
  categoryPath: [],
  optionGroups: [
    { id: 1, name: '색상', values: [{ id: 11, name: '블랙' }, { id: 12, name: '화이트' }] },
    { id: 2, name: '사이즈', values: [{ id: 21, name: 'M' }, { id: 22, name: 'L' }] },
  ],
  skus: [
    { id: 100, optionValueIds: [11, 21], optionLabel: '블랙 / M', extraPrice: 0, stock: 3 },
    { id: 101, optionValueIds: [11, 22], optionLabel: '블랙 / L', extraPrice: 500, stock: 0 },
    { id: 102, optionValueIds: [12, 21], optionLabel: '화이트 / M', extraPrice: 0, stock: 1 },
  ],
  ...over,
})

describe('sku', () => {
  it('resolves the SKU only when every group is chosen', () => {
    const d = detail()
    expect(selectSku(d, {})).toBeUndefined()
    expect(selectSku(d, { 1: 11 })).toBeUndefined()
    expect(selectSku(d, { 1: 11, 2: 22 })?.id).toBe(101)
    expect(selectSku(d, { 2: 21, 1: 12 })?.id).toBe(102)
    expect(selectSku(d, { 1: 12, 2: 22 })).toBeUndefined()
  })

  it('uses the single SKU of a product without options', () => {
    const d = detail({ optionGroups: [], skus: [{ id: 7, optionValueIds: [], optionLabel: '', extraPrice: 0, stock: 5 }] })
    expect(selectSku(d, {})?.id).toBe(7)
  })

  it('marks a value available only with an in-stock SKU that fits the other choices', () => {
    const d = detail()
    expect(isValueAvailable(d, {}, 1, 11)).toBe(true)
    expect(isValueAvailable(d, {}, 2, 22)).toBe(false) // 블랙/L 만 있는데 재고 0
    expect(isValueAvailable(d, { 1: 12 }, 2, 22)).toBe(false) // 화이트/L 조합 없음
    expect(isValueAvailable(d, { 2: 21 }, 1, 12)).toBe(true)
  })

  it('toggles a value and prices by quantity within stock', () => {
    const d = detail()
    expect(toggleValue({ 1: 11 }, 1, 11)).toEqual({})
    expect(toggleValue({ 1: 11 }, 1, 12)).toEqual({ 1: 12 })
    const sku = selectSku(d, { 1: 11, 2: 22 })
    expect(totalPrice(d, sku, 2)).toBe(21000)
    expect(totalPrice(d, undefined, 2)).toBe(0)
    expect(clampQuantity(9, selectSku(d, { 1: 11, 2: 21 }))).toBe(3)
    expect(clampQuantity(0, undefined)).toBe(1)
  })
})
