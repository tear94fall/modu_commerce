import { Screen, TopBar } from '../components/Layout'

interface Section {
  title: string
  body: string[]
}

/** 서버 `modu.customer.terms-version` 과 같은 값. */
const TERMS_VERSION = '2026-10'

const TERMS: Section[] = [
  { title: '제1조 (목적)', body: ['이 약관은 모두의 커머스(이하 "서비스")가 제공하는 상품 구매·쿠폰·포인트·회원 등급 서비스의 이용 조건과 절차, 이용자와 서비스의 권리·의무를 정합니다.'] },
  {
    title: '제2조 (가입과 탈퇴)',
    body: [
      '모두 계정으로 로그인한 뒤 이 약관과 개인정보 수집·이용에 동의하면 서비스 회원이 됩니다.',
      '모두 계정을 탈퇴하면 서비스 회원 자격도 함께 끝나고, 알림 수신 정보는 바로 지웁니다.',
    ],
  },
  { title: '제3조 (주문과 결제)', body: ['지금의 결제는 모의 결제로, 실제 대금이 오가지 않습니다. 결제완료 상태의 주문은 언제든 취소할 수 있습니다.'] },
  {
    title: '제4조 (포인트·쿠폰·회원 등급)',
    body: [
      '회원 등급은 매월 1일, 지난 6개월 동안 배송 완료된 주문의 결제 금액(포인트로 낸 금액 제외)으로 정합니다.',
      '배송이 완료되면 그때의 등급 적립률만큼 포인트를 적립합니다. 등급별 기준·적립률·쿠폰은 회원 등급 화면에서 볼 수 있고, 미리 알린 뒤 바뀔 수 있습니다.',
    ],
  },
  { title: '제5조 (책임의 제한)', body: ['개발 중인 서비스로, 데이터가 예고 없이 초기화될 수 있습니다.'] },
  { title: '문의', body: ['모두의 채팅 앱의 문의하기로 연락해 주세요. (개발용 초안 — 연락처 미정)'] },
]

const PRIVACY: Section[] = [
  { title: '수집·이용 목적', body: ['상품 주문과 배송, 쿠폰·포인트 지급, 회원 등급 산정, 혜택 알림 발송(동의한 경우)에 씁니다.'] },
  {
    title: '수집 항목',
    body: [
      '모두 계정 식별자, 이름, 이메일(로그인 정보에서 받음)',
      '배송지(받는 사람, 연락처, 주소), 주문·결제 기록, 찜·장바구니·리뷰',
      '알림을 받는 경우 기기 푸시 토큰과 수신 동의 기록',
    ],
  },
  { title: '보유 기간', body: ['회원 탈퇴 때까지 보관합니다. 다만 관계 법령이 보관을 요구하는 주문·결제 기록은 그 기간 동안 보관합니다.'] },
  { title: '제3자 제공', body: ['수집한 개인정보를 제3자에게 제공하지 않습니다.'] },
  { title: '동의 거부', body: ['동의하지 않을 수 있지만, 이 경우 장바구니·주문·쿠폰 등 회원 기능은 쓸 수 없고 상품 둘러보기만 할 수 있습니다.'] },
  { title: '문의', body: ['모두의 채팅 앱의 문의하기로 연락해 주세요. (개발용 초안 — 연락처 미정)'] },
]

function LegalPage({ title, sections }: { title: string; sections: Section[] }) {
  return (
    <Screen className="my legal">
      <TopBar title={title} back />
      <article className="my-card legal-doc">
        <p className="draft-mark" role="note">
          개발용 초안 · 실제 약관이 아닙니다
        </p>
        <p className="version">버전 {TERMS_VERSION}</p>
        {sections.map((s) => (
          <section key={s.title}>
            <h2>{s.title}</h2>
            {s.body.length === 1 ? (
              <p>{s.body[0]}</p>
            ) : (
              <ul>
                {s.body.map((b) => (
                  <li key={b}>{b}</li>
                ))}
              </ul>
            )}
          </section>
        ))}
      </article>
    </Screen>
  )
}

export const TermsPage = () => <LegalPage title="이용약관" sections={TERMS} />

export const PrivacyPage = () => <LegalPage title="개인정보 수집·이용" sections={PRIVACY} />
