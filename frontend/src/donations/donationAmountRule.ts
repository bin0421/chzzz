/** 후원 금액 규칙(서버와 같음): 원 단위 정수, 1,000 이상 1,000,000 이하. */
export const MIN_DONATION = 1000
export const MAX_DONATION = 1_000_000

/** 규칙에 맞지 않으면 이유를, 맞으면 undefined를 돌려준다. 결제창을 열기 전에 막는 보조이고 서버가 다시 검사한다. */
export function checkDonationAmount(typed: string): string | undefined {
  const value = Number(typed)
  if (typed.trim() === '' || !Number.isInteger(value) || value < MIN_DONATION || value > MAX_DONATION) {
    return '후원 금액은 1,000원 이상 1,000,000원 이하의 정수로 입력해 주세요.'
  }
  return undefined
}
