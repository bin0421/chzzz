import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { stubFetch } from './test/fetchStub'
import { renderApp } from './test/renderApp'

describe('녹화방송 목록 화면', () => {
  it('등록된 녹화방송이 없으면 안내 문구와 등록 버튼을 보여 준다', async () => {
    stubFetch([{ path: '/api/recordings', body: [] }])
    const user = userEvent.setup()
    renderApp('/')

    expect(await screen.findByText('아직 등록된 녹화방송이 없습니다.')).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: '첫 녹화방송 등록하기' }))

    expect(screen.getByRole('heading', { name: '녹화방송 등록' })).toBeInTheDocument()
  })

  it('목록 항목마다 제목, 브라우저 로컬 시간의 등록일시, 출처 종류를 보여 준다', async () => {
    stubFetch([
      {
        path: '/api/recordings',
        body: [
          { id: 2, title: '업로드한 방송', sourceType: 'UPLOADED_FILE', createdAt: '2026-11-09T07:00:00Z' },
          { id: 1, title: 'URL 방송', sourceType: 'EXTERNAL_URL', createdAt: '2026-11-09T06:00:00Z' },
        ],
      },
    ])
    renderApp('/')

    const items = await screen.findAllByRole('listitem')
    expect(items[0]).toHaveTextContent('업로드한 방송')
    expect(items[0]).toHaveTextContent(localTime('2026-11-09T07:00:00Z'))
    expect(items[0]).toHaveTextContent('업로드')
    expect(items[1]).toHaveTextContent('URL 방송')
    expect(items[1]).toHaveTextContent(localTime('2026-11-09T06:00:00Z'))
    expect(items[1]).toHaveTextContent(/^URL 방송.*URL$/)
  })
})

describe('녹화방송 상세 화면', () => {
  it('제목과 브라우저 로컬 시간의 등록일시를 보여 준다', async () => {
    stubFetch([{ path: '/api/recordings/1', body: 첫방송 }])
    renderApp('/recordings/1')

    expect(await screen.findByRole('heading', { name: '첫 방송 다시보기' })).toBeInTheDocument()
    expect(screen.getByRole('article')).toHaveTextContent(`등록일시 ${localTime('2026-11-09T06:00:00Z')}`)
  })

  it('플레이어에서 재생 오류가 나면 안내 문구를 보여 준다', async () => {
    stubFetch([{ path: '/api/recordings/1', body: 첫방송 }])
    renderApp('/recordings/1')
    await screen.findByRole('heading', { name: '첫 방송 다시보기' })
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()

    fireEvent.error(screen.getByTestId('recording-player'))

    expect(screen.getByRole('alert')).toHaveTextContent('영상을 재생할 수 없습니다.')
  })

  it('없는 녹화방송이면 찾을 수 없음과 목록 링크를 보여 준다', async () => {
    stubFetch([
      {
        path: '/api/recordings/999',
        status: 404,
        body: { status: 404, title: '찾을 수 없음', detail: '녹화방송을 찾을 수 없습니다.' },
      },
      { path: '/api/recordings', body: [] },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/999')

    expect(await screen.findByRole('heading', { name: '찾을 수 없음' })).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: '목록으로 돌아가기' }))

    expect(await screen.findByText('아직 등록된 녹화방송이 없습니다.')).toBeInTheDocument()
  })
})

describe('공통 헤더', () => {
  it('상세 화면에서도 헤더의 링크로 등록 화면과 목록으로 갈 수 있다', async () => {
    stubFetch([
      { path: '/api/recordings', body: [] },
      { path: '/api/recordings/1', body: 첫방송 },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/1')
    await screen.findByRole('heading', { name: '첫 방송 다시보기' })

    const header = screen.getByRole('banner')
    await user.click(within(header).getByRole('link', { name: '녹화방송 등록' }))
    expect(screen.getByRole('heading', { name: '녹화방송 등록' })).toBeInTheDocument()

    await user.click(within(header).getByRole('link', { name: '목록' }))
    expect(await screen.findByText('아직 등록된 녹화방송이 없습니다.')).toBeInTheDocument()
  })
})

const 첫방송 = {
  id: 1,
  title: '첫 방송 다시보기',
  sourceType: 'EXTERNAL_URL',
  videoUrl: 'https://cdn.example.com/videos/first.mp4',
  createdAt: '2026-11-09T06:00:00Z',
}

/** 화면이 쓰는 형식과 같은, 브라우저 로컬 시간대 기준의 표시. */
function localTime(iso: string) {
  return new Date(iso).toLocaleString('ko-KR')
}
