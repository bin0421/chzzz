import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import App from '../App'
import { MediaAdapterContext, type MediaAdapter } from '../media/MediaAdapter'
import { PaymentAdapterContext, type PaymentAdapter } from '../payment/PaymentAdapter'
import { createFakeMediaAdapter } from './fakeMediaAdapter'
import { createFakePaymentAdapter } from './fakePaymentAdapter'

export function renderApp(path: string, options: { media?: MediaAdapter; payment?: PaymentAdapter } = {}) {
  const media = options.media ?? createFakeMediaAdapter().adapter
  const payment = options.payment ?? createFakePaymentAdapter().adapter
  return render(
    <MediaAdapterContext.Provider value={media}>
      <PaymentAdapterContext.Provider value={payment}>
        <MemoryRouter initialEntries={[path]}>
          <App />
        </MemoryRouter>
      </PaymentAdapterContext.Provider>
    </MediaAdapterContext.Provider>,
  )
}
