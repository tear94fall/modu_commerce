/** 다음 장을 붙인다. 이미 있는 것(앞 장에 끼운 방금 쓴 것, 그사이 밀려 겹친 것)은 빼고. */
export function appendNew<T extends { id: number }>(current: T[], next: T[]): T[] {
  const seen = new Set(current.map((x) => x.id))
  return [...current, ...next.filter((x) => !seen.has(x.id))]
}
