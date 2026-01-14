import express from 'express'
import { createProxyMiddleware } from 'http-proxy-middleware'
import path from 'path'
import { fileURLToPath } from 'url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

const app = express()
const PORT = process.env.PORT || 3000

// API target - MANDATORY environment variable
const API_TARGET = process.env.API_TARGET

if (!API_TARGET) {
  console.error('❌ ERROR: API_TARGET environment variable is required but not set!')
  console.error('Please set the API_TARGET environment variable to your backend API URL.')
  console.error('Example: API_TARGET=https://your-api-server.com/api')
  process.exit(1)
}

// Serve static files from the dashboard directory
app.use('/tls-manager', express.static(path.join(__dirname, 'tls-manager'), {
  // Set proper MIME types for JavaScript modules
  setHeaders: (res, filePath) => {
    if (filePath.endsWith('.js')) {
      res.setHeader('Content-Type', 'application/javascript')
    } else if (filePath.endsWith('.mjs')) {
      res.setHeader('Content-Type', 'application/javascript')
    } else if (filePath.endsWith('.css')) {
      res.setHeader('Content-Type', 'text/css')
    }
  }
}))

// Proxy API requests to the backend
app.use('/api', createProxyMiddleware({
  target: API_TARGET,
  changeOrigin: true,
  secure: false, // for local https dev
  logger: console,
  on: {
    proxyReq(proxyReq, req, res) {
      console.log('➡️', req.method, req.url);
    },
    proxyRes(proxyRes, req, res) {
      console.log('⬅️', proxyRes.statusCode);
    },
    error(err, req, res) {
      console.error('❌', err.message);
      res.status(500).json({ error: 'Proxy error' });
    }
  }
}))

// Redirect root to dashboard
app.get('/', (req, res) => {
  res.redirect('/tls-manager')
})

// Handle client-side routing - serve index.html for dashboard routes
app.get('/tls-manager', (req, res) => {
  res.sendFile(path.join(__dirname, 'tls-manager', 'index.html'))
})

// Catch-all route for client-side routing (must be last)
app.use((req, res) => {
  if (req.path.startsWith('/tls-manager')) {
    res.sendFile(path.join(__dirname, 'tls-manager', 'index.html'))
  } else {
    res.status(404).send('Not Found')
  }
})

app.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 Server running on http://0.0.0.0:${PORT}`)
  console.log(`📁 Serving static files from: ${path.join(__dirname, 'tls-manager')}`)
  console.log(`🔗 Proxying API requests to: ${API_TARGET}`)
  console.log(`🌐 Access your app at: http://localhost:${PORT}/tls-manager`)
})
