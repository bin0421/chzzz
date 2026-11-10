import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { ApiError } from '../api'
import { useAuth } from '../auth/auth'
import { formatDateTime } from '../format'
import { commentApi, type Comment, type CommentTarget } from './commentApi'
import { checkCommentContent } from './commentRule'

/**
 * 방송 화면 아래의 댓글 영역. 누구나 목록을 보고, 로그인한 회원만 쓰며, 작성자에게만 수정·삭제 버튼이 보인다.
 * 버튼을 숨기는 것은 보조이고 서버가 401·403으로 거절하면 그 안내를 보여 준다.
 */
export default function CommentSection({ kind, id, pollMs }: CommentTarget & { pollMs?: number }) {
  const { auth } = useAuth()
  const location = useLocation()
  const [comments, setComments] = useState<Comment[]>([])
  const [loadFailed, setLoadFailed] = useState(false)
  const [actionError, setActionError] = useState<string>()

  // pollMs가 있으면(라이브 방송) 주기적으로 다시 불러와 다른 사람이 쓴 댓글을 보여 준다.
  useEffect(() => {
    let cancelled = false
    let loaded = false
    const load = () =>
      commentApi.list({ kind, id }).then(
        (list) => {
          loaded = true
          if (cancelled) return
          setComments(list)
          setLoadFailed(false)
        },
        () => {
          // 처음 불러오기 실패만 알린다. 폴링 중 일시적 실패는 다음 폴링에 맡긴다.
          if (!cancelled && !loaded) setLoadFailed(true)
        },
      )
    load()
    const timer = pollMs ? setInterval(load, pollMs) : undefined
    return () => {
      cancelled = true
      clearInterval(timer)
    }
  }, [kind, id, pollMs])

  const memberId = auth.status === 'member' ? auth.member.id : undefined
  const next = encodeURIComponent(location.pathname + location.search)

  function replace(updated: Comment) {
    setComments((current) => current.map((c) => (c.id === updated.id ? updated : c)))
  }

  function remove(commentId: number) {
    setComments((current) => current.filter((c) => c.id !== commentId))
  }

  return (
    <section aria-labelledby="comments-heading" className="comments">
      <h2 id="comments-heading">댓글</h2>
      {actionError && (
        <p role="alert" className="notice">
          {actionError}
        </p>
      )}
      {loadFailed && <p className="notice">댓글을 불러오지 못했습니다.</p>}
      <ul>
        {comments.map((comment) => (
          <CommentItem
            key={comment.id}
            comment={comment}
            mine={comment.author.id === memberId}
            onEdited={(updated) => {
              setActionError(undefined)
              replace(updated)
            }}
            onDeleted={() => {
              setActionError(undefined)
              remove(comment.id)
            }}
            onRejected={setActionError}
          />
        ))}
      </ul>
      {auth.status === 'member' && (
        <CommentForm target={{ kind, id }} onWritten={(comment) => setComments((current) => [...current, comment])} />
      )}
      {auth.status === 'anonymous' && (
        <p>
          <span>로그인하면 댓글을 쓸 수 있습니다.</span> <Link to={`/login?next=${next}`}>로그인</Link>
        </p>
      )}
    </section>
  )
}

type CommentItemProps = {
  comment: Comment
  mine: boolean
  onEdited: (comment: Comment) => void
  onDeleted: () => void
  /** 서버가 401·403으로 거절했을 때 영역 위에 보여 줄 안내. */
  onRejected: (message: string) => void
}

function CommentItem({ comment, mine, onEdited, onDeleted, onRejected }: CommentItemProps) {
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState('')
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)
  const editorId = `comment-${comment.id}-editor`

  function startEditing() {
    setDraft(comment.content)
    setError(undefined)
    setEditing(true)
  }

  async function save(event: FormEvent) {
    event.preventDefault()
    const problem = checkCommentContent(draft)
    if (problem) return setError(problem)
    setBusy(true)
    try {
      onEdited(await commentApi.edit(comment.id, draft))
      setEditing(false)
    } catch (e) {
      const rejection = rejectionMessage(e)
      if (rejection) {
        setEditing(false)
        onRejected(rejection)
      } else {
        setError(contentError(e))
      }
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    if (!window.confirm('이 댓글을 삭제할까요?')) return
    setBusy(true)
    try {
      await commentApi.delete(comment.id)
      onDeleted()
    } catch (e) {
      onRejected(rejectionMessage(e) ?? '댓글을 삭제하지 못했습니다.')
      setBusy(false)
    }
  }

  return (
    <li>
      <p className="meta">
        <strong>{comment.author.nickname}</strong>
        <time dateTime={comment.createdAt}>{formatDateTime(comment.createdAt)}</time>
        {comment.updatedAt && <span>수정됨</span>}
      </p>
      {editing ? (
        <form className="comment-form" onSubmit={save} noValidate>
          <label htmlFor={editorId}>댓글 수정</label>
          <textarea
            id={editorId}
            autoFocus
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? `${editorId}-error` : undefined}
          />
          {error && (
            <p id={`${editorId}-error`} className="field-error">
              {error}
            </p>
          )}
          <button type="submit" disabled={busy}>
            저장
          </button>
          <button type="button" onClick={() => setEditing(false)}>
            취소
          </button>
        </form>
      ) : (
        <p className="comment-content">{comment.content}</p>
      )}
      {mine && !editing && (
        <p className="comment-actions">
          <button type="button" onClick={startEditing} disabled={busy}>
            수정
          </button>
          <button type="button" onClick={remove} disabled={busy}>
            삭제
          </button>
        </p>
      )}
    </li>
  )
}

function CommentForm({ target, onWritten }: { target: CommentTarget; onWritten: (comment: Comment) => void }) {
  const [content, setContent] = useState('')
  const [error, setError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent) {
    event.preventDefault()
    const problem = checkCommentContent(content)
    if (problem) return setError(problem)
    setSubmitting(true)
    try {
      onWritten(await commentApi.write(target, content))
      setContent('')
      setError(undefined)
    } catch (e) {
      setError(rejectionMessage(e) ?? contentError(e))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="comment-form" onSubmit={submit} noValidate>
      <label htmlFor="comment-content">댓글 쓰기</label>
      <textarea
        id="comment-content"
        value={content}
        onChange={(event) => setContent(event.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? 'comment-content-error' : undefined}
      />
      {error && (
        <p id="comment-content-error" className="field-error">
          {error}
        </p>
      )}
      <button type="submit" disabled={submitting}>
        등록
      </button>
    </form>
  )
}

/** 서버의 401·403 거절을 사용자에게 보일 안내로 바꾼다. 그 밖의 오류는 undefined. */
function rejectionMessage(error: unknown): string | undefined {
  if (!(error instanceof ApiError)) return undefined
  if (error.status === 401) return '로그인이 필요합니다. 다시 로그인해 주세요.'
  if (error.status === 403) return '댓글 작성자만 수정·삭제할 수 있습니다.'
  return undefined
}

function contentError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.errors.find((e) => e.field === 'content')?.message ?? error.message
  }
  return '댓글을 저장하지 못했습니다.'
}
