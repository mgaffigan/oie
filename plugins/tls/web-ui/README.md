# Settings Dashboard (TLS / Certificates)

A React + Vite + MUI + Tailwind v4 dashboard for managing certificate stores and PKI-related settings.

## Stack
- React 18+ with functional components and hooks
- Vite 7
- MUI (Material UI) + Tailwind CSS v4 (via layers)
- React Router DOM v6/7
- Node >= 20

## Quick start
```bash
# Install dependencies
npm install

# Start dev server (http://localhost:5173/dashboard)
npm run dev

# Lint
npm run lint

# Build static assets (output in ./dashboard)
npm run build

# Preview production build
npm run preview
```

### Environment
- Create a `.env` file if needed:
  - `VITE_API_BASE_URL=https://oie-test.quantis.health/api`

Axios is configured with `withCredentials=true`, so successful login at `/users/_login` will set the `JSESSIONID` cookie in the browser (CORS must allow credentials).

## Routing & Auth
- BrowserRouter `basename` is `/dashboard`.
- Routes:
  - `/login` (public)
  - `/tls` (protected)
- `AuthContext` provides `login()` and `logout()`; `ProtectedRoute` guards private routes.
- Unauthenticated users are redirected to `/login`.

## Layout
- `DashboardLayout` uses a top AppBar only (no drawer). The app content renders beneath it.

## TLS Manager UI
- Page: `src/pages/TlsManagement.jsx`
- Features a tabbed interface with 3 stores and count chips:
  1. Native Java Certificate Store (read-only)
  2. Additional Trusted Certificates
  3. Private Key Store
- Selected tab persists in the URL via `?tab=<native|trusted|private>`.
- Search input filters the visible list for the active store.
- Cards: certificates are displayed as cards (not a table) showing:
  - Name/alias, type (Root/Intermediate/End-entity)
  - Subject, Issuer
  - Valid From/To
  - Fingerprint (SHA‑1)
  - Status pill: Valid / Expiring soon (30 days) / Expired
  - Actions: View Details, Export (placeholder)

### Reusable components
- `TabsWithCounts` — Tabs with icon + label + count, full width
- `TabPanel` — Conditional content wrapper for tabs
- `StoreToolbar` — Title, optional warning, action buttons
- `SearchInput` — Debounced search with icon
- `StatusPill` — Validity indicator (30-day threshold)
- `CertificateCard` — Presentational card for a certificate
- `CertificateList` — Responsive grid of cards
- Hook: `useCertificates` — fetches once, returns counts and per-store filtered lists

## Data & Services
- Mock service: `src/services/tlsService.js`
  - Returns a mixed list across stores with fields: `alias`, `name`, `type`, `subject`, `issuer`, `validFrom`, `validTo`, `fingerprintSha1`, `hasPrivateKey`, `store` (`native|trusted|private`).
- Replace with a real API by switching the implementation in `tlsService.js` to use Axios.

## Environment
- Vite base path is `/dashboard/` (see `vite.config.js`).
- API base URL is centralized in `src/services/api.js` and reads `import.meta.env.VITE_API_BASE_URL`.

## Build & Deploy
- `vite build` outputs to `./dashboard`.
- Serve the folder under a path matching `/dashboard/` (e.g., Nginx location or app server context path).
- If reverse proxying, ensure the base path is preserved for static assets.

## Security & PKCS#12 handling (design notes)
- PKCS#12 bundles (.p12/.pfx) typically contain certificates and private keys protected by a password.
- Recommended flow:
  - Parse on the server (Java KeyStore, OpenSSL, Python cryptography, or Node/OpenSSL) and return safe JSON (subjects, issuers, validity, fingerprints, chain, aliases). Do not return private key material to the client.
  - Client displays the parsed items using the existing card components.
- Optional client-side parsing (prototype only): use a pure JS library (e.g., node-forge) to parse in-memory after prompting for the password; never upload the password; do not persist or log sensitive data.
- Error cases to handle: wrong password, empty/unsupported bags, multiple key entries, duplicated aliases.

## Conventions
- Functional components, hooks, and one component per file
- Tailwind utilities for layout spacing; MUI `sx` for component overrides
- Avoid inline styles for static styling
- Keep files short (<300 lines) and split into subcomponents when needed

## Project structure (key folders)
```
src/
  components/     # Reusable UI components
  context/        # Auth context + ProtectedRoute
  layout/         # Dashboard layout (AppBar)
  pages/          # Route pages (Login, TlsManagement)
  services/       # Data fetching (mock service for now)
  hooks/          # Custom hooks (useCertificates)
```

## Development tips
- Use `console.debug` for quick diagnostics.
- When swapping to real APIs, centralize Axios setup (base URL, interceptors) and error handling.
- Keep secrets out of logs and never send private keys to the frontend.

---
Maintainers: update this README when adding routes, API contracts, or new modules.

