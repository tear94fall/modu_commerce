import { describe, expect, it } from 'vitest'
import { pictureUrl } from './me'

describe('pictureUrl', () => {
  it('proxies storage file names and keeps absolute urls', () => {
    expect(pictureUrl('abc.png')).toBe('/storage-service/api-public/download?file=abc.png')
    expect(pictureUrl('https://lh3.googleusercontent.com/a/x')).toBe('https://lh3.googleusercontent.com/a/x')
    expect(pictureUrl('')).toBe('')
  })
})
