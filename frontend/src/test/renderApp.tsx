import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import App from '../App'
import { MediaAdapterContext, type MediaAdapter } from '../media/MediaAdapter'
import { createFakeMediaAdapter } from './fakeMediaAdapter'

export function renderApp(path: string, options: { media?: MediaAdapter } = {}) {
  const media = options.media ?? createFakeMediaAdapter().adapter
  return render(
    <MediaAdapterContext.Provider value={media}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </MediaAdapterContext.Provider>,
  )
}
