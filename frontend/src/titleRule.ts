export const TITLE_MAX_LENGTH = 100

/** 녹화방송과 라이브 방송이 함께 쓰는 제목 규칙: 앞뒤 공백을 뺀 1~100자. 어기면 안내 문구를 돌려준다. */
export function validateTitle(title: string): string | undefined {
  const trimmed = title.trim()
  if (trimmed.length === 0) return '제목을 입력해 주세요.'
  if (trimmed.length > TITLE_MAX_LENGTH) return `제목은 ${TITLE_MAX_LENGTH}자 이하로 입력해 주세요.`
  return undefined
}
