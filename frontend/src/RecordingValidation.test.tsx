import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { stubFetch } from './test/fetchStub'
import { renderApp } from './test/renderApp'

describe('녹화방송 등록 검증', () => {
  it('제목이 공백뿐이면 서버에 보내지 않고 제목 오류를 보여 준다', async () => {
    const fetchMock = stubFetch([])
    const user = userEvent.setup()
    renderApp('/recordings/new')

    await user.type(screen.getByLabelText('제목'), '   ')
    await user.type(screen.getByLabelText('영상 URL'), 'https://cdn.example.com/a.mp4')
    await user.click(screen.getByRole('button', { name: '등록' }))

    expect(screen.getByLabelText('제목')).toHaveAccessibleDescription('제목을 입력해 주세요.')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('제목이 100자를 넘으면 서버에 보내지 않고 제목 오류를 보여 준다', async () => {
    const fetchMock = stubFetch([])
    const user = userEvent.setup()
    renderApp('/recordings/new')

    await user.click(screen.getByLabelText('제목'))
    await user.paste('가'.repeat(101))
    await user.type(screen.getByLabelText('영상 URL'), 'https://cdn.example.com/a.mp4')
    await user.click(screen.getByRole('button', { name: '등록' }))

    expect(screen.getByLabelText('제목')).toHaveAccessibleDescription('제목은 100자 이하로 입력해 주세요.')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('URL을 비우면 서버에 보내지 않고 URL 오류를 보여 준다', async () => {
    const fetchMock = stubFetch([])
    const user = userEvent.setup()
    renderApp('/recordings/new')

    await user.type(screen.getByLabelText('제목'), 'URL 없는 방송')
    await user.click(screen.getByRole('button', { name: '등록' }))

    expect(screen.getByLabelText('영상 URL')).toHaveAccessibleDescription('영상 URL을 입력해 주세요.')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it.each([
    ['ftp://cdn.example.com/a.mp4', 'http 또는 https로 시작하는 영상 주소를 입력해 주세요.'],
    ['https:///a.mp4', '영상 주소에 호스트가 없습니다. 주소를 확인해 주세요.'],
  ])('URL이 %s 이면 서버에 보내지 않고 URL 오류를 보여 준다', async (url, message) => {
    const fetchMock = stubFetch([])
    const user = userEvent.setup()
    renderApp('/recordings/new')

    await user.type(screen.getByLabelText('제목'), '잘못된 주소 방송')
    await user.type(screen.getByLabelText('영상 URL'), url)
    await user.click(screen.getByRole('button', { name: '등록' }))

    expect(screen.getByLabelText('영상 URL')).toHaveAccessibleDescription(message)
    expect(fetchMock).not.toHaveBeenCalled()
  })

  it('서버가 400으로 거절하면 errors를 필드별로 보여 주고 등록 화면에 남는다', async () => {
    stubFetch([
      {
        method: 'POST',
        path: '/api/recordings',
        status: 400,
        body: {
          title: '잘못된 요청',
          status: 400,
          detail: '입력값을 확인해 주세요.',
          errors: [
            { field: 'title', message: '서버가 거절한 제목입니다.' },
            { field: 'url', message: '서버가 거절한 주소입니다.' },
          ],
        },
      },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/new')

    await user.type(screen.getByLabelText('제목'), '서버가 거절할 방송')
    await user.type(screen.getByLabelText('영상 URL'), 'https://cdn.example.com/rejected.mp4')
    await user.click(screen.getByRole('button', { name: '등록' }))

    expect(await screen.findByText('서버가 거절한 제목입니다.')).toBeInTheDocument()
    expect(screen.getByLabelText('제목')).toHaveAccessibleDescription('서버가 거절한 제목입니다.')
    expect(screen.getByLabelText('영상 URL')).toHaveAccessibleDescription('서버가 거절한 주소입니다.')
    expect(screen.getByRole('heading', { name: '녹화방송 등록' })).toBeInTheDocument()
  })

  it('서버가 입력란 밖의 오류(출처 개수 등)로 거절하면 그 메시지와 요약을 알림으로 보여 준다', async () => {
    stubFetch([
      {
        method: 'POST',
        path: '/api/recordings',
        status: 400,
        body: {
          status: 400,
          detail: '입력값을 확인해 주세요.',
          errors: [{ field: 'source', message: '업로드 파일과 외부 URL 중 하나만 입력해 주세요.' }],
        },
      },
    ])
    const user = userEvent.setup()
    renderApp('/recordings/new')

    await user.type(screen.getByLabelText('제목'), '출처 오류 방송')
    await user.type(screen.getByLabelText('영상 URL'), 'https://cdn.example.com/source.mp4')
    await user.click(screen.getByRole('button', { name: '등록' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('입력값을 확인해 주세요.')
    expect(alert).toHaveTextContent('업로드 파일과 외부 URL 중 하나만 입력해 주세요.')
  })
})
