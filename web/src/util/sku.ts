import type { ProductDetail, Sku } from '../api/catalog'

/** 옵션 그룹 id → 고른 값 id. */
export type Selection = Record<number, number>

/** 모든 그룹을 골랐을 때의 SKU. 옵션 없는 상품은 유일한 SKU. 아직 덜 골랐으면 undefined. */
export function selectSku(detail: ProductDetail, selected: Selection): Sku | undefined {
  if (detail.optionGroups.length === 0) return detail.skus[0]
  const chosen = Object.values(selected)
  if (chosen.length < detail.optionGroups.length) return undefined
  return detail.skus.find((sku) => sku.optionValueIds.length === chosen.length && chosen.every((id) => sku.optionValueIds.includes(id)))
}

/**
 * 이 값을 고를 수 있는가: 이미 고른 다른 그룹의 값들과 함께 재고가 있는 SKU 가 하나라도 있으면 된다.
 * 그룹 하나뿐이면 그 값을 가진 SKU 의 재고만 본다.
 */
export function isValueAvailable(detail: ProductDetail, selected: Selection, groupId: number, valueId: number): boolean {
  const others = Object.entries(selected)
    .filter(([g]) => Number(g) !== groupId)
    .map(([, v]) => v)
  return detail.skus.some((sku) => sku.stock > 0 && sku.optionValueIds.includes(valueId) && others.every((id) => sku.optionValueIds.includes(id)))
}

/** 같은 값을 다시 누르면 해제. */
export function toggleValue(selected: Selection, groupId: number, valueId: number): Selection {
  const next = { ...selected }
  if (next[groupId] === valueId) delete next[groupId]
  else next[groupId] = valueId
  return next
}

export const totalPrice = (detail: ProductDetail, sku: Sku | undefined, quantity: number) => (sku ? (detail.price + sku.extraPrice) * quantity : 0)

export const clampQuantity = (quantity: number, sku: Sku | undefined) => Math.min(Math.max(quantity, 1), Math.max(sku?.stock ?? 1, 1))
