import { json, request, type Member } from '../api'

/** 댓글이 달리는 방송. 녹화방송과 라이브 방송은 식별자가 겹칠 수 있어 종류로 구분한다. */
export type CommentTarget = { kind: 'recording' | 'live'; id: string | number }

export type Comment = {
  id: number
  content: string
  author: Member
  createdAt: string
  /** 수정 전에는 null. 값이 있으면 "수정됨"으로 표시한다. */
  updatedAt: string | null
}

function commentsPath(target: CommentTarget) {
  const broadcasts = target.kind === 'recording' ? 'recordings' : 'live-broadcasts'
  return `/api/${broadcasts}/${target.id}/comments`
}

export const commentApi = {
  list(target: CommentTarget) {
    return request<Comment[]>(commentsPath(target))
  },
  write(target: CommentTarget, content: string) {
    return request<Comment>(commentsPath(target), json('POST', { content }))
  },
  edit(commentId: number, content: string) {
    return request<Comment>(`/api/comments/${commentId}`, json('PATCH', { content }))
  },
  delete(commentId: number) {
    return request<void>(`/api/comments/${commentId}`, { method: 'DELETE' })
  },
}
