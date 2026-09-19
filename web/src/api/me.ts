import { bridge } from '../bridge/app'
import { api } from './client'

export interface Profile {
  name: string
  email: string
  picture: string
}

/** 앱은 로그인 때 받아 둔 프로필을 브리지로 주고, 브라우저는 auth-service userinfo 를 부른다. */
export async function getProfile(): Promise<Profile> {
  const b = bridge()
  if (b) return normalize(JSON.parse(b.getProfile() || '{}'))
  return normalize(await api<Partial<Profile>>('/auth-service/userinfo'))
}

const normalize = (p: Partial<Profile> | null | undefined): Profile => ({ name: p?.name ?? '', email: p?.email ?? '', picture: p?.picture ?? '' })
