import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
// Library stylesheets for the editor. These are package CSS imports (not
// hand-written CSS), required for React Flow and MapLibre to render correctly.
import '@xyflow/react/dist/style.css'
import 'maplibre-gl/dist/maplibre-gl.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
