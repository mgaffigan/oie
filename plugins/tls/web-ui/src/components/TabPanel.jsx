import React from 'react'
import { Box } from '@mui/material'

export default function TabPanel({ children, value, index, sx }) {
  if (value !== index) return null
  return (
    <Box role="tabpanel" sx={sx}>{children}</Box>
  )
}


