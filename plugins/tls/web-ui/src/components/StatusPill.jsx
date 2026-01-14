import React, { useMemo } from 'react'
import { Chip } from '@mui/material'
import dayjs from 'dayjs'

function computeStatus(validFrom, validTo, thresholdDays = 30) {
  const now = dayjs()
  
  // Parse dates with dayjs for better handling
  const start = validFrom ? dayjs(validFrom) : null
  const end = validTo ? dayjs(validTo) : null
  
  // Validate that dates are actually valid
  if (start && !start.isValid()) {
    return { label: 'Invalid start date', color: 'error' }
  }
  if (end && !end.isValid()) {
    return { label: 'Invalid end date', color: 'error' }
  }
  
  // Check if certificate is not yet valid
  if (start && start.isAfter(now)) {
    const daysUntilValid = start.diff(now, 'day')
    return { label: `Valid in ${daysUntilValid} days`, color: 'info' }
  }
  
  // Check if certificate is expired
  if (end && end.isBefore(now)) {
    return { label: 'Expired', color: 'error' }
  }
  
  // Check if certificate is expiring soon
  if (end) {
    const daysLeft = end.diff(now, 'day')
    if (daysLeft <= thresholdDays && daysLeft >= 0) {
      return { label: `Expires in ${daysLeft} days`, color: 'warning' }
    }
  }
  
  return { label: 'Valid', color: 'success' }
}

export default function StatusPill({ validFrom, validTo, thresholdDays = 30 }) {
  const status = useMemo(() => computeStatus(validFrom, validTo, thresholdDays), [validFrom, validTo, thresholdDays])
  return <Chip label={status.label} color={status.color} size="small" variant="outlined" />
}

export { computeStatus }


