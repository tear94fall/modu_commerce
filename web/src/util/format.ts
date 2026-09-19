/** 89000 → "89,000원". 앱·백오피스와 같은 표기. */
export const formatPrice = (price: number) => `${price.toLocaleString('ko-KR')}원`
