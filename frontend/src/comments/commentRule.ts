/** 댓글 내용 규칙(서버와 같음): 앞뒤 공백을 지운 뒤 1~500자. 맞으면 undefined, 아니면 안내 문구. */
export const COMMENT_MAX_LENGTH = 500

export function checkCommentContent(content: string): string | undefined {
  const trimmed = content.trim()
  if (!trimmed) return '댓글 내용을 입력해 주세요.'
  if (trimmed.length > COMMENT_MAX_LENGTH) return `댓글은 ${COMMENT_MAX_LENGTH}자 이하로 입력해 주세요.`
  return undefined
}
