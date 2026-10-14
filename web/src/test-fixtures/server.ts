import { vi } from 'vitest'

/** 한 요청에 대한 가짜 서버의 답. 값을 주면 200 JSON, undefined 면 204. */
export type Handler = (body: unknown, url: URL) => unknown

/**
 * fetch 를 가로채 "METHOD /path" 로 답한다(쿼리는 url 로 본다). api/*.ts 의 덮개(recent.ts)까지 그대로 돈다 —
 * 레플리카가 늦은 서버를 흉내 내려면 쓰기 뒤에도 옛 목록을 돌려주면 된다. 모르는 요청은 500.
 */
export function fakeServer(routes: Record<string, Handler>) {
  const calls: string[] = []
  const fetchMock = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = new URL(String(input), 'http://localhost')
    const key = `${init?.method ?? 'GET'} ${url.pathname}`
    calls.push(key)
    const handler = routes[key]
    if (!handler) return new Response('{"message":"no route"}', { status: 500 })
    const body = init?.body ? JSON.parse(String(init.body)) : undefined
    const result = handler(body, url)
    return result === undefined ? new Response(null, { status: 204 }) : new Response(JSON.stringify(result), { status: 200 })
  })
  return { calls, fetchMock }
}

export const page = <T>(content: T[], totalElements = content.length, size = 20, number = 0) => ({
  content,
  totalElements,
  totalPages: Math.max(1, Math.ceil(totalElements / size)),
  number,
  size,
})
