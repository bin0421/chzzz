import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { MediaAccessError } from './media/MediaAdapter'
import { createFakeMediaAdapter } from './test/fakeMediaAdapter'
import { stubFetch } from './test/fetchStub'
import { renderApp } from './test/renderApp'

const 대기 = {
  id: 7,
  title: '저녁 산책 라이브',
  status: 'PENDING',
  createdAt: '2026-11-09T09:00:00Z',
  startedAt: null,
  endedAt: null,
  whepUrl: '/media/live/7/whep',
}
const 방송중 = { ...대기, status: 'LIVE', startedAt: '2026-11-09T09:00:05Z' }
const 시작응답 = { ...대기, streamKey: 'secret-key', whipUrl: '/media/live/7/whip' }

/** 트랙 종료를 관찰할 수 있는 가짜 카메라 스트림. */
function cameraStream(label: string) {
  const track = { stop: vi.fn() }
  const stream = { id: label, getTracks: () => [track] } as unknown as MediaStream
  return { stream, track }
}

describe('송출 화면 카메라 선택', () => {
  it('전면 카메라로 미리보기를 열고, 후면을 고르면 이전 카메라를 끄고 후면으로 다시 연다', async () => {
    stubFetch([])
    const front = cameraStream('front')
    const back = cameraStream('back')
    const media = createFakeMediaAdapter()
    media.adapter.openPreview.mockImplementation(async (facing) => (facing === 'user' ? front.stream : back.stream))
    const user = userEvent.setup()
    renderApp('/live/new', { media: media.adapter })

    const preview = screen.getByTestId('live-preview') as HTMLVideoElement
    await vi.waitFor(() => expect(preview.srcObject).toBe(front.stream))
    expect(screen.getByRole('radio', { name: '전면 카메라' })).toBeChecked()

    await user.click(screen.getByRole('radio', { name: '후면 카메라' }))

    await vi.waitFor(() => expect(preview.srcObject).toBe(back.stream))
    expect(media.adapter.openPreview).toHaveBeenLastCalledWith('environment')
    expect(front.track.stop).toHaveBeenCalled()
  })

  it('방송을 시작한 뒤에는 카메라를 바꿀 수 없다', async () => {
    stubFetch([
      { method: 'POST', path: '/api/live-broadcasts', status: 201, body: 시작응답 },
      { path: '/api/live-broadcasts/7', body: 방송중 },
    ])
    const media = createFakeMediaAdapter()
    let connect = () => {}
    media.adapter.publish.mockImplementation(
      () => new Promise((resolve) => (connect = () => resolve(media.publication))),
    )
    const user = userEvent.setup()
    renderApp('/live/new', { media: media.adapter })

    await user.type(screen.getByLabelText('제목'), '저녁 산책 라이브')
    await user.click(await screen.findByRole('button', { name: '방송 시작' }))

    await vi.waitFor(() => expect(media.adapter.publish).toHaveBeenCalled())
    expect(screen.getByRole('radio', { name: '후면 카메라' })).toBeDisabled()

    connect()
    expect(await screen.findByText('방송 중')).toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: '후면 카메라' })).not.toBeInTheDocument()
    expect(media.adapter.openPreview).toHaveBeenCalledTimes(1)
  })
})

describe('송출 화면 카메라 오류 안내', () => {
  it.each([
    ['permission-denied', '카메라·마이크 권한이 거부되었습니다', '사이트 설정'],
    ['no-device', '카메라나 마이크를 찾지 못했습니다', '다른 앱'],
    ['insecure-context', 'HTTPS 주소가 아니라서', 'https://'],
  ] as const)('미리보기가 %s 오류로 실패하면 이유와 해결 방법을 보여 준다', async (reason, cause, remedy) => {
    stubFetch([])
    const media = createFakeMediaAdapter()
    media.adapter.openPreview.mockRejectedValue(new MediaAccessError(reason))
    renderApp('/live/new', { media: media.adapter })

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(cause)
    expect(alert).toHaveTextContent(remedy)
    expect(screen.getByRole('button', { name: '방송 시작' })).toBeDisabled()
  })
})

describe('송출 연결 실패 안내', () => {
  it('방송 시작 후 미디어 서버에 연결하지 못하면 네트워크·방화벽을 확인하라고 안내하고 다시 시작할 수 있다', async () => {
    stubFetch([{ method: 'POST', path: '/api/live-broadcasts', status: 201, body: 시작응답 }])
    const media = createFakeMediaAdapter()
    media.adapter.publish.mockRejectedValue(new Error('미디어 서버가 연결을 거절했습니다 (502).'))
    const user = userEvent.setup()
    renderApp('/live/new', { media: media.adapter })

    await user.type(screen.getByLabelText('제목'), '저녁 산책 라이브')
    await user.click(await screen.findByRole('button', { name: '방송 시작' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('송출 연결에 실패했습니다')
    expect(alert).toHaveTextContent('방화벽')
    expect(screen.getByRole('button', { name: '방송 시작' })).toBeEnabled()
  })

  it('영상이 서버에 닿지 못해 방송 중이 되기 전에 종료되면 송출을 멈추고 같은 안내를 보여 준다', async () => {
    stubFetch([
      { method: 'POST', path: '/api/live-broadcasts', status: 201, body: 시작응답 },
      { path: '/api/live-broadcasts/7', body: { ...대기, status: 'ENDED', endedAt: '2026-11-09T09:01:00Z' } },
    ])
    const media = createFakeMediaAdapter()
    const user = userEvent.setup()
    renderApp('/live/new', { media: media.adapter })

    await user.type(screen.getByLabelText('제목'), '저녁 산책 라이브')
    await user.click(await screen.findByRole('button', { name: '방송 시작' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('송출 연결에 실패했습니다')
    expect(media.publication.stop).toHaveBeenCalled()
    expect(screen.queryByText('방송 중')).not.toBeInTheDocument()
  })
})

describe('방송 중 시청 화면 링크', () => {
  it('방송 중이 되면 다른 기기로 옮길 수 있게 시청 화면 주소를 링크로 보여 준다', async () => {
    stubFetch([
      { method: 'POST', path: '/api/live-broadcasts', status: 201, body: 시작응답 },
      { path: '/api/live-broadcasts/7', body: 방송중 },
    ])
    const user = userEvent.setup()
    renderApp('/live/new')

    await user.type(screen.getByLabelText('제목'), '저녁 산책 라이브')
    await user.click(await screen.findByRole('button', { name: '방송 시작' }))

    const link = await screen.findByRole('link', { name: `${window.location.origin}/live/7` })
    expect(link).toHaveAttribute('href', '/live/7')
  })
})

describe('시청 화면 소리 켜기', () => {
  it('자동재생으로 소리가 막히면 음소거로 재생하고, 소리 켜기를 누르면 소리가 켜진다', async () => {
    stubFetch([{ path: '/api/live-broadcasts/7', body: 방송중 }])
    const media = createFakeMediaAdapter()
    media.adapter.startPlayback.mockImplementation(async (video) => {
      video.muted = true
      return 'muted'
    })
    const user = userEvent.setup()
    renderApp('/live/7', { media: media.adapter })

    const player = (await screen.findByTestId('live-player')) as HTMLVideoElement
    await vi.waitFor(() => expect(media.adapter.startPlayback).toHaveBeenCalledWith(player))
    expect(player.srcObject).toBe(media.remoteStream)

    await user.click(await screen.findByRole('button', { name: '소리 켜기' }))

    expect(player.muted).toBe(false)
    expect(screen.queryByRole('button', { name: '소리 켜기' })).not.toBeInTheDocument()
  })

  it('소리와 함께 재생되면 소리 켜기 버튼을 보여 주지 않는다', async () => {
    stubFetch([{ path: '/api/live-broadcasts/7', body: 방송중 }])
    const media = createFakeMediaAdapter()
    renderApp('/live/7', { media: media.adapter })

    await vi.waitFor(() => expect(media.adapter.startPlayback).toHaveBeenCalled())
    expect(screen.queryByRole('button', { name: '소리 켜기' })).not.toBeInTheDocument()
  })
})

describe('시청 화면 오류 안내', () => {
  it('라이브 영상에 연결하지 못하면 안내를 보여 준다', async () => {
    stubFetch([{ path: '/api/live-broadcasts/7', body: 방송중 }])
    const media = createFakeMediaAdapter()
    media.adapter.watch.mockRejectedValue(new Error('미디어 서버가 연결을 거절했습니다 (400).'))
    renderApp('/live/7', { media: media.adapter })

    expect(await screen.findByRole('alert')).toHaveTextContent('라이브 영상에 연결하지 못했습니다')
    expect(screen.getByRole('heading', { name: '저녁 산책 라이브' })).toBeInTheDocument()
  })

  it('없는 라이브 방송이면 찾을 수 없음과 목록 링크를 보여 준다', async () => {
    stubFetch([
      { path: '/api/live-broadcasts/999', status: 404, body: { detail: '라이브 방송을 찾을 수 없습니다.' } },
      { path: '/api/live-broadcasts', body: [] },
      { path: '/api/recordings', body: [] },
    ])
    const media = createFakeMediaAdapter()
    const user = userEvent.setup()
    renderApp('/live/999', { media: media.adapter })

    expect(await screen.findByRole('heading', { name: '찾을 수 없음' })).toBeInTheDocument()
    expect(media.adapter.watch).not.toHaveBeenCalled()

    await user.click(screen.getByRole('link', { name: '목록으로 돌아가기' }))

    expect(await screen.findByRole('region', { name: '라이브 방송' })).toBeInTheDocument()
  })
})

describe('송출 화면 제목 검증', () => {
  it('제목이 비어 있으면 서버에 보내지 않고 입력란 옆에 안내한다', async () => {
    const fetchMock = stubFetch([])
    const media = createFakeMediaAdapter()
    const user = userEvent.setup()
    renderApp('/live/new', { media: media.adapter })

    await user.type(screen.getByLabelText('제목'), '   ')
    await user.click(await screen.findByRole('button', { name: '방송 시작' }))

    expect(screen.getByLabelText('제목')).toHaveAccessibleDescription('제목을 입력해 주세요.')
    expect(fetchMock).not.toHaveBeenCalled()
    expect(media.adapter.publish).not.toHaveBeenCalled()
  })

  it('서버가 400으로 제목을 거절하면 그 메시지를 입력란 옆에 보여 주고 다시 시작할 수 있다', async () => {
    stubFetch([
      {
        method: 'POST',
        path: '/api/live-broadcasts',
        status: 400,
        body: { detail: '입력값을 확인해 주세요.', errors: [{ field: 'title', message: '서버가 거절한 제목입니다.' }] },
      },
    ])
    const media = createFakeMediaAdapter()
    const user = userEvent.setup()
    renderApp('/live/new', { media: media.adapter })

    await user.type(screen.getByLabelText('제목'), '저녁 산책 라이브')
    await user.click(await screen.findByRole('button', { name: '방송 시작' }))

    await vi.waitFor(() =>
      expect(screen.getByLabelText('제목')).toHaveAccessibleDescription('서버가 거절한 제목입니다.'),
    )
    expect(screen.getByRole('button', { name: '방송 시작' })).toBeEnabled()
    expect(media.adapter.publish).not.toHaveBeenCalled()
  })
})
