import React from 'react'
import { Box, Stack, Typography, Button, Alert } from '@mui/material'

export default function StoreToolbar({ title, warning, actions = [] }) {
  return (
    <Box sx={{ mb: 2 }}>
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
        <Typography variant="h5" sx={{ fontWeight: 'bold' }}>{title}</Typography>
        <Stack direction="row" spacing={1}>
          {actions.map((a) => (
            <Button key={a.key} variant={a.variant ?? 'outlined'} color={a.color ?? 'primary'} onClick={a.onClick}>
              {a.label}
            </Button>
          ))}
        </Stack>
      </Stack>
      {warning ? <Alert severity="warning" variant="outlined">{warning}</Alert> : null}
    </Box>
  )
}


