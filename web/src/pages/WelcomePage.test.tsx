import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import * as customerApi from '../api/customer'
import type { ModuAppBridge } from '../bridge/app'
import { ModuWebBridge } from '../bridge/web'
import CustomerLayout from '../customer/CustomerProvider'
import { customerMe } from '../test-fixtures/customer'
import WelcomePage from './WelcomePage'

function Where() {
  const location = useLocation()
  return <p>도착 {location.pathname + location.search}</p>
}

const renderWelcome = (url = '/welcome?next=%2Fcheckout%3FcartItemIds%3D1%2C2') =>
  render(
    <MemoryRouter initialEntries={[url]}>
      <ModuWebBridge />
      <Routes>
        <Route element={<CustomerLayout />}>
          <Route path="/welcome" element={<WelcomePage />} />
          <Route path="*" element={<Where />} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )

const startButton = () => screen.getByRole('button', { name: '동의하고 시작하기' })
const check = (name: string) => userEvent.click(screen.getByRole('checkbox', { name }))

describe('WelcomePage', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    sessionStorage.clear()
    vi.spyOn(customerApi, 'getMyCustomer').mockResolvedValue(null)
  })
  afterEach(() => {
    delete window.ModuApp
  })

  it('keeps the start button disabled until both required agreements are checked', async () => {
    renderWelcome()

    expect(await screen.findByText('모두의 커머스에 오신 걸 환영해요')).toBeInTheDocument()
    expect(startButton()).toBeDisabled()
    await check('이용약관 동의 (필수)')
    expect(startButton()).toBeDisabled()
    await check('혜택·이벤트 알림 수신 동의 (선택)')
    expect(startButton()).toBeDisabled()
    await check('개인정보 수집·이용 동의 (필수)')
    expect(startButton()).toBeEnabled()
    await check('이용약관 동의 (필수)')
    expect(startButton()).toBeDisabled()
  })

  it('checks and clears everything with 전체 동의, and links to the draft terms', async () => {
    renderWelcome()
    const all = await screen.findByRole('checkbox', { name: '전체 동의' })

    await userEvent.click(all)
    for (const name of ['이용약관 동의 (필수)', '개인정보 수집·이용 동의 (필수)', '혜택·이벤트 알림 수신 동의 (선택)']) expect(screen.getByRole('checkbox', { name })).toBeChecked()
    expect(startButton()).toBeEnabled()
    await userEvent.click(all)
    expect(startButton()).toBeDisabled()

    expect(screen.getByRole('link', { name: '이용약관 동의 보기' })).toHaveAttribute('href', '/terms')
    expect(screen.getByRole('link', { name: '개인정보 수집·이용 동의 보기' })).toHaveAttribute('href', '/privacy')
  })

  it('joins and goes back to the next path', async () => {
    const join = vi.spyOn(customerApi, 'joinCustomer').mockResolvedValue(customerMe())
    renderWelcome()

    await check('이용약관 동의 (필수)')
    await check('개인정보 수집·이용 동의 (필수)')
    await userEvent.click(startButton())

    expect(join).toHaveBeenCalledWith({ agreeTerms: true, agreePrivacy: true, marketing: false })
    expect(await screen.findByText('도착 /checkout?cartItemIds=1,2')).toBeInTheDocument()
  })

  it('goes home when next is not an allowed in-app path', async () => {
    vi.spyOn(customerApi, 'joinCustomer').mockResolvedValue(customerMe())
    renderWelcome('/welcome?next=https%3A%2F%2Fevil.example')

    await userEvent.click(await screen.findByRole('checkbox', { name: '전체 동의' }))
    await userEvent.click(startButton())

    expect(await screen.findByText('도착 /')).toBeInTheDocument()
  })

  it('asks for the OS notification permission in the app when marketing is checked, then joins anyway', async () => {
    const requestNotificationPermission = vi.fn(() => {
      setTimeout(() => window.ModuWeb?.onNotificationPermission('denied'), 0)
    })
    window.ModuApp = { getNotificationPermission: () => 'default', requestNotificationPermission } as unknown as ModuAppBridge
    const join = vi.spyOn(customerApi, 'joinCustomer').mockResolvedValue(customerMe())
    renderWelcome()

    await userEvent.click(await screen.findByRole('checkbox', { name: '전체 동의' }))
    await userEvent.click(startButton())

    expect(await screen.findByText('도착 /checkout?cartItemIds=1,2')).toBeInTheDocument()
    expect(requestNotificationPermission).toHaveBeenCalledOnce()
    expect(join).toHaveBeenCalledWith({ agreeTerms: true, agreePrivacy: true, marketing: true })
  })

  it('does not ask again when the permission was already answered', async () => {
    const requestNotificationPermission = vi.fn()
    window.ModuApp = { getNotificationPermission: () => 'granted', requestNotificationPermission } as unknown as ModuAppBridge
    vi.spyOn(customerApi, 'joinCustomer').mockResolvedValue(customerMe())
    renderWelcome()

    await userEvent.click(await screen.findByRole('checkbox', { name: '전체 동의' }))
    await userEvent.click(startButton())

    expect(await screen.findByText('도착 /checkout?cartItemIds=1,2')).toBeInTheDocument()
    expect(requestNotificationPermission).not.toHaveBeenCalled()
  })

  it('shows the server message and stays when joining fails', async () => {
    vi.spyOn(customerApi, 'joinCustomer').mockRejectedValue(new ApiError(400, JSON.stringify({ message: '필수 약관에 동의해 주세요' })))
    renderWelcome()

    await userEvent.click(await screen.findByRole('checkbox', { name: '전체 동의' }))
    await userEvent.click(startButton())

    expect(await screen.findByText('필수 약관에 동의해 주세요')).toBeInTheDocument()
    expect(startButton()).toBeEnabled()
  })

  it('sends an existing customer straight to next', async () => {
    vi.spyOn(customerApi, 'getMyCustomer').mockResolvedValue(customerMe())
    renderWelcome('/welcome?next=%2Forders')

    expect(await screen.findByText('도착 /orders')).toBeInTheDocument()
  })
})
