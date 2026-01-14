import React, { useState } from 'react'
import { Box, Button, TextField, Typography, Stack, Paper, InputAdornment } from '@mui/material'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import logo from '../assets/oie_logo_bottom_text.svg'
import PersonOutlineIcon from '@mui/icons-material/PersonOutline'
import KeyIcon from '@mui/icons-material/Key'

export default function Login() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const { login } = useAuth()

  const handleSubmit = async (e) => {
    e.preventDefault()
    if (!username || !password) {
      setError('Please enter username and password')
      return
    }
    setLoading(true)
    setError('')
    try {
      await login({ username, password })
      // Preserve search params when redirecting after login
      const from = location.state?.from
      const redirectTo = from?.pathname || '/tls'
      const search = from?.search || ''
      navigate(redirectTo + search, { replace: true })
    } catch (err) {
      const msg = err?.message || 'Login failed. Please try again.'
      setError(msg)
      // eslint-disable-next-line no-console
      console.debug('[Login] failed', { msg, err })
    } finally {
      setLoading(false)
    }
  }

  return (
    <Box sx={{
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      minHeight: '100vh',
      width: '100%',
      px: 2,
      background: 'linear-gradient(0deg, white 16%, rgb(254, 213, 216) 100%)'
    }}>
      <Paper sx={{ p: 4, width: 360, maxWidth: '100%' }} elevation={3}>
        <Stack spacing={2}>
          <Box component="img" src={logo} alt="Company logo" sx={{ height: 100, alignSelf: 'center' }} />
          <Stack component="form" spacing={2} onSubmit={handleSubmit}>
            <TextField
              label="Username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              fullWidth
              autoComplete="username"
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <PersonOutlineIcon fontSize="small" />
                    </InputAdornment>
                  ),
                }
              }}
            />
            <TextField
              label="Password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              fullWidth
              autoComplete="current-password"
              slotProps={{
                input: {
                  startAdornment: (
                  <InputAdornment position="start">
                    <KeyIcon fontSize="small" />
                  </InputAdornment>
                  ),
                }
              }}
            />
            {error ? <Typography color="error" variant="body2">{error}</Typography> : null}
            <Button type="submit" variant="contained" disabled={loading} className='bg-novamap-orange hover:bg-novamap-orange/90 text-white'>
              {loading ? 'Logging in…' : 'Login'}
            </Button>
          </Stack>
        </Stack>
      </Paper>
    </Box>
  )
}
