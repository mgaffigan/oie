import React from 'react'
import { AppBar, Toolbar, IconButton, Box } from '@mui/material'
import logo from '../assets/oie_logo_bottom_text.svg'
import LogoutIcon from '@mui/icons-material/Logout';
import { useAuth } from '../context/AuthContext';

export default function DashboardLayout({ children }) {
  const { logout } = useAuth()

  return (
    <Box sx={{ display: 'flex'}}>
      <AppBar position="fixed" sx={{ zIndex: (theme) => theme.zIndex.appBar, background: 'linear-gradient(0deg, white 16%, rgb(254, 213, 216) 100%)'  }}>
        <Toolbar sx={{ gap: 2 }}>
          <Box component="img" src={logo} alt="Company logo" sx={{ height: 60 }} />
          <Box sx={{ flexGrow: 1 }} />
          <IconButton color="black" edge="end" aria-label="account" onClick={logout}>
            <LogoutIcon />
          </IconButton>
        </Toolbar>
      </AppBar>

      <Box component="main" sx={{ 
        flexGrow: 1, 
        p: 3, 
        height: '100vh',
        overflow: 'auto',
        display: 'flex',
        flexDirection: 'column'
      }}>
        <Toolbar />
        <Box sx={{ flex: 1, minHeight: 0 }}>
          {children}
        </Box>
      </Box>
    </Box>
  )
}
