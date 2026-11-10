import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { defaultMember, requestsTo, stubFetch } from './test/fetchStub'
import { renderApp } from './test/renderApp'

const 업로드방송 = {
  id: 7,
  title: '업로드 방송',
  sourceType: 'UPLOADED_FILE',
  videoUrl: '/api/recordings/7/video',
  createdAt: '2026-11-09T06:00:00Z',
}

describe('녹화방송 등록: 업로드 파일', () => {
  it('출처 종류를 전환하면 고른 종류의 입력란만 보인다', async () => {
    stubFetch([])
    const user = userEvent.setup()
    renderApp('/recordings/new')

    await user.click(screen.getByRole('radio', { name: '업로드 파일' }))
    expect(screen.getByLabelText('영상 파일')).toBeInTheDocument()
    expect(screen.queryByLabelText('영상 URL')).not.toBeInTheDocument()

    await user.click(screen.getByRole('radio', { name: '외부 URL' }))
    expect(screen.getByLabelText('영상 URL')).toBeInTheDocument()
    expect(screen.queryByLabelText('영상 파일')).not.toBeInTheDocument()
  })

  it('파일을 골라 등록하면 상세 화면으로 이동해 업로드한 영상을 재생한다', async () => {
    const fetchMock = stubFetch([
      { method: 'POST', path: '/api/recordings', status: 201, body: 업로드방송 },
      { path: '/api/recordings/7', body: 업로드방송 },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/new')
    const video = new File(['fake-video'], 'clip.mp4', { type: 'video/mp4' })

    await user.type(screen.getByLabelText('제목'), '업로드 방송')
    await user.click(screen.getByRole('radio', { name: '업로드 파일' }))
    await user.upload(screen.getByLabelText('영상 파일'), video)
    await user.click(screen.getByRole('button', { name: '등록' }))

    expect(await screen.findByRole('heading', { name: '업로드 방송' })).toBeInTheDocument()
    expect(screen.getByTestId('recording-player')).toHaveAttribute('src', '/api/recordings/7/video')
    const body = requestsTo(fetchMock, 'POST', '/api/recordings')[0][1]?.body as FormData
    expect(body.get('title')).toBe('업로드 방송')
    expect(body.get('file')).toBe(video)
    expect(body.has('url')).toBe(false)
  })

  it('서버가 파일을 거절하면 그 이유를 파일 입력란 옆에 보여 준다', async () => {
    stubFetch([
      {
        method: 'POST',
        path: '/api/recordings',
        status: 400,
        body: {
          status: 400,
          detail: '입력값을 확인해 주세요.',
          errors: [{ field: 'file', message: 'mp4 또는 webm 파일만 올릴 수 있습니다.' }],
        },
      },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/new')

    await user.type(screen.getByLabelText('제목'), '형식이 틀린 방송')
    await user.click(screen.getByRole('radio', { name: '업로드 파일' }))
    await user.upload(screen.getByLabelText('영상 파일'), new File(['v'], 'clip.mp4', { type: 'video/mp4' }))
    await user.click(screen.getByRole('button', { name: '등록' }))

    const fileInput = await screen.findByLabelText('영상 파일')
    expect(await screen.findByText('mp4 또는 webm 파일만 올릴 수 있습니다.')).toBeInTheDocument()
    expect(fileInput).toHaveAccessibleDescription('mp4 또는 webm 파일만 올릴 수 있습니다.')
    expect(screen.getByRole('alert')).not.toHaveTextContent('mp4 또는 webm 파일만 올릴 수 있습니다.')
  })

  it('업로드가 끝날 때까지 등록 버튼이 비활성화된다', async () => {
    let finishUpload: (response: Response) => void = () => {}
    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
        if (init?.method === 'POST') return new Promise<Response>((resolve) => (finishUpload = resolve))
        if (String(input) === '/api/auth/me') return Promise.resolve(Response.json(defaultMember))
        expect(String(input)).toBe('/api/recordings/7')
        return Promise.resolve(Response.json(업로드방송))
      }),
    )
    const user = userEvent.setup()
    renderApp('/recordings/new')

    await user.type(screen.getByLabelText('제목'), '업로드 방송')
    await user.click(screen.getByRole('radio', { name: '업로드 파일' }))
    await user.upload(screen.getByLabelText('영상 파일'), new File(['v'], 'clip.webm', { type: 'video/webm' }))
    await user.click(screen.getByRole('button', { name: '등록' }))

    expect(screen.getByRole('button', { name: /등록/ })).toBeDisabled()

    finishUpload(Response.json(업로드방송, { status: 201 }))
    expect(await screen.findByRole('heading', { name: '업로드 방송' })).toBeInTheDocument()
  })
})
