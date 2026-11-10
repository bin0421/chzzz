import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { anonymous, stubFetch } from './test/fetchStub'
import { renderApp } from './test/renderApp'

const 송출주인 = { id: 5, nickname: '송출주인' }

describe('라이브 방송 시작에는 로그인이 필요하다', () => {
  it('비로그인으로 송출 화면에 들어가면 로그인 화면으로 가고, 로그인하면 송출 화면으로 돌아온다', async () => {
    stubFetch([anonymous, { method: 'POST', path: '/api/auth/login', body: 송출주인 }])
    const user = userEvent.setup()
    renderApp('/live/new')

    expect(await screen.findByRole('heading', { name: '로그인' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '방송 시작' })).not.toBeInTheDocument()
    await user.type(screen.getByLabelText('아이디'), 'caster')
    await user.type(screen.getByLabelText('비밀번호'), 'password123')
    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('heading', { name: '라이브 방송 시작' })).toBeInTheDocument()
  })
})

describe('방송 목록의 라이브 방송 소유자', () => {
  it('라이브 섹션의 각 방송에 소유자 닉네임을 보여 주고, 소유자 없는 기존 방송에는 보여 주지 않는다', async () => {
    const 방송중 = { id: 8, title: '아침 라이브', status: 'LIVE', startedAt: '2026-11-10T01:00:00Z', endedAt: null }
    stubFetch([
      {
        path: '/api/live-broadcasts',
        body: [
          { ...방송중, owner: 송출주인 },
          { ...방송중, id: 3, title: '옛 라이브', status: 'ENDED', endedAt: '2026-11-01T00:00:00Z', owner: null },
        ],
      },
      { path: '/api/recordings', body: [] },
    ])
    renderApp('/')

    const section = await screen.findByRole('region', { name: '라이브 방송' })
    const items = await within(section).findAllByRole('listitem')
    expect(items[0]).toHaveTextContent('송출주인')
    expect(items[1]).toHaveTextContent('옛 라이브')
    expect(items[1].querySelector('.owner')).toBeNull()
  })
})
