/**
 * ============================================================================
 * FILE README: src/main.tsx
 * ============================================================================
 * Purpose:
 *   Application bootstrap entry point for the CSC 8223 Federated Learning
 *   Smartphone Pose Dashboard (PROJECT_DASHBOARD).
 *
 * Responsibilities:
 *   1. Imports global Tailwind CSS v4 stylesheet (`./index.css`).
 *   2. Mounts the root `<App />` component inside `<StrictMode>` onto the
 *      `#root` DOM container defined in `index.html`.
 * ============================================================================
 */
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
