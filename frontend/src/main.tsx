import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import App from './App.tsx'
import './index.css'
import { MediaAdapterContext } from './media/MediaAdapter'
import { webrtcMediaAdapter } from './media/webrtcMediaAdapter'
import { PaymentAdapterContext } from './payment/PaymentAdapter'
import { tossPaymentAdapter } from './payment/tossPaymentAdapter'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <MediaAdapterContext.Provider value={webrtcMediaAdapter}>
      <PaymentAdapterContext.Provider value={tossPaymentAdapter}>
        <BrowserRouter>
          <App />
        </BrowserRouter>
      </PaymentAdapterContext.Provider>
    </MediaAdapterContext.Provider>
  </StrictMode>,
)
