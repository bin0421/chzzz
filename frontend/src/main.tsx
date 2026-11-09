import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import App from './App.tsx'
import './index.css'
import { MediaAdapterContext } from './media/MediaAdapter'
import { webrtcMediaAdapter } from './media/webrtcMediaAdapter'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <MediaAdapterContext.Provider value={webrtcMediaAdapter}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </MediaAdapterContext.Provider>
  </StrictMode>,
)
